package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.kian.mymettle.data.local.entity.ContextAnnotationEntity
import dev.kian.mymettle.data.local.entity.NoteInterpretationRunEntity

@Dao
abstract class ContextDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertRunInternal(run: NoteInterpretationRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertAnnotationsInternal(annotations: List<ContextAnnotationEntity>)

    @Transaction
    open suspend fun insertInterpretation(
        run: NoteInterpretationRunEntity,
        annotations: List<ContextAnnotationEntity>,
    ) {
        require(annotations.all { it.interpretationRunId == run.id })
        require(annotations.map { it.ordinal }.distinct().size == annotations.size)
        insertRunInternal(run)
        if (annotations.isNotEmpty()) insertAnnotationsInternal(annotations)
    }

    @Query("SELECT * FROM note_interpretation_run WHERE id = :runId LIMIT 1")
    abstract suspend fun run(runId: String): NoteInterpretationRunEntity?

    @Query("SELECT * FROM context_annotation WHERE interpretationRunId = :runId ORDER BY ordinal")
    abstract suspend fun annotations(runId: String): List<ContextAnnotationEntity>

    @Query("SELECT * FROM note_interpretation_run WHERE sessionReviewSessionId = :sessionId ORDER BY createdAt DESC, id DESC")
    abstract suspend fun sessionReviewRuns(sessionId: String): List<NoteInterpretationRunEntity>

    @Query("SELECT * FROM note_interpretation_run WHERE exerciseReflectionSessionExerciseId = :sessionExerciseId ORDER BY createdAt DESC, id DESC")
    abstract suspend fun exerciseReviewRuns(sessionExerciseId: String): List<NoteInterpretationRunEntity>

    @Query("SELECT * FROM note_interpretation_run ORDER BY createdAt DESC, id DESC LIMIT :limit")
    abstract suspend fun recentRuns(limit: Int = 40): List<NoteInterpretationRunEntity>

    @Query("DELETE FROM context_annotation WHERE interpretationRunId = :runId")
    abstract suspend fun deleteAnnotationsForRun(runId: String): Int

    @Query("DELETE FROM note_interpretation_run WHERE id = :runId")
    abstract suspend fun deleteRun(runId: String): Int

    @Query("DELETE FROM note_interpretation_run")
    abstract suspend fun deleteAllInterpretations(): Int
}
