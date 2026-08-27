package dev.kian.mymettle.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "inference_run",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReferenceProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["referenceProfileId"],
        ),
        ForeignKey(
            entity = InferenceModelManifestEntity::class,
            parentColumns = ["id"],
            childColumns = ["modelManifestId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("userProfileId"),
        Index("referenceProfileId"),
        Index("modelManifestId"),
        Index("calculatedAt"),
        Index(value = ["userProfileId", "executionMode", "calculatedAt"]),
    ],
)
data class InferenceRunEntity(
    val id: String,
    val userProfileId: String,
    val modelVersion: String,
    val referenceProfileId: String,
    val referenceProfileVersion: Int,
    val referenceModelVersion: String,
    val recruitmentModelVersion: String,
    val stimulusModelVersion: String,
    val muscleStateModelVersion: String,
    val exerciseTranslationModelVersion: String,
    val modelManifestId: String,
    val executionMode: String,
    val semanticsMode: String,
    val calculatedAt: String,
    val evidenceThrough: String?,
    val evidenceSetCount: Int,
    val evidenceObservationCount: Int,
    val effectiveIndependentSessionCount: Int,
)

/** Legacy N-BIO-4/6 benchmark projection. Candidate v7 adaptive state uses adaptive_muscle_state. */
@Entity(
    tableName = "muscle_state_snapshot",
    primaryKeys = ["inferenceRunId", "muscleSegmentId", "side"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MuscleSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleSegmentId"],
        ),
    ],
    indices = [Index("inferenceRunId"), Index("muscleSegmentId")],
)
data class MuscleStateSnapshotEntity(
    val inferenceRunId: String,
    val muscleSegmentId: String,
    val side: String,
    val developmentIndex: Double,
    val developmentUncertainty: Double?,
    val volumeScale: Double?,
    val volumeScaleUncertainty: Double?,
    val structuralCapacityScale: Double?,
    val structuralCapacityScaleUncertainty: Double?,
    val recentStimulus: Double?,
    val recentStimulusUncertainty: Double?,
    val recovery: Double?,
    val recoveryUncertainty: Double?,
    val evidenceCount: Int,
    val updatedAt: String,
    val inferenceModelVersion: String,
)

/** Legacy N-BIO-4/6 recruitment-weighted working-set benchmark projection. */
@Entity(
    tableName = "stimulus_estimate",
    primaryKeys = ["id"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SessionExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionExerciseId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["setObservationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["setRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExecutionProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecruitmentProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recruitmentProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MuscleSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["muscleSegmentId"],
        ),
    ],
    indices = [
        Index("inferenceRunId"),
        Index("sessionExerciseId"),
        Index("setRecordId"),
        Index("setObservationId"),
        Index("executionProfileVersionId"),
        Index("recruitmentProfileVersionId"),
        Index("muscleSegmentId"),
        Index(
            value = ["inferenceRunId", "setObservationId", "muscleSegmentId", "side"],
            unique = true,
        ),
    ],
)
data class StimulusEstimateEntity(
    val id: String,
    val inferenceRunId: String,
    val sessionExerciseId: String,
    val setRecordId: String,
    val setObservationId: String,
    val executionProfileVersionId: String,
    val recruitmentProfileVersionId: String,
    val muscleSegmentId: String,
    val side: String,
    val role: String,
    val recruitmentWeighting: Double,
    val estimatedStimulus: Double,
    val confidence: Double,
    val modelVersion: String,
)

/** Legacy N-BIO-4/6 same-profile anchor benchmark projection. */
@Entity(
    tableName = "exercise_translation_state",
    primaryKeys = ["inferenceRunId", "executionProfileVersionId", "side"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExecutionProfileVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileVersionId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("inferenceRunId"), Index("executionProfileVersionId")],
)
data class ExerciseTranslationStateEntity(
    val inferenceRunId: String,
    val executionProfileVersionId: String,
    val side: String,
    val sampleCount: Int,
    val updatedAt: String,
    val modelVersion: String,
)

@Entity(
    tableName = "exercise_translation_metric_anchor",
    primaryKeys = ["inferenceRunId", "executionProfileVersionId", "side", "metric"],
    foreignKeys = [
        ForeignKey(
            entity = ExerciseTranslationStateEntity::class,
            parentColumns = ["inferenceRunId", "executionProfileVersionId", "side"],
            childColumns = ["inferenceRunId", "executionProfileVersionId", "side"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetObservationEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceObservationId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SetRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceSetRecordId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["inferenceRunId", "executionProfileVersionId", "side"]),
        Index("sourceObservationId"),
        Index("sourceSetRecordId"),
    ],
)
data class ExerciseTranslationMetricAnchorEntity(
    val inferenceRunId: String,
    val executionProfileVersionId: String,
    val side: String,
    val metric: String,
    val canonicalValue: Double,
    val canonicalUnit: String,
    val uncertainty: Double?,
    val sourceObservationId: String,
    val sourceSetRecordId: String,
)
