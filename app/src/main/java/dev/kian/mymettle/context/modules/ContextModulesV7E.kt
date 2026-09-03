package dev.kian.mymettle.context.modules

import dev.kian.mymettle.domain.context.AssertionSemantics
import dev.kian.mymettle.domain.context.ContextEvidenceItem
import dev.kian.mymettle.domain.context.ContextEvidenceMaturity
import dev.kian.mymettle.domain.context.ContextEvidenceMissingness
import dev.kian.mymettle.domain.context.ContextEvidenceSourceKind
import dev.kian.mymettle.domain.context.ContextFeatureDefinitionV7E
import dev.kian.mymettle.domain.context.ContextFeatureEvidenceV7E
import dev.kian.mymettle.domain.context.ContextFeatureKey
import dev.kian.mymettle.domain.context.ContextFeatureMissingnessSemantics
import dev.kian.mymettle.domain.context.ContextFeatureTemporalSemantics
import dev.kian.mymettle.domain.context.ContextFeatureValueKind
import dev.kian.mymettle.domain.context.ContextFeatureValueSchema
import dev.kian.mymettle.domain.context.ContextFeatureValueV7E
import dev.kian.mymettle.domain.context.ContextModuleDescriptor
import dev.kian.mymettle.domain.context.ContextModulePhase
import dev.kian.mymettle.domain.context.ContextModuleProviderV7E
import dev.kian.mymettle.domain.context.ContextModuleRegistryV7E
import dev.kian.mymettle.domain.context.ContextModuleResultV7E
import dev.kian.mymettle.domain.context.ContextModuleStateCodecV7E
import dev.kian.mymettle.domain.context.ContextModuleStateV7E
import dev.kian.mymettle.domain.context.ContextModuleV7E
import dev.kian.mymettle.domain.context.ContextReadCapability
import dev.kian.mymettle.domain.context.ContextReadViewV1
import dev.kian.mymettle.domain.context.ContextScope
import dev.kian.mymettle.domain.context.ContextScopeKind
import dev.kian.mymettle.domain.context.ContextSignalEffectRepresentation
import dev.kian.mymettle.domain.context.ContextSignalStatus
import dev.kian.mymettle.domain.context.ContextSignalTarget
import dev.kian.mymettle.domain.context.ContextSignalV1
import dev.kian.mymettle.domain.context.ContextValue
import dev.kian.mymettle.domain.context.InferenceEligibility
import dev.kian.mymettle.domain.context.NoteScope
import dev.kian.mymettle.domain.context.TemporalApplicability
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

object ProductionContextFeaturesV7E {
    val illness = ContextFeatureDefinitionV7E(
        key = ContextFeatureKey("ILLNESS_REPORTED", 1),
        humanMeaning = "User-reported illness/unwell context; not a diagnosis or causal claim.",
        valueSchema = ContextFeatureValueSchema(ContextFeatureValueKind.BOOLEAN),
        allowedScopes = setOf(ContextScopeKind.SESSION, ContextScopeKind.SYSTEMIC),
        allowedSourceKinds = setOf(ContextEvidenceSourceKind.LEGACY_NOTE_INTERPRETATION, ContextEvidenceSourceKind.EXPLICIT_USER_INPUT),
        temporalSemantics = ContextFeatureTemporalSemantics.EPISODE_LIKE,
        missingnessSemantics = ContextFeatureMissingnessSemantics.ABSENCE_IS_NOT_REPORTED,
        allowedSignalTargets = setOf(ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE),
        requiredReadCapabilities = setOf(
            ContextReadCapability.OWN_FEATURE_EVIDENCE,
            ContextReadCapability.TIME_AND_SCOPE,
            ContextReadCapability.FROZEN_PRE_SESSION_PREDICTION,
            ContextReadCapability.REALISED_POST_SESSION_RESIDUAL,
        ),
    )

