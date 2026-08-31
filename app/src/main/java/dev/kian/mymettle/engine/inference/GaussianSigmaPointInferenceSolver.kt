package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceComputeBackend
import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import dev.kian.mymettle.domain.inference.InferencePosteriorRepresentation
import dev.kian.mymettle.domain.inference.InferenceSolverDiagnostics
import dev.kian.mymettle.domain.inference.InferenceSolverFamily
import dev.kian.mymettle.domain.inference.InferenceSolverIdentity
import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/** Mean/covariance transition supplied by the mathematical model, not by the solver. */
data class GaussianTransitionMoments(
    val mean: DoubleArray,
    val covariance: Array<DoubleArray>,
)

data class GaussianInferencePosterior(
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity,
    val solverIdentity: InferenceSolverIdentity,
    val stateNames: List<String>,
    val horizon: Instant,
    val observationIds: List<String>,
    val mean: DoubleArray,
    val covariance: Array<DoubleArray>,
    val diagnostics: InferenceSolverDiagnostics,
) {
    init {
        require(stateNames.isNotEmpty() && stateNames.distinct().size == stateNames.size)
        require(mean.size == stateNames.size)
        require(covariance.size == mean.size && covariance.all { it.size == mean.size })
        require(mean.all { it.isFinite() } && covariance.all { row -> row.all { it.isFinite() } })
        require(observationIds.distinct().size == observationIds.size)
    }
}

/**
 * Mathematical contract for a Gaussian-moment sequential approximation.
 *
 * The model owns state-transition moments and observation likelihood. The solver owns only the
 * posterior representation and deterministic sigma-point moment matching. Non-Gaussian likelihoods
 * are allowed; genuinely multimodal posteriors are an explicit limitation of this representation.
 */
interface GaussianMomentDynamicInferenceProblem<T> {
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
    val stateNames: List<String>
    fun initialMoments(horizon: Instant): GaussianTransitionMoments
    fun transitionMoments(
        previousMean: DoubleArray,
        previousCovariance: Array<DoubleArray>,
        elapsed: Duration,
    ): GaussianTransitionMoments
    fun logLikelihood(state: DoubleArray, observation: TimedInferenceObservation<T>): Double
}

data class GaussianSigmaPointSolverConfig(
    /** Positive central-weight parameter. lambda=1 keeps every covariance weight non-negative. */
    val lambda: Double = 1.0,
    /** Absolute covariance floor applied only for numerical positive-definiteness. */
    val covarianceJitter: Double = 1e-10,
) {
    init {
        require(lambda > 0.0 && lambda.isFinite())
        require(covarianceJitter > 0.0 && covarianceJitter.isFinite())
    }
}

/**
 * Deterministic sigma-point assumed-density filter.
 *
 * Prediction propagates model-supplied Gaussian moments. Update evaluates the exact model
 * log-likelihood at 2d+1 deterministic sigma points, reweights those points, then moment-matches a
 * Gaussian posterior. It is cheap and sequential, but intentionally cannot claim multimodal/tail
 * fidelity without dense-reference evidence.
 */
