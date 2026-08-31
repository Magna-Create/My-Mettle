package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GridInferenceSolversTest {
    @Test
    fun `incremental dense tensor is exactly equivalent to full replay`() {
        val problem = GaussianRandomWalkProblem()
        val observations = observations()
        val solver = DenseSequentialGridInferenceSolver<Double>()
        val full = solver.fullReplay(problem, observations)
        var incremental: GridInferencePosterior? = null
        observations.forEach { observation -> incremental = solver.update(problem, incremental, observation) }
        val replayed = requireNotNull(incremental)
        assertEquals(full.observationIds, replayed.observationIds)
        assertEquals(full.nodes.map { it.point.id }, replayed.nodes.map { it.point.id })
        full.nodes.zip(replayed.nodes).forEach { (left, right) ->
            assertEquals(left.posteriorWeight, right.posteriorWeight, 1e-14)
        }
    }

    @Test
    fun `adaptive sparse tensor preserves central quantiles and tail probability on smooth fixture`() {
        val problem = GaussianRandomWalkProblem()
        val observations = observations()
        val dense = DenseSequentialGridInferenceSolver<Double>().fullReplay(problem, observations)
        val sparse = AdaptiveSparseGridInferenceSolver<Double>(
            AdaptiveSparseGridSolverConfig(
                retainedPosteriorMass = 0.9995,
                minimumRetainedNodes = 15,
                maximumRetainedNodes = 61,
            ),
        ).fullReplay(problem, observations)

        assertTrue(sparse.nodes.size < dense.nodes.size)
        listOf(0.05, 0.50, 0.95).forEach { probability ->
            assertTrue(abs(quantile(dense, probability) - quantile(sparse, probability)) <= 0.05)
        }
        assertTrue(abs(mean(dense) - mean(sparse)) <= 0.025)
        assertTrue(abs(tailAbove(dense, 0.6) - tailAbove(sparse, 0.6)) <= 0.01)
    }

    @Test
    fun `positive and negative state movement remain identifiable`() {
        val problem = GaussianRandomWalkProblem()
        val solver = DenseSequentialGridInferenceSolver<Double>()
        val positive = solver.fullReplay(
            problem,
            listOf(
                observation("p1", 1, 0.0),
                observation("p2", 2, 0.25),
                observation("p3", 3, 0.50),
            ),
        )
        val negative = solver.fullReplay(
            problem,
            listOf(
                observation("n1", 1, 0.0),
                observation("n2", 2, -0.25),
                observation("n3", 3, -0.50),
            ),
        )
        assertTrue(mean(positive) > 0.20)
        assertTrue(mean(negative) < -0.20)
    }

    private inner class GaussianRandomWalkProblem : FixedGridDynamicInferenceProblem<Double> {
        override val mathematicalModelIdentity = InferenceMathematicalModelIdentity(
            family = "solver_test_random_walk",
            semanticVersion = "v1",
            definition = "state_k=state_k-1+Normal(0,0.12);obs=state+Normal(0,0.10)",
        )
        override val parameterNames = listOf("stateLevel")
        override val grid = List(121) { index ->
            val value = -1.5 + index * 0.025
            InferenceGridPoint("g$index", listOf(value))
        }

        override fun initialLogPrior(point: InferenceGridPoint, horizon: Instant): Double =
            normalLogDensity(point.coordinates.single(), 0.0, 0.45)

        override fun logTransitionDensity(
            from: InferenceGridPoint,
            to: InferenceGridPoint,
            elapsed: Duration,
        ): Double {
            val days = elapsed.toMillis().toDouble() / Duration.ofDays(1).toMillis().toDouble()
            val sd = 0.12 * kotlin.math.sqrt(maxOf(days, 1e-6))
            return normalLogDensity(to.coordinates.single(), from.coordinates.single(), sd)
        }

        override fun logLikelihood(point: InferenceGridPoint, observation: TimedInferenceObservation<Double>): Double =
            normalLogDensity(observation.value, point.coordinates.single(), 0.10)
    }

    private fun observations() = listOf(
        observation("o1", 1, -0.05),
        observation("o2", 2, 0.10),
        observation("o3", 3, 0.18),
        observation("o4", 4, 0.32),
        observation("o5", 5, 0.38),
    )

    private fun observation(id: String, day: Long, value: Double) = TimedInferenceObservation(
        id = id,
        observedAt = BASE.plusSeconds(day * 86_400L),
        value = value,
    )

    private fun mean(posterior: GridInferencePosterior): Double =
        posterior.nodes.sumOf { it.point.coordinates.single() * it.posteriorWeight }

    private fun quantile(posterior: GridInferencePosterior, probability: Double): Double {
        val ordered = posterior.nodes.sortedBy { it.point.coordinates.single() }
        var cumulative = 0.0
        ordered.forEach { node ->
            cumulative += node.posteriorWeight
            if (cumulative >= probability) return node.point.coordinates.single()
        }
        return ordered.last().point.coordinates.single()
    }

    private fun tailAbove(posterior: GridInferencePosterior, threshold: Double): Double =
        posterior.nodes.filter { it.point.coordinates.single() >= threshold }.sumOf { it.posteriorWeight }

    private fun normalLogDensity(value: Double, mean: Double, sd: Double): Double {
        val z = (value - mean) / sd
        return -0.5 * z.pow(2) - ln(sd) - 0.5 * ln(2.0 * PI)
    }

    companion object {
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
    }
}