    val timePressure = ContextFeatureDefinitionV7E(
        key = ContextFeatureKey("TIME_PRESSURE_REPORTED", 1),
        humanMeaning = "User-reported unusual time pressure; candidate observation-variance association only.",
        valueSchema = ContextFeatureValueSchema(ContextFeatureValueKind.BOOLEAN),
        allowedScopes = setOf(ContextScopeKind.SESSION, ContextScopeKind.SYSTEMIC),
        allowedSourceKinds = setOf(ContextEvidenceSourceKind.LEGACY_NOTE_INTERPRETATION, ContextEvidenceSourceKind.EXPLICIT_USER_INPUT),
        temporalSemantics = ContextFeatureTemporalSemantics.SESSION_SCOPED,
        missingnessSemantics = ContextFeatureMissingnessSemantics.ABSENCE_IS_NOT_REPORTED,
        allowedSignalTargets = setOf(ContextSignalTarget.OBSERVATION_VARIANCE),
        requiredReadCapabilities = setOf(
            ContextReadCapability.OWN_FEATURE_EVIDENCE,
            ContextReadCapability.TIME_AND_SCOPE,
            ContextReadCapability.FROZEN_PRE_SESSION_PREDICTION,
            ContextReadCapability.REALISED_POST_SESSION_RESIDUAL,
        ),
    )

    val all: List<ContextFeatureDefinitionV7E> = listOf(illness, timePressure)
}

object LegacyContextEvidenceAdapterV1 {
    fun adapt(
        item: ContextEvidenceItem,
        evidenceId: String,
        ownerId: String,
        observedAt: Instant,
        ownerScope: ContextScope,
    ): ContextFeatureEvidenceV7E {
        require(item.eligibility == InferenceEligibility.CANDIDATE_COVARIATE) {
            "Legacy evidence must cross the 7A.5 candidate-covariate projector before 7E."
        }
        val annotation = item.annotation
        val missingness = when {
            annotation.temporalApplicability != TemporalApplicability.CURRENT -> ContextEvidenceMissingness.UNKNOWN
            annotation.assertion == AssertionSemantics.UNCERTAIN -> ContextEvidenceMissingness.UNKNOWN
            annotation.assertion == AssertionSemantics.NEGATED -> ContextEvidenceMissingness.KNOWN_FALSE
            annotation.value is ContextValue.BooleanValue && !annotation.value.value -> ContextEvidenceMissingness.KNOWN_FALSE
            else -> ContextEvidenceMissingness.PRESENT
        }
        val value = if (missingness == ContextEvidenceMissingness.PRESENT) annotation.value.toV7E() else null
        require(annotation.scope == NoteScope.SESSION_REVIEW || ownerScope.kind == ContextScopeKind.SESSION_EXERCISE)
        return ContextFeatureEvidenceV7E(
            evidenceId = evidenceId,
            featureKey = ContextFeatureKey(annotation.tagId.value, annotation.tagSchemaVersion),
            value = value,
            missingness = missingness,
            scope = ownerScope,
            observedAt = observedAt,
            sourceKind = ContextEvidenceSourceKind.LEGACY_NOTE_INTERPRETATION,
            sourceRevisionId = "$ownerId:${item.provenance.sourceTextHash}:${item.provenance.runId}",
            extractorConfidence = null,
        )
    }

    fun missingLegacyEvidence(
        feature: ContextFeatureDefinitionV7E,
        ownerId: String,
        observedAt: Instant,
        scope: ContextScope,
    ): ContextFeatureEvidenceV7E = ContextFeatureEvidenceV7E(
        evidenceId = "missing:${feature.key.canonical}:$ownerId",
        featureKey = feature.key,
        value = null,
        missingness = ContextEvidenceMissingness.NOT_REPORTED,
        scope = scope,
        observedAt = observedAt,
        sourceKind = ContextEvidenceSourceKind.LEGACY_NOTE_INTERPRETATION,
        sourceRevisionId = "missing:$ownerId",
    )

