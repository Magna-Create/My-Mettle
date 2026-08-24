
```text
algorithmId
algorithmVersion
inputTraceIds
inputRevision/hash
computedAt
```

and may be recomputed.

**UI CACHE / DOWNSAMPLED TRACE**

A 100–500-point graph cache is appropriate for instant history rendering. It can use min/max buckets or another shape-preserving downsampler, but it is disposable and **must never replace the canonical trace**.

### Sampling, gaps and irregularity

A raw trace must not imply uniform sampling. Store sample timestamps rather than an assumed sampling rate. Derive properties such as:

```text
sampleCount
medianSampleInterval
p90SampleInterval
largestObservedGap
coverageFraction
```

later.

Absence of samples is ordinarily sufficient to represent a gap. Do not insert synthetic zeroes, carry-forward values or interpolated HR into canonical evidence. A derived `TraceQualityIssue` can mark a suspiciously long discontinuity after comparing it with the empirical local cadence.

For interval quantities such as `DistanceRecord`, do not transform them into instantaneous point values. Android explicitly defines the distance value over its record interval. citeturn24view2

### Time representation

Canonical timestamps should be UTC `Instant`s. Preserve the source/user-experienced zone offset separately where Health Connect provides it; Android notes that zone offsets are relevant to user-time history and travel. citeturn24view1turn24view2

For persistence, avoid throwing away `Instant` precision simply because UI rendering occurs in milliseconds. A robust raw representation is:

```text
epochSecond: Long
nanoAdjustment: Int
```

or an equivalently lossless encoded `Instant`.

Derived UI caches may safely use millisecond offsets.

For resistance training, N-BIO should add actual set/bout bounds:

```text
setStartedAt
setEndedAt
timestampQuality / uncertainty
```

rather than only an eventual completion timestamp. These boundaries are materially valuable because HR recovery and pre-next-set features are otherwise impossible to align cleanly.

### Room storage recommendation

A naïve `PerformanceTraceSampleEntity` row for every sample is conceptually clean, but long-term SQLite overhead becomes substantial.

An illustrative 1-hour workout containing four 1-Hz traces has:

```text
4 × 3,600 = 14,400 samples
```

Even before SQLite row/index overhead, storing only a 64-bit timestamp and 64-bit value requires about **225 KiB per hour**. At 2,500 workouts averaging 45 minutes with four 1-Hz channels, that minimal two-field payload alone approaches **0.43 GB**; real row-per-sample storage adds primary keys, foreign keys, indexes, page overhead and provenance relationships.

For My Mettle's access pattern — usually “load the trace for this workout”, not “SQL-query the 18,436,729th HR sample globally” — a chunked physical representation is preferable.

Recommended Room entities:

```text
performance_observation
PK id
INDEX setRecordId
INDEX startedAt

performance_metric_value
PK id
FK observationId
INDEX (observationId, metric)

evidence_trace
PK id
metric
semanticRole
representation
scopeType
scopeId
startTime
endTime
...
INDEX (scopeType, scopeId)
INDEX (metric, startTime)

external_evidence_artifact
PK id
provider
dataOrigin
sourceRecordType
sourceRecordId
clientRecordId
clientRecordVersion
sourceLastModifiedAt
importedAt
sourceState
UNIQUE(provider, dataOrigin, sourceRecordType, sourceRecordId, revision)

evidence_trace_chunk
PK id
FK traceId
FK provenanceId
sourceStart
sourceEnd
sampleCount
encodingVersion
payload BLOB
INDEX traceId
INDEX provenanceId

observation_trace_link
PK (observationId, traceId, linkRole)

derived_trace_summary
PK id
traceId
summaryType
algorithmId
algorithmVersion
inputFingerprint
value / payload
INDEX (traceId, summaryType)

ui_trace_cache
PK (traceId, cacheVersion, targetPointCount)
payload BLOB
```

`payload` should use a **versioned binary representation**, not JSON. JSON is convenient for debugging but wasteful for millions of numeric timestamps and values. A simple columnar encoding of timestamp deltas plus IEEE-754 values, optionally compressed, is sufficient. `PerformanceTraceSample` remains a domain value type even if it is not one SQLite row.

Further chunking should respect source-record boundaries first. A pathological giant source record may additionally be split into bounded chunks, with all subchunks still referencing the same source artifact.

### Updates, deletions and immutable snapshots

Android describes Health Connect integrations as commonly keeping their own datastore and provides changes APIs precisely so local data can respond to Health Connect upserts/deletions. It also advises retaining Health Connect IDs to process deletion notifications. citeturn22view7

For N-BIO, however, **mirroring the live record destructively conflicts with immutable raw evidence**.

Recommended contract:

```text
Health Connect source
         │
         ├── import at T1
         │       └── immutable Native snapshot revision 1
         │
         ├── source changed at T2
         │       └── immutable Native snapshot revision 2
         │              supersedes revision 1
         │
         └── source deleted at T3
                 └── sourceState = DELETED_AT_SOURCE
                     snapshots remain unless user deletes Native evidence
```

