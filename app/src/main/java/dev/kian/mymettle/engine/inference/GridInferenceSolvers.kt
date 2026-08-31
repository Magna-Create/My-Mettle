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

/** Generic deterministic node used by solver bake-offs; coordinates have model-defined meaning. */
data class InferenceGridPoint(
    val id: String,
    val coordinates: List<Double>,
) {
    init {
        require(id.isNotBlank())
        require(coordinates.isNotEmpty() && coordinates.all { it.isFinite() })
    }
}

data class TimedInferenceObservation<T>(
    val id: String,
    val observedAt: Instant,
    val value: T,
) {
    init { require(id.isNotBlank()) }
}

data class WeightedInferenceGridPoint(
    val point: InferenceGridPoint,
    val posteriorWeight: Double,
) {
    init { require(posteriorWeight.isFinite() && posteriorWeight >= 0.0) }
}

data class GridInferencePosterior(
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity,
    val solverIdentity: InferenceSolverIdentity,
    val parameterNames: List<String>,
    val horizon: Instant,
    val observationIds: List<String>,
    val nodes: List<WeightedInferenceGridPoint>,
    val diagnostics: InferenceSolverDiagnostics,
) {
    init {
        require(parameterNames.isNotEmpty() && parameterNames.distinct().size == parameterNames.size)
        require(nodes.isNotEmpty())
        require(nodes.all { it.point.coordinates.size == parameterNames.size })
        require(kotlin.math.abs(nodes.sumOf { it.posteriorWeight } - 1.0) <= 1e-8)
        require(observationIds.distinct().size == observationIds.size)
    }
}

/**
 * Model contract consumed identically by dense/sequential/sparse fixed-grid solvers.
 * Transition density is model-owned; numerical enumeration/normalisation is solver-owned.
 */
interface FixedGridDynamicInferenceProblem<T> {
    val mathematicalModelIdentity: InferenceMathematicalModelIdentity
    val parameterNames: List<String>
    val grid: List<InferenceGridPoint>

    fun initialLogPrior(point: InferenceGridPoint, horizon: Instant): Double
    fun logTransitionDensity(
        from: InferenceGridPoint,
        to: InferenceGridPoint,
        elapsed: Duration,
    ): Double
    fun logLikelihood(point: InferenceGridPoint, observation: TimedInferenceObservation<T>): Double
}

interface FixedGridInferenceSolver<T> {
    val solverIdentity: InferenceSolverIdentity

    fun fullReplay(
        problem: FixedGridDynamicInferenceProblem<T>,
        observations: List<TimedInferenceObservation<T>>,
    ): GridInferencePosterior

    fun update(
        problem: FixedGridDynamicInferenceProblem<T>,
        previous: GridInferencePosterior?,
        observation: TimedInferenceObservation<T>,
    ): GridInferencePosterior
}

/** High-fidelity fixed-grid sequential Bayes filter. No posterior pruning. */
class DenseSequentialGridInferenceSolver<T> : FixedGridInferenceSolver<T> {
    override val solverIdentity = InferenceSolverIdentity(
        solverFamily = InferenceSolverFamily.SEQUENTIAL_TENSOR,
        semanticVersion = "fixed-grid-sequential-bayes-v1",
        computeBackend = InferenceComputeBackend.KOTLIN_JVM,
        deterministicReplay = true,
        approximationDefinition = "complete_target_grid;ordered_logsumexp_transition;no_posterior_pruning",
    )

    override fun fullReplay(
        problem: FixedGridDynamicInferenceProblem<T>,
        observations: List<TimedInferenceObservation<T>>,
    ): GridInferencePosterior {
        require(observations.isNotEmpty())
        val ordered = observations.sortedWith(compareBy<TimedInferenceObservation<T>> { it.observedAt }.thenBy { it.id })
        var posterior: GridInferencePosterior? = null
        ordered.forEach { observation -> posterior = update(problem, posterior, observation) }
        return requireNotNull(posterior)
    }

