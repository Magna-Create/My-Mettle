package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaMetricEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RecruitmentProfileVersionEntity
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionConstraintEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity
import dev.kian.mymettle.data.local.entity.SessionSetPrescriptionEntity
import dev.kian.mymettle.data.local.entity.SessionMetricTargetEntity
import dev.kian.mymettle.data.local.entity.SetDraftMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetObservationEntity
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertPerformanceSchemas(values: List<PerformanceSchemaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertPerformanceSchemaMetrics(values: List<PerformanceSchemaMetricEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertRecruitmentProfileVersions(values: List<RecruitmentProfileVersionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertExecutionProfileVersions(values: List<ExecutionProfileVersionEntity>)

    @Upsert
    suspend fun upsertCues(values: List<ExerciseCueEntity>)

    @Upsert
    suspend fun upsertCommonMistakes(values: List<ExerciseCommonMistakeEntity>)

    @Upsert
    suspend fun upsertSubstitutions(values: List<ExerciseSubstitutionEntity>)

    @Upsert
    suspend fun upsertSetupMedia(values: List<ExerciseSetupMediaEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertRecruitmentAllocations(values: List<RecruitmentAllocationEntity>)

    @Upsert
    suspend fun upsertRoutineVersions(values: List<RoutineVersionEntity>)

    @Upsert
    suspend fun upsertRoutineSlots(values: List<RoutineSlotEntity>)

    @Upsert
    suspend fun upsertRoutineMetricTargets(values: List<RoutineMetricTargetEntity>)

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
    suspend fun upsertSessionSetPrescriptions(values: List<SessionSetPrescriptionEntity>)

    @Upsert
    suspend fun upsertSessionMetricTargets(values: List<SessionMetricTargetEntity>)

    @Upsert
    suspend fun upsertSets(values: List<SetRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertSetObservations(values: List<SetObservationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun upsertSetMetricValues(values: List<SetMetricValueEntity>)

    @Upsert
    suspend fun upsertSetDraftMetricValues(values: List<SetDraftMetricValueEntity>)

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

    @Query("SELECT * FROM execution_profile_version WHERE executionProfileId IN (:profileIds) ORDER BY executionProfileId, version")
    suspend fun executionProfileVersions(profileIds: List<String>): List<ExecutionProfileVersionEntity>

    @Query("SELECT * FROM execution_profile_version WHERE id IN (:versionIds) ORDER BY id")
    suspend fun executionProfileVersionsById(versionIds: List<String>): List<ExecutionProfileVersionEntity>

    @Query("SELECT * FROM performance_schema WHERE id IN (:schemaIds)")
    suspend fun performanceSchemas(schemaIds: List<String>): List<PerformanceSchemaEntity>

    @Query("SELECT * FROM performance_schema_metric WHERE performanceSchemaId IN (:schemaIds) ORDER BY performanceSchemaId, metric")
    suspend fun performanceSchemaMetrics(schemaIds: List<String>): List<PerformanceSchemaMetricEntity>

    @Query("SELECT * FROM recruitment_profile_version WHERE id IN (:versionIds)")
    suspend fun recruitmentProfileVersions(versionIds: List<String>): List<RecruitmentProfileVersionEntity>

    @Query("SELECT * FROM recruitment_allocation WHERE recruitmentProfileVersionId IN (:versionIds)")
    suspend fun recruitmentAllocations(versionIds: List<String>): List<RecruitmentAllocationEntity>

    @Query(
        """
        SELECT * FROM routine_slot
        WHERE routineVersionId = :routineVersionId AND daySymbol = :daySymbol
        ORDER BY position
        """,
    )
    suspend fun routineSlots(routineVersionId: String, daySymbol: String): List<RoutineSlotEntity>

    @Query("SELECT * FROM routine_metric_target WHERE routineVersionId = :routineVersionId AND slotId IN (:slotIds) ORDER BY slotId, metric")
    suspend fun routineMetricTargets(routineVersionId: String, slotIds: List<String>): List<RoutineMetricTargetEntity>

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

    @Query("SELECT * FROM session_set_prescription WHERE sessionExerciseId = :sessionExerciseId ORDER BY setIndex")
    suspend fun sessionSetPrescriptions(sessionExerciseId: String): List<SessionSetPrescriptionEntity>

    @Query("SELECT * FROM session_metric_target WHERE sessionSetPrescriptionId IN (:setPrescriptionIds) ORDER BY sessionSetPrescriptionId, metric")
    suspend fun sessionMetricTargets(setPrescriptionIds: List<String>): List<SessionMetricTargetEntity>

    @Query("DELETE FROM session_exercise_target WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun deleteSessionExerciseTargets(sessionExerciseId: String)

    @Query("DELETE FROM session_set_prescription WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun deleteSessionSetPrescriptions(sessionExerciseId: String)

    @Query("DELETE FROM set_record WHERE sessionExerciseId = :sessionExerciseId")
    suspend fun deleteSets(sessionExerciseId: String)

    @Query("UPDATE session_exercise SET position = position + 10000 WHERE sessionId = :sessionId")
    suspend fun offsetSessionExercisePositions(sessionId: String)

    @Query("SELECT * FROM set_record WHERE sessionExerciseId = :sessionExerciseId ORDER BY setIndex")
    suspend fun sets(sessionExerciseId: String): List<SetRecordEntity>

    @Query("SELECT * FROM set_observation WHERE setRecordId IN (:setRecordIds) ORDER BY setRecordId, ordinal")
    suspend fun observations(setRecordIds: List<String>): List<SetObservationEntity>

    @Query("SELECT * FROM set_metric_value WHERE observationId IN (:observationIds) ORDER BY observationId, metric")
    suspend fun metricValues(observationIds: List<String>): List<SetMetricValueEntity>

    @Query("SELECT * FROM set_draft_metric_value WHERE setRecordId IN (:setRecordIds) ORDER BY setRecordId, metric")
    suspend fun draftMetricValues(setRecordIds: List<String>): List<SetDraftMetricValueEntity>

    @Query("DELETE FROM set_draft_metric_value WHERE setRecordId = :setRecordId")
    suspend fun deleteSetDraftMetricValues(setRecordId: String)

    @Query("SELECT * FROM session_review WHERE sessionId = :sessionId LIMIT 1")
    suspend fun sessionReview(sessionId: String): SessionReviewEntity?

    @Query(
        """
        SELECT DISTINCT sr.*
        FROM set_record AS sr
        INNER JOIN session_exercise AS se ON se.id = sr.sessionExerciseId
        INNER JOIN session AS s ON s.id = se.sessionId
        INNER JOIN set_observation AS so ON so.setRecordId = sr.id
        WHERE se.exerciseId = :exerciseId
          AND s.status = 'completed'
          AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
          AND NOT EXISTS (SELECT 1 FROM set_observation newer WHERE newer.supersedesObservationId = so.id)
        ORDER BY so.completedAt DESC, s.startedAt DESC, sr.setIndex DESC
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
        SELECT smv.metric AS metric, smv.canonicalValue AS canonicalValue,
               smv.canonicalUnit AS canonicalUnit, smv.enteredValue AS enteredValue,
               smv.enteredUnit AS enteredUnit, so.id AS observationId,
               sr.id AS setRecordId, so.completedAt AS completedAt
        FROM set_metric_value smv
        INNER JOIN set_observation so ON so.id = smv.observationId
        INNER JOIN set_record sr ON sr.id = so.setRecordId
        INNER JOIN session_exercise se ON se.id = sr.sessionExerciseId
        INNER JOIN session s ON s.id = se.sessionId
        WHERE se.executionProfileVersionId = :executionProfileVersionId
          AND smv.metric = :metric
          AND s.status = 'completed'
          AND sr.warmUp = 0
          AND (:excludeSessionId IS NULL OR s.id != :excludeSessionId)
          AND NOT EXISTS (SELECT 1 FROM set_observation newer WHERE newer.supersedesObservationId = so.id)
        ORDER BY so.completedAt DESC, sr.setIndex DESC
        LIMIT 1
        """,
    )
    suspend fun latestCompletedMetricForExecutionProfileVersion(
        executionProfileVersionId: String,
        metric: String,
        excludeSessionId: String? = null,
    ): MetricEvidenceRow?
}

data class MetricEvidenceRow(
    val metric: String,
    val canonicalValue: Double,
    val canonicalUnit: String,
    val enteredValue: Double,
    val enteredUnit: String,
    val observationId: String,
    val setRecordId: String,
    val completedAt: String,
)