    private fun ContextValue.toV7E(): ContextFeatureValueV7E = when (this) {
        is ContextValue.BooleanValue -> ContextFeatureValueV7E.BooleanValue(value)
        is ContextValue.NumberValue -> ContextFeatureValueV7E.ContinuousValue(value, unit)
        is ContextValue.CategoryValue -> ContextFeatureValueV7E.CategoryValue(value)
        is ContextValue.TextActionValue -> ContextFeatureValueV7E.StructuredReferenceValue("legacy_text_action", "redacted")
    }
}

data class EpisodeAssociationStateV2(
    override val ownerModuleId: String = EpisodeAssociationModuleV1.MODULE_ID,
    val processedEvidenceIds: Set<String> = emptySet(),
    val learnedEpisodeIds: Set<String> = emptySet(),
    val countedSessionKeys: Set<String> = emptySet(),
    val activeEpisodeId: String? = null,
    val episodeStartedAt: Instant? = null,
    val lastPositiveAt: Instant? = null,
    val lastEvidenceId: String? = null,
    val evidenceRowCount: Int = 0,
    val independentSessionCount: Int = 0,
    val independentEpisodeCount: Int = 0,
    val associationMean: Double = 0.0,
    val associationVariance: Double = 0.0100,
    val persistenceAlpha: Double = 1.0,
    val persistenceBeta: Double = 1.0,
) : ContextModuleStateV7E {
    init {
        require(ownerModuleId == EpisodeAssociationModuleV1.MODULE_ID)
        require(evidenceRowCount >= 0 && independentSessionCount in 0..evidenceRowCount)
        require(independentEpisodeCount in 0..independentSessionCount)
        require(countedSessionKeys.size == independentSessionCount)
        require(learnedEpisodeIds.size <= independentEpisodeCount)
        require(associationMean.isFinite())
        require(associationVariance.isFinite() && associationVariance > 0.0)
        require(persistenceAlpha.isFinite() && persistenceAlpha > 0.0)
        require(persistenceBeta.isFinite() && persistenceBeta > 0.0)
        require(episodeStartedAt == null || lastPositiveAt == null || !lastPositiveAt.isBefore(episodeStartedAt))
    }
}

object EpisodeAssociationStateCodecV2 : ContextModuleStateCodecV7E {
    override val moduleId: String = EpisodeAssociationModuleV1.MODULE_ID
    override val schemaVersion: Int = 2

    override fun encode(state: ContextModuleStateV7E): String {
        require(state is EpisodeAssociationStateV2 && state.ownerModuleId == moduleId)
        return listOf(
            "2",
            state.processedEvidenceIds.encodedSet(),
            state.learnedEpisodeIds.encodedSet(),
            state.countedSessionKeys.encodedSet(),
            state.activeEpisodeId.orEmpty().encodedString(),
            state.episodeStartedAt?.toString().orEmpty(),
            state.lastPositiveAt?.toString().orEmpty(),
            state.lastEvidenceId.orEmpty().encodedString(),
            state.evidenceRowCount,
            state.independentSessionCount,
            state.independentEpisodeCount,
            state.associationMean,
            state.associationVariance,
            state.persistenceAlpha,
            state.persistenceBeta,
        ).joinToString("|")
    }

    override fun decode(encoded: String): ContextModuleStateV7E {
        val p = encoded.split('|')
        require(p.size == 15 && p[0] == "2") { "Unsupported episode state codec." }
        return EpisodeAssociationStateV2(
            processedEvidenceIds = p[1].decodedSet(),
            learnedEpisodeIds = p[2].decodedSet(),
            countedSessionKeys = p[3].decodedSet(),
            activeEpisodeId = p[4].decodedString().ifBlank { null },
            episodeStartedAt = p[5].ifBlank { null }?.let(Instant::parse),
            lastPositiveAt = p[6].ifBlank { null }?.let(Instant::parse),
            lastEvidenceId = p[7].decodedString().ifBlank { null },
            evidenceRowCount = p[8].toInt(),
            independentSessionCount = p[9].toInt(),
            independentEpisodeCount = p[10].toInt(),
            associationMean = p[11].toDouble(),
            associationVariance = p[12].toDouble(),
            persistenceAlpha = p[13].toDouble(),
            persistenceBeta = p[14].toDouble(),
        )
    }
}

