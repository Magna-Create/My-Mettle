package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Device/runtime benchmark for solver representations, deliberately separate from Candidate-v2
 * biological/performance evidence. Every solver below receives the same two-state mathematical
 * problem. Results establish algorithm/backend feasibility only; they are never product authority.
 */
data class InferenceSolverRuntimeSummary(
    val medianNanos: Long,
    val p95Nanos: Long,
    val repetitions: Int,
) {
    init {
        require(medianNanos >= 0L && p95Nanos >= medianNanos && repetitions > 0)
    }
}

data class InferenceSolverSubstrateBenchmarkResult(
    val benchmarkVersion: String,
    val mathematicalModelIdentity: String,
    val gridNodeCount: Int,
    val observationCount: Int,
    val denseRuntime: InferenceSolverRuntimeSummary,
    val sparseRuntime: InferenceSolverRuntimeSummary,
    val sigmaPointRuntime: InferenceSolverRuntimeSummary,
    val denseIncrementalReplayEquivalent: Boolean,
    val sparseRetainedNodeCount: Int,
    val sparseLevelQuantileMaxAbsoluteError: Double,
    val sparseDriftQuantileMaxAbsoluteError: Double,
    val sparseMeanMaxAbsoluteError: Double,
    val sparseCovarianceMaxAbsoluteError: Double,
    val sigmaPointMeanMaxAbsoluteError: Double,
    val sigmaPointCovarianceMaxAbsoluteError: Double,
    val denseEffectiveNodeCount: Double,
    val sparseEffectiveNodeCount: Double,
    val sigmaPointEffectiveNodeCount: Double,
    val lowRankScreens: List<LowRankPosteriorScreenResult>,
) {
    init {
        require(benchmarkVersion.isNotBlank() && mathematicalModelIdentity.isNotBlank())
        require(gridNodeCount > 0 && observationCount > 0 && sparseRetainedNodeCount > 0)
        listOf(
            sparseLevelQuantileMaxAbsoluteError,
            sparseDriftQuantileMaxAbsoluteError,
            sparseMeanMaxAbsoluteError,
            sparseCovarianceMaxAbsoluteError,
            sigmaPointMeanMaxAbsoluteError,
            sigmaPointCovarianceMaxAbsoluteError,
            denseEffectiveNodeCount,
            sparseEffectiveNodeCount,
            sigmaPointEffectiveNodeCount,
        ).forEach { require(it.isFinite() && it >= 0.0) }
    }
}

object InferenceSolverSubstrateBenchmark {
    const val VERSION = "dynamic-level-drift-solver-substrate-benchmark-v1"
    private const val REPEATS = 5

