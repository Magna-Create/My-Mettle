package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.performance.Laterality
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveInferenceContractsTest {
    @Test
    fun `absolute resistance and side capability cannot weakly pool`() {
        assertFailsWith<IllegalArgumentException> {
            HierarchicalPriorContract(
                parameterKind = HierarchicalParameterKind.ABSOLUTE_RESISTANCE_CAPABILITY,
                poolingMode = HierarchicalPoolingMode.USER_LEVEL_WEAK_POOLING,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            HierarchicalPriorContract(
                parameterKind = HierarchicalParameterKind.SIDE_SPECIFIC_CAPABILITY,
                poolingMode = HierarchicalPoolingMode.SEMANTIC_FAMILY_WEAK_POOLING,
                familyMappingVersion = "test-family-v1",
            )
        }
    }

    @Test
    fun `candidate statistical parameters may weakly pool only through explicit contract`() {
        val slope = HierarchicalPriorContract(
            parameterKind = HierarchicalParameterKind.REP_RANGE_SLOPE,
            poolingMode = HierarchicalPoolingMode.USER_LEVEL_WEAK_POOLING,
            priorSourceIdentity = "user-hyperposterior-v1",
        )
        assertEquals(HierarchicalPoolingMode.USER_LEVEL_WEAK_POOLING, slope.poolingMode)
        assertFailsWith<IllegalArgumentException> {
            HierarchicalPriorContract(
                parameterKind = HierarchicalParameterKind.OBSERVATION_VARIABILITY,
                poolingMode = HierarchicalPoolingMode.SEMANTIC_FAMILY_WEAK_POOLING,
            )
        }
    }

    @Test
    fun `statistical regime suspicion is derived and cannot become known boundary`() {
        val suspected = SemanticRegimeDecision(
            regimeId = "derived-regime-suspicion",
            executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
            side = Laterality.UNKNOWN,
            continuity = SemanticRegimeContinuity.DERIVED_DISCONTINUITY_SUSPECTED,
            source = SemanticBoundarySource.STATISTICAL_SUSPICION_ONLY,
            effectiveAt = Instant.EPOCH,
            derivedOnly = true,
            reason = "posterior discontinuity probability elevated",
        )
        assertTrue(suspected.derivedOnly)
        assertFailsWith<IllegalArgumentException> {
            suspected.copy(
                continuity = SemanticRegimeContinuity.KNOWN_BOUNDARY,
                derivedOnly = false,
            )
        }
    }

    @Test
    fun `metadata can create deterministic semantic boundary without rewriting evidence`() {
        val boundary = SemanticRegimeDecision(
            regimeId = "explicit-regime-2",
            executionProfileVersionId = ExecutionProfileVersionId("profile:v2"),
            side = Laterality.BILATERAL,
            continuity = SemanticRegimeContinuity.KNOWN_BOUNDARY,
            source = SemanticBoundarySource.EXPLICIT_MIGRATION_CORRECTION,
            effectiveAt = Instant.parse("2026-08-01T00:00:00Z"),
            derivedOnly = false,
            reason = "reviewed execution semantics changed",
        )
        assertEquals(SemanticRegimeContinuity.KNOWN_BOUNDARY, boundary.continuity)
    }

    @Test
    fun `action policy is structurally separate and fabricates no load reps or RIR proxy`() {
        val support = EvidenceSupport.empty(EvidenceFamily.DYNAMIC_RESISTANCE)
        val capability = PosteriorEstimate(
            summary = null,
            support = support,
            provenance = ModelOutputProvenance(
                modelConfigId = ModelConfigId("test-model"),
                modelManifestId = null,
                inferenceRunId = null,
                evidenceThrough = null,
            ),
        )
        val prediction = UnmodelledTrainingActionPolicy.predict(
            input = TrainingActionPolicyInput(
                executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
                side = Laterality.BILATERAL,
                inferenceHorizon = Instant.EPOCH,
            ),
            capability = capability,
        )
        assertNull(prediction.loadKg)
        assertNull(prediction.repetitions)
        assertTrue(prediction.modelIdentity.contains("unmodelled-v1"))
    }

    @Test
    fun `dependency invalidation reaches downstream derived nodes only`() {
        val index = InferenceDependencyIndex(
            listOf(
                InferenceDependencyNode("regime", "semantic_regime", setOf("raw_obs"), "regime-model", null),
                InferenceDependencyNode("capability", "capability", setOf("regime"), "model-v2", "solver-dense"),
                InferenceDependencyNode("prediction", "prediction", setOf("capability"), "policy-unmodelled", null),
                InferenceDependencyNode("unrelated", "capability", setOf("other_raw"), "other-model", "solver-dense"),
            ),
        )
        assertEquals(setOf("regime", "capability", "prediction"), index.invalidatedBy("raw_obs"))
        assertTrue("unrelated" !in index.invalidatedBy("raw_obs"))
    }
}
