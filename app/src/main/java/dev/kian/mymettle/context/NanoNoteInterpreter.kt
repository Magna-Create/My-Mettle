package dev.kian.mymettle.context

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generateTypedContentRequest
import com.google.mlkit.genai.schema.annotations.Generable
import com.google.mlkit.genai.schema.annotations.Guide
import dev.kian.mymettle.domain.context.AssertionSemantics
import dev.kian.mymettle.domain.context.ContextAnnotation
import dev.kian.mymettle.domain.context.ContextTagId
import dev.kian.mymettle.domain.context.ContextTagRegistry
import dev.kian.mymettle.domain.context.ContextValue
import dev.kian.mymettle.domain.context.ContextValueType
import dev.kian.mymettle.domain.context.NoteScope
import dev.kian.mymettle.domain.context.SourceTextSpan
import dev.kian.mymettle.domain.context.TemporalApplicability

@Generable("A bounded list of context facts explicitly stated by the user in one workout note.")
data class NanoContextAnnotationBatch(
    @Guide(description = "Only annotations directly supported by the note.", minItems = 0, maxItems = 12)
    val annotations: List<NanoContextAnnotationItem>,
)

@Generable("One bounded context annotation. Never diagnose or infer unstated causes.")
data class NanoContextAnnotationItem(
    @Guide(
        description = "Registered My Mettle context tag id.",
        enumValues = [
            "SLEEP_DURATION_REPORTED",
            "SLEEP_QUALITY_REPORTED",
            "ILLNESS_REPORTED",
            "MALAISE_REPORTED",
            "GENERAL_FATIGUE_REPORTED",
            "STRESS_REPORTED",
            "HYDRATION_CONCERN_REPORTED",
            "TIME_PRESSURE_REPORTED",
            "SESSION_INTERRUPTED",
            "EXERCISE_FELT_UNUSUALLY_DIFFICULT",
            "EXERCISE_FELT_UNUSUALLY_EASY",
            "FORM_ISSUE_REPORTED",
            "FORM_REPORTED_NORMAL",
            "SETUP_ISSUE_REPORTED",
            "SETUP_CHANGE_REPORTED",
            "TECHNIQUE_CHANGE_REPORTED",
            "ROM_CHANGE_REPORTED",
            "GRIP_CHANGE_REPORTED",
            "EQUIPMENT_DIFFERENCE_REPORTED",
            "DISCOMFORT_REPORTED",
            "PAIN_REPORTED",
            "NEXT_SESSION_ACTION",
            "POSITIVE_SETUP_DISCOVERY",
        ],
    )
    val tagId: String,
    @Guide(description = "Value type required by the registered tag.", enumValues = ["boolean", "number", "category", "text_action"])
    val valueType: String,
    @Guide(description = "Canonical value. Booleans are true/false; numbers use plain decimal text; actions preserve the user's wording.")
    val valueText: String,
    @Guide(description = "Canonical unit for numeric values, otherwise empty string.")
    val unit: String,
    @Guide(description = "Whether the user explicitly negated or was uncertain about the assertion.", enumValues = ["asserted", "negated", "uncertain"])
    val assertion: String,
    @Guide(description = "When the statement applies.", enumValues = ["current", "historical", "next_session", "unspecified"])
    val temporalApplicability: String,
    @Guide(description = "True only when the user's quantity wording is approximate.")
    val approximate: Boolean,
    @Guide(description = "Zero-based UTF-16 start offset of the exact supporting span in the supplied note.", minimum = 0.0)
    val sourceStart: Int,
    @Guide(description = "Exclusive UTF-16 end offset of the exact supporting span in the supplied note.", minimum = 1.0)
    val sourceEnd: Int,
)

