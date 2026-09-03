package dev.kian.mymettle.developer

import dev.kian.mymettle.context.modules.EpisodeAssociationModuleProviderV1
import dev.kian.mymettle.context.modules.EpisodeAssociationModuleV1
import dev.kian.mymettle.context.modules.EpisodeAssociationStateCodecV1
import dev.kian.mymettle.context.modules.EpisodeAssociationStateV1
import dev.kian.mymettle.context.modules.ObservationVarianceAssociationModuleV1
import dev.kian.mymettle.context.modules.ObservationVarianceStateCodecV1
import dev.kian.mymettle.context.modules.ObservationVarianceStateV1
import dev.kian.mymettle.context.modules.ProductionContextFeaturesV7E
import dev.kian.mymettle.context.modules.ProductionContextModuleRegistryV7E
import dev.kian.mymettle.domain.context.*
import dev.kian.mymettle.domain.inference.*
import java.time.Instant
import kotlin.math.abs

data class NBio7ESyntheticCase(val id: String, val passed: Boolean, val detail: String? = null)

data class NBio7ESyntheticValidationReport(
    val temporalCases: List<NBio7ESyntheticCase>,
    val contextModuleCases: List<NBio7ESyntheticCase>,
) {
    val allPassed: Boolean get() = temporalCases.size == 17 && contextModuleCases.size == 25 &&
        temporalCases.all { it.passed } && contextModuleCases.all { it.passed }
}

/** Device-runnable mirror of the richer unit truth suite; every case computes a contract property. */
object NBio7ESyntheticValidation {
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    fun run(): NBio7ESyntheticValidationReport = NBio7ESyntheticValidationReport(
        temporalCases = temporalCases(),
        contextModuleCases = contextCases(),
    )

    private fun temporalCases(): List<NBio7ESyntheticCase> {
        val filter = NeutralTemporalStateFilterV1()
        fun fit(values: List<Double>, spacingDays: Long = 1): TemporalStatePosteriorV1 {
            var state = filter.initial(start)
            values.forEachIndexed { index, value ->
                state = filter.update(state, start.plusSeconds((index + 1L) * spacingDays * DAY), value, TemporalCandidateLayer.TEMPORAL_BASE).posterior
            }
            return state
        }
        val stable = fit(List(12) { 0.0 })
        val improving = fit(List(16) { it * 0.006 }, 3)
        val declining = fit(List(16) { -it * 0.006 }, 3)
        val stableThenOutlier = fit(List(10) { 0.0 })
        val outlier = filter.update(stableThenOutlier, stableThenOutlier.horizon.plusSeconds(DAY), -1.0, TemporalCandidateLayer.TEMPORAL_BASE)
        val suppression = fit(List(8) { 0.0 } + List(3) { -0.12 })
        val recovered = filter.predictState(suppression, suppression.horizon.plusSeconds(12 * DAY))
        var doseState = filter.initial(start)
        repeat(12) { index ->
            val dose = if (index % 2 == 0) 1.0 else 0.1
            doseState = filter.update(doseState, start.plusSeconds((index + 1L) * DAY), -0.05 * dose, TemporalCandidateLayer.DOSE_TEMPORAL, dose).posterior
        }
        val noisy = fit(List(24) { if (it % 2 == 0) -0.10 else 0.10 })
        val sparse = fit(listOf(0.03))
        val simultaneousBase = improving
        val simultaneous = filter.update(simultaneousBase, simultaneousBase.horizon.plusSeconds(DAY), simultaneousBase.persistentMean - 0.12, TemporalCandidateLayer.TEMPORAL_BASE).posterior
        val (_, broadBase) = filter.predict(filter.initial(start), start.plusSeconds(DAY), TemporalCandidateLayer.TEMPORAL_BASE)
        val (_, broadCompeting) = filter.predict(
            filter.initial(start).copy(doseCoefficientMean = -0.04), start.plusSeconds(DAY),
            TemporalCandidateLayer.CONTEXT_TEMPORAL, 1.0, TemporalContextAdjustment(0.04, 0.03),
        )
        val (_, systemicA) = filter.predict(filter.initial(start), start.plusSeconds(DAY), TemporalCandidateLayer.CONTEXT_TEMPORAL, 0.0, TemporalContextAdjustment(-0.05, 0.01))
        val (_, systemicB) = filter.predict(filter.initial(start), start.plusSeconds(DAY), TemporalCandidateLayer.CONTEXT_TEMPORAL, 0.0, TemporalContextAdjustment(-0.05, 0.01))
        val freshRegime = filter.initial(start.plusSeconds(100 * DAY))
        val noEvidence = filter.initial(start)
        return listOf(
            case("stable_persistent_state") { abs(stable.persistentMean) < 1e-12 },
            case("slow_persistent_improvement") { improving.persistentMean > 0.025 },
            case("slow_persistent_decline") { declining.persistentMean < -0.025 },
            case("one_session_negative_anomaly") { outlier.robustWeight < 1.0 },
            case("multi_session_transient_suppression") { suppression.transientMean < 0.0 },
            case("recovery_after_transient_suppression") { abs(recovered.transientMean) < abs(suppression.transientMean) / 8.0 + 1e-9 },
            case("dose_associated_transient_effect") { doseState.doseCoefficientMean < 0.0 },
            case("no_dose_effect") { filter.initial(start).doseCoefficientMean == 0.0 },
            case("high_observation_noise") { abs(noisy.persistentMean) < 0.025 },
            case("sparse_history") { sparse.covariance.pp > 0.005 },
            case("robust_outlier") { abs(outlier.posterior.persistentMean) < 0.05 },
            case("semantic_regime_boundary") { freshRegime.observationCount == 0 && freshRegime.persistentMean == 0.0 },
            case("systemic_effect_across_profiles") { systemicA.mean == systemicB.mean && systemicA.contextContributionMean == -0.05 },
            case("local_effect_isolated_to_target_scope") { localScopeFixture() },
            case("progression_plus_transient_suppression") { simultaneous.persistentMean > 0.0 && simultaneous.transientMean < 0.0 },
            case("identifiability_stress_remains_broad") { broadCompeting.variance > broadBase.variance },
            case("no_evidence_fail_closed") { noEvidence.observationCount == 0 && noEvidence.independentSessionCount == 0 },
        )
    }

