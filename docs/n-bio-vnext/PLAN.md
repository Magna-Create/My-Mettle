# My Mettle Native — N-BIO vNext Execution Plan

> **Authority:** This document is the forward implementation contract for N-BIO vNext.
>
> Read this first. Use [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) for the core biological/performance research and [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md) for temporal evidence, conditioning, Health Connect and resistance-training HR. Consult the corresponding raw research only for targeted evidence, exact wording, equations, platform behaviour or citations.
>
> Core raw research: [`RESEARCH_RAW.md`](./RESEARCH_RAW.md).
>
> Health/temporal raw research: [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md), an index over verbatim storage parts.
>
> Existing `docs/N_BIO_*.md` files remain historical implementation-stage documentation. Where they conflict with this vNext plan, this document governs unless the conflict concerns an already-established canonical anatomy/reference-data invariant.

## Contents

1. [Purpose and completion criterion](#1-purpose-and-completion-criterion)
2. [Decision labels and authority rules](#2-decision-labels-and-authority-rules)
3. [Non-negotiable system invariants](#3-non-negotiable-system-invariants)
4. [Target vNext architecture](#4-target-vnext-architecture)
5. [N-BIO-6 — Generalised Performance, Prescription & Temporal Evidence Foundation](#5-n-bio-6--generalised-performance-prescription--temporal-evidence-foundation)
6. [N-BIO-7 — Adaptive Biological Inference](#6-n-bio-7--adaptive-biological-inference)
7. [N-BIO-8 — Adaptive Programme Resolution](#7-n-bio-8--adaptive-programme-resolution)
8. [N-BIO-9 — Context, Health, Intelligence & Data](#8-n-bio-9--context-health-intelligence--data)
9. [Native Cutover](#9-native-cutover)
10. [Cross-phase persistence and provenance contract](#10-cross-phase-persistence-and-provenance-contract)
11. [Validation contract](#11-validation-contract)
12. [Expected package/file work](#12-expected-packagefile-work)
13. [Explicitly deferred / do not implement](#13-explicitly-deferred--do-not-implement)
14. [Agent execution protocol](#14-agent-execution-protocol)
15. [Global definition of done](#15-global-definition-of-done)

---

# 1. Purpose and completion criterion

Evolve the current conservative N-BIO scaffold into the production performance, biological inference, translation and programme-resolution system required **before My Mettle Native becomes the authoritative training application**.

The existing implementation deliberately began with conservative placeholders:

- performed working sets become recruitment-weighted exposure;
- muscle development remains a neutral prior;
- recent stimulus/recovery are not claimed;
- performance translation is same-execution-profile only;
- ordinary workout evidence contains no subjective RIR requirement.

Preserve the architecture that made those placeholders safely replaceable:

```text
canonical anatomy / reference physiology
                ↓
exercise + immutable/versioned execution semantics
                ↓
immutable raw performance + temporal evidence
                ↓
versioned/recomputable inference
                ↓
profile capability + local/systemic biological state
                ↓
exercise-independent programme intent
                ↓
session resolution + metric-general prescription
                ↓
immutable historical session snapshot
```

Native becomes authoritative only when:

- heterogeneous resistance, hold, unilateral, ordinal and conditioning performance is represented faithfully;
- temporal evidence can be preserved without flattening or destructive smoothing;
- N-BIO capability/stimulus/fatigue/development behaviour matches the accepted vNext contract;
- prescriptions are uncertainty-aware and can legitimately remain blank;
- programme resolution consumes the new state correctly;
- real Lite history imports without invented information;
- full backup/restore/replay is validated;
- Health Connect, physiological evidence and note-intelligence boundaries are safe;
- the first authoritative Room schema is selected and migration policy is locked.

`fallbackToDestructiveMigration(true)` remains acceptable throughout development and is removed **last**, during Native cutover.

---

# 2. Decision labels and authority rules

Use these labels for architectural/model decisions:

- **[STRUCTURAL]** — architecture/data requirement independent of a particular biological equation.
- **[RESEARCH-BACKED]** — direction/relationship sufficiently supported to encode.
- **[MODELLING-ASSUMPTION]** — useful computational form proposed by research, not a discovered physiological law; must be versioned and replaceable.
- **[PRODUCT-POLICY]** — UX/risk/utility threshold selected by My Mettle rather than physiology; explicit and versioned/configurable where material.
- **[DEFER]** — schema/interface may anticipate it, but do not make it behaviour-driving yet.
- **[DO-NOT-IMPLEMENT]** — explicitly rejected for the current generation.

Rules:

1. Never promote a modelling assumption to scientific fact in code comments, UI or diagnostics.
2. Never silently invent constants where research gives only directional evidence.
3. Scientific/model evidence quality and user-specific posterior uncertainty are separate concepts.
4. A narrow posterior built on a weak scientific relationship is not strong biological truth.
5. Preserve raw/recomputable evidence so later model versions can replace assumptions.
6. When research is underdetermined, record the decision in model/config provenance rather than hiding it in code.
7. `null` / unknown is valid. Do not force numerical completeness for presentation convenience.
8. Platform facts, physiological evidence, modelling choices and product policy must remain distinguishable.
9. Descriptive association, within-person prediction, causal mechanism and practical modelling value must not be conflated.

---

# 3. Non-negotiable system invariants

## 3.1 Raw evidence is canonical

**[STRUCTURAL]** Store what happened before interpreting why it happened.

Historical evidence must retain enough information to answer:

- what exercise was performed;
- which execution-profile **version** was used;
- which recruitment-profile version described historical semantics;
- which side / entry basis / implement count applied;
- which scalar metrics were entered or observed;
- entered values/units and canonical values/units;
- when the bout/set actually occurred, with timing quality where necessary;
- which temporal traces/interval evidence were associated with it;
- how each value/trace was acquired;
- which external source/artifact/device supplied imported evidence;
- equipment identity and relevant execution context;
- body-mass context where required;
- what was prescribed and the evidence/model behind it;
- which training targets the session item was resolving;
- raw user-authored notes.

Derived state may be discarded/rebuilt without changing these records.

## 3.2 Separate four layers of evidence

**[STRUCTURAL]** The data model must keep conceptually separate:

```text
WHAT HAPPENED
external performance / machine output

HOW IT WAS OBSERVED
manual / sensor / device-derived / imported provenance

HOW THE BODY RESPONDED
heart rate and future physiological evidence

WHAT N-BIO INFERS
capability / dose / fatigue / recovery / development / systemic state
```

Heart rate is physiological-response evidence, not an `EXTERNAL_LOAD`-style performance output.

## 3.3 Infer the narrowest quantity supported

**[RESEARCH-BACKED]** A performed observation is strongest evidence about performance on that execution profile; weaker evidence about muscle exposure/stimulus; weaker still about long-term adaptation; and essentially no direct evidence about segmental anatomical volume.

Posterior uncertainty widens across inferential boundaries.

## 3.4 No universal muscle-load or cardio scalar

**[RESEARCH-BACKED]** Do not collapse kilograms, repetitions, seconds, conditioning capability, cardiovascular response, fatigue and development into one conserved quantity.

Muscle-dose values remain muscle-local/model-version-local. Conditioning capability remains modality/family-specific.

## 3.5 Capability is not development

**[RESEARCH-BACKED]** Fast task-specific improvement and slow muscle-general adaptation must be separable.

Rapid improvement on one unfamiliar profile preferentially updates profile capability/skill. Persistent improvement across overlapping profiles may gradually support a shared muscle-development latent.

## 3.6 Unknown effort remains unknown

**[RESEARCH-BACKED]** Do not restore RIR as a required input and do not derive an integer RIR from load/reps alone.

`80 kg × 8` demonstrates at least eight repetitions at that resistance. It does not prove 8RM or exact proximity to failure.

## 3.7 Historical semantics are immutable/versioned

**[STRUCTURAL]** Editing execution, recruitment, trace interpretation or source mappings must not silently mutate historical meaning.

Historical-semantics replay and current-model reinterpretation are explicit separate modes.

## 3.8 Blank is a valid intelligent output

**[PRODUCT-POLICY]** If predictive uncertainty is too large, emit no numerical prescription rather than fabricate a conversion.

## 3.9 Raw traces are never destructively cleaned

**[STRUCTURAL]** Preserve received samples, gaps, timestamps and source boundaries. Sensor spikes may be flagged in derived QC but not overwritten. Interpolation/smoothing is derived data, never canonical observation.

## 3.10 AI note interpretation is not N-BIO inference

Gemini Nano / ML Kit may classify explicitly allow-listed user-authored text. It cannot directly mutate workout evidence, recruitment coefficients, biological state or prescriptions.

Health Connect/physiological evidence must be structurally impossible to pass through the note-interpreter input type.

## 3.11 Development imports are compatibility tools, not ontology owners

Lite migration code adapts to vNext. vNext must not be distorted to preserve obsolete Lite concepts. Unknown legacy facts remain unknown.

---

# 4. Target vNext architecture

## 4.1 Required state decomposition

| Layer | State / evidence | Required behaviour |
|---|---|---|
| Raw scalar performance | `PerformanceObservation` + metric values | immutable entered/canonical facts |
| Raw temporal evidence | `EvidenceTrace` / source chunks | immutable point/interval/spatial evidence with provenance |
| Profile capability | `CapabilityState_e` | execution-family-specific demonstrated-performance posterior |
| Profile adaptation | `SkillState_e` | fast task-specific neural/technical/coordination latent |
| Set demand | `SetDemand_s` | uncertainty about closeness to current performance frontier; not RIR |
| Muscle exposure | `Exposure_sm` | recruitment-weighted conservative set-equivalent exposure |
| Muscle effective dose | `EffectiveDose_sm` | uncertain exposure adjusted by inferred demand |
| Local acute state | `RecentStimulus_m`, `Fatigue_m` / `LocalMuscleAcuteState` | recent local dose and transient performance suppression |
| Readiness | `Recovery_m` | bounded readiness posterior/index; never literal `% tissue repaired` |
| Systemic acute state | `SystemicAcuteState` | separate future systemic/physiological strain state; initially conservative/experimental |
| Long-term muscle state | `Development_m` | slow user-relative latent, prior near `1.0` |
| Structural state | `VolumeScale_m`, `StructuralCapacityScale_m` | nullable; non-driving without genuine user evidence |
| Conditioning | `ConditioningState_f` | modality/family-specific power/speed/duration capability |

The decomposition is required. Exact estimators remain versioned where research identifies modelling rather than physiological law.

## 4.2 Required pipeline

```text
IMMUTABLE SCALAR + TEMPORAL EVIDENCE
        ↓
PERFORMANCE NORMALISATION
        ↓
CAPABILITY FAMILY
  ├─ dynamic load↔rep
  ├─ loaded hold load↔duration
  ├─ duration/context
  ├─ repeated contraction
  └─ conditioning power/speed↔duration
        ↓
SET-DEMAND POSTERIOR
        ↓
EXECUTION RECRUITMENT → MUSCLE EXPOSURE
        ↓
EFFECTIVE DOSE POSTERIOR
        ↓
LOCAL RECENT STIMULUS + FATIGUE
        ↓
SLOW DEVELOPMENT + FAST SkillState
        ↓
OPTIONAL / VALIDATED SYSTEMIC-CONTEXT CONSUMERS
        ↓
CROSS-PROFILE TRANSLATION
        ↓
SESSION PRESCRIPTION OR NULL
```

Physiological traces such as HR do not automatically enter local stimulus. They are separate evidence available to validated consumers.

## 4.3 Posterior contract

Important inferred states/predictions must be able to persist at least:

```text
credibleLower05 / p05
estimateMedian / p50
credibleUpper95 / p95
posteriorVariance
observationCount
effectiveIndependentSessionCount
firstEvidenceAt
lastEvidenceAt
evidenceFamily
provenance
modelVersion
referenceProfileVersion
```

Prefer a shared `PosteriorSummary<T>` / `EstimateDistribution<T>` plus model-specific parameter state.

Do not add heavyweight probabilistic-programming infrastructure merely to satisfy the representation.

---

# 5. N-BIO-6 — Generalised Performance, Prescription & Temporal Evidence Foundation

## Goal

Make Native capable of faithfully representing every reasonable training/conditioning observation, exercise preference, prescription and future temporal evidence source **without implementing final biological equations yet**.

N-BIO-6 is complete only when both halves are satisfied:

```text
A. metric-general scalar foundation
B. temporal/provenance foundation
```

Actual Health Connect ingestion remains later.

## 5.1 Generic performance domain

Maintain/implement concepts equivalent to:

```text
PerformanceMetric
MetricFamily
PerformanceSchema
PerformanceObservation
PerformanceTarget / MetricTarget
Quantity
UnitDefinition / UnitId
UnitConverter
ResistanceModel
Laterality
```

Metric/capability families include at least:

```text
DYNAMIC_RESISTANCE
BODYWEIGHT_RESISTANCE
LOADED_HOLD
DURATION_ONLY
REPEATED_CONTRACTION
POWER_DURATION
SPEED_DURATION
DEVICE_ORDINAL
```

Metrics include at least:

```text
EXTERNAL_LOAD
ASSISTANCE
REPETITIONS
DURATION
DISTANCE
SPEED
PACE
INCLINE_GRADE
MACHINE_LEVEL
POWER
CADENCE
STEPS
FLOORS
ELEVATION_GAIN
```

Do not create combinatorial enums such as `LOAD_REPS_DURATION`.

## 5.2 Canonical quantities and entered-unit preservation

Persist entered and canonical representations.

Recommended canonical conventions:

```text
mass/load       kg
duration        s
distance        m
speed           m/s
grade           dimensionless fraction
power           W
cadence         events/min
repetitions     integer
steps/floors    integer
```

An uncalibrated machine `level 8` remains ordinal/device-local. Count-like concepts are not mutually convertible merely because they are counts.

Unit conversion must be deterministic and independently tested, including negative grade.

## 5.3 Measurement semantics belong to immutable execution-profile versions

Generic `Exercise` is the stable movement identity.

`ExecutionProfileVersion` owns/references:

- metric/capability family;
- performance schema;
- equipment identity/type;
- resistance relationship;
- bodyweight contribution semantics;
- assistance semantics;
- entry basis (`TOTAL`, `PER_HAND`, `PER_SIDE`);
- implement count where material;
- allowed values/increments/min/max;
- ROM / technique class where relevant;
- resistance-curve class;
- laterality mode;
- recruitment-profile version;
- created/effective/superseded metadata.

Historical evidence references the exact version used.

## 5.4 Generic performed-bout storage

Long-term scalar contract:

```text
set_record / performed-bout identity
        ↓
performance_observation
        ↓
performance_metric_value
```

`SetRecord` may keep its name only if its semantics are generic enough to mean one performed bout rather than a mandatory resistance set.

One set/bout may contain several observations (e.g. LEFT and RIGHT). Each observation may contain several metrics.

Mutable save-on-keystroke state remains a non-historical draft and cannot enter inference/history until committed as immutable evidence.

### `set_observation`

Owns one performed observation:

```text
id
setRecordId
ordinal
bodySide
completedAt
source
bodyMassContext / snapshot reference or explicit value when required
```

### `set_metric_value`

Owns arbitrary measured dimensions:

```text
observationId
metric
enteredValue
enteredUnit
canonicalValue
canonicalUnit
```

One set may contain several observations (for example LEFT and RIGHT) and each observation may contain several metrics.

A session-level body-mass snapshot may remain the default context. If an observation stores/references its own body-mass context, precedence must be explicit and historical values must never drift when later body measurements change.

**[PRODUCT / PERSISTENCE]** Active-workout save-on-keystroke state is not performed evidence. If
draft entry must survive navigation or process recreation, store it in an explicitly non-historical
draft buffer keyed by set and metric. Logging appends an immutable `set_observation` plus its metric
values, then clears the draft. Corrections append a superseding observation; they do not update the
original observation or metric rows in place. Inference, history evidence and export must ignore the
draft buffer.

Do not create three silently competing bodyweight values across session, session-exercise and observation layers.

## 5.5 Laterality

Support at least:

```text
LEFT
RIGHT
BILATERAL
ALTERNATING
NOT_APPLICABLE
UNKNOWN   // historical legacy when side genuinely was not recorded
```

Do not average asymmetric evidence at ingestion.

## 5.6 Resistance semantics

Preserve:

```text
EXTERNAL
ASSISTANCE
BODYWEIGHT
BODYWEIGHT_PLUS_EXTERNAL
NONE / DEVICE_ORDINAL
```

Do not equate these directly to muscle force.

A profile resistance coordinate such as:

```text
R = k_bw * bodyMass + k_ext * externalLoad - k_assist * assistance
```

is a versioned modelling assumption, not persistence truth.

Hard invariant: at fixed body mass/reps, lower assistance is harder/higher resolved resistance.

Missing required body mass/load/assistance yields unknown rather than fabricated resolution.

## 5.7 Entry basis

Preserve total / per-hand / per-side semantics.

Do not multiply unilateral 20 kg evidence into 40 kg merely to resemble a bilateral exercise.

## 5.8 Recruitment versioning

Recruitment definitions are immutable/versioned and retain:

```text
muscleSegmentId
weighting [0,1]
role
uncertainty/confidence
provenance
applicable ROM/technique/resistance context
model version
```

`weighting` is an independent muscle-local exposure coefficient, not load share, EMG percentage or conserved total. Allocations need not sum to 1.

## 5.9 Similarity features for later translation

Persist/deterministically derive versioned features including:

```text
muscle exposure vector
joint actions / movement pattern
single-/multi-joint
contraction type
ROM / long-length region
kinetic chain
bodyweight/assistance mechanism
laterality
equipment class
resistance curve
grip/support constraints
entry basis
metric family
```

Do not persist one universal similarity score as truth.

## 5.10 Metric-general prescriptions end-to-end

Replace mandatory:

```text
sets + repRange + prescribedLoad
```

with per-set generic metric targets:

```text
EXACT
RANGE
MINIMUM
MAXIMUM
OPEN
```

Generalise the entire path:

```text
routine/preference
→ candidate selection
→ prescription request
→ generated prescription
→ session snapshot
→ workout UI/history
```

No hidden mandatory rep range may remain in current write paths.

Legacy rep preferences may translate to `REPETITIONS` targets for compatible dynamic-resistance profiles only.

## 5.11 Per-metric prescription provenance

Prescription evidence is metric-specific:

```text
source
sourceObservationId / sourceSetId
inferenceRunId
anchor / posterior
modelVersion
```

Historical prescriptions are immutable snapshots.

## 5.12 Development Lite importer

Keep the development importer usable while destructive migrations remain enabled.

Translate factual legacy load/reps/duration/distance into generic observations.

Never invent laterality, incline, power, cadence, execution geometry or machine calibration.

Imported evidence remains distinguishable from Native-recorded evidence.

The final hardened Lite→Native translator still occurs at cutover.

## 5.13 Generic temporal-evidence substrate

**[STRUCTURAL — NEW FOUNDATION REQUIREMENT]** Add concepts equivalent to:

```text
EvidenceTrace
EvidenceTraceChunk
ExternalEvidenceArtifact / ExternalRecordProvenance
EvidenceQuality
EvidenceSemanticRole
TemporalRepresentation
IntervalValueSemantics
AcquisitionMethod
EvidenceGranularity
TraceScopeType
TimingQuality
```

The exact class names may differ. The semantic contract may not.

### Temporal representation

Support:

```text
POINT_SERIES
INTERVAL_SERIES
SPATIAL_ROUTE   // representation designed now; full route ingestion may follow later
```

### Interval semantics

Support at least:

```text
TOTAL_OVER_INTERVAL
MEAN_OVER_INTERVAL
STATE_OVER_INTERVAL
UNSPECIFIED
```

Do not transform interval totals into fake point samples.

## 5.14 Separate temporal granularity from acquisition

Do not encode `MANUAL_ESTIMATE` as though it were a temporal form.

Per metric/trace preserve independent dimensions:

```text
EvidenceGranularity
- TRACE
- INTERVAL
- SUMMARY

AcquisitionMethod
- SENSOR_RECORDED
- DEVICE_DERIVED
- AUTOMATICALLY_INFERRED
- USER_REPORTED
- USER_ESTIMATE
- UNKNOWN
```

This permits one observation to mix imported traces and manual machine context without invalidating either.

## 5.15 Semantic role

Temporal evidence must distinguish at least:

```text
PERFORMANCE_OUTPUT
PHYSIOLOGICAL_RESPONSE
MOVEMENT_CONTEXT
ENVIRONMENTAL_CONTEXT
```

Example:

```text
speed(t)      PERFORMANCE_OUTPUT
power(t)      PERFORMANCE_OUTPUT
heartRate(t)  PHYSIOLOGICAL_RESPONSE
grade         MOVEMENT_CONTEXT
```

Do not allow physiological response to be mistaken for mechanical output merely because both use numeric traces.

## 5.16 Exact bout/set timing

Persist observable start and end bounds, not completion time alone:

```text
startedAt
endedAt
timingQuality / uncertainty
```

Canonical time uses lossless `Instant` semantics (e.g. epoch seconds + nanos or equivalent). Preserve relevant source/user zone offset separately when imported.

Accurate set boundaries are required for:

- actual set duration;
- actual rest duration;
- session elapsed time;
- future HR alignment;
- future pre/post-set physiological features.

If exact physical contraction bounds are unavailable, record the observable app/user/source event and mark timing quality rather than pretending exactness.

## 5.17 Session-scoped physiological traces and links

A continuous session HR trace must be representable once at workout/session scope and linked/aligned to multiple resistance sets without copying samples.

Support trace scope equivalent to:

```text
PERFORMANCE_OBSERVATION
SET_RECORD
SESSION_EXERCISE
WORKOUT_SESSION
```

Provide observation↔trace links where required.

## 5.18 Source-record/chunk provenance

One logical trace may contain several independent source records. Preserve source boundaries.

Generic external provenance must support, where known:

```text
provider
dataOrigin
sourceRecordType
sourceRecordId
clientRecordId
clientRecordVersion
source device manufacturer/model/type
recording method
sourceLastModifiedAt
importedAt
sourceStart / sourceEnd
source revision / supersession
source state
```

No `androidx.health.connect.*` DTO belongs in core N-BIO domain models.

Future Health Connect mapping occurs at an integration boundary.

## 5.19 Imported-source update/deletion semantics

Design source states including:

```text
AVAILABLE
UPDATED_AT_SOURCE
DELETED_AT_SOURCE
PERMISSION_UNAVAILABLE
SOURCE_DISCONNECTED
UNKNOWN
```

Source changes create immutable new Native revisions that supersede old revisions.

Source deletion/disconnection does not silently delete Native history.

Loss of permission is not proof of deletion.

## 5.20 Trace physical storage

Prefer a chunked physical representation rather than one SQLite row per sample for long-lived high-volume traces.

Recommended shape:

```text
evidence_trace
external_evidence_artifact
evidence_trace_chunk
observation_trace_link
```

`evidence_trace_chunk` stores a versioned payload codec and source boundaries. Domain code may still expose individual point/interval samples.

The exact compression/columnar codec is an engineering choice; `encodingVersion` and deterministic round-trip tests are mandatory.

Source-record boundaries define chunks first. Very large source records may be sub-chunked while retaining the same provenance artifact.

## 5.21 Canonical trace versus derived summary/cache

### Canonical

Store what was received:

- exact sample/interval values;
- timestamps;
- source boundaries;
- units/canonical values;
- provenance;
- acquisition/granularity;
- missingness by absence;
- source revisions.

### Derived

Examples:

- time-weighted means;
- sustained maxima;
- integrated work;
- trace QC/outlier flags;
- HR recovery features;
- interval detection;
- route-derived elevation;
- personalised HR zones.

Derived values carry algorithm/version/input fingerprints and are recomputable.

### UI cache

Downsampled graph payloads are disposable. They never replace canonical traces.

## 5.22 Cardio bout semantics

A continuous cardio workout is one canonical performed bout/observation even if an algorithm later detects multiple intervals.

Automatically detected intervals are derived/recomputable segments, not raw sets.

Only intervals explicitly entered/prescribed as distinct bouts become canonical child bouts.

## 5.23 Spatial route design

N-BIO-6 must ensure the temporal abstraction can represent a future spatial route without another ontology rewrite.

Full Health Connect route ingestion/UI may remain N-BIO-9.

Do not assume route altitude equals measured elevation; quality/filtering remains derived.

## 5.24 N-BIO-6 acceptance cases

The domain, Room schema, repositories, prescription path and history/developer surfaces must represent without hacks:

| Case | Required evidence |
|---|---|
| incline press | load + reps |
| dragon flag | reps + body-mass context where useful |
| assisted dip/pull-up | assistance + reps + body mass |
| weighted bodyweight movement | body mass + external load + reps |
| dead hang | duration/context, zero fake reps |
| loaded static grip hold | load + duration + side |
| repeated grip roll | load + cycles/reps (+ cadence/duration if recorded) |
| unilateral row/lunge | side-aware load/reps |
| treadmill summary | speed + grade + duration + distance |
| treadmill trace fixture | speed(t) + duration + optional manual grade |
| StairMaster | machine level + duration + steps/floors; level remains ordinal |
| row/cycle fixture | power(t) + duration + distance/cadence where available |
| resistance session HR fixture | one session-scoped physiological trace linked to several timed sets |
| interval evidence | distance/steps over explicit bounded intervals |
| external revision | source update creates superseding immutable revision |

End-to-end acceptance additionally proves:

1. duration-only routine→selection→prescription→recording works without fake reps;
2. load-duration unilateral evidence remains separate left/right;
3. assisted difficulty direction is correct;
4. uncalibrated machine levels remain ordinal;
5. entered units round-trip while canonical units remain stable;
6. real Lite resistance history imports without factual change;
7. raw temporal samples survive encode→Room→decode exactly;
8. gaps remain gaps and sensor spikes remain present in canonical evidence;
9. one session trace can contextualise several sets without duplicating canonical samples;
10. deleting/rebuilding derived state leaves scalar and temporal raw evidence unchanged;
11. equal scalar averages with different traces remain distinguishable;
12. automatically detected intervals are never persisted as canonical truth by default.

N-BIO-6 must not be frozen until these semantic requirements are satisfied. Device-only Health Connect ingestion itself is not required for N-BIO-6 completion.

---

# 6. N-BIO-7 — Adaptive Biological Inference

## Goal

Replace conservative biological placeholders with a versioned probabilistic inference system that learns profile-specific demonstrated capability quickly, preserves unknown effort, models muscle exposure/dose separately from fatigue, updates general muscle development slowly, and treats systemic/conditioning evidence as separate state families.

Do not implement every equation simultaneously. Keep the current same-profile anchor and conservative exposure model as benchmarks.

## 6.1 Engine boundaries

Create/refactor around replaceable engines such as:

```text
engine/performance/
  PerformanceNormalizer
  ResistanceResolver
  CapabilityEstimator / capability-family models

engine/inference/
  SetDemandEstimator
  MuscleExposureEstimator
  EffectiveDoseEstimator
  AcuteStateUpdater
  DevelopmentUpdater
  SkillStateUpdater
  SystemicStateUpdater / contextual interface

engine/translation/
  ExerciseTranslationModel
  ExerciseSimilarityModel
```

Exact package names are implementation details; replaceability/version provenance is required.

## 6.2 Performance normalisation

Input:

- canonical performed metrics;
- execution-profile version;
- body-mass context;
- side;
- equipment/resistance semantics;
- evidence granularity/acquisition where relevant.

Output: profile-specific normalised performance evidence.

Do not calculate hypertrophy here.

## 6.3 Capability state

Replace “latest tuple” as the conceptual endpoint with user-specific capability posteriors.

Support model families for:

- dynamic resistance load↔reps;
- loaded hold load↔duration;
- duration-only/repeated contraction where meaningful;
- conditioning power/speed↔duration.

Do not force e1RM as universal capability.

## 6.4 Unknown submaximality / SetDemand

Ordinary completed sets are lower-bound performance evidence unless stronger information establishes frontier proximity.

Use a versioned probabilistic/frontier model if adopted from research. Do not reconstruct RIR labels.

## 6.5 Cold start and hierarchical evidence

Use a hierarchy such as:

```text
same execution profile
→ related profile/family evidence
→ overlapping muscle/movement evidence
→ reference/population prior
→ unknown / blank
```

Every transferred prediction carries uncertainty. High uncertainty may yield no numerical recommendation.

## 6.6 Muscle exposure and effective dose

Keep distinct:

```text
Exposure_sm
```

from:

```text
EffectiveDose_sm
```

Exposure remains the conservative recruitment-weighted evidence layer.

Effective dose may incorporate inferred demand only where research-backed/model-versioned.

Do not create a universal muscle-dose scalar across different muscles.

## 6.7 Temporal dose / diminishing returns

Implement research-backed/versioned accumulation of recent dose, diminishing marginal contribution and decay only after model decisions are locked.

Exponential kernels are modelling choices, not literal biological kinetics.

## 6.8 Local fatigue and recovery

Model recent dose, fatigue and recovery separately from long-term development.

`Recovery` is a bounded readiness state/posterior, not `% tissue repaired`.

Avoid fixed 24/48/72-hour clocks.

## 6.9 SkillState versus Development

Rapid exercise-specific improvement may update profile skill/capability quickly.

`Development_m` updates slowly and should require broader/persistent evidence.

One unfamiliar exercise's rapid progression must not be interpreted as equivalent muscle growth.

## 6.10 SystemicAcuteState architecture

Add a separate systemic-state boundary, but keep its behaviour conservative until validated.

The intended decomposition allows:

```text
systemic strain/state
→ future available capability / cost
```

without retroactively rewriting local stimulus from completed work.

Heart-rate-derived features are not automatically model inputs merely because trace storage exists.

## 6.11 Conditioning capability

Conditioning is modality-specific.

Candidate families:

- running/treadmill: speed-duration / critical-speed family;
- cycling: calibrated power-duration / CP/W′ family;
- rowing: erg-specific power/pace-duration;
- stairs/ordinal machines: device/profile-local sustained capability when physical calibration is absent.

When temporal traces exist, use intensity distribution/sustained efforts rather than flattening to averages. When only summaries exist, model lower-information evidence explicitly.

Do not infer watts from HR alone.

## 6.12 Cross-profile translation

Prediction input may include:

- capability states;
- target profile/recruitment;
- similarity features;
- body mass;
- related history;
- equipment mechanics;
- uncertainty.

Never use a fixed generic `newLoad = oldLoad × constant` as the universal model.

## 6.13 Incremental inference and full replay

Keep full raw-history recomputation authoritative.

Add incremental updates after completed evidence and regression-test:

```text
incremental(all evidence)
≈ full replay(all evidence)
```

within defined tolerances.

## 6.14 Inference provenance

Each run identifies versions for all behaviour-driving components, including:

```text
reference model
performance normalisation
resistance model
capability model
recruitment model
set-demand model
exposure/effective-dose model
fatigue/recovery model
development model
skill model
conditioning model
systemic-context model if used
translation model
```

## 6.15 HR/systemic predictive research gate

Do not make resistance HR prescription-driving by default.

Future validation compares:

```text
A: performance history
B: A + timing/rest
C: B + HR features
```

using chronologically held-out sessions.

Candidate HR features include pre-set HR, rise, sustained peak, recovery slope, pre-next-set HR and observed-minus-expected residual.

Only if C repeatedly improves held-out prediction over B may HR-derived terms graduate from contextual/experimental evidence into behaviour-driving models.

No rule such as `HR < X = ready` may be introduced without personal validation.

---

# 7. N-BIO-8 — Adaptive Programme Resolution

## Goal

Connect N-BIO state to programme intent so exercise selection/set allocation/prescription become state-aware rather than primarily recruitment/set-budget driven.

## 7.1 Preserve exercise-independent TrainingTarget

Targets remain segment/priority/dose intent, not exercise recipes.

## 7.2 Define real target-dose semantics

Once N-BIO-7 has a defensible dose representation, define precisely:

- unit/model version;
- time horizon;
- target/range/soft-objective semantics;
- how recent state converts programme intent into current need.

## 7.3 Resolve target need

Conceptually:

```text
programme target
+ current muscle state
+ recent dose
+ recovery
+ uncertainty
+ session constraints
= resolved target need
```

## 7.4 Candidate evaluation

Eventually consider:

- predicted target benefit;
- secondary exposure;
- fatigue/time/setup cost;
- equipment availability;
- exercise preference;
- capability/translation confidence;
- recent exposure;
- validated systemic-cost evidence if it exists.

Keep scoring inspectable in developer diagnostics.

## 7.5 Marginal set allocation

Allocate additional work according to marginal target benefit versus fatigue/time cost. Stop when target need is satisfied, marginal value falls too low, or session constraints are exhausted.

## 7.6 Capability-based metric prescriptions

Generate targets appropriate to each performance schema:

- load + reps;
- duration;
- load + duration;
- speed/grade/duration;
- power/duration;
- device-local ordinal targets where warranted.

Unknown/high-uncertainty targets may remain blank.

## 7.7 Target-aware substitution

Preserve biological intent on swap. Never copy outgoing load blindly.

Use destination direct history first, then calibrated/user-specific transfer when defensible, else conservative/null target.

## 7.8 Dynamic `&`

Resolve optional work from under-served programme targets after actual completed dose/state rather than a fixed exercise recipe.

## 7.9 Workout modes remain whole-session constraints

Modes may change time, dose/set budget, exercise count, target priority floor and fatigue allowance. They are not separate hardcoded exercise plans.

---

# 8. N-BIO-9 — Context, Health, Intelligence & Data

## Goal

Implement auxiliary evidence/interpretation systems after the canonical substrate and core inference are stable.

## 8A. Natural-language exercise notes

Replace mandatory rating questionnaires with optional free text.

Raw note is canonical.

Derived/versioned annotation may include bounded tags and next-session cues.

Create a `NoteInterpreter` abstraction with runtime Nano support/fallback. Annotation failure must never block workout completion.

Health/physiological data is not an allowed note-interpreter input.

## 8B. Health Connect integration

Implement a dedicated integration boundary that maps platform DTOs into the generic N-BIO-6 temporal/evidence substrate.

Evolve existing health persistence placeholders rather than creating a competing generic health datastore.

Workout import flow:

```text
user chooses recent compatible ExerciseSessionRecord
→ snapshot session provenance
→ fetch route by session ID where available
→ query granular evidence by interval
→ partition by origin/device/type
→ prefer coherent same-origin stream
→ do not silently fuse unrelated origins
→ preserve source chunks
→ map to generic Native evidence
```

Same `DataOrigin` is a strong default association, not an absolute semantic foreign key. Cross-origin overlap may be valid but requires stronger matching/user confirmation.

## 8C. Samsung / Galaxy Watch product flow

Expected ecosystem:

```text
Galaxy Watch
→ Samsung Health
→ Health Connect
→ My Mettle
```

Do not assume fixed sync latency or guaranteed cadence/sample density.

Missing workout UX:

```text
[ Check again ]
[ Open Samsung Health ]
[ Enter manually ]
```

No false countdown or guaranteed-sync promise.

## 8D. Cardio import UI

For conditioning slots, support:

```text
[ Import from Health ]
[ Enter manually ]
```

Imported observation may show compact trace + summary + provenance badge.

Manual machine context (e.g. treadmill grade / stepmill level) may coexist with imported sensor evidence using separate acquisition/granularity metadata.

Do not split detected intervals into raw sets automatically.

## 8E. Health source update/deletion sync

Use platform change mechanisms to mark/update source state and create superseding immutable Native revisions.

Disconnecting Health Connect and deleting Native stored evidence are distinct user actions.

## 8F. Device fixture matrix

Before Health ingestion is considered final, empirically test the actual supported Galaxy Watch/Samsung Health stack by workout type:

- which record types are emitted;
- sample density/gaps;
- source origins/devices;
- HR/speed/power availability;
- cadence availability;
- route availability;
- update/deletion behaviour;
- practical sync latency.

Document results as fixtures, not universal Android truths.

## 8G. Experimental HR analysis

Build only after enough longitudinal data exists.

Priority experiment: personal expected-HR residual conditioned on exercise/profile, external performance, timing/rest and session position.

Do not turn raw BPM into diagnosis.

Potential future consumers:

- conditioning context;
- systemic anomaly/strain context;
- exercise-specific systemic-cost research;
- next-set/later-session performance prediction.

Prescription-driving use requires held-out incremental validation.

## 8H. Cardio→local-muscle context

Store the evidence required to study local endurance/fatigue exposure, but do not convert cardio time into hypertrophy sets.

Any future local-muscle model remains modality/anatomy-specific and uncertainty-aware.

## 8I. Exports

Maintain three products:

### Full Backup

Restoration contract. Includes all canonical scalar evidence, temporal chunks, external provenance/source state, profile/recruitment versions, notes and other authoritative user data.

### Analysis Export

Human/LLM-friendly longitudinal analysis. Include summaries, model provenance, uncertainty and enough trace metadata/optional trace payload to investigate performance without requiring internal database knowledge.

### Biology Diagnostic

Narrow technical developer output. Do not merge it with full backup.

## 8J. Data controls

Allow independent actions for:

- recomputing/deleting derived biological state;
- deleting AI annotations while retaining raw notes;
- disconnecting Health Connect;
- deleting locally retained imported health evidence;
- deleting workout history/setup media;
- development reset.

---

# 9. Native Cutover

Execute only after N-BIO-6 through N-BIO-9 reach acceptable behaviour for authoritative use.

## 9.1 Freeze first canonical production schema

Select the Room version that becomes the first permanent Native history schema.

## 9.2 Remove destructive migration

Remove `fallbackToDestructiveMigration(true)` from the authoritative database path only now.

Debug-only destructive reset may remain explicitly gated.

## 9.3 Forward migration policy

Every later schema change requires explicit migration + migration test + backup/restore compatibility.

## 9.4 Final Lite→Native translator

Translate factual legacy history against the settled schema.

Never invent newer facts Lite did not record.

## 9.5 Full-history validation

Compare counts/order/values for exercises, routines, sessions, sets, metrics, body mass, notes, setup media and targets. Manually inspect representative historical sessions.

## 9.6 Recompute N-BIO after import

Derived state is regenerated from raw imported evidence; stale derived output is not canonical import truth.

## 9.7 Backup/restore torture test

```text
fresh install
→ import Lite
→ recompute
→ complete representative Native workouts
→ create notes/import traces where applicable
→ full backup
→ wipe
→ restore
→ recompute
→ compare canonical raw state
```

## 9.8 Cutover declaration

Only after acceptance:

```text
Lite Legacy = frozen archive/source
My Mettle Native = authoritative workout application
```

From then on raw history is permanent and schema changes require explicit migrations.

---

# 10. Cross-phase persistence and provenance contract

Every behaviour-driving derived value must identify relevant model/config versions and evidence horizon.

Raw evidence must remain independent of derived state.

Historical execution/recruitment semantics remain reproducible.

External imported evidence retains provider/source/device/artifact identity and source revision/state.

Derived trace summaries retain algorithm/version/input fingerprint.

Correction/supersession chains must remain acyclic and unambiguous.

Deleting derived inference must never cascade into canonical workout/trace evidence.

---

# 11. Validation contract

## 11.1 Performance/unit tests

Cover:

- kg↔lb;
- speed/pace conversions;
- negative grade;
- total/per-hand/per-side;
- assistance direction;
- bodyweight + external;
- unilateral asymmetry;
- load + duration;
- duration-only;
- device ordinal;
- multi-dimensional conditioning.

## 11.2 Historical semantics tests

Verify profile/recruitment successor creation preserves old history and supports deliberate historical/current-model replay modes.

## 11.3 Temporal evidence tests

Cover:

- point series;
- interval totals/states;
- irregular timestamps;
- gaps;
- raw sensor spikes;
- exact/approx timing;
- session-scoped trace links;
- source chunk boundaries;
- codec round trip;
- superseding external revisions;
- source deletion state;
- derived-state deletion/raw preservation;
- equal averages/different trace shapes.

## 11.4 Prescription tests

Duration-only exercises must not require reps. Load-duration/cardio prescriptions support multiple metric targets and per-metric provenance.

## 11.5 Inference tests

Use research-defined vectors for dynamic progression, assistance, loaded holds, asymmetry, cold start, cross-profile transfer, poor sessions, skill learning, stable performance and detraining/decay where modelled.

## 11.6 HR predictive validation

When attempted, use chronological held-out validation and compare performance-only → timing → timing+HR models. Negative results are valid outcomes.

## 11.7 Health Connect tests

Later integration tests cover permission grant/revoke, duplicate avoidance, updates/deletions, origin preservation, disconnect and no damage to Native evidence.

## 11.8 Export tests

Prove independent contracts for full backup, analysis export and biology diagnostic.

---

# 12. Expected package/file work

Names may vary if current architecture has cleaner equivalents. Avoid parallel duplicate domains.

Likely areas:

```text
domain/performance/
  performance metrics/schemas/units/resistance/laterality
  temporal evidence types

 data/local/entity/
  execution/recruitment versions
  generic observations/metric values
  trace/artifact/chunk/link entities
  derived inference entities

 data/local/dao/
  observation/trace/provenance/inference queries

 data/migration/
  Lite translator compatibility

 engine/performance/
 engine/inference/
 engine/translation/
 engine/targeting/

 health/
  Health Connect integration boundary (N-BIO-9)
```

Before creating any new file/package, inspect current source and reuse/evolve existing models where appropriate.

---

# 13. Explicitly deferred / do not implement

Until specifically promoted by validated work, do not implement as behaviour-driving truth:

- universal hypertrophy/muscle-load formula;
- deterministic RIR reconstruction;
- fixed effective-reps model;
- linear TUT stimulus multiplier;
- cardio→hypertrophy-set conversion;
- universal cardio score;
- direct HR→hypertrophy multiplier;
- deterministic HR→local-fatigue rule;
- fixed HR readiness/rest threshold;
- average HR as universal training load;
- calories as universal dose;
- arbitrary cross-machine kg/level equivalence;
- reference PCSA→user load conversion;
- fixed 24/48/72-hour recovery clocks;
- confident muscle Development from one exercise's rapid progress;
- destructive smoothing/interpolation of raw traces;
- automatic interval segmentation as raw truth;
- arbitrary mixing of overlapping Health Connect origins;
- Gemini/Nano biological inference.

---

# 14. Agent execution protocol

1. Inspect current `main` and working branch before relying on documented file names/status.
2. Read this plan first.
3. Read only relevant research-guide sections, then targeted raw research if necessary.
4. Treat current source code as truth about what exists; reconcile material contradictions explicitly.
5. Implement phases in a small number of coherent commits, not dozens of artificial milestone labels.
6. Keep raw evidence and derived state separate.
7. Prefer reversible design and explicit unknowns.
8. Never claim a test passed unless it ran.
9. Run real Gradle/Android/Room checks when environment permits; lightweight harnesses do not substitute for project compilation.
10. Keep destructive migration during pre-cutover schema work.
11. Do not begin the next major phase while known structural contradictions remain in the current foundation.
12. Update PLAN/research guides only when architecture/research interpretation genuinely changes.
13. Do not silently reinterpret modelling proposals as scientific constants.
14. Push working branches; do not merge `main` without explicit user approval unless the user directly requests the merge/write.

---

# 15. Global definition of done

## N-BIO-6 complete when

- metric-general scalar performance/prescriptions work end-to-end;
- units/laterality/resistance semantics are safe;
- execution/recruitment semantics are immutable/versioned;
- Lite development import remains factual/useful;
- temporal point/interval evidence is representable/persistable;
- exact/qualified bout timing exists;
- session-scoped traces and observation links exist;
- acquisition/granularity/semantic role are distinct;
- external provenance/source chunks/revisions are preserved;
- trace codec round-trip and raw immutability tests pass;
- no Health Connect-specific DTO leaks into core domain;
- actual Android/Room build/tests pass where available;
- no known source/Room contradictions remain.

## N-BIO-7 complete when

- profile capability, SetDemand, Exposure, EffectiveDose, local acute state, SkillState, Development and conditioning inference are versioned/recomputable;
- uncertainty is explicit;
- full replay/incremental equivalence is tested;
- cross-profile prediction is conservative/null-capable;
- systemic-context architecture is separated from local muscle state;
- unvalidated HR heuristics do not drive prescriptions.

## N-BIO-8 complete when

- programme target dose has explicit semantics;
- current state resolves remaining need;
- exercise/set allocation is marginal-value/state aware;
- substitutions preserve intent without blind numeric copying;
- `&` resolves from under-served targets;
- uncertainty can suppress numerical prescriptions.

## N-BIO-9 complete when

- note interpretation is bounded/versioned/fallback-safe;
- Health Connect maps through generic evidence with provenance;
- actual Samsung/device fixtures establish real-world behaviour;
- cardio import/manual fallback works;
- external-source update/deletion semantics are safe;
- exports/data controls satisfy their separate contracts;
- experimental HR/systemic features remain gated by validation.

## Cutover complete when

- final authoritative schema is frozen;
- real Lite history is validated;
- N-BIO is recomputed from raw evidence;
- backup/restore torture test passes;
- destructive production migrations are removed;
- Native is explicitly declared authoritative.

## Governing invariant

> **N-BIO may become substantially more intelligent over time, but historical training and physiological evidence must never become less interpretable.**

Store observable evidence richly enough that future models can reinterpret it; keep every derived interpretation versioned, attributable and replaceable.