class NanoUnavailableException(
    val capabilities: NanoRuntimeCapabilities,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * ML Kit Prompt adapter. It receives only the raw user-authored note, note scope, optional exercise
 * name and bounded ontology definitions. It has no dependency on Health Connect, workout history,
 * physiological records, posterior state, or any biological repository.
 */
class NanoNoteInterpreter(
    private val registry: ContextTagRegistry = ContextTagRegistry.V1,
) : NoteInterpreter {
    suspend fun capabilities(): NanoRuntimeCapabilities {
        val model = Generation.getClient()
        return try {
            val status = mapStatus(model.checkStatus())
            if (status != PromptApiStatus.AVAILABLE) {
                NanoRuntimeCapabilities(promptApiStatus = status)
            } else {
                NanoRuntimeCapabilities(
                    promptApiStatus = status,
                    structuredOutputAvailable = runCatching { model.isStructuredOutputFeatureAvailable() }.getOrDefault(false),
                    systemInstructionAvailable = runCatching { model.isSystemPromptAvailable() }.getOrDefault(false),
                    baseModelName = runCatching { model.getBaseModelName() }.getOrNull(),
                )
            }
        } catch (error: Throwable) {
            NanoRuntimeCapabilities(
                promptApiStatus = PromptApiStatus.ERROR,
                probeFailure = error::class.java.simpleName,
            )
        } finally {
            model.close()
        }
    }

    override suspend fun interpret(request: NoteInterpretationRequest): NoteInterpretationResult {
        val model = Generation.getClient()
        var capabilities = NanoRuntimeCapabilities(PromptApiStatus.NOT_CHECKED)
        try {
            val status = try {
                mapStatus(model.checkStatus())
            } catch (error: Throwable) {
                capabilities = NanoRuntimeCapabilities(PromptApiStatus.ERROR, probeFailure = error::class.java.simpleName)
                throw NanoUnavailableException(capabilities, "Prompt API status probe failed.", error)
            }
            if (status != PromptApiStatus.AVAILABLE) {
                capabilities = NanoRuntimeCapabilities(promptApiStatus = status)
                throw NanoUnavailableException(capabilities, "Prompt API is ${status.storageValue}; Save must not download a model.")
            }

            capabilities = NanoRuntimeCapabilities(
                promptApiStatus = status,
                structuredOutputAvailable = runCatching { model.isStructuredOutputFeatureAvailable() }.getOrDefault(false),
                systemInstructionAvailable = runCatching { model.isSystemPromptAvailable() }.getOrDefault(false),
                baseModelName = runCatching { model.getBaseModelName() }.getOrNull(),
            )
            if (!capabilities.strictExtractionAvailable) {
                throw NanoUnavailableException(
                    capabilities,
                    "Prompt API is available but Structured Output is unavailable; free-form model JSON is intentionally not accepted.",
                )
            }

            val prompt = buildPrompt(request)
            val baseRequest = if (capabilities.systemInstructionAvailable == true) {
                generateContentRequest(SystemInstruction(SYSTEM_INSTRUCTION), TextPart(prompt)) {
                    temperature = 0.1f
                    topK = 1
                    candidateCount = 1
                    maxOutputTokens = 1536
                    enableThinking = false
                }
            } else {
                generateContentRequest(TextPart("$SYSTEM_INSTRUCTION\n\n$prompt")) {
                    temperature = 0.1f
                    topK = 1
                    candidateCount = 1
                    maxOutputTokens = 1536
                    enableThinking = false
                }
            }
            val typedRequest = generateTypedContentRequest(
                generateContentRequest = baseRequest,
                outputClass = NanoContextAnnotationBatch::class,
                includeSchemaInPrompt = true,
            )
            val response = model.generateContent(typedRequest)
            val batch = response.candidates.firstOrNull()?.response
                ?: throw IllegalArgumentException("Structured Output returned no parsed candidate.")
            val annotations = batch.annotations.map { it.toDomain(request) }
            val result = NoteInterpretationResult(
                interpreterKind = InterpreterKind.ML_KIT_NANO,
                implementationVersion = IMPLEMENTATION_VERSION,
                annotations = annotations,
                promptVersion = PROMPT_VERSION,
                structuredOutputSchemaVersion = STRUCTURED_OUTPUT_SCHEMA_VERSION,
                capabilities = capabilities,
            )
            result.validate(request, registry)
            return result
        } finally {
            model.close()
        }
    }

    private fun NanoContextAnnotationItem.toDomain(request: NoteInterpretationRequest): ContextAnnotation {
        val tag = ContextTagId(tagId)
        val definition = registry.requireDefinition(tag)
        val type = ContextValueType.entries.firstOrNull { it.storageValue == valueType }
            ?: throw IllegalArgumentException("Unsupported structured value type: $valueType")
        require(type == definition.valueType) { "Structured value type does not match registered tag $tagId." }
        val value = when (type) {
            ContextValueType.BOOLEAN -> when (valueText.trim().lowercase()) {
                "true" -> ContextValue.BooleanValue(true)
                "false" -> ContextValue.BooleanValue(false)
                else -> throw IllegalArgumentException("Boolean structured value must be true or false.")
            }
            ContextValueType.NUMBER -> ContextValue.NumberValue(
                valueText.trim().toDoubleOrNull() ?: throw IllegalArgumentException("Numeric structured value is not a number."),
                unit.trim(),
            )
            ContextValueType.CATEGORY -> ContextValue.CategoryValue(valueText.trim())
            ContextValueType.TEXT_ACTION -> ContextValue.TextActionValue(valueText.trim())
        }
        require(sourceStart >= 0 && sourceEnd > sourceStart && sourceEnd <= request.sourceText.length) {
            "Structured source span is outside the canonical note."
        }
        val span = SourceTextSpan(
            sourceStart,
            sourceEnd,
            request.sourceText.substring(sourceStart, sourceEnd),
        )
        val parsedAssertion = AssertionSemantics.entries.firstOrNull { it.storageValue == assertion }
            ?: throw IllegalArgumentException("Unknown structured assertion semantics: $assertion")
        val parsedTemporal = TemporalApplicability.entries.firstOrNull { it.storageValue == temporalApplicability }
            ?: throw IllegalArgumentException("Unknown structured temporal applicability: $temporalApplicability")
        return ContextAnnotation(
            tagId = tag,
            tagSchemaVersion = registry.schemaVersion,
            value = value,
            scope = request.scope,
            assertion = parsedAssertion,
            temporalApplicability = parsedTemporal,
            approximate = approximate,
            sourceSpan = span,
        )
    }

    private fun buildPrompt(request: NoteInterpretationRequest): String {
        val allowed = registry.all().filter { request.scope in it.validScopes }
        return buildString {
            appendLine("Extract only explicit user-reported context from this single workout note.")
            appendLine("Scope: ${request.scope.storageValue}")
            request.exerciseName?.let { appendLine("Exercise name (context only): $it") }
            appendLine("Registered tags for this scope:")
            allowed.forEach { definition ->
                append("- ").append(definition.id.value)
                    .append(" | ").append(definition.valueType.storageValue)
                    .append(" | ").append(definition.definition)
                definition.canonicalUnit?.let { append(" | unit=").append(it) }
                if (definition.allowedCategories.isNotEmpty()) {
                    append(" | categories=").append(definition.allowedCategories.sorted().joinToString(","))
                }
                appendLine()
            }
            appendLine("Rules:")
            appendLine("- Return zero annotations when wording is unsupported or ambiguous.")
            appendLine("- Negation must never become a positive assertion.")
            appendLine("- Historical statements must not be marked current.")
            appendLine("- Never diagnose illness, pain, discomfort, injury, or causes.")
            appendLine("- Do not invent tag ids or values.")
            appendLine("- sourceStart/sourceEnd must delimit the exact characters supporting each annotation.")
            appendLine("- NUMBER valueText is decimal text and unit must be the registered canonical unit.")
            appendLine("- For non-NUMBER tags, unit must be empty.")
            appendLine("- Do not provide reasoning or explanation.")
            appendLine()
            appendLine("Raw note begins after this marker:")
            append(request.sourceText)
        }
    }

    private fun mapStatus(status: Int): PromptApiStatus = when (status) {
        FeatureStatus.UNAVAILABLE -> PromptApiStatus.UNAVAILABLE
        FeatureStatus.DOWNLOADABLE -> PromptApiStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> PromptApiStatus.DOWNLOADING
        FeatureStatus.AVAILABLE -> PromptApiStatus.AVAILABLE
        else -> PromptApiStatus.ERROR
    }

    companion object {
        const val IMPLEMENTATION_VERSION = "ml-kit-prompt-beta4-context-v1"
        const val PROMPT_VERSION = "context-extraction-v1"
        const val STRUCTURED_OUTPUT_SCHEMA_VERSION = 1
        const val PROMPT_API_LIBRARY_VERSION = "1.0.0-beta4"

        private const val SYSTEM_INSTRUCTION =
            "You are a bounded entity extractor for My Mettle. Return only schema-constrained annotations " +
                "that the user's note explicitly supports. Do not reason aloud, diagnose, advise, or infer hidden biological state."
    }
}