class EpisodeAssociationModuleV1 : ContextModuleV7E {
    override val descriptor = ContextModuleDescriptor(
        moduleId = MODULE_ID,
        protocolVersion = 1,
        learnerFamily = "episode_persistence_conjugate_association",
        modelVersion = "illness-episode-association-v1",
        configId = "context-module:illness-episode:v1",
        stateSchemaVersion = 2,
        consumedFeatures = setOf(ProductionContextFeaturesV7E.illness.key),
        requiredReadCapabilities = ProductionContextFeaturesV7E.illness.requiredReadCapabilities,
        allowedTargets = setOf(ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE),
        deterministicReplay = true,
    )
    override val stateCodec: ContextModuleStateCodecV7E = EpisodeAssociationStateCodecV2

    override fun initialState(): ContextModuleStateV7E = EpisodeAssociationStateV2()

    override fun evaluate(state: ContextModuleStateV7E, view: ContextReadViewV1): ContextModuleResultV7E {
        require(state is EpisodeAssociationStateV2 && state.ownerModuleId == MODULE_ID)
        var next: EpisodeAssociationStateV2 = state
        val newEvidence = view.ownFeatureEvidence()
            .filter { it.featureKey == ProductionContextFeaturesV7E.illness.key && it.evidenceId !in state.processedEvidenceIds }
            .sortedWith(compareBy({ it.observedAt }, { it.evidenceId }))
        newEvidence.forEach { evidence -> next = applyEvidence(next, evidence) }

        val residual = if (view.phase == ContextModulePhase.POST_SESSION_UPDATE) view.realisedPostSessionResidual() else null
        val episodeId = next.activeEpisodeId
        if (residual != null && episodeId != null && episodeId !in next.learnedEpisodeIds) {
            val priorPrecision = 1.0 / next.associationVariance
            val observationPrecision = 1.0 / ASSOCIATION_OBSERVATION_VARIANCE
            next = next.copy(
                associationMean = (next.associationMean * priorPrecision + residual * observationPrecision) / (priorPrecision + observationPrecision),
                associationVariance = 1.0 / (priorPrecision + observationPrecision),
                learnedEpisodeIds = next.learnedEpisodeIds + episodeId,
            )
        }

        val signal = publishSignal(next, view.horizon())
        return ContextModuleResultV7E(next, listOfNotNull(signal))
    }

    private fun applyEvidence(state: EpisodeAssociationStateV2, evidence: ContextFeatureEvidenceV7E): EpisodeAssociationStateV2 {
        val processed = state.processedEvidenceIds + evidence.evidenceId
        val sessionKey = evidence.independentSessionKey()
        val newSession = sessionKey !in state.countedSessionKeys
        if (evidence.missingness == ContextEvidenceMissingness.NOT_REPORTED ||
            evidence.missingness == ContextEvidenceMissingness.NOT_MEASURED ||
            evidence.missingness == ContextEvidenceMissingness.UNKNOWN
        ) return state.copy(processedEvidenceIds = processed)

        if (evidence.missingness == ContextEvidenceMissingness.KNOWN_FALSE) {
            return state.copy(
                processedEvidenceIds = processed,
                activeEpisodeId = null,
                episodeStartedAt = null,
                lastEvidenceId = evidence.evidenceId,
                evidenceRowCount = state.evidenceRowCount + 1,
                countedSessionKeys = state.countedSessionKeys + sessionKey,
                independentSessionCount = state.independentSessionCount + if (newSession) 1 else 0,
                persistenceBeta = state.persistenceBeta + if (state.activeEpisodeId != null) 1.0 else 0.0,
            )
        }

        if (evidence.missingness != ContextEvidenceMissingness.PRESENT) return state.copy(processedEvidenceIds = processed)
        val continueEpisode = state.activeEpisodeId != null && state.lastPositiveAt != null &&
            Duration.between(state.lastPositiveAt, evidence.observedAt).toHours() <= MAX_EPISODE_GAP_HOURS
        val newEpisodeId = if (continueEpisode) state.activeEpisodeId else "episode:${evidence.evidenceId}"
        return state.copy(
            processedEvidenceIds = processed,
            activeEpisodeId = newEpisodeId,
            episodeStartedAt = if (continueEpisode) state.episodeStartedAt else evidence.observedAt,
            lastPositiveAt = evidence.observedAt,
            lastEvidenceId = evidence.evidenceId,
            evidenceRowCount = state.evidenceRowCount + 1,
            countedSessionKeys = state.countedSessionKeys + sessionKey,
            independentSessionCount = state.independentSessionCount + if (newSession) 1 else 0,
            independentEpisodeCount = state.independentEpisodeCount + if (continueEpisode) 0 else 1,
            persistenceAlpha = state.persistenceAlpha + if (continueEpisode) 1.0 else 0.0,
        )
    }

