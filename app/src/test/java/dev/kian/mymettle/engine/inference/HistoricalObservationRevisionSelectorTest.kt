package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoricalObservationRevisionSelectorTest {
    @Test
    fun `supersession is resolved before laterality grouping`() {
        val original = revision("old", Laterality.LEFT, "2026-01-01T10:00:00Z", null)
        val corrected = revision("new", Laterality.RIGHT, "2026-01-03T10:00:00Z", original.evidence.observationId)
        val current = HistoricalObservationRevisionSelector.currentAsOf(
            listOf(original, corrected),
            Instant.parse("2026-01-04T00:00:00Z"),
        )
        assertEquals(listOf("obs_new"), current.map { it.observationId })
        assertEquals(listOf(Laterality.RIGHT), current.map { it.laterality })
    }

    private fun revision(
        id: String,
        side: Laterality,
        recordedAt: String,
        supersedes: String?,
    ): HistoricalCompletedSetEvidenceRevision {
        val completedAt = Instant.parse("2026-01-01T09:00:00Z")
        return HistoricalCompletedSetEvidenceRevision(
            evidence = CompletedSetEvidence(
                setRecordId = "set_1",
                observationId = "obs_$id",
                sessionExerciseId = "se_1",
                executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
                metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
                laterality = side,
                completedAt = completedAt,
                metricValues = listOf(
                    PerformanceMetricValue(
                        PerformanceMetric.EXTERNAL_LOAD,
                        Quantity(60.0, UnitId.KILOGRAM),
                        Quantity(60.0, UnitId.KILOGRAM),
                    ),
                    PerformanceMetricValue(
                        PerformanceMetric.REPETITIONS,
                        Quantity(8.0, UnitId.REPETITION),
                        Quantity(8.0, UnitId.REPETITION),
                    ),
                ),
                bodyMassContextKg = null,
                warmUp = false,
                kind = "working",
                sessionId = "session_1",
            ),
            recordedAt = Instant.parse(recordedAt),
            sessionCompletedAt = Instant.parse("2026-01-01T11:00:00Z"),
            supersedesObservationId = supersedes,
        )
    }
}
