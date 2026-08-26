package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExecutionProfileVersionEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.RecruitmentProfileVersionEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaMetricEntity

@Dao
interface LibraryDao {
    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    suspend fun activeExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise WHERE id = :exerciseId LIMIT 1")
    suspend fun exercise(exerciseId: String): ExerciseEntity?

    @Upsert
    suspend fun upsertExercise(value: ExerciseEntity)

    @Query("SELECT * FROM exercise_memory WHERE exerciseId = :exerciseId LIMIT 1")
    suspend fun memory(exerciseId: String): ExerciseMemoryEntity?

    @Upsert
    suspend fun upsertMemory(value: ExerciseMemoryEntity)

    @Query("SELECT * FROM exercise_execution_profile WHERE exerciseId = :exerciseId ORDER BY isDefault DESC, name COLLATE NOCASE")
    suspend fun executionProfiles(exerciseId: String): List<ExerciseExecutionProfileEntity>

    @Query("SELECT * FROM execution_profile_version WHERE executionProfileId IN (:profileIds) ORDER BY executionProfileId, version")
    suspend fun executionProfileVersions(profileIds: List<String>): List<ExecutionProfileVersionEntity>

    @Query("SELECT * FROM performance_schema WHERE id IN (:schemaIds)")
    suspend fun performanceSchemas(schemaIds: List<String>): List<PerformanceSchemaEntity>

    @Query("SELECT * FROM performance_schema_metric WHERE performanceSchemaId IN (:schemaIds) ORDER BY performanceSchemaId, metric")
    suspend fun performanceSchemaMetrics(schemaIds: List<String>): List<PerformanceSchemaMetricEntity>

    @Query("SELECT * FROM recruitment_profile_version WHERE id IN (:versionIds)")
    suspend fun recruitmentProfileVersions(versionIds: List<String>): List<RecruitmentProfileVersionEntity>

    @Query("SELECT * FROM recruitment_allocation WHERE recruitmentProfileVersionId IN (:versionIds) ORDER BY recruitmentProfileVersionId, weighting DESC")
    suspend fun recruitmentAllocations(versionIds: List<String>): List<RecruitmentAllocationEntity>

    @Query("SELECT * FROM exercise_cue WHERE exerciseId = :exerciseId ORDER BY ordinal")
    suspend fun cues(exerciseId: String): List<ExerciseCueEntity>

    @Query("SELECT * FROM exercise_common_mistake WHERE exerciseId = :exerciseId ORDER BY ordinal")
    suspend fun commonMistakes(exerciseId: String): List<ExerciseCommonMistakeEntity>

    @Query("SELECT * FROM exercise_substitution WHERE exerciseId = :exerciseId ORDER BY ordinal")
    suspend fun substitutions(exerciseId: String): List<ExerciseSubstitutionEntity>

    @Query("SELECT * FROM exercise_setup_media WHERE exerciseId = :exerciseId ORDER BY sortOrder")
    suspend fun setupMedia(exerciseId: String): List<ExerciseSetupMediaEntity>

    @Query("SELECT * FROM exercise_setup_media WHERE id = :mediaId LIMIT 1")
    suspend fun setupMediaById(mediaId: String): ExerciseSetupMediaEntity?

    @Upsert
    suspend fun upsertSetupMedia(value: ExerciseSetupMediaEntity)

    @Query("DELETE FROM exercise_setup_media WHERE id = :mediaId")
    suspend fun deleteSetupMedia(mediaId: String)
}
