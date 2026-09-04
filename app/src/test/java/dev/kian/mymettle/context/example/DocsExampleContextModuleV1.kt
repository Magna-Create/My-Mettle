package dev.kian.mymettle.context.example

import dev.kian.mymettle.domain.context.CONTEXT_MODULE_PROTOCOL_VERSION
import dev.kian.mymettle.domain.context.ContextEvidenceMaturity
import dev.kian.mymettle.domain.context.ContextEvidenceMissingness
import dev.kian.mymettle.domain.context.ContextEvidenceSourceKind
import dev.kian.mymettle.domain.context.ContextFeatureDefinitionV7E
import dev.kian.mymettle.domain.context.ContextFeatureKey
import dev.kian.mymettle.domain.context.ContextFeatureMissingnessSemantics
import dev.kian.mymettle.domain.context.ContextFeatureTemporalSemantics
import dev.kian.mymettle.domain.context.ContextFeatureValueKind
import dev.kian.mymettle.domain.context.ContextFeatureValueSchema
import dev.kian.mymettle.domain.context.ContextModuleDescriptor
import dev.kian.mymettle.domain.context.ContextModuleProviderV7E
import dev.kian.mymettle.domain.context.ContextModuleResultV7E
import dev.kian.mymettle.domain.context.ContextModuleStateCodecV7E
import dev.kian.mymettle.domain.context.ContextModuleStateV7E
import dev.kian.mymettle.domain.context.ContextModuleV7E
import dev.kian.mymettle.domain.context.ContextReadCapability
import dev.kian.mymettle.domain.context.ContextReadViewV1
import dev.kian.mymettle.domain.context.ContextScopeKind
import dev.kian.mymettle.domain.context.ContextSignalEffectRepresentation
import dev.kian.mymettle.domain.context.ContextSignalStatus
import dev.kian.mymettle.domain.context.ContextSignalTarget
import dev.kian.mymettle.domain.context.ContextSignalV1
import java.util.Base64

/**
 * Compile-tested documentation fixture. It is deliberately absent from the production feature and
 * module registries. The neutral signal proves the extension path without claiming a learned effect.
 */
object DocsExampleContextFeatureV1 {
    val definition = ContextFeatureDefinitionV7E(
        key = ContextFeatureKey("DOCS_SESSION_FLAG", 1),
        humanMeaning = "Synthetic session flag used only by the Context Module documentation tests.",
        valueSchema = ContextFeatureValueSchema(ContextFeatureValueKind.BOOLEAN),
        allowedScopes = setOf(ContextScopeKind.SESSION),
        allowedSourceKinds = setOf(ContextEvidenceSourceKind.TEST_FIXTURE),
        temporalSemantics = ContextFeatureTemporalSemantics.SESSION_SCOPED,
        missingnessSemantics = ContextFeatureMissingnessSemantics.ABSENCE_IS_NOT_REPORTED,
        allowedSignalTargets = setOf(ContextSignalTarget.OBSERVATION_VARIANCE),
        requiredReadCapabilities = setOf(
            ContextReadCapability.OWN_FEATURE_EVIDENCE,
            ContextReadCapability.TIME_AND_SCOPE,
        ),
    )
}

data class DocsExampleModuleStateV1(
    override val ownerModuleId: String = DocsExampleContextModuleV1.MODULE_ID,
    val processedEvidenceIds: Set<String> = emptySet(),
    val countedSessionKeys: Set<String> = emptySet(),
    val presentRowCount: Int = 0,
    val knownFalseRowCount: Int = 0,
) : ContextModuleStateV7E {
    init {
        require(ownerModuleId == DocsExampleContextModuleV1.MODULE_ID)
        require(presentRowCount >= 0 && knownFalseRowCount >= 0)
    }
}

object DocsExampleModuleStateCodecV1 : ContextModuleStateCodecV7E {
    override val moduleId: String = DocsExampleContextModuleV1.MODULE_ID
    override val schemaVersion: Int = 1

    override fun encode(state: ContextModuleStateV7E): String {
        require(state is DocsExampleModuleStateV1 && state.ownerModuleId == moduleId)
        return listOf(
            "1",
            state.processedEvidenceIds.encodeSet(),
            state.countedSessionKeys.encodeSet(),
            state.presentRowCount,
            state.knownFalseRowCount,
        ).joinToString("|")
    }

    override fun decode(encoded: String): ContextModuleStateV7E {
        val parts = encoded.split('|')
        require(parts.size == 5 && parts[0] == "1") { "Unsupported documentation example state." }
        return DocsExampleModuleStateV1(
            processedEvidenceIds = parts[1].decodeSet(),
            countedSessionKeys = parts[2].decodeSet(),
            presentRowCount = parts[3].toInt(),
            knownFalseRowCount = parts[4].toInt(),
        )
    }
}