    override fun update(
        problem: FixedGridDynamicInferenceProblem<T>,
        previous: GridInferencePosterior?,
        observation: TimedInferenceObservation<T>,
    ): GridInferencePosterior {
        validatePrevious(problem, previous, observation)
        val start = System.nanoTime()
        val raw = if (previous == null) {
            problem.grid.map { point ->
                RawWeight(point, problem.initialLogPrior(point, observation.observedAt) + problem.logLikelihood(point, observation))
            }
        } else {
            val elapsed = Duration.between(previous.horizon, observation.observedAt)
            problem.grid.map { target ->
                val transitionTerms = previous.nodes.map { source ->
                    if (source.posteriorWeight == 0.0) Double.NEGATIVE_INFINITY
                    else ln(source.posteriorWeight) + problem.logTransitionDensity(source.point, target, elapsed)
                }
                RawWeight(target, logSumExp(transitionTerms) + problem.logLikelihood(target, observation))
            }
        }
        val nodes = normalise(raw)
        val elapsedNanos = System.nanoTime() - start
        return GridInferencePosterior(
            mathematicalModelIdentity = problem.mathematicalModelIdentity,
            solverIdentity = solverIdentity,
            parameterNames = problem.parameterNames,
            horizon = observation.observedAt,
            observationIds = previous.orEmptyObservationIds() + observation.id,
            nodes = nodes,
            diagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_DENSE_NODES,
                evaluatedNodeCount = nodes.size.toLong(),
                effectiveNodeCount = effectiveNodeCount(nodes),
                updateRuntimeNanos = elapsedNanos,
            ),
        )
    }
}

/**
 * Deterministic posterior-focused approximation. It evaluates the same target grid/transition and
 * likelihood as the dense solver, then retains the smallest stable node set reaching target mass.
 * Pruning is explicit approximation; omitted support can never be presented as exact replay.
 */
data class AdaptiveSparseGridSolverConfig(
    val retainedPosteriorMass: Double = 0.999,
    val minimumRetainedNodes: Int = 9,
    val maximumRetainedNodes: Int = 512,
) {
    init {
        require(retainedPosteriorMass in 0.90..1.0)
        require(minimumRetainedNodes > 0)
        require(maximumRetainedNodes >= minimumRetainedNodes)
    }
}

