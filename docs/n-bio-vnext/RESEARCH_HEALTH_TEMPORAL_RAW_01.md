# My Mettle Native — N-BIO Cardio, Health Connect Time-Series, Resistance-Training Heart Rate and Physiological Context Research

## Architectural decision and evidence boundaries

**Executive architectural decision: yes — N-BIO-6 should support temporal/trace evidence before its foundation is frozen.** The important architectural reason is broader than cardio graphs. Current Health Connect workout evidence is not uniformly scalar: heart rate, speed, cycling cadence, steps cadence and power are sample-series records; distance, elevation gained, floors climbed, steps and activity intensity are interval records; routes are timestamped spatial sequences. Flattening those forms into scalar averages would destroy distinctions that later conditioning, fatigue and physiological-response models cannot reconstruct. citeturn6search6turn2view2turn2view3turn3view0turn24view2

The present candidate

```text
PerformanceTrace
    └── timestamp → scalar
```

is therefore **necessary but not quite sufficient**. It handles `SpeedRecord`, `HeartRateRecord`, `PowerRecord`, `CyclingPedalingCadenceRecord` and `StepsCadenceRecord` well, but not `DistanceRecord` or `ActivityIntensityRecord`, whose values describe intervals, and not `ExerciseRoute`, whose samples are spatial and multi-dimensional. Android explicitly models distance as distance accumulated since the previous reading over a bounded interval, while `ActivityIntensityRecord` represents a moderate/vigorous state from start to end. citeturn24view0turn24view1turn24view2

The more durable abstraction is therefore:

```text
Exercise / SessionExercise
        │
        ├── SetRecord / ExerciseBout
        │       │
        │       └── PerformanceObservation
        │               ├── PerformanceMetricValue
        │               └── EvidenceTrace links
        │
        └── Session-scoped EvidenceTrace
                │
                ├── POINT_SERIES
                ├── INTERVAL_SERIES
                └── SPATIAL_ROUTE
```

with every evidence object explicitly distinguishing:

```text
WHAT HAPPENED
external performance / machine output

HOW IT WAS OBSERVED
manual entry / sensor / device-derived / imported source

HOW THE BODY RESPONDED
heart rate and future physiological traces

WHAT N-BIO INFERS
capability / conditioning / systemic state / local fatigue / etc.
```

That separation matters particularly for heart rate. HR is not an `EXTERNAL_LOAD`-style performance metric. Resistance-exercise studies show that cardiovascular responses vary with exercise type, resistance configuration, active muscle mass, rest structure, dynamic versus isometric contraction and breathing behaviour. That makes HR genuine physiological evidence, but it also means the same HR value can arise from very different muscular and systemic situations. citeturn17search4turn17search5turn17search6turn17search11

### Evidence categories used in this report

To avoid silently converting research findings into product policy, conclusions below use five categories:

| Category | Meaning |
|---|---|
| **PLATFORM FACT** | Behaviour explicitly represented or documented by Android/Health Connect/Samsung. |
| **PHYSIOLOGICAL EVIDENCE** | Empirical relationship supported by primary literature, reviews or consensus. |
| **MODELLING CHOICE** | A computational design that follows reasonably from the evidence but is not itself established physiology. |
| **PRODUCT POLICY** | A UX/data-retention decision My Mettle must choose. |
| **UNKNOWN / UNSUPPORTED** | Current evidence or documentation does not justify a stronger statement. |

For physiological questions, four different claims must also remain separate: **descriptive association**, **within-person prediction**, **causal/mechanistic interpretation**, and **practical modelling value**. A repeated exercise-specific HR pattern, for example, could prove useful for personal prediction even if its mechanistic decomposition between muscle mass, autonomic drive, breathing and cardiovascular conditioning remains uncertain.

### The most consequential foundation change

The crucial N-BIO-6 change is **not merely adding a list of samples to `PerformanceObservation`**. N-BIO needs a generic temporal-evidence substrate with:

1. point samples;
2. interval-valued evidence;
3. provenance-preserving source-record chunks;
4. source-independent canonical units;
5. exact time alignment;
6. evidence-acquisition/resolution metadata;
7. session-scoped physiological traces that can contextualise several resistance sets without being copied into each one;
8. immutable source snapshots with supersession rather than mutation.

That is the minimum sufficiently rich substrate that keeps future interpretations reversible.

## Health Connect and Samsung workout evidence model

### Health Connect capability matrix

Health Connect's current workout representation is intentionally compositional. `ExerciseSessionRecord` describes the workout interval and exercise type; granular measurements are separate records. Android's workout guide tells clients to use the session's `startTime`/`endTime` when reading granular data such as HR. `ExerciseRoute` is the notable exception: the route is retrieved specifically through the exercise-session record ID. citeturn24view5turn6search5

