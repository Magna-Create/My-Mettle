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

class NBio7DHistoricalReplayPlannerEligibilityTest {
    @Test
    fun `prescribed and additional performed sets are eligible while warmups remain excluded`() {
        val prior = revision(
            observationId = "prior_prescribed",
            sessionId = "prior",
            completedAt = "2026-01-01T10:30:00Z",
            sessionCompletedAt = "2026-01-01T11:00:00Z",
            kind = "prescribed",
        )
        val targetAdditional = revision(
            observationId = "target_additional",
            sessionId = "target",
            completedAt = "2026-01-03T12:20:00Z",
            sessionCompletedAt = "2026-01-03T13:00:00Z",
            kind = "additional",
        )
        val targetWarmup = revision(
            observationId = "target_warmup",
            sessionId = "target",
            completedAt = "2026-01-03T12:10:00Z",
            sessionCompletedAt = "2026-01-03T13:00:00Z",
            kind = "warm_up",
            warmUp = true,
        )

        val inputs = NBio7DHistoricalInputs(
            sessions = mapOf(
                "prior" to NBio7DHistoricalSession(
                    "prior",
                    Instant.parse("2026-01-01T10:00:00Z"),
                    Instant.parse("2026-01-01T11:00:00Z"),
                ),
                "target" to NBio7DHistoricalSession(
                    "target",
                    Instant.parse("2026-01-03T12:00:00Z"),
                    Instant.parse("2026-01-03T13:00:00Z"),
                ),
            ),
            observations = listOf(prior, targetAdditional, targetWarmup).associate { revision ->
                val evidence = revision.evidence
                evidence.observationId to NBio7DObservationContext(
                    observationId = evidence.observationId,
                    sessionId = evidence.sessionId,
                    executionProfileVersionId = evidence.executionProfileVersionId.value,
                    recruitmentProfileVersionId = "recruitment:v1",
                    side = evidence.laterality.storageValue,
                    completedAt = evidence.completedAt,
                    exposures = emptyList(),
                )
            },
        )

        val plan = NBio7DHistoricalReplayPlanner.plan(
            dynamicHistory = NBio7BRawHistory(
                revisions = listOf(prior, targetAdditional, targetWarmup),
                profiles = emptyMap(),
            ),
            nonDynamicHistory = NBio7CRawHistory(emptyList(), emptyMap()),
            inputs = inputs,
            replayKnowledgeAt = Instant.parse("2026-01-04T00:00:00Z"),
        )

        val targetPlan = plan.sessions.single { it.session.sessionId == "target" }
        assertEquals(listOf("target_additional"), targetPlan.sets.map { it.target.observationId })
        assertEquals(listOf("prior_prescribed"), targetPlan.sets.single().preSessionTrainingEvidence.map { it.observationId })
        assertEquals(mapOf("warm_up" to 1), plan.skippedTargetReasonCounts)
    }

    private fun revision(
        observationId: String,
        sessionId: String,
        completedAt: String,
        sessionCompletedAt: String,
        kind: String,
        warmUp: Boolean = false,
    ): HistoricalCompletedSetEvidenceRevision {
        val completion = Instant.parse(completedAt)
        return HistoricalCompletedSetEvidenceRevision(
            evidence = CompletedSetEvidence(
                setRecordId = "set_$observationId",
                observationId = observationId,
                sessionExerciseId = "se_$sessionId",
                executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
                metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
                laterality = Laterality.BILATERAL,
                completedAt = completion,
                metricValues = listOf(
                    PerformanceMetricValue(
                        metric = PerformanceMetric.EXTERNAL_LOAD,
                        entered = Quantity(50.0, UnitId.KILOGRAM),
                        canonical = Quantity(50.0, UnitId.KILOGRAM),
                    ),
                    PerformanceMetricValue(
                        metric = PerformanceMetric.REPETITIONS,
                        entered = Quantity(8.0, UnitId.REPETITION),
                        canonical = Quantity(8.0, UnitId.REPETITION),
                    ),
                ),
                bodyMassContextKg = null,
                warmUp = warmUp,
                kind = kind,
                sessionId = sessionId,
            ),
            recordedAt = completion,
            sessionCompletedAt = Instant.parse(sessionCompletedAt),
            supersedesObservationId = null,
        )
    }
}
