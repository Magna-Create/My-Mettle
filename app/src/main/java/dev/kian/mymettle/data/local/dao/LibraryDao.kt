package dev.kian.mymettle.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.ExerciseTargetMuscleEntity

@Dao
interface LibraryDao {
    @Query("SELECT * FROM exercise WHERE archived = 0 ORDER BY name COLLATE NOCASE")
    suspend fun activeExercises(): List<ExerciseEntity>

    @Query("SELECT * FROM exercise_memory WHERE exerciseId = :exerciseId LIMIT 1")
    suspend fun memory(exerciseId: String): ExerciseMemoryEntity?

    @Query("SELECT * FROM exercise_target_muscle WHERE exerciseId = :exerciseId ORDER BY ordinal")
    suspend fun targetMuscles(exerciseId: String): List<ExerciseTargetMuscleEntity>

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
