package dev.kian.mymettle.domain.context

import java.time.Instant
import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.math.max

const val CONTEXT_MODULE_PROTOCOL_VERSION: Int = 1
const val CONTEXT_SIGNAL_SCHEMA_VERSION: Int = 1

data class ContextFeatureKey(
    val featureId: String,
    val schemaVersion: Int,
) {
    init {
        require(featureId.isNotBlank())
        require(schemaVersion > 0)
    }

    val canonical: String = "$featureId@$schemaVersion"
}

enum class ContextFeatureValueKind {
    BOOLEAN,
    ORDINAL,
    CONTINUOUS,
    CATEGORICAL,
    ANATOMY_SCOPED,
    STRUCTURED_REFERENCE,
}

data class ContextFeatureValueSchema(
    val kind: ContextFeatureValueKind,
    val canonicalUnit: String? = null,
    val lowerBound: Double? = null,
    val upperBound: Double? = null,
    val allowedValues: Set<String> = emptySet(),
) {
    init {
        require(canonicalUnit == null || canonicalUnit.isNotBlank())
        require(lowerBound == null || lowerBound.isFinite())
        require(upperBound == null || upperBound.isFinite())
        require(lowerBound == null || upperBound == null || lowerBound <= upperBound)
        require(allowedValues.all { it.isNotBlank() })
        if (kind == ContextFeatureValueKind.CATEGORICAL) require(allowedValues.isNotEmpty())
        if (kind != ContextFeatureValueKind.CATEGORICAL) require(allowedValues.isEmpty())
    }
}

enum class ContextEvidenceMissingness {
    PRESENT,
    KNOWN_FALSE,
    NOT_REPORTED,
    NOT_MEASURED,
    NOT_APPLICABLE,
    UNKNOWN,
}

enum class ContextFeatureMissingnessSemantics {
    ABSENCE_IS_NOT_REPORTED,
    ABSENCE_IS_NOT_MEASURED,
    EXPLICIT_BOOLEAN_REQUIRED,
    NOT_APPLICABLE_ALLOWED,
}

enum class ContextFeatureTemporalSemantics {
    INSTANTANEOUS,
    SESSION_SCOPED,
    FIXED_INTERVAL,
    EPISODE_LIKE,
    DECAYING,
    UNKNOWN_PERSISTENCE,
}

enum class ContextScopeKind {
    SYSTEMIC,
    SESSION,
    SESSION_EXERCISE,
    EXECUTION_PROFILE,
    SIDE,
    ANATOMY,
    EQUIPMENT,
    EPISODE,
}

data class ContextScope(
    val kind: ContextScopeKind,
    val id: String? = null,
) {
    init {
        if (kind == ContextScopeKind.SYSTEMIC) require(id == null)
        else require(!id.isNullOrBlank()) { "$kind scope requires an id." }
    }

    val canonical: String = "${kind.name}:${id.orEmpty()}"

    companion object {
        val SYSTEMIC = ContextScope(ContextScopeKind.SYSTEMIC)
    }
}

enum class ContextEvidenceSourceKind {
    LEGACY_NOTE_INTERPRETATION,
    EXPLICIT_USER_INPUT,
    DETERMINISTIC_APP_EVENT,
    IMPORTED_STRUCTURED,
    SENSOR,
    TEST_FIXTURE,
}

enum class ContextSignalTarget {
    SYSTEMIC_TRANSIENT_STATE,
    LOCAL_TRANSIENT_STATE,
    OBSERVATION_RELIABILITY,
    OBSERVATION_VARIANCE,
    PROCESS_VOLATILITY,
    RECOVERY_DYNAMICS,
    CAPABILITY_CONDITIONING,
    EXECUTION_CONTEXT,
    EQUIPMENT_TRANSLATION,
    RECRUITMENT_CONTEXT,
}

enum class ContextReadCapability {
    OWN_FEATURE_EVIDENCE,
    TIME_AND_SCOPE,
    FROZEN_PRE_SESSION_PREDICTION,
    REALISED_POST_SESSION_RESIDUAL,
    SESSION_DOSE_SUMMARY,
    APPROVED_EXECUTION_SEMANTICS,
}

