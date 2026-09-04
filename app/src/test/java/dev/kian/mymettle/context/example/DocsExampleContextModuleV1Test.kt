package dev.kian.mymettle.context.example

import dev.kian.mymettle.context.ContextModuleContractTckV1
import dev.kian.mymettle.domain.context.ContextEvidenceMissingness
import dev.kian.mymettle.domain.context.ContextEvidenceSourceKind
import dev.kian.mymettle.domain.context.ContextFeatureEvidenceV7E
import dev.kian.mymettle.domain.context.ContextFeatureValueV7E
import dev.kian.mymettle.domain.context.ContextModulePhase
import dev.kian.mymettle.domain.context.ContextModuleRegistryV7E
import dev.kian.mymettle.domain.context.ContextModuleRuntimeV7E
import dev.kian.mymettle.domain.context.ContextReadViewV1
import dev.kian.mymettle.domain.context.ContextScope
import dev.kian.mymettle.domain.context.ContextScopeKind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocsExampleContextModuleV1Test {
    private val at = Instant.parse("2026-01-01T12:00:00Z")
    private val sessionScope = ContextScope(ContextScopeKind.SESSION, "docs-session")

    @Test
    fun `provider passes the Context Module contract kit`() {
        assertEquals(9, ContextModuleContractTckV1.verify(DocsExampleContextModuleProviderV1).checks.size)
    }

    @Test
    fun `example registers and returns a neutral candidate signal`() {
        val registry = ContextModuleRegistryV7E(
            providers = listOf(DocsExampleContextModuleProviderV1),
            featureDefinitions = listOf(DocsExampleContextFeatureV1.definition),
        )
        val module = registry.modules.single()
        val evidence = evidence("docs-present", ContextEvidenceMissingness.PRESENT)
        val result = ContextModuleRuntimeV7E(registry).evaluate(emptyMap()) { descriptor ->
            ContextReadViewV1(
                phase = ContextModulePhase.PRE_SESSION_PUBLICATION,
                horizon = at,
                scope = sessionScope,
                grantedCapabilities = descriptor.requiredReadCapabilities,
                ownFeatureEvidence = listOf(evidence),
            )
        }

        assertTrue(result.failures.isEmpty())
        assertEquals(DocsExampleContextModuleV1.MODULE_ID, module.descriptor.moduleId)
        assertEquals(0.0, result.signals.single().locationMean)
        val state = result.states.getValue(module.descriptor.moduleId) as DocsExampleModuleStateV1
        assertEquals(1, state.presentRowCount)
        assertEquals(1, state.countedSessionKeys.size)
    }

    @Test
    fun `repeated and missing evidence do not create false support`() {
        val module = DocsExampleContextModuleV1()
        val present = evidence("docs-present", ContextEvidenceMissingness.PRESENT)
        val first = module.evaluate(module.initialState(), view(listOf(present)))
        val repeated = module.evaluate(first.state, view(listOf(present)))
        val missing = module.evaluate(repeated.state, view(listOf(evidence("docs-missing", ContextEvidenceMissingness.NOT_REPORTED))))

        val state = missing.state as DocsExampleModuleStateV1
        assertEquals(1, state.presentRowCount)
        assertEquals(0, state.knownFalseRowCount)
        assertTrue(missing.signals.isEmpty())
    }

    private fun view(evidence: List<ContextFeatureEvidenceV7E>) = ContextReadViewV1(
        phase = ContextModulePhase.PRE_SESSION_PUBLICATION,
        horizon = at,
        scope = sessionScope,
        grantedCapabilities = DocsExampleContextFeatureV1.definition.requiredReadCapabilities,
        ownFeatureEvidence = evidence,
    )

    private fun evidence(id: String, missingness: ContextEvidenceMissingness) = ContextFeatureEvidenceV7E(
        evidenceId = id,
        featureKey = DocsExampleContextFeatureV1.definition.key,
        value = if (missingness == ContextEvidenceMissingness.PRESENT) ContextFeatureValueV7E.BooleanValue(true) else null,
        missingness = missingness,
        scope = sessionScope,
        observedAt = at,
        effectiveUntil = at.plusSeconds(3_600),
        sourceKind = ContextEvidenceSourceKind.TEST_FIXTURE,
        sourceRevisionId = "revision:$id",
    )
}