    private fun publishSignal(state: EpisodeAssociationStateV2, horizon: Instant): ContextSignalV1? {
        val last = state.lastPositiveAt ?: return null
        val episodeId = state.activeEpisodeId ?: return null
        val ageDays = max(0.0, Duration.between(last, horizon).toMillis() / 86_400_000.0)
        if (ageDays > MAX_EPISODE_AGE_DAYS) return null
        val persistenceMean = state.persistenceAlpha / (state.persistenceAlpha + state.persistenceBeta)
        val decay = exp(-ln(2.0) * ageDays / ASSOCIATION_HALF_LIFE_DAYS) * persistenceMean
        val maturity = when {
            state.independentEpisodeCount >= 3 -> ContextEvidenceMaturity.DATA_INFORMED
            state.independentEpisodeCount >= 1 -> ContextEvidenceMaturity.PARTIALLY_LEARNED
            else -> ContextEvidenceMaturity.PRIOR_DOMINATED
        }
        return ContextSignalV1(
            signalId = "$MODULE_ID:$episodeId:${horizon.toEpochMilli()}",
            sourceModuleId = MODULE_ID,
            moduleModelVersion = descriptor.modelVersion,
            moduleConfigId = descriptor.configId,
            sourceFeatureKey = ProductionContextFeaturesV7E.illness.key,
            target = ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE,
            scope = ContextScope.SYSTEMIC,
            effectiveFrom = horizon,
            effectiveUntil = last.plusSeconds((MAX_EPISODE_AGE_DAYS * 86_400).toLong()),
            effectRepresentation = ContextSignalEffectRepresentation.LOG_PERFORMANCE_LOCATION_SHIFT,
            locationMean = min(0.20, max(-0.20, state.associationMean * decay)),
            variance = min(1.0, max(0.0001, state.associationVariance + (1.0 - decay) * 0.0100)),
            evidenceRowCount = state.evidenceRowCount,
            independentSessionCount = state.independentSessionCount,
            independentEpisodeCount = state.independentEpisodeCount,
            evidenceMaturity = maturity,
            correlationGroupId = "systemic_episode_context",
            episodeId = episodeId,
            sourceEvidenceIds = setOfNotNull(state.lastEvidenceId),
            upstreamModelIdentities = setOf("frozen-pre-session-residual-v1"),
            publishedAt = horizon,
            status = if (maturity == ContextEvidenceMaturity.PRIOR_DOMINATED) ContextSignalStatus.PRIOR_DOMINATED else ContextSignalStatus.APPLICABLE,
        )
    }

    companion object {
        const val MODULE_ID = "context.illness.episode.v1"
        private const val MAX_EPISODE_GAP_HOURS = 7L * 24L
        private const val MAX_EPISODE_AGE_DAYS = 14.0
        private const val ASSOCIATION_HALF_LIFE_DAYS = 3.0
        private const val ASSOCIATION_OBSERVATION_VARIANCE = 0.0400
    }
}

object EpisodeAssociationModuleProviderV1 : ContextModuleProviderV7E {
    override fun create(): ContextModuleV7E = EpisodeAssociationModuleV1()
}

