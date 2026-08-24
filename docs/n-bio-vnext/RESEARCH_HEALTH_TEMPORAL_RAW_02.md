typealias TraceId = String
typealias TraceChunkId = String
typealias ExternalArtifactId = String

enum class EvidenceSemanticRole {
    PERFORMANCE_OUTPUT,       // speed, power, cadence
    PHYSIOLOGICAL_RESPONSE,  // heart rate
    MOVEMENT_CONTEXT,        // incline, elevation, route
    ENVIRONMENTAL_CONTEXT
}

enum class TemporalRepresentation {
    POINT_SERIES,
    INTERVAL_SERIES,
    SPATIAL_ROUTE
}

enum class IntervalValueSemantics {
    TOTAL_OVER_INTERVAL,     // distance, steps, elevation gained
    MEAN_OVER_INTERVAL,
    STATE_OVER_INTERVAL,     // e.g. activity intensity
    UNSPECIFIED
}

enum class AcquisitionMethod {
    SENSOR_RECORDED,
    DEVICE_DERIVED,
    AUTOMATICALLY_INFERRED,
    USER_REPORTED,
    USER_ESTIMATE,
    UNKNOWN
}

enum class EvidenceGranularity {
    TRACE,
    INTERVAL,
    SUMMARY
}

enum class TraceScopeType {
    PERFORMANCE_OBSERVATION,
    SET_RECORD,
    SESSION_EXERCISE,
    WORKOUT_SESSION
}

data class EvidenceQuality(
    val acquisition: AcquisitionMethod,
    val granularity: EvidenceGranularity,
    val sourcePrecision: Double? = null,
    val qualityFlags: Set<String> = emptySet()
)

data class ExternalRecordProvenance(
    val provider: String,                 // e.g. HEALTH_CONNECT
    val sourceRecordType: String?,        // generic string, not HC class
    val sourceRecordId: String?,
    val sourceClientRecordId: String?,
    val sourceClientRecordVersion: Long?,
    val dataOrigin: String?,
    val sourceDeviceManufacturer: String?,
    val sourceDeviceModel: String?,
    val sourceDeviceType: String?,
    val recordingMethod: String?,
    val sourceLastModifiedAt: Instant?,
    val importedAt: Instant
)

data class PerformanceObservation(
    val id: ObservationId,
    val setRecordId: String?,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val timingQuality: TimingQuality?,
    val metricValues: List<PerformanceMetricValue>,
    val supersedesObservationId: ObservationId? = null
)

data class PerformanceMetricValue(
    val metric: MetricId,
    val enteredValue: Double?,
    val enteredUnit: UnitId?,
    val canonicalValue: Double,
    val canonicalUnit: UnitId,
    val evidence: EvidenceQuality,
    val provenanceId: ExternalArtifactId?
)

data class EvidenceTrace(
    val id: TraceId,
    val metric: MetricId,
    val semanticRole: EvidenceSemanticRole,
    val representation: TemporalRepresentation,
    val canonicalUnit: UnitId?,
    val intervalSemantics: IntervalValueSemantics?,

    // It may be session-scoped rather than physically copied into each set.
    val scopeType: TraceScopeType,
    val scopeId: String,

    val startTime: Instant,
    val endTime: Instant,
    val evidence: EvidenceQuality
)

data class PointTraceSample(
    val time: Instant,
    val canonicalValue: Double
)

data class IntervalTraceSample(
    val startTime: Instant,
    val endTime: Instant,
    val canonicalValue: Double
)

data class RouteSample(
    val time: Instant,
    val latitudeDegrees: Double,
    val longitudeDegrees: Double,
    val altitudeMetres: Double?,
    val horizontalAccuracyMetres: Double?,
    val verticalAccuracyMetres: Double?
)

data class EvidenceTraceChunk(
    val id: TraceChunkId,
    val traceId: TraceId,
    val provenanceId: ExternalArtifactId,
    val sourceStartTime: Instant,
    val sourceEndTime: Instant,
    val sampleCount: Int,
    val encodingVersion: Int
)
```

The intentionally generic strings in `ExternalRecordProvenance` prevent `androidx.health.connect.*` DTOs from crossing into the N-BIO domain. A future `HealthConnectMapper` converts Health Connect objects into these structures at an integration boundary.

### Why traces need chunks

A logical trace should not erase Health Connect record boundaries. Suppose one 60-minute workout produces six `HeartRateRecord`s. Flattening all samples into one provenance-less vector loses the ability to answer:

* which Health Connect record produced a particular sample;
* which chunk was subsequently corrected or deleted;
* whether two chunks originated from different apps or devices;
* whether overlapping samples are duplicates or genuinely independent observations.

The correct structure is therefore:

```text
EvidenceTrace: HEART_RATE
  ├── Chunk A
  │     provenance = HC record A
  │     samples ...
  ├── Chunk B
  │     provenance = HC record B
  │     samples ...
  └── Chunk C
        provenance = HC record C
        samples ...
```

A chunk is a provenance boundary, **not necessarily a physiological interval**.

### Evidence resolution should not be one observation-level enum

`TRACE / INTERVAL / SUMMARY / MANUAL_ESTIMATE` is directionally correct but conflates two independent dimensions:

* temporal information content;
* acquisition quality/method.

Consider:

```text
duration = 20 min
```

entered directly from a treadmill display. That is a manual summary, but not necessarily an “estimate”.

Likewise:

```text
incline = 4%
```

could be an accurately transcribed machine setting, while

```text
speed ≈ 9 km/h
```

could genuinely be estimated.

The recommended representation is therefore **per metric or trace**:

```text
temporal representation:
    TRACE
    INTERVAL
    SUMMARY

acquisition:
    SENSOR_RECORDED
    DEVICE_DERIVED
    USER_REPORTED
    USER_ESTIMATE
    UNKNOWN
```

with provenance separately describing import source.

This permits a single treadmill observation such as:

```text
duration        SUMMARY   DEVICE_DERIVED / imported
distance        INTERVAL  DEVICE_DERIVED / imported
speed(t)        TRACE     SENSOR_RECORDED / imported
heartRate(t)    TRACE     SENSOR_RECORDED / imported
incline = 4%    SUMMARY   USER_REPORTED
```

Downstream models can therefore condition on information quality without declaring the manual evidence invalid.

### Raw canonical trace versus summaries and UI cache

**RAW CANONICAL TRACE**

Store:

* every sample My Mettle actually receives;
* original source-record/chunk boundaries;
* point or interval semantics;
* exact timestamps;
* canonical values without smoothing;
* source/unit mapping;
* source record identity;
* `DataOrigin`;
* source-device metadata where exposed;
* recording method;
* source modification/version metadata;
* import timestamp;
* explicit source interval;
* the source's supplied zone offset where available;
* supersession relationships.

Do **not** delete an HR spike just because it looks implausible. Marking it as a derived quality outlier preserves reversibility. Wrist optical HR is vulnerable to motion artefacts during activity; earlier resistance-exercise validation found device- and intensity-dependent HR errors, while newer smartwatch validation has reported substantially stronger performance, demonstrating why quality should be device- and context-dependent rather than globally assumed. citeturn18search2turn18search0turn18search15

**DERIVED SUMMARY**

Examples include time-weighted mean HR, sustained maximum speed, work from power integration, detected intervals, HR-recovery slopes and outlier flags. These should carry:
