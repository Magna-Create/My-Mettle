package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelManifest
import dev.kian.mymettle.domain.inference.REQUIRED_NBIO7_COMPONENTS
import java.time.Instant

data class ModelManifestBundle(
    val manifest: ModelManifest,
    val configs: List<ModelConfigDefinition>,
)

/**
 * Reifies the current conservative benchmark, including explicit identities for components that are
 * intentionally absent. An absent component config reproduces blank behaviour; it does not invent a
 * biological output.
 */
object BenchmarkV0ModelManifestFactory {
    private val CREATED_AT: Instant = Instant.parse("2026-08-27T00:00:00Z")

    fun create(
        referenceModelVersion: String,
        recruitmentModelVersion: String,
        exposureModelVersion: String,
        muscleStateModelVersion: String,
        translationModelVersion: String,
    ): ModelManifestBundle {
        val active = mapOf(
            InferenceModelComponent.REFERENCE to active(
                InferenceModelComponent.REFERENCE,
                "reference_profile",
                referenceModelVersion,
                mapOf("referenceModelVersion" to referenceModelVersion),
            ),
            InferenceModelComponent.PERFORMANCE_NORMALISATION to active(
                InferenceModelComponent.PERFORMANCE_NORMALISATION,
                "canonical_units",
                "n-bio-6-deterministic-canonical-units-v1",
                mapOf("conversionPolicy" to "deterministic_canonical_units"),
            ),
            InferenceModelComponent.RESISTANCE to active(
                InferenceModelComponent.RESISTANCE,
                "profile_resistance_semantics",
                "n-bio-6-versioned-resistance-profile-v1",
                mapOf("source" to "execution_profile_version.resistance_model"),
            ),
            InferenceModelComponent.RECRUITMENT to active(
                InferenceModelComponent.RECRUITMENT,
                "versioned_recruitment",
                recruitmentModelVersion,
                mapOf("legacyModelVersion" to recruitmentModelVersion),
            ),
            InferenceModelComponent.EXPOSURE to active(
                InferenceModelComponent.EXPOSURE,
                "working_set_exposure",
                exposureModelVersion,
                mapOf(
                    "legacyModelVersion" to exposureModelVersion,
                    "workingSetConfidence" to "0.40",
                    "exposureRule" to "recruitment_weight",
                ),
            ),
            InferenceModelComponent.DEVELOPMENT to active(
                InferenceModelComponent.DEVELOPMENT,
                "neutral_development_benchmark",
                muscleStateModelVersion,
                mapOf(
                    "legacyModelVersion" to muscleStateModelVersion,
                    "priorMedian" to "1.0",
                    "recentStimulus" to "unknown",
                    "recovery" to "unknown",
                ),
            ),
            InferenceModelComponent.TRANSLATION to active(
                InferenceModelComponent.TRANSLATION,
                "same_profile_latest_anchor",
                translationModelVersion,
                mapOf(
                    "legacyModelVersion" to translationModelVersion,
                    "crossProfileTransfer" to "disabled",
                    "anchorUncertainty" to "1.0",
                ),
            ),
        )
        val configs = REQUIRED_NBIO7_COMPONENTS.map { component ->
            active[component] ?: absent(component)
        }
        val manifest = ModelManifest.create(configs.associate { it.component to it.id })
            .requireComponents(REQUIRED_NBIO7_COMPONENTS)
        return ModelManifestBundle(manifest, configs)
    }

    private fun active(
        component: InferenceModelComponent,
        family: String,
        semanticVersion: String,
        parameters: Map<String, String>,
    ): ModelConfigDefinition = ModelConfigDefinition.create(
        component = component,
        modelFamily = family,
        modelName = "benchmark-v0-${component.storageValue}",
        semanticVersion = semanticVersion,
        configSchemaVersion = 1,
        parameters = parameters + ("implementationState" to "active"),
        createdAt = CREATED_AT,
        effectiveAt = CREATED_AT,
    )

    private fun absent(component: InferenceModelComponent): ModelConfigDefinition = ModelConfigDefinition.create(
        component = component,
        modelFamily = component.storageValue,
        modelName = "benchmark-v0-${component.storageValue}-absent",
        semanticVersion = "n-bio-7a-absent-v1",
        configSchemaVersion = 1,
        parameters = mapOf(
            "implementationState" to "not_implemented",
            "outputPolicy" to "blank",
        ),
        createdAt = CREATED_AT,
        effectiveAt = CREATED_AT,
    )
}
