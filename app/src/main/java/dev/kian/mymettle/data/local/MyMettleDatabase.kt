package dev.kian.mymettle.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.kian.mymettle.data.local.dao.WorkoutDao
import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseMuscleLoadEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.ExerciseSetupMediaEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.ExerciseTargetMuscleEntity
import dev.kian.mymettle.data.local.entity.ExperimentEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.ModePrescriptionEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        BodyMeasurementEntity::class,
        ExerciseEntity::class,
        ExerciseMemoryEntity::class,
        ExerciseTargetMuscleEntity::class,
        ExerciseCueEntity::class,
        ExerciseCommonMistakeEntity::class,
        ExerciseSubstitutionEntity::class,
        ExerciseSetupMediaEntity::class,
        ExerciseMuscleLoadEntity::class,
        RoutineVersionEntity::class,
        RoutineSlotEntity::class,
        ModePrescriptionEntity::class,
        TrainingCycleEntity::class,
        CycleCompletedDayEntity::class,
        SessionEntity::class,
        SessionExerciseEntity::class,
        SetRecordEntity::class,
        ExerciseReflectionEntity::class,
        ExperimentEntity::class,
        HealthObservationEntity::class,
        HealthIntegrationStateEntity::class,
        AppStateEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MyMettleDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
}
