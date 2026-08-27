package dev.kian.mymettle.developer

import dev.kian.mymettle.context.ContextInterpretationCoordinator
import dev.kian.mymettle.context.ContextInterpretationRepository
import dev.kian.mymettle.context.InterpreterKind
import dev.kian.mymettle.context.NanoRuntimeCapabilities
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ContextAnnotationEntity
import dev.kian.mymettle.domain.context.CONTEXT_TAG_SCHEMA_VERSION
import dev.kian.mymettle.domain.context.ContextTagId
import dev.kian.mymettle.domain.context.ContextTagRegistry
import org.json.JSONArray
import org.json.JSONObject

data class ContextAnnotationDebugSummary(
    val tagId: String,
    val valueType: String,
    val valueSummary: Any?,
    val unit: String?,
    val assertionSemantics: String,
    val temporalApplicability: String,
    val approximate: Boolean,
    val sourceSpanStart: Int?,
    val sourceSpanEnd: Int?,
    val inferenceEligibility: String,
)

data class ContextInterpretationRunDebugSummary(
    val id: String,
    val sourceScope: String,
    val sourceTextHash: String,
    val sourceUpdatedAt: String,
    val interpreterKind: String,
    val interpreterImplementationVersion: String,
    val tagSchemaVersion: Int,
    val promptVersion: String?,
    val structuredOutputSchemaVersion: Int?,
    val promptApiLibraryVersion: String?,
    val promptApiStatus: String,
    val structuredOutputAvailable: Boolean?,
    val systemInstructionAvailable: Boolean?,
    val actualBaseModelName: String?,
    val createdAt: String,
    val executionOutcome: String,
    val fallbackReason: String?,
    val annotations: List<ContextAnnotationDebugSummary>,
)

data class ContextDeveloperSnapshot(
    val roomSchemaVersion: Int,
    val tagSchemaVersion: Int,
    val capabilities: NanoRuntimeCapabilities,
    val selectedInterpreter: String,
    val recentRuns: List<ContextInterpretationRunDebugSummary>,
)

/**
 * Developer-only observability for 7A.5. Raw note text and exact source-span text are deliberately
 * omitted. Text-action values are redacted because they are user-authored memory, not diagnostics.
 */
class ContextDeveloperDiagnosticsRepository(
    private val database: MyMettleDatabase,
    private val interpretationRepository: ContextInterpretationRepository = ContextInterpretationRepository(database),
    private val coordinator: ContextInterpretationCoordinator = ContextInterpretationCoordinator(database),
) {
    suspend fun snapshot(limit: Int = 40): ContextDeveloperSnapshot {
        val capabilities = coordinator.capabilities()
        val runs = interpretationRepository.recentRuns(limit).map { run ->
            val annotations = database.contextDao().annotations(run.id).map(ContextAnnotationEntity::toDebugSummary)
            ContextInterpretationRunDebugSummary(
                id = run.id,
                sourceScope = run.sourceScope,
                sourceTextHash = run.sourceTextHash,
                sourceUpdatedAt = run.sourceUpdatedAt,
                interpreterKind = run.interpreterKind,
                interpreterImplementationVersion = run.interpreterImplementationVersion,
                tagSchemaVersion = run.tagSchemaVersion,
                promptVersion = run.promptVersion,
                structuredOutputSchemaVersion = run.structuredOutputSchemaVersion,
                promptApiLibraryVersion = run.promptApiLibraryVersion,
                promptApiStatus = run.promptApiStatus,
                structuredOutputAvailable = run.structuredOutputAvailable,
                systemInstructionAvailable = run.systemInstructionAvailable,
                actualBaseModelName = run.actualBaseModelName,
                createdAt = run.createdAt,
                executionOutcome = run.executionOutcome,
                fallbackReason = run.fallbackReason,
                annotations = annotations,
            )
        }
        return ContextDeveloperSnapshot(
            roomSchemaVersion = 14,
            tagSchemaVersion = CONTEXT_TAG_SCHEMA_VERSION,
            capabilities = capabilities,
            selectedInterpreter = if (capabilities.strictExtractionAvailable) {
                InterpreterKind.ML_KIT_NANO.storageValue
            } else {
                InterpreterKind.RULES.storageValue
            },
            recentRuns = runs,
        )
    }

    suspend fun diagnosticJson(limit: Int = 40): String = snapshot(limit).toJson().toString(2)
}

