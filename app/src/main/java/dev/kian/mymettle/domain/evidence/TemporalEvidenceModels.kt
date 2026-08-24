package dev.kian.mymettle.domain.evidence

import dev.kian.mymettle.domain.performance.UnitId
import java.time.Instant

enum class TemporalRepresentation(val storageValue: String) {
    POINT_SERIES("point_series"),
    INTERVAL_SERIES("interval_series"),
    SPATIAL_ROUTE("spatial_route");

    companion object {
        fun fromStorage(value: String): TemporalRepresentation = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported temporal representation: $value")
    }
}

enum class IntervalValueSemantics(val storageValue: String) {
    TOTAL_OVER_INTERVAL("total_over_interval"),
    MEAN_OVER_INTERVAL("mean_over_interval"),
    STATE_OVER_INTERVAL("state_over_interval"),
    UNSPECIFIED("unspecified");

    companion object {
        fun fromStorage(value: String): IntervalValueSemantics = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported interval semantics: $value")
    }
}

enum class AcquisitionMethod(val storageValue: String) {
    SENSOR_RECORDED("sensor_recorded"),
    DEVICE_DERIVED("device_derived"),
    AUTOMATICALLY_INFERRED("automatically_inferred"),
    USER_REPORTED("user_reported"),
    USER_ESTIMATE("user_estimate"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorage(value: String): AcquisitionMethod = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported acquisition method: $value")
    }
}

enum class EvidenceGranularity(val storageValue: String) {
    TRACE("trace"),
    INTERVAL("interval"),
    SUMMARY("summary");

    companion object {
        fun fromStorage(value: String): EvidenceGranularity = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported evidence granularity: $value")
    }
}

enum class EvidenceSemanticRole(val storageValue: String) {
    PERFORMANCE_OUTPUT("performance_output"),
    PHYSIOLOGICAL_RESPONSE("physiological_response"),
    MOVEMENT_CONTEXT("movement_context"),
    ENVIRONMENTAL_CONTEXT("environmental_context");

    companion object {
        fun fromStorage(value: String): EvidenceSemanticRole = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported evidence semantic role: $value")
    }
}

enum class TimingQuality(val storageValue: String) {
    SOURCE_REPORTED_BOUND("source_reported_bound"),
    APP_TIMER_BOUND("app_timer_bound"),
    USER_ACTION_BOUND("user_action_bound"),
    APP_INTERACTION_APPROXIMATION("app_interaction_approximation"),
    COMPLETION_ONLY("completion_only"),
    LEGACY_UNKNOWN("legacy_unknown"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorage(value: String): TimingQuality = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported timing quality: $value")
    }
}

enum class SourceState(val storageValue: String) {
    AVAILABLE("available"),
    UPDATED_AT_SOURCE("updated_at_source"),
    DELETED_AT_SOURCE("deleted_at_source"),
    PERMISSION_UNAVAILABLE("permission_unavailable"),
    SOURCE_DISCONNECTED("source_disconnected"),
    UNKNOWN("unknown");

    companion object {
        fun fromStorage(value: String): SourceState = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported source state: $value")
    }
}

enum class EvidenceValueKind {
    NUMERIC,
    CATEGORICAL,
    SPATIAL,
}

/**
 * Versionable semantic registry for temporal evidence. It intentionally remains distinct from
 * PerformanceMetric: heart rate is physiological response, not mechanical performance output.
 */
enum class EvidenceMetricKey(
    val storageValue: String,
    val valueKind: EvidenceValueKind,
    val canonicalUnit: UnitId?,
    val defaultRole: EvidenceSemanticRole,
) {
    SPEED("speed", EvidenceValueKind.NUMERIC, UnitId.METRES_PER_SECOND, EvidenceSemanticRole.PERFORMANCE_OUTPUT),
    POWER("power", EvidenceValueKind.NUMERIC, UnitId.WATT, EvidenceSemanticRole.PERFORMANCE_OUTPUT),
    CADENCE("cadence", EvidenceValueKind.NUMERIC, UnitId.EVENTS_PER_MINUTE, EvidenceSemanticRole.MOVEMENT_CONTEXT),
    DISTANCE("distance", EvidenceValueKind.NUMERIC, UnitId.METRE, EvidenceSemanticRole.PERFORMANCE_OUTPUT),
    STEPS("steps", EvidenceValueKind.NUMERIC, UnitId.STEP, EvidenceSemanticRole.MOVEMENT_CONTEXT),
    FLOORS("floors", EvidenceValueKind.NUMERIC, UnitId.FLOOR, EvidenceSemanticRole.PERFORMANCE_OUTPUT),
    ELEVATION_GAIN("elevation_gain", EvidenceValueKind.NUMERIC, UnitId.METRE, EvidenceSemanticRole.MOVEMENT_CONTEXT),
    HEART_RATE("heart_rate", EvidenceValueKind.NUMERIC, UnitId.BEATS_PER_MINUTE, EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE),
    ACTIVITY_STATE("activity_state", EvidenceValueKind.CATEGORICAL, null, EvidenceSemanticRole.MOVEMENT_CONTEXT),
    ROUTE("route", EvidenceValueKind.SPATIAL, null, EvidenceSemanticRole.MOVEMENT_CONTEXT);

    companion object {
        fun fromStorage(value: String): EvidenceMetricKey = entries.firstOrNull { it.storageValue == value }
            ?: throw IllegalArgumentException("Unsupported evidence metric: $value")
    }
}