    fun run(): InferenceSolverSubstrateBenchmarkResult {
        val problem = BenchmarkProblem()
        val observations = problem.observations()
        val denseSolver = DenseSequentialGridInferenceSolver<Double>()
        val sparseSolver = AdaptiveSparseGridInferenceSolver<Double>(
            AdaptiveSparseGridSolverConfig(
                retainedPosteriorMass = 0.9995,
                minimumRetainedNodes = 21,
                maximumRetainedNodes = 256,
            ),
        )
        val sigmaSolver = GaussianSigmaPointInferenceSolver<Double>()

        // One unmeasured invocation reduces class-loading/JIT setup noise in the reported repeats.
        denseSolver.fullReplay(problem, observations)
        sparseSolver.fullReplay(problem, observations)
        sigmaSolver.fullReplay(problem, observations)

        val denseRuns = timedRuns(REPEATS) { denseSolver.fullReplay(problem, observations) }
        val sparseRuns = timedRuns(REPEATS) { sparseSolver.fullReplay(problem, observations) }
        val sigmaRuns = timedRuns(REPEATS) { sigmaSolver.fullReplay(problem, observations) }
        val dense = denseRuns.last().value
        val sparse = sparseRuns.last().value
        val sigma = sigmaRuns.last().value

        var incremental: GridInferencePosterior? = null
        observations.forEach { observation -> incremental = denseSolver.update(problem, incremental, observation) }
        val incrementalDense = requireNotNull(incremental)
        val incrementalEquivalent = dense.nodes.size == incrementalDense.nodes.size &&
            dense.nodes.zip(incrementalDense.nodes).all { (left, right) ->
                left.point.id == right.point.id && abs(left.posteriorWeight - right.posteriorWeight) <= 1e-13
            }

        val denseMoments = moments(dense)
        val sparseMoments = moments(sparse)
        val levelQuantileError = QUANTILES.maxOf { q ->
            abs(quantile(dense, 0, q) - quantile(sparse, 0, q))
        }
        val driftQuantileError = QUANTILES.maxOf { q ->
            abs(quantile(dense, 1, q) - quantile(sparse, 1, q))
        }
        val sparseMeanError = denseMoments.mean.indices.maxOf { axis ->
            abs(denseMoments.mean[axis] - sparseMoments.mean[axis])
        }
        val sparseCovarianceError = maxMatrixDifference(denseMoments.covariance, sparseMoments.covariance)
        val sigmaMeanError = denseMoments.mean.indices.maxOf { axis ->
            abs(denseMoments.mean[axis] - sigma.mean[axis])
        }
        val sigmaCovarianceError = maxMatrixDifference(denseMoments.covariance, sigma.covariance)

        val posteriorMatrix = Array(problem.levelValues.size) { DoubleArray(problem.driftValues.size) }
        dense.nodes.forEach { node ->
            val row = problem.levelIndex.getValue(node.point.coordinates[0])
            val col = problem.driftIndex.getValue(node.point.coordinates[1])
            posteriorMatrix[row][col] = node.posteriorWeight
        }
        val lowRank = listOf(1, 2, 3, 4).filter { it <= minOf(problem.levelValues.size, problem.driftValues.size) }
            .map { rank -> LowRankPosteriorViabilityScreen.screen(posteriorMatrix.map { it.toList() }, rank) }

        return InferenceSolverSubstrateBenchmarkResult(
            benchmarkVersion = VERSION,
            mathematicalModelIdentity = problem.mathematicalModelIdentity.identity,
            gridNodeCount = problem.grid.size,
            observationCount = observations.size,
            denseRuntime = runtimeSummary(denseRuns),
            sparseRuntime = runtimeSummary(sparseRuns),
            sigmaPointRuntime = runtimeSummary(sigmaRuns),
            denseIncrementalReplayEquivalent = incrementalEquivalent,
            sparseRetainedNodeCount = sparse.nodes.size,
            sparseLevelQuantileMaxAbsoluteError = levelQuantileError,
            sparseDriftQuantileMaxAbsoluteError = driftQuantileError,
            sparseMeanMaxAbsoluteError = sparseMeanError,
            sparseCovarianceMaxAbsoluteError = sparseCovarianceError,
            sigmaPointMeanMaxAbsoluteError = sigmaMeanError,
            sigmaPointCovarianceMaxAbsoluteError = sigmaCovarianceError,
            denseEffectiveNodeCount = requireNotNull(dense.diagnostics.effectiveNodeCount),
            sparseEffectiveNodeCount = requireNotNull(sparse.diagnostics.effectiveNodeCount),
            sigmaPointEffectiveNodeCount = requireNotNull(sigma.diagnostics.effectiveNodeCount),
            lowRankScreens = lowRank,
        )
    }

    private data class Timed<T>(val nanos: Long, val value: T)

    private fun <T> timedRuns(count: Int, block: () -> T): List<Timed<T>> = List(count) {
        val start = System.nanoTime()
        val value = block()
        Timed(System.nanoTime() - start, value)
    }

    private fun <T> runtimeSummary(runs: List<Timed<T>>): InferenceSolverRuntimeSummary {
        val ordered = runs.map { it.nanos }.sorted()
        val median = ordered[ordered.size / 2]
        val p95Index = ((ordered.size - 1) * 0.95).toInt().coerceIn(0, ordered.lastIndex)
        return InferenceSolverRuntimeSummary(median, ordered[p95Index], ordered.size)
    }

    private data class Moments(val mean: DoubleArray, val covariance: Array<DoubleArray>)

    private fun moments(posterior: GridInferencePosterior): Moments {
        val dimension = posterior.parameterNames.size
        val mean = DoubleArray(dimension)
        posterior.nodes.forEach { node ->
            node.point.coordinates.indices.forEach { axis -> mean[axis] += node.posteriorWeight * node.point.coordinates[axis] }
        }
        val covariance = Array(dimension) { DoubleArray(dimension) }
        posterior.nodes.forEach { node ->
            for (row in 0 until dimension) for (col in 0 until dimension) {
                covariance[row][col] += node.posteriorWeight *
                    (node.point.coordinates[row] - mean[row]) *
                    (node.point.coordinates[col] - mean[col])
            }
        }
        return Moments(mean, covariance)
    }

    private fun quantile(posterior: GridInferencePosterior, axis: Int, probability: Double): Double {
        val ordered = posterior.nodes.sortedBy { it.point.coordinates[axis] }
        var cumulative = 0.0
        ordered.forEach { node ->
            cumulative += node.posteriorWeight
            if (cumulative >= probability) return node.point.coordinates[axis]
        }
        return ordered.last().point.coordinates[axis]
    }

    private fun maxMatrixDifference(left: Array<DoubleArray>, right: Array<DoubleArray>): Double {
        require(left.size == right.size && left.indices.all { left[it].size == right[it].size })
        var maximum = 0.0
        for (row in left.indices) for (col in left[row].indices) {
            maximum = max(maximum, abs(left[row][col] - right[row][col]))
        }
        return maximum
    }