data class ObservationVarianceStateV2(
    override val ownerModuleId: String = ObservationVarianceAssociationModuleV1.MODULE_ID,
    val processedEvidenceIds: Set<String> = emptySet(),
    val evidenceRowCount: Int = 0,
    val presentSessionCount: Int = 0,
    val falseSessionCount: Int = 0,
    val presentSquaredResidualSum: Double = 0.0,
    val falseSquaredResidualSum: Double = 0.0,
    val countedPresentSessionKeys: Set<String> = emptySet(),
    val countedFalseSessionKeys: Set<String> = emptySet(),
    val currentPresence: ContextEvidenceMissingness = ContextEvidenceMissingness.NOT_REPORTED,
    val lastEvidenceId: String? = null,
    val currentEffectiveFrom: Instant? = null,
    val currentEffectiveUntil: Instant? = null,
) : ContextModuleStateV7E {
    init {
        require(ownerModuleId == ObservationVarianceAssociationModuleV1.MODULE_ID)
        require(evidenceRowCount >= 0)
        require(presentSessionCount >= 0 && falseSessionCount >= 0)
        require(presentSessionCount + falseSessionCount <= evidenceRowCount)
        require(countedPresentSessionKeys.size == presentSessionCount)
        require(countedFalseSessionKeys.size == falseSessionCount)
        require(presentSquaredResidualSum.isFinite() && presentSquaredResidualSum >= 0.0)
        require(falseSquaredResidualSum.isFinite() && falseSquaredResidualSum >= 0.0)
        require((currentEffectiveFrom == null) == (currentEffectiveUntil == null))
        require(currentEffectiveFrom == null || !requireNotNull(currentEffectiveUntil).isBefore(currentEffectiveFrom))
    }
}

object ObservationVarianceStateCodecV2 : ContextModuleStateCodecV7E {
    override val moduleId: String = ObservationVarianceAssociationModuleV1.MODULE_ID
    override val schemaVersion: Int = 2

    override fun encode(state: ContextModuleStateV7E): String {
        require(state is ObservationVarianceStateV2 && state.ownerModuleId == moduleId)
        return listOf(
            "2", state.processedEvidenceIds.encodedSet(), state.evidenceRowCount,
            state.presentSessionCount, state.falseSessionCount, state.presentSquaredResidualSum,
            state.falseSquaredResidualSum, state.countedPresentSessionKeys.encodedSet(), state.countedFalseSessionKeys.encodedSet(),
            state.currentPresence.name, state.lastEvidenceId.orEmpty().encodedString(),
            state.currentEffectiveFrom?.toString().orEmpty(), state.currentEffectiveUntil?.toString().orEmpty(),
        ).joinToString("|")
    }

    override fun decode(encoded: String): ContextModuleStateV7E {
        val p = encoded.split('|')
        require(p.size == 13 && p[0] == "2") { "Unsupported variance state codec." }
        return ObservationVarianceStateV2(
            processedEvidenceIds = p[1].decodedSet(), evidenceRowCount = p[2].toInt(),
            presentSessionCount = p[3].toInt(), falseSessionCount = p[4].toInt(),
            presentSquaredResidualSum = p[5].toDouble(), falseSquaredResidualSum = p[6].toDouble(),
            countedPresentSessionKeys = p[7].decodedSet(), countedFalseSessionKeys = p[8].decodedSet(),
            currentPresence = ContextEvidenceMissingness.valueOf(p[9]), lastEvidenceId = p[10].decodedString().ifBlank { null },
            currentEffectiveFrom = p[11].ifBlank { null }?.let(Instant::parse),
            currentEffectiveUntil = p[12].ifBlank { null }?.let(Instant::parse),
        )
    }
}

