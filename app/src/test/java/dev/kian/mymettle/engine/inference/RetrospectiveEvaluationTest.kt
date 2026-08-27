package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceExecutionMode
import dev.kian.mymettle.domain.inference.PosteriorSummary
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.QuantityDimension
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RetrospectiveEvaluationTest {
    @Test
    fun `chronological evaluator never trains on the session it predicts`() {
        val observations = listOf(
            observation("a1", "a", "2026-08-01T10:00:00Z", 50.0),
            observation("a2", "a", "2026-08-01T10:05:00Z", 52.0),
            observation("b1", "b", "2026-08-05T10:00:00Z", 55.0),
        )
        val result = evaluator().evaluate(
            observations,
            InferenceExecutionMode.BENCHMARK_V0,
            MetricFamily.DYNAMIC_RESISTANCE,
            QuantityDimension.MASS,
        ) { training, _ -> posterior(training.observations.lastOrNull()?.observedCanonical ?: 0.0) }

        result.predictions.forEach { prediction ->
            assertFalse(prediction.holdout.observationId in prediction.trainingObservationIds)
            assertFalse(prediction.holdout.sessionId in prediction.trainingSessionIds)
        }
        assertTrue(result.predictions.filter { it.holdout.sessionId == "a" }.all { it.trainingObservationIds.isEmpty() })
        assertEquals(setOf("a1", "a2"), result.predictions.single { it.holdout.observationId == "b1" }.trainingObservationIds)
    }

    @Test
    fun `later evidence cannot leak into an earlier prediction`() {
        val observations = listOf(
            observation("later", "session_c", "2026-08-10T10:00:00Z", 70.0),
            observation("earlier", "session_a", "2026-08-01T10:00:00Z", 50.0),
            observation("middle", "session_b", "2026-08-05T10:00:00Z", 60.0),
        )
        val result = evaluator().evaluate(
            observations,
            InferenceExecutionMode.SHADOW,
            MetricFamily.DYNAMIC_RESISTANCE,
            QuantityDimension.MASS,
        ) { training, _ -> posterior(training.observations.size.toDouble()) }

        val earlier = result.predictions.single { it.holdout.observationId == "earlier" }
        val middle = result.predictions.single { it.holdout.observationId == "middle" }
        assertTrue(earlier.trainingObservationIds.isEmpty())
        assertEquals(setOf("earlier"), middle.trainingObservationIds)
        assertFalse("later" in middle.trainingObservationIds)
    }

    @Test
    fun `benchmark and candidate evaluation results coexist`() {
        val observations = listOf(
            observation("a", "session_a", "2026-08-01T10:00:00Z", 50.0),
            observation("b", "session_b", "2026-08-05T10:00:00Z", 55.0),
        )
        val benchmark = evaluator().evaluate(
            observations,
            InferenceExecutionMode.BENCHMARK_V0,
            MetricFamily.DYNAMIC_RESISTANCE,
            QuantityDimension.MASS,
        ) { _, _ -> posterior(50.0) }
        val candidate = evaluator().evaluate(
            observations,
            InferenceExecutionMode.CANDIDATE_V7,
            MetricFamily.DYNAMIC_RESISTANCE,
            QuantityDimension.MASS,
        ) { _, _ -> posterior(54.0) }

        assertEquals(InferenceExecutionMode.BENCHMARK_V0, benchmark.executionMode)
        assertEquals(InferenceExecutionMode.CANDIDATE_V7, candidate.executionMode)
        assertEquals(2, benchmark.predictions.size)
        assertEquals(2, candidate.predictions.size)
    }

    @Test
    fun `family and physical dimension cannot be accidentally aggregated`() {
        val kg = observation("kg", "session_a", "2026-08-01T10:00:00Z", 50.0)
        val seconds = RetrospectiveObservation(
            observationId = "seconds",
            sessionId = "session_b",
            observedAt = Instant.parse("2026-08-02T10:00:00Z"),
            metricFamily = MetricFamily.DURATION_ONLY,
            dimension = QuantityDimension.TIME,
            observedCanonical = 40.0,
        )

        assertFailsWith<IllegalArgumentException> {
            evaluator().evaluate(
                listOf(kg, seconds),
                InferenceExecutionMode.BENCHMARK_V0,
                MetricFamily.DYNAMIC_RESISTANCE,
                QuantityDimension.MASS,
            ) { _, _ -> posterior(1.0) }
        }
    }

    private fun evaluator() = ChronologicalRetrospectiveEvaluator()

    private fun observation(id: String, session: String, at: String, value: Double) = RetrospectiveObservation(
        observationId = id,
        sessionId = session,
        observedAt = Instant.parse(at),
        metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        dimension = QuantityDimension.MASS,
        observedCanonical = value,
    )

    private fun posterior(median: Double) = PosteriorSummary(
        credibleLower05 = median - 5.0,
        estimateMedian = median,
        credibleUpper95 = median + 5.0,
        posteriorVariance = 9.0,
    )
}
