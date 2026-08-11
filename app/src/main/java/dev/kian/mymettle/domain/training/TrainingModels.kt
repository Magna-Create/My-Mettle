package dev.kian.mymettle.domain.training

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExerciseId

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

/** A resolved, session-specific intervention generated from targets and current evidence. */
data class ExercisePrescription(
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val targetIds: List<TrainingTargetId>,
    val sets: Int,
    val repRange: IntRange,
    val targetRir: Double?,
    val prescribedLoad: Double?,
    val restSeconds: Int,
    val generatedByModelVersion: String,
) {
    init {
        require(sets > 0) { "A generated prescription must contain at least one set." }
        require(repRange.first > 0 && repRange.last >= repRange.first) {
            "A generated prescription must contain a valid positive rep range."
        }
        require(targetRir == null || targetRir in 0.0..10.0) { "Target RIR must be between 0 and 10." }
        require(prescribedLoad == null || prescribedLoad >= 0.0) { "Prescribed load cannot be negative." }
        require(restSeconds >= 0) { "Rest time cannot be negative." }
        require(generatedByModelVersion.isNotBlank()) { "Prescription model version cannot be blank." }
        require(targetIds.distinct().size == targetIds.size) {
            "A prescription cannot reference the same target more than once."
        }
    }
}
