package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicPerformanceNoiseDistribution
import dev.kian.mymettle.domain.inference.DynamicResistanceExclusionReason
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicResistanceV3Contract
import dev.kian.mymettle.domain.inference.DynamicSlackDistribution
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierEvidenceV3
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierV1
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierEvidenceV3
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierV2
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
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class DynamicResistanceCorrectedLegacyPolicyTest {
    @Test
    fun `v2 remains immutable while v3 admits corrected Lite unknown provenance`() {
        val item = evidence(source = DynamicResistanceV3Contract.CORRECTED_LEGACY_UNSIDED_SOURCE)
        val v2 = DynamicResistanceEvidenceProjector.project(
            profile(),
            Laterality.UNKNOWN,
            listOf(item),
            DynamicResistanceV2Contract.evidencePolicy,
        )
        val v3 = DynamicResistanceEvidenceProjector.project(
            profile(),
            Laterality.UNKNOWN,
            listOf(item),
            DynamicResistanceV3Contract.evidencePolicy,
        )

        assertTrue(v2.evidence.isEmpty())
        assertEquals(
            DynamicResistanceExclusionReason.UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE,
            v2.exclusions.single().reason,
        )
        assertEquals(1, v3.evidence.size)
        assertEquals(Laterality.UNKNOWN, v3.evidence.single().side)
        assertNotEquals(DynamicResistanceV2Contract.evidencePolicy.identity, DynamicResistanceV3Contract.evidencePolicy.identity)
    }

    @Test
    fun `v3 still rejects native unknown and never leaks corrected unknown into known sides`() {
        val native = DynamicResistanceEvidenceProjector.project(
            profile(),
            Laterality.UNKNOWN,
            listOf(evidence(source = "native_manual_entry")),
            DynamicResistanceV3Contract.evidencePolicy,
        )
        assertTrue(native.evidence.isEmpty())
        assertEquals(
            DynamicResistanceExclusionReason.UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE,
            native.exclusions.single().reason,
        )

        val corrected = evidence(source = DynamicResistanceV3Contract.CORRECTED_LEGACY_UNSIDED_SOURCE)
        listOf(Laterality.LEFT, Laterality.RIGHT, Laterality.BILATERAL).forEach { requested ->
            val projection = DynamicResistanceEvidenceProjector.project(
                profile(),
                requested,
                listOf(corrected),
                DynamicResistanceV3Contract.evidencePolicy,
            )
            assertTrue(projection.evidence.isEmpty())
            assertEquals(
                DynamicResistanceExclusionReason.LATERALITY_INCOMPATIBLE,
                projection.exclusions.single().reason,
            )
        }
    }

    @Test
    fun `corrected evidence rebinding preserves frozen frontier and trend mathematics`() {
        val base = DynamicStochasticFrontierEvidenceV3.config
        assertEquals(DynamicResistanceV3Contract.evidencePolicy.identity, base.evidencePolicyIdentity)
        assertEquals(DynamicSlackDistribution.HALF_NORMAL, base.slackDistribution)
        assertEquals(DynamicPerformanceNoiseDistribution.STUDENT_T, base.noiseDistribution)
        assertEquals(DynamicStochasticFrontierV1.config.studentTDegreesOfFreedom, base.studentTDegreesOfFreedom)
        assertEquals(DynamicStochasticFrontierV1.config.slopePriorMedian, base.slopePriorMedian)
        assertEquals(DynamicStochasticFrontierV1.config.slackScalePriorMedian, base.slackScalePriorMedian)
        assertEquals(DynamicStochasticFrontierV1.config.noiseScalePriorMedian, base.noiseScalePriorMedian)
        assertEquals(DynamicStochasticFrontierV1.config.recentIndependentSessionWindow, base.recentIndependentSessionWindow)

        val trend = DynamicTrendFrontierEvidenceV3.config
        assertEquals(DynamicTrendFrontierV2.config.trendPriorSdLogResistancePerSession, trend.trendPriorSdLogResistancePerSession)
        assertEquals(DynamicTrendFrontierV2.config.trendMinimumIndependentSessionsToLearn, trend.trendMinimumIndependentSessionsToLearn)
        assertEquals(DynamicTrendFrontierV2.config.trendPosteriorQuadraturePoints, trend.trendPosteriorQuadraturePoints)
        assertEquals(DynamicResistanceV3Contract.evidencePolicy.identity, trend.evidencePolicyIdentity)
        assertNotEquals(
            DynamicTrendFrontierV2.mathematicalModelIdentity.identity,
            DynamicTrendFrontierEvidenceV3.mathematicalModelIdentity.identity,
        )
    }

    private fun profile() = DynamicResistanceProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("corrected_legacy_profile_v1"),
        executionProfileId = ExecutionProfileId("corrected_legacy_profile"),
        metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        resistanceModel = ResistanceModel(
            "corrected-legacy-resistance-v1",
            ResistanceSemantics.EXTERNAL,
            0.0,
            1.0,
            0.0,
        ),
        entryBasis = EntryBasis.PER_SIDE,
        lateralityMode = LateralityMode.UNKNOWN,
    )

    private fun evidence(source: String) = CompletedSetEvidence(
        setRecordId = "set_corrected_legacy",
        observationId = "obs_corrected_legacy",
        sessionExerciseId = "session_exercise_corrected_legacy",
        executionProfileVersionId = ExecutionProfileVersionId("corrected_legacy_profile_v1"),
        metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        laterality = Laterality.UNKNOWN,
        completedAt = Instant.parse("2026-08-16T12:16:49.491Z"),
        metricValues = listOf(
            PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(20.0, UnitId.KILOGRAM)),
            PerformanceMetricValue(PerformanceMetric.REPETITIONS, Quantity(6.0, UnitId.REPETITION)),
        ),
        bodyMassContextKg = null,
        warmUp = false,
        kind = "prescribed",
        observationSource = source,
        sessionId = "session_corrected_legacy",
    )
}
