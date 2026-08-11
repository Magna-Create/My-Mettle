package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.AppStateEntity
import dev.kian.mymettle.data.local.entity.BodyMeasurementEntity
import dev.kian.mymettle.data.local.entity.CycleCompletedDayEntity
import dev.kian.mymettle.data.local.entity.ExerciseCommonMistakeEntity
import dev.kian.mymettle.data.local.entity.ExerciseCueEntity
import dev.kian.mymettle.data.local.entity.ExerciseEntity
import dev.kian.mymettle.data.local.entity.ExerciseExecutionProfileEntity
import dev.kian.mymettle.data.local.entity.ExerciseMemoryEntity
import dev.kian.mymettle.data.local.entity.ExerciseReflectionEntity
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.data.local.entity.TrainingCycleEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity

data class LegacyRestTimerSettings(
    val autoStart: Boolean,
    val vibrationEnabled: Boolean,
    val vibrationStrength: String,
    val chimeEnabled: Boolean,
    val backgroundNotificationEnabled: Boolean,
)

data class LegacySetupPhotoPayload(
    val id: String,
    val exerciseId: String,
    val dataUrl: String,
    val createdAt: String,
    val width: Int,
    val height: Int,
    val sortOrder: Int,
)

/** Free text exists only inside the transient Legacy translator; it is never persisted. */
data class LegacyRecruitmentAllocation(
    val executionProfileId: String,
    val muscleLabel: String,
    val weighting: Double,
    val role: String,
    val confidence: Double,
    val source: String?,
)

/** Per-slot Legacy A/B/C data remains transient input to the programme-level constraint projector. */
data class LegacyModePrescription(
    val routineVersionId: String,
    val daySymbol: String,
    val slotId: String,
    val mode: String,
    val included: Boolean,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
    val deferToAnd: Boolean,
)

data class LegacyImportSnapshot(
    val exportedAt: String?,
    val profile: UserProfileEntity,
    val restTimerSettings: LegacyRestTimerSettings,
    val bodyMeasurements: List<BodyMeasurementEntity>,
    val exercises: List<ExerciseEntity>,
    val exerciseMemory: List<ExerciseMemoryEntity>,
    val executionProfiles: List<ExerciseExecutionProfileEntity>,
    val legacyRecruitment: List<LegacyRecruitmentAllocation>,
    val cues: List<ExerciseCueEntity>,
    val commonMistakes: List<ExerciseCommonMistakeEntity>,
    val substitutions: List<ExerciseSubstitutionEntity>,
    val setupPhotos: List<LegacySetupPhotoPayload>,
    val routineVersions: List<RoutineVersionEntity>,
    val routineSlots: List<RoutineSlotEntity>,
    val modePrescriptions: List<LegacyModePrescription>,
    val trainingCycles: List<TrainingCycleEntity>,
    val completedDays: List<CycleCompletedDayEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val sets: List<SetRecordEntity>,
    val reflections: List<ExerciseReflectionEntity>,
    val healthObservations: List<HealthObservationEntity>,
    val healthIntegration: HealthIntegrationStateEntity,
    val appState: AppStateEntity,
)
