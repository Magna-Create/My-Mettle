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
    ],
    indices = [
        Index("userProfileId"),
        Index("referenceProfileId"),
        Index("calculatedAt"),
        Index(value = ["userProfileId", "calculatedAt"]),
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
    val calculatedAt: String,
    val evidenceThrough: String?,
    val evidenceSetCount: Int,
)

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
            entity = SetRecordEntity::class,
            parentColumns = ["id"],
            childColumns = ["setRecordId"],
            onDelete = ForeignKey.CASCADE,
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
        Index("muscleSegmentId"),
        Index(
            value = ["inferenceRunId", "setRecordId", "muscleSegmentId", "side"],
            unique = true,
        ),
    ],
)
data class StimulusEstimateEntity(
    val id: String,
    val inferenceRunId: String,
    val sessionExerciseId: String,
    val setRecordId: String,
    val muscleSegmentId: String,
    val side: String,
    val role: String,
    val recruitmentWeighting: Double,
    val estimatedStimulus: Double,
    val confidence: Double,
    val modelVersion: String,
)

@Entity(
    tableName = "exercise_translation_state",
    primaryKeys = ["inferenceRunId", "executionProfileId"],
    foreignKeys = [
        ForeignKey(
            entity = InferenceRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["inferenceRunId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseExecutionProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionProfileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("inferenceRunId"), Index("executionProfileId")],
)
data class ExerciseTranslationStateEntity(
    val inferenceRunId: String,
    val executionProfileId: String,
    val observedLoadAnchor: Double?,
    val observedLoadUnit: String?,
    val observedLoadUncertainty: Double?,
    val observedRepAnchor: Double?,
    val observedRepUncertainty: Double?,
    val observedDurationSecondsAnchor: Double?,
    val observedDurationUncertainty: Double?,
    val observedDistanceMetresAnchor: Double?,
    val observedDistanceUncertainty: Double?,
    val observedAnchorSetRecordId: String?,
    val sampleCount: Int,
    val updatedAt: String,
    val modelVersion: String,
)
