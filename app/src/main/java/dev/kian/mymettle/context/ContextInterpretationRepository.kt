package dev.kian.mymettle.context

import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.data.local.entity.ContextAnnotationEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.NoteInterpretationRunEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.domain.context.AssertionSemantics
import dev.kian.mymettle.domain.context.ContextAnnotation
import dev.kian.mymettle.domain.context.ContextEvidenceProjector
import dev.kian.mymettle.domain.context.ContextEvidenceView
import dev.kian.mymettle.domain.context.ContextInterpretationProvenance
import dev.kian.mymettle.domain.context.ContextTagId
import dev.kian.mymettle.domain.context.ContextTagRegistry
import dev.kian.mymettle.domain.context.ContextValue
import dev.kian.mymettle.domain.context.ContextValueType
import dev.kian.mymettle.domain.context.NoteScope
import dev.kian.mymettle.domain.context.SourceTextSpan
import dev.kian.mymettle.domain.context.TemporalApplicability
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

sealed interface CanonicalNoteSource {
    val text: String
    val updatedAt: String
    val scope: NoteScope
    val exerciseName: String?

    data class SessionReview(
        val sessionId: String,
        override val text: String,
        override val updatedAt: String,
    ) : CanonicalNoteSource {
        override val scope = NoteScope.SESSION_REVIEW
        override val exerciseName: String? = null
    }

    data class ExerciseReview(
        val sessionExerciseId: String,
        override val text: String,
        override val updatedAt: String,
        override val exerciseName: String?,
    ) : CanonicalNoteSource {
        override val scope = NoteScope.EXERCISE_REVIEW
    }

    companion object {
        fun from(review: SessionReviewEntity): CanonicalNoteSource? = review.note?.takeIf(String::isNotBlank)?.let {
            SessionReview(review.sessionId, it, review.updatedAt)
        }

        fun from(reflection: ExerciseReflectionEntity, exerciseName: String? = null): CanonicalNoteSource? =
            reflection.note?.takeIf(String::isNotBlank)?.let {
                ExerciseReview(reflection.sessionExerciseId, it, reflection.updatedAt, exerciseName)
            }
    }
}

