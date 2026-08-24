# N-BIO vNext — Health / Temporal Research Guide

> **Purpose:** implementation-facing evaluation and navigation guide for the Health Connect, temporal-evidence, conditioning and resistance-training HR DeepResearch pass.
>
> **Authority:** [`PLAN.md`](./PLAN.md) is the implementation contract. This guide explains how to use the research, what has been adopted, what remains experimental, and where to read the exact raw source. The raw report is indexed at [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md).
>
> This is deliberately **not a summary**. It is a routing/evaluation layer intended to prevent agents from either loading the entire research report unnecessarily or promoting modelling suggestions into biological fact.

## Contents

1. [How to use this guide](#1-how-to-use-this-guide)
2. [Executive evaluation](#2-executive-evaluation)
3. [Decision ledger](#3-decision-ledger)
4. [Temporal evidence architecture](#4-temporal-evidence-architecture)
5. [Evidence quality and acquisition semantics](#5-evidence-quality-and-acquisition-semantics)
6. [Exact timing and resistance-set alignment](#6-exact-timing-and-resistance-set-alignment)
7. [Provenance, chunks, updates and deletion](#7-provenance-chunks-updates-and-deletion)
8. [Room / physical trace storage](#8-room--physical-trace-storage)
9. [Cardio bout semantics](#9-cardio-bout-semantics)
10. [Modality-specific conditioning capability](#10-modality-specific-conditioning-capability)
11. [Resistance-training heart rate](#11-resistance-training-heart-rate)
12. [Systemic versus local state](#12-systemic-versus-local-state)
13. [Health Connect association and Samsung behaviour](#13-health-connect-association-and-samsung-behaviour)
14. [Trace-derived summaries](#14-trace-derived-summaries)
15. [Validation programme](#15-validation-programme)
16. [Explicit red lines](#16-explicit-red-lines)
17. [Open questions requiring device/user data](#17-open-questions-requiring-deviceuser-data)
18. [Raw research routing table](#18-raw-research-routing-table)

---

# 1. How to use this guide

For implementation work:

```text
PLAN.md
   ↓
this guide: relevant implementation question
   ↓
only the routed raw part(s)
   ↓
current source code
```

Do not read all seven raw parts by default.

When interpreting the research, preserve these categories:

- **PLATFORM FACT** — documented Android / Health Connect / Samsung behaviour.
- **PHYSIOLOGICAL EVIDENCE** — empirical relationship supported by literature.
- **MODELLING CHOICE** — computational proposal; useful but not discovered physiology.
- **PRODUCT POLICY** — My Mettle UX/risk/data-retention decision.
- **UNKNOWN / UNSUPPORTED** — current evidence does not justify a stronger claim.

For physiological questions, also keep separate:

- descriptive association;
- within-person predictive value;
- causal/mechanistic interpretation;
- practical modelling value.

A signal may be useful predictively without being a direct biological proxy. Conversely, a plausible physiological mechanism does not automatically justify prescription-driving use.

---

# 2. Executive evaluation

## 2.1 The research materially changes N-BIO-6

The existing scalar N-BIO-6 work remains correct and valuable, but the foundation is incomplete if it freezes at:

```text
SetRecord
→ PerformanceObservation
→ PerformanceMetricValue
```

The research establishes that future evidence is not uniformly scalar. Health Connect exposes:

- point-series evidence such as HR, speed, power and cadence;
- interval-valued evidence such as distance, steps, floors, elevation and activity intensity;
- spatial route evidence;
- source records that may be split into several independent provenance-bearing records during one workout.

The adopted foundation therefore becomes:

```text
SessionExercise
   │
   ├── SetRecord / performed bout
   │      ↓
   │   PerformanceObservation
   │      ├── scalar metric values
   │      └── links to temporal evidence
   │
   └── session-scoped temporal / physiological evidence
          ↓
       EvidenceTrace
          ├── POINT_SERIES
          ├── INTERVAL_SERIES
          └── SPATIAL_ROUTE-compatible representation
```

This is a **structural requirement**, not an instruction to implement Health Connect during N-BIO-6.

## 2.2 The research does not justify making HR prescription-driving now

Resistance-training HR is worth preserving because exercise identity, muscle mass involved, posture, contraction type, rest structure and breathing all produce structured cardiovascular responses.

However, current evidence does not establish that wrist-derived set-level HR reliably improves prediction of the next set after external performance and elapsed rest are already known.

Adopt:

```text
store now
align accurately
preserve quality/provenance
validate later
```

not:

```text
high HR = fatigue
low HR = ready
HR × muscle dose
```

## 2.3 A new future state boundary is warranted

The research strengthens a conceptual distinction between local muscular state and systemic physiological state.

The target architecture should be able to represent:

```text
LocalMuscleAcuteState
SystemicAcuteState
ConditioningState
CapabilityState
SkillState
```

`SystemicAcuteState` is an architectural state family / future consumer. N-BIO-6 does not infer it, and N-BIO-7 should not make HR-derived systemic state prescription-driving without held-out personal validation.

## 2.4 Conditioning traces are performance evidence, not graph decoration

Two sessions can share identical scalar averages while having materially different intensity distributions. Conditioning capability models must be able to consume temporal evidence where available and fall back to summaries where it is not.

---

# 3. Decision ledger

## MUST ADD BEFORE N-BIO-6 FOUNDATION FREEZE

- generic temporal evidence abstraction;
- point-series representation;
- interval-series representation with interval-value semantics;
- source-record/chunk boundaries;
- per-metric/per-trace temporal granularity;
- acquisition method separate from temporal granularity;
- semantic role separating performance output from physiological response/context;
- generic external-record provenance;
- source origin/device/type/ID/version/last-modified metadata;
- source start/end interval;
- immutable imported revisions / supersession;
- exact observation/set/bout start and end timestamps;
- timing quality/uncertainty;
- session-scoped trace ownership;
- observation↔trace links so one session HR trace can contextualise many sets without duplication;
- mixed manual/imported evidence on one observation;
- a versioned physical trace encoding strategy;
- source-state distinction for updated/deleted/disconnected/unavailable evidence.

## MUST DESIGN FOR NOW; FULL IMPLEMENTATION MAY FOLLOW LATER

- spatial route evidence;
- derived trace-summary provenance/versioning;
- trace QC/outlier/gap representation;
- disposable downsample/UI cache;
- derived interval segmentation;
- source update/deletion reconciliation.

## SAFE TO ADD LATER

- Android `HealthConnectMapper` and permissions;
- Health Connect changes-token synchronisation;
- actual cardio import UI;
- automatic interval detector;
- critical-speed / critical-power fitting;
- expected-HR residual model;
- personalised `SystemicCost`;
- cardio local-muscle exposure inference;
- HR-informed readiness or rest decisions, if validated.

## DO NOT MODEL YET

- HR→hypertrophy multiplier;
- deterministic HR→local-fatigue rule;
- cardio→equivalent hypertrophy sets;
- universal cross-machine cardio score;
- fixed HR rest threshold;
- population HR zones as personal ground truth.

Raw source: parts 6–7.

---

# 4. Temporal evidence architecture

## Use when

- extending N-BIO-6 persistence;
- adding traces to the domain;
- deciding whether a metric belongs as scalar, interval or series;
- preparing for later Health Connect ingestion.

## Research position

A single `timestamp → scalar` trace type is insufficient because temporal evidence has different semantics.

Adopt at least:

```text
TemporalRepresentation
- POINT_SERIES
- INTERVAL_SERIES
- SPATIAL_ROUTE

IntervalValueSemantics
- TOTAL_OVER_INTERVAL
- MEAN_OVER_INTERVAL
- STATE_OVER_INTERVAL
- UNSPECIFIED
```

Examples:

```text
Heart rate       POINT_SERIES
Speed            POINT_SERIES
Power            POINT_SERIES
Distance         INTERVAL_SERIES / TOTAL_OVER_INTERVAL
Steps            INTERVAL_SERIES / TOTAL_OVER_INTERVAL
Activity state   INTERVAL_SERIES / STATE_OVER_INTERVAL
Route            SPATIAL_ROUTE
```

Do not convert interval quantities into fake instantaneous samples.

## Adopted implementation consequence

Core N-BIO types remain Android-independent. A future Health Connect adapter maps platform DTOs into generic evidence structures.

`EvidenceTrace` should be able to exist at several scopes:

```text
PERFORMANCE_OBSERVATION
SET_RECORD / BOUT
SESSION_EXERCISE
WORKOUT_SESSION
```

Session scope is important for continuous physiological traces such as HR.

## Do not infer

- uniform sampling frequency from producer identity;
- missing sample = zero;
- interval total = instantaneous rate;
- route altitude = measured elevation without quality/algorithm handling.

Raw source: parts 1–3.

---

# 5. Evidence quality and acquisition semantics

## Use when

- adding persistence fields to scalar metrics or traces;
- designing manual cardio fallback;
- importing mixed manual + sensor evidence;
- deciding how downstream models see evidence quality.

## Key research correction

Do not use one enum such as:

```text
TRACE
INTERVAL
SUMMARY
MANUAL_ESTIMATE
```

because it conflates **information structure** with **acquisition method**.

Adopt independent dimensions.

### Temporal granularity

```text
TRACE
INTERVAL
SUMMARY
```

### Acquisition

```text
SENSOR_RECORDED
DEVICE_DERIVED
AUTOMATICALLY_INFERRED
USER_REPORTED
USER_ESTIMATE
UNKNOWN
```

### Semantic role

```text
PERFORMANCE_OUTPUT
PHYSIOLOGICAL_RESPONSE
MOVEMENT_CONTEXT
ENVIRONMENTAL_CONTEXT
```

Example mixed observation:

```text
duration        SUMMARY   DEVICE_DERIVED
speed(t)        TRACE     SENSOR_RECORDED
distance        INTERVAL  DEVICE_DERIVED
heartRate(t)    TRACE     SENSOR_RECORDED / PHYSIOLOGICAL_RESPONSE
incline = 4%    SUMMARY   USER_REPORTED / MOVEMENT_CONTEXT
```

A manually copied treadmill grade may be highly accurate while still being a summary. A user-estimated speed is different. Preserve that distinction.

Raw source: part 2.

---

# 6. Exact timing and resistance-set alignment

## Use when

- modifying `SetRecord` / `PerformanceObservation` timestamps;
- designing workout-set lifecycle events;
- preparing future HR analysis;
- calculating actual rest duration.

## Adopted decision

N-BIO-6 must preserve real bout/set bounds where observable:

```text
startedAt
endedAt
timingQuality / uncertainty
```

Do not rely only on `completedAt`.

Why:

- set duration becomes observable rather than reconstructed;
- actual rest duration becomes observable;
- one session-scoped HR trace can be aligned to multiple sets;
- pre-set, set-rise, post-set recovery and pre-next-set features become possible later;
- timing uncertainty can be represented instead of silently pretending button timing is exact physiology.

The research considers accurate set boundaries more valuable than forcing a particular HR sample frequency. Do not require 1 Hz or any other fixed density.

## Product/implementation nuance

If the UI cannot know the exact first contraction timestamp, record the event it genuinely observes and assign a timing-quality class rather than naming it exact.

Examples could include:

```text
EXACT_USER_ACTION_BOUND
APP_EVENT_BOUND
INFERRED_FROM_COMPLETION
IMPORTED_SOURCE_BOUND
UNKNOWN
```

Exact enum design remains an implementation decision; uncertainty must not be erased.

Raw source: parts 3–5.

---

# 7. Provenance, chunks, updates and deletion

## Use when

- designing `EvidenceTraceChunk`;
- adding external-source identity;
- future Health Connect sync;
- deduplication;
- correcting imported records.

## Source records are provenance boundaries

One logical workout trace may be composed of several source records. Preserve those boundaries.

Conceptually:

```text
EvidenceTrace: HEART_RATE
  ├── source chunk A
  ├── source chunk B
  └── source chunk C
```

Each chunk should reference generic external provenance such as:

```text
provider
dataOrigin
sourceRecordType
sourceRecordId
clientRecordId?
clientRecordVersion?
sourceDevice...
recordingMethod?
sourceLastModifiedAt?
importedAt
sourceStart / sourceEnd
```

Do not put Android classes in N-BIO domain types.

## Update contract

Adopt immutable revisions:

```text
source revision 1
    ↓
source changes
    ↓
Native revision 2 supersedes 1
```

Do not mutate revision 1.

## Deletion/disconnect contract

Keep separate states such as:

```text
AVAILABLE
UPDATED_AT_SOURCE
DELETED_AT_SOURCE
PERMISSION_UNAVAILABLE
SOURCE_DISCONNECTED
UNKNOWN
```

Loss of permission is not proof of deletion.

Deleting/unlinking a source connection is not the same operation as deleting My Mettle's retained Native evidence.

Raw source: parts 1–3 and 6.

---

# 8. Room / physical trace storage

## Use when

- selecting Room entities;
- choosing row-per-sample versus chunk payload;
- implementing trace codecs.

## Research recommendation

A domain API may expose individual samples, but long-lived Room persistence should prefer source-aligned chunks over one SQLite row per sample.

Reasons:

- long-term row/index overhead becomes substantial;
- normal access is workout/trace-oriented, not global sample querying;
- source-record provenance naturally defines chunk boundaries;
- a codec version lets physical representation evolve without changing evidence semantics.

Recommended physical shape:

```text
evidence_trace
external_evidence_artifact
evidence_trace_chunk
observation_trace_link
```

with a versioned binary payload for samples.

The research proposes columnar timestamp-delta + numeric values as a simple starting point. Treat the exact codec/compression as an engineering choice, not biological architecture.

### Canonical / derived / cache separation

**Canonical:** received source samples/intervals, timestamps, units, provenance and boundaries.

**Derived:** time-weighted means, sustained peaks, work, HR recovery, interval segmentation, QC flags.

**Disposable cache:** graph downsampling / display payload.

Never let downsampling or smoothing replace canonical evidence.

Raw source: parts 2–3.

---

# 9. Cardio bout semantics

## Use when

- deciding how cardio fits `SetRecord`;
- designing the later import UI;
- handling interval workouts.

## Adopted decision

A continuous cardio workout is one performed **bout/observation**, even if later analysis detects several intensity intervals.

If `SetRecord` is semantically generic enough to mean one performed bout, keep it.

Do not create eight raw sets because an algorithm detects eight treadmill intervals.

Derived intervals belong in a recomputable structure such as:

```text
DerivedObservationSegment
- parentObservationId
- start/end
- algorithmVersion
```

Only user/prescription-intended separate bouts become canonical child bouts.

## UX direction for N-BIO-9

```text
[ Import from Health ]
[ Enter manually ]
```

Imported cardio may display a compact trace plus source and summaries. Manual fallback remains valid lower-information evidence.

Raw source: parts 5–6.

---

# 10. Modality-specific conditioning capability

## Use when

- implementing conditioning capability in N-BIO-7+;
- deciding which trace is primary for a modality.

## Running / treadmill

Strongest evidence:

```text
speed(t)
duration
distance
grade/context where known
```

Candidate family: speed-duration / critical-speed.

Keep treadmill execution, grade and surface/environment context specific. Do not hardcode universal `1% treadmill = outdoors` conversion.

## Cycling

Strongest portable evidence where calibrated:

```text
power(t)
duration
cadence(t) context
```

Candidate family: power-duration / critical power / W′-style models.

Indoor bike speed is often device-derived and should remain profile-specific when power is unavailable.

## Rowing

Strongest evidence:

```text
power/pace(t)
distance
duration
stroke rate
```

Cross-manufacturer equivalence remains conservative.

## Stair climbing / stepmill

Strongest evidence:

```text
step cadence
elapsed duration
ascent/elevation/floors
body mass
machine-local level if manually/device recorded
```

`level 8` is device/profile-local. It is not SI intensity.

## Elliptical / ordinal machines

Use calibrated power if genuinely available. Otherwise preserve device-local resistance/cadence/setting evidence without invented physical equivalence.

## Critical principle

There is no universal cardio capability scalar.

Raw source: parts 3–4.

---

# 11. Resistance-training heart rate

## Use when

- deciding what HR to preserve now;
- designing future feature extraction;
- considering fatigue/readiness inference.

## What the literature supports now

Resistance exercise produces structured cardiovascular responses affected by:

- exercise identity;
- active muscle mass;
- dynamic versus isometric contraction;
- posture;
- work rate;
- rest/set configuration;
- breathing/Valsalva;
- prior accumulated work.

This supports storing continuous HR and aligning it to the workout timeline.

It does **not** establish HR as local muscle activation, local hypertrophic stimulus, or local contractile fatigue.

## Highest-value future experiment

The report recommends testing incremental predictive utility:

```text
Model A
performance history

Model B
A + set/rest timing

Model C
B + HR features
```

Target examples:

```text
next-set reps/load/duration/completion
later-session underperformance
spontaneous rest extension
load reduction
exercise abandonment
```

Use chronologically held-out sessions. Do not randomly split neighbouring sets from one workout between train/test.

No HR feature becomes prescription-driving until Model C repeatedly beats Model B within the individual.

## Candidate HR features — experimental unless stated otherwise

- pre-set HR;
- set-start HR;
- set HR rise;
- sustained/peak HR;
- time to peak;
- HR AUC above a versioned baseline;
- early post-set recovery slope;
- HRR30/HRR60;
- pre-next-set HR;
- fractional recovery;
- set-to-set drift;
- observed-minus-expected HR residual;
- exercise-specific recovery kinetics.

The **expected-HR residual** is the highest-priority modelling experiment:

```text
ExpectedHRFeature(
  user,
  exerciseProfile,
  external performance,
  setDuration,
  restDuration,
  setOrdinal,
  sessionElapsedTime,
  recentWork
)

HRResidual = observed - expected
```

Interpret the residual initially as an anomaly observation, not a diagnosis.

Raw source: parts 4–6.

---

# 12. Systemic versus local state

## Use when

- designing N-BIO-7 state decomposition;
- considering how fatigue affects future performance;
- adding `SystemicCost` later.

## Adopted conceptual decomposition

The research supports allowing:

```text
poor systemic state
→ greater strain / reduced available capability
→ worse future performance
```

without requiring:

```text
poor systemic state
→ retroactively lower local stimulus from work already completed
```

External performance remains strong evidence of what was actually performed.

Add/retain conceptual separation among:

```text
LocalMuscleAcuteState
SystemicAcuteState
ConditioningState
CapabilityState
SkillState
```

HR-derived state does not belong inside `SkillState`.

## Future `SystemicCost`

Collect inputs now; do not seed a confident constant.

Possible future latent:

```text
SystemicCost(
  exerciseProfile,
  externalWork,
  duration,
  restStructure,
  physiologicalResponse,
  downstreamPerformanceEffects
)
```

not:

```text
SystemicCost = averageHR
```

Validation should focus on downstream behaviour: do similar local-performance interventions systematically differ in HR residual/recovery/later-session degradation for the same user?

Raw source: part 5.

---

# 13. Health Connect association and Samsung behaviour

## Use when

- implementing N-BIO-9 Health Connect import;
- matching sessions;
- handling `DataOrigin`;
- building Samsung-specific UX.

## Platform topology

`ExerciseSessionRecord` defines workout interval/type. Ordinary granular records are generally queried by time; `ExerciseRoute` is a special route-by-session-record-ID case.

There is no documented universal foreign key from an exercise session to ordinary HR/speed/power/cadence records.

## DataOrigin rule

Do **not** implement either extreme:

```text
same origin = certainly associated
```

or:

```text
different origin = certainly unrelated
```

Adopt:

- same-origin stream = default high-confidence candidate;
- partition ancillary evidence by origin + device + record type;
- never silently fuse all overlapping origins;
- ambiguous cross-origin evidence requires stronger evidence/user selection.

## Samsung

Current documented flow:

```text
Galaxy Watch
→ Samsung Health
→ Health Connect
→ My Mettle
```

Current Samsung mapping supports several useful workout record types including exercise session, HR, distance, speed and power. Cadence export is not guaranteed by current public documentation.

Do not promise instant sync or fixed sample density.

Missing-workout UX should support:

```text
[ Check again ]
[ Open Samsung Health ]
[ Enter manually ]
```

## Device-fixture requirement

Before N-BIO-9 is finalised, test the actual Galaxy Watch / Samsung Health stack by workout type. Measure what record types, sample densities, origins and route/cadence behaviour are really emitted.

Raw source: parts 1 and 7.

---

# 14. Trace-derived summaries

## Use when

- building UI summaries;
- producing analysis exports;
- feeding conditioning models.

Derived summaries are not raw evidence.

High-value deterministic/versioned summaries include:

- elapsed/active duration;
- coverage fraction;
- source distance or derived `∫speed dt` with provenance;
- time-weighted speed statistics;
- sustained-speed windows;
- time-weighted power;
- integrated work;
- best sustained power;
- cadence mean/variability;
- elevation/floor/step aggregates under coherent-origin rules;
- time-weighted HR;
- sustained HR peak;
- trace quality/gap/outlier features.

More interpretive summaries such as HR zones, cardiovascular drift, interval segmentation and recovery features must carry algorithm/version/input provenance.

For irregular traces, prefer time-weighted statistics to unweighted sample averages.

Raw source: part 4.

---

# 15. Validation programme

## N-BIO-6 temporal substrate tests

At minimum test:

1. point-series round trip;
2. interval-series round trip with `TOTAL_OVER_INTERVAL` semantics;
3. source-record chunk boundaries survive persistence/export;
4. mixed manual summary + imported trace on one observation;
5. session-scoped HR trace linked to several resistance sets without sample duplication;
6. exact/set timing bounds and timing-quality round trip;
7. source update creates superseding revision, not mutation;
8. source deletion state does not delete Native evidence;
9. gap preservation without interpolation;
10. sensor spike survives raw round trip while derived QC may flag it;
11. ordinal machine metric remains device-local;
12. two equal-average cardio sessions with different traces remain distinguishable;
13. automatically detected intervals remain derived/recomputable;
14. encoded trace chunks decode deterministically across codec version tests;
15. derived-state deletion never alters raw trace/artifact/chunk evidence.

## Later Health Connect/device tests

Test actual Samsung output for treadmill, cycling, stair/step, walking/running and other supported modalities:

- record types emitted;
- source origins;
- sample density/gaps;
- HR/speed/power availability;
- cadence availability;
- route availability;
- sync latency behaviour;
- record update/deletion behaviour.

## Later HR predictive validation

Retain both positive and negative examples. Do not construct a dataset where elevated HR always precedes performance loss.

Raw source: part 6.

---

# 16. Explicit red lines

Reject:

- scalar average speed as the complete representation of interval cardio when a trace exists;
- average HR as universal training load;
- HR as a direct muscle-stimulus multiplier;
- HR as a deterministic local-fatigue measure;
- calorie burn as universal dose;
- cross-machine comparison of ordinal levels;
- cadence as effort by itself;
- watch HR zones as immutable personal truth;
- age-predicted HRmax as exact personal HRmax;
- cardio minutes → hypertrophy sets;
- import without source provenance;
- arbitrary fusion of overlapping Health Connect origins;
- deleting Native evidence merely because an external source disappeared;
- manual summary treated as information-equivalent to a full trace;
- high HR always = poor recovery;
- low HR always = improved conditioning;
- HRR threshold as rest-timer truth;
- exercise HR as local muscle activation;
- smoothing/deleting canonical sensor spikes;
- interpolating gaps and storing interpolation as observed evidence;
- critical-speed/power estimates from uninformative ordinary sessions without uncertainty;
- assuming Samsung cadence or instant sync from undocumented behaviour.

Raw source: parts 6–7.

---

# 17. Open questions requiring device/user data

These are intentionally unresolved rather than converted into constants:

1. What sample density does the actual Galaxy Watch + current Samsung Health stack export for each workout type?
2. Does Samsung expose cadence through Health Connect in the user's real configuration?
3. How often are workout session and ancillary records same-origin versus cross-origin?
4. How stable are exercise-specific HR response distributions within the user over months?
5. Does HR add held-out predictive value beyond performance + elapsed rest?
6. Can a useful personalised `SystemicCost` latent be validated from downstream performance effects?
7. How much conditioning capability can be learned from ordinary recreational sessions versus deliberately informative efforts?
8. Can cardio provide useful local-muscle fatigue/exposure context without pretending it is resistance hypertrophy dose?

These questions belong in empirical validation, not population-derived hardcoded heuristics.

---

# 18. Raw research routing table

| Implementation question | Read raw part(s) |
|---|---|
| Overall temporal architecture / Health Connect types | 01 |
| Session↔granular-record association / DataOrigin | 01 |
| Samsung sync / metric availability | 01 |
| Domain types for traces/evidence quality | 01–02 |
| Acquisition vs granularity | 02 |
| Source chunks / immutable provenance | 02–03 |
| Raw vs derived vs UI cache | 02–03 |
| Exact timestamps / set bounds | 03 |
| Room storage / BLOB chunking | 03 |
| Conditioning modality models | 03–04 |
| Cardio→muscle evidence / TRIMP | 04 |
| Trace-summary registry | 04 |
| Resistance HR feature registry | 04–05 |
| HR residual/readiness/systemic state | 05 |
| Cardio UI / bout semantics | 05–06 |
| Predictive validation vectors | 06 |
| N-BIO-6 foundation-freeze checklist | 06 |
| Explicit rejections | 06–07 |
| Evidence-quality ledger / open questions | 07 |

The raw parts are linked from [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md).