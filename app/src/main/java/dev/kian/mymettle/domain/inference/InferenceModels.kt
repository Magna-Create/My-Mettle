package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.physiology.Estimate
import dev.kian.mymettle.domain.physiology.ReferenceProfileId
import java.time.Instant

@JvmInline
value class InferenceRunId(val value: String) {
    init {
        require(value.isNotBlank()) { "InferenceRunId cannot be blank." }
    }
}

enum class BodySide(val storageValue: String) {
    LEFT("left"),
    RIGHT("right"),
    BILATERAL("bilateral");

    companion object {
        fun fromStorage(value: String): BodySide = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported body side: $value")
    }
}

data class InferenceRun(
    val id: InferenceRunId,
    val userProfileId: String,
    val modelVersion: String,
    val referenceProfileId: ReferenceProfileId,
    val referenceProfileVersion: Int,
    val referenceModelVersion: String,
    val recruitmentModelVersion: String,
    val stimulusModelVersion: String,
    val muscleStateModelVersion: String,
    val exerciseTranslationModelVersion: String,
    val calculatedAt: Instant,
    val evidenceThrough: Instant?,
    val evidenceSetCount: Int,
) {
    init {
        require(userProfileId.isNotBlank()) { "Inference requires a user profile id." }
        require(modelVersion.isNotBlank()) { "Inference model version cannot be blank." }
        require(referenceProfileVersion > 0) { "Reference profile version must be positive." }
        require(evidenceSetCount >= 0) { "Inference evidence count cannot be negative." }
    }
}

data class CompletedSetEvidence(
    val setRecordId: String,
    val sessionExerciseId: String,
    val executionProfileId: ExecutionProfileId,
    val completedAt: Instant,
    val load: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMetres: Double?,
    val unit: String,
    val rir: Double?,
    val effortSource: String?,
    val warmUp: Boolean,
    val kind: String,
) {
    init {
        require(setRecordId.isNotBlank()) { "Set evidence needs an id." }
        require(sessionExerciseId.isNotBlank()) { "Set evidence needs a session-exercise id." }
        require(unit.isNotBlank()) { "Set evidence needs a stored unit." }
        require(load == null || load >= 0.0) { "Performed load cannot be negative." }
        require(reps == null || reps >= 0) { "Performed reps cannot be negative." }
        require(durationSeconds == null || durationSeconds >= 0) { "Duration cannot be negative." }
        require(distanceMetres == null || distanceMetres >= 0.0) { "Distance cannot be negative." }
        require(rir == null || rir in 0.0..10.0) { "RIR must be between 0 and 10." }
    }

    val hasPerformedWork: Boolean
        get() = (reps ?: 0) > 0 || (durationSeconds ?: 0) > 0 || (distanceMetres ?: 0.0) > 0.0
}

data class RecruitmentEvidence(
    val segmentId: MuscleSegmentId,
    val role: RecruitmentRole,
    val weighting: Double,
    val confidence: Double,
) {
    init {
        require(weighting >= 0.0) { "Recruitment weighting cannot be negative." }
        require(confidence in 0.0..1.0) { "Recruitment confidence must be between 0 and 1." }
    }
}

data class StimulusEstimate(
    val setRecordId: String,
    val sessionExerciseId: String,
    val segmentId: MuscleSegmentId,
    val side: BodySide,
    val role: RecruitmentRole,
    val recruitmentWeighting: Double,
    val estimatedStimulus: Double,
    val confidence: Double,
    val modelVersion: String,
) {
    init {
        require(recruitmentWeighting >= 0.0) { "Recruitment weighting cannot be negative." }
        require(estimatedStimulus >= 0.0) { "Estimated stimulus cannot be negative." }
        require(confidence in 0.0..1.0) { "Stimulus confidence must be between 0 and 1." }
        require(modelVersion.isNotBlank()) { "Stimulus model version cannot be blank." }
    }
}

data class UserMuscleState(
    val segmentId: MuscleSegmentId,
    val side: BodySide,
    val developmentIndex: Estimate<Double>,
    val volumeScale: Estimate<Double>?,
    val structuralCapacityScale: Estimate<Double>?,
    val recentStimulus: Estimate<Double>?,
    val recovery: Estimate<Double>?,
    val evidenceCount: Int,
    val updatedAt: Instant,
    val inferenceModelVersion: String,
) {
    init {
        require(evidenceCount >= 0) { "Muscle-state evidence count cannot be negative." }
        require(inferenceModelVersion.isNotBlank()) { "Muscle-state model version cannot be blank." }
    }
}

data class ExerciseTranslationState(
    val executionProfileId: ExecutionProfileId,
    val observedLoadAnchor: Estimate<Double>?,
    val observedLoadUnit: String?,
    val observedRepAnchor: Estimate<Double>?,
    val observedDurationSecondsAnchor: Estimate<Double>?,
    val observedDistanceMetresAnchor: Estimate<Double>?,
    val observedRirAnchor: Double?,
    val sampleCount: Int,
    val updatedAt: Instant,
    val modelVersion: String,
) {
    init {
        require(sampleCount > 0) { "Exercise translation requires performed evidence." }
        require(observedLoadAnchor == null || !observedLoadUnit.isNullOrBlank()) {
            "A load anchor requires its stored unit."
        }
        require(modelVersion.isNotBlank()) { "Translation model version cannot be blank." }
    }
}

data class UserInferenceSnapshot(
    val run: InferenceRun,
    val muscleStates: List<UserMuscleState>,
    val stimulusEstimates: List<StimulusEstimate>,
    val exerciseTranslationStates: List<ExerciseTranslationState>,
)