data class EvidenceQuality(
    val granularity: EvidenceGranularity,
    val acquisitionMethod: AcquisitionMethod,
)

data class EvidenceTimeBounds(
    val startedAt: Instant?,
    val endedAt: Instant?,
    val timingQuality: TimingQuality,
    val sourceZoneOffsetMinutes: Int? = null,
) {
    init {
        require(startedAt == null || endedAt == null || !startedAt.isAfter(endedAt)) {
            "Evidence start cannot be after its end."
        }
        require(timingQuality != TimingQuality.COMPLETION_ONLY || startedAt == null) {
            "Completion-only evidence cannot claim a start bound."
        }
        require(sourceZoneOffsetMinutes == null || sourceZoneOffsetMinutes in -18 * 60..18 * 60)
    }
}

data class PointEvidenceSample(
    val timestamp: Instant,
    val canonicalValue: Double,
) {
    init {
        require(canonicalValue.isFinite())
    }
}

sealed interface IntervalEvidenceValue {
    data class Numeric(val canonicalValue: Double) : IntervalEvidenceValue {
        init {
            require(canonicalValue.isFinite())
        }
    }

    data class State(val value: String) : IntervalEvidenceValue {
        init {
            require(value.isNotBlank())
        }
    }
}

data class IntervalEvidenceSample(
    val startedAt: Instant,
    val endedAt: Instant,
    val value: IntervalEvidenceValue,
) {
    init {
        require(startedAt.isBefore(endedAt)) { "An interval must have positive duration." }
    }
}

data class SpatialRouteSample(
    val timestamp: Instant,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeMetres: Double? = null,
    val horizontalAccuracyMetres: Double? = null,
    val verticalAccuracyMetres: Double? = null,
) {
    init {
        require(latitudeDegrees.isFinite() && latitudeDegrees in -90.0..90.0)
        require(longitudeDegrees.isFinite() && longitudeDegrees in -180.0..180.0)
        require(altitudeMetres == null || altitudeMetres.isFinite())
        require(horizontalAccuracyMetres == null || horizontalAccuracyMetres.isFinite() && horizontalAccuracyMetres >= 0.0)
        require(verticalAccuracyMetres == null || verticalAccuracyMetres.isFinite() && verticalAccuracyMetres >= 0.0)
    }
}

sealed interface EvidencePayload {
    val representation: TemporalRepresentation
    val sampleCount: Int

    data class Points(val samples: List<PointEvidenceSample>) : EvidencePayload {
        override val representation = TemporalRepresentation.POINT_SERIES
        override val sampleCount: Int get() = samples.size
    }

    data class Intervals(val samples: List<IntervalEvidenceSample>) : EvidencePayload {
        override val representation = TemporalRepresentation.INTERVAL_SERIES
        override val sampleCount: Int get() = samples.size
    }

    data class Route(val samples: List<SpatialRouteSample>) : EvidencePayload {
        override val representation = TemporalRepresentation.SPATIAL_ROUTE
        override val sampleCount: Int get() = samples.size
    }
}

data class ExternalRecordProvenance(
    val id: String,
    /** Stable Native key for all revisions of one external logical record. */
    val logicalSourceKey: String,
    val nativeRevision: Int,
    val provider: String?,
    val dataOrigin: String?,
    val sourceRecordType: String?,
    val sourceRecordId: String?,
    val sourceClientRecordId: String?,
    val sourceClientRecordVersion: Long?,
    val sourceDeviceManufacturer: String?,
    val sourceDeviceModel: String?,
    val sourceDeviceType: String?,
    val recordingMethod: String?,
    val sourceLastModifiedAt: Instant?,
    val importedAt: Instant,
    val sourceBounds: EvidenceTimeBounds,
    val sourceState: SourceState,
    val supersedesArtifactId: String? = null,
) {
    init {
        require(id.isNotBlank() && logicalSourceKey.isNotBlank())
        require(nativeRevision > 0)
        require(sourceClientRecordVersion == null || sourceClientRecordVersion >= 0)
        require(supersedesArtifactId != id)
    }
}

