package dev.kian.mymettle.domain.physiology

import dev.kian.mymettle.domain.anatomy.MuscleId
import dev.kian.mymettle.domain.anatomy.MuscleSegmentId

@JvmInline
value class ReferenceProfileId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReferenceProfileId cannot be blank." }
    }
}

enum class ReferenceSex {
    MALE,
    FEMALE,
    MIXED,
    UNSPECIFIED,
}

data class ReferencePopulation(
    val sex: ReferenceSex,
    val ageSummary: String,
    val description: String,
)

enum class EstimateSourceKind {
    DIRECT_MEASUREMENT,
    PUBLISHED_DERIVED,
    MODEL_DERIVED,
    STRUCTURAL_PRIOR,
    GEOMETRIC_FALLBACK,
}

data class Estimate<T>(
    val value: T,
    val uncertainty: Double?,
    val sourceKind: EstimateSourceKind,
    val sourceId: String?,
    val modelVersion: String?,
)

enum class UncertaintyClass {
    LOW,
    LOW_MODERATE,
    MODERATE,
    MODERATE_HIGH,
    HIGH,
    VERY_HIGH,
    UNKNOWN,
}

sealed interface AbsoluteSharePolicy {
    data class Known(val fraction: Double) : AbsoluteSharePolicy

    data class StructuralPrior(
        val fraction: Double,
        val uncertainty: UncertaintyClass,
    ) : AbsoluteSharePolicy

    data object Latent : AbsoluteSharePolicy
}

/**
 * A prior may target a whole muscle or one of its stable segments. Parent-muscle targets are
 * necessary when an adult-male parent volume exists but the child allocation is deliberately
 * latent. They do not create a competing whole-muscle longitudinal state.
 */
data class ReferencePhysiologyPrior(
    val muscleId: MuscleId,
    val segmentId: MuscleSegmentId?,
    val volumeCm3: Estimate<Double>?,
    val optimalFibreLengthMm: Estimate<Double>?,
    val pennationDeg: Estimate<Double>?,
    val geometricPcsaCm2: Estimate<Double>?,
    val effectivePcsaCm2: Estimate<Double>?,
    val structuralCapacityIndex: Estimate<Double>?,
    val absoluteSharePolicy: AbsoluteSharePolicy,
    val availability: String,
    val uncertaintyClass: UncertaintyClass,
    val selectionRule: String,
)

data class ReferenceProfile(
    val id: ReferenceProfileId,
    val version: Int,
    val population: ReferencePopulation,
    val datasetVersion: String,
    val modelVersion: String,
    val priors: List<ReferencePhysiologyPrior>,
)
