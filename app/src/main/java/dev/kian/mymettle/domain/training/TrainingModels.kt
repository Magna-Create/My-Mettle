package dev.kian.mymettle.domain.training

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.MetricTarget
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PrescriptionEvidence
import dev.kian.mymettle.domain.performance.TargetKind

@JvmInline
value class TrainingTargetId(val value: String) {
    init {
        require(value.isNotBlank()) { "TrainingTargetId cannot be blank." }
    }
}

data class TargetSource(
    val description: String,
) {
    init {
        require(description.isNotBlank()) { "Target source cannot be blank." }
    }
}

/**
 * Programme/session intent. This deliberately has no exercise identity: exercise selection and
 * execution-profile recruitment are downstream resolution concerns.
 */
data class TrainingTarget(
    val id: TrainingTargetId,
    val segmentId: MuscleSegmentId,
    val priority: Double,
    val desiredStimulus: Double?,
    val source: TargetSource,
) {
    init {
        require(priority in 0.0..1.0) { "Training-target priority must be between 0 and 1." }
        require(desiredStimulus == null || desiredStimulus >= 0.0) {
            "Desired stimulus cannot be negative."
        }
    }
}

/** Whole-session limits. Modes are configurations of these constraints, not exercise recipes. */
data class SessionConstraints(
    val mode: String,
    val workingSetBudget: Int,
    val exerciseBudget: Int,
    val minimumSetsPerExercise: Int,
    val targetPriorityFloor: Double,
    val timeBudgetSeconds: Int?,
    val source: String,
    val resolverModelVersion: String,
) {
    init {
        require(mode.isNotBlank()) { "Constraint mode cannot be blank." }
        require(workingSetBudget >= 0) { "Working-set budget cannot be negative." }
        require(exerciseBudget >= 0) { "Exercise budget cannot be negative." }
        require(minimumSetsPerExercise > 0) { "Minimum sets per selected exercise must be positive." }
        require(targetPriorityFloor in 0.0..1.0) { "Target-priority floor must be between 0 and 1." }
        require(timeBudgetSeconds == null || timeBudgetSeconds >= 0) { "Time budget cannot be negative." }
        require(source.isNotBlank()) { "Constraint source cannot be blank." }
        require(resolverModelVersion.isNotBlank()) { "Resolver model version cannot be blank." }
    }
}

/** Target intent after applying a session's mode/state context. */
data class ResolvedTrainingTarget(
    val target: TrainingTarget,
    val included: Boolean,
    val resolvedPriority: Double,
    val resolutionModelVersion: String,
) {
    init {
        require(resolvedPriority in 0.0..1.0) { "Resolved target priority must be between 0 and 1." }
        require(resolutionModelVersion.isNotBlank()) { "Target-resolution model version cannot be blank." }
    }
}

data class SetPrescription(
    val index: Int,
    val kind: String,
    val laterality: Laterality,
    val metricTargets: List<MetricTarget>,
) {
    init {
        require(index >= 0) { "Prescription set index cannot be negative." }
        require(kind.isNotBlank()) { "Prescription set kind cannot be blank." }
        require(metricTargets.map { it.metric }.distinct().size == metricTargets.size) {
            "A set prescription cannot target the same metric more than once."
        }
    }
}

/** A resolved, session-specific intervention generated from targets and current evidence. */
data class ExercisePrescription(
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val targetIds: List<TrainingTargetId>,
    val setPrescriptions: List<SetPrescription>,
    val restSeconds: Int,
    val generatedByModelVersion: String,
) {
    init {
        require(setPrescriptions.isNotEmpty()) { "A generated prescription must contain at least one set." }
        require(setPrescriptions.map { it.index }.sorted() == setPrescriptions.indices.toList()) {
            "Set prescriptions must be contiguous and zero-based."
        }
        require(restSeconds >= 0) { "Rest time cannot be negative." }
        require(generatedByModelVersion.isNotBlank()) { "Prescription model version cannot be blank." }
        require(targetIds.distinct().size == targetIds.size) {
            "A prescription cannot reference the same target more than once."
        }
    }

    val sets: Int get() = setPrescriptions.size

    /** Compatibility read model for the existing dynamic-resistance UI; never required. */
    val repRange: IntRange?
        get() = setPrescriptions.firstOrNull()?.metricTargets
            ?.firstOrNull { it.metric == PerformanceMetric.REPETITIONS }
            ?.let { target ->
                when (target.kind) {
                    TargetKind.EXACT -> target.lowerCanonical?.toInt()?.let { it..it }
                    TargetKind.RANGE -> target.lowerCanonical?.toInt()?.let { first ->
                        target.upperCanonical?.toInt()?.let { last -> first..last }
                    }
                    else -> null
                }
            }

    val prescribedLoad: Double?
        get() = setPrescriptions.firstOrNull()?.metricTargets
            ?.firstOrNull { it.metric in setOf(PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.ASSISTANCE) }
            ?.exactOrLower

    val loadEvidence: PrescriptionEvidence?
        get() = setPrescriptions.firstOrNull()?.metricTargets
            ?.firstOrNull { it.metric in setOf(PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.ASSISTANCE) }
            ?.evidence
}