    private fun contextCases(): List<NBio7ESyntheticCase> {
        val registry = ContextModuleRegistryV7E(ProductionContextModuleRegistryV7E.providers)
        val episode = EpisodeAssociationModuleV1()
        val variance = ObservationVarianceAssociationModuleV1()
        val positive = evidence("ill", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start)
        val missing = evidence("missing", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.NOT_REPORTED, start.plusSeconds(DAY))
        val resolved = evidence("resolved", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, start.plusSeconds(2 * DAY))
        var episodeResult = episode.evaluate(episode.initialState(), view(start, listOf(positive), -0.1))
        val persisted = episode.evaluate(episodeResult.state, view(start.plusSeconds(DAY), listOf(missing), null, ContextModulePhase.PRE_SESSION_PUBLICATION))
        val resolution = episode.evaluate(persisted.state, view(start.plusSeconds(2 * DAY), listOf(resolved), null, ContextModulePhase.PRE_SESSION_PUBLICATION))
        var repeated: ContextModuleStateV7E = episode.initialState()
        repeat(5) { index -> repeated = episode.evaluate(repeated, view(start.plusSeconds(index * DAY), listOf(evidence("row$index", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start.plusSeconds(index * DAY))), -0.05)).state }
        val repeatedState = repeated as EpisodeAssociationStateV1
        val falseVariance = variance.evaluate(
            variance.initialState(),
            view(start, listOf(evidence("false", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.KNOWN_FALSE, start)), 0.1),
        ).state as ObservationVarianceStateV1
        val missingVariance = variance.evaluate(
            variance.initialState(),
            view(start, listOf(evidence("absent", ProductionContextFeaturesV7E.timePressure.key, ContextEvidenceMissingness.NOT_REPORTED, start)), 0.1),
        ).state as ObservationVarianceStateV1
        val correlated = ContextSignalArbitratorV1.arbitrate(listOf(signal("a", -0.08, "same"), signal("b", -0.07, "same")), start).single()
        val contradictory = ContextSignalArbitratorV1.arbitrate(listOf(signal("n", -0.08, "n"), signal("p", 0.08, "p")), start).single()
        val runtimeDenied = ContextModuleRuntimeV7E(registry).evaluate(emptyMap()) { descriptor ->
            ContextReadViewV1(ContextModulePhase.PRE_SESSION_PUBLICATION, start, ContextScope.SYSTEMIC, if (descriptor.moduleId == episode.descriptor.moduleId) emptySet() else descriptor.requiredReadCapabilities)
        }
        val replay = EpisodeAssociationStateCodecV1.decode(EpisodeAssociationStateCodecV1.encode(episodeResult.state))
        val lowConfidence = episode.evaluate(episode.initialState(), view(start, listOf(positive.copy(evidenceId = "low", sourceRevisionId = "low", extractorConfidence = 0.1)), -0.1)).state as EpisodeAssociationStateV1
        val highConfidence = episodeResult.state as EpisodeAssociationStateV1
        val basePrediction = NeutralTemporalStateFilterV1().predict(NeutralTemporalStateFilterV1().initial(start), start, TemporalCandidateLayer.TEMPORAL_BASE).second
        val contextPrediction = NeutralTemporalStateFilterV1().predict(NeutralTemporalStateFilterV1().initial(start), start, TemporalCandidateLayer.CONTEXT_TEMPORAL, 0.0, TemporalContextAdjustment(-0.05, 0.01)).second
        return listOf(
            case("new_module_registration_without_core_feature_branch") { registry.modules.size == 2 },
            case("two_distinct_learner_implementations") { registry.modules.map { it.descriptor.learnerFamily }.distinct().size == 2 },
            case("module_owned_state_isolation") { episode.initialState().ownerModuleId != variance.initialState().ownerModuleId },
            case("unknown_feature_version") { fails { ContextSignalValidatorV1.validate(signal("wrong").copy(sourceFeatureKey = ContextFeatureKey("ILLNESS_REPORTED", 99)), episode.descriptor, start) } },
            case("unknown_module_version") { fails { ContextModuleRegistryV7E(listOf(ContextModuleProviderV7E { VersionFixtureModule(99) })) } },
            case("malformed_signal") { fails { signal("bad-scope").copy(target = ContextSignalTarget.LOCAL_TRANSIENT_STATE).let { ContextSignalValidatorV1.validate(it, episode.descriptor.copy(allowedTargets = setOf(ContextSignalTarget.LOCAL_TRANSIENT_STATE)), start) } } },
            case("nan_signal") { fails { signal("nan", Double.NaN) } },
            case("denied_read_capability") { runtimeDenied.failures.size == 1 && runtimeDenied.states.size == 2 },
            case("missing_tag_is_unknown") { missingVariance.presentSessionCount == 0 && missingVariance.falseSessionCount == 0 },
            case("explicit_false_tag") { falseVariance.falseSessionCount == 1 },
            case("negated_annotation_maps_to_false") { resolved.missingness == ContextEvidenceMissingness.KNOWN_FALSE },
            case("episode_persistence") { persisted.signals.isNotEmpty() },
            case("explicit_episode_resolution") { resolution.signals.isEmpty() },
            case("repeated_rows_one_episode") { repeatedState.independentEpisodeCount == 1 },
            case("independent_repeated_episodes") { independentEpisodeFixture() },
            case("irrelevant_context_shrinks_inert") { irrelevantEpisodeFixture() },
            case("predictive_context_learns_direction") { highConfidence.associationMean < 0.0 },
            case("wrong_scope_local_signal_rejected") { fails { ContextSignalValidatorV1.validate(signal("local").copy(target = ContextSignalTarget.LOCAL_TRANSIENT_STATE), episode.descriptor.copy(allowedTargets = setOf(ContextSignalTarget.LOCAL_TRANSIENT_STATE)), start) } },
            case("correlated_modules_not_double_counted") { correlated.acceptedSignalIds.size == 1 && correlated.suppressedCorrelatedSignalIds.size == 1 },
            case("contradictory_modules_remain_uncertain") { contradictory.contradictory && contradictory.variance > 0.005 },
            case("delete_derived_full_replay") { replay == episodeResult.state },
            case("correction_reannotation_changes_replay") { resolution.state != episodeResult.state },
            case("module_failure_does_not_poison_peers") { runtimeDenied.states.containsKey(variance.descriptor.moduleId) },
            case("context_aware_vs_context_free") { contextPrediction.mean != basePrediction.mean && contextPrediction.variance > basePrediction.variance },
            case("nano_confidence_not_biological_certainty") { lowConfidence.associationMean == highConfidence.associationMean && lowConfidence.associationVariance == highConfidence.associationVariance },
        )
    }