data class ContextFeatureDefinitionV7E(
    val key: ContextFeatureKey,
    val humanMeaning: String,
    val valueSchema: ContextFeatureValueSchema,
    val allowedScopes: Set<ContextScopeKind>,
    val allowedSourceKinds: Set<ContextEvidenceSourceKind>,
    val temporalSemantics: ContextFeatureTemporalSemantics,
    val missingnessSemantics: ContextFeatureMissingnessSemantics,
    val allowedSignalTargets: Set<ContextSignalTarget>,
    val requiredReadCapabilities: Set<ContextReadCapability>,
    val compatibleEvidenceVersions: Set<Int> = setOf(key.schemaVersion),
) {
    init {
        require(humanMeaning.isNotBlank())
        require(allowedScopes.isNotEmpty())
        require(allowedSourceKinds.isNotEmpty())
        require(compatibleEvidenceVersions.isNotEmpty() && compatibleEvidenceVersions.all { it > 0 })
    }
}

sealed interface ContextFeatureValueV7E {
    data class BooleanValue(val value: Boolean) : ContextFeatureValueV7E
    data class OrdinalValue(val value: Int) : ContextFeatureValueV7E
    data class ContinuousValue(val value: Double, val unit: String) : ContextFeatureValueV7E {
        init { require(value.isFinite() && unit.isNotBlank()) }
    }
    data class CategoryValue(val value: String) : ContextFeatureValueV7E {
        init { require(value.isNotBlank()) }
    }
    data class AnatomyScopedValue(val anatomyId: String, val value: Double?) : ContextFeatureValueV7E {
        init { require(anatomyId.isNotBlank()); require(value == null || value.isFinite()) }
    }
    data class StructuredReferenceValue(val type: String, val id: String) : ContextFeatureValueV7E {
        init { require(type.isNotBlank() && id.isNotBlank()) }
    }
}

data class ContextFeatureEvidenceV7E(
    val evidenceId: String,
    val featureKey: ContextFeatureKey,
    val value: ContextFeatureValueV7E?,
    val missingness: ContextEvidenceMissingness,
    val scope: ContextScope,
    val observedAt: Instant,
    val effectiveFrom: Instant = observedAt,
    val effectiveUntil: Instant? = null,
    val sourceKind: ContextEvidenceSourceKind,
    val sourceRevisionId: String,
    val extractorConfidence: Double? = null,
) {
    init {
        require(evidenceId.isNotBlank() && sourceRevisionId.isNotBlank())
        require(effectiveUntil == null || !effectiveUntil.isBefore(effectiveFrom))
        require(extractorConfidence == null || extractorConfidence.isFinite() && extractorConfidence in 0.0..1.0)
        require((missingness == ContextEvidenceMissingness.PRESENT) == (value != null)) {
            "Only PRESENT evidence carries a value."
        }
    }
}

class ContextFeatureDefinitionRegistryV7E(definitions: Collection<ContextFeatureDefinitionV7E>) {
    private val byKey = definitions.associateBy { it.key }

    init {
        require(byKey.size == definitions.size) { "Context feature identities must be unique." }
    }

    fun definition(key: ContextFeatureKey): ContextFeatureDefinitionV7E? = byKey[key]

    fun validate(evidence: ContextFeatureEvidenceV7E) {
        val definition = requireNotNull(byKey[evidence.featureKey]) { "Unknown context feature/version ${evidence.featureKey.canonical}." }
        require(evidence.featureKey.schemaVersion in definition.compatibleEvidenceVersions)
        require(evidence.scope.kind in definition.allowedScopes) { "Feature scope is not allowed." }
        require(evidence.sourceKind in definition.allowedSourceKinds) { "Feature source is not allowed." }
        when (val value = evidence.value) {
            null -> Unit
            is ContextFeatureValueV7E.BooleanValue -> require(definition.valueSchema.kind == ContextFeatureValueKind.BOOLEAN)
            is ContextFeatureValueV7E.OrdinalValue -> require(definition.valueSchema.kind == ContextFeatureValueKind.ORDINAL)
            is ContextFeatureValueV7E.ContinuousValue -> {
                require(definition.valueSchema.kind == ContextFeatureValueKind.CONTINUOUS)
                require(definition.valueSchema.canonicalUnit == null || definition.valueSchema.canonicalUnit == value.unit)
                require(definition.valueSchema.lowerBound == null || value.value >= definition.valueSchema.lowerBound)
                require(definition.valueSchema.upperBound == null || value.value <= definition.valueSchema.upperBound)
            }
            is ContextFeatureValueV7E.CategoryValue -> {
                require(definition.valueSchema.kind == ContextFeatureValueKind.CATEGORICAL)
                require(value.value in definition.valueSchema.allowedValues)
            }
            is ContextFeatureValueV7E.AnatomyScopedValue -> require(definition.valueSchema.kind == ContextFeatureValueKind.ANATOMY_SCOPED)
            is ContextFeatureValueV7E.StructuredReferenceValue -> require(definition.valueSchema.kind == ContextFeatureValueKind.STRUCTURED_REFERENCE)
        }
    }
}