data class EvidenceTrace(
    val id: String,
    val metric: EvidenceMetricKey,
    val representation: TemporalRepresentation,
    val intervalSemantics: IntervalValueSemantics,
    val canonicalUnit: UnitId?,
    val quality: EvidenceQuality,
    val semanticRole: EvidenceSemanticRole,
    val bounds: EvidenceTimeBounds,
    val provenance: String,
    val createdAt: Instant,
    val recordedAt: Instant,
    val supersedesTraceId: String? = null,
) {
    init {
        require(id.isNotBlank() && provenance.isNotBlank())
        require(supersedesTraceId != id)
        require(canonicalUnit == metric.canonicalUnit) { "Trace unit must match the evidence metric registry." }
        require(quality.granularity != EvidenceGranularity.SUMMARY) { "A trace cannot claim summary granularity." }
        require(
            (representation == TemporalRepresentation.INTERVAL_SERIES && quality.granularity == EvidenceGranularity.INTERVAL) ||
                (representation != TemporalRepresentation.INTERVAL_SERIES && quality.granularity == EvidenceGranularity.TRACE),
        ) { "Trace granularity must match its temporal representation." }
        require(representation == TemporalRepresentation.INTERVAL_SERIES || intervalSemantics == IntervalValueSemantics.UNSPECIFIED)
        require((metric == EvidenceMetricKey.ROUTE) == (representation == TemporalRepresentation.SPATIAL_ROUTE))
        require(metric != EvidenceMetricKey.ACTIVITY_STATE || representation == TemporalRepresentation.INTERVAL_SERIES)
        require((metric.valueKind == EvidenceValueKind.CATEGORICAL) == (intervalSemantics == IntervalValueSemantics.STATE_OVER_INTERVAL))
        require(metric != EvidenceMetricKey.HEART_RATE || semanticRole == EvidenceSemanticRole.PHYSIOLOGICAL_RESPONSE) {
            "Heart rate evidence must remain a physiological response."
        }
    }
}

data class EvidenceTraceChunk(
    val id: String,
    val traceId: String,
    val sourceArtifactId: String?,
    val ordinal: Int,
    val sourceBounds: EvidenceTimeBounds,
    val payload: EvidencePayload,
    val encodingVersion: Int,
    val payloadSha256: String,
    val createdAt: Instant,
) {
    init {
        require(id.isNotBlank() && traceId.isNotBlank())
        require(ordinal >= 0 && encodingVersion > 0)
        require(payloadSha256.matches(Regex("[0-9a-f]{64}")))
    }
}

data class TraceScopeLinks(
    val workoutSessionIds: Set<String> = emptySet(),
    val sessionExerciseIds: Set<String> = emptySet(),
    val setRecordIds: Set<String> = emptySet(),
    val observationIds: Set<String> = emptySet(),
) {
    init {
        require((workoutSessionIds + sessionExerciseIds + setRecordIds + observationIds).all { it.isNotBlank() })
    }
}

data class StoredEvidenceTrace(
    val trace: EvidenceTrace,
    val chunks: List<EvidenceTraceChunk>,
    val links: TraceScopeLinks,
)

data class DerivedEvidenceSummary(
    val id: String,
    val summaryType: String,
    val algorithmId: String,
    val algorithmVersion: String,
    val inputTraceIds: Set<String>,
    val inputFingerprint: String,
    val computedAt: Instant,
    val numericValue: Double?,
    val canonicalUnit: UnitId?,
    val payload: ByteArray?,
) {
    init {
        require(id.isNotBlank() && summaryType.isNotBlank())
        require(algorithmId.isNotBlank() && algorithmVersion.isNotBlank())
        require(inputTraceIds.isNotEmpty() && inputTraceIds.all { it.isNotBlank() })
        require(inputFingerprint.isNotBlank())
        require(numericValue == null || numericValue.isFinite())
    }

    override fun equals(other: Any?): Boolean = other is DerivedEvidenceSummary &&
        id == other.id && summaryType == other.summaryType && algorithmId == other.algorithmId &&
        algorithmVersion == other.algorithmVersion && inputTraceIds == other.inputTraceIds &&
        inputFingerprint == other.inputFingerprint && computedAt == other.computedAt &&
        numericValue == other.numericValue && canonicalUnit == other.canonicalUnit &&
        payload.contentEqualsNullable(other.payload)

    override fun hashCode(): Int = 31 * id.hashCode() + (payload?.contentHashCode() ?: 0)
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this == null -> other == null
    other == null -> false
    else -> contentEquals(other)
}
