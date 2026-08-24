package dev.kian.mymettle.domain.inference

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentProfileVersionId
import dev.kian.mymettle.domain.exercise.RecruitmentRole
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
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
    BILATERAL("bilateral"),
    ALTERNATING("alternating"),
    NOT_APPLICABLE("not_applicable"),
    UNKNOWN("unknown");

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
    val observationId: String,
    val sessionExerciseId: String,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val metricFamily: MetricFamily,
    val laterality: Laterality,
    val completedAt: Instant,
    val metricValues: List<PerformanceMetricValue>,
    val bodyMassContextKg: Double?,
    val warmUp: Boolean,
    val kind: String,
) {
    init {
        require(setRecordId.isNotBlank()) { "Set evidence needs an id." }
        require(observationId.isNotBlank()) { "Set evidence needs an observation id." }
        require(sessionExerciseId.isNotBlank()) { "Set evidence needs a session-exercise id." }
        require(metricValues.isNotEmpty()) { "Set evidence needs performed metric values." }
        require(metricValues.map { it.metric }.distinct().size == metricValues.size)
        require(bodyMassContextKg == null || bodyMassContextKg > 0.0)
    }

    val hasPerformedWork: Boolean
        get() = metricValues.any { it.canonical.value > 0.0 }

    fun metric(metric: PerformanceMetric): PerformanceMetricValue? = metricValues.firstOrNull { it.metric == metric }
}

data class RecruitmentEvidence(
    val recruitmentProfileVersionId: RecruitmentProfileVersionId,
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
    val observationId: String,
    val sessionExerciseId: String,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val recruitmentProfileVersionId: RecruitmentProfileVersionId,
    val segmentId: MuscleSegmentId,
    val side: BodySide,
    val role: RecruitmentRole,
    val recruitmentWeighting: Double,
    val estimatedStimulus: Double,
    val confidence: Double,
    val modelVersion: String,
) {
    init {
        require(observationId.isNotBlank()) { "Stimulus estimate requires an observation id." }
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

data class PerformanceAnchor(
    val metric: PerformanceMetric,
    val estimate: Estimate<Double>,
    val canonicalUnit: String,
    val sourceObservationId: String,
    val sourceSetRecordId: String,
)

data class ExerciseTranslationState(
    val executionProfileVersionId: ExecutionProfileVersionId,
    val laterality: Laterality,
    val anchors: List<PerformanceAnchor>,
    val sampleCount: Int,
    val updatedAt: Instant,
    val modelVersion: String,
) {
    init {
        require(sampleCount > 0) { "Exercise translation requires performed evidence." }
        require(anchors.map { it.metric }.distinct().size == anchors.size)
        require(modelVersion.isNotBlank()) { "Translation model version cannot be blank." }
    }

    fun anchor(metric: PerformanceMetric): PerformanceAnchor? = anchors.firstOrNull { it.metric == metric }
}

data class UserInferenceSnapshot(
    val run: InferenceRun,
    val muscleStates: List<UserMuscleState>,
    val stimulusEstimates: List<StimulusEstimate>,
    val exerciseTranslationStates: List<ExerciseTranslationState>,
)