class GaussianSigmaPointInferenceSolver<T>(
    val config: GaussianSigmaPointSolverConfig = GaussianSigmaPointSolverConfig(),
) {
    val solverIdentity = InferenceSolverIdentity(
        solverFamily = InferenceSolverFamily.SIGMA_POINT,
        semanticVersion = "positive-weight-sigma-point-moment-matching-v1",
        computeBackend = InferenceComputeBackend.KOTLIN_JVM,
        deterministicReplay = true,
        approximationDefinition = "Gaussian moments;2d+1 sigma points;lambda=${config.lambda};likelihood reweight;moment match",
    )

    fun fullReplay(
        problem: GaussianMomentDynamicInferenceProblem<T>,
        observations: List<TimedInferenceObservation<T>>,
    ): GaussianInferencePosterior {
        require(observations.isNotEmpty())
        val ordered = observations.sortedWith(compareBy<TimedInferenceObservation<T>> { it.observedAt }.thenBy { it.id })
        var posterior: GaussianInferencePosterior? = null
        ordered.forEach { posterior = update(problem, posterior, it) }
        return requireNotNull(posterior)
    }

    fun update(
        problem: GaussianMomentDynamicInferenceProblem<T>,
        previous: GaussianInferencePosterior?,
        observation: TimedInferenceObservation<T>,
    ): GaussianInferencePosterior {
        if (previous != null) {
            require(previous.mathematicalModelIdentity == problem.mathematicalModelIdentity)
            require(previous.stateNames == problem.stateNames)
            require(!observation.observedAt.isBefore(previous.horizon))
            require(observation.id !in previous.observationIds)
        }
        val start = System.nanoTime()
        val prediction = if (previous == null) {
            problem.initialMoments(observation.observedAt)
        } else {
            problem.transitionMoments(
                previous.mean.copyOf(),
                copyMatrix(previous.covariance),
                Duration.between(previous.horizon, observation.observedAt),
            )
        }
        validateMoments(problem.stateNames.size, prediction)
        val sigma = sigmaPoints(prediction.mean, prediction.covariance)
        val logWeights = sigma.map { point ->
            ln(point.priorWeight) + problem.logLikelihood(point.state, observation)
        }
        val posteriorWeights = normaliseLogWeights(logWeights)
        val mean = DoubleArray(prediction.mean.size)
        sigma.indices.forEach { index ->
            val weight = posteriorWeights[index]
            sigma[index].state.indices.forEach { axis -> mean[axis] += weight * sigma[index].state[axis] }
        }
        val covariance = Array(mean.size) { DoubleArray(mean.size) }
        sigma.indices.forEach { index ->
            val weight = posteriorWeights[index]
            val state = sigma[index].state
            for (row in mean.indices) {
                for (col in mean.indices) {
                    covariance[row][col] += weight * (state[row] - mean[row]) * (state[col] - mean[col])
                }
            }
        }
        mean.indices.forEach { covariance[it][it] += config.covarianceJitter }
        val elapsed = System.nanoTime() - start
        return GaussianInferencePosterior(
            mathematicalModelIdentity = problem.mathematicalModelIdentity,
            solverIdentity = solverIdentity,
            stateNames = problem.stateNames,
            horizon = observation.observedAt,
            observationIds = previous?.observationIds.orEmpty() + observation.id,
            mean = mean,
            covariance = covariance,
            diagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.GAUSSIAN_MOMENTS,
                evaluatedNodeCount = sigma.size.toLong(),
                effectiveNodeCount = 1.0 / posteriorWeights.sumOf { it * it },
                updateRuntimeNanos = elapsed,
                notes = setOf("multimodality_not_representable", "tails_approximate"),
            ),
        )
    }

    private fun sigmaPoints(mean: DoubleArray, covariance: Array<DoubleArray>): List<SigmaPoint> {
        val dimension = mean.size
        val lambda = config.lambda
        val scale = sqrt(dimension + lambda)
        val root = choleskyWithJitter(covariance)
        val centralWeight = lambda / (dimension + lambda)
        val outerWeight = 1.0 / (2.0 * (dimension + lambda))
        return buildList(2 * dimension + 1) {
            add(SigmaPoint(mean.copyOf(), centralWeight))
            for (axis in 0 until dimension) {
                val plus = mean.copyOf()
                val minus = mean.copyOf()
                for (row in 0 until dimension) {
                    val delta = scale * root[row][axis]
                    plus[row] += delta
                    minus[row] -= delta
                }
                add(SigmaPoint(plus, outerWeight))
                add(SigmaPoint(minus, outerWeight))
            }
        }
    }

    private fun choleskyWithJitter(input: Array<DoubleArray>): Array<DoubleArray> {
        val n = input.size
        var jitter = config.covarianceJitter
        repeat(8) {
            val result = Array(n) { DoubleArray(n) }
            var valid = true
            for (row in 0 until n) {
                for (col in 0..row) {
                    var sum = input[row][col]
                    if (row == col) sum += jitter
                    for (k in 0 until col) sum -= result[row][k] * result[col][k]
                    if (row == col) {
                        if (!sum.isFinite() || sum <= 0.0) {
                            valid = false
                            break
                        }
                        result[row][col] = sqrt(sum)
                    } else {
                        val denominator = result[col][col]
                        if (denominator <= 0.0 || !denominator.isFinite()) {
                            valid = false
                            break
                        }
                        result[row][col] = sum / denominator
                    }
                }
                if (!valid) break
            }
            if (valid) return result
            jitter *= 10.0
        }
        throw IllegalArgumentException("Gaussian sigma-point covariance is not positive definite within numerical jitter budget.")
    }

    private fun normaliseLogWeights(values: List<Double>): DoubleArray {
        val finite = values.filter { it.isFinite() }
        require(finite.isNotEmpty()) { "Sigma-point likelihood produced no finite posterior support." }
        val maximum = finite.max()
        val weights = DoubleArray(values.size) { index ->
            if (values[index].isFinite()) exp(values[index] - maximum) else 0.0
        }
        val total = weights.sum()
        require(total.isFinite() && total > 0.0)
        weights.indices.forEach { weights[it] /= total }
        return weights
    }

    private fun validateMoments(dimension: Int, moments: GaussianTransitionMoments) {
        require(moments.mean.size == dimension)
        require(moments.covariance.size == dimension && moments.covariance.all { it.size == dimension })
        require(moments.mean.all { it.isFinite() })
        require(moments.covariance.all { row -> row.all { it.isFinite() } })
    }

    private fun copyMatrix(matrix: Array<DoubleArray>): Array<DoubleArray> =
        Array(matrix.size) { matrix[it].copyOf() }

    private data class SigmaPoint(val state: DoubleArray, val priorWeight: Double)
}
