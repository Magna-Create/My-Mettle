package dev.kian.mymettle.domain.exercise

import dev.kian.mymettle.domain.anatomy.MuscleSegmentId

@JvmInline
value class ExerciseId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExerciseId cannot be blank." }
    }
}

@JvmInline
value class ExecutionProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ExecutionProfileId cannot be blank." }
    }
}

enum class TrackingMetric(val storageValue: String) {
    LOAD_REPS("load_reps"),
    REPS("reps"),
    DURATION("duration"),
    DISTANCE("distance");

    companion object {
        fun fromStorage(value: String): TrackingMetric = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported tracking metric: $value")
    }
}

enum class LoadRelationship(val storageValue: String) {
    EXTERNAL("external"),
    ASSISTANCE("assistance"),
    BODYWEIGHT("bodyweight"),
    BODYWEIGHT_PLUS_EXTERNAL("bodyweight_plus_external"),
    NONE("none");

    companion object {
        fun fromStorage(value: String): LoadRelationship = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported load relationship: $value")
    }
}

enum class EntryBasis(val storageValue: String) {
    TOTAL("total"),
    PER_HAND("per_hand"),
    PER_SIDE("per_side");

    companion object {
        fun fromStorage(value: String): EntryBasis = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported entry basis: $value")
    }
}

data class ExerciseTracking(
    val defaultUnit: String,
    val metric: TrackingMetric,
    val loadRelationship: LoadRelationship,
    val entryBasis: EntryBasis,
)

data class LoadResolution(
    val minimumLoad: Double?,
    val maximumLoad: Double?,
    val increment: Double?,
    val allowedValues: List<Double>,
)

data class EquipmentProfile(
    val description: String,
)

enum class RecruitmentRole(val storageValue: String) {
    PRIME("prime"),
    SYNERGIST("synergist"),
    STABILISER("stabiliser");

    companion object {
        fun fromStorage(value: String): RecruitmentRole = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported recruitment role: $value")
    }
}

data class RecruitmentSource(
    val description: String,
)

data class RecruitmentAllocation(
    val segmentId: MuscleSegmentId,
    val segmentName: String,
    val role: RecruitmentRole,
    val weighting: Double,
    val confidence: Double,
    val source: RecruitmentSource?,
)

data class RecruitmentProfile(
    val allocations: List<RecruitmentAllocation>,
)

data class ExecutionProfile(
    val id: ExecutionProfileId,
    val exerciseId: ExerciseId,
    val name: String,
    val equipment: EquipmentProfile,
    val loadResolution: LoadResolution?,
    val recruitment: RecruitmentProfile,
    val isDefault: Boolean,
)

data class ExerciseMemory(
    val category: String,
    val equipment: String,
    val fatigueCost: Int,
    val skillDifficulty: Int,
    val setupNotes: String,
    val videoReferenceUrl: String,
    val machineSettings: String,
    val cues: List<String>,
    val commonMistakes: List<String>,
    val substitutions: List<String>,
)

data class ExerciseSetupMedia(
    val id: String,
    val exerciseId: ExerciseId,
    val relativePath: String,
    val mimeType: String,
    val sortOrder: Int,
    val createdAt: String,
    val width: Int,
    val height: Int,
)

data class Exercise(
    val id: ExerciseId,
    val name: String,
    val archived: Boolean,
    val tracking: ExerciseTracking,
    val essentialCue: String?,
    val createdAt: String,
    val updatedAt: String,
    val memory: ExerciseMemory?,
    val setupMedia: List<ExerciseSetupMedia>,
    val executionProfiles: List<ExecutionProfile>,
)