enum class ContextEvidenceMaturity {
    NO_EVIDENCE,
    PRIOR_DOMINATED,
    PARTIALLY_LEARNED,
    DATA_INFORMED,
    EMPIRICALLY_USEFUL,
    NO_PREDICTIVE_BENEFIT,
    REJECTED,
}

enum class ContextSignalEffectRepresentation {
    LOG_PERFORMANCE_LOCATION_SHIFT,
    LOG_OBSERVATION_VARIANCE_SHIFT,
}

enum class ContextSignalStatus {
    APPLICABLE,
    PRIOR_DOMINATED,
    UNAVAILABLE,
    REJECTED,
}

data class ContextSignalV1(
    val signalId: String,
    val signalSchemaVersion: Int = CONTEXT_SIGNAL_SCHEMA_VERSION,
    val sourceModuleId: String,
    val moduleModelVersion: String,
    val moduleConfigId: String,
    val sourceFeatureKey: ContextFeatureKey,
    val target: ContextSignalTarget,
    val scope: ContextScope,
    val effectiveFrom: Instant,
    val effectiveUntil: Instant?,
    val effectRepresentation: ContextSignalEffectRepresentation,
    val locationMean: Double?,
    val variance: Double?,
    val evidenceRowCount: Int,
    val independentSessionCount: Int,
    val independentEpisodeCount: Int,
    val evidenceMaturity: ContextEvidenceMaturity,
    val correlationGroupId: String,
    val episodeId: String? = null,
    val sourceEvidenceIds: Set<String>,
    val upstreamModelIdentities: Set<String>,
    val publishedAt: Instant,
    val status: ContextSignalStatus,
    val failureCode: String? = null,
) {
    init {
        require(signalId.isNotBlank() && sourceModuleId.isNotBlank() && moduleModelVersion.isNotBlank() && moduleConfigId.isNotBlank())
        require(correlationGroupId.isNotBlank())
        require(effectiveUntil == null || !effectiveUntil.isBefore(effectiveFrom))
        require(evidenceRowCount >= 0)
        require(independentSessionCount in 0..evidenceRowCount)
        require(independentEpisodeCount in 0..independentSessionCount)
        require(sourceEvidenceIds.all { it.isNotBlank() } && upstreamModelIdentities.all { it.isNotBlank() })
        if (status in setOf(ContextSignalStatus.APPLICABLE, ContextSignalStatus.PRIOR_DOMINATED)) {
            require(locationMean != null && locationMean.isFinite())
            require(variance != null && variance.isFinite() && variance > 0.0)
            require(failureCode == null)
        } else {
            require(locationMean == null && variance == null)
            require(!failureCode.isNullOrBlank())
        }
    }
}

data class ContextModuleDescriptor(
    val moduleId: String,
    val protocolVersion: Int,
    val learnerFamily: String,
    val modelVersion: String,
    val configId: String,
    val configPayload: String,
    val stateSchemaVersion: Int,
    val consumedFeatures: Set<ContextFeatureKey>,
    val requiredReadCapabilities: Set<ContextReadCapability>,
    val allowedTargets: Set<ContextSignalTarget>,
    val deterministicReplay: Boolean,
) {
    init {
        require(moduleId.isNotBlank() && learnerFamily.isNotBlank() && modelVersion.isNotBlank())
        require(configId.isNotBlank() && configPayload.isNotBlank())
        require(protocolVersion > 0 && stateSchemaVersion > 0)
        require(consumedFeatures.isNotEmpty() && allowedTargets.isNotEmpty())
    }
}

interface ContextModuleStateV7E {
    val ownerModuleId: String
}

interface ContextModuleStateCodecV7E {
    val moduleId: String
    val schemaVersion: Int
    fun encode(state: ContextModuleStateV7E): String
    fun decode(encoded: String): ContextModuleStateV7E
}

enum class ContextModulePhase {
    PRE_SESSION_PUBLICATION,
    POST_SESSION_UPDATE,
}

data class FrozenContextPrediction(
    val predictionId: String,
    val predictedAt: Instant,
    val evidenceThrough: Instant?,
    val meanLogResidual: Double,
    val variance: Double,
    val modelIdentity: String,
) {
    init {
        require(predictionId.isNotBlank() && modelIdentity.isNotBlank())
        require(evidenceThrough == null || !evidenceThrough.isAfter(predictedAt))
        require(meanLogResidual.isFinite() && variance.isFinite() && variance > 0.0)
    }
}

