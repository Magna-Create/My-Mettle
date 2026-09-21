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

class NBio7FInstalledHistoryEvaluatorTest {
    @Test
    fun `future revision cannot create a historical destination event`() {
        val completedAt = Instant.parse("2026-01-01T10:00:00Z")
        val sessionCompletedAt = Instant.parse("2026-01-01T11:00:00Z")
        val session = NBio7DHistoricalSession(
            sessionId = SESSION_ID,
            startedAt = Instant.parse("2026-01-01T09:00:00Z"),
            completedAt = sessionCompletedAt,
        )
        val original = revision(
            observationId = "observation-original",
            profileVersionId = "profile-v1",
            completedAt = completedAt,
            recordedAt = completedAt,
            sessionCompletedAt = sessionCompletedAt,
            supersedesObservationId = null,
        )
        val futureCorrection = revision(
            observationId = "observation-future-correction",
            profileVersionId = "profile-v2",
            completedAt = completedAt,
            recordedAt = Instant.parse("2026-01-03T10:00:00Z"),
            sessionCompletedAt = sessionCompletedAt,
            supersedesObservationId = original.evidence.observationId,
        )

        val seeds = NBio7FInstalledHistoryEvaluator.planDestinationSeeds(
            revisions = listOf(original, futureCorrection),
            sessions = mapOf(SESSION_ID to session),
        )

        assertEquals(1, seeds.size)
        assertEquals("profile-v1", seeds.single().executionProfileVersionId)
        assertEquals(Laterality.BILATERAL, seeds.single().side)
        assertEquals(completedAt, seeds.single().firstObservationTime)
        assertEquals(sessionCompletedAt, seeds.single().outcomeKnowledgeAt)
    }

    private fun revision(
        observationId: String,
        profileVersionId: String,
        completedAt: Instant,
        recordedAt: Instant,
        sessionCompletedAt: Instant,
        supersedesObservationId: String?,
    ) = HistoricalCompletedSetEvidenceRevision(
        evidence = CompletedSetEvidence(
            setRecordId = "set-record-1",
            observationId = observationId,
            sessionExerciseId = "session-exercise-1",
            executionProfileVersionId = ExecutionProfileVersionId(profileVersionId),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            laterality = Laterality.BILATERAL,
            completedAt = completedAt,
            metricValues = listOf(
                PerformanceMetricValue(
                    metric = PerformanceMetric.EXTERNAL_LOAD,
                    entered = Quantity(60.0, UnitId.KILOGRAM),
                ),
            ),
            bodyMassContextKg = null,
            warmUp = false,
            kind = "working",
            observationSource = "native",
            sessionId = SESSION_ID,
        ),
        recordedAt = recordedAt,
        sessionCompletedAt = sessionCompletedAt,
        supersedesObservationId = supersedesObservationId,
    )

    private companion object {
        const val SESSION_ID = "session-1"
    }
}
