package dev.kian.mymettle.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.kian.mymettle.data.local.dao.HistoryDao
import dev.kian.mymettle.data.local.dao.InferenceDao
import dev.kian.mymettle.data.local.dao.LibraryDao
import dev.kian.mymettle.data.local.dao.ReferenceDao
import dev.kian.mymettle.data.local.dao.WorkoutDao
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
import dev.kian.mymettle.data.local.entity.ExerciseTranslationStateEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.MuscleEntity
import dev.kian.mymettle.data.local.entity.MuscleSegmentEntity
import dev.kian.mymettle.data.local.entity.MuscleStateSnapshotEntity
import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.ProgrammeTargetEntity
import dev.kian.mymettle.data.local.entity.RecruitmentAllocationEntity
import dev.kian.mymettle.data.local.entity.ReferencePhysiologyPriorEntity
import dev.kian.mymettle.data.local.entity.ReferenceProfileEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionConstraintEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseTargetEntity
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.local.entity.SessionTargetEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.StimulusEstimateEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        BodyMeasurementEntity::class,
        MuscleEntity::class,
        MuscleSegmentEntity::class,
        ReferenceProfileEntity::class,
        ReferencePhysiologyPriorEntity::class,
        ExerciseEntity::class,
        ExerciseMemoryEntity::class,
        ExerciseExecutionProfileEntity::class,
        RecruitmentAllocationEntity::class,
        ExerciseCueEntity::class,
        ExerciseCommonMistakeEntity::class,
        ExerciseSubstitutionEntity::class,
        ExerciseSetupMediaEntity::class,
        RoutineVersionEntity::class,
        RoutineSlotEntity::class,
        ProgrammeTargetEntity::class,
        ProgrammeModeConstraintEntity::class,
        TrainingCycleEntity::class,
        CycleCompletedDayEntity::class,
        SessionEntity::class,
        SessionTargetEntity::class,
        SessionConstraintEntity::class,
        SessionExerciseEntity::class,
        SessionExerciseTargetEntity::class,
        SetRecordEntity::class,
        ExerciseReflectionEntity::class,
        SessionReviewEntity::class,
        HealthObservationEntity::class,
        HealthIntegrationStateEntity::class,
        AppStateEntity::class,
        InferenceRunEntity::class,
        MuscleStateSnapshotEntity::class,
        StimulusEstimateEntity::class,
        ExerciseTranslationStateEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class MyMettleDatabase : RoomDatabase() {
    abstract fun workoutDao(): WorkoutDao
    abstract fun historyDao(): HistoryDao
    abstract fun libraryDao(): LibraryDao
    abstract fun referenceDao(): ReferenceDao
    abstract fun inferenceDao(): InferenceDao
}
