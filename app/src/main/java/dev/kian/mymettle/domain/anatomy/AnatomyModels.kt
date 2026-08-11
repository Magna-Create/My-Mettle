package dev.kian.mymettle.domain.anatomy

@JvmInline
value class MuscleId(val value: String) {
    init {
        require(value.isNotBlank()) { "MuscleId cannot be blank." }
    }
}

@JvmInline
value class MuscleSegmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "MuscleSegmentId cannot be blank." }
    }
}

enum class BodyRegion {
    NECK,
    BACK,
    THORAX,
    ABDOMEN,
    SHOULDER,
    UPPER_ARM,
    FOREARM,
    PELVIC_FLOOR_PERINEUM,
    HIP_GLUTEAL,
    THIGH,
    LOWER_LEG,
}

enum class AnatomicalUnitKind {
    MUSCLE,
    SERIAL_MUSCLE_FAMILY,
}

enum class LateralityModel {
    BILATERAL_SHARED_REFERENCE,
    MIDLINE_SINGLE_REFERENCE,
}

enum class SegmentType {
    WHOLE_MUSCLE,
    HEAD,
    PART,
    BELLY,
    FIBRE_REGION,
}

enum class AnatomicalStatus {
    WHOLE_FORMAL_MUSCLE,
    FORMALLY_NAMED_HEAD,
    FORMALLY_NAMED_PART,
    FORMALLY_NAMED_BELLY,
    TA2_PARENTHESISED_PART,
    EXPERIMENTALLY_USEFUL_FIBRE_REGION,
}

enum class SegmentStatePolicy {
    TRACK,
    PROVISIONAL_TRACK,
    SHARED_PARENT,
}

enum class AnatomyVerificationStatus {
    RECONSTRUCTED_V0_1,
    SOURCE_SUPPORTED_V0_1,
    TA2_SIDE_AGGREGATED_V0_1,
    TA2_VERIFIED_STRONG_V0_1,
    TA2_VERIFIED_V0_1,
    PRIMARY_REGIONAL_SOURCE_PENDING,
}

data class MuscleSegment(
    val id: MuscleSegmentId,
    val muscleId: MuscleId,
    val name: String,
    val type: SegmentType,
    val anatomicalStatus: AnatomicalStatus,
    val statePolicy: SegmentStatePolicy,
    val verificationStatus: AnatomyVerificationStatus,
)

data class Muscle(
    val id: MuscleId,
    val name: String,
    val region: BodyRegion,
    val unitKind: AnatomicalUnitKind,
    val lateralityModel: LateralityModel,
    val instancePattern: String?,
    val verificationStatus: AnatomyVerificationStatus,
    val segments: List<MuscleSegment>,
)
