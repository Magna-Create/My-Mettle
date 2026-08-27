package dev.kian.mymettle.domain.inference

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

@JvmInline
value class ModelConfigId(val value: String) {
    init {
        require(value.isNotBlank()) { "Model config id cannot be blank." }
    }
}

@JvmInline
value class ModelManifestId(val value: String) {
    init {
        require(value.isNotBlank()) { "Model manifest id cannot be blank." }
    }
}

enum class InferenceModelComponent(val storageValue: String) {
    REFERENCE("reference"),
    PERFORMANCE_NORMALISATION("performance_normalisation"),
    RESISTANCE("resistance"),
    DYNAMIC_CAPABILITY("dynamic_capability"),
    HOLD_CAPABILITY("hold_capability"),
    DURATION_CAPABILITY("duration_capability"),
    REPEATED_CONTRACTION_CAPABILITY("repeated_contraction_capability"),
    RECRUITMENT("recruitment"),
    SET_DEMAND("set_demand"),
    EXPOSURE("exposure"),
    EFFECTIVE_DOSE("effective_dose"),
    SESSION_DOSE("session_dose"),
    FATIGUE("fatigue"),
    RECOVERY("recovery"),
    SKILL("skill"),
    DEVELOPMENT("development"),
    CONDITIONING("conditioning"),
    TRANSLATION("translation"),
    SYSTEMIC_CONTEXT("systemic_context");

    companion object {
        fun fromStorage(value: String): InferenceModelComponent = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported inference model component: $value")
    }
}

enum class InferenceExecutionMode(val storageValue: String) {
    BENCHMARK_V0("benchmark_v0"),
    CANDIDATE_V7("candidate_v7"),
    SHADOW("shadow");

    companion object {
        fun fromStorage(value: String): InferenceExecutionMode = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported inference execution mode: $value")
    }
}

enum class InferenceSemanticsMode(val storageValue: String) {
    HISTORICAL_SEMANTICS("historical_semantics"),
    CURRENT_MODEL_REINTERPRETATION("current_model_reinterpretation");

    companion object {
        fun fromStorage(value: String): InferenceSemanticsMode = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported inference semantics mode: $value")
    }
}

/**
 * Immutable behaviour/config identity. Metadata timestamps are deliberately outside the fingerprint;
 * changing any behaviour-driving descriptor or canonical payload changes the id.
 */
class ModelConfigDefinition private constructor(
    val id: ModelConfigId,
    val component: InferenceModelComponent,
    val modelFamily: String,
    val modelName: String,
    val semanticVersion: String,
    val configSchemaVersion: Int,
    val canonicalConfigPayload: String,
    val createdAt: Instant,
    val effectiveAt: Instant?,
) {
    init {
        require(modelFamily.isNotBlank()) { "Model family cannot be blank." }
        require(modelName.isNotBlank()) { "Model name cannot be blank." }
        require(semanticVersion.isNotBlank()) { "Model semantic version cannot be blank." }
        require(configSchemaVersion > 0) { "Config schema version must be positive." }
        require(canonicalConfigPayload.isNotBlank()) { "Canonical config payload cannot be blank." }
        require(effectiveAt == null || !effectiveAt.isBefore(createdAt)) {
            "Model config cannot become effective before it was created."
        }
        require(id == fingerprint(component, modelFamily, modelName, semanticVersion, configSchemaVersion, canonicalConfigPayload)) {
            "Model config id does not match its immutable behaviour/config definition."
        }
    }

    override fun equals(other: Any?): Boolean = other is ModelConfigDefinition &&
        id == other.id && component == other.component && modelFamily == other.modelFamily &&
        modelName == other.modelName && semanticVersion == other.semanticVersion &&
        configSchemaVersion == other.configSchemaVersion && canonicalConfigPayload == other.canonicalConfigPayload &&
        createdAt == other.createdAt && effectiveAt == other.effectiveAt

    override fun hashCode(): Int = id.hashCode()

    companion object {
        fun create(
            component: InferenceModelComponent,
            modelFamily: String,
            modelName: String,
            semanticVersion: String,
            configSchemaVersion: Int,
            parameters: Map<String, String>,
            createdAt: Instant,
            effectiveAt: Instant? = null,
        ): ModelConfigDefinition {
            val payload = canonicaliseParameters(parameters)
            return restore(
                id = fingerprint(component, modelFamily, modelName, semanticVersion, configSchemaVersion, payload),
                component = component,
                modelFamily = modelFamily,
                modelName = modelName,
                semanticVersion = semanticVersion,
                configSchemaVersion = configSchemaVersion,
                canonicalConfigPayload = payload,
                createdAt = createdAt,
                effectiveAt = effectiveAt,
            )
        }

        fun restore(
            id: ModelConfigId,
            component: InferenceModelComponent,
            modelFamily: String,
            modelName: String,
            semanticVersion: String,
            configSchemaVersion: Int,
            canonicalConfigPayload: String,
            createdAt: Instant,
            effectiveAt: Instant? = null,
        ): ModelConfigDefinition = ModelConfigDefinition(
            id,
            component,
            modelFamily,
            modelName,
            semanticVersion,
            configSchemaVersion,
            canonicalConfigPayload,
            createdAt,
            effectiveAt,
        )

        fun canonicaliseParameters(parameters: Map<String, String>): String {
            require(parameters.isNotEmpty()) { "A model config must describe at least one parameter or state." }
            return parameters.toSortedMap().entries.joinToString("\n") { (key, value) ->
                require(key.isNotBlank()) { "Model config parameter keys cannot be blank." }
                "${escape(key)}=${escape(value)}"
            }
        }

        private fun escape(value: String): String = value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("=", "\\=")

        private fun fingerprint(
            component: InferenceModelComponent,
            modelFamily: String,
            modelName: String,
            semanticVersion: String,
            configSchemaVersion: Int,
            payload: String,
        ): ModelConfigId {
            val canonical = listOf(
                component.storageValue,
                modelFamily,
                modelName,
                semanticVersion,
                configSchemaVersion.toString(),
                payload,
            ).joinToString("\n")
            return ModelConfigId("modelcfg_sha256_${sha256(canonical)}")
        }
    }
}

