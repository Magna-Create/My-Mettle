package dev.kian.mymettle.data.migration

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
import dev.kian.mymettle.data.local.entity.ExerciseSubstitutionEntity
import dev.kian.mymettle.data.local.entity.HealthIntegrationStateEntity
import dev.kian.mymettle.data.local.entity.HealthObservationEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.data.local.entity.RoutineMetricTargetEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaEntity
import dev.kian.mymettle.data.local.entity.PerformanceSchemaMetricEntity
import dev.kian.mymettle.data.local.entity.RecruitmentProfileVersionEntity
import dev.kian.mymettle.data.local.entity.RoutineVersionEntity
import dev.kian.mymettle.data.local.entity.SessionEntity
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SessionSetPrescriptionEntity
import dev.kian.mymettle.data.local.entity.SessionMetricTargetEntity
import dev.kian.mymettle.data.local.entity.SetObservationEntity
import dev.kian.mymettle.data.local.entity.SetMetricValueEntity
import dev.kian.mymettle.data.local.entity.SetDraftMetricValueEntity
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

/** AI-reviewed Native supplement attached to an otherwise immutable Lite backup. */
data class TranslatedRecruitmentAllocation(
    val recruitmentProfileVersionId: String,
    val muscleSegmentId: String,
    val weighting: Double,
    val role: String,
    val confidence: Double,
    val provenanceReference: String,
    val applicableRom: String?,
    val applicableTechnique: String?,
    val resistanceCurveClass: String?,
    val modelVersion: String,
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
    val performanceSchemas: List<PerformanceSchemaEntity>,
    val performanceSchemaMetrics: List<PerformanceSchemaMetricEntity>,
    val recruitmentProfileVersions: List<RecruitmentProfileVersionEntity>,
    val executionProfileVersions: List<ExecutionProfileVersionEntity>,
    val translatedRecruitment: List<TranslatedRecruitmentAllocation>,
    val cues: List<ExerciseCueEntity>,
    val commonMistakes: List<ExerciseCommonMistakeEntity>,
    val substitutions: List<ExerciseSubstitutionEntity>,
    val setupPhotos: List<LegacySetupPhotoPayload>,
    val routineVersions: List<RoutineVersionEntity>,
    val routineSlots: List<RoutineSlotEntity>,
    val routineMetricTargets: List<RoutineMetricTargetEntity>,
    val modePrescriptions: List<LegacyModePrescription>,
    val trainingCycles: List<TrainingCycleEntity>,
    val completedDays: List<CycleCompletedDayEntity>,
    val sessions: List<SessionEntity>,
    val sessionExercises: List<SessionExerciseEntity>,
    val sessionSetPrescriptions: List<SessionSetPrescriptionEntity>,
    val sessionMetricTargets: List<SessionMetricTargetEntity>,
    val sets: List<SetRecordEntity>,
    val setObservations: List<SetObservationEntity>,
    val setMetricValues: List<SetMetricValueEntity>,
    val setDraftMetricValues: List<SetDraftMetricValueEntity>,
    val reflections: List<ExerciseReflectionEntity>,
    val healthObservations: List<HealthObservationEntity>,
    val healthIntegration: HealthIntegrationStateEntity,
    val appState: AppStateEntity,
)