class ContextCapabilityViolationException(message: String) : IllegalStateException(message)

class ContextReadViewV1(
    val phase: ContextModulePhase,
    horizon: Instant,
    scope: ContextScope,
    val grantedCapabilities: Set<ContextReadCapability>,
    ownFeatureEvidence: List<ContextFeatureEvidenceV7E> = emptyList(),
    frozenPrediction: FrozenContextPrediction? = null,
    realisedPostSessionResidual: Double? = null,
    sessionDoseSummary: Double? = null,
    approvedExecutionSemantics: Map<String, String> = emptyMap(),
) {
    private val horizonValue = horizon
    private val scopeValue = scope
    private val ownEvidence = ownFeatureEvidence.toList()
    private val prediction = frozenPrediction
    private val residual = realisedPostSessionResidual
    private val dose = sessionDoseSummary
    private val execution = approvedExecutionSemantics.toMap()

    init {
        require(realisedPostSessionResidual == null || realisedPostSessionResidual.isFinite())
        require(sessionDoseSummary == null || sessionDoseSummary.isFinite())
        require(phase == ContextModulePhase.POST_SESSION_UPDATE || realisedPostSessionResidual == null) {
            "Realised outcomes are unavailable during pre-session publication."
        }
    }

    fun ownFeatureEvidence(): List<ContextFeatureEvidenceV7E> = read(ContextReadCapability.OWN_FEATURE_EVIDENCE, ownEvidence)
    fun horizon(): Instant = read(ContextReadCapability.TIME_AND_SCOPE, horizonValue)
    fun scope(): ContextScope = read(ContextReadCapability.TIME_AND_SCOPE, scopeValue)
    fun frozenPrediction(): FrozenContextPrediction? = read(ContextReadCapability.FROZEN_PRE_SESSION_PREDICTION, prediction)
    fun realisedPostSessionResidual(): Double? = read(ContextReadCapability.REALISED_POST_SESSION_RESIDUAL, residual)
    fun sessionDoseSummary(): Double? = read(ContextReadCapability.SESSION_DOSE_SUMMARY, dose)
    fun approvedExecutionSemantics(): Map<String, String> = read(ContextReadCapability.APPROVED_EXECUTION_SEMANTICS, execution)

    private fun <T> read(capability: ContextReadCapability, value: T): T {
        if (capability !in grantedCapabilities) throw ContextCapabilityViolationException("Read capability $capability was not granted.")
        return value
    }
}

data class ContextModuleResultV7E(
    val state: ContextModuleStateV7E,
    val signals: List<ContextSignalV1>,
)

interface ContextModuleV7E {
    val descriptor: ContextModuleDescriptor
    val stateCodec: ContextModuleStateCodecV7E
    fun initialState(): ContextModuleStateV7E
    fun evaluate(state: ContextModuleStateV7E, view: ContextReadViewV1): ContextModuleResultV7E
}

fun interface ContextModuleProviderV7E {
    fun create(): ContextModuleV7E
}

class ContextModuleRegistryV7E(
    providers: Collection<ContextModuleProviderV7E>,
    featureDefinitions: Collection<ContextFeatureDefinitionV7E> = emptyList(),
) {
    private val featureRegistry = ContextFeatureDefinitionRegistryV7E(featureDefinitions)
    private val enforceFeatureDefinitions = featureDefinitions.isNotEmpty()
    val modules: List<ContextModuleV7E> = providers.map { it.create() }.sortedBy { it.descriptor.moduleId }

    init {
        require(modules.map { it.descriptor.moduleId }.distinct().size == modules.size) { "Context module ids must be unique." }
        modules.forEach { module ->
            require(module.descriptor.protocolVersion == CONTEXT_MODULE_PROTOCOL_VERSION) {
                "Unsupported module protocol ${module.descriptor.protocolVersion}: ${module.descriptor.moduleId}"
            }
            require(module.stateCodec.moduleId == module.descriptor.moduleId)
            require(module.stateCodec.schemaVersion == module.descriptor.stateSchemaVersion)
            require(ContextReadCapability.OWN_FEATURE_EVIDENCE in module.descriptor.requiredReadCapabilities)
            require(ContextReadCapability.TIME_AND_SCOPE in module.descriptor.requiredReadCapabilities)
        }
    }

    fun module(moduleId: String): ContextModuleV7E? = modules.firstOrNull { it.descriptor.moduleId == moduleId }
    fun validateEvidence(evidence: ContextFeatureEvidenceV7E) {
        if (enforceFeatureDefinitions) featureRegistry.validate(evidence)
    }
}