/** Immutable set of component config identities used by an inference run. */
class ModelManifest private constructor(
    val id: ModelManifestId,
    entries: Map<InferenceModelComponent, ModelConfigId>,
) {
    val entries: Map<InferenceModelComponent, ModelConfigId> = entries.toMap()

    init {
        require(this.entries.isNotEmpty()) { "Model manifest cannot be empty." }
        require(id == fingerprint(this.entries)) { "Model manifest id does not match its entries." }
    }

    fun configId(component: InferenceModelComponent): ModelConfigId? = entries[component]

    fun requireComponents(required: Set<InferenceModelComponent>): ModelManifest {
        val missing = required - entries.keys
        require(missing.isEmpty()) { "Model manifest is missing components: ${missing.joinToString { it.storageValue }}" }
        return this
    }

    companion object {
        fun create(entries: Map<InferenceModelComponent, ModelConfigId>): ModelManifest =
            ModelManifest(fingerprint(entries), entries)

        fun restore(id: ModelManifestId, entries: Map<InferenceModelComponent, ModelConfigId>): ModelManifest =
            ModelManifest(id, entries)

        private fun fingerprint(entries: Map<InferenceModelComponent, ModelConfigId>): ModelManifestId {
            require(entries.isNotEmpty()) { "Model manifest cannot be empty." }
            val canonical = entries.entries
                .sortedBy { it.key.storageValue }
                .joinToString("\n") { "${it.key.storageValue}=${it.value.value}" }
            return ModelManifestId("manifest_sha256_${sha256(canonical)}")
        }
    }
}

val REQUIRED_NBIO7_COMPONENTS: Set<InferenceModelComponent> = setOf(
    InferenceModelComponent.REFERENCE,
    InferenceModelComponent.PERFORMANCE_NORMALISATION,
    InferenceModelComponent.RESISTANCE,
    InferenceModelComponent.DYNAMIC_CAPABILITY,
    InferenceModelComponent.HOLD_CAPABILITY,
    InferenceModelComponent.DURATION_CAPABILITY,
    InferenceModelComponent.REPEATED_CONTRACTION_CAPABILITY,
    InferenceModelComponent.RECRUITMENT,
    InferenceModelComponent.SET_DEMAND,
    InferenceModelComponent.EXPOSURE,
    InferenceModelComponent.EFFECTIVE_DOSE,
    InferenceModelComponent.SESSION_DOSE,
    InferenceModelComponent.FATIGUE,
    InferenceModelComponent.RECOVERY,
    InferenceModelComponent.SKILL,
    InferenceModelComponent.DEVELOPMENT,
    InferenceModelComponent.CONDITIONING,
    InferenceModelComponent.TRANSLATION,
)

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(StandardCharsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