    private class VersionFixtureModule(protocol: Int) : ContextModuleV7E {
        override val descriptor = EpisodeAssociationModuleV1().descriptor.copy(moduleId = "fixture.version", protocolVersion = protocol)
        override val stateCodec = object : ContextModuleStateCodecV7E {
            override val moduleId = "fixture.version"; override val schemaVersion = 1
            override fun encode(state: ContextModuleStateV7E) = "1"
            override fun decode(encoded: String) = initialState()
        }
        override fun initialState() = object : ContextModuleStateV7E { override val ownerModuleId = "fixture.version" }
        override fun evaluate(state: ContextModuleStateV7E, view: ContextReadViewV1) = ContextModuleResultV7E(state, emptyList())
    }

    private fun independentEpisodeFixture(): Boolean {
        val module = EpisodeAssociationModuleV1()
        var state = module.evaluate(module.initialState(), view(start, listOf(evidence("a", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start)), -0.05)).state
        state = module.evaluate(state, view(start.plusSeconds(DAY), listOf(evidence("ar", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, start.plusSeconds(DAY))), null, ContextModulePhase.PRE_SESSION_PUBLICATION)).state
        state = module.evaluate(state, view(start.plusSeconds(20 * DAY), listOf(evidence("b", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, start.plusSeconds(20 * DAY))), -0.05)).state
        return (state as EpisodeAssociationStateV1).independentEpisodeCount == 2
    }