class ObservationVarianceAssociationModuleV1 : ContextModuleV7E {
    override val descriptor = ContextModuleDescriptor(
        moduleId = MODULE_ID,
        protocolVersion = 1,
        learnerFamily = "two_group_robust_variance_ratio",
        modelVersion = "time-pressure-observation-variance-v1",
        configId = "context-module:time-pressure-variance:v1",
        stateSchemaVersion = 2,
        consumedFeatures = setOf(ProductionContextFeaturesV7E.timePressure.key),
        requiredReadCapabilities = ProductionContextFeaturesV7E.timePressure.requiredReadCapabilities,
        allowedTargets = setOf(ContextSignalTarget.OBSERVATION_VARIANCE),
        deterministicReplay = true,
    )
    override val stateCodec: ContextModuleStateCodecV7E = ObservationVarianceStateCodecV2
    override fun initialState(): ContextModuleStateV7E = ObservationVarianceStateV2()

    override fun evaluate(state: ContextModuleStateV7E, view: ContextReadViewV1): ContextModuleResultV7E {
        require(state is ObservationVarianceStateV2 && state.ownerModuleId == MODULE_ID)
        var next: ObservationVarianceStateV2 = state
        val newEvidence = view.ownFeatureEvidence()
            .filter { it.featureKey == ProductionContextFeaturesV7E.timePressure.key && it.evidenceId !in state.processedEvidenceIds }
            .sortedWith(compareBy({ it.observedAt }, { it.evidenceId }))
        newEvidence.forEach { evidence ->
            next = next.copy(
                processedEvidenceIds = next.processedEvidenceIds + evidence.evidenceId,
                evidenceRowCount = next.evidenceRowCount + if (evidence.missingness in setOf(ContextEvidenceMissingness.PRESENT, ContextEvidenceMissingness.KNOWN_FALSE)) 1 else 0,
                currentPresence = evidence.missingness,
                lastEvidenceId = evidence.evidenceId,
                currentEffectiveFrom = evidence.effectiveFrom,
                currentEffectiveUntil = evidence.effectiveUntil ?: evidence.effectiveFrom,
            )
        }
        val residual = if (view.phase == ContextModulePhase.POST_SESSION_UPDATE) view.realisedPostSessionResidual() else null
        val learnedEvidence = newEvidence.lastOrNull()
        val learnedPresence = learnedEvidence?.missingness
        if (residual != null && learnedPresence in setOf(ContextEvidenceMissingness.PRESENT, ContextEvidenceMissingness.KNOWN_FALSE)) {
            val boundedSquare = min(MAX_SQUARED_RESIDUAL, residual * residual)
            val sessionKey = requireNotNull(learnedEvidence).independentSessionKey()
            next = when (learnedPresence) {
                ContextEvidenceMissingness.PRESENT -> if (sessionKey in next.countedPresentSessionKeys) next else next.copy(
                    presentSessionCount = next.presentSessionCount + 1,
                    presentSquaredResidualSum = next.presentSquaredResidualSum + boundedSquare,
                    countedPresentSessionKeys = next.countedPresentSessionKeys + sessionKey,
                )
                ContextEvidenceMissingness.KNOWN_FALSE -> if (sessionKey in next.countedFalseSessionKeys) next else next.copy(
                    falseSessionCount = next.falseSessionCount + 1,
                    falseSquaredResidualSum = next.falseSquaredResidualSum + boundedSquare,
                    countedFalseSessionKeys = next.countedFalseSessionKeys + sessionKey,
                )
                else -> next // Missing/unmentioned is never a negative/control row.
            }
        }
        return ContextModuleResultV7E(next, listOfNotNull(publish(next, view.horizon())))
    }

