package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceMathematicalModelIdentity
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class GaussianSigmaPointInferenceSolverTest {
    @Test
    fun `sigma point challenger tracks dense random walk posterior with far fewer likelihood evaluations`() {
        val problem = SharedRandomWalkProblem()
        val observations = observations()
        val dense = DenseSequentialGridInferenceSolver<Double>().fullReplay(problem, observations)
        val gaussian = GaussianSigmaPointInferenceSolver<Double>().fullReplay(problem, observations)
        val denseMean = dense.nodes.sumOf { it.point.coordinates.single() * it.posteriorWeight }
        val denseVariance = dense.nodes.sumOf {
            it.posteriorWeight * (it.point.coordinates.single() - denseMean).pow(2)
        }
        assertTrue(kotlin.math.abs(gaussian.mean.single() - denseMean) <= 0.12)
        assertTrue(kotlin.math.abs(sqrt(gaussian.covariance[0][0]) - sqrt(denseVariance)) <= 0.12)
        assertTrue(requireNotNull(gaussian.diagnostics.evaluatedNodeCount) < requireNotNull(dense.diagnostics.evaluatedNodeCount))
    }

    @Test
    fun `sigma point challenger follows negative trajectory without positive drift bias`() {
        val problem = SharedRandomWalkProblem()
        val result = GaussianSigmaPointInferenceSolver<Double>().fullReplay(
            problem,
            listOf(
                observation("n1", 1, 0.10),
                observation("n2", 2, -0.10),
                observation("n3", 3, -0.30),
                observation("n4", 4, -0.45),
            ),
        )
        assertTrue(result.mean.single() < -0.15)
    }

    /** One mathematical fixture implements both dense-grid and Gaussian-moment contracts. */
    private inner class SharedRandomWalkProblem :
        FixedGridDynamicInferenceProblem<Double>,
        GaussianMomentDynamicInferenceProblem<Double> {
        override val mathematicalModelIdentity = InferenceMathematicalModelIdentity(
            "solver_test_random_walk",
            "v2",
            "state_k=state_k-1+Normal(0,0.12);obs=state+Normal(0,0.10)",
        )
        override val parameterNames = listOf("stateLevel")
        override val stateNames = parameterNames
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
        ): Double = normalLogDensity(
            to.coordinates.single(),
            from.coordinates.single(),
            processSd(elapsed),
        )

        override fun logLikelihood(point: InferenceGridPoint, observation: TimedInferenceObservation<Double>): Double =
            logLikelihood(doubleArrayOf(point.coordinates.single()), observation)

        override fun initialMoments(horizon: Instant) = GaussianTransitionMoments(
            mean = doubleArrayOf(0.0),
            covariance = arrayOf(doubleArrayOf(0.45 * 0.45)),
        )

        override fun transitionMoments(
            previousMean: DoubleArray,
            previousCovariance: Array<DoubleArray>,
            elapsed: Duration,
        ) = GaussianTransitionMoments(
            mean = previousMean.copyOf(),
            covariance = arrayOf(doubleArrayOf(previousCovariance[0][0] + processSd(elapsed).pow(2))),
        )

        override fun logLikelihood(state: DoubleArray, observation: TimedInferenceObservation<Double>): Double =
            normalLogDensity(observation.value, state.single(), 0.10)

        private fun processSd(elapsed: Duration): Double {
            val days = elapsed.toMillis().toDouble() / Duration.ofDays(1).toMillis().toDouble()
            return 0.12 * sqrt(maxOf(days, 1e-6))
        }
    }

    private fun observations() = listOf(
        observation("o1", 1, -0.05),
        observation("o2", 2, 0.10),
        observation("o3", 3, 0.18),
        observation("o4", 4, 0.32),
        observation("o5", 5, 0.38),
    )

    private fun observation(id: String, day: Long, value: Double) = TimedInferenceObservation(
        id,
        BASE.plusSeconds(day * 86_400L),
        value,
    )

    private fun normalLogDensity(value: Double, mean: Double, sd: Double): Double {
        val z = (value - mean) / sd
        return -0.5 * z * z - ln(sd) - 0.5 * ln(2.0 * PI)
    }

    companion object {
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
    }
}