    private fun irrelevantEpisodeFixture(): Boolean {
        val module = EpisodeAssociationModuleV1()
        var state: ContextModuleStateV7E = module.initialState()
        repeat(6) { index ->
            val at = start.plusSeconds(index * 10L * DAY)
            state = module.evaluate(state, view(at, listOf(evidence("i$index", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.PRESENT, at)), if (index % 2 == 0) -0.06 else 0.06)).state
            state = module.evaluate(state, view(at.plusSeconds(DAY), listOf(evidence("ir$index", ProductionContextFeaturesV7E.illness.key, ContextEvidenceMissingness.KNOWN_FALSE, at.plusSeconds(DAY))), null, ContextModulePhase.PRE_SESSION_PUBLICATION)).state
        }
        return abs((state as EpisodeAssociationStateV1).associationMean) < 0.02
    }

    private fun localScopeFixture(): Boolean {
        val descriptor = EpisodeAssociationModuleV1().descriptor.copy(allowedTargets = setOf(ContextSignalTarget.LOCAL_TRANSIENT_STATE))
        val local = signal("local-ok").copy(target = ContextSignalTarget.LOCAL_TRANSIENT_STATE, scope = ContextScope(ContextScopeKind.ANATOMY, "fixture"))
        return !fails { ContextSignalValidatorV1.validate(local, descriptor, start) }
    }

    private fun view(at: Instant, evidence: List<ContextFeatureEvidenceV7E>, residual: Double?, phase: ContextModulePhase = ContextModulePhase.POST_SESSION_UPDATE) =
        ContextReadViewV1(
            phase, at, ContextScope.SYSTEMIC, ContextReadCapability.entries.toSet(), evidence,
            FrozenContextPrediction("p:${at.toEpochMilli()}", at, at.minusNanos(1), 0.0, 0.02, "base"), residual,
        )

    private fun evidence(id: String, key: ContextFeatureKey, missingness: ContextEvidenceMissingness, at: Instant) = ContextFeatureEvidenceV7E(
        id, key, if (missingness == ContextEvidenceMissingness.PRESENT) ContextFeatureValueV7E.BooleanValue(true) else null,
        missingness, ContextScope.SYSTEMIC, at, sourceKind = ContextEvidenceSourceKind.TEST_FIXTURE, sourceRevisionId = "r:$id", extractorConfidence = 0.9,
    )

    private fun signal(id: String, mean: Double = -0.05, group: String = "group") = ContextSignalV1(
        id, sourceModuleId = EpisodeAssociationModuleV1.MODULE_ID,
        moduleModelVersion = EpisodeAssociationModuleV1().descriptor.modelVersion,
        moduleConfigId = EpisodeAssociationModuleV1().descriptor.configId,
        sourceFeatureKey = ProductionContextFeaturesV7E.illness.key,
        target = ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE, scope = ContextScope.SYSTEMIC,
        effectiveFrom = start, effectiveUntil = start.plusSeconds(DAY),
        effectRepresentation = ContextSignalEffectRepresentation.LOG_PERFORMANCE_LOCATION_SHIFT,
        locationMean = mean, variance = 0.01, evidenceRowCount = 2, independentSessionCount = 2,
        independentEpisodeCount = 1, evidenceMaturity = ContextEvidenceMaturity.PARTIALLY_LEARNED,
        correlationGroupId = group, sourceEvidenceIds = setOf("e:$id"), upstreamModelIdentities = setOf("base"),
        publishedAt = start, status = ContextSignalStatus.APPLICABLE,
    )

    private fun case(id: String, check: () -> Boolean): NBio7ESyntheticCase = try {
        NBio7ESyntheticCase(id, check())
    } catch (error: Throwable) {
        NBio7ESyntheticCase(id, false, "${error::class.java.simpleName}:${error.message}")
    }

    private fun fails(block: () -> Unit): Boolean = runCatching(block).isFailure
    private const val DAY = 86_400L
}