    /** Shared dynamic level+drift problem for dense/sparse/sigma-point algorithm comparison. */
    private class BenchmarkProblem :
        FixedGridDynamicInferenceProblem<Double>,
        GaussianMomentDynamicInferenceProblem<Double> {
        override val mathematicalModelIdentity = InferenceMathematicalModelIdentity(
            family = "solver_substrate_dynamic_level_drift",
            semanticVersion = "v1",
            definition = "level_k=level_k-1+drift+N(0,0.055);drift_k=drift_k-1+N(0,0.012);obs=level+N(0,0.10)",
        )
        override val parameterNames = listOf("stateLevel", "trajectory")
        override val stateNames = parameterNames
        val levelValues = List(41) { -1.0 + it * 0.05 }
        val driftValues = List(21) { -0.20 + it * 0.02 }
        val levelIndex = levelValues.withIndex().associate { it.value to it.index }
        val driftIndex = driftValues.withIndex().associate { it.value to it.index }
        override val grid = buildList {
            levelValues.forEachIndexed { levelIndex, level ->
                driftValues.forEachIndexed { driftIndex, drift ->
                    add(InferenceGridPoint("l${levelIndex}_d$driftIndex", listOf(level, drift)))
                }
            }
        }

        override fun initialLogPrior(point: InferenceGridPoint, horizon: Instant): Double =
            normalLogDensity(point.coordinates[0], 0.0, 0.40) + normalLogDensity(point.coordinates[1], 0.0, 0.06)

        override fun logTransitionDensity(
            from: InferenceGridPoint,
            to: InferenceGridPoint,
            elapsed: Duration,
        ): Double {
            val steps = elapsedSteps(elapsed)
            val expectedLevel = from.coordinates[0] + from.coordinates[1] * steps
            val expectedDrift = from.coordinates[1]
            return normalLogDensity(to.coordinates[0], expectedLevel, 0.055 * sqrt(steps)) +
                normalLogDensity(to.coordinates[1], expectedDrift, 0.012 * sqrt(steps))
        }

        override fun logLikelihood(point: InferenceGridPoint, observation: TimedInferenceObservation<Double>): Double =
            normalLogDensity(observation.value, point.coordinates[0], 0.10)

        override fun initialMoments(horizon: Instant) = GaussianTransitionMoments(
            mean = doubleArrayOf(0.0, 0.0),
            covariance = arrayOf(
                doubleArrayOf(0.40.pow(2), 0.0),
                doubleArrayOf(0.0, 0.06.pow(2)),
            ),
        )

        override fun transitionMoments(
            previousMean: DoubleArray,
            previousCovariance: Array<DoubleArray>,
            elapsed: Duration,
        ): GaussianTransitionMoments {
            val steps = elapsedSteps(elapsed)
            val f00 = 1.0
            val f01 = steps
            val f10 = 0.0
            val f11 = 1.0
            val mean = doubleArrayOf(
                previousMean[0] + steps * previousMean[1],
                previousMean[1],
            )
            val a00 = f00 * previousCovariance[0][0] + f01 * previousCovariance[1][0]
            val a01 = f00 * previousCovariance[0][1] + f01 * previousCovariance[1][1]
            val a10 = f10 * previousCovariance[0][0] + f11 * previousCovariance[1][0]
            val a11 = f10 * previousCovariance[0][1] + f11 * previousCovariance[1][1]
            val covariance = arrayOf(
                doubleArrayOf(
                    a00 * f00 + a01 * f01 + (0.055 * sqrt(steps)).pow(2),
                    a00 * f10 + a01 * f11,
                ),
                doubleArrayOf(
                    a10 * f00 + a11 * f01,
                    a10 * f10 + a11 * f11 + (0.012 * sqrt(steps)).pow(2),
                ),
            )
            return GaussianTransitionMoments(mean, covariance)
        }

        override fun logLikelihood(state: DoubleArray, observation: TimedInferenceObservation<Double>): Double =
            normalLogDensity(observation.value, state[0], 0.10)

        fun observations(): List<TimedInferenceObservation<Double>> = listOf(
            observation("o1", 1, -0.05),
            observation("o2", 2, 0.06),
            observation("o3", 3, 0.16),
            observation("o4", 4, 0.29),
            observation("o5", 5, 0.36),
            observation("o6", 6, 0.46),
        )

        private fun observation(id: String, step: Long, value: Double) = TimedInferenceObservation(
            id = id,
            observedAt = BASE.plusSeconds(step * 86_400L),
            value = value,
        )

        private fun elapsedSteps(elapsed: Duration): Double = max(
            elapsed.toMillis().toDouble() / Duration.ofDays(1).toMillis().toDouble(),
            1e-6,
        )
    }

    private fun normalLogDensity(value: Double, mean: Double, sd: Double): Double {
        require(sd > 0.0 && sd.isFinite())
        val z = (value - mean) / sd
        return -0.5 * z * z - ln(sd) - 0.5 * ln(2.0 * PI)
    }

    private val QUANTILES = listOf(0.05, 0.50, 0.95)
    private val BASE = Instant.parse("2026-01-01T00:00:00Z")
}
