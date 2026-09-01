package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class NBio7CRevisionSemanticsTest {
    @Test
    fun `later correction cannot leak backwards and revision selection is input-order deterministic`() {
        val original = evidence("original", 30.0)
        val corrected = evidence("corrected", 42.0)
        val originalRevision = HistoricalCompletedSetEvidenceRevision(
            evidence = original,
            recordedAt = Instant.parse("2026-01-01T13:00:00Z"),
            sessionCompletedAt = SESSION_COMPLETE,
            supersedesObservationId = null,
        )
        val correctionRevision = HistoricalCompletedSetEvidenceRevision(
            evidence = corrected,
            recordedAt = Instant.parse("2026-01-10T12:00:00Z"),
            sessionCompletedAt = SESSION_COMPLETE,
            supersedesObservationId = original.observationId,
        )
        val revisions = listOf(originalRevision, correctionRevision)

        assertEquals(
            listOf("original"),
            HistoricalObservationRevisionSelector.currentAsOf(revisions, Instant.parse("2026-01-05T12:00:00Z"))
                .map { it.observationId },
        )
        assertEquals(
            listOf("corrected"),
            HistoricalObservationRevisionSelector.currentAsOf(revisions, Instant.parse("2026-01-11T12:00:00Z"))
                .map { it.observationId },
        )
        assertEquals(
            HistoricalObservationRevisionSelector.currentAsOf(revisions, Instant.parse("2026-01-11T12:00:00Z")),
            HistoricalObservationRevisionSelector.currentAsOf(revisions.reversed(), Instant.parse("2026-01-11T12:00:00Z")),
        )
    }

    private fun evidence(id: String, duration: Double) = CompletedSetEvidence(
        setRecordId = "set_1",
        observationId = id,
        sessionExerciseId = "se_1",
        executionProfileVersionId = ExecutionProfileVersionId("epv_duration_revision_v1"),
        metricFamily = MetricFamily.DURATION_ONLY,
        laterality = Laterality.UNKNOWN,
        completedAt = Instant.parse("2026-01-01T12:30:00Z"),
        metricValues = listOf(
            PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(duration, UnitId.SECOND)),
        ),
        bodyMassContextKg = null,
        warmUp = false,
        kind = "work",
        observationSource = "corrected_lite_import",
        sessionId = "session_1",
    )

    private companion object {
        val SESSION_COMPLETE: Instant = Instant.parse("2026-01-01T13:00:00Z")
    }
}