class AdaptiveSparseGridInferenceSolver<T>(
    val config: AdaptiveSparseGridSolverConfig = AdaptiveSparseGridSolverConfig(),
) : FixedGridInferenceSolver<T> {
    override val solverIdentity = InferenceSolverIdentity(
        solverFamily = InferenceSolverFamily.ADAPTIVE_SPARSE_TENSOR,
        semanticVersion = "posterior-mass-pruned-grid-v1",
        computeBackend = InferenceComputeBackend.KOTLIN_JVM,
        deterministicReplay = true,
        approximationDefinition = "targetMass=${config.retainedPosteriorMass};min=${config.minimumRetainedNodes};max=${config.maximumRetainedNodes}",
    )

    override fun fullReplay(
        problem: FixedGridDynamicInferenceProblem<T>,
        observations: List<TimedInferenceObservation<T>>,
    ): GridInferencePosterior {
        require(observations.isNotEmpty())
        val ordered = observations.sortedWith(compareBy<TimedInferenceObservation<T>> { it.observedAt }.thenBy { it.id })
        var posterior: GridInferencePosterior? = null
        ordered.forEach { observation -> posterior = update(problem, posterior, observation) }
        return requireNotNull(posterior)
    }

    override fun update(
        problem: FixedGridDynamicInferenceProblem<T>,
        previous: GridInferencePosterior?,
        observation: TimedInferenceObservation<T>,
    ): GridInferencePosterior {
        validatePrevious(problem, previous, observation)
        val start = System.nanoTime()
        val raw = if (previous == null) {
            problem.grid.map { point ->
                RawWeight(point, problem.initialLogPrior(point, observation.observedAt) + problem.logLikelihood(point, observation))
            }
        } else {
            val elapsed = Duration.between(previous.horizon, observation.observedAt)
            problem.grid.map { target ->
                val transitionTerms = previous.nodes.map { source ->
                    if (source.posteriorWeight == 0.0) Double.NEGATIVE_INFINITY
                    else ln(source.posteriorWeight) + problem.logTransitionDensity(source.point, target, elapsed)
                }
                RawWeight(target, logSumExp(transitionTerms) + problem.logLikelihood(target, observation))
            }
        }
        val complete = normalise(raw)
        val retained = prune(complete)
        val elapsedNanos = System.nanoTime() - start
        return GridInferencePosterior(
            mathematicalModelIdentity = problem.mathematicalModelIdentity,
            solverIdentity = solverIdentity,
            parameterNames = problem.parameterNames,
            horizon = observation.observedAt,
            observationIds = previous.orEmptyObservationIds() + observation.id,
            nodes = retained,
            diagnostics = InferenceSolverDiagnostics(
                solverIdentity = solverIdentity,
                posteriorRepresentation = InferencePosteriorRepresentation.WEIGHTED_SPARSE_NODES,
                evaluatedNodeCount = problem.grid.size.toLong(),
                effectiveNodeCount = effectiveNodeCount(retained),
                updateRuntimeNanos = elapsedNanos,
                notes = setOf("posterior_support_pruned_after_update"),
            ),
        )
    }

    private fun prune(nodes: List<WeightedInferenceGridPoint>): List<WeightedInferenceGridPoint> {
        val ordered = nodes.sortedWith(
            compareByDescending<WeightedInferenceGridPoint> { it.posteriorWeight }.thenBy { it.point.id },
        )
        val selected = ArrayList<WeightedInferenceGridPoint>()
        var mass = 0.0
        for (node in ordered) {
            if (selected.size >= config.maximumRetainedNodes) break
            selected += node
            mass += node.posteriorWeight
            if (selected.size >= config.minimumRetainedNodes && mass >= config.retainedPosteriorMass) break
        }
        val total = selected.sumOf { it.posteriorWeight }
        require(total > 0.0 && total.isFinite())
        return selected.map { it.copy(posteriorWeight = it.posteriorWeight / total) }
            .sortedBy { it.point.id }
    }
}

private data class RawWeight(val point: InferenceGridPoint, val logWeight: Double)

private fun <T> validatePrevious(
    problem: FixedGridDynamicInferenceProblem<T>,
    previous: GridInferencePosterior?,
    observation: TimedInferenceObservation<T>,
) {
    if (previous == null) return
    require(previous.mathematicalModelIdentity == problem.mathematicalModelIdentity)
    require(previous.parameterNames == problem.parameterNames)
    require(!observation.observedAt.isBefore(previous.horizon)) { "Sequential inference cannot move backwards in time." }
    require(observation.id !in previous.observationIds) { "An observation cannot be assimilated twice." }
}

private fun GridInferencePosterior?.orEmptyObservationIds(): List<String> = this?.observationIds.orEmpty()

private fun normalise(raw: List<RawWeight>): List<WeightedInferenceGridPoint> {
    val finite = raw.filter { it.logWeight.isFinite() }
    require(finite.isNotEmpty()) { "Inference posterior has no finite mass." }
    val maximum = finite.maxOf { it.logWeight }
    val unnormalised = raw.map { if (it.logWeight.isFinite()) exp(it.logWeight - maximum) else 0.0 }
    val total = unnormalised.sum()
    require(total.isFinite() && total > 0.0) { "Inference posterior normalisation failed." }
    return raw.mapIndexed { index, value -> WeightedInferenceGridPoint(value.point, unnormalised[index] / total) }
}

private fun logSumExp(values: List<Double>): Double {
    val finite = values.filter { it.isFinite() }
    if (finite.isEmpty()) return Double.NEGATIVE_INFINITY
    val maximum = finite.max()
    return maximum + ln(finite.sumOf { exp(it - maximum) })
}

private fun effectiveNodeCount(nodes: List<WeightedInferenceGridPoint>): Double =
    1.0 / nodes.sumOf { it.posteriorWeight * it.posteriorWeight }
