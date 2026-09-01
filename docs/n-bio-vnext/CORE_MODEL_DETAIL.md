# N-BIO vNext — Core Model Detailed Contract

> **Status:** normative detailed supplement to [`PLAN.md`](./PLAN.md).
>
> **Purpose:** preserve the explicit N-BIO-7/N-BIO-8 mathematical, behavioural and validation requirements from the audited pre-temporal plan (`da064490f767259c6a249d243b4a2d78a32de9df`) after temporal-evidence research expanded N-BIO-6.
>
> `PLAN.md` remains the overarching authority. Where the newer plan or Health/Temporal guide explicitly changes a boundary — especially temporal evidence, `SystemicAcuteState`, conditioning traces or HR gating — the newer requirement wins. Otherwise this document is normative detail, not historical commentary.
>
> Core biological research: [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) → targeted [`RESEARCH_RAW.md`](./RESEARCH_RAW.md).
>
> Temporal/HR research: [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md) → targeted raw parts indexed by [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md).

## Contents

1. [Implementation sequencing](#1-implementation-sequencing)
2. [Engine boundaries](#2-engine-boundaries)
3. [Dynamic resistance capability](#3-dynamic-resistance-capability)
4. [Loaded-hold capability](#4-loaded-hold-capability)
5. [Duration-only and repeated-contraction capability](#5-duration-only-and-repeated-contraction-capability)
6. [Conditioning capability](#6-conditioning-capability)
7. [SkillState](#7-skillstate)
8. [Set-demand posterior](#8-set-demand-posterior)
9. [Exposure versus EffectiveDose](#9-exposure-versus-effectivedose)
10. [Session-dose aggregation](#10-session-dose-aggregation)
11. [Recent stimulus and fatigue](#11-recent-stimulus-and-fatigue)
12. [Recovery](#12-recovery)
13. [Slow Development](#13-slow-development)
14. [Structural morphology](#14-structural-morphology)
15. [Cross-profile translation](#15-cross-profile-translation)
16. [Translation emission policy](#16-translation-emission-policy)
17. [Full replay and incremental inference](#17-full-replay-and-incremental-inference)
18. [Inference provenance](#18-inference-provenance)
19. [Systemic/HR extension boundary](#19-systemichr-extension-boundary)
20. [N-BIO-8 detailed resolver contract](#20-n-bio-8-detailed-resolver-contract)
21. [Cross-phase provenance constraints](#21-cross-phase-provenance-constraints)
22. [Retrospective validation contract](#22-retrospective-validation-contract)
23. [Core behavioural test vectors](#23-core-behavioural-test-vectors)

---

# 1. Implementation sequencing

N-BIO-7 replaces the conservative v0 placeholders with a versioned probabilistic inference system that:

- learns profile-specific demonstrated capability relatively quickly;
- preserves unknown effort rather than reconstructing RIR;
- separates conservative muscle exposure from uncertain effective dose;
- separates transient fatigue/recovery from long-term development;
- separates fast profile-specific skill from slow shared muscle state;
- performs cross-profile translation hierarchically and uncertainty-aware;
- retains current same-profile behaviour and conservative exposure as benchmarks.

Do **not** implement every equation simultaneously.

Forward model implementation order (with same-profile capability now active in N-BIO-7C):

```text
1. common posterior + model-version infrastructure
2. performance normalisation / resistance coordinate
3. same-profile capability models
4. SetDemand posterior
5. Exposure preservation + EffectiveDose layer
6. session-dose aggregation
7. acute fatigue/recent-stimulus/recovery state
8. SkillState + slow Development
9. cross-profile similarity/translation
10. conditioning capability consumers of temporal evidence
11. only then experimental systemic/HR consumers
```

Each stage must be benchmarkable independently against the simpler predecessor.

---

# 2. Engine boundaries

Maintain replaceable boundaries conceptually equivalent to:

```text
engine/performance/
  PerformanceNormalizer
  ResistanceResolver
  CapabilityEstimator
  DynamicCapabilityModel
  HoldCapabilityModel
  DurationCapabilityModel
  RepeatedContractionCapabilityModel
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
  SystemicStateUpdater          // initially conservative/experimental

engine/translation/
  ExerciseFeatureEncoder
  ExerciseSimilarityModel
  CrossProfileTranslationModel
  TranslationEmissionPolicy
```

Exact package/class names may differ if the current code supports a cleaner arrangement.

Repositories orchestrate persistence. They do not own biological equations.

All behaviour-driving model/config choices are immutable/versioned.

---

# 3. Dynamic resistance capability

**[RESEARCH-BACKED]** Canonical capability is profile-specific demonstrated performance near the observed rep domain, not generic e1RM.

**[MODELLING-ASSUMPTION]** The accepted first candidate family is the research-proposed stochastic frontier behind a versioned interface:

```text
ln(R_s) = a_e(t) - b_e ln(reps_s) - u_s + epsilon_s
u_s >= 0
```

Interpretation:

```text
a_e(t)   time-varying profile capability intercept
b_e      personalised/profile load–rep slope
u_s      latent submaximality slack
epsilon  robust day/performance noise
```

Required behaviour:

- ordinary completed sets are lower-bound evidence, not assumed RM efforts;
- use robust/heavy-tailed observation handling so one poor session cannot catastrophically rewrite state;
- uncertainty grows sharply outside the user's observed rep/load domain;
- direct profile history dominates generic priors as evidence accumulates;
- slope remains broad until several performance zones are observed;
- an e1RM may exist as a secondary diagnostic when evidence supports it, never as canonical capability.

Do not describe the frontier equation as a physiological law. It is a compact monotonic performance model.

---

# 4. Loaded-hold capability

**[RESEARCH-BACKED]** Load-duration/isometric work requires a separate capability family.

**[MODELLING-ASSUMPTION]** A first candidate is a profile-specific monotonic load-duration frontier such as:

```text
ln(load_s) = a_e(t) - b_e ln(duration_s / T0) - u_s + epsilon_s
```

Requirements:

- unilateral holds remain side-resolved;
- direct hold history dominates transferred priors;
- duration/load extrapolation widens uncertainty;
- no universal `kg × seconds` workload scalar;
- dead hang and loaded grip hold may share target/muscle evidence without pretending their raw numeric dimensions are directly convertible.

---

# 5. Duration-only and repeated-contraction capability

## Duration-only

For dead hangs, planks and similar profiles, maintain duration capability under the relevant execution/context.

Body mass may be a profile/context covariate where meaningful. Do not invent hidden kilograms.

## Repeated contraction

A continuous loaded hold and a repeated grip-roll/cycle movement are not the same performance family merely because both involve forearm work.

Use a distinct family when:

```text
load
+ repetitions/cycles
+ optional cadence
+ optional duration
```

are semantically meaningful.

Do not force repeated-contraction evidence into either the dynamic load×rep frontier or static load×duration frontier if the execution semantics differ materially.

---

# 6. Conditioning capability

**[RESEARCH-BACKED]** Conditioning shares the generic evidence substrate, not a universal skeletal-muscle adaptation unit.

Use modality-specific families.

### Running / treadmill

Candidate capability family:

```text
speed-duration / critical-speed family
```

Consume temporal speed distribution when available. Keep grade, treadmill execution and surface/environment context explicit.

Do not use HR alone as external capability.

### Cycling

Where calibrated power exists:

```text
power-duration / critical-power / W′ family
```

Calibrated power outranks indoor machine speed for cross-session capability. Cadence is context, not effort by itself.

### Rowing

Use erg-specific power/pace-duration capability with device/calibration context.

### Stepmill / ordinal cardio machines

If only local machine level exists, capability remains device/profile-local. Do not create SI meaning or cross-machine equivalence.

### Trace versus summary

Temporal traces are capability evidence because equal averages can hide materially different interval/sustained performance.

When traces are unavailable, summary evidence remains valid but lower-information; uncertainty should reflect that difference.

Do not convert cardio minutes, watts, kilometres or calories into hypertrophy-set units.

---

# 7. SkillState

**[RESEARCH-BACKED]** `SkillState_e` is a fast profile-specific latent representing neural/technical/coordination adaptation that must not automatically become muscle Development.

Required behaviour:

- first sessions on an unfamiliar movement may update profile capability/skill substantially;
- shared muscle Development remains strongly shrunk during rapid early profile learning;
- persistent improvement across multiple overlapping execution profiles provides stronger evidence for shared Development than one movement alone;
- SkillState, capability-process drift and observation noise require explicit identifiability constraints so the model does not double-count the same improvement.

Document those identifiability constraints in the model-version specification.

---

# 8. Set-demand posterior

**[RESEARCH-BACKED]** Unknown proximity to the performance frontier is latent, not an RIR label.

**[MODELLING-ASSUMPTION]** A candidate statistic is:

```text
q_s = P(submaximalitySlack <= delta_e | history)
```

where `delta_e` defines a model-specific high-demand band.

Rules:

- `delta_e` is an immutable/versioned hyperparameter, not a biological constant;
- insufficient history produces broad uncertainty;
- do not manufacture an integer RIR;
- do not silently treat every completed working set as maximal;
- SetDemand may inform EffectiveDose only through a versioned model with uncertainty preserved.

---

# 9. Exposure versus EffectiveDose

Keep the conservative evidence layer directly recomputable.

Under the current convention for a valid working set:

```text
Exposure_sm = recruitmentWeight_e,m
```

This remains useful even when demand inference is weak.

`EffectiveDose_sm` is a separate posterior derived only when a model can combine exposure with uncertain demand defensibly.

When demand is uninformative:

```text
retain Exposure
keep EffectiveDose broad / null / unresolved
```

Do not manufacture a precise “stimulus score” for presentation convenience.

The distinction allows future demand science/models to change without losing the original muscle-local exposure projection.

---

# 10. Session-dose aggregation

**[RESEARCH-BACKED]** Raw exposure/dose accumulates; later sets plausibly have diminishing marginal contribution.

**[MODELLING-ASSUMPTION]** The accepted first candidate transform is:

```text
rawDose X = sum(EffectiveDose)
concaveDose = tau * ln(1 + X/tau)
```

Requirements:

- preserve raw and transformed dose;
- `tau` belongs to immutable model/config provenance;
- begin with global/broad pooled priors rather than claiming a known personal per-muscle MRV;
- validate/replace the transform as evidence develops;
- do not present `tau` as a discovered biological threshold.

---

# 11. Recent stimulus and fatigue

**[RESEARCH-BACKED]** Acute performance suppression and longer-term adaptation operate on different timescales.

**[MODELLING-ASSUMPTION]** Transparent exponential state-estimation kernels are accepted first candidates:

```text
RecentStimulus(t) = Σ(SessionDose_j * exp(-ln2 * age_j / hS))
Fatigue(t)        = Σ(FatigueImpulse_j * exp(-ln2 * age_j / hF))
```

Rules:

- these are computational kernels, not literal molecular kinetics;
- `hS` / `hF` are versioned priors/configuration;
- initialise conservatively and pool broadly;
- personalisation should come from repeated future-performance residuals rather than arbitrary muscle clocks;
- no fixed 24/48/72-hour recovery rule;
- a poor performance observation should first increase probability of transient fatigue/noise before implying structural regression.

---

# 12. Recovery

`Recovery_m` is a bounded readiness posterior/index related to acute fatigue/state.

Never describe:

```text
Recovery = 0.82
```

as:

```text
82% physically repaired
```

A fatigue impulse may use dose, uncertain set demand and density/rest context through versioned coefficients.

Later sleep/RHR/HRV/HR evidence may act only as uncertainty-aware covariates if empirically useful. Availability of a health signal never grants causal authority.

The temporal/HR research additionally requires a separate `SystemicAcuteState` boundary rather than forcing systemic cardiovascular context into local muscle Recovery.

---

# 13. Slow Development

**[RESEARCH-BACKED]** `Development_m` changes substantially more slowly than profile capability/SkillState.

**[MODELLING-ASSUMPTION]** Use a strongly regularised slow latent transition behind a versioned model.

Required behavioural constraints:

- prior centred near `1.0` with wide uncertainty;
- small process/update rate initially;
- one exercise's progression cannot drive a large muscle-development jump;
- corroboration across several overlapping profiles improves identifiability;
- transient poor performance raises fatigue/noise probability before structural regression;
- short training absences remove fatigue much faster than they erase Development;
- rapid beginner improvement may be mostly SkillState/profile capability until broader evidence accumulates;
- Development may exist computationally before becoming prescription-driving.

Do not describe Development as measured muscle size.

---

# 14. Structural morphology

**[DEFER]** Keep:

```text
VolumeScale_m
StructuralCapacityScale_m
```

nullable/non-driving unless genuine user-specific segmental morphology evidence exists.

Reference PCSA/fibre architecture may inform broad priors/provenance but must not directly determine:

- user kilograms;
- Development percentage;
- recovery speed;
- dose requirement;
- learning rate.

Whole-body lean mass/body-fat records cannot populate segmental morphology.

---

# 15. Cross-profile translation

**[RESEARCH-BACKED]** Translation is hierarchical and uncertainty-aware, never a fixed kg-ratio table.

Use N-BIO-6 execution-feature vectors and partial pooling.

**[MODELLING-ASSUMPTION]** Recruitment-vector cosine similarity and kernel/hierarchical regression are accepted candidate components, not fixed biological laws.

Prediction variance must widen when:

- recruitment similarity is low;
- mechanical family differs;
- metric/capability family differs;
- equipment is uncalibrated;
- recruitment provenance is weak;
- direct user history is sparse;
- prediction extrapolates beyond the observed performance domain.

Cold-start hierarchy:

```text
same profile history
→ highly similar profile/family evidence
→ broader user feature/hierarchical prior
→ population/reference prior where defensible
→ unknown / blank
```

First direct observations on a new execution profile should update its profile intercept quickly. Slope remains broad until several performance zones exist.

The biological target/intention may transfer even when the numeric prescription remains null.

---

# 16. Translation emission policy

**[PRODUCT-POLICY]** Emit a numerical cross-profile prescription only when predictive uncertainty is practically useful.

The original research proposed a possible initial product gate:

```text
90% predictive interval
narrower than roughly 35% of median
AND
no wider than roughly two equipment increments
```

This is **not physiology** and is not immutable policy. Treat it as a configurable/versioned starting candidate to validate retrospectively/prospectively.

If the uncertainty gate fails:

```text
return null
preserve target/intention
```

Never fabricate a number merely to avoid a blank UI state.

---

# 17. Full replay and incremental inference

Full-history recomputation remains the canonical rebuild path.

Add incremental inference for normal product latency after completed evidence.

Regression invariant:

```text
incremental(all evidence) ≈ fullReplay(all evidence)
```

within explicit model-defined numerical tolerance.

Ordinary navigation must not silently trigger expensive full-history replay.

Deleting/rebuilding derived state must not change raw scalar or temporal evidence.

---

# 18. Inference provenance

Each inference run references immutable versions/configuration sufficient to reproduce its behaviour, including at least:

```text
referenceModelVersion
performanceNormalisationVersion
resistanceModelVersion
capabilityModelVersion(s)
recruitmentModelVersion
setDemandModelVersion
stimulus/exposureModelVersion
effectiveDoseModelVersion
sessionDoseModelVersion
fatigueModelVersion
recoveryModelVersion
developmentModelVersion
skillModelVersion
conditioningModelVersion
systemicContextModelVersion   // only when actually consumed
translationModelVersion
```

Hyperparameters required to reproduce a run must be referenced by immutable model/config identity rather than hidden constants.

---

# 19. Systemic/HR extension boundary

The temporal research adds a distinct future state boundary:

```text
SystemicAcuteState
```

Conceptual behaviour may allow:

```text
systemic strain/state
→ reduced future available capability / increased cost
```

without requiring:

```text
systemic strain
→ retroactively less local stimulus from a completed set
```

Resistance HR is stored/aligned now but does **not** automatically enter N-BIO-7 equations.

Highest-priority later experiment:

```text
Model A = performance history
Model B = A + set/rest timing
Model C = B + HR features
```

Use chronological held-out validation. HR becomes behaviour-driving only if C repeatedly improves prediction/calibration over B.

Candidate future personal model:

```text
ExpectedHRFeature(
  user,
  exerciseProfile,
  externalPerformance,
  setDuration,
  restDuration,
  setOrdinal,
  sessionElapsedTime,
  recentWork
)

HRResidual = observed - expected
```

Residual is initially an anomaly/context observation, not a diagnosis.

Do not implement fixed BPM readiness thresholds, HR→hypertrophy multipliers or deterministic HR→local-fatigue rules.

---

# 20. N-BIO-8 detailed resolver contract

## 20.1 Preserve exercise-independent target intent

`TrainingTarget` remains conceptually:

```text
segmentId
priority
desiredStimulus
source
```

Exercise pins/routine slots are preferences, not biological truth.

## 20.2 Define `desiredStimulus` precisely

Once N-BIO-7 dose semantics exist, document the exact muscle-local/model-version-local meaning and time horizon.

Do not present it as a universal physical quantity.

## 20.3 Resolve remaining target need

```text
programme target
+ recent stimulus/dose
+ fatigue/recovery
+ current session constraints
+ long-term Development only when sufficiently identified
= resolved target need
```

Developer diagnostics should expose inclusion, priority, remaining dose and uncertainty.

## 20.4 Candidate scoring

Consider explicitly:

```text
expected target exposure/dose
secondary exposure
dose already accumulated
marginal dose value
local fatigue/recovery state
validated systemic cost only when available
time cost
setup/equipment cost
user preference / pinned constraints
execution confidence
translation/prescription uncertainty
recent execution exposure
```

Keep scoring inspectable. Do not hide it behind opaque “AI”.

## 20.5 Marginal set allocation

Allocate additional sets while expected marginal benefit remains worthwhile and constraints permit.

Stop when:

- target sufficiently satisfied;
- marginal value falls materially;
- fatigue/time/systemic cost dominates according to validated model;
- session budget exhausted.

Dose curve remains model-version-dependent.

## 20.6 Prescription ordering

Follow:

```text
programme target
→ candidate execution profiles
→ target coverage/recruitment
→ current profile capability posterior
→ current local fatigue/recovery posterior
→ validated systemic state/cost only if available
→ Development only if sufficiently identified
→ predictive performance distribution
→ equipment quantisation
→ uncertainty gate
→ prescription OR null
```

## 20.7 Substitution

Do not swap a session item after performed work exists for it.

Replacement ranking uses:

```text
target fit
expected dose
mechanical/recruitment similarity
fatigue/time
user preference
prediction uncertainty
validated systemic cost if available
```

A substitution can be biologically valid while its numeric prescription is blank.

## 20.8 Dynamic `&`

```text
cycle biological targets
- actual completed dose
= remaining under-served targets
```

Construct the optional session from outstanding need under constraints/preferences rather than a fixed recipe.

## 20.9 Workout modes

Modes modify whole-session budgets, target priority, time and fatigue allowances. They do not become separate hardcoded exercise plans.

---

# 21. Cross-phase provenance constraints

## Raw versus derived

Raw/user-authored/history tables never depend on the current inference formula.

Derived tables reference immutable inference/model versions and may be discarded/rebuilt.

## Historical versus current-model replay

Support explicit modes:

```text
HISTORICAL_SEMANTICS
→ use execution/recruitment definitions active at workout time

CURRENT_MODEL_REINTERPRETATION
→ reuse immutable performance evidence under explicitly selected current semantics/model
```

Do not conflate outputs.

## Model registry

Maintain immutable identities for every behaviour-driving model/config version and important hyperparameters.

## Session correlation

Multiple sets in one session are correlated evidence for long-term latent state.

Track/derive effective independent-session evidence. Five sets in one session are not five independent longitudinal adaptation observations.

## Correlated muscle uncertainty

A compound exercise does not independently prove equal Development changes in every recruited segment.

Use shared/hierarchical latent uncertainty or an explicit documented approximation. A dense full covariance matrix is not mandatory if impractical; the approximation must be versioned and visible.

## Historical prescriptions

Keep distinct:

```text
what was prescribed then
what was performed then
what current N-BIO would recommend/infer now
```

Never overwrite historical prescriptions in place after model changes.

---

# 22. Retrospective validation contract

Retrospective prediction is the primary model test.

For compatible historical session `k`:

```text
train/recompute using evidence <= k-1
→ predict k
→ compare predictive distribution with observed k
```

Compare every added model layer against the current simpler same-profile anchor / predecessor.

Sophistication is justified only when it improves:

- held-out predictive accuracy;
- calibration;
- uncertainty/blank behaviour;
- or another explicitly defined user-relevant validation target.

By measurement family track at least:

```text
MAE in native dimension (kg / reps / seconds / watts / etc.)
log predictive density
credible-interval coverage
calibration error
cold-start “blank appropriately” rate
```

Do not aggregate kilograms and seconds into a fake global error score.

A nominal 90% predictive interval should approach ~90% empirical coverage over enough compatible observations.

For resistance-HR experiments, chronologically split sessions rather than neighbouring sets from the same workout to prevent session-state leakage.

---

# 23. Core behavioural test vectors

Automate at least:

1. **Dynamic progression:** `60×8 → 62.5×8 → 65×8` — capability rises; dose does not simply scale with kg; Development moves slowly.
2. **Assistance:** `30 kg assist×8 → 20 kg assist×8` — lower assistance is harder and capability improves.
3. **Bodyweight repetitions:** push-up `15 → 20` — profile capability rises without equating body mass to bench kilograms.
4. **Body mass context:** body mass rises while reps stay stable — interpretation depends on profile-specific bodyweight contribution.
5. **Duration:** dead hang `45s → 55s` — duration capability rises with no rep/kg conversion.
6. **Loaded hold:** `20 kg×30s → 22.5 kg×30s` — load-duration capability rises; no e1RM/kg×seconds scalar.
7. **Laterality:** left/right hold asymmetry remains separate with only explicit partial pooling.
8. **Cross-profile cold start:** dumbbell press → unfamiliar machine — similarity produces a broad prior, not kg copy.
9. **Different metric family:** dead hang → loaded grip hold — target intent can transfer while numeric load remains null.
10. **Poor session:** one `75×5` after normal `80×5` — robust noise/fatigue first, not permanent regression.
11. **Beginner skill:** `40×8 → 55×8` — large profile skill/capability change; small/broad Development unless corroborated.
12. **High set count:** ten same-muscle sets — raw exposure additive; diminishing marginal session dose only through versioned model/uncertainty.
13. **Short detraining:** three-week absence — acute fatigue disappears; Development does not collapse on a 48–72 h clock.
14. **Equal cardio averages, different traces:** steady versus interval session — conditioning evidence differs when temporal trace exists.
15. **HR anomaly without performance loss:** local capability remains anchored by external performance; systemic/context uncertainty may change experimentally but muscle Development does not regress automatically.
16. **HR positive/negative prediction pair:** elevated HR sometimes precedes later degradation and sometimes does not — prevents a deterministic high-HR=fatigue rule.

Every test must assert both what **should change** and what **must remain unchanged**.