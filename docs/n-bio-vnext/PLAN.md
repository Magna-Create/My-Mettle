# My Mettle Native — N-BIO vNext Execution Plan

> **Authority:** This document is the forward implementation contract for N-BIO vNext.
>
> Read this first. Use [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) to locate and interpret the research behind a decision. Consult [`RESEARCH_RAW.md`](./RESEARCH_RAW.md) only for the relevant source section when deeper evidence/wording is needed.
>
> Existing `docs/N_BIO_*.md` files remain historical implementation-stage documentation. Where they conflict with this document for vNext work, this document governs unless the conflict concerns an already-established canonical anatomy/reference-data invariant.

## Contents

1. [Purpose and completion criterion](#1-purpose-and-completion-criterion)
2. [Decision labels and authority rules](#2-decision-labels-and-authority-rules)
3. [Non-negotiable system invariants](#3-non-negotiable-system-invariants)
4. [Target vNext architecture](#4-target-vnext-architecture)
5. [N-BIO-6 — Generalised Performance & Prescription Foundation](#5-n-bio-6--generalised-performance--prescription-foundation)
6. [N-BIO-7 — Adaptive Biological Inference](#6-n-bio-7--adaptive-biological-inference)
7. [N-BIO-8 — Adaptive Programme Resolution](#7-n-bio-8--adaptive-programme-resolution)
8. [N-BIO-9 — Context, Intelligence & Data](#8-n-bio-9--context-intelligence--data)
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

The current code intentionally uses defensible placeholders: working sets become recruitment-weighted exposure, muscle development remains neutral, recovery/recent stimulus are unset, and performance translation is same-execution-profile only. Preserve the architecture that made those placeholders safely replaceable:

```text
canonical anatomy / reference physiology
                ↓
exercise + versioned execution semantics
                ↓
immutable raw performance evidence
                ↓
versioned/recomputable inference
                ↓
profile capability + biological state
                ↓
exercise-independent programme intent
                ↓
session resolution + prescription
                ↓
immutable historical session snapshot
```

The final cutover criterion is **not** merely that Native can record workouts. Native becomes authoritative only when N-BIO behaves as intended, heterogeneous performance is represented correctly, prescriptions are uncertainty-aware, import/backup/restore are validated, and the final migration policy is locked.

`fallbackToDestructiveMigration(true)` remains acceptable throughout this development stage and is removed **last**, during cutover.

---

# 2. Decision labels and authority rules

Use these labels when adding design notes, PR descriptions or new decisions:

- **[STRUCTURAL]** — architecture/data requirement independent of a specific biological equation.
- **[RESEARCH-BACKED]** — direction/relationship sufficiently supported to encode.
- **[MODELLING-ASSUMPTION]** — useful computational form proposed by the research, not a discovered physiological law; must be versioned and replaceable.
- **[PRODUCT-POLICY]** — UX/risk threshold selected by My Mettle rather than physiology; must be explicit and configurable/versioned where material.
- **[DEFER]** — schema/interface may anticipate it, but do not make it behaviour-driving yet.
- **[DO-NOT-IMPLEMENT]** — explicitly rejected for v1.

Rules:

1. Do not promote a `[MODELLING-ASSUMPTION]` to scientific fact in code comments, UI or developer diagnostics.
2. Do not silently invent constants where research gives only directional evidence.
3. Scientific/model uncertainty and user-specific posterior uncertainty are separate concepts.
4. A precise posterior built on a weak scientific relationship is **not** high-confidence biological truth.
5. Preserve raw/recomputable values so later model versions can replace assumptions.

---

# 3. Non-negotiable system invariants

## 3.1 Raw evidence is canonical

**[STRUCTURAL]** Store what happened before interpreting why it happened.

Raw historical evidence must preserve enough context to answer:

- what exercise and execution-profile version was performed;
- which side / entry basis / implement count applied;
- what physical or ordinal metrics were entered;
- entered units and canonical units;
- equipment identity/context;
- relevant body-mass snapshot where required;
- what was prescribed and why;
- which targets were being resolved;
- what the user actually performed;
- raw user notes.

Derived state may be deleted and rebuilt without changing these records.

## 3.2 Infer the narrowest quantity supported

**[RESEARCH-BACKED]** Evidence strength decreases across inferential boundaries.

A set is strongest evidence about performance on that execution profile, weaker evidence about muscle exposure/stimulus, weaker still about long-term adaptation, and essentially no direct evidence of segmental anatomical volume.

Posterior uncertainty must widen accordingly.

## 3.3 No universal muscle-load scalar

**[RESEARCH-BACKED]** Do not collapse kilograms, repetitions, seconds, conditioning capability, fatigue and development into one conserved universal unit.

Use multiple latents and keep muscle-dose values **muscle-local and model-version-local**. `8` quadriceps dose units are not declared equivalent to `8` forearm dose units.

## 3.4 Capability is not development

**[RESEARCH-BACKED]** Fast task-specific improvement and slow muscle-general adaptation must be separable.

Rapid progression on one unfamiliar exercise should preferentially update execution capability/skill. Persistent improvement across multiple overlapping execution profiles can gradually support a shared muscle-development latent.

## 3.5 Unknown effort remains unknown

**[RESEARCH-BACKED]** Do not restore RIR as a required input or derive an integer RIR from load/reps alone.

A completed `80 kg × 8` observation establishes at least eight demonstrated repetitions at that resistance. It does not establish an 8RM or exact proximity to failure.

## 3.6 Historical semantics are immutable/versioned

**[STRUCTURAL]** Editing an execution profile or recruitment definition must not silently mutate historical meaning.

Historical replay and current-model reinterpretation must be explicit separate modes.

## 3.7 Blank is a valid intelligent output

**[PRODUCT-POLICY]** If predictive uncertainty is too large, return no numerical prescription rather than a fabricated conversion.

## 3.8 AI note interpretation is not N-BIO inference

**[STRUCTURAL]** Gemini Nano/ML Kit may classify allow-listed user-authored notes. It cannot directly mutate workout evidence, recruitment coefficients, biological state or prescriptions.

Health Connect data must be structurally impossible to pass through the note-interpreter input type.

---

# 4. Target vNext architecture

## 4.1 Required latent/state decomposition

Adopt the following conceptual decomposition:

| Layer | State | Required behaviour |
|---|---|---|
| Raw performance | `PerformanceObservation` | immutable facts in native/canonical units |
| Profile capability | `CapabilityState_e` | execution-family-specific demonstrated performance posterior |
| Profile adaptation | `SkillState_e` | fast task-specific neural/technical/coordination latent |
| Set demand | `SetDemand_s` | latent uncertainty about closeness to current performance frontier; not RIR |
| Muscle exposure | `Exposure_sm` | recruitment-weighted conservative set-equivalent exposure |
| Muscle effective dose | `EffectiveDose_sm` | uncertain exposure adjusted by inferred demand |
| Acute muscle state | `RecentStimulus_m`, `Fatigue_m` | separate recent-dose and transient suppression states |
| Readiness | `Recovery_m` | bounded readiness posterior/index; never “% tissue repaired” |
| Long-term muscle state | `Development_m` | slow user-relative latent, prior near `1.0` |
| Structural state | `VolumeScale_m`, `StructuralCapacityScale_m` | nullable; remain non-driving until genuinely observed/inferable |
| Conditioning | `ConditioningState_f` | modality/family-specific power/speed/duration capability |

## 4.2 Required pipeline

```text
IMMUTABLE PERFORMANCE EVIDENCE
        ↓
PERFORMANCE NORMALISATION
        ↓
CAPABILITY FAMILY
  ├─ dynamic load↔rep
  ├─ loaded hold load↔duration
  ├─ duration/context
  └─ conditioning power/speed↔duration
        ↓
SET-DEMAND POSTERIOR
        ↓
EXECUTION RECRUITMENT → MUSCLE EXPOSURE
        ↓
EFFECTIVE DOSE POSTERIOR
        ↓
RECENT STIMULUS + FATIGUE
        ↓
SLOW DEVELOPMENT + FAST SkillState
        ↓
CROSS-PROFILE TRANSLATION
        ↓
SESSION PRESCRIPTION OR NULL
```

All derived nodes must identify the inference/model versions that produced them.

## 4.3 Posterior contract

**[STRUCTURAL]** Replace naked `value + confidence` as the default derived-state representation.

At minimum, important inferred states/predictions must be able to persist:

```text
p05 / credibleLower05
p50 / estimateMedian
p95 / credibleUpper95
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

Use a shared domain type such as `PosteriorSummary<T>` / `EstimateDistribution<T>` rather than duplicating fields across every entity.

Scientific evidence quality remains separate metadata on model/recruitment/provenance records.

---

# 5. N-BIO-6 — Generalised Performance & Prescription Foundation

## Goal

Make Native capable of faithfully representing every reasonable training/conditioning observation and prescription N-BIO vNext may consume, without encoding the final biological equations yet.

This phase should be completed before implementing the research model deeply, because all later inference depends on its raw evidence quality.

## 5.1 Create a generic performance domain

**[STRUCTURAL]** Create `domain/performance` containing at least:

```text
PerformanceMetric.kt
MetricFamily.kt
PerformanceSchema.kt
PerformanceObservation.kt
PerformanceTarget.kt
Quantity.kt
UnitDefinition.kt
UnitConverter.kt
ResistanceModel.kt
Laterality.kt
PosteriorSummary.kt     // if shared inference package is not preferable
```

### Metric families

Define families consumed by different capability engines, e.g.:

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

An execution profile declares a family; raw storage remains generic enough to preserve all metrics.

### Metrics

Support at least:

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

Body mass is context/snapshot rather than pretending it is always an exercise load metric.

Do not create combinatorial enums such as `LOAD_REPS_DURATION`.

## 5.2 Canonical quantities and entered-unit preservation

**[STRUCTURAL]** Persist both user-entered representation and canonical representation.

Recommended canonical units:

```text
mass/load      kg
duration       s
distance       m
speed          m/s
grade          dimensionless fraction
power          W
cadence        events/min
repetitions    integer
steps/floors   integer
```

Example:

```text
enteredValue = 10
enteredUnit  = mph
canonicalValue = 4.4704
canonicalUnit  = m/s
```

All N-BIO engines consume canonical values, but the original input basis remains available for history/export/debugging.

Do not label an uncalibrated machine level as kg. Preserve it as an ordinal device/profile coordinate.

## 5.3 Move measurement semantics to execution profile/version

**[STRUCTURAL]** Generic `Exercise` remains conceptual movement identity.

`ExecutionProfileVersion` owns:

- metric family;
- performance schema;
- equipment identity/type;
- resistance relationship;
- bodyweight contribution semantics/prior;
- assistance semantics;
- entry basis (`TOTAL`, `PER_HAND`, `PER_SIDE`);
- implement count where material;
- allowed values/increments/min/max;
- ROM / technique class where relevant;
- resistance-curve class;
- laterality mode;
- recruitment-profile version;
- effective/superseded metadata.

This allows one conceptual exercise to have multiple semantically distinct machine/free-weight/bodyweight executions.

## 5.4 Generalise raw set storage

**[STRUCTURAL]** Replace fixed performance columns as the long-term contract with:

```text
set_record
    ↓
set_observation
    ↓
set_metric_value
```

### `set_record`

Owns:

```text
id
sessionExerciseId
setIndex
setKind
warmUp
raw note/reference
created/completed metadata
```

### `set_observation`

Owns:

```text
id
setRecordId
ordinal
bodySide
completedAt
source
bodyMassSnapshotKg? / context reference
```

### `set_metric_value`

Owns:

```text
observationId
metric
enteredValue
enteredUnit
canonicalValue
canonicalUnit
```

One set may contain multiple observations (e.g. left/right) and each observation may contain multiple metrics.

## 5.5 Laterality

**[STRUCTURAL]** Support:

```text
LEFT
RIGHT
BILATERAL
ALTERNATING
NOT_APPLICABLE
```

Do not average unilateral asymmetry away at ingestion.

Example:

```text
Grip hold — Set 1
LEFT  → 20 kg, 39 s
RIGHT → 20 kg, 35 s
```

Derived states may partially pool sides later, but raw evidence remains side-addressed.

## 5.6 Resistance normalisation metadata

**[RESEARCH-BACKED]** Preserve the semantic distinction between:

```text
EXTERNAL
ASSISTANCE
BODYWEIGHT
BODYWEIGHT_PLUS_EXTERNAL
NONE / DEVICE_ORDINAL
```

Do not equate these directly to muscle force.

The research-proposed profile resistance coordinate:

```text
R = k_bw * bodyMass + k_ext * externalLoad - k_assist * assistance
```

is a **[MODELLING-ASSUMPTION]** and belongs in a versioned `ResistanceResolver`, not persistence truth.

Hard invariant: with body mass/reps unchanged, decreasing assistance must increase the resolved resistance coordinate for assisted profiles.

## 5.7 Preserve entry basis without destructive “totalisation”

**[STRUCTURAL]** Entry-basis conversion may create bookkeeping values but must not destroy semantic meaning.

- Pair of `20 kg` dumbbells entered `PER_HAND`: total implement mass can be derived, but `20 kg per hand` remains the performed basis.
- Unilateral `20 kg` row: do **not** multiply to `40 kg` merely to resemble a bilateral movement.

Side, implement count and entry basis survive normalisation.

## 5.8 Version execution semantics

**[STRUCTURAL]** Introduce immutable versioning:

```text
exercise
execution_profile
execution_profile_version
```

Once a version has historical evidence, it is immutable.

Editing semantics creates version N+1.

Historical sessions reference the exact execution-profile version used.

## 5.9 Version recruitment semantics

**[STRUCTURAL]** Introduce:

```text
recruitment_profile_version
recruitment_allocation
```

Recruitment allocations must persist:

```text
muscleSegmentId
weighting [0,1]
role
uncertainty / confidence distribution
provenanceType
provenanceReference
applicableRom
applicableTechnique
resistanceCurveClass
modelVersion
```

**[RESEARCH-BACKED]** `weighting` means an independent, muscle-local exposure coefficient. It is:

- not an exercise-load fraction;
- not an EMG percentage;
- not a conserved share;
- not required to sum to 1 across muscles.

`PRIME | SYNERGIST | STABILISER` remains descriptive/provenance-bearing and must not automatically apply a fixed stimulus multiplier.

## 5.10 Add execution-profile similarity features

**[STRUCTURAL]** Persist or deterministically derive features needed later for cross-profile translation:

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

Do not yet hardcode a single similarity score as truth. Translation models consume versioned feature representations.

## 5.11 Generic prescription domain

**[STRUCTURAL]** Replace mandatory `repRange + prescribedLoad` with metric-target prescriptions.

Conceptual structure:

```text
ExercisePrescription
  exerciseId
  executionProfileVersionId
  targetIds[]
  setPrescriptions[]
  restSeconds
  generatedByModelVersion

SetPrescription
  index
  kind
  metricTargets[]

MetricTarget
  metric
  targetKind = EXACT | RANGE | MINIMUM | MAXIMUM | OPEN
  values / canonicalUnit
  evidence
  predictiveDistribution?
```

Examples:

```text
Chest press: 40 kg, 8–10 reps
Dead hang: 35–45 s
Grip hold: 20 kg, 30–40 s per side
Treadmill: 9.5–10 km/h, 3% grade, 20 min
```

## 5.12 Generalise prescription provenance

**[STRUCTURAL]** Replace load-only provenance with per-metric evidence:

```text
PrescriptionEvidence
  source
  sourceObservationId / setId
  inferenceRunId
  anchor / posterior summary
  modelVersion
```

Historical prescriptions remain immutable snapshots after model changes.

## 5.13 Phase 6 acceptance cases

The domain, Room schema, repositories and workout UI must represent without hacks:

| Case | Required evidence |
|---|---|
| incline press | load + reps |
| dragon flag | reps + body-mass context where useful |
| assisted dip/pull-up | assistance + reps + body mass |
| weighted bodyweight movement | body mass + external load + reps |
| dead hang | duration/context |
| loaded static grip hold | load + duration + side |
| repeated grip roll | load + cycles/reps (+ cadence/duration if recorded) |
| unilateral row/lunge | side-aware load/reps |
| treadmill | speed + grade + duration + distance |
| StairMaster | machine level + duration + steps/floors |
| row/cycle erg | power + duration + distance/cadence where available |

No later phase may require a destructive reinterpretation of these raw records.

---

# 6. N-BIO-7 — Adaptive Biological Inference

## Goal

Replace the current v0 biological placeholders with a versioned probabilistic inference system that learns profile-specific demonstrated capability quickly, preserves unknown effort, models muscle exposure/dose separately from fatigue, and updates general muscle development slowly.

## 6.1 Engine boundaries

Create/refactor around replaceable engines:

```text
engine/performance/
  PerformanceNormalizer
  ResistanceResolver
  CapabilityEstimator
  DynamicCapabilityModel
  HoldCapabilityModel
  DurationCapabilityModel
  ConditioningCapabilityModel

engine/stimulus/
  SetDemandEstimator
  ExposureEstimator
  EffectiveDoseEstimator
  SessionDoseAccumulator

engine/inference/
  AcuteStateUpdater
  RecoveryModel
  DevelopmentModel
  SkillStateUpdater

engine/translation/
  ExerciseFeatureEncoder
  ExerciseSimilarityModel
  CrossProfileTranslationModel
  TranslationEmissionPolicy
```

Repositories orchestrate persistence; they do not own biological equations.

## 6.2 Dynamic resistance capability

**[RESEARCH-BACKED]** Canonical capability is profile-specific demonstrated performance near the observed rep domain, not generic e1RM.

**[MODELLING-ASSUMPTION]** Implement the research-proposed stochastic frontier family behind a versioned interface:

```text
ln(R_s) = a_e(t) - b_e ln(reps_s) - u_s + epsilon_s
u_s >= 0
```

Interpretation:

- `a_e(t)` = time-varying profile capability intercept;
- `b_e` = personalised/profile load–rep slope;
- `u_s` = latent submaximality slack;
- `epsilon_s` = robust day/performance noise.

Use robust heavy-tailed observation noise (e.g. Student-t) so one poor session does not catastrophically rewrite state.

Do not treat this equation as a physiological law. It is a compact monotonic performance model over the **observed domain**.

Extrapolation beyond observed rep ranges must widen uncertainty sharply.

An e1RM may exist as a secondary diagnostic if evidence supports it; it is never the canonical capability state.

## 6.3 Loaded-hold capability

**[RESEARCH-BACKED]** Isometric/load-duration performance requires a separate family.

**[MODELLING-ASSUMPTION]** Implement a monotonic profile-specific load-duration frontier, e.g. the research proposal:

```text
ln(load_s) = a_e(t) - b_e ln(duration_s / T0) - u_s + epsilon_s
```

Side-resolve unilateral holds.

Do not use `kg × seconds` as a universal workload scalar.

## 6.4 Duration-only capability

For dead hangs/planks/etc., maintain duration capability under the relevant profile/context.

Body mass may be a profile/context covariate where meaningful, but no hidden kilograms are invented.

## 6.5 Repeated-contraction profiles

A continuous hold and repeated grip-roll/cycle movement may be similar but do not share the same capability equation.

Use a separate measurement family where load + repetitions/cycles + optional cadence/duration are semantically meaningful.

## 6.6 Conditioning capability

**[RESEARCH-BACKED]** Conditioning shares the generic performance substrate but not the skeletal-muscle adaptation unit.

Where appropriate:

- cycling/rowing with power → critical-power / power-duration family;
- running → critical-speed / speed-duration family;
- treadmill grade remains explicit context;
- uncalibrated machine levels remain device/profile-local ordinal coordinates.

Do not convert cardio minutes/watts/km/calories to hypertrophy-set units.

Local skeletal-muscle fatigue/exposure from conditioning remains conservative/uncertain until specifically modelled.

## 6.7 Skill state

**[RESEARCH-BACKED]** Introduce `SkillState_e` as a fast profile-specific latent representing neural/technical/coordination adaptation that should not automatically become muscle development.

First sessions on a new movement may update profile intercept/skill substantially while shared muscle development remains strongly shrunk.

Avoid double-counting: `SkillState`, profile intercept/process drift and noise require explicit identifiability constraints in the model implementation. Document those constraints in the model-version specification.

## 6.8 Set-demand posterior

**[RESEARCH-BACKED]** Unknown proximity to frontier is latent, not RIR.

**[MODELLING-ASSUMPTION]** A statistic such as:

```text
q_s = P(submaximalitySlack <= delta_e | history)
```

may represent probability that a set lies in a model-defined high-demand band.

`delta_e` is a versioned hyperparameter, not a biological constant.

If history is insufficient, preserve broad uncertainty instead of inventing a precise demand score.

## 6.9 Preserve Exposure separately from EffectiveDose

**[RESEARCH-BACKED]** Keep conservative exposure directly recomputable:

```text
Exposure_sm = recruitmentWeight_e,m
```

for a valid working set under the current conservative convention.

Then derive:

```text
EffectiveDose_sm = Exposure_sm × uncertain demand component
```

only when the demand posterior supports doing so.

When demand is uninformative:

- retain `Exposure`;
- keep `EffectiveDose` broad/null/unresolved according to model representation;
- do not manufacture a precise decimal.

## 6.10 Session-dose aggregation

**[RESEARCH-BACKED]** Raw exposure/dose accumulates; later sets plausibly have diminishing marginal benefit.

**[MODELLING-ASSUMPTION]** The research-proposed concave function:

```text
rawDose X = sum(EffectiveDose)
concaveDose = tau * ln(1 + X/tau)
```

is an acceptable v1 candidate only if:

- both raw and transformed dose are preserved;
- `tau` is model-version metadata;
- `tau` begins globally/broadly pooled rather than pretending each muscle has a known personal MRV;
- validation can replace the function later.

## 6.11 Recent stimulus and fatigue are separate states

**[RESEARCH-BACKED]** Acute suppression and long-term adaptation have different time scales.

**[MODELLING-ASSUMPTION]** Exponential kernels may be used as transparent state-estimation kernels:

```text
RecentStimulus(t) = sum(SessionDose_j * exp(-ln2 * age / hS))
Fatigue(t)        = sum(FatigueImpulse_j * exp(-ln2 * age / hF))
```

Do not describe these as literal molecular kinetics.

Initial half-lives should be conservative, pooled/versioned priors. Personalisation should rely on repeated future performance residuals, not arbitrary muscle clocks.

## 6.12 Fatigue impulse and recovery

**[MODELLING-ASSUMPTION]** A fatigue impulse may use dose plus uncertain demand and set density/rest context. Coefficients remain conservative/versioned.

`Recovery_m` may be a bounded readiness posterior/index derived from fatigue state.

Never display/describe `Recovery = 0.82` as “82% physically repaired”.

Health Connect sleep/RHR/HRV, when later available, may only be uncertain covariates explaining residuals; they do not deterministically set muscle recovery.

## 6.13 Slow development state

**[RESEARCH-BACKED]** Development moves much more slowly than execution capability/skill.

**[MODELLING-ASSUMPTION]** Use a strongly regularised slow latent transition such as the research-proposed state-space form only behind a versioned model.

Required behavioural constraints:

- prior centred around `1.0` with wide uncertainty;
- small process/update rate initially;
- one exercise's progression cannot drive a large muscle-development change;
- corroboration across multiple overlapping profiles increases identifiability;
- transient poor performance raises fatigue/noise probability before structural regression;
- short training absences remove fatigue quickly but do not erase development quickly.

Development may exist computationally before it becomes prescription-driving.

## 6.14 Structural morphology states remain non-driving

**[DEFER]** Keep `VolumeScale_m` and `StructuralCapacityScale_m` nullable unless user-specific segmental morphology evidence genuinely exists.

Reference PCSA/fibre architecture may inform broad priors/provenance but must not directly determine:

- user kilograms;
- development percentage;
- recovery speed;
- dose requirement;
- learning rate.

Whole-body Health Connect lean mass/body fat cannot populate segmental morphology.

## 6.15 Cross-profile translation

**[RESEARCH-BACKED]** Translation is hierarchical/uncertainty-aware; never a kg ratio table.

Use profile feature vectors from N-BIO-6 and partial pooling.

**[MODELLING-ASSUMPTION]** Recruitment-vector cosine similarity and kernel/hierarchical regression are accepted candidate components, not fixed biological laws.

Prediction variance must increase when:

- recruitment similarity is low;
- mechanical/metric family differs;
- equipment is uncalibrated;
- recruitment provenance is weak;
- user history is sparse;
- extrapolation leaves the observed domain.

### Cold-start order

```text
same profile history
→ highly similar profile family evidence
→ broader user feature/hierarchical prior
→ population/reference prior where defensible
→ unknown / blank
```

First direct observations on the new profile should update its intercept quickly; slope remains broad until multiple performance zones exist.

## 6.16 Translation emission policy

**[PRODUCT-POLICY]** A numerical cross-profile prescription is emitted only when predictive uncertainty is practically useful.

The research suggests a possible starting rule: 90% predictive interval narrower than both approximately 35% of the median and no more than two equipment increments. Treat this as a configurable/versioned candidate, not a hard scientific constant.

Validate and tune against retrospective/prospective prediction.

Otherwise return `null` and preserve the biological target/intention.

## 6.17 Full replay plus incremental inference

Keep full-history recomputation as canonical rebuild.

Add incremental inference after completed sessions for product latency.

Regression invariant:

```text
incremental(all evidence) ≈ fullReplay(all evidence)
```

within model-defined tolerance.

No ordinary navigation should silently trigger an expensive full-history replay.

## 6.18 Inference provenance

Each run records at least:

```text
referenceModelVersion
performanceNormalisationVersion
resistanceModelVersion
capabilityModelVersion(s)
recruitmentModelVersion
setDemandModelVersion
stimulusModelVersion
sessionDoseModelVersion
fatigueModelVersion
recoveryModelVersion
developmentModelVersion
skillModelVersion
translationModelVersion
```

Model hyperparameters required to reproduce the run must be referenced by immutable model/config version.

---

# 7. N-BIO-8 — Adaptive Programme Resolution

## Goal

Make programme resolution respond to actual inferred need and capability rather than primarily recruitment coverage + static set budgets.

## 7.1 Preserve exercise-independent `TrainingTarget`

Do not put exercise identity back into programme biological intent.

Keep conceptually:

```text
segmentId
priority
desiredStimulus
source
```

## 7.2 Define `desiredStimulus` precisely

**[STRUCTURAL]** Once N-BIO-7 dose semantics exist, document the exact local meaning/time horizon of `desiredStimulus`.

It must be a muscle-local/model-version dose target, not a universal physical quantity.

## 7.3 Resolve remaining target need

Conceptual flow:

```text
programme target
+ recent stimulus/dose
+ fatigue/recovery
+ current session constraints
+ long-term development only when identified enough to matter
= resolved target need
```

`ResolvedTrainingTarget` should expose enough developer data to explain inclusion/priority/remaining dose and uncertainty.

## 7.4 Candidate scoring

Upgrade candidate evaluation to consider:

```text
expected target exposure/dose
secondary exposure
dose already accumulated
marginal dose value
fatigue cost
recovery state
time cost
setup/equipment cost
user preference / pinned constraints
execution confidence
translation/prescription uncertainty
recent execution exposure
```

Keep all candidate scores inspectable in Biological Developer tools.

Do not hide complex scoring behind opaque “AI”.

## 7.5 Marginal set allocation

Allocate additional sets while expected marginal target benefit remains worthwhile and constraints permit.

Stop when:

- target is sufficiently satisfied;
- marginal benefit falls materially;
- fatigue/time cost dominates;
- session budget is exhausted.

The dose curve remains model-version-dependent.

## 7.6 Prescription order

Follow the research-derived ordering:

```text
programme target
→ candidate execution profiles
→ target coverage / recruitment
→ current profile capability posterior
→ current fatigue/recovery posterior
→ development only if sufficiently identified
→ predictive performance distribution
→ equipment quantisation
→ uncertainty gate
→ prescription OR null
```

## 7.7 Exercise substitution

Preserve existing safety/integrity behaviour: do not swap an exercise once performed work has been recorded for that session item.

Replacement ranking should use target fit + expected dose + mechanical/recruitment similarity + fatigue/time + user preference + prediction uncertainty.

A substitution can be valid while its numeric prescription remains blank.

## 7.8 Dynamic `&`

Resolve optional `&` from outstanding cycle target need rather than a permanently fixed recipe:

```text
cycle biological targets
- actual completed dose
= remaining under-served targets
```

Construct the best optional session subject to constraints and user preferences/pins.

## 7.9 Workout modes remain session constraints

Modes continue to modify whole-session budgets/priority/time/fatigue allowances rather than becoming separate fixed exercise plans.

---

# 8. N-BIO-9 — Context, Intelligence & Data

## Goal

Add human qualitative input, on-device note interpretation, Health Connect integration and production-grade backup/analysis surfaces after the core inference/resolver behaviour is functioning.

## 8A. Human notes and Gemini Nano

### 8A.1 Replace mandatory reflection survey

Retire the normal requirement for target-muscle/execution/enjoyment/comfort ratings.

Default UX:

```text
Anything worth noting?
[free text]
```

Structured follow-up may appear only where ambiguity genuinely matters.

### 8A.2 Raw note is canonical

Create `exercise_note` with raw text + revision/timestamps.

AI output is derived metadata and never overwrites the raw note.

### 8A.3 Annotation persistence

Create versioned:

```text
note_annotation_run
note_annotation_tag
next_session_cue
```

Store interpreter provider/model/schema version and note revision hash.

Suggested closed vocabulary may include:

```text
SETUP_ISSUE
TECHNIQUE_ISSUE
TARGET_ENGAGEMENT_CONCERN
LOAD_TOO_LIGHT
LOAD_TOO_HEAVY
DISCOMFORT
PAIN_REPORTED
EQUIPMENT_DIFFERENCE
POSITIVE_SETUP_DISCOVERY
PROGRESSION_OPPORTUNITY
NEXT_SESSION_ACTION
```

Vocabulary is versioned; deterministic app code validates outputs.

### 8A.4 Interpreter abstraction

Create:

```text
NoteInterpreter
  ├─ NanoNoteInterpreter
  ├─ RulesFallbackNoteInterpreter
  └─ NoOpNoteInterpreter
```

The app remains fully functional with no Nano support.

### 8A.5 Structural privacy boundary

`NoteInterpreterInput` may contain only explicitly allow-listed values such as:

```text
noteText
exerciseDisplayName?
executionProfileDisplayName?
```

It must be impossible by type/module dependency to include:

```text
Health Connect records or aggregates
bodyweight
sleep
heart rate / HRV
body composition
full workout history
unrelated notes
```

Prefer a module/package boundary with no dependency on Health Connect DTOs.

### 8A.6 Foreground-only inference

Current ML Kit Prompt API availability/behaviour is time-sensitive. At the researched August 2026 state, Prompt inference is foreground-only and device-dependent.

Interpret the note interactively after submission when available; do not rely on deferred WorkManager/background inference.

Reverify API/device support at implementation time.

### 8A.7 LLM confidence is not N-BIO confidence

Never feed model self-reported confidence into biological posterior confidence.

## 8B. Health Connect

### 8B.1 New subsystem

Create a dedicated integration boundary:

```text
health/
  HealthConnectRepository
  HealthPermissionController
  HealthSyncCoordinator
  HealthRecordMapper
  HealthSummaryEngine
  HealthCapabilities
```

Reverify current SDK surface before implementation. Research snapshot: `connect-client 1.1.0` stable, `1.2.0-alpha05` current alpha on 2026-08-24.

Stable and alpha capabilities must be feature/version-gated.

### 8B.2 Relevant record families

At minimum consider:

```text
ExerciseSessionRecord
PlannedExerciseSessionRecord
WeightRecord
HeightRecord
BodyFatRecord
LeanBodyMassRecord
HeartRateRecord
RestingHeartRateRecord
HeartRateVariabilityRmssdRecord
SleepSessionRecord
StepsRecord
StepsCadenceRecord
DistanceRecord
SpeedRecord
PowerRecord
CyclingPedalingCadenceRecord
ElevationGainedRecord
FloorsClimbedRecord
ActivityIntensityRecord
Vo2MaxRecord
```

Not every available record automatically becomes an N-BIO input.

### 8B.3 Explicit N-BIO covariate declaration

Each model version declares which external health signals it consumes.

Example:

```text
usesBodyMass = true
usesSleep = false
usesRestingHr = false
usesHrv = false
```

Availability does not imply causal use.

### 8B.4 Provenance and mirroring

For external records retain:

```text
recordType
healthConnectId
dataOrigin.packageName
lastModifiedTime
canonical payload/value
importedAt
```

Use separate Changes tokens for independently consumed record types.

On token expiry, re-read + deduplicate; never blindly append.

Use Health Connect aggregation for cumulative data where origins may overlap.

### 8B.5 Idempotent Native export

For My Mettle-originated records:

```text
clientRecordId      = stable Native UUID
clientRecordVersion = monotonic Native export revision
```

Persist returned HC identity/export state.

Do not fabricate optional RPE/RIR fields.

### 8B.6 Deletion semantics

```text
External HC record deleted
→ expire/remove local mirror
→ recompute affected derived context

Native workout exported to HC then deleted in HC
→ NEVER erase Native workout evidence
→ mark HC export deleted/suppressed
→ do not silently recreate against user intent

Workout deleted in My Mettle
→ apply Native deletion semantics
→ delete/suppress corresponding HC export where appropriate
```

## 8C. Backup, analysis export and diagnostics

Maintain three distinct products.

### Full Backup

Purpose: exact restoration.

Include canonical user data, exercise/execution/recruitment versions, programmes, sessions, prescriptions, performance observations, raw notes, annotations, settings, media manifest and necessary provenance.

Derived inference may be included for inspection/cache but is always marked recomputable.

### Analysis Export

Purpose: longitudinal external analysis/ChatGPT/project work.

Include readable:

```text
app/schema/reference versions
exercise + execution semantics
metric schemas
recruitment definitions
programme targets/constraints
session chronology
prescription distributions + evidence
performed observations + units + laterality
body-mass/context provenance
raw notes
AI annotations/reminders
N-BIO outputs + posterior summaries + model versions
explicitly opted-in health summaries
```

Do not require media binaries.

### Biology Diagnostic

Retain the existing privacy-limited technical diagnostic focused on reference/resolver/inference internals.

Do not merge it with personal backup/analysis export.

## 8D. Data controls

Provide independent actions for:

```text
Export full backup
Import backup
Export analysis data
Recompute biological state
Delete derived biological state
Delete AI annotations while retaining raw notes
Delete locally mirrored Health Connect data
Disconnect Health Connect
Delete workout history
Delete setup media
Development reset
```

---

# 9. Native Cutover

## Goal

Make Native authoritative only after N-BIO-6 through N-BIO-9 meet their acceptance criteria.

## 9.1 Keep destructive migration until this phase

Do **not** spend development effort preserving disposable intermediate schemas.

Only now choose the first canonical production Room schema.

## 9.2 Remove destructive production migration last

Remove `fallbackToDestructiveMigration(true)` from the authoritative/release database path only after:

- final schema is selected;
- full backup/restore works;
- Lite import works against that schema;
- N-BIO replay passes;
- real data validation passes.

A debug-only destructive reset may remain.

## 9.3 Forward migration policy

Every subsequent authoritative schema change requires:

```text
explicit Room migration
+ migration test
+ raw-history preservation test
+ backup/restore compatibility test
```

## 9.4 Final Lite → Native translator

Update the existing translation workflow only against the settled schema.

Translate factual legacy data; preserve unknowns as unknown.

Never invent laterality, execution conditions or metrics Lite never recorded.

## 9.5 Recompute from imported raw history

After import:

```text
discard derived state
→ full N-BIO replay
```

Imported stale derived values are not authoritative.

## 9.6 Backup/restore torture test

Required sequence:

```text
fresh install
→ import real Lite backup
→ full N-BIO replay
→ complete representative Native sessions
→ create notes/annotations
→ optional Health Connect sync
→ full backup
→ wipe app
→ restore
→ rebuild derived state
→ compare canonical raw state
```

## 9.7 Final cutover declaration

Only after all gates pass:

```text
Lite Legacy = frozen archive/import source
My Mettle Native = authoritative workout application
```

---

# 10. Cross-phase persistence and provenance contract

## 10.1 Raw versus derived lifecycle

Raw/user-authored/history tables must never depend on the current inference formula.

Derived tables must reference immutable inference/model versions and may be discarded/rebuilt.

## 10.2 Historical versus current-model replay

Support explicit semantics:

```text
HISTORICAL_SEMANTICS
→ use execution/recruitment definition active at workout time

CURRENT_MODEL_REINTERPRETATION
→ reuse immutable performance evidence under explicitly selected modern semantics/model
```

Do not conflate these outputs.

## 10.3 Model registry

Create/maintain an immutable registry/config identity for every behaviour-driving model version and its important hyperparameters.

No inference output should be impossible to trace to its model configuration.

## 10.4 Session independence

Multiple sets in one session are correlated evidence for long-term latent state.

Persist/derive `effectiveIndependentSessionCount`; do not claim five same-session sets equal five independent longitudinal observations.

## 10.5 Correlated muscle uncertainty

A multi-muscle compound exercise does not independently prove equal development changes in every recruited muscle.

Do not blindly update segment development as independent scalars. Use shared/hierarchical latent uncertainty or an explicit approximation that preserves this ambiguity.

A dense full covariance matrix is not mandated if impractical; the approximation and its limitations must be documented/versioned.

---

# 11. Validation contract

## 11.1 Retrospective prediction is the primary model test

For each historical compatible session `k`:

```text
train/recompute using evidence <= k-1
→ predict k
→ compare prediction distribution with observed k
```

Compare vNext against the current simple same-profile anchor. Added sophistication is justified only when it improves held-out predictive accuracy/calibration or meaningfully improves uncertainty/blank behaviour.

## 11.2 Required predictive metrics

By measurement family, track:

```text
MAE in native dimension (kg/reps/s/etc.)
log predictive density
credible-interval coverage
calibration error
cold-start “blank appropriately” rate
```

Do not aggregate kilograms and seconds into one fake global error score.

A nominal 90% interval should approach 90% empirical coverage over sufficient compatible observations.

## 11.3 Core behavioural test vectors

Automate at least:

1. `60×8 → 62.5×8 → 65×8`: capability rises; dose does not rise simply in proportion to kg; development moves slowly.
2. Assisted pull-up `30 kg assist×8 → 20 kg assist×8`: capability improves; lower assistance is harder.
3. Push-up `15 → 20 reps`: profile capability rises without equating body mass to bench kg.
4. Body mass increases while reps are stable: capability effect depends on profile-specific bodyweight contribution.
5. Dead hang `45s → 55s`: duration capability rises; no rep/kg conversion.
6. Grip hold `20 kg×30s → 22.5 kg×30s`: load-duration capability rises; no e1RM.
7. Left/right hold asymmetry remains separate with partial pooling.
8. Dumbbell press → unfamiliar machine: similarity creates a broad prior; no kg copy.
9. Dead hang → loaded grip hold: target intent transfers; numeric load may remain null.
10. One poor `75×5` after normal `80×5`: robust noise/fatigue first, not instant permanent regression.
11. Beginner `40×8 → 55×8`: large profile skill/capability change; small/broad muscle development unless corroborated.
12. Ten same-muscle sets: raw exposure additive; marginal session-dose contribution decreases only through versioned model with uncertainty.
13. Three-week absence: fatigue disappears; development does not collapse on a 48–72 h clock.

## 11.4 Performance-substrate tests

Cover unit conversions, per-hand/per-side semantics, bodyweight/assistance monotonicity, ordinal device metrics, multi-dimensional cardio and round-trip entered-unit preservation.

## 11.5 Historical-version tests

Verify profile/recruitment edits create new versions and old sessions remain exactly interpretable.

## 11.6 Replay tests

Full replay and incremental inference must agree within explicit tolerance.

Deleting derived inference must not alter raw evidence.

## 11.7 Nano privacy tests

At compile/API architecture level, prove Health Connect DTOs cannot enter `NoteInterpreterInput`.

Annotation failure must never block saving a workout/note.

## 11.8 Health Connect tests

Cover permission grant/revoke, history/background feature availability, change-token expiry, duplicate avoidance, external deletion, exported-record deletion and Native-source-of-truth preservation.

## 11.9 Export tests

- Full backup round-trip restores canonical raw state.
- Analysis export contains sufficient semantics/provenance for independent longitudinal analysis.
- Biology diagnostic remains intentionally narrower and excludes personal raw payloads as designed.

---

# 12. Expected package/file work

Exact names may evolve, but preserve domain boundaries.

## Create / expand

```text
domain/performance/
  PerformanceMetric.kt
  MetricFamily.kt
  PerformanceSchema.kt
  PerformanceObservation.kt
  PerformanceTarget.kt
  Quantity.kt
  UnitDefinition.kt
  UnitConverter.kt
  ResistanceModel.kt
  Laterality.kt

domain/inference/
  PosteriorSummary.kt
  CapabilityState.kt
  SkillState.kt
  AcuteMuscleState.kt
  DevelopmentState.kt
  ConditioningState.kt

engine/performance/
  PerformanceNormalizer.kt
  ResistanceResolver.kt
  DynamicCapabilityModel.kt
  HoldCapabilityModel.kt
  DurationCapabilityModel.kt
  ConditioningCapabilityModel.kt

engine/stimulus/
  SetDemandEstimator.kt
  ExposureEstimator.kt
  EffectiveDoseEstimator.kt
  SessionDoseAccumulator.kt

engine/inference/
  AcuteStateUpdater.kt
  RecoveryModel.kt
  DevelopmentModel.kt
  SkillStateUpdater.kt

engine/translation/
  ExerciseFeatureEncoder.kt
  ExerciseSimilarityModel.kt
  CrossProfileTranslationModel.kt
  TranslationEmissionPolicy.kt

notes/
  NoteRepository.kt
  NoteInterpreter.kt
  NanoNoteInterpreter.kt
  NoteAnnotationSchema.kt

health/
  HealthConnectRepository.kt
  HealthPermissionController.kt
  HealthSyncCoordinator.kt
  HealthRecordMapper.kt
  HealthSummaryEngine.kt
  HealthCapabilities.kt

backup/
  NativeBackupRepository.kt
  AnalysisExportRepository.kt
```

## Heavily modify

```text
domain/exercise/ExerciseModels.kt
domain/training/TrainingModels.kt
domain/inference/InferenceModels.kt

data/local/entity/CoreEntities.kt
data/local/entity/BiologyEntities.kt
data/local/entity/InferenceEntities.kt
training entities/DAOs

data/local/MyMettleDatabase.kt
data/local/Migrations.kt
data/local/DatabaseProvider.kt

engine/inference/InferenceEngines.kt
engine/prescription/*
engine/targeting/*

inference/RoomInferenceRepository.kt
workout/RoomWorkoutRepository.kt
library repositories

Workout / Library / History / Settings / Biological Developer UI + ViewModels
```

Do not treat this list as permission to create needless abstractions. Prefer a smaller coherent implementation where boundaries remain explicit.

---

# 13. Explicitly deferred / do not implement

These are v1 red lines derived from the research.

## [DO-NOT-IMPLEMENT] Universal hypertrophy equation

Do not implement `load × reps × sets × recruitment = muscle growth` or equivalent volume-load-to-growth formula.

## [DO-NOT-IMPLEMENT] Deterministic RIR from ordinary set data

Latent submaximality may be inferred probabilistically; exact RIR is not observed.

## [DO-NOT-IMPLEMENT] Fixed effective-reps rule

No universal “last five reps” currency.

## [DO-NOT-IMPLEMENT] Linear time-under-tension stimulus

Tempo may be recorded/contextual; normal TUT is not a linear hypertrophy term.

## [DO-NOT-IMPLEMENT] Additive pump/metabolic-stress/damage currencies

Do not create extra apparent mechanistic precision without predictive evidence.

## [DO-NOT-IMPLEMENT] Role-based recruitment attenuation

No automatic `STABILISER × 0.25`. Weighting already carries expected local exposure.

## [DO-NOT-IMPLEMENT] EMG percentages as recruitment percentages

EMG may support provenance; it does not directly specify force/hypertrophic share.

## [DO-NOT-IMPLEMENT] Reference PCSA → user force/dose

Reference morphology is not measured user anatomy.

## [DO-NOT-IMPLEMENT] Fixed 24/48/72 h recovery clock

Use state estimation with uncertainty; time constants are model priors/learnable parameters.

## [DO-NOT-IMPLEMENT] Cardio → hypertrophy-set conversion

Conditioning shares performance/context infrastructure, not a universal adaptation scalar.

## [DO-NOT-IMPLEMENT] Exact dead-hang ↔ loaded-hold or machine ↔ machine conversion

Similarity informs priors and uncertainty only.

## [DO-NOT-IMPLEMENT] Development from one exercise progression

Shared development must remain strongly shrunk until multiple overlapping evidence sources support it.

## [DO-NOT-IMPLEMENT] Segmental volume from whole-body Health Connect composition

Leave segment morphology null without segmental observation.

## [DO-NOT-IMPLEMENT] Gemini Nano as biological inference

Nano only interprets allow-listed text into constrained metadata/reminder candidates.

## [DEFER] Precision inverse dynamics / muscle-force solving

Do not infer exact muscle force without measured kinematics/external-force geometry.

## [DEFER] User-specific muscle-by-muscle saturation constants

Do not attempt 164 independently learnt MRV-like parameters from sparse personal data.

---

# 14. Agent execution protocol

For every implementation task:

1. Read the relevant section of this `PLAN.md`.
2. Open the corresponding topic in `RESEARCH_GUIDE.md`.
3. Read only the linked/identified section of `RESEARCH_RAW.md` when the scientific/model detail is required.
4. Inspect the current `main` implementation before editing; do not rely on historical docs alone.
5. Identify whether each behaviour is structural, research-backed, modelling assumption or product policy.
6. Keep modelling constants/config versions explicit.
7. Add/modify tests before declaring a phase item complete.
8. Update Biological Developer diagnostics when new state becomes behaviour-driving.
9. Update this plan only when the architectural contract genuinely changes; implementation notes belong in phase-specific docs/PRs.
10. Never silently resolve a research ambiguity in code. Record it as a versioned assumption or leave it unresolved.

For token efficiency, normal agents should not load the full raw research report unless their task requires it.

---

# 15. Global definition of done

N-BIO vNext is ready for Native cutover only when all of the following are true:

- Heterogeneous performance metrics are first-class and unit-safe.
- Laterality, assistance/bodyweight and entry-basis semantics survive raw storage.
- Execution/recruitment semantics are immutable/versioned historically.
- Prescriptions are metric-general and preserve per-metric provenance.
- Dynamic resistance, holds/duration and conditioning use appropriate separate capability families.
- Profile capability/skill changes quickly; shared muscle development changes slowly and remains uncertainty-aware.
- Unknown set effort remains latent rather than fabricated as RIR.
- Exposure, effective dose, recent stimulus and fatigue are separate concepts.
- Recovery is a readiness posterior/index, not a literal tissue-repair percentage.
- Cross-profile translation uses similarity/partial pooling without unit copying and can return null.
- Programme resolution consumes real biological need/capability rather than only static set budgets.
- `&` can resolve from remaining target need.
- Raw notes are canonical; Nano annotations are derived and privacy-isolated.
- Health Connect is provenance-safe, permission-aware and does not overwrite Native truth.
- Full backup, analysis export and biology diagnostic have separate validated contracts.
- Retrospective predictive calibration is at least competitive with the current same-profile baseline.
- Full replay and incremental inference agree within tolerance.
- Real Lite history imports without invented information.
- Backup → wipe → restore → replay preserves canonical history.
- Only then is destructive production migration removed and Native declared authoritative.

## Governing invariant

> **N-BIO may become substantially more intelligent over time, but historical training evidence must never become less interpretable.**

Any value reasonably observable during a workout and potentially important to future modelling should be preserved as raw evidence. Any interpretation of that evidence must be versioned, attributable, uncertainty-aware and replaceable.