Use source-state metadata such as:

```text
AVAILABLE
UPDATED_AT_SOURCE
DELETED_AT_SOURCE
PERMISSION_UNAVAILABLE
SOURCE_DISCONNECTED
UNKNOWN
```

**Do not equate “could not read source today” with “source was deleted”.** Revoked permission, disconnected integration and actual deletion are separate states.

The user's explicit deletion of My Mettle's stored copy is a different operation from unlinking Samsung Health. Product privacy controls should expose that distinction clearly. Android's sync guidance establishes that apps maintain their own datastore; it does not make a historical consumer snapshot intrinsically dependent on the continuing existence of a third-party record. citeturn22view7

## Conditioning capability models, cardio-to-muscle evidence and trace summaries

### Modality-specific capability models

There should be **no universal cardio score**. The external physics and best-observed performance quantity differ too much by modality.

| Modality | Strongest raw observations | Useful summaries | Candidate capability latent | Must remain specific | Material advantage of traces |
|---|---|---|---|---|---|
| **Running / treadmill** | speed(t), duration, distance; grade where actually known | distance/time performances, sustained speeds, speed distribution | speed-duration / critical-speed family | treadmill execution, grade, surface/environment | distinguishes intervals from equal-average steady runs; identifies sustained efforts |
| **Cycling** | **power(t)** where calibrated, duration; cadence(t) | power-duration bests, work, sustained power, cadence distribution | critical-power / power-duration state | power-meter/device calibration, bicycle/erg profile | power surges/recoveries and W′-style depletion cannot be recovered from average power |
| **Rowing ergometer** | power/pace(t), distance, duration, stroke rate | pace/power-duration, work, stroke-rate distribution | erg-specific power-duration capacity | ergometer type/calibration/settings | separates starts/sprints/steady rowing and power-rate interaction |
| **Stair climbing / stepmill** | actual step cadence, elapsed duration, measured ascent/elevation/floors, body mass | steps/min, ascent, duration | device/profile-specific sustained climbing capacity | machine geometry and local “level” | reveals cadence/intensity changes and interval structure |
| **Elliptical / cross-trainer** | calibrated power if available; cadence; resistance/level | sustained power/cadence if trustworthy | device/profile-specific capability | machine model and resistance scale | distinguishes varying resistance/cadence despite same duration |
| **Other ordinal machines** | duration + machine setting + cadence if available | profile-local trends | device-local capability only | essentially everything about machine scale | traces useful only if setting/cadence varies during session |

#### Running and treadmill

Critical speed models the hyperbolic/curvilinear relationship between sustainable running speed and duration, with a finite distance capacity above critical speed (`D′`). Modern reviews support critical speed as a useful performance-domain representation rather than a generic cardio-fitness scalar. citeturn15search20

Grade matters, but a universal “grade adjustment” would be unsafe. Jones and Doust's classic treadmill study found approximately 1% grade best approximated outdoor energetic cost under its tested running conditions; this is useful empirical context, not a law that every treadmill run should be normalised with a 1% correction. Incline itself measurably changes cardiorespiratory demand at the same belt speed. citeturn15search6turn15search10

Consequently:

```text
running capability ≠ speed alone
running capability ≠ HR alone

candidate:
    performance capability(profile, grade/context, duration)
```

Treadmill grade manually copied from the machine is valuable contextual evidence even when absent from the watch.

#### Cycling

The power-duration relationship and critical-power/W′ framework are much more defensible anchors than indoor bicycle “speed”. Critical power approximates the boundary of the severe-intensity domain, while W′ represents a finite amount of work possible above CP within that model. citeturn14search0

Indoor cycle speed is often an output of device resistance/flywheel/virtual-speed assumptions rather than a portable measure of external performance. Therefore:

```text
calibrated power > indoor speed
```

for cross-session capability modelling, while speed remains useful context within the same device/profile.

Cadence modifies the way a given power is produced but is not itself effort: 100 rpm at 50 W and 100 rpm at 300 W are radically different tasks.

#### Rowing

Rowing ergometer performance is naturally represented through pace/power, distance and time, with stroke rate adding execution context. Reviews of Concept2-based rowing performance research consistently use 2,000-m time/mean power and physiological/power parameters as important predictors. citeturn14search4

Cross-device equivalence should nevertheless be conservative. A power reading from one calibrated erg family should not automatically be treated as numerically interchangeable with another manufacturer's estimate.

#### Stair climbing

`FloorsClimbedRecord` is literally a count of floors and does not specify the vertical height represented by one “floor”; `ElevationGainedRecord` provides length where actual elevation is available. citeturn24view3turn24view4

A StairMaster “level 8” therefore belongs in something like:

```text
MACHINE_LEVEL
executionProfile.machineFamily/model
```

not a universal intensity scale.

Where actual cadence plus known machine geometry is available, richer physical modelling may become possible. Where only `level = 8` exists, the defensible latent is:
