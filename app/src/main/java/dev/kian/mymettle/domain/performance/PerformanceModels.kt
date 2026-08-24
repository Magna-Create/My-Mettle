package dev.kian.mymettle.domain.performance

import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.evidence.AcquisitionMethod
import dev.kian.mymettle.domain.evidence.EvidenceGranularity
import dev.kian.mymettle.domain.evidence.EvidenceQuality
import dev.kian.mymettle.domain.evidence.EvidenceSemanticRole
import dev.kian.mymettle.domain.evidence.TimingQuality
import java.time.Instant
import kotlin.math.abs
import kotlin.math.round

enum class MetricFamily(val storageValue: String) {
    DYNAMIC_RESISTANCE("dynamic_resistance"),
    BODYWEIGHT_RESISTANCE("bodyweight_resistance"),
    LOADED_HOLD("loaded_hold"),
    DURATION_ONLY("duration_only"),
    REPEATED_CONTRACTION("repeated_contraction"),
    POWER_DURATION("power_duration"),
    SPEED_DURATION("speed_duration"),
    DEVICE_ORDINAL("device_ordinal");

    companion object {
        fun fromStorage(value: String): MetricFamily = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported metric family: $value")
    }
}

enum class PerformanceMetric(
    val storageValue: String,
    val dimension: QuantityDimension,
    val canonicalUnit: UnitId,
) {
    EXTERNAL_LOAD("external_load", QuantityDimension.MASS, UnitId.KILOGRAM),
    ASSISTANCE("assistance", QuantityDimension.MASS, UnitId.KILOGRAM),
    REPETITIONS("repetitions", QuantityDimension.COUNT, UnitId.REPETITION),
    DURATION("duration", QuantityDimension.TIME, UnitId.SECOND),
    DISTANCE("distance", QuantityDimension.DISTANCE, UnitId.METRE),
    SPEED("speed", QuantityDimension.SPEED, UnitId.METRES_PER_SECOND),
    PACE("pace", QuantityDimension.PACE, UnitId.SECONDS_PER_METRE),
    INCLINE_GRADE("incline_grade", QuantityDimension.GRADE, UnitId.FRACTION),
    MACHINE_LEVEL("machine_level", QuantityDimension.ORDINAL, UnitId.MACHINE_LEVEL),
    POWER("power", QuantityDimension.POWER, UnitId.WATT),
    CADENCE("cadence", QuantityDimension.RATE, UnitId.EVENTS_PER_MINUTE),
    STEPS("steps", QuantityDimension.COUNT, UnitId.STEP),
    FLOORS("floors", QuantityDimension.COUNT, UnitId.FLOOR),
    ELEVATION_GAIN("elevation_gain", QuantityDimension.DISTANCE, UnitId.METRE);

    companion object {
        fun fromStorage(value: String): PerformanceMetric = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported performance metric: $value")
    }
}

enum class Laterality(val storageValue: String) {
    LEFT("left"),
    RIGHT("right"),
    BILATERAL("bilateral"),
    ALTERNATING("alternating"),
    NOT_APPLICABLE("not_applicable"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorage(value: String): Laterality = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported laterality: $value")
    }
}

enum class LateralityMode(val storageValue: String) {
    BILATERAL_ONLY("bilateral_only"),
    UNILATERAL("unilateral"),
    ALTERNATING_ALLOWED("alternating_allowed"),
    NOT_APPLICABLE("not_applicable"),
    UNKNOWN("unknown")
}

data class SchemaMetric(
    val metric: PerformanceMetric,
    val required: Boolean,
    val targetable: Boolean = true,
    val defaultUnit: UnitId = metric.canonicalUnit,
    val minimumCanonical: Double? = null,
    val maximumCanonical: Double? = null,
    val incrementCanonical: Double? = null,
    val allowedCanonicalValues: List<Double> = emptyList(),
) {
    init {
        require(defaultUnit.dimension == metric.dimension) { "Default unit must match ${metric.storageValue}." }
        require(minimumCanonical == null || maximumCanonical == null || minimumCanonical <= maximumCanonical)
        require(incrementCanonical == null || incrementCanonical > 0.0)
    }
}

data class PerformanceSchema(
    val id: String,
    val version: Int,
    val family: MetricFamily,
    val metrics: List<SchemaMetric>,
    val provenance: String,
) {
    init {
        require(id.isNotBlank())
        require(version > 0)
        require(metrics.isNotEmpty())
        require(metrics.map { it.metric }.distinct().size == metrics.size) { "A schema defines each metric once." }
        require(provenance.isNotBlank())
    }

    fun validate(values: List<PerformanceMetricValue>) {
        val byMetric = values.associateBy { it.metric }
        val allowed = metrics.associateBy { it.metric }
        require(values.size == byMetric.size) { "An observation contains a metric more than once." }
        require(byMetric.keys.all { it in allowed }) { "Observation contains a metric outside its schema." }
        require(metrics.filter { it.required }.all { it.metric in byMetric }) { "Observation is missing a required metric." }
        values.forEach { value ->
            val definition = allowed.getValue(value.metric)
            val canonical = value.canonical.value
            require(definition.minimumCanonical == null || canonical >= definition.minimumCanonical) {
                "${value.metric.storageValue} is below the schema minimum."
            }
            require(definition.maximumCanonical == null || canonical <= definition.maximumCanonical) {
                "${value.metric.storageValue} is above the schema maximum."
            }
            if (definition.allowedCanonicalValues.isNotEmpty()) {
                require(definition.allowedCanonicalValues.any { abs(it - canonical) <= 1e-9 }) {
                    "${value.metric.storageValue} is not an allowed profile-local value."
                }
            } else if (definition.incrementCanonical != null) {
                val origin = definition.minimumCanonical ?: 0.0
                val steps = (canonical - origin) / definition.incrementCanonical
                require(abs(steps - round(steps)) <= 1e-9) {
                    "${value.metric.storageValue} does not match the profile resolution."
                }
            }
        }
    }
}

data class PerformanceMetricValue(
    val metric: PerformanceMetric,
    val entered: Quantity,
    val canonical: Quantity = UnitConverter.canonical(entered),
    val evidenceQuality: EvidenceQuality = EvidenceQuality(EvidenceGranularity.SUMMARY, AcquisitionMethod.UNKNOWN),
    val semanticRole: EvidenceSemanticRole = metric.defaultSemanticRole(),
) {
    init {
        require(entered.unit.dimension == metric.dimension) { "Entered unit does not match ${metric.storageValue}." }
        require(canonical.unit == metric.canonicalUnit) {
            "Canonical ${metric.storageValue} must use ${metric.canonicalUnit.storageValue}."
        }
        require(evidenceQuality.granularity == EvidenceGranularity.SUMMARY) {
            "Scalar performance values must use summary granularity; traces use temporal evidence."
        }
        require(semanticRole in setOf(EvidenceSemanticRole.PERFORMANCE_OUTPUT, EvidenceSemanticRole.MOVEMENT_CONTEXT)) {
            "Scalar performance values cannot masquerade as physiological or environmental evidence."
        }
        val converted = UnitConverter.canonical(entered)
        require(abs(converted.value - canonical.value) <= 1e-9 * maxOf(1.0, abs(converted.value))) {
            "Canonical ${metric.storageValue} does not match deterministic conversion of entered value."
        }
        require(metric == PerformanceMetric.INCLINE_GRADE || canonical.value >= 0.0) {
            "Performance values other than incline/decline grade cannot be negative."
        }
        if (metric.dimension in setOf(QuantityDimension.COUNT, QuantityDimension.ORDINAL)) {
            require(canonical.value % 1.0 == 0.0) { "${metric.storageValue} must be integral." }
        }
    }
}

data class PerformanceObservation(
    val id: String,
    val setRecordId: String,
    val executionProfileVersionId: ExecutionProfileVersionId,
    val ordinal: Int,
    val laterality: Laterality,
    val completedAt: Instant,
    val source: String,
    /** Overrides the session snapshot when non-null; otherwise the session snapshot is authoritative. */
    val bodyMassContextKg: Double?,
    val values: List<PerformanceMetricValue>,
    val supersedesObservationId: String? = null,
    /** Observable physical/app/source bound; null when the source only supplied completion. */
    val startedAt: Instant? = null,
    /** Observable end bound. completedAt remains the stable ordering/completion event. */
    val endedAt: Instant? = completedAt,
    val timingQuality: TimingQuality = TimingQuality.COMPLETION_ONLY,
    val sourceZoneOffsetMinutes: Int? = null,
) {
    init {
        require(id.isNotBlank() && setRecordId.isNotBlank())
        require(ordinal >= 0)
        require(source.isNotBlank())
        require(bodyMassContextKg == null || bodyMassContextKg > 0.0)
        require(values.isNotEmpty()) { "A performed observation needs at least one metric." }
        require(values.map { it.metric }.distinct().size == values.size) {
            "A performed observation cannot contain duplicate metrics."
        }
        require(startedAt == null || endedAt == null || !startedAt.isAfter(endedAt))
        require(timingQuality != TimingQuality.COMPLETION_ONLY || startedAt == null)
        require(sourceZoneOffsetMinutes == null || sourceZoneOffsetMinutes in -18 * 60..18 * 60)
    }
}

fun PerformanceMetric.defaultSemanticRole(): EvidenceSemanticRole = when (this) {
    PerformanceMetric.INCLINE_GRADE,
    PerformanceMetric.MACHINE_LEVEL -> EvidenceSemanticRole.MOVEMENT_CONTEXT
    else -> EvidenceSemanticRole.PERFORMANCE_OUTPUT
}

enum class TargetKind(val storageValue: String) {
    EXACT("exact"),
    RANGE("range"),
    MINIMUM("minimum"),
    MAXIMUM("maximum"),
    OPEN("open")
}

data class PrescriptionEvidence(
    val source: String,
    val sourceObservationId: String?,
    val sourceSetRecordId: String?,
    val inferenceRunId: String?,
    val anchorCanonical: Double?,
    val modelVersion: String,
) {
    init {
        require(source.isNotBlank())
        require(modelVersion.isNotBlank())
    }
}

data class MetricTarget(
    val metric: PerformanceMetric,
    val kind: TargetKind,
    val lowerCanonical: Double? = null,
    val upperCanonical: Double? = null,
    val canonicalUnit: UnitId = metric.canonicalUnit,
    val displayUnit: UnitId = canonicalUnit,
    val evidence: PrescriptionEvidence? = null,
) {
    init {
        require(canonicalUnit == metric.canonicalUnit)
        require(displayUnit.dimension == metric.dimension)
        when (kind) {
            TargetKind.EXACT -> require(lowerCanonical != null && upperCanonical == null)
            TargetKind.RANGE -> require(lowerCanonical != null && upperCanonical != null && lowerCanonical <= upperCanonical)
            TargetKind.MINIMUM -> require(lowerCanonical != null && upperCanonical == null)
            TargetKind.MAXIMUM -> require(lowerCanonical == null && upperCanonical != null)
            TargetKind.OPEN -> require(lowerCanonical == null && upperCanonical == null)
        }
        require(metric == PerformanceMetric.INCLINE_GRADE || lowerCanonical == null || lowerCanonical >= 0.0)
        require(metric == PerformanceMetric.INCLINE_GRADE || upperCanonical == null || upperCanonical >= 0.0)
    }

    val exactOrLower: Double? get() = lowerCanonical
}

data class PerformanceTargetTemplate(
    val metricTargets: List<MetricTarget>,
) {
    init {
        require(metricTargets.map { it.metric }.distinct().size == metricTargets.size)
    }
}
