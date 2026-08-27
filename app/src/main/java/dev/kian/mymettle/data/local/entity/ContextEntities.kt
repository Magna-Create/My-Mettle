package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "note_interpretation_run",
    foreignKeys = [
        ForeignKey(
            entity = SessionReviewEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionReviewSessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseReflectionEntity::class,
            parentColumns = ["sessionExerciseId"],
            childColumns = ["exerciseReflectionSessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionReviewSessionId"),
        Index("exerciseReflectionSessionExerciseId"),
        Index("sourceTextHash"),
        Index("createdAt"),
    ],
)
data class NoteInterpretationRunEntity(
    @androidx.room.PrimaryKey val id: String,
    val sessionReviewSessionId: String?,
    val exerciseReflectionSessionExerciseId: String?,
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
) {
    init {
        require(id.isNotBlank())
        require((sessionReviewSessionId != null) xor (exerciseReflectionSessionExerciseId != null)) {
            "An interpretation run must reference exactly one canonical raw-note owner."
        }
        require(sourceTextHash.matches(Regex("[0-9a-f]{64}"))) { "Source text hash must be lowercase SHA-256." }
        require(sourceUpdatedAt.isNotBlank())
        require(interpreterKind.isNotBlank() && interpreterImplementationVersion.isNotBlank())
        require(tagSchemaVersion > 0)
        require(createdAt.isNotBlank())
        require(executionOutcome.isNotBlank())
    }
}

@Entity(
    tableName = "context_annotation",
    primaryKeys = ["interpretationRunId", "ordinal"],
    foreignKeys = [
        ForeignKey(
            entity = NoteInterpretationRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["interpretationRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("interpretationRunId"), Index("tagId")],
)
data class ContextAnnotationEntity(
    val interpretationRunId: String,
    val ordinal: Int,
    val tagId: String,
    val tagSchemaVersion: Int,
    val valueType: String,
    val booleanValue: Boolean?,
    val numberValue: Double?,
    val numberUnit: String?,
    val categoryValue: String?,
    val textActionValue: String?,
    val assertionSemantics: String,
    val temporalApplicability: String,
    val approximate: Boolean,
    val sourceSpanStart: Int?,
    val sourceSpanEnd: Int?,
    val sourceSpanText: String?,
) {
    init {
        require(interpretationRunId.isNotBlank())
        require(ordinal >= 0)
        require(tagId.isNotBlank() && tagSchemaVersion > 0)
        require(valueType.isNotBlank())
        val populated = listOf(booleanValue, numberValue, categoryValue, textActionValue).count { it != null }
        require(populated == 1) { "A context annotation must persist exactly one typed value." }
        require((numberValue == null) == (numberUnit == null)) { "Numeric annotations require a unit and only numeric annotations may have one." }
        require((sourceSpanStart == null) == (sourceSpanEnd == null) && (sourceSpanEnd == null) == (sourceSpanText == null)) {
            "Source span offsets/text must be all null or all populated."
        }
        if (sourceSpanStart != null && sourceSpanEnd != null && sourceSpanText != null) {
            require(sourceSpanStart >= 0 && sourceSpanEnd > sourceSpanStart)
            require(sourceSpanText.length == sourceSpanEnd - sourceSpanStart)
        }
    }
}
