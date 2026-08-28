package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DynamicResistanceHistoricalValidationTest {
    @Test
    fun `future correction cannot rewrite an earlier historical cutoff`() {
        val original = revision("original", "set_1", "s1", completedDay = 1, recordedDay = 1, sessionDay = 1, resistanceKg = 60.0)
        val correction = revision(
            "correction",
            "set_1",
            "s1",
            completedDay = 1,
            recordedDay = 10,
            sessionDay = 1,
            resistanceKg = 70.0,
            supersedes = original.evidence.observationId,
        )
        val before = HistoricalObservationRevisionSelector.currentAsOf(listOf(original, correction), day(5))
        val after = HistoricalObservationRevisionSelector.currentAsOf(listOf(original, correction), day(15))
        assertEquals(listOf(original.evidence.observationId), before.map { it.observationId })
        assertEquals(listOf(correction.evidence.observationId), after.map { it.observationId })
    }

    @Test
    fun `historical evaluator applies corrections only after recordedAt and holds out whole session`() {
        val original = revision("original", "set_1", "s1", 1, 1, 1, 60.0)
        val correction = revision(
            "correction",
            "set_1",
            "s1",
            1,
            10,
            1,
            70.0,
            supersedes = original.evidence.observationId,
        )
        val s2a = revision("s2a", "set_2a", "s2", 5, 5, 5, 64.0, reps = 8)
        val s2b = revision("s2b", "set_2b", "s2", 5, 5, 5, 57.0, reps = 12, seconds = 1)
        val s3 = revision("s3", "set_3", "s3", 15, 15, 15, 72.0)
        val result = DynamicResistanceHistoricalEvaluator().evaluate(
            profile = PROFILE,
            side = Laterality.BILATERAL,
            revisions = listOf(original, correction, s2a, s2b, s3),
        )

        val session2 = result.observations.filter { it.sessionId == "s2" }
        assertEquals(2, session2.size)
        session2.forEach { heldOut ->
            assertEquals(listOf(original.evidence.observationId), heldOut.trainingObservationIds)
            assertFalse(s2a.evidence.observationId in heldOut.trainingObservationIds)
            assertFalse(s2b.evidence.observationId in heldOut.trainingObservationIds)
        }

        val session3 = result.observations.single { it.sessionId == "s3" }
        assertTrue(correction.evidence.observationId in session3.trainingObservationIds)
        assertFalse(original.evidence.observationId in session3.trainingObservationIds)
        assertTrue(s2a.evidence.observationId in session3.trainingObservationIds)
        assertTrue(s2b.evidence.observationId in session3.trainingObservationIds)
    }

    private fun revision(
        id: String,
        setId: String,
        sessionId: String,
        completedDay: Int,
        recordedDay: Int,
        sessionDay: Int,
        resistanceKg: Double,
        reps: Int = 8,
        supersedes: String? = null,
        seconds: Long = 0,
    ): HistoricalCompletedSetEvidenceRevision {
        val completedAt = day(completedDay).plusSeconds(seconds)
        return HistoricalCompletedSetEvidenceRevision(
            evidence = CompletedSetEvidence(
                setRecordId = setId,
                observationId = "obs_$id",
                sessionExerciseId = "se_$sessionId",
                executionProfileVersionId = PROFILE.executionProfileVersionId,
                metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
                laterality = Laterality.BILATERAL,
                completedAt = completedAt,
                metricValues = listOf(
                    metric(PerformanceMetric.EXTERNAL_LOAD, resistanceKg, UnitId.KILOGRAM),
                    metric(PerformanceMetric.REPETITIONS, reps.toDouble(), UnitId.REPETITION),
                ),
                bodyMassContextKg = null,
                warmUp = false,
                kind = "working",
                sessionId = sessionId,
            ),
            recordedAt = day(recordedDay).plusSeconds(seconds),
            sessionCompletedAt = day(sessionDay).plusSeconds(3_600),
            supersedesObservationId = supersedes,
        )
    }

    private fun metric(metric: PerformanceMetric, value: Double, unit: UnitId) = PerformanceMetricValue(
        metric = metric,
        entered = Quantity(value, unit),
        canonical = Quantity(value, unit),
    )

    private fun day(value: Int): Instant = BASE.plusSeconds(value.toLong() * DAY_SECONDS)

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val PROFILE = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId("historical-profile:v1"),
            executionProfileId = ExecutionProfileId("historical-profile"),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "historical-test-resistance-v1",
                semantics = ResistanceSemantics.EXTERNAL,
                bodyweightCoefficient = 0.0,
                externalLoadCoefficient = 1.0,
                assistanceCoefficient = 0.0,
            ),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.BILATERAL_ONLY,
        )
    }
}
