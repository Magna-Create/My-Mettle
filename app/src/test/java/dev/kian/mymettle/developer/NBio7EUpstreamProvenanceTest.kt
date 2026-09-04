package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.TemporalStateConfigV1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class NBio7EUpstreamProvenanceTest {
    @Test
    fun `execution config is deterministic and changes with temporal behaviour`() {
        val provenance = validProvenance()
        val first = NBio7EExecutionConfigV2.definition(TemporalStateConfigV1(), provenance)
        val replay = NBio7EExecutionConfigV2.definition(TemporalStateConfigV1(), provenance)
        val changed = NBio7EExecutionConfigV2.definition(
            TemporalStateConfigV1(observationVariance = 0.02),
            provenance,
        )

        assertEquals(first.id, replay.id)
        assertEquals(first.canonicalConfigPayload, replay.canonicalConfigPayload)
        assertNotEquals(first.id, changed.id)
    }

    @Test
    fun `unknown 7D config identity fails closed`() {
        assertFailsWith<IllegalArgumentException> {
            validProvenance().copy(doseConfigIds = mapOf("session_dose" to "unknown"))
        }
    }

    private fun validProvenance() = NBio7EUpstreamProvenanceV1(
        capabilityModelIdentity = NBioCorrectedCandidateV2Bundle.mathematicalModelIdentity.identity,
        capabilitySolverIdentity = NBioCorrectedCandidateV2Bundle.sparseSolver().solverIdentity.identity,
        capabilityEvidencePolicyIdentity = NBioCorrectedCandidateV2Bundle.evidencePolicy.identity,
        capabilityEvaluationProtocol = "fixture-protocol",
        doseSourceMode = NBio7EUpstreamProvenanceV1.DOSE_SOURCE_MODE,
        doseModelIdentities = NBio7EUpstreamProvenanceV1.expectedDoseModelIdentities,
        doseConfigIds = NBio7EUpstreamProvenanceV1.expectedDoseConfigIds,
        doseEligibleSessions = 3,
    )
}
