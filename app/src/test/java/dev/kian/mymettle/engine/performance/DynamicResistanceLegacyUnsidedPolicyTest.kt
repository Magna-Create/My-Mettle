package dev.kian.mymettle.engine.performance

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicPerformanceNoiseDistribution
import dev.kian.mymettle.domain.inference.DynamicResistanceExclusionReason
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicSlackDistribution
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierEvidenceV2
import dev.kian.mymettle.domain.inference.DynamicStochasticFrontierV1
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

class DynamicResistanceLegacyUnsidedPolicyTest {
    @Test
    fun `v1 remains closed to unknown laterality while v2 admits explicit legacy unknown stream`() {
        val item = evidence(source = DynamicResistanceV2Contract.LEGACY_UNSIDED_SOURCE)
        val v1 = DynamicResistanceEvidenceProjector.project(profile(), Laterality.UNKNOWN, listOf(item), DynamicResistanceV1Contract.evidencePolicy)
        val v2 = DynamicResistanceEvidenceProjector.project(profile(), Laterality.UNKNOWN, listOf(item), DynamicResistanceV2Contract.evidencePolicy)
        assertTrue(v1.evidence.isEmpty())
        assertEquals(DynamicResistanceExclusionReason.UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE, v1.exclusions.single().reason)
        assertEquals(1, v2.evidence.size)
        assertEquals(Laterality.UNKNOWN, v2.evidence.single().side)
        assertNotEquals(DynamicResistanceV1Contract.evidencePolicy.identity, DynamicResistanceV2Contract.evidencePolicy.identity)
    }

    @Test
    fun `native unknown is not admitted and unknown never enters a known-side stream`() {
        val nativeUnknown = DynamicResistanceEvidenceProjector.project(
  profile(), Laterality.UNKNOWN, listOf(evidence(source = "native_manual_entry")), DynamicResistanceV2Contract.evidencePolicy,
        )
        assertTrue(nativeUnknown.evidence.isEmpty())
        assertEquals(DynamicResistanceExclusionReason.UNKNOWN_LATERALITY_PROVENANCE_INELIGIBLE, nativeUnknown.exclusions.single().reason)

        val legacy = evidence(source = DynamicResistanceV2Contract.LEGACY_UNSIDED_SOURCE)
        listOf(Laterality.LEFT, Laterality.RIGHT, Laterality.BILATERAL).forEach { requested ->
  val projection = DynamicResistanceEvidenceProjector.project(profile(), requested, listOf(legacy), DynamicResistanceV2Contract.evidencePolicy)
  assertTrue(projection.evidence.isEmpty())
  assertEquals(DynamicResistanceExclusionReason.LATERALITY_INCOMPATIBLE, projection.exclusions.single().reason)
        }
    }

    @Test
    fun `frozen frontier maths is unchanged while active config binds evidence policy v2`() {
        val config = DynamicStochasticFrontierEvidenceV2.config
        assertEquals(DynamicResistanceV1Contract.evidencePolicy.identity, DynamicStochasticFrontierV1.config.evidencePolicyIdentity)
        assertEquals(DynamicResistanceV2Contract.evidencePolicy.identity, config.evidencePolicyIdentity)
        assertEquals(DynamicSlackDistribution.HALF_NORMAL, config.slackDistribution)
        assertEquals(DynamicPerformanceNoiseDistribution.STUDENT_T, config.noiseDistribution)
        assertEquals(5.0, config.studentTDegreesOfFreedom)
        assertEquals(0.16, config.slopePriorMedian)
        assertEquals(12, config.recentIndependentSessionWindow)
        assertTrue(config.contextConsumption.startsWith("NONE:"))
    }

    private fun profile() = DynamicResistanceProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("legacy_profile_v1"),
        executionProfileId = ExecutionProfileId("legacy_profile"),
        metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        resistanceModel = ResistanceModel("legacy-resistance-v1", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0),
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = LateralityMode.UNKNOWN,
    )

    private fun evidence(source: String) = CompletedSetEvidence(
        setRecordId = "set_legacy",
        observationId = "obs_legacy",
        sessionExerciseId = "session_exercise_legacy",
        executionProfileVersionId = ExecutionProfileVersionId("legacy_profile_v1"),
        metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
        laterality = Laterality.UNKNOWN,
        completedAt = Instant.parse("2026-08-01T10:00:00Z"),
        metricValues = listOf(
  PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(60.0, UnitId.KILOGRAM)),
  PerformanceMetricValue(PerformanceMetric.REPETITIONS, Quantity(8.0, UnitId.REPETITION)),
        ),
        bodyMassContextKg = null,
        warmUp = false,
        kind = "prescribed",
        observationSource = source,
        sessionId = "session_legacy",
    )
}