| Health Connect type | Temporal form | API representation / units | Multiple samples per record? | Relationship to session | N-BIO relevance |
|---|---|---|---|---|---|
| `ExerciseSessionRecord` | **Interval** | Start/end `Instant`, exercise type, optional segments/laps, metadata | No sample series; may contain segment/lap structures | **Primary workout record** | Defines source workout identity and bounds. citeturn0search2turn0search8 |
| `SpeedRecord` | **Point series inside an interval record** | `List<Sample>`, each sample time + `Velocity` | **Yes** | Query by workout time; no documented foreign key to session | Strong running/cycling contextual/performance trace. citeturn2view2turn24view5 |
| `HeartRateRecord` | **Point series** | Samples with time + beats/min | **Yes** | Query by workout time | Physiological response; not external performance. citeturn2view3turn24view5 |
| `PowerRecord` | **Point series** | Samples with time + `Power`; API explicitly cites cycling/rowing | **Yes** | Time-interval association | Particularly strong cycling/rowing performance evidence. citeturn3view0turn3view1 |
| `CyclingPedalingCadenceRecord` | **Point series** | Time + revolutions/min | **Yes** | Time-interval association | Useful alongside cycling power; not effort by itself. citeturn5search2turn6search6 |
| `StepsCadenceRecord` | **Point series** | Time + step rate | **Yes** | Time-interval association | Potentially useful running/stair locomotion evidence. citeturn2view4turn6search6 |
| `DistanceRecord` | **Interval quantity** | Start/end + `Length`; value is distance covered since previous reading | One quantity per record | Time-interval association | Strong cumulative performance evidence; several records may represent one workout. citeturn24view2 |
| `ElevationGainedRecord` | **Interval quantity** | Start/end + `Length` | One quantity per record | Time-interval association | Useful outdoor climbing/stair context. citeturn24view3 |
| `FloorsClimbedRecord` | **Interval quantity** | Start/end + `Double` floors | One quantity per record | Time-interval association | Useful count, but not inherently vertical metres. citeturn24view4 |
| `StepsRecord` | **Interval quantity** | Start/end + count | One quantity per record | Time-interval association | Useful for walking/stairs; writers are expected not to create overlapping increments within their own source stream. citeturn5search1 |
| `ActivityIntensityRecord` | **Categorical interval** | Start/end + moderate/vigorous category | One state per record | Time-interval association | Contextual categorisation, not raw mechanical intensity. citeturn24view0turn24view1 |
| `ExerciseRoute` | **Spatial point series** | Timestamped lat/lon with optional altitude and accuracy fields | **Yes** | **Retrieved using the session's record ID** | Route/display, grade/elevation derivation where quality permits. citeturn6search5turn6search12 |

Health Connect's series records have API timestamps capable of representing fine temporal precision, but **the platform does not promise a fixed sampling frequency to consumers**. Practical frequency is producer-, device-, workout- and battery-policy-dependent. Consequently, N-BIO must record the actual timestamp sequence and derive empirical sampling statistics rather than labelling a trace “1 Hz” simply because a watch commonly samples near that rate. citeturn2view2turn2view3turn3view0

Likewise, several records of one type can legitimately describe a workout. Android explicitly recommends multiple `DistanceRecord`s when breakdowns of a long workout are desired, and series APIs define a series *within each record* rather than making one record synonymous with an entire exercise session. citeturn24view2turn2view3

### Provenance semantics

Every Health Connect record carries common metadata. Current Android metadata includes the Health Connect-assigned `id`, `lastModifiedTime`, `dataOrigin`, optional device information, optional `clientRecordId` and `clientRecordVersion`, and a recording method distinguishing actively recorded, automatically recorded, manual entry and unknown. `clientRecordId` is unique for a source and record type, and the highest `clientRecordVersion` wins when the writer upserts the same logical record. citeturn22view4turn22view7

This provenance should be preserved **per source record/chunk**, not merely once on the overall workout. A cardio session could theoretically contain one session record, several HR series records, several distance intervals and a route, each with independent source identifiers and modification times.

Android's changes API reinforces the importance of record identity. Upsertion changes provide updated records; deletion changes expose only the deleted record ID, not its type, so Android explicitly advises clients to retain IDs and enough local information to resolve deletion events. citeturn22view7

### Does `ExerciseSessionRecord.dataOrigin` constrain ancillary records?

**PLATFORM FACT:** no Health Connect session foreign key is documented for ordinary HR, speed, power or cadence records. Android's own workout example reads `HeartRateRecord`s solely using the selected exercise session's start/end interval; it does **not** specify a `DataOrigin` filter. Health Connect nevertheless supports data-origin filtering on reads. citeturn24view5turn9search8

Therefore the rule should not be:

```text
session.dataOrigin == ancillary.dataOrigin
    therefore definitely associated

session.dataOrigin != ancillary.dataOrigin
    therefore definitely unrelated
```

Neither implication is guaranteed by the platform.

**MODELLING CHOICE:** My Mettle should use same-origin evidence as the **default high-confidence join**, while treating cross-origin temporal overlap as a possible association requiring additional evidence or user selection.

The safe import logic is:

```text
selected ExerciseSessionRecord
          │
          ├── snapshot session + provenance
          │
          ├── ExerciseRoute
          │       └── retrieve by session record ID
          │
          └── for HR / speed / power / cadence / distance / etc.
                  │
                  ├── query session time interval
                  │
                  ├── retain every source record's provenance
                  │
                  ├── partition by DataOrigin + Device + record type
                  │
                  ├── prefer session-origin stream where available
                  │
                  └── never silently fuse unrelated origins
```

Cross-origin evidence can still be useful. A workout logger could create the session while a different wearable origin supplies cardiovascular data. Android's time-range example accommodates this ecosystem architecture. But merely overlapping in time does not establish that two records describe one physical activity, so My Mettle should never concatenate all Health Connect HR/speed/power records inside a session window into one supposedly coherent trace. citeturn24view5turn7view1

For cumulative records there is an additional trap: Health Connect aggregation has source-deduplication behaviour for particular activity/sleep use cases, but raw reads return origin-specific records. A workout snapshot should therefore preserve individual origins rather than summing arbitrary overlapping records from several apps. citeturn0search1turn7view1

### Samsung Health and Galaxy Watch

Samsung currently documents the intended flow as:

```text
Galaxy Watch
      ↓
Samsung Health on phone
      ↓
Health Connect
      ↓
My Mettle import
```

Samsung states that wearable data is transferred to Samsung Health on the smartphone and then shared with Health Connect when the integration is enabled. Samsung Health has supported Health Connect since version 6.22.5, and newly created or changed compatible Samsung Health data is shared once the permissions/link are configured. citeturn22view6turn12view1

Samsung's current documented Samsung Health → Health Connect mapping includes `ExerciseSessionRecord`, `HeartRateRecord`, `DistanceRecord`, `PowerRecord`, `SpeedRecord`, total exercise calories and several other health types. Samsung specifically says exercise-tracker distance, power, speed and VO₂max data are the versions shared for these exercise metrics. citeturn22view6

There are important limits:

* Samsung's current Health Connect mapping does **not document cycling or steps cadence records as Samsung Health outputs**. Samsung APIs and accessories can collect cadence internally, but that does not establish that Samsung currently exports cadence through Health Connect. Cadence must therefore be capability-detected, not expected. citeturn22view6turn13search2
* The fact that Samsung exports `HeartRateRecord`, `SpeedRecord` and `PowerRecord` means the Health Connect representation can contain series samples, but Samsung does **not document a guaranteed sample density for every workout type/watch/version**. My Mettle should measure whatever arrives. citeturn22view6turn2view3turn3view0
* Metric availability varies with workout type and what the watch/exercise tracker actually measured. Samsung's internal exercise data documentation, for example, describes exercise heart rate, speed and distance, but does not imply every exercise exposes every metric to Health Connect. citeturn12view2

Samsung also explicitly documents non-instantaneous watch behaviour. Watch data generally reaches the phone when the wearable reconnects and Samsung Health synchronises; opening Samsung Health or pulling down on its home screen can trigger/encourage watch-to-phone synchronisation. Continuous watch HR is not necessarily transferred immediately because Samsung employs battery-conscious synchronisation policies. Samsung does **not provide a universal guaranteed Health Connect latency** that My Mettle can safely encode. citeturn22view6turn12view1

Accordingly the correct UX for a missing workout is:

```text
No matching health workout has appeared yet.

[ Check again ]
[ Open Samsung Health ]
[ Enter manually ]
```

There should be no countdown promising “available in N minutes”, and this flow does not require background polling. Android itself recommends checking for new Health Connect data when an app becomes active and, where useful, periodically while it remains foregrounded. citeturn22view7

### Matching a My Mettle slot to a Health Connect session

The following is a **PRODUCT POLICY / MODELLING CHOICE**, not Android platform behaviour.

Use a reasonably generous candidate window rather than an automatic equality test. A defensible initial rule is:

```text
primary candidate window:
    MyMettleSession.start - 2 hours
    through
    MyMettleSession.end + 2 hours

fallback:
    recently completed compatible workouts from the same calendar day
```

Then rank, but **do not automatically commit**, using:

```text
type compatibility
× temporal overlap
× start-time proximity
× duration plausibility
× source
× not-already-imported
```

Exercise-type compatibility should distinguish:

```text
EXACT          running ↔ running
COMPATIBLE     treadmill ↔ running/walking
POSSIBLE       generic workout ↔ expected modality
INCOMPATIBLE   cycling ↔ rowing
```

A user selection remains necessary whenever more than one plausible session exists.

Idempotency should be enforced with a unique external-source claim approximately equivalent to:

```text
(provider, dataOrigin, sourceRecordType, sourceRecordId)
```

and preserve `clientRecordId`/version when available. A second attempt to import the same record should return the existing import rather than create another observation. Two distinct apps reporting nearly identical workouts should be flagged as **possible duplicates**, not merged based solely on timestamps.

## Canonical trace, evidence resolution and Room architecture

### Recommended domain model

The key design change is to make **temporal evidence generic while retaining the existing performance substrate**.

```kotlin
typealias ObservationId = String