object RawNoteHash {
    fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

enum class InterpretationExecutionOutcome(val storageValue: String) {
    SUCCESS("success"),
    FALLBACK_SUCCESS("fallback_success"),
    NO_ANNOTATIONS("no_annotations"),
}

data class PersistedInterpretation(
    val run: NoteInterpretationRunEntity,
    val annotations: List<ContextAnnotation>,
)

/**
 * Derived-only persistence boundary. It never writes canonical note text, workout evidence, or
 * biological state. Currentness is resolved by hashing the note that its real owner currently holds.
 */
class ContextInterpretationRepository(
    private val database: MyMettleDatabase,
    private val registry: ContextTagRegistry = ContextTagRegistry.V1,
) {
    private val dao get() = database.contextDao()

    suspend fun persist(
        source: CanonicalNoteSource,
        result: NoteInterpretationResult,
        outcome: InterpretationExecutionOutcome,
        fallbackReason: String? = null,
        promptApiLibraryVersion: String? = null,
        createdAt: Instant = Instant.now(),
    ): NoteInterpretationRunEntity {
        val request = NoteInterpretationRequest(source.text, source.scope, source.exerciseName)
        result.validate(request, registry)
        val runId = UUID.randomUUID().toString()
        val run = NoteInterpretationRunEntity(
            id = runId,
            sessionReviewSessionId = (source as? CanonicalNoteSource.SessionReview)?.sessionId,
            exerciseReflectionSessionExerciseId = (source as? CanonicalNoteSource.ExerciseReview)?.sessionExerciseId,
            sourceScope = source.scope.storageValue,
            sourceTextHash = RawNoteHash.sha256(source.text),
            sourceUpdatedAt = source.updatedAt,
            interpreterKind = result.interpreterKind.storageValue,
            interpreterImplementationVersion = result.implementationVersion,
            tagSchemaVersion = registry.schemaVersion,
            promptVersion = result.promptVersion,
            structuredOutputSchemaVersion = result.structuredOutputSchemaVersion,
            promptApiLibraryVersion = promptApiLibraryVersion,
            promptApiStatus = result.capabilities.promptApiStatus.storageValue,
            structuredOutputAvailable = result.capabilities.structuredOutputAvailable,
            systemInstructionAvailable = result.capabilities.systemInstructionAvailable,
            actualBaseModelName = result.capabilities.baseModelName,
            createdAt = createdAt.toString(),
            executionOutcome = outcome.storageValue,
            fallbackReason = fallbackReason,
        )
        val entities = result.annotations.mapIndexed { index, annotation -> annotation.toEntity(runId, index) }
        dao.insertInterpretation(run, entities)
        return run
    }

    suspend fun current(source: CanonicalNoteSource): PersistedInterpretation? {
        val hash = RawNoteHash.sha256(source.text)
        val runs = when (source) {
            is CanonicalNoteSource.SessionReview -> dao.sessionReviewRuns(source.sessionId)
            is CanonicalNoteSource.ExerciseReview -> dao.exerciseReviewRuns(source.sessionExerciseId)
        }
        val run = runs.firstOrNull { it.sourceTextHash == hash && it.tagSchemaVersion == registry.schemaVersion } ?: return null
        val annotations = dao.annotations(run.id).map { it.toDomain(source.scope) }
        annotations.forEach { it.validate(source.text, registry) }
        return PersistedInterpretation(run, annotations)
    }

    suspend fun contextEvidenceView(source: CanonicalNoteSource): ContextEvidenceView {
        val current = current(source) ?: return ContextEvidenceView(emptyList())
        val provenance = ContextInterpretationProvenance(
            runId = current.run.id,
            sourceTextHash = current.run.sourceTextHash,
            interpreterKind = current.run.interpreterKind,
            interpreterImplementationVersion = current.run.interpreterImplementationVersion,
            tagSchemaVersion = current.run.tagSchemaVersion,
            promptVersion = current.run.promptVersion,
            structuredOutputSchemaVersion = current.run.structuredOutputSchemaVersion,
            baseModelName = current.run.actualBaseModelName,
        )
        return ContextEvidenceProjector.project(source.text, current.annotations, provenance, registry)
    }

    suspend fun deleteRun(runId: String): Boolean = dao.deleteRun(runId) > 0

    suspend fun deleteAllDerivedInterpretations(): Int = dao.deleteAllInterpretations()

    suspend fun recentRuns(limit: Int = 40): List<NoteInterpretationRunEntity> = dao.recentRuns(limit)

    suspend fun annotations(runId: String): List<ContextAnnotation> {
        val run = requireNotNull(dao.run(runId)) { "Unknown interpretation run: $runId" }
        val scope = NoteScope.entries.firstOrNull { it.storageValue == run.sourceScope }
            ?: throw IllegalArgumentException("Unknown persisted note scope: ${run.sourceScope}")
        return dao.annotations(runId).map { it.toDomain(scope) }
    }

    private fun ContextAnnotation.toEntity(runId: String, ordinal: Int): ContextAnnotationEntity {
        val boolean = (value as? ContextValue.BooleanValue)?.value
        val number = value as? ContextValue.NumberValue
        val category = (value as? ContextValue.CategoryValue)?.value
        val textAction = (value as? ContextValue.TextActionValue)?.value
        return ContextAnnotationEntity(
            interpretationRunId = runId,
            ordinal = ordinal,
            tagId = tagId.value,
            tagSchemaVersion = tagSchemaVersion,
            valueType = value.type.storageValue,
            booleanValue = boolean,
            numberValue = number?.value,
            numberUnit = number?.unit,
            categoryValue = category,
            textActionValue = textAction,
            assertionSemantics = assertion.storageValue,
            temporalApplicability = temporalApplicability.storageValue,
            approximate = approximate,
            sourceSpanStart = sourceSpan?.startInclusive,
            sourceSpanEnd = sourceSpan?.endExclusive,
            sourceSpanText = sourceSpan?.exactText,
        )
    }

    private fun ContextAnnotationEntity.toDomain(scope: NoteScope): ContextAnnotation {
        val type = ContextValueType.entries.firstOrNull { it.storageValue == valueType }
            ?: throw IllegalArgumentException("Unknown persisted context value type: $valueType")
        val value = when (type) {
            ContextValueType.BOOLEAN -> ContextValue.BooleanValue(requireNotNull(booleanValue))
            ContextValueType.NUMBER -> ContextValue.NumberValue(requireNotNull(numberValue), requireNotNull(numberUnit))
            ContextValueType.CATEGORY -> ContextValue.CategoryValue(requireNotNull(categoryValue))
            ContextValueType.TEXT_ACTION -> ContextValue.TextActionValue(requireNotNull(textActionValue))
        }
        return ContextAnnotation(
            tagId = ContextTagId(tagId),
            tagSchemaVersion = tagSchemaVersion,
            value = value,
            scope = scope,
            assertion = AssertionSemantics.entries.firstOrNull { it.storageValue == assertionSemantics }
                ?: throw IllegalArgumentException("Unknown assertion semantics: $assertionSemantics"),
            temporalApplicability = TemporalApplicability.entries.firstOrNull { it.storageValue == temporalApplicability }
                ?: throw IllegalArgumentException("Unknown temporal applicability: $temporalApplicability"),
            approximate = approximate,
            sourceSpan = sourceSpanStart?.let { start ->
                SourceTextSpan(start, requireNotNull(sourceSpanEnd), requireNotNull(sourceSpanText))
            },
        )
    }
}
