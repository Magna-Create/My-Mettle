package dev.kian.mymettle.domain.context

import java.security.MessageDigest

const val CONTEXT_TAG_SCHEMA_VERSION: Int = 1

@JvmInline
value class ContextTagId(val value: String) {
    init {
        require(value.isNotBlank()) { "Context tag id cannot be blank." }
    }
}

enum class ContextValueType(val storageValue: String) {
    BOOLEAN("boolean"),
    NUMBER("number"),
    CATEGORY("category"),
    TEXT_ACTION("text_action"),
}

enum class NoteScope(val storageValue: String) {
    SESSION_REVIEW("session_review"),
    EXERCISE_REVIEW("exercise_review"),
}

enum class AssertionSemantics(val storageValue: String) {
    ASSERTED("asserted"),
    NEGATED("negated"),
    UNCERTAIN("uncertain"),
}

enum class TemporalApplicability(val storageValue: String) {
    CURRENT("current"),
    HISTORICAL("historical"),
    NEXT_SESSION("next_session"),
    UNSPECIFIED("unspecified"),
}

enum class InferenceEligibility(val storageValue: String) {
    UX_ONLY("ux_only"),
    CONTEXT_ONLY("context_only"),
    CANDIDATE_COVARIATE("candidate_covariate"),
    PROHIBITED_FROM_INFERENCE("prohibited_from_inference"),
}

data class ContextTagDefinition(
    val id: ContextTagId,
    val schemaVersion: Int = CONTEXT_TAG_SCHEMA_VERSION,
    val definition: String,
    val valueType: ContextValueType,
    val validScopes: Set<NoteScope>,
    val quantityDimension: String? = null,
    val canonicalUnit: String? = null,
    val allowedCategories: Set<String> = emptySet(),
    val inferenceEligibility: InferenceEligibility,
    val uxEligible: Boolean = false,
) {
    init {
        require(schemaVersion > 0)
        require(definition.isNotBlank())
        require(validScopes.isNotEmpty())
        require(valueType == ContextValueType.NUMBER || (quantityDimension == null && canonicalUnit == null))
        require(valueType == ContextValueType.CATEGORY || allowedCategories.isEmpty())
        if (valueType == ContextValueType.CATEGORY) require(allowedCategories.isNotEmpty())
    }
}

sealed interface ContextValue {
    val type: ContextValueType

    data class BooleanValue(val value: Boolean) : ContextValue {
        override val type = ContextValueType.BOOLEAN
    }

    data class NumberValue(
        val value: Double,
        val unit: String,
    ) : ContextValue {
        init {
            require(value.isFinite())
            require(unit.isNotBlank())
        }
        override val type = ContextValueType.NUMBER
    }

    data class CategoryValue(val value: String) : ContextValue {
        init { require(value.isNotBlank()) }
        override val type = ContextValueType.CATEGORY
    }

    data class TextActionValue(val value: String) : ContextValue {
        init { require(value.isNotBlank()) }
        override val type = ContextValueType.TEXT_ACTION
    }
}

data class SourceTextSpan(
    val startInclusive: Int,
    val endExclusive: Int,
    val exactText: String,
) {
    init {
        require(startInclusive >= 0)
        require(endExclusive > startInclusive)
        require(exactText.length == endExclusive - startInclusive) {
            "Source span text length must match its offsets."
        }
    }

    fun validateAgainst(source: String) {
        require(endExclusive <= source.length) { "Source span exceeds note bounds." }
        require(source.substring(startInclusive, endExclusive) == exactText) {
            "Source span must match the exact source characters."
        }
    }
}

data class ContextAnnotation(
    val tagId: ContextTagId,
    val tagSchemaVersion: Int,
    val value: ContextValue,
    val scope: NoteScope,
    val assertion: AssertionSemantics,
    val temporalApplicability: TemporalApplicability,
    val approximate: Boolean = false,
    val sourceSpan: SourceTextSpan? = null,
) {
    init {
        require(tagSchemaVersion > 0)
    }

    fun validate(sourceText: String, registry: ContextTagRegistry = ContextTagRegistry.V1) {
        val definition = registry.requireDefinition(tagId, tagSchemaVersion)
        require(value.type == definition.valueType) {
            "${tagId.value} requires ${definition.valueType.storageValue}, not ${value.type.storageValue}."
        }
        require(scope in definition.validScopes) { "${tagId.value} is not valid for ${scope.storageValue}." }
        if (value is ContextValue.NumberValue) {
            require(definition.canonicalUnit == null || value.unit == definition.canonicalUnit) {
                "${tagId.value} requires canonical unit ${definition.canonicalUnit}."
            }
        }
        if (value is ContextValue.CategoryValue) {
            require(value.value in definition.allowedCategories) {
                "Unsupported category ${value.value} for ${tagId.value}."
            }
        }
        sourceSpan?.validateAgainst(sourceText)
    }
}

