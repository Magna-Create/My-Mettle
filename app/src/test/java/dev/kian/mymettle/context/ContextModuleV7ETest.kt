package dev.kian.mymettle.context

import dev.kian.mymettle.context.modules.EpisodeAssociationModuleProviderV1
import dev.kian.mymettle.context.modules.EpisodeAssociationModuleV1
import dev.kian.mymettle.context.modules.EpisodeAssociationStateCodecV2
import dev.kian.mymettle.context.modules.EpisodeAssociationStateV2
import dev.kian.mymettle.context.modules.ObservationVarianceAssociationModuleProviderV1
import dev.kian.mymettle.context.modules.ObservationVarianceAssociationModuleV1
import dev.kian.mymettle.context.modules.ObservationVarianceStateCodecV2
import dev.kian.mymettle.context.modules.ObservationVarianceStateV2
import dev.kian.mymettle.context.modules.ProductionContextFeaturesV7E
import dev.kian.mymettle.context.modules.ProductionContextModuleRegistryV7E
import dev.kian.mymettle.domain.context.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ContextModuleV7ETest {
    private val start = Instant.parse("2026-01-01T12:00:00Z")
    private val allCapabilities = ContextReadCapability.entries.toSet()

    @Test
    fun `two distinct learner families register behind one protocol`() {
        val registry = ContextModuleRegistryV7E(listOf(EpisodeAssociationModuleProviderV1, ObservationVarianceAssociationModuleProviderV1))
        assertEquals(2, registry.modules.size)
        assertNotEquals(registry.modules[0].descriptor.learnerFamily, registry.modules[1].descriptor.learnerFamily)
    }

    @Test
    fun `both production providers pass reusable module contract kit`() {
        ProductionContextModuleRegistryV7E.providers.forEach { provider ->
            assertEquals(9, ContextModuleContractTckV1.verify(provider).checks.size)
        }
    }

    @Test
    fun `duplicate module identity fails registration`() {
        assertFailsWith<IllegalArgumentException> {
            ContextModuleRegistryV7E(listOf(EpisodeAssociationModuleProviderV1, EpisodeAssociationModuleProviderV1))
        }
    }

    @Test
    fun `unknown module protocol fails closed`() {
        val badProvider = ContextModuleProviderV7E {
            object : ContextModuleV7E {
                override val descriptor = EpisodeAssociationModuleV1().descriptor.copy(moduleId = "bad.protocol", protocolVersion = 99)
                override val stateCodec = object : ContextModuleStateCodecV7E {
                    override val moduleId = "bad.protocol"
                    override val schemaVersion = 2
                    override fun encode(state: ContextModuleStateV7E) = ""
                    override fun decode(encoded: String) = object : ContextModuleStateV7E { override val ownerModuleId = moduleId }
                }
                override fun initialState() = object : ContextModuleStateV7E { override val ownerModuleId = "bad.protocol" }
                override fun evaluate(state: ContextModuleStateV7E, view: ContextReadViewV1) = ContextModuleResultV7E(state, emptyList())
            }
        }
        assertFailsWith<IllegalArgumentException> { ContextModuleRegistryV7E(listOf(badProvider)) }
    }

    @Test
    fun `module state codecs round trip independently`() {
        val episode = EpisodeAssociationStateV2(
            processedEvidenceIds = setOf("b,one", "a|two"), learnedEpisodeIds = setOf("episode:a,b|c"),
            countedSessionKeys = setOf("SESSION:session,a|b", "SESSION:session,c|d"), activeEpisodeId = "episode:a,b|c", episodeStartedAt = start,
            lastPositiveAt = start, lastEvidenceId = "a,b|c", evidenceRowCount = 2, independentSessionCount = 2,
            independentEpisodeCount = 1, associationMean = -0.04, associationVariance = 0.005,
        )
        assertEquals(episode, EpisodeAssociationStateCodecV2.decode(EpisodeAssociationStateCodecV2.encode(episode)))
        val variance = ObservationVarianceStateV2(
            processedEvidenceIds = setOf("x,one|two"), evidenceRowCount = 1, presentSessionCount = 1,
            presentSquaredResidualSum = 0.03, countedPresentSessionKeys = setOf("SESSION:s,1|2"),
            currentPresence = ContextEvidenceMissingness.PRESENT, lastEvidenceId = "x,one|two",
        )
        assertEquals(variance, ObservationVarianceStateCodecV2.decode(ObservationVarianceStateCodecV2.encode(variance)))
    }

    @Test
    fun `multiple evidence rows in one session count as one independent session`() {
        val module = EpisodeAssociationModuleV1()
        val sessionScope = ContextScope(ContextScopeKind.SESSION, "session-one")
        val rows = listOf(
            evidence("ill-row-a", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start, sessionScope),
            evidence("ill-row-b", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start.plusSeconds(1), sessionScope),
        )
        val state = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start.plusSeconds(1), rows, -0.1)).state as EpisodeAssociationStateV2
        assertEquals(2, state.evidenceRowCount)
        assertEquals(1, state.independentSessionCount)
        assertEquals(1, state.independentEpisodeCount)
    }

    @Test
    fun `variance learner does not count a session twice across incremental updates`() {
        val module = ObservationVarianceAssociationModuleV1()
        val sessionScope = ContextScope(ContextScopeKind.SESSION, "session-one")
        val first = evidence("pressure-a", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.PRESENT, start, sessionScope)
        var state = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(first), 0.1)).state
        val second = evidence("pressure-b", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.PRESENT, start.plusSeconds(1), sessionScope)
        state = module.evaluate(state, view(ContextModulePhase.POST_SESSION_UPDATE, start.plusSeconds(1), listOf(second), 0.2)).state
        state as ObservationVarianceStateV2
        assertEquals(2, state.evidenceRowCount)
        assertEquals(1, state.presentSessionCount)
        assertEquals(0.01, state.presentSquaredResidualSum, 1e-12)
    }

    @Test
    fun `missing legacy tag is not trained as false`() {
        val module = ObservationVarianceAssociationModuleV1()
        val evidence = evidence("missing", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.NOT_REPORTED, start)
        val result = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(evidence), residual = 0.2))
        val state = result.state as ObservationVarianceStateV2
        assertEquals(0, state.falseSessionCount)
        assertEquals(0, state.presentSessionCount)
        assertTrue(result.signals.isEmpty())
    }

    @Test
    fun `explicit false is a control observation`() {
        val module = ObservationVarianceAssociationModuleV1()
        val evidence = evidence("false", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.KNOWN_FALSE, start)
        val state = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(evidence), residual = 0.1)).state as ObservationVarianceStateV2
        assertEquals(1, state.falseSessionCount)
        assertEquals(0, state.presentSessionCount)
    }

    @Test
    fun `episode persists through unreported evidence and explicit false resolves it`() {
        val module = EpisodeAssociationModuleV1()
        val positive = evidence("ill-1", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start)
        var result = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(positive), residual = -0.1))
        val missingAt = start.plusSeconds(2 * 86_400)
        val missing = evidence("missing-2", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.NOT_REPORTED, missingAt)
        result = module.evaluate(result.state, view(ContextModulePhase.PRE_SESSION_PUBLICATION, missingAt, listOf(missing)))
        assertEquals(1, result.signals.size)
        val resolvedAt = start.plusSeconds(3 * 86_400)
        val falseEvidence = evidence("well-3", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, resolvedAt)
        result = module.evaluate(result.state, view(ContextModulePhase.PRE_SESSION_PUBLICATION, resolvedAt, listOf(falseEvidence)))
        assertTrue(result.signals.isEmpty())
        assertEquals(null, (result.state as EpisodeAssociationStateV2).activeEpisodeId)
    }

    @Test
    fun `repeated rows in one episode do not manufacture independent episodes`() {
        val module = EpisodeAssociationModuleV1()
        var state = module.initialState()
        repeat(5) { index ->
            val at = start.plusSeconds(index * 86_400L)
            state = module.evaluate(state, view(ContextModulePhase.POST_SESSION_UPDATE, at, listOf(evidence("ill-$index", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, at)), residual = -0.05)).state
        }
        val learned = state as EpisodeAssociationStateV2
        assertEquals(5, learned.evidenceRowCount)
        assertEquals(1, learned.independentEpisodeCount)
        assertEquals(1, learned.learnedEpisodeIds.size)
    }

    @Test
    fun `temporally independent illness episodes increase episode support`() {
        val module = EpisodeAssociationModuleV1()
        val first = evidence("ill-a", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start)
        var state = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(first), residual = -0.1)).state
        val resolutionAt = start.plusSeconds(2 * 86_400)
        state = module.evaluate(state, view(ContextModulePhase.PRE_SESSION_PUBLICATION, resolutionAt, listOf(evidence("well-a", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, resolutionAt)))).state
        val secondAt = start.plusSeconds(20 * 86_400)
        state = module.evaluate(state, view(ContextModulePhase.POST_SESSION_UPDATE, secondAt, listOf(evidence("ill-b", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, secondAt)), residual = -0.04)).state
        assertEquals(2, (state as EpisodeAssociationStateV2).independentEpisodeCount)
    }

    @Test
    fun `denied capability fails one module without poisoning another`() {
        val registry = ContextModuleRegistryV7E(listOf(EpisodeAssociationModuleProviderV1, ObservationVarianceAssociationModuleProviderV1))
        val runtime = ContextModuleRuntimeV7E(registry)
        val result = runtime.evaluate(emptyMap()) { descriptor ->
            val granted = if (descriptor.moduleId == EpisodeAssociationModuleV1.MODULE_ID) emptySet() else allCapabilities
            ContextReadViewV1(ContextModulePhase.PRE_SESSION_PUBLICATION, start, ContextScope.SYSTEMIC, granted)
        }
        assertEquals(1, result.failures.size)
        assertTrue(ObservationVarianceAssociationModuleV1.MODULE_ID in result.states)
    }

    @Test
    fun `local signal with systemic scope is rejected`() {
        val descriptor = EpisodeAssociationModuleV1().descriptor.copy(allowedTargets = setOf(ContextSignalTarget.LOCAL_TRANSIENT_STATE))
        val signal = signal("local", target = ContextSignalTarget.LOCAL_TRANSIENT_STATE, scope = ContextScope.SYSTEMIC)
        assertFailsWith<IllegalArgumentException> { ContextSignalValidatorV1.validate(signal, descriptor, start) }
    }

    @Test
    fun `correlated signals are collapsed rather than added`() {
        val a = signal("a", mean = -0.08, correlation = "same")
        val b = signal("b", mean = -0.07, correlation = "same")
        val result = ContextSignalArbitratorV1.arbitrate(listOf(a, b), start).single()
        assertEquals(1, result.acceptedSignalIds.size)
        assertEquals(1, result.suppressedCorrelatedSignalIds.size)
        assertTrue(result.locationMean >= -0.08 && result.locationMean <= -0.07)
    }

    @Test
    fun `contradictory independent signals inflate uncertainty`() {
        val negative = signal("negative", mean = -0.08, correlation = "one", variance = 0.01)
        val positive = signal("positive", mean = 0.08, correlation = "two", variance = 0.01)
        val result = ContextSignalArbitratorV1.arbitrate(listOf(negative, positive), start).single()
        assertTrue(result.contradictory)
        assertEquals(0.0, result.locationMean, 1e-12)
        assertTrue(result.variance > 0.005)
    }

    @Test
    fun `future effective signal is rejected for current horizon`() {
        val module = EpisodeAssociationModuleV1()
        val future = signal("future", effectiveFrom = start.plusSeconds(1))
        assertFailsWith<IllegalArgumentException> { ContextSignalValidatorV1.validate(future, module.descriptor, start) }
    }

    @Test
    fun `NaN signal cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> { signal("nan", mean = Double.NaN) }
    }

    @Test
    fun `unknown feature version is rejected by descriptor compatibility`() {
        val module = EpisodeAssociationModuleV1()
        val wrong = signal("wrong-feature").copy(sourceFeatureKey = ContextFeatureKey("ILLNESS_REPORTED", 99))
        assertFailsWith<IllegalArgumentException> { ContextSignalValidatorV1.validate(wrong, module.descriptor, start) }
    }

    @Test
    fun `unknown and malformed module state versions fail closed`() {
        assertFailsWith<IllegalArgumentException> { EpisodeAssociationStateCodecV2.decode("1|old") }
        assertFailsWith<IllegalArgumentException> { ObservationVarianceStateCodecV2.decode("1|old") }
        assertFailsWith<IllegalArgumentException> {
            ObservationVarianceStateV2().copy(presentSquaredResidualSum = Double.NaN)
        }
    }

    @Test
    fun `wrong effect representation for target is rejected`() {
        val wrong = signal("wrong-effect").copy(effectRepresentation = ContextSignalEffectRepresentation.LOG_OBSERVATION_VARIANCE_SHIFT)
        assertFailsWith<IllegalArgumentException> {
            ContextSignalValidatorV1.validate(wrong, EpisodeAssociationModuleV1().descriptor, start)
        }
    }

    @Test
    fun `duplicate signal publication is rejected by arbitration`() {
        val duplicate = signal("duplicate")
        assertFailsWith<IllegalArgumentException> { ContextSignalArbitratorV1.arbitrate(listOf(duplicate, duplicate), start) }
    }

    @Test
    fun `predictive episode residuals learn negative association direction`() {
        val module = EpisodeAssociationModuleV1()
        var state = module.initialState()
        repeat(4) { episode ->
            val at = start.plusSeconds(episode * 10L * 86_400)
            state = module.evaluate(
                state,
                view(ContextModulePhase.POST_SESSION_UPDATE, at, listOf(evidence("ill-p$episode", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, at)), -0.10),
            ).state
            state = module.evaluate(
                state,
                view(ContextModulePhase.PRE_SESSION_PUBLICATION, at.plusSeconds(86_400), listOf(evidence("well-p$episode", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, at.plusSeconds(86_400)))),
            ).state
        }
        assertTrue((state as EpisodeAssociationStateV2).associationMean < -0.04)
    }

    @Test
    fun `irrelevant episode context remains near inert under symmetric outcomes`() {
        val module = EpisodeAssociationModuleV1()
        var state = module.initialState()
        repeat(6) { episode ->
            val at = start.plusSeconds(episode * 10L * 86_400)
            val residual = if (episode % 2 == 0) -0.06 else 0.06
            state = module.evaluate(
                state,
                view(ContextModulePhase.POST_SESSION_UPDATE, at, listOf(evidence("ill-i$episode", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, at)), residual),
            ).state
            state = module.evaluate(
                state,
                view(ContextModulePhase.PRE_SESSION_PUBLICATION, at.plusSeconds(86_400), listOf(evidence("well-i$episode", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, at.plusSeconds(86_400)))),
            ).state
        }
        assertTrue(kotlin.math.abs((state as EpisodeAssociationStateV2).associationMean) < 0.02)
    }

    @Test
    fun `extractor confidence does not become biological posterior certainty`() {
        val module = EpisodeAssociationModuleV1()
        val high = evidence("high", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start)
        val low = high.copy(evidenceId = "low", sourceRevisionId = "revision:low", extractorConfidence = 0.1)
        val highState = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(high), -0.05)).state as EpisodeAssociationStateV2
        val lowState = module.evaluate(module.initialState(), view(ContextModulePhase.POST_SESSION_UPDATE, start, listOf(low), -0.05)).state as EpisodeAssociationStateV2
        assertEquals(highState.associationMean, lowState.associationMean)
        assertEquals(highState.associationVariance, lowState.associationVariance)
    }

    @Test
    fun `runtime rejects evidence outside provider declaration without poisoning peers`() {
        val registry = ContextModuleRegistryV7E(ProductionContextModuleRegistryV7E.providers)
        val foreign = evidence("foreign", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.PRESENT, start)
        val result = ContextModuleRuntimeV7E(registry).evaluate(emptyMap()) { descriptor ->
            val evidence = if (descriptor.moduleId == EpisodeAssociationModuleV1.MODULE_ID) listOf(foreign) else emptyList()
            ContextReadViewV1(ContextModulePhase.PRE_SESSION_PUBLICATION, start, ContextScope.SYSTEMIC, allCapabilities, evidence)
        }
        assertEquals(1, result.failures.size)
        assertTrue(ObservationVarianceAssociationModuleV1.MODULE_ID in result.states)
    }

    @Test
    fun `ordinary module exception is isolated with previous state retained`() {
        val failingProvider = ContextModuleProviderV7E {
            object : ContextModuleV7E {
                private val delegate = EpisodeAssociationModuleV1()
                override val descriptor = delegate.descriptor.copy(moduleId = "fixture.throwing")
                override val stateCodec = object : ContextModuleStateCodecV7E {
                    override val moduleId = "fixture.throwing"
                    override val schemaVersion = delegate.descriptor.stateSchemaVersion
                    override fun encode(state: ContextModuleStateV7E) = "1"
                    override fun decode(encoded: String) = initialState()
                }
                override fun initialState() = object : ContextModuleStateV7E { override val ownerModuleId = "fixture.throwing" }
                override fun evaluate(state: ContextModuleStateV7E, view: ContextReadViewV1): ContextModuleResultV7E = error("fixture")
            }
        }
        val registry = ContextModuleRegistryV7E(listOf(failingProvider, ObservationVarianceAssociationModuleProviderV1))
        val result = ContextModuleRuntimeV7E(registry).evaluate(emptyMap()) {
            ContextReadViewV1(ContextModulePhase.PRE_SESSION_PUBLICATION, start, ContextScope.SYSTEMIC, allCapabilities)
        }
        assertEquals(1, result.failures.size)
        assertTrue("fixture.throwing" in result.states)
        assertTrue(ObservationVarianceAssociationModuleV1.MODULE_ID in result.states)
    }

    @Test
    fun `synthetic anatomy scoped local signal is accepted only by declared local provider`() {
        val module = EpisodeAssociationModuleV1()
        val descriptor = module.descriptor.copy(allowedTargets = setOf(ContextSignalTarget.LOCAL_TRANSIENT_STATE))
        val local = signal(
            "local-ok",
            target = ContextSignalTarget.LOCAL_TRANSIENT_STATE,
            scope = ContextScope(ContextScopeKind.ANATOMY, "quadriceps"),
        )
        ContextSignalValidatorV1.validate(local, descriptor, start)
    }

    private fun view(
        phase: ContextModulePhase,
        at: Instant,
        evidence: List<ContextFeatureEvidenceV7E>,
        residual: Double? = null,
    ) = ContextReadViewV1(
        phase = phase,
        horizon = at,
        scope = ContextScope.SYSTEMIC,
        grantedCapabilities = allCapabilities,
        ownFeatureEvidence = evidence,
        frozenPrediction = FrozenContextPrediction("prediction:${at.toEpochMilli()}", at, at.minusSeconds(1), 0.0, 0.02, "temporal-base-v1"),
        realisedPostSessionResidual = residual,
    )

    private fun evidence(
        id: String,
        key: ContextFeatureKey,
        missingness: ContextEvidenceMissingness,
        at: Instant,
        scope: ContextScope = ContextScope.SYSTEMIC,
    ) = ContextFeatureEvidenceV7E(
        evidenceId = id,
        featureKey = key,
        value = if (missingness == ContextEvidenceMissingness.PRESENT) ContextFeatureValueV7E.BooleanValue(true) else null,
        missingness = missingness,
        scope = scope,
        observedAt = at,
        sourceKind = ContextEvidenceSourceKind.TEST_FIXTURE,
        sourceRevisionId = "revision:$id",
        extractorConfidence = 0.99,
    )

    private fun signal(
        id: String,
        mean: Double = -0.05,
        variance: Double = 0.01,
        correlation: String = "systemic_episode_context",
        target: ContextSignalTarget = ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE,
        scope: ContextScope = ContextScope.SYSTEMIC,
        effectiveFrom: Instant = start,
    ) = ContextSignalV1(
        signalId = id,
        sourceModuleId = EpisodeAssociationModuleV1.MODULE_ID,
        moduleModelVersion = EpisodeAssociationModuleV1().descriptor.modelVersion,
        moduleConfigId = EpisodeAssociationModuleV1().descriptor.configId,
        sourceFeatureKey = ProductionContextFeaturesV7E.illness.key,
        target = target,
        scope = scope,
        effectiveFrom = effectiveFrom,
        effectiveUntil = effectiveFrom.plusSeconds(86_400),
        effectRepresentation = ContextSignalEffectRepresentation.LOG_PERFORMANCE_LOCATION_SHIFT,
        locationMean = mean,
        variance = variance,
        evidenceRowCount = 3,
        independentSessionCount = 3,
        independentEpisodeCount = 2,
        evidenceMaturity = ContextEvidenceMaturity.PARTIALLY_LEARNED,
        correlationGroupId = correlation,
        sourceEvidenceIds = setOf("evidence:$id"),
        upstreamModelIdentities = setOf("temporal-base-v1"),
        publishedAt = start,
        status = ContextSignalStatus.APPLICABLE,
    )
}