class DocsExampleContextModuleV1 : ContextModuleV7E {
    override val descriptor = ContextModuleDescriptor(
        moduleId = MODULE_ID,
        protocolVersion = CONTEXT_MODULE_PROTOCOL_VERSION,
        learnerFamily = "documentation_neutral_counter",
        modelVersion = "docs-session-flag-v1",
        configId = "context-module:docs-session-flag:v1",
        configPayload = "schema=1;effect=neutral",
        stateSchemaVersion = DocsExampleModuleStateCodecV1.schemaVersion,
        consumedFeatures = setOf(DocsExampleContextFeatureV1.definition.key),
        requiredReadCapabilities = DocsExampleContextFeatureV1.definition.requiredReadCapabilities,
        allowedTargets = DocsExampleContextFeatureV1.definition.allowedSignalTargets,
        deterministicReplay = true,
    )

    override val stateCodec: ContextModuleStateCodecV7E = DocsExampleModuleStateCodecV1

    override fun initialState(): ContextModuleStateV7E = DocsExampleModuleStateV1()

    override fun evaluate(
        state: ContextModuleStateV7E,
        view: ContextReadViewV1,
    ): ContextModuleResultV7E {
        require(state is DocsExampleModuleStateV1 && state.ownerModuleId == MODULE_ID)
        val newEvidence = view.ownFeatureEvidence()
            .filter { it.featureKey == DocsExampleContextFeatureV1.definition.key }
            .filter { it.evidenceId !in state.processedEvidenceIds }
            .sortedWith(compareBy({ it.observedAt }, { it.evidenceId }))

        val next = state.copy(
            processedEvidenceIds = state.processedEvidenceIds + newEvidence.map { it.evidenceId },
            countedSessionKeys = state.countedSessionKeys + newEvidence
                .filter { it.missingness in setOf(ContextEvidenceMissingness.PRESENT, ContextEvidenceMissingness.KNOWN_FALSE) }
                .map { it.scope.canonical },
            presentRowCount = state.presentRowCount + newEvidence.count { it.missingness == ContextEvidenceMissingness.PRESENT },
            knownFalseRowCount = state.knownFalseRowCount + newEvidence.count { it.missingness == ContextEvidenceMissingness.KNOWN_FALSE },
        )

        val horizon = view.horizon()
        val current = newEvidence.lastOrNull {
            it.missingness == ContextEvidenceMissingness.PRESENT &&
                !it.effectiveFrom.isAfter(horizon) &&
                (it.effectiveUntil == null || !it.effectiveUntil.isBefore(horizon))
        }
        val signal = current?.let {
            ContextSignalV1(
                signalId = "$MODULE_ID:${it.evidenceId}:${horizon.toEpochMilli()}",
                sourceModuleId = MODULE_ID,
                moduleModelVersion = descriptor.modelVersion,
                moduleConfigId = descriptor.configId,
                sourceFeatureKey = it.featureKey,
                target = ContextSignalTarget.OBSERVATION_VARIANCE,
                scope = view.scope(),
                effectiveFrom = horizon,
                effectiveUntil = it.effectiveUntil ?: horizon,
                effectRepresentation = ContextSignalEffectRepresentation.LOG_OBSERVATION_VARIANCE_SHIFT,
                locationMean = 0.0,
                variance = 1.0,
                evidenceRowCount = next.presentRowCount + next.knownFalseRowCount,
                independentSessionCount = next.countedSessionKeys.size,
                independentEpisodeCount = 0,
                evidenceMaturity = ContextEvidenceMaturity.PRIOR_DOMINATED,
                correlationGroupId = "docs_session_observation_quality",
                sourceEvidenceIds = setOf(it.evidenceId),
                upstreamModelIdentities = emptySet(),
                publishedAt = horizon,
                status = ContextSignalStatus.PRIOR_DOMINATED,
            )
        }
        return ContextModuleResultV7E(next, listOfNotNull(signal))
    }

    companion object {
        const val MODULE_ID = "docs.example.session_flag.observation_variance.v1"
    }
}

object DocsExampleContextModuleProviderV1 : ContextModuleProviderV7E {
    override fun create(): ContextModuleV7E = DocsExampleContextModuleV1()
}

private fun Set<String>.encodeSet(): String = sorted().joinToString(",") { value ->
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
}

private fun String.decodeSet(): Set<String> = ifBlank { null }
    ?.split(',')
    ?.mapTo(linkedSetOf()) { value ->
        String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
    }
    .orEmpty()