    private fun publish(state: ObservationVarianceStateV2, horizon: Instant): ContextSignalV1? {
        if (state.currentPresence != ContextEvidenceMissingness.PRESENT ||
            state.currentEffectiveFrom?.let(horizon::isBefore) != false ||
            state.currentEffectiveUntil?.let(horizon::isAfter) != false
        ) return null
        val bothGroups = state.presentSessionCount > 0 && state.falseSessionCount > 0
        val presentVariance = (state.presentSquaredResidualSum + PRIOR_VARIANCE_SUM) / (state.presentSessionCount + PRIOR_COUNT)
        val falseVariance = (state.falseSquaredResidualSum + PRIOR_VARIANCE_SUM) / (state.falseSessionCount + PRIOR_COUNT)
        val logRatio = if (bothGroups) ln(presentVariance / falseVariance) else 0.0
        val uncertainty = if (bothGroups) 2.0 / (state.presentSessionCount + PRIOR_COUNT) + 2.0 / (state.falseSessionCount + PRIOR_COUNT) else 1.0
        val maturity = when {
            state.presentSessionCount >= 8 && state.falseSessionCount >= 8 -> ContextEvidenceMaturity.DATA_INFORMED
            bothGroups -> ContextEvidenceMaturity.PARTIALLY_LEARNED
            else -> ContextEvidenceMaturity.PRIOR_DOMINATED
        }
        return ContextSignalV1(
            signalId = "$MODULE_ID:${state.lastEvidenceId}:${horizon.toEpochMilli()}",
            sourceModuleId = MODULE_ID,
            moduleModelVersion = descriptor.modelVersion,
            moduleConfigId = descriptor.configId,
            sourceFeatureKey = ProductionContextFeaturesV7E.timePressure.key,
            target = ContextSignalTarget.OBSERVATION_VARIANCE,
            scope = ContextScope.SYSTEMIC,
            effectiveFrom = horizon,
            effectiveUntil = horizon.plusSeconds(86_400),
            effectRepresentation = ContextSignalEffectRepresentation.LOG_OBSERVATION_VARIANCE_SHIFT,
            locationMean = min(MAX_ABS_LOG_VARIANCE_SHIFT, max(-MAX_ABS_LOG_VARIANCE_SHIFT, logRatio)),
            variance = min(1.0, max(0.0001, uncertainty)),
            evidenceRowCount = state.evidenceRowCount,
            independentSessionCount = state.presentSessionCount + state.falseSessionCount,
            independentEpisodeCount = 0,
            evidenceMaturity = maturity,
            correlationGroupId = "session_observation_quality",
            sourceEvidenceIds = setOfNotNull(state.lastEvidenceId),
            upstreamModelIdentities = setOf("frozen-pre-session-residual-v1"),
            publishedAt = horizon,
            status = if (maturity == ContextEvidenceMaturity.PRIOR_DOMINATED) ContextSignalStatus.PRIOR_DOMINATED else ContextSignalStatus.APPLICABLE,
        )
    }

    companion object {
        const val MODULE_ID = "context.time_pressure.observation_variance.v1"
        private const val PRIOR_COUNT = 2.0
        private const val PRIOR_VARIANCE_SUM = 0.02
        private const val MAX_SQUARED_RESIDUAL = 0.25
        private const val MAX_ABS_LOG_VARIANCE_SHIFT = 1.38629436112
    }
}

object ObservationVarianceAssociationModuleProviderV1 : ContextModuleProviderV7E {
    override fun create(): ContextModuleV7E = ObservationVarianceAssociationModuleV1()
}

object ProductionContextModuleRegistryV7E {
    val providers: List<ContextModuleProviderV7E> = listOf(
        EpisodeAssociationModuleProviderV1,
        ObservationVarianceAssociationModuleProviderV1,
    )

    fun create(): ContextModuleRegistryV7E = ContextModuleRegistryV7E(providers, ProductionContextFeaturesV7E.all)
}

private fun ContextFeatureEvidenceV7E.independentSessionKey(): String = when (scope.kind) {
    ContextScopeKind.SESSION -> scope.canonical
    else -> "evidence:$evidenceId"
}

private fun Set<String>.encodedSet(): String = sorted().joinToString(",") { it.encodedString() }

private fun String.decodedSet(): Set<String> = ifBlank { null }
    ?.split(',')
    ?.mapTo(linkedSetOf()) { it.decodedString() }
    .orEmpty()

private fun String.encodedString(): String = Base64.getUrlEncoder().withoutPadding()
    .encodeToString(toByteArray(Charsets.UTF_8))

private fun String.decodedString(): String = if (isEmpty()) "" else String(
    Base64.getUrlDecoder().decode(this),
    Charsets.UTF_8,
)

private fun <T : Any> setOfNotNull(value: T?): Set<T> = if (value == null) emptySet() else setOf(value)
