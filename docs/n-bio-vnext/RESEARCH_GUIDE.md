# My Mettle Native — N-BIO vNext Research Guide

> **Purpose:** Navigation and critical evaluation of [`RESEARCH_RAW.md`](./RESEARCH_RAW.md) for implementation agents.
>
> This is **not** a summary and is **not** the implementation contract. [`PLAN.md`](./PLAN.md) determines what is to be built. This guide explains where the raw research supports a plan decision, how strongly, which parts are modelling proposals rather than empirical findings, and what implementation mistakes to avoid.

## Contents

1. [How to use the three-document set](#1-how-to-use-the-three-document-set)
2. [Overall evaluation of the research](#2-overall-evaluation-of-the-research)
3. [Core latent architecture](#3-core-latent-architecture)
4. [Uncertainty and posterior representation](#4-uncertainty-and-posterior-representation)
5. [Dynamic load × reps capability](#5-dynamic-load--reps-capability)
6. [Bodyweight, assistance and resistance normalisation](#6-bodyweight-assistance-and-resistance-normalisation)
7. [Set demand, RIR and stimulus inference](#7-set-demand-rir-and-stimulus-inference)
8. [ROM, muscle length, tempo, rest and frequency](#8-rom-muscle-length-tempo-rest-and-frequency)
9. [Isometrics, duration and loaded holds](#9-isometrics-duration-and-loaded-holds)
10. [Conditioning/cardio](#10-conditioningcardio)
11. [Recruitment semantics](#11-recruitment-semantics)
12. [Cross-exercise translation](#12-cross-exercise-translation)
13. [Reference morphology](#13-reference-morphology)
14. [Dose and diminishing returns](#14-dose-and-diminishing-returns)
15. [Fatigue, recovery and temporal state](#15-fatigue-recovery-and-temporal-state)
16. [Long-term development and SkillState](#16-long-term-development-and-skillstate)
17. [Cold start and prescription uncertainty](#17-cold-start-and-prescription-uncertainty)
18. [Validation methodology](#18-validation-methodology)
19. [Health Connect](#19-health-connect)
20. [Gemini Nano / note interpretation](#20-gemini-nano--note-interpretation)
21. [Evidence ledger and explicit rejections](#21-evidence-ledger-and-explicit-rejections)
22. [Important gaps the research does not solve](#22-important-gaps-the-research-does-not-solve)
23. [Quick routing table for agents](#23-quick-routing-table-for-agents)

---

# 1. How to use the three-document set

The documents have deliberately different authority:

```text
PLAN.md
= WHAT My Mettle has decided to build

RESEARCH_RAW.md
= WHAT DeepResearch actually said

RESEARCH_GUIDE.md
= HOW to navigate/evaluate that research in the context of implementation
```

Normal task flow:

```text
read relevant PLAN section
→ read relevant GUIDE section
→ if necessary, search/open matching RAW heading
→ inspect current source code
→ implement + test
```

Do not read all of `RESEARCH_RAW.md` for every task. It is intentionally preserved as the full research record, not optimised agent context.

The raw report has seven primary headings:

1. `Executive recommendation and mathematical architecture`
2. `Performance inference across dynamic resistance, holds and cardio`
3. `Muscle recruitment, cross-exercise translation and morphology`
4. `Dose, fatigue, recovery and long-term development`
5. `Uncertainty, cold start and validation behaviour`
6. `Android Health Connect and Gemini Nano findings for August 2026`
7. `Evidence ledger and relationships not yet fit to implement`

Use those headings as stable search anchors rather than relying on line numbers, which may move if repository headers are added.

---

# 2. Overall evaluation of the research

## Verdict

The research is unusually well aligned with the existing N-BIO architecture and is strong enough to define the **shape** of vNext. Its most valuable contribution is not one equation; it is the insistence that several quantities previously at risk of being conflated must remain separate:

```text
profile capability
profile-specific skill
unknown set demand
muscle exposure
muscle effective dose
recent stimulus
fatigue/recovery
slow muscle development
conditioning capability
```

This directly answers the earlier temptation to “convert every load/unit into one universal muscle unit”: the report argues persuasively that no single scalar can simultaneously mean kilograms, endurance, stimulus, fatigue and development without destroying useful information.

### Strong conclusions worth adopting as architecture

The following are well supported enough that the plan treats them as structural/research-backed:

- performance capability is task/execution specific;
- performance gain is not equivalent to hypertrophy/development;
- a normal completed load × reps set does not identify exact RIR or an RM;
- dynamic resistance, isometric/hold and endurance performance require different capability families;
- cardio should not be converted into resistance-training set units;
- recruitment weights should not be a conserved sum-to-one allocation;
- EMG is supporting evidence, not a direct muscle-force/hypertrophy percentage;
- acute fatigue and long-term development require different states/time scales;
- reference morphology is prior/reference evidence, not measured user anatomy;
- uncertainty should grow across inferential distance;
- blank/null is preferable to an unjustified cold-start prescription;
- Health Connect and Nano must remain separate from immutable Native workout truth.

### Good modelling proposals that are **not** empirical laws

The report proposes several useful computational forms. These should be implemented only behind replaceable versioned interfaces:

- stochastic frontier load–rep model;
- stochastic load–duration frontier;
- bodyweight/external/assistance resistance-coordinate equation;
- probability-of-high-demand statistic from latent submaximality;
- logarithmic/concave session-dose aggregation;
- exponential recent-stimulus/fatigue kernels;
- logistic recovery index;
- shared muscle-development + profile-skill factor decomposition;
- feature/kernel/Bayesian cross-profile translation;
- suggested cross-profile numeric-emission threshold.

The raw report is commendably explicit that these are modelling recommendations or transparent state-estimation kernels. Preserve that wording in model documentation.

## Principal implementation risk

The report's mathematical architecture is more sophisticated than the current app needs to prove immediately. The risk is **complexity theatre**: implementing a large Bayesian/state-space system whose outputs appear scientific but are poorly identifiable from one user's sparse training history.

Mitigation:

1. keep every model replaceable/versioned;
2. preserve the simple current same-profile anchor as a benchmark;
3. validate retrospectively;
4. require calibrated uncertainty;
5. allow broad/null outputs;
6. do not expose `Development` as confident product truth until multi-profile evidence exists.

---

# 3. Core latent architecture

## Where to look

Raw heading: **Executive recommendation and mathematical architecture**.

Search terms:

- `multi-latent state-space model`
- `CapabilityState_e`
- `SkillState_e`
- `SetDemand_s`
- `Exposure_sm`
- `EffectiveDose_sm`
- `RecentStimulus_m`
- `Development_m`
- `ConditioningState_f`

## Research position

The report explicitly rejects a universal “muscle load” scalar and proposes separate latent layers.

The most important distinction is:

```text
CapabilityState_e
≠
Development_m
```

`SkillState_e` exists to absorb rapid execution-specific neural/technical/coordination adaptation so a new exercise improving rapidly does not imply equivalent muscle growth.

## Evidence strength

- Task specificity of strength: **strong**.
- Need to distinguish strength/performance from muscle development: **strong**.
- Exact selected latent-state decomposition: **well-motivated modelling architecture**, not experimentally unique.

## Our evaluation

Adopt the decomposition conceptually. Do not assume every state needs to become a user-facing feature or separately estimated with high precision in v1.

The separation itself is more important than the exact inference algorithm.

## Implementation consequence

See `PLAN.md` §§4 and 6.

Do not allow one generic `muscleScore` / `headPoints` / `universalLoad` replacement to creep back into the domain.

---

# 4. Uncertainty and posterior representation

## Where to look

Raw heading: **Executive recommendation and mathematical architecture**, from `The v1 design principle should be...` through the variable contract.

Also: **Uncertainty, cold start and validation behaviour**.

Search terms:

- `scientific uncertainty`
- `individual posterior uncertainty`
- `p05`
- `effectiveIndependentSessionCount`
- `Student-t`
- `covariance`

## Research position

Two uncertainty types must not be conflated:

1. **Scientific/model uncertainty** — confidence in the relationship/form itself.
2. **Individual posterior uncertainty** — uncertainty about this user's current latent/prediction given an accepted model.

The raw report recommends storing at least p05/p50/p95, posterior variance, counts, evidence dates, provenance and model versions.

It also recommends robust heavy-tailed observation noise so an unusually poor session cannot dominate state updates.

## Evidence strength

- Need for uncertainty / task noise: strong statistical reasoning.
- Specific distribution choices (log-normal, Beta/logit-normal, Student-t): **modelling choices**, although conventional and sensible.

## Our evaluation

This is one of the strongest design improvements over the current `value + confidence` pattern.

However, do not build a general-purpose probabilistic-programming framework into the app unless needed. A compact persisted posterior summary plus model-specific parameter state is sufficient.

A dense covariance matrix across all muscle segments is likely excessive. Preserve correlated uncertainty using hierarchical/shared factors or a documented approximation.

## Implementation traps

- Do not call model confidence `scientific confidence`.
- Do not count five sets in one workout as five independent long-term observations.
- Do not make narrow intervals outside the user's observed load/rep/time domain.

---

# 5. Dynamic load × reps capability

## Where to look

Raw heading: **Performance inference across dynamic resistance, holds and cardio**.

Search terms:

- `execution-profile-specific performance frontier`
- `stochastic frontier model`
- `SubmaximalitySlack`
- `CapabilityDynamic(targetRep)`
- `generic e1RM`

## Research position

A normal set such as `80 kg × 8` proves only that the user completed at least eight reps at 80 kg. Because N-BIO does not know failure/RIR, it must not declare this an 8RM.

The proposed stochastic frontier:

```text
ln(R) = a_e(t) - b_e ln(reps) - u + epsilon
u >= 0
```

allows the observed set to lie below an estimated performance frontier through latent submaximality `u`.

The report recommends predicting capability at the actual intended rep range rather than estimating 1RM and translating back.

## Evidence strength

- Generic e1RM limitations/exercise specificity: moderate/strong evidence.
- Individualised performance relations: moderate/strong.
- Stochastic frontier form itself: **modelling recommendation**.

## Our evaluation

The frontier model is attractive specifically because it matches My Mettle's refusal to require RIR. It should be prototyped against the simple same-profile anchor before becoming canonical.

Do not overfit a separate slope per profile from two nearly identical sessions. Use strong priors/partial pooling and widen uncertainty outside observed rep bands.

## Implementation consequence

Canonical prescription query becomes approximately:

```text
CapabilityDynamic(profile, targetRepRange)
```

not:

```text
calculate e1RM → convert back to rep target
```

---

# 6. Bodyweight, assistance and resistance normalisation

## Where to look

Same raw performance heading.

Search terms:

- `Resistance normalisation must be profile-aware`
- `k_{bw,e}`
- `assistance`
- `PER_HAND`
- `unilateral`
- `ordinal device/profile coordinate`

## Research position

The proposed resistance coordinate is:

```text
R = k_bw,e * bodyMass + k_ext,e * externalLoad - k_assist,e * assistance
```

This is bookkeeping/performance-coordinate modelling, **not muscle force**.

Examples:

- external barbell mass may map directly to profile resistance coordinate;
- assisted movement uses negative assistance sign;
- push-up body-mass fraction is profile-specific and uncertain;
- machine `level 8` remains ordinal if uncalibrated;
- unilateral per-side load must not be doubled merely to mimic bilateral exercise.

## Evidence strength

Semantic direction is strong. Exact `k` coefficients are profile/model priors and may be weakly identified.

## Our evaluation

This strongly supports N-BIO-6's decision to preserve raw body mass, assistance, entry basis, implement count and side separately.

Do not persist only the resolved resistance coordinate. It is derived and model-version-dependent.

## Key test

For a fixed body mass/reps on an assisted profile:

```text
30 kg assistance → 20 kg assistance
```

must always represent increased difficulty/resistance.

---

# 7. Set demand, RIR and stimulus inference

## Where to look

Raw performance section around:

- `Failure should likewise not become a binary requirement`
- `q_s=P(...)`
- `Exposure_{s,m}`
- `EffectiveDose_{s,m}`

## Research position

Do not recreate RIR.

Instead, the learned performance frontier can provide a broad probability that a set was close to the user's demonstrated frontier.

The report distinguishes:

```text
Exposure_sm
= conservative recruitment-weighted set exposure

EffectiveDose_sm
= Exposure adjusted by uncertain demand
```

When demand is poorly identified, preserve Exposure and leave EffectiveDose broad/unresolved.

## Evidence strength

- Proximity to failure matters for fatigue and probably stimulus: moderate.
- Exact hypertrophy mapping from frontier probability: weak.
- `q_s` construction: modelling proposal.

## Our evaluation

The Exposure/EffectiveDose split is excellent and should be adopted even if the first EffectiveDose model remains extremely conservative.

The implementation must resist the urge to produce a single attractive stimulus decimal for every set.

---

# 8. ROM, muscle length, tempo, rest and frequency

## Where to look

Raw performance section after EffectiveDose.

Search:

- `ROM and muscle length`
- `Tempo`
- `Rest intervals`
- `Training frequency`

## Research position

### ROM / muscle length

Treat as execution-profile semantics/features affecting recruitment/similarity, not fixed scalar stimulus multipliers.

### Tempo

Record objectively if available, but do not use time-under-tension linearly as hypertrophy dose.

### Rest

Primarily performance/fatigue context, not a universal direct hypertrophy multiplier.

### Frequency

Primarily temporal distribution of dose; no extra frequency bonus once dose is accounted for.

## Our evaluation

These are mostly **anti-overfitting instructions**. N-BIO should store context that could matter without eagerly turning every variable into a multiplier.

---

# 9. Isometrics, duration and loaded holds

## Where to look

Raw performance heading, search:

- `Isometrics require a different capability family`
- `loaded holds`
- `dead hang`
- `loaded unilateral grip hold`
- `Repeated grip rolls`

## Research position

Seconds are not repetitions.

For loaded holds, the report proposes a monotonic load-duration frontier:

```text
ln(load) = a_e(t) - b_e ln(duration/T0) - u + epsilon
```

Duration-only tasks maintain a separate duration/context capability.

A suitcase/grip hold may recruit both forearm and trunk stabilisers while still having one whole-task performance observation; `20 kg` is not separately `20 kg of forearm force + 20 kg of QL force`.

Repeated grip-roll contractions should be a related but distinct profile/family from a static hold.

## Our evaluation

This is directly relevant to the exercise case that exposed the old schema limitation. Treat it as a mandatory N-BIO-6/7 acceptance path, not an edge case.

---

# 10. Conditioning/cardio

## Where to look

Raw performance heading from `Cardiorespiratory/conditioning exercises require a parallel adaptation model` through the exercise-family table.

Search:

- `critical-power`
- `generic performance-observation substrate`
- `no hypertrophy-set conversion`
- `StairMaster`

## Research position

Use shared raw performance infrastructure but parallel capability/adaptation models.

Suitable modalities may use critical-power or critical-speed style models.

Do not convert:

```text
minutes / watts / kilometres / steps / calories
```

into skeletal-muscle hypertrophy-set units.

Uncalibrated machine levels remain profile/device-local.

## Evidence strength

Critical power/speed: strong within appropriate modalities.

Exact local-muscle fatigue contribution from cardio: much less certain.

## Our evaluation

Implement conditioning capability independently first. Allow contextual fatigue integration later only where evidence/user history justifies it.

Avoid letting “support cardio” balloon N-BIO-7 into a full sports-science platform before resistance-training behaviour is validated.

---

# 11. Recruitment semantics

## Where to look

Raw heading: **Muscle recruitment, cross-exercise translation and morphology**.

Search:

- `weighting is an independent`
- `weights should not sum to one`
- `STABILISER × 0.25`
- `EMG`
- `authoring pipeline`

## Research position

`RecruitmentAllocation.weighting` should mean:

> independent relative local loading/exposure for a specific muscle segment under a specific execution profile.

`1.0` means high/direct exposure for that segment under a reference execution.

It is not:

- fraction of external load;
- EMG percentage;
- conserved pie;
- comparable absolute force across muscles.

Weights do not sum to 1.

Role remains separate and does not get an automatic fixed multiplier.

## Evidence strength

The rejection of EMG-as-force and sum-to-one is strong reasoning grounded in biomechanics. The exact `[0,1]` normalisation convention is a modelling/authoring convention.

## Our evaluation

This should become an explicit repository contract because otherwise later data authors/agents may incorrectly normalise recruitment.

Recruitment provenance needs richer execution-condition metadata than today.

---

# 12. Cross-exercise translation

## Where to look

Same raw heading, from `cross-exercise translation problem` through the dead-hang example.

Search:

- `feature vector`
- `cosine similarity`
- `hierarchical capability decomposition`
- `partial pooling`
- `cold start`
- `35%`
- `dead hang`

## Research position

Translation is a hierarchical prediction problem.

Profile feature vector includes muscle exposure, joint mechanics, contraction type, ROM, kinetic chain, bodyweight mechanism, laterality, equipment, resistance curve, grip/support, entry basis and measurement family.

The research proposes:

- recruitment-vector cosine similarity;
- shared muscle-development factors;
- profile-specific skill/mechanical intercept;
- kernel/hierarchical priors;
- predictive variance that grows when similarity/evidence quality falls.

No kilograms are copied between profiles.

## Evidence strength

- Strength task specificity / need for broad uncertainty: strong.
- Exact hierarchical/kernel equations: reasonable modelling assumptions.
- Suggested 35%/two-increment blank threshold: explicit product-risk proposal, not physiology.

## Our evaluation

This is a good design direction, but implement translation **after** same-profile capability is calibrated and benchmarked.

Do not let cross-profile sophistication contaminate simple same-profile prediction.

The feature schema belongs in N-BIO-6 because it is expensive to reconstruct later.

### Dead hang → loaded grip hold

Raw research explicitly treats a null kg prescription as correct when uncertainty is broad. This is the canonical cross-family test case.

---

# 13. Reference morphology

## Where to look

End of raw recruitment/translation heading.

Search:

- `Reference morphology should remain a prior`
- `specific-tension`
- `PCSA`

## Research position

Reference anatomy/morphology may inform broad priors, expected functional roles or recruitment uncertainty.

It must not directly determine:

```text
user predicted kg
development percentage
recovery speed
training dose requirement
learning rate
```

## Our evaluation

This protects the earlier anatomy research from being overused. Keep reference physiology valuable but epistemically separate from user observation.

`VolumeScale` / `StructuralCapacityScale` should remain null/non-driving for v1 unless actual user-specific evidence appears.

---

# 14. Dose and diminishing returns

## Where to look

Raw heading: **Dose, fatigue, recovery and long-term development**.

Search:

- `Exposure → EffectiveDose → SessionDose`
- `concave aggregation`
- `tau`
- `rawDose`

## Research position

Weekly/set volume has meaningful evidence for hypertrophy, but there is no validated universal equation mapping each set/load/rep to exact growth.

The proposed hierarchy is:

```text
Exposure
→ EffectiveDose
→ SessionDose
```

A logarithmic concave session aggregation is proposed to encode diminishing marginal returns without a hard productive-set ceiling.

## Evidence strength

- Direction “more volume generally helps”: strong.
- Exact concave/log equation and `tau`: weak/modelling assumption.

## Our evaluation

Preserve both `rawDose` and transformed dose. Start with global/broadly pooled saturation parameters. Do not learn 164 independent “MRV” constants from sparse data.

The ability to replace the dose transform later is more important than choosing the perfect v1 curve.

---

# 15. Fatigue, recovery and temporal state

## Where to look

Same raw dose heading.

Search:

- `Recent stimulus and fatigue should be distinct`
- `exponential kernels`
- `FatigueImpulse`
- `Recovery_m`
- `future performance residuals`

## Research position

Recent stimulus and transient fatigue require separate temporal memories.

Exponential half-life kernels are proposed as convenient state estimators, not literal biological kinetics.

Recovery is a bounded readiness posterior/index, not physical repair percentage.

Personal fatigue decay should eventually be learnt from repeated future performance residuals.

Health context can explain some residual variation only probabilistically.

## Evidence strength

- Acute fatigue distinct from long detraining: strong.
- Exact exponential/logistic forms and half-lives: weak/modelling assumptions.

## Our evaluation

This is the section most likely to produce misleading UI if terminology is sloppy. Keep internal names precise and prevent “92% recovered muscle” UI unless semantics have been deliberately redesigned.

A single poor session should increase fatigue/noise uncertainty before changing slow development.

---

# 16. Long-term development and SkillState

## Where to look

Later raw dose/development section.

Search:

- `Development should evolve much more slowly`
- `ΔCapability`
- `possible slow state transition`
- `squat, leg press and knee extension`
- `development may exist computationally before`

## Research position

Performance change decomposes conceptually into:

```text
fast profile Skill
+ shared muscle Development
- transient Fatigue
+ noise
```

The report proposes strongly shrinking Development to a neutral prior and only allowing shared multi-profile evidence to move it confidently.

## Evidence strength

- Strength ≠ hypertrophy / task-specific neural learning: strong.
- Exact factor/state-transition model: modelling assumption.

## Our evaluation

This is essential, but also the hardest latent to identify from personal gym history.

Do not force Development to become prescription-driving just because the state exists in Room.

Implementation needs explicit identifiability rules to avoid `SkillState`, profile intercept drift and Development all explaining the same observation arbitrarily.

A practical staged implementation may initially estimate capability + skill/fatigue robustly while leaving Development extremely broad.

---

# 17. Cold start and prescription uncertainty

## Where to look

Raw translation cold-start table and **Uncertainty, cold start and validation behaviour**.

Search:

- `First observation`
- `Two or three independent sessions`
- `New related profile`
- `Long absence`
- `prescription OR null`

## Research position

First observation = strong lower-bound performance evidence, weak slope/submaximality evidence.

Repeated independent sessions gradually personalise the profile.

New related profiles begin with wider hierarchical priors.

A long absence increases capability uncertainty before slow development disappears.

## Our evaluation

Use Bayesian/posterior mechanics rather than hard evidence-count thresholds where possible. The stages are best used for diagnostics/UX policy.

The application must treat null as a first-class prescription outcome rather than an error state.

---

# 18. Validation methodology

## Where to look

End of **Uncertainty, cold start and validation behaviour**.

Search:

- `retrospective prediction`
- `Calibration(p)`
- `90% predictive interval`
- `same-profile anchor`
- `blank appropriately`

## Research position

Do not validate merely by looking at biologically plausible internal numbers.

For historical session `k`, train only through `k-1`, predict `k`, then evaluate:

- native-unit MAE;
- log predictive density;
- interval coverage;
- calibration;
- appropriate blank rate.

Compare against the simple current same-profile anchor.

## Evidence strength

This is standard, strong statistical validation practice.

## Our evaluation

This should become a release gate for N-BIO-7. It is the best defence against building an elaborate model that performs worse than “use the last set”.

Do not combine kg, reps and seconds into one error score.

The research test-vector table should be ported almost directly into automated invariants.

---

# 19. Health Connect

## Where to look

Raw heading: **Android Health Connect and Gemini Nano findings for August 2026**.

Search:

- `1.1.0 as stable`
- `1.2.0-alpha05`
- `PERMISSION_READ_HEALTH_DATA_HISTORY`
- `clientRecordId`
- `Changes tokens`
- `deletion policy`
- `ExerciseSegment`

## Research position at 2026-08-24

The report found stable `connect-client 1.1.0` and alpha `1.2.0-alpha05`, with richer strength-set fields in the alpha surface.

It identifies useful records for workout, body mass, conditioning and context.

Key sync guidance:

- preserve `Metadata.id`, origin, modification metadata;
- use stable `clientRecordId` + monotonic `clientRecordVersion` for Native exports;
- use separate change tokens by independently consumed record family;
- expired tokens require re-read/deduplication;
- cumulative records such as steps should generally use aggregation;
- external deletion must not erase immutable Native workout truth;
- Health Connect deletion of a Native-exported record should be respected rather than silently recreated.

## Evidence/status quality

API findings are authoritative for the **research date only** and inherently time-sensitive.

## Our evaluation

Reverify official Android documentation immediately before implementation.

Stable and alpha functionality must remain feature-gated.

Health data should enter N-BIO only through explicit versioned covariates; do not connect “available data” to “used by recovery” automatically.

Whole-body body composition is not segment morphology.

---

# 20. Gemini Nano / note interpretation

## Where to look

Same Android raw heading.

Search:

- `Gemini Nano / ML Kit`
- `Structured Output`
- `BACKGROUND_USE_BLOCKED`
- `NoteInterpreterInput`
- `LLM self-reported confidence`

## Research position at 2026-08-24

Prompt API/Nano is suitable for a short post-exercise note classifier/extractor.

Structured Output is preferable to regex-parsed prose.

Availability is device/model dependent; inference is foreground-only in the researched API state.

Most important architectural finding:

```text
Health Connect ─X→ NoteInterpreter
full workout evidence ─X→ NoteInterpreter
```

unless an individual field is explicitly allow-listed.

Recommended input is essentially raw note + static exercise/profile display labels.

Gemini output never directly mutates biological state/prescription.

## Our evaluation

The proposed **type/module-level privacy barrier** is stronger and should be adopted. A prompt instruction saying “do not include health data” is insufficient.

LLM “confidence” is not statistically calibrated N-BIO uncertainty and should be ignored for biological inference.

Recheck device/API support at implementation time.

---

# 21. Evidence ledger and explicit rejections

## Where to look

Raw heading: **Evidence ledger and relationships not yet fit to implement**.

This is the fastest section to consult when an agent asks “can I add X multiplier/equation?”.

The ledger explicitly distinguishes evidence quality from computational form.

### Strong / implement direction

- performance and muscle development need separate latents;
- higher-load work has load-specific strength effects;
- broad load ranges can support hypertrophy;
- more weekly sets generally support more hypertrophy;
- isometric load-duration capability needs a distinct model;
- critical power/speed is useful in appropriate endurance modalities;
- acute fatigue and long-term detraining are distinct.

### Reject

- universal muscle-load scalar;
- completed load/reps uniquely identifies RIR;
- universal e1RM accuracy;
- EMG supplies direct recruitment percentage;
- recruitment weights sum to one;
- seconds convert to repetitions;
- cardio converts to hypertrophy sets;
- strength improvement equals hypertrophy;
- reference PCSA predicts this user's kilograms;
- one poor session means permanent regression;
- numeric cold-start load must always be returned.

### Modelling assumptions to validate

- user/profile-specific stochastic frontier;
- exact session-dose concavity;
- exponential temporal kernels;
- multi-profile factor model for Development;
- Bayesian feature-based cross-exercise translation.

## Explicit “do not implement yet” list

The raw report ends with fourteen red lines. They are copied into `PLAN.md §13` and should be treated as an architectural test: if a PR introduces one, it requires an explicit plan/research revision first.

---

# 22. Important gaps the research does not solve

The report is detailed, but it does **not** supply every engineering answer.

## 22.1 Inference algorithm / mobile implementation

It recommends Bayesian/posterior reasoning but does not choose an on-device algorithm/library.

Open engineering choices include:

- analytic/conjugate approximations where possible;
- sequential robust regression;
- extended/unscented Kalman-style state estimation;
- variational approximation;
- lightweight custom optimisation + uncertainty approximation;
- offline/background full replay versus incremental sufficient-statistic updates.

Do not introduce a heavyweight probabilistic framework before benchmarking memory, CPU, reproducibility and testability on Android.

## 22.2 Identifiability constraints

The proposed decomposition contains profile intercept, Skill, shared Development, Fatigue and noise. Without constraints, several can explain the same observation.

The implementation must define priors/process rates/shared structure that make the decomposition identifiable enough to be useful.

This is a modelling-engineering task; the raw research gives the conceptual discipline but not a unique solution.

## 22.3 Recruitment dataset completeness

The report defines semantics for `weighting`, but it does not provide a completed evidence-authored recruitment library for every execution profile.

Existing recruitment data still needs future evidence improvement under the established semantics.

## 22.4 Exact stimulus mapping

The report intentionally does not discover an exact map from `DemandProbability` to hypertrophic dose.

The first `EffectiveDose` model should therefore remain conservative and heavily versioned.

## 22.5 Individual recovery learning

The report proposes learning fatigue decay from future performance residuals, but sparse/noisy history may make muscle-specific personalisation weak for a long time.

Prefer pooled priors + broad uncertainty rather than forcing personal values.

## 22.6 Conditioning-to-local-muscle interaction

Concurrent training can affect fatigue/adaptation, but the report rejects a simple set conversion. Exact local integration remains under-specified.

Conditioning capability should work independently before sophisticated local-muscle interaction is attempted.

## 22.7 Product presentation

The research does not decide what posterior information should be visible in normal UI. Avoid surfacing researchy pseudo-precision just because it exists in developer state.

## 22.8 Health/Nano findings decay over time

These API findings must be revalidated. Do not treat August 2026 API versions/device lists as timeless architecture.

---

# 23. Quick routing table for agents

| Task | Read PLAN | Read RAW heading / search terms |
|---|---|---|
| performance schema / metrics | §5 | Performance inference → `generic performance-observation substrate` |
| kg/lb/bodyweight/assistance | §5.2–5.7 | Performance inference → `Resistance normalisation` |
| unilateral sets | §5.4–5.5 | Performance inference → `unilateral`; validation table |
| execution/recruitment versioning | §5.8–5.10, §10 | Recruitment heading → `weighting`, `feature vector` |
| generic prescription model | §5.11–5.12 | Dose section → final prescription ordering |
| load×rep capability | §6.2 | Performance inference → `stochastic frontier model` |
| loaded holds | §6.3 | Performance inference → `loaded holds` |
| dead hangs | §6.4 | Performance inference → `dead hang` |
| cardio/treadmill/StairMaster | §6.6 | Performance inference → `conditioning exercises` |
| set stimulus | §6.8–6.10 | Performance inference → `EffectiveDose`; Dose heading |
| recovery/fatigue | §6.11–6.12 | Dose heading → `Recent stimulus and fatigue` |
| Development / beginner gains | §6.13 | Dose heading → `Development should evolve`; validation table |
| cross-exercise swaps | §6.15–6.16 | Recruitment heading → `hierarchical prediction`, `cold start` |
| dynamic `&` / programme resolver | §7 | Dose heading → prescription logic; core architecture |
| validation harness | §11 | Uncertainty heading → `retrospective prediction` |
| notes/Nano | §8A | Android heading → `Gemini Nano`, `NoteInterpreterInput` |
| Health Connect | §8B | Android heading → `Health Connect`, `Changes tokens` |
| backup/export | §8C–8D, §10 | Architecture/provenance sections |
| check questionable multiplier | §13 | Evidence ledger + final `Do not implement yet` list |

## Final guidance

The research's most important discipline is epistemic rather than mathematical:

> **Keep strong observations strong, keep weak inferences uncertain, and never improve product confidence by silently collapsing distinct biological/performance questions into one number.**

Agents should prefer an honest broad posterior or `null` over a persuasive-looking number that cannot be reconstructed from evidence.