private fun ContextAnnotationEntity.toDebugSummary(): ContextAnnotationDebugSummary {
    val definition = ContextTagRegistry.V1.definitionOrNull(ContextTagId(tagId))
    val valueSummary: Any? = when (valueType) {
        "boolean" -> booleanValue
        "number" -> numberValue
        "category" -> categoryValue
        "text_action" -> "<redacted-user-text-action>"
        else -> "<unsupported-persisted-value-type>"
    }
    return ContextAnnotationDebugSummary(
        tagId = tagId,
        valueType = valueType,
        valueSummary = valueSummary,
        unit = numberUnit,
        assertionSemantics = assertionSemantics,
        temporalApplicability = temporalApplicability,
        approximate = approximate,
        sourceSpanStart = sourceSpanStart,
        sourceSpanEnd = sourceSpanEnd,
        inferenceEligibility = definition?.inferenceEligibility?.storageValue ?: "unregistered",
    )
}

private fun ContextDeveloperSnapshot.toJson(): JSONObject = JSONObject()
    .put("format", "my-mettle-context-interpretation-diagnostic")
    .put("formatVersion", 1)
    .put("roomSchemaVersion", roomSchemaVersion)
    .put("tagSchemaVersion", tagSchemaVersion)
    .put("selectedInterpreter", selectedInterpreter)
    .put("promptApi", JSONObject()
        .put("status", capabilities.promptApiStatus.storageValue)
        .put("structuredOutputAvailable", capabilities.structuredOutputAvailable ?: JSONObject.NULL)
        .put("systemInstructionAvailable", capabilities.systemInstructionAvailable ?: JSONObject.NULL)
        .put("actualBaseModelName", capabilities.baseModelName ?: JSONObject.NULL)
        .put("probeFailure", capabilities.probeFailure ?: JSONObject.NULL))
    .put("recentRuns", JSONArray(recentRuns.map(ContextInterpretationRunDebugSummary::toJson)))

private fun ContextInterpretationRunDebugSummary.toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("sourceScope", sourceScope)
    .put("sourceTextHash", sourceTextHash)
    .put("sourceUpdatedAt", sourceUpdatedAt)
    .put("interpreterKind", interpreterKind)
    .put("interpreterImplementationVersion", interpreterImplementationVersion)
    .put("tagSchemaVersion", tagSchemaVersion)
    .put("promptVersion", promptVersion ?: JSONObject.NULL)
    .put("structuredOutputSchemaVersion", structuredOutputSchemaVersion ?: JSONObject.NULL)
    .put("promptApiLibraryVersion", promptApiLibraryVersion ?: JSONObject.NULL)
    .put("promptApiStatus", promptApiStatus)
    .put("structuredOutputAvailable", structuredOutputAvailable ?: JSONObject.NULL)
    .put("systemInstructionAvailable", systemInstructionAvailable ?: JSONObject.NULL)
    .put("actualBaseModelName", actualBaseModelName ?: JSONObject.NULL)
    .put("createdAt", createdAt)
    .put("executionOutcome", executionOutcome)
    .put("fallbackReason", fallbackReason ?: JSONObject.NULL)
    .put("annotations", JSONArray(annotations.map(ContextAnnotationDebugSummary::toJson)))

private fun ContextAnnotationDebugSummary.toJson(): JSONObject = JSONObject()
    .put("tagId", tagId)
    .put("valueType", valueType)
    .put("value", valueSummary ?: JSONObject.NULL)
    .put("unit", unit ?: JSONObject.NULL)
    .put("assertionSemantics", assertionSemantics)
    .put("temporalApplicability", temporalApplicability)
    .put("approximate", approximate)
    .put("sourceSpanStart", sourceSpanStart ?: JSONObject.NULL)
    .put("sourceSpanEnd", sourceSpanEnd ?: JSONObject.NULL)
    .put("inferenceEligibility", inferenceEligibility)
