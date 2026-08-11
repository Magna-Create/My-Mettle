package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.kian.mymettle.data.local.entity.ExerciseTranslationStateEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.MuscleSegmentEntity
import dev.kian.mymettle.data.local.entity.MuscleStateSnapshotEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.ReferenceProfileEntity
import dev.kian.mymettle.data.local.entity.StimulusEstimateEntity

data class CompletedSetEvidenceRow(
    val setRecordId: String,
    val sessionExerciseId: String,
    val executionProfileId: String,
    val completedAt: String,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMetres: Double?,
    val unit: String,
    val rir: Double?,
    val effortSource: String?,
    val warmUp: Boolean,
    val kind: String,
)

@Dao
interface InferenceDao {
    @Query("SELECT id FROM user_profile ORDER BY createdAt, id")
    suspend fun userProfileIds(): List<String>

    @Query(
        """
        SELECT
            sr.id AS setRecordId,
            sr.sessionExerciseId AS sessionExerciseId,
            se.executionProfileId AS executionProfileId,
            sr.completedAt AS completedAt,
            sr.load AS load,
            sr.reps AS reps,
            sr.durationSeconds AS durationSeconds,
            sr.distanceMetres AS distanceMetres,
            sr.unit AS unit,
            sr.rir AS rir,
            sr.effortSource AS effortSource,
            sr.warmUp AS warmUp,
            sr.kind AS kind
        FROM set_record AS sr
        INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
        INNER JOIN session AS s ON s.id = se.sessionId
        WHERE s.status = 'completed'
          AND s.excludedFromInsights = 0
          AND sr.completedAt IS NOT NULL
        ORDER BY sr.completedAt, sr.id
        """,
    )
    suspend fun completedSetEvidence(): List<CompletedSetEvidenceRow>

    @Query("SELECT * FROM recruitment_allocation WHERE executionProfileId IN (:executionProfileIds)")
    suspend fun recruitmentAllocations(
        executionProfileIds: List<String>,
    ): List<RecruitmentAllocationEntity>

    @Query("SELECT * FROM muscle_segment WHERE statePolicy != 'SHARED_PARENT' ORDER BY id")
    suspend fun independentlyTrackedSegments(): List<MuscleSegmentEntity>

    @Query("SELECT * FROM reference_profile ORDER BY version DESC, id LIMIT 1")
    suspend fun latestReferenceProfile(): ReferenceProfileEntity?

    @Query(
        """
        SELECT * FROM inference_run
        WHERE userProfileId = :userProfileId
        ORDER BY calculatedAt DESC, id DESC
        LIMIT 1
        """,
    )
    suspend fun latestInferenceRun(userProfileId: String): InferenceRunEntity?

    @Query("SELECT * FROM muscle_state_snapshot WHERE inferenceRunId = :inferenceRunId ORDER BY muscleSegmentId, side")
    suspend fun muscleStateSnapshots(inferenceRunId: String): List<MuscleStateSnapshotEntity>

    @Query("SELECT * FROM stimulus_estimate WHERE inferenceRunId = :inferenceRunId ORDER BY setRecordId, muscleSegmentId, side")
    suspend fun stimulusEstimates(inferenceRunId: String): List<StimulusEstimateEntity>

    @Query("SELECT * FROM exercise_translation_state WHERE inferenceRunId = :inferenceRunId ORDER BY executionProfileId")
    suspend fun exerciseTranslationStates(inferenceRunId: String): List<ExerciseTranslationStateEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInferenceRun(value: InferenceRunEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMuscleStateSnapshots(values: List<MuscleStateSnapshotEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStimulusEstimates(values: List<StimulusEstimateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExerciseTranslationStates(values: List<ExerciseTranslationStateEntity>)

    @Query("DELETE FROM inference_run WHERE userProfileId = :userProfileId")
    suspend fun deleteDerivedState(userProfileId: String)
}
