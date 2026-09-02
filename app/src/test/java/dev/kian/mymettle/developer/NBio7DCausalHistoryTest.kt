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
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NBio7DCausalHistoryTest {
    @Test
    fun `pre-session baseline excludes same-session sets and corrections learned after session start`() {
        val priorOriginal = revision(
            observationId = "prior_old",
            sessionId = "prior",
            setCompletedAt = "2026-01-01T10:30:00Z",
            sessionCompletedAt = "2026-01-01T11:00:00Z",
            recordedAt = "2026-01-01T11:00:00Z",
            load = 50.0,
        )
        val priorCorrection = revision(
            observationId = "prior_new",
            sessionId = "prior",
            setCompletedAt = "2026-01-01T10:30:00Z",
            sessionCompletedAt = "2026-01-01T11:00:00Z",
            recordedAt = "2026-01-03T13:00:00Z",
            load = 55.0,
            supersedes = "prior_old",
        )
        val target = revision(
            observationId = "target_old",
            sessionId = "target",
            setCompletedAt = "2026-01-03T12:30:00Z",
            sessionCompletedAt = "2026-01-03T13:30:00Z",
            recordedAt = "2026-01-03T12:30:00Z",
            load = 60.0,
        )
        val targetCorrection = revision(
            observationId = "target_new",
            sessionId = "target",
            setCompletedAt = "2026-01-03T12:30:00Z",
            sessionCompletedAt = "2026-01-03T13:30:00Z",
            recordedAt = "2026-01-04T09:00:00Z",
            load = 62.0,
            supersedes = "target_old",
        )

        val slice = NBio7DCausalHistory.slice(
            revisions = listOf(priorOriginal, priorCorrection, target, targetCorrection),
            targetSessionId = "target",
            targetStartedAt = Instant.parse("2026-01-03T12:00:00Z"),
            replayKnowledgeAt = Instant.parse("2026-01-05T00:00:00Z"),
        )

        assertEquals(listOf("prior_old"), slice.training.map { it.observationId })
        assertEquals(listOf("target_new"), slice.target.map { it.observationId })
    }

    @Test
    fun `earlier correction becomes baseline evidence once a later target starts after it is knowable`() {
        val original = revision(
            observationId = "prior_old",
            sessionId = "prior",
            setCompletedAt = "2026-01-01T10:30:00Z",
            sessionCompletedAt = "2026-01-01T11:00:00Z",
            recordedAt = "2026-01-01T11:00:00Z",
            load = 50.0,
        )
        val correction = revision(
            observationId = "prior_new",
            sessionId = "prior",
            setCompletedAt = "2026-01-01T10:30:00Z",
            sessionCompletedAt = "2026-01-01T11:00:00Z",
            recordedAt = "2026-01-03T13:00:00Z",
            load = 55.0,
            supersedes = "prior_old",
        )
        val target = revision(
            observationId = "target",
            sessionId = "later",
            setCompletedAt = "2026-01-05T10:30:00Z",
            sessionCompletedAt = "2026-01-05T11:00:00Z",
            recordedAt = "2026-01-05T10:30:00Z",
            load = 60.0,
        )

        val slice = NBio7DCausalHistory.slice(
            revisions = listOf(original, correction, target),
            targetSessionId = "later",
            targetStartedAt = Instant.parse("2026-01-05T10:00:00Z"),
            replayKnowledgeAt = Instant.parse("2026-01-05T12:00:00Z"),
        )

        assertEquals(listOf("prior_new"), slice.training.map { it.observationId })
    }

    @Test
    fun `session completed exactly at target start is not prior evidence`() {
        val simultaneous = revision(
            observationId = "simultaneous",
            sessionId = "prior",
            setCompletedAt = "2026-01-03T11:30:00Z",
            sessionCompletedAt = "2026-01-03T12:00:00Z",
            recordedAt = "2026-01-03T12:00:00Z",
            load = 50.0,
        )
        val target = revision(
            observationId = "target",
            sessionId = "target",
            setCompletedAt = "2026-01-03T12:30:00Z",
            sessionCompletedAt = "2026-01-03T13:00:00Z",
            recordedAt = "2026-01-03T12:30:00Z",
            load = 60.0,
        )
        val slice = NBio7DCausalHistory.slice(
            listOf(simultaneous, target),
            "target",
            Instant.parse("2026-01-03T12:00:00Z"),
            Instant.parse("2026-01-03T14:00:00Z"),
        )
        assertEquals(emptyList(), slice.training)
    }

    @Test
    fun `replay horizon cannot precede target start`() {
        val target = revision(
            observationId = "target",
            sessionId = "target",
            setCompletedAt = "2026-01-03T12:30:00Z",
            sessionCompletedAt = "2026-01-03T13:00:00Z",
            recordedAt = "2026-01-03T12:30:00Z",
            load = 60.0,
        )
        assertFailsWith<IllegalArgumentException> {
            NBio7DCausalHistory.slice(
                listOf(target),
                "target",
                Instant.parse("2026-01-03T12:00:00Z"),
                Instant.parse("2026-01-03T11:59:59Z"),
            )
        }
    }

    private fun revision(
        observationId: String,
        sessionId: String,
        setCompletedAt: String,
        sessionCompletedAt: String,
        recordedAt: String,
        load: Double,
        supersedes: String? = null,
    ) = HistoricalCompletedSetEvidenceRevision(
        evidence = CompletedSetEvidence(
            setRecordId = "set_$observationId",
            observationId = observationId,
            sessionExerciseId = "se_$sessionId",
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            laterality = Laterality.BILATERAL,
            completedAt = Instant.parse(setCompletedAt),
            metricValues = listOf(
                PerformanceMetricValue(
                    PerformanceMetric.EXTERNAL_LOAD,
                    Quantity(load, UnitId.KILOGRAM),
                    Quantity(load, UnitId.KILOGRAM),
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
            sessionId = sessionId,
        ),
        recordedAt = Instant.parse(recordedAt),
        sessionCompletedAt = Instant.parse(sessionCompletedAt),
        supersedesObservationId = supersedes,
    )
}
