package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionConstraintEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Upsert
    suspend fun upsertProfile(value: UserProfileEntity)

    @Upsert
    suspend fun upsertAppState(value: AppStateEntity)

    @Upsert
    suspend fun upsertBodyMeasurements(values: List<BodyMeasurementEntity>)

    @Upsert
    suspend fun upsertExercises(values: List<ExerciseEntity>)

    @Upsert
    suspend fun upsertExerciseMemory(values: List<ExerciseMemoryEntity>)

    @Upsert
    suspend fun upsertExecutionProfiles(values: List<ExerciseExecutionProfileEntity>)

    @Upsert
    suspend fun upsertCues(values: List<ExerciseCueEntity>)

    @Upsert
    suspend fun upsertCommonMistakes(values: List<ExerciseCommonMistakeEntity>)

    @Upsert
    suspend fun upsertSubstitutions(values: List<ExerciseSubstitutionEntity>)

    @Upsert
    suspend fun upsertSetupMedia(values: List<ExerciseSetupMediaEntity>)

    @Upsert
    suspend fun upsertRecruitmentAllocations(values: List<RecruitmentAllocationEntity>)

    @Upsert
    suspend fun upsertRoutineVersions(values: List<RoutineVersionEntity>)

    @Upsert
    suspend fun upsertRoutineSlots(values: List<RoutineSlotEntity>)

    @Upsert
    suspend fun upsertProgrammeTargets(values: List<ProgrammeTargetEntity>)

    @Upsert
    suspend fun upsertProgrammeModeConstraints(values: List<ProgrammeModeConstraintEntity>)

    @Upsert
    suspend fun upsertTrainingCycles(values: List<TrainingCycleEntity>)

    @Upsert
    suspend fun upsertCompletedDays(values: List<CycleCompletedDayEntity>)

    @Upsert
    suspend fun upsertSessions(values: List<SessionEntity>)

    @Upsert
    suspend fun upsertSessionTargets(values: List<SessionTargetEntity>)

    @Upsert
    suspend fun upsertSessionConstraint(value: SessionConstraintEntity)

    @Upsert
    suspend fun upsertSessionExercises(values: List<SessionExerciseEntity>)

    @Upsert
    suspend fun upsertSessionExerciseTargets(values: List<SessionExerciseTargetEntity>)

    @Upsert
    suspend fun upsertSets(values: List<SetRecordEntity>)

    @Upsert
    suspend fun upsertReflections(values: List<ExerciseReflectionEntity>)

    @Upsert
    suspend fun upsertSessionReview(value: SessionReviewEntity)

    @Upsert
    suspend fun upsertHealthObservations(values: List<HealthObservationEntity>)

    @Upsert
    suspend fun upsertHealthIntegrationState(value: HealthIntegrationStateEntity)

    @Query("SELECT COUNT(*) FROM user_profile")
    suspend fun profileCount(): Int

    @Query("SELECT * FROM app_state WHERE id = 'primary' LIMIT 1")
    fun observeAppState(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 'primary' LIMIT 1")
    suspend fun appState(): AppStateEntity?

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    fun observeActiveExercises(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercise WHERE id IN (:exerciseIds)")
    suspend fun exercises(exerciseIds: List<String>): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    suspend fun allActiveExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_execution_profile WHERE exerciseId IN (:exerciseIds) ORDER BY exerciseId, isDefault DESC, id")
    suspend fun executionProfiles(exerciseIds: List<String>): List<ExerciseExecutionProfileEntity>

    @Query("SELECT * FROM exercise_execution_profile WHERE id IN (:profileIds) ORDER BY id")
    suspend fun executionProfilesById(profileIds: List<String>): List<ExerciseExecutionProfileEntity>

    @Query("SELECT * FROM recruitment_allocation WHERE executionProfileId IN (:executionProfileIds)")
    suspend fun recruitmentAllocations(executionProfileIds: List<String>): List<RecruitmentAllocationEntity>

    @Query(
        """
        SELECT * FROM routine_slot
        WHERE routineVersionId = :routineVersionId AND daySymbol = :daySymbol
        ORDER BY position
        """,
    )
    suspend fun routineSlots(routineVersionId: String, daySymbol: String): List<RoutineSlotEntity>

    @Query("SELECT daySymbol FROM routine_slot WHERE routineVersionId = :routineVersionId GROUP BY daySymbol ORDER BY MIN(position)")
    suspend fun routineDays(routineVersionId: String): List<String>

    @Query(
        """
        SELECT * FROM programme_target
        WHERE routineVersionId = :routineVersionId AND daySymbol = :daySymbol
        ORDER BY priority DESC, id
        """,
    )
    suspend fun programmeTargets(routineVersionId: String, daySymbol: String): List<ProgrammeTargetEntity>

    @Query(
        """
        SELECT * FROM programme_mode_constraint
        WHERE routineVersionId = :routineVersionId AND daySymbol = :daySymbol AND mode = :mode
        LIMIT 1
        """,
    )
    suspend fun programmeModeConstraint(
        routineVersionId: String,
        daySymbol: String,
        mode: String,
    ): ProgrammeModeConstraintEntity?

    @Query("SELECT * FROM body_measurement WHERE weightKg IS NOT NULL ORDER BY recordedAt DESC LIMIT 1")
    suspend fun latestBodyMeasurement(): BodyMeasurementEntity?

    @Query("SELECT * FROM training_cycle WHERE id = :cycleId LIMIT 1")
    suspend fun trainingCycle(cycleId: String): TrainingCycleEntity?

    @Query("SELECT * FROM cycle_completed_day WHERE cycleId = :cycleId")
    suspend fun completedDays(cycleId: String): List<CycleCompletedDayEntity>

    @Query("SELECT * FROM session WHERE id = :sessionId LIMIT 1")
    suspend fun session(sessionId: String): SessionEntity?

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position")
    suspend fun sessionExercises(sessionId: String): List<SessionExerciseEntity>

    @Query("SELECT * FROM session_exercise WHERE id = :sessionExerciseId LIMIT 1")
    suspend fun sessionExercise(sessionExerciseId: String): SessionExerciseEntity?

    @Query("SELECT * FROM session_target WHERE sessionId = :sessionId ORDER BY priority DESC, id")
    suspend fun sessionTargets(sessionId: String): List<SessionTargetEntity>

    @Query("SELECT * FROM session_constraint WHERE sessionId = :sessionId LIMIT 1")
    suspend fun sessionConstraint(sessionId: String): SessionConstraintEntity?

    @Query("SELECT * FROM session_exercise_target WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun sessionExerciseTargets(sessionExerciseId: String): List<SessionExerciseTargetEntity>

    @Query("DELETE FROM session_exercise_target WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun deleteSessionExerciseTargets(sessionExerciseId: String)

    @Query("DELETE FROM set_record WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun deleteSets(sessionExerciseId: String)

    @Query("UPDATE session_exercise SET position = position + 10000 WHERE sessionId = :sessionId")
    suspend fun offsetSessionExercisePositions(sessionId: String)

    @Query("SELECT * FROM set_record WHERE sessionExerciseId = :sessionExerciseId ORDER BY setIndex")
    suspend fun sets(sessionExerciseId: String): List<SetRecordEntity>

    @Query("SELECT * FROM session_review WHERE sessionId = :sessionId LIMIT 1")
    suspend fun sessionReview(sessionId: String): SessionReviewEntity?

    @Query(
        """
        SELECT sr.*
        FROM set_record AS sr
        INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
        INNER JOIN session AS s ON s.id = se.sessionId
        WHERE se.exerciseId = :exerciseId
          AND s.status = 'completed'
          AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
          AND sr.completedAt IS NOT NULL
        ORDER BY sr.completedAt DESC, s.startedAt DESC, sr.setIndex DESC
        LIMIT :limit
        """,
    )
    suspend fun latestCompletedSetsForExercise(
        exerciseId: String,
        excludeSessionId: String? = null,
        limit: Int = 12,
    ): List<SetRecordEntity>

    @Query(
        """
        SELECT sr.*
        FROM set_record AS sr
        INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
        INNER JOIN session AS s ON s.id = se.sessionId
        WHERE se.executionProfileId = :executionProfileId
          AND s.status = 'completed'
          AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
          AND sr.completedAt IS NOT NULL
          AND sr.load IS NOT NULL
          AND sr.warmUp = 0
        ORDER BY sr.completedAt DESC, s.startedAt DESC, sr.setIndex DESC
        LIMIT 1
        """,
    )
    suspend fun latestCompletedLoadForExecutionProfile(
        executionProfileId: String,
        excludeSessionId: String? = null,
    ): SetRecordEntity?
}