/**
 * Registered, versioned vocabulary for user-authored context. Interpreters must fail closed on
 * unknown ids; arbitrary model-generated identifiers never enter persistence or N-BIO views.
 */
class ContextTagRegistry private constructor(
    val schemaVersion: Int,
    private val definitions: Map<ContextTagId, ContextTagDefinition>,
) {
    fun all(): List<ContextTagDefinition> = definitions.values.sortedBy { it.id.value }

    fun requireDefinition(id: ContextTagId, version: Int = schemaVersion): ContextTagDefinition {
        require(version == schemaVersion) { "Unsupported context tag schema version: $version" }
        return requireNotNull(definitions[id]) { "Unregistered context tag id: ${id.value}" }
    }

    fun definitionOrNull(id: ContextTagId): ContextTagDefinition? = definitions[id]

    companion object {
        private val BOTH = setOf(NoteScope.SESSION_REVIEW, NoteScope.EXERCISE_REVIEW)
        private val SESSION = setOf(NoteScope.SESSION_REVIEW)
        private val EXERCISE = setOf(NoteScope.EXERCISE_REVIEW)

        private fun boolean(
            id: String,
            definition: String,
            scopes: Set<NoteScope> = BOTH,
            eligibility: InferenceEligibility = InferenceEligibility.CANDIDATE_COVARIATE,
            ux: Boolean = false,
        ) = ContextTagDefinition(
            id = ContextTagId(id),
            definition = definition,
            valueType = ContextValueType.BOOLEAN,
            validScopes = scopes,
            inferenceEligibility = eligibility,
            uxEligible = ux,
        )

        private fun category(
            id: String,
            definition: String,
            categories: Set<String>,
            scopes: Set<NoteScope> = BOTH,
            eligibility: InferenceEligibility = InferenceEligibility.CANDIDATE_COVARIATE,
        ) = ContextTagDefinition(
            id = ContextTagId(id),
            definition = definition,
            valueType = ContextValueType.CATEGORY,
            validScopes = scopes,
            allowedCategories = categories,
            inferenceEligibility = eligibility,
        )

        val V1: ContextTagRegistry = listOf(
            ContextTagDefinition(
                id = ContextTagId("SLEEP_DURATION_REPORTED"),
                definition = "User explicitly reports an approximate or exact sleep duration.",
                valueType = ContextValueType.NUMBER,
                validScopes = SESSION,
                quantityDimension = "time",
                canonicalUnit = "h",
                inferenceEligibility = InferenceEligibility.CANDIDATE_COVARIATE,
            ),
            category("SLEEP_QUALITY_REPORTED", "User qualitatively reports sleep quality.", setOf("poor", "normal", "good"), SESSION),
            boolean("ILLNESS_REPORTED", "User reports being ill/unwell; this is not a diagnosis.", SESSION),
            boolean("MALAISE_REPORTED", "User reports general malaise/feeling rough; this is not a diagnosis.", SESSION),
            boolean("GENERAL_FATIGUE_REPORTED", "User reports unusual general tiredness/fatigue.", SESSION),
            boolean("STRESS_REPORTED", "User reports unusual stress.", SESSION),
            boolean("HYDRATION_CONCERN_REPORTED", "User reports a hydration concern.", SESSION),
            boolean("TIME_PRESSURE_REPORTED", "User reports being under unusual time pressure.", SESSION),
            boolean("SESSION_INTERRUPTED", "User reports that the session was materially interrupted.", SESSION),
            boolean("EXERCISE_FELT_UNUSUALLY_DIFFICULT", "User reports the exercise felt unusually difficult.", EXERCISE),
            boolean("EXERCISE_FELT_UNUSUALLY_EASY", "User reports the exercise felt unusually easy.", EXERCISE),
            boolean("FORM_ISSUE_REPORTED", "User reports a form/execution quality issue.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("FORM_REPORTED_NORMAL", "User explicitly reports normal/expected form.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("SETUP_ISSUE_REPORTED", "User reports a setup problem.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("SETUP_CHANGE_REPORTED", "Possible execution-semantic setup change reported; never mutates profile history.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("TECHNIQUE_CHANGE_REPORTED", "Possible execution-semantic technique change reported; never mutates profile history.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("ROM_CHANGE_REPORTED", "Possible execution-semantic range-of-motion change reported; never mutates profile history.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("GRIP_CHANGE_REPORTED", "Possible execution-semantic grip change reported; never mutates profile history.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("EQUIPMENT_DIFFERENCE_REPORTED", "Possible execution-semantic equipment difference reported; never mutates profile history.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("DISCOMFORT_REPORTED", "User reports discomfort; this does not infer an injury or cause.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            boolean("PAIN_REPORTED", "User reports pain; this does not infer an injury, diagnosis, or cause.", EXERCISE, InferenceEligibility.CONTEXT_ONLY),
            ContextTagDefinition(
                id = ContextTagId("NEXT_SESSION_ACTION"),
                definition = "User-authored action to remember for a future session; product-memory only.",
                valueType = ContextValueType.TEXT_ACTION,
                validScopes = EXERCISE,
                inferenceEligibility = InferenceEligibility.UX_ONLY,
                uxEligible = true,
            ),
            ContextTagDefinition(
                id = ContextTagId("POSITIVE_SETUP_DISCOVERY"),
                definition = "User-authored positive setup discovery worth retaining; product-memory only.",
                valueType = ContextValueType.TEXT_ACTION,
                validScopes = EXERCISE,
                inferenceEligibility = InferenceEligibility.UX_ONLY,
                uxEligible = true,
            ),
        ).associateBy { it.id }.let { ContextTagRegistry(CONTEXT_TAG_SCHEMA_VERSION, it) }
    }
}

data class ContextInterpretationProvenance(
    val runId: String,
    val sourceTextHash: String,
    val interpreterKind: String,
    val interpreterImplementationVersion: String,
    val tagSchemaVersion: Int,
    val promptVersion: String?,
    val structuredOutputSchemaVersion: Int?,
    val baseModelName: String?,
)

data class ContextEvidenceItem(
    val annotation: ContextAnnotation,
    val eligibility: InferenceEligibility,
    val provenance: ContextInterpretationProvenance,
)

/**
 * Typed candidate-model boundary. It deliberately assigns no coefficient, penalty, variance change,
 * dose change, or any other mathematical effect to context.
 */
data class ContextEvidenceView(
    val items: List<ContextEvidenceItem>,
)

object ContextEvidenceProjector {
    fun project(
        sourceText: String,
        annotations: List<ContextAnnotation>,
        provenance: ContextInterpretationProvenance,
        registry: ContextTagRegistry = ContextTagRegistry.V1,
    ): ContextEvidenceView {
        val items = annotations.mapNotNull { annotation ->
            annotation.validate(sourceText, registry)
            val definition = registry.requireDefinition(annotation.tagId, annotation.tagSchemaVersion)
            if (definition.inferenceEligibility != InferenceEligibility.CANDIDATE_COVARIATE) null else {
                ContextEvidenceItem(annotation, definition.inferenceEligibility, provenance)
            }
        }
        return ContextEvidenceView(items)
    }
}

/**
 * Future behaviour-driving models must name an immutable policy like this in their model config.
 * 7A.5 defines only the contract; no current N-BIO model consumes it.
 */
data class ContextConsumptionPolicy(
    val semanticVersion: String,
    val allowedTagIds: Set<ContextTagId>,
) {
    init {
        require(semanticVersion.isNotBlank())
        allowedTagIds.forEach { ContextTagRegistry.V1.requireDefinition(it) }
    }

    val identity: String by lazy {
        val canonical = buildString {
            append("context-consumption|").append(semanticVersion).append('|')
            allowedTagIds.map { it.value }.sorted().forEach { append(it).append(';') }
        }
        MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