data class ContextModuleFailureV7E(
    val moduleId: String,
    val phase: ContextModulePhase,
    val code: String,
    val message: String,
)

data class ContextRuntimeResultV7E(
    val states: Map<String, ContextModuleStateV7E>,
    val signals: List<ContextSignalV1>,
    val failures: List<ContextModuleFailureV7E>,
)

class ContextModuleRuntimeV7E(
    private val registry: ContextModuleRegistryV7E,
) {
    fun evaluate(
        previousStates: Map<String, ContextModuleStateV7E>,
        readViewFor: (ContextModuleDescriptor) -> ContextReadViewV1,
    ): ContextRuntimeResultV7E {
        val states = linkedMapOf<String, ContextModuleStateV7E>()
        val signals = mutableListOf<ContextSignalV1>()
        val failures = mutableListOf<ContextModuleFailureV7E>()
        registry.modules.forEach { module ->
            val previous = previousStates[module.descriptor.moduleId] ?: module.initialState()
            val view = readViewFor(module.descriptor)
            try {
                require(view.grantedCapabilities.containsAll(module.descriptor.requiredReadCapabilities)) {
                    "Host did not grant declared capabilities."
                }
                require(view.ownFeatureEvidence().all { it.featureKey in module.descriptor.consumedFeatures }) {
                    "Read view exposed evidence outside the module's declared feature set."
                }
                view.ownFeatureEvidence().forEach(registry::validateEvidence)
                val result = module.evaluate(previous, view)
                require(result.state.ownerModuleId == module.descriptor.moduleId)
                result.signals.forEach { signal -> ContextSignalValidatorV1.validate(signal, module.descriptor, view.horizon()) }
                states[module.descriptor.moduleId] = result.state
                signals += result.signals
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                states[module.descriptor.moduleId] = previous
                failures += ContextModuleFailureV7E(
                    moduleId = module.descriptor.moduleId,
                    phase = view.phase,
                    code = error::class.simpleName ?: "MODULE_FAILURE",
                    message = error.message ?: "Module failed without a message.",
                )
            }
        }
        return ContextRuntimeResultV7E(states, signals.sortedBy { it.signalId }, failures)
    }
}

object ContextSignalTargetPolicyV1 {
    val effectfulTemporalTargets: Set<ContextSignalTarget> = setOf(
        ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE,
        ContextSignalTarget.OBSERVATION_VARIANCE,
    )
    val protocolOnlyTargets: Set<ContextSignalTarget> = setOf(ContextSignalTarget.LOCAL_TRANSIENT_STATE)
    val implementedTargets: Set<ContextSignalTarget> = effectfulTemporalTargets + protocolOnlyTargets
}

object ContextSignalValidatorV1 {
    private const val MAX_LOCATION_SHIFT = 0.20
    private const val MAX_LOG_VARIANCE_SHIFT = 1.38629436112
    private const val MIN_VARIANCE = 0.0001
    private const val MAX_VARIANCE = 1.0

    fun validate(signal: ContextSignalV1, descriptor: ContextModuleDescriptor, horizon: Instant) {
        require(signal.signalSchemaVersion == CONTEXT_SIGNAL_SCHEMA_VERSION) { "Unknown signal schema." }
        require(signal.sourceModuleId == descriptor.moduleId)
        require(signal.moduleModelVersion == descriptor.modelVersion && signal.moduleConfigId == descriptor.configId)
        require(signal.sourceFeatureKey in descriptor.consumedFeatures)
        require(signal.target in descriptor.allowedTargets && signal.target in ContextSignalTargetPolicyV1.implementedTargets)
        require(!signal.effectiveFrom.isAfter(horizon)) { "Future signal cannot apply to this horizon." }
        if (signal.target == ContextSignalTarget.LOCAL_TRANSIENT_STATE) {
            require(signal.scope.kind == ContextScopeKind.ANATOMY) { "Local transient signal requires anatomy scope." }
        }
        if (signal.target == ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE) {
            require(signal.scope.kind == ContextScopeKind.SYSTEMIC) { "Systemic signal requires systemic scope." }
        }
        when (signal.target) {
            ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE,
            ContextSignalTarget.LOCAL_TRANSIENT_STATE,
            -> require(signal.effectRepresentation == ContextSignalEffectRepresentation.LOG_PERFORMANCE_LOCATION_SHIFT)
            ContextSignalTarget.OBSERVATION_VARIANCE ->
                require(signal.effectRepresentation == ContextSignalEffectRepresentation.LOG_OBSERVATION_VARIANCE_SHIFT)
            else -> Unit // Reserved targets have already failed the v1 target policy above.
        }
        if (signal.status in setOf(ContextSignalStatus.APPLICABLE, ContextSignalStatus.PRIOR_DOMINATED)) {
            val mean = requireNotNull(signal.locationMean)
            val variance = requireNotNull(signal.variance)
            require(variance in MIN_VARIANCE..MAX_VARIANCE)
            when (signal.effectRepresentation) {
                ContextSignalEffectRepresentation.LOG_PERFORMANCE_LOCATION_SHIFT -> require(abs(mean) <= MAX_LOCATION_SHIFT)
                ContextSignalEffectRepresentation.LOG_OBSERVATION_VARIANCE_SHIFT -> require(abs(mean) <= MAX_LOG_VARIANCE_SHIFT)
            }
        }
    }
}

data class ArbitratedContextTargetV1(
    val target: ContextSignalTarget,
    val scope: ContextScope,
    val locationMean: Double,
    val variance: Double,
    val acceptedSignalIds: Set<String>,
    val suppressedCorrelatedSignalIds: Set<String>,
    val contradictory: Boolean,
)

object ContextSignalArbitratorV1 {
    fun arbitrate(signals: Collection<ContextSignalV1>, horizon: Instant): List<ArbitratedContextTargetV1> {
        val applicable = signals.filter {
            it.status in setOf(ContextSignalStatus.APPLICABLE, ContextSignalStatus.PRIOR_DOMINATED) &&
                !it.effectiveFrom.isAfter(horizon) && (it.effectiveUntil == null || !it.effectiveUntil.isBefore(horizon))
        }
        require(applicable.map { it.signalId }.distinct().size == applicable.size) { "Duplicate ContextSignal id." }
        return applicable.groupBy { it.target to it.scope }.map { (key, scoped) ->
            val representatives = scoped.groupBy { it.correlationGroupId }.values.map { group ->
                group.sortedWith(
                    compareByDescending<ContextSignalV1> { maturityRank(it.evidenceMaturity) }
                        .thenByDescending { it.independentEpisodeCount }
                        .thenByDescending { it.independentSessionCount }
                        .thenBy { requireNotNull(it.variance) }
                        .thenBy { it.signalId },
                ).first()
            }
            val suppressed = scoped.map { it.signalId }.toSet() - representatives.map { it.signalId }.toSet()
            val precisions = representatives.map { 1.0 / requireNotNull(it.variance) }
            val precisionSum = precisions.sum()
            val mean = representatives.zip(precisions).sumOf { (signal, precision) -> requireNotNull(signal.locationMean) * precision } / precisionSum
            val withinVariance = 1.0 / precisionSum
            val betweenVariance = representatives.zip(precisions).sumOf { (signal, precision) ->
                precision * (requireNotNull(signal.locationMean) - mean) * (requireNotNull(signal.locationMean) - mean)
            } / precisionSum
            val contradictory = representatives.any { requireNotNull(it.locationMean) < 0.0 } && representatives.any { requireNotNull(it.locationMean) > 0.0 }
            ArbitratedContextTargetV1(
                target = key.first,
                scope = key.second,
                locationMean = mean,
                variance = max(0.0001, withinVariance + betweenVariance),
                acceptedSignalIds = representatives.map { it.signalId }.toSet(),
                suppressedCorrelatedSignalIds = suppressed,
                contradictory = contradictory,
            )
        }.sortedWith(compareBy({ it.target.name }, { it.scope.canonical }))
    }

    private fun maturityRank(value: ContextEvidenceMaturity): Int = when (value) {
        ContextEvidenceMaturity.NO_EVIDENCE -> 0
        ContextEvidenceMaturity.PRIOR_DOMINATED -> 1
        ContextEvidenceMaturity.PARTIALLY_LEARNED -> 2
        ContextEvidenceMaturity.DATA_INFORMED -> 3
        ContextEvidenceMaturity.EMPIRICALLY_USEFUL -> 4
        ContextEvidenceMaturity.NO_PREDICTIVE_BENEFIT -> 2
        ContextEvidenceMaturity.REJECTED -> -1
    }
}
