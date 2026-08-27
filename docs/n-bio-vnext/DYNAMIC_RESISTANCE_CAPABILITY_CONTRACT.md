# N-BIO-7B Dynamic Resistance Capability Contract

Status: **7B.1 complete; 7B.2 stochastic-frontier candidate specified / implementation in progress**

This document makes the implementation boundary for N-BIO-7B concrete without replacing the research record in `DeepResearch/` or the normative model detail in `CORE_MODEL_DETAIL.md`.

## Internal 7B checkpoints

N-BIO-7B is intentionally split:

1. **7B.1 — Evidence Eligibility, Resistance Coordinate & Model Contract**
   - strict factual evidence projection;
   - profile-local positive resistance coordinate;
   - centred log-rep representation;
   - reference-rep policy;
   - persistence and validation semantics;
   - no frontier fit.
2. **7B.2 — Stochastic Frontier Inference**
   - fit the dynamic-resistance frontier;
   - infer capability/slack/noise parameters and posterior uncertainty.
3. **7B.3 — Held-Out Validation, Shadow Persistence & Diagnostics**
   - chronological validation;
   - candidate/shadow persistence;
   - calibration and model-comparison diagnostics.
4. **7B.4 — Real-Data / Device Acceptance & 7B Closure**
   - real-history/device acceptance and final promotion decision.

7B.1 stops immediately before mathematical fitting.

## Source and canonical evidence

Canonical performance truth remains N-BIO-6 `PerformanceObservation` + `PerformanceMetricValue` evidence. The 7B projection does not mutate entered values, canonical values, units, execution semantics, warm-up status, or correction history.

The inference DAO already supplies current factual completed observations only:

- owning session must be completed;
- session must not be excluded from insights;
- an observation is omitted when another observation supersedes it.

Draft values are never capability evidence.

A domain-level current-observation selector mirrors the supersession invariant for replay/testing, but the Room query remains the production source boundary.

## Dynamic-resistance eligibility v1

A 7B.1 candidate observation must have all of the following:

- exact target `ExecutionProfileVersionId`;
- exact compatible side/laterality;
- eligible metric family (`DYNAMIC_RESISTANCE` or `BODYWEIGHT_RESISTANCE`);
- a resistance semantic explicitly compatible with that metric family under the versioned evidence policy;
- a current performed observation from a completed insight-eligible session;
- non-empty session identity for longitudinal support accounting;
- positive integral repetitions;
- an explicit resistance semantic mapping that resolves a finite positive physical challenge coordinate;
- canonical physical resistance in kilograms after deterministic metric conversion;
- non-warm-up status.

The first candidate's family/semantics compatibility is deliberately narrow:

- `DYNAMIC_RESISTANCE` -> `EXTERNAL`;
- `BODYWEIGHT_RESISTANCE` -> `ASSISTANCE`, `BODYWEIGHT`, or `BODYWEIGHT_PLUS_EXTERNAL`.

That mapping is encoded directly in `DynamicResistanceEvidencePolicy`, participates in the evidence-policy fingerprint, and is included in immutable model-config payloads. It is therefore behaviour-driving provenance rather than an anonymous engine constant.

The following do **not** enter the first dynamic-resistance frontier:

- duration-only work;
- loaded/static holds;
- repeated-contraction capability;
- power-duration work;
- speed-duration/cardio;
- device ordinal resistance;
- an otherwise eligible family paired with incompatible resistance semantics;
- zero/non-positive repetitions;
- unresolved or non-positive resistance coordinates;
- another execution profile/version;
- another side where capability is side-specific;
- explicitly marked warm-ups.

Those exclusions do not delete or reinterpret canonical history.

## Warm-up policy

`n-bio-7b1-dynamic-resistance-evidence-v1` uses:

`warmUpPolicy = EXCLUDE`

Warm-ups are deliberately submaximal by design and therefore do not behave like ordinary frontier-defining observations in v1. A future model may represent them as weaker lower-bound constraints only under a new versioned policy.

## Corrections and supersession

Corrections are append-only raw evidence:

`A -> corrected by B`

Only `B` is projected into current capability evidence. `A` remains auditable history and is never double-counted.

## Profile/version and side isolation

Dynamic capability is keyed to one immutable execution-profile version and one exact side/laterality state.

There is no 7B cross-profile transfer. Different exercises, execution profiles, profile versions, LEFT/RIGHT observations, and bilateral observations do not silently update one another.

Cross-profile translation remains N-BIO-7F work.

## Profile-local resistance coordinate

The stochastic frontier requires `R > 0` because it models `ln(R)`.

7B.1 therefore defines a strict, derived, profile-local physical challenge coordinate. It is not canonical raw evidence and it is not intended to compare kilograms across unrelated exercises.

The resolver version is:

`n-bio-7b1-profile-local-positive-resistance-v1`

The strict 7B adapter reuses the established N-BIO-6 resistance equation for resistance arithmetic. It adds frontier-specific eligibility around that resolver and **never accepts** a zero/non-positive bookkeeping result merely to make `ln(R)` usable. Unresolvable or non-positive coordinates are excluded; no epsilon clamp or arbitrary offset is introduced.

### Family/resistance-semantics gate

Metric family and resistance semantics are separate canonical facts, so 7B must validate their combination explicitly rather than assuming that either one implies the other.

In v1, an ambiguous combination such as:

`BODYWEIGHT_RESISTANCE + EXTERNAL`

is excluded. Treating its external-load metric as the complete challenge coordinate could silently discard the bodyweight component. A profile that genuinely combines bodyweight and external load must state `BODYWEIGHT_PLUS_EXTERNAL` with explicit coefficients.

Likewise, assistance belongs to a `BODYWEIGHT_RESISTANCE` profile in this candidate rather than being silently accepted under `DYNAMIC_RESISTANCE`.

Changing these compatibility rules is model behaviour and therefore requires changed config/evidence-policy identity.

### External resistance

For a calibrated `DYNAMIC_RESISTANCE + EXTERNAL` profile, the profile's explicit external-load coefficient is applied to the canonical external-load metric. Ordinary profiles normally use coefficient `1.0`.

`EntryBasis` is preserved. A `20 kg PER_HAND` observation remains a profile-local coordinate of `20 kg`; it is not silently totalised to `40 kg`.

### Units

Raw entered units remain unchanged. The model consumes deterministic canonical units. Thus equivalent `kg` and `lb` entries resolve to the same profile-local coordinate within numerical tolerance.

### Assistance

Assistance uses the already-versioned profile coefficients only for an explicitly compatible `BODYWEIGHT_RESISTANCE + ASSISTANCE` profile and only when the execution profile provides a complete deterministic mapping. The coordinate direction is:

more assistance -> lower challenge

less assistance -> higher challenge

No `R = -assistance`, arbitrary offset, or epsilon trick is allowed. Missing body mass, missing assistance, inconsistent coefficients, or a non-positive result makes the observation unresolved for v1.

### Bodyweight and bodyweight + external

Body mass is used only for `BODYWEIGHT_RESISTANCE` when the immutable profile semantics contain an explicit positive bodyweight coefficient. 7B.1 never assumes that every bodyweight exercise moves 100% of body mass.

`BODYWEIGHT_PLUS_EXTERNAL` similarly requires explicit positive bodyweight and external-load coefficients. If those semantics are absent or inconsistent, the observation is excluded rather than assigned hidden kilograms.

The coefficients are a performance-coordinate convention, not a claim about muscle force.

### Device ordinal

`DEVICE_ORDINAL` is never converted to kilograms. A machine level remains a profile-local ordinal variable and belongs to its own capability family.

## Centred log-rep representation

The normative dynamic-resistance model is:

`ln(R_s) = a_e(t) - b_e ln(reps_s) - u_s + epsilon_s`

7B uses the algebraically equivalent centred form:

`x_s = ln(reps_s / r_ref)`

`ln(R_s) = c_e(t) - b_e x_s - u_s + epsilon_s`

`c_e(t)` therefore represents the log frontier resistance near where the user actually trains rather than making `exp(a)` look like a canonical one-repetition maximum.

No `c`, `b`, slack, noise, or posterior fitting occurs in 7B.1.

## Reference-rep policy

The v1 policy is:

`MEDIAN_OBSERVED_LOWER_V1`

Eligible rep counts are sorted and the lower median observed rep count is chosen. This policy is:

- deterministic;
- strictly positive;
- always an actually observed rep count;
- inside the observed rep domain;
- stable for identical evidence/config.

For one set, that set's reps are the reference. For multiple sets at one rep count, the reference remains that count. Wider rep domains remain visible for future slope/uncertainty logic.

Changing this algorithm is behaviour-changing and therefore requires new model/config identity.

## Capability-state semantics

For `DYNAMIC_RESISTANCE`, canonical `CapabilityState` means exactly:

> posterior frontier resistance at the model reference rep count for this execution-profile version and side at the inference horizon

It does **not** mean:

- e1RM;
- generic strength;
- muscle development;
- fatigue;
- recovery;
- load the user is likely to choose.

`CapabilityParameterState` can later encode the reference reps and fitted slope/noise/slack/temporal parameters under a versioned schema.

No e1RM field is introduced by 7B.1.

## Replaceable model interface

`DynamicCapabilityModel` defines the future boundary for:

- `fit(evidence, inference horizon, config)`;
- `predictFrontier(fit, reps)`.

7B.1 supplies **no implementation** and therefore cannot fabricate a stochastic frontier value. 7B.2 supplies the first real implementation behind this interface.

## Context policy

7A.5 `ContextEvidenceView` exists, but the initial dynamic-capability model explicitly encodes:

`consumedContextTags = NONE`

Illness, sleep, fatigue, stress, Nano annotations, UX-only tags, and other context do not change evidence eligibility, weighting, resistance coordinates, or capability mathematics in v1.

A future context-consuming model must use a new immutable behaviour-driving config and pass held-out evaluation.

## Successful sets are lower-bound evidence

A successful `60 kg x 8` observation does **not** establish:

`frontier(8) = 60 kg`

It establishes that approximately this performance was demonstrated successfully. The frontier is an upper performance boundary inferred from many such lower-bound observations under noise/slack assumptions.

Therefore ordinary user-chosen successful loads must not be scored as if they were observed maximum-capability targets. Naive MAE between predicted frontier load and every chosen training load is not a valid capability metric.

## Capability-specific validation prepared for 7B.3

The validation contract is designed to later ask:

- how often a high-confidence frontier falls below a future successful set;
- whether lower-bound exceedance probabilities are calibrated;
- whether uncertainty widens outside the observed rep domain;
- whether later frontier improvements receive plausible probability;
- whether the stochastic frontier beats simpler frontier baselines without pretending chosen training load is a max test.

A model of **load choice** is separate from a model of **capability**.

## Failed/incomplete attempts

Current canonical workout evidence does not reliably distinguish a true physical failure from stopping, skipping, interruption, or an incomplete prescription. 7B v1 therefore consumes no upper-bound failure evidence and never infers failure from absence.

If explicit failure semantics are added later, they may support upper-bound evidence under a new model version.

## Temporal boundary

The model is indexed by inference horizon because demonstrated capability can change over time. 7B.1 encodes only:

`INFERENCE_HORIZON_SNAPSHOT_NO_BIOLOGICAL_DECAY_V1`

No fixed strength-decay half-life, fatigue, recovery, SkillState, or Development process is introduced. Those biological dynamics remain later-phase work.

## Persistence

Room remains **14**.

Existing tables are sufficient:

- `capability_state` for the reference-rep frontier posterior;
- `capability_parameter_state` for later fitted parameter state;
- existing immutable model configs/manifests/runs for provenance.

7B.1 writes no capability posterior and adds no persistence table. Native full-backup behavior therefore remains unchanged.

## Developer diagnostics

`DynamicResistancePreparationDiagnostics` can summarise any selected profile-version/side projection with:

- eligible observation count;
- independent-session count;
- profile/version/side;
- rep domain;
- resistance-coordinate range and canonical unit;
- selected reference reps;
- warm-up/profile/family/laterality/resistance-semantic exclusions;
- optional upstream superseded-row count;
- `context consumption = NONE`;
- `candidate capability posterior = NOT_YET_FIT_7B1`.

It deliberately reports no fabricated capability number.

---

# 7B.2 candidate mathematical specification

## Research-backed boundaries versus modelling assumptions

The following are treated as **[RESEARCH-BACKED]** boundaries from the preserved research record:

- an ordinary completed load×rep set does not identify RM or RIR;
- capability is execution-profile/task specific;
- capability should be queried around the actual rep region rather than routed through generic e1RM;
- uncertainty must widen as inference moves away from direct support;
- robust/heavy-tailed observation handling is desirable because one poor session should not catastrophically rewrite state.

The following are explicitly **[MODELLING-ASSUMPTION]** choices of the first candidate and may be replaced only behind a new immutable config identity:

- the stochastic-frontier likelihood itself;
- Half-Normal slack;
- Student-t symmetric performance noise with fixed degrees of freedom;
- all prior families and numeric hyperparameters;
- the staged parameter-learning thresholds;
- equal-total-weight-per-session dependence approximation;
- the bounded recent-session local-stationarity policy;
- deterministic tensor-grid/midpoint-quadrature posterior approximation;
- the explicit extrapolation-uncertainty increment.

None of those choices is described as a discovered physiological constant.

## Exact v1 equation

For selected evidence at an inference horizon:

```text
y_s = ln(R_s)
x_s = ln(reps_s / r_ref)

y_s = c - b*x_s - u_s + epsilon_s
```

with:

```text
b > 0
u_s >= 0
u_s ~ HalfNormal(sigma_u)
epsilon_s ~ StudentT(df = 5, location = 0, scale = sigma_e)
```

`c` is the log frontier at reference reps. `u_s` is distance below that frontier on the log-performance scale. It is not RIR and is never converted to an integer rep count.

### Why Half-Normal slack

Half-Normal is the first one-sided candidate because it:

- has support only for `u >= 0`;
- has maximum density at zero, so a frontier-near set remains plausible;
- penalises increasingly large slack smoothly without an arbitrary hard maximum;
- is conventional in stochastic-frontier modelling;
- keeps deterministic quadrature straightforward and replaceable.

The v1 slack-scale prior median is `0.12` log-performance with log-SD `0.55`. These are regularising modelling assumptions, not an RIR mapping.

### Why Student-t noise

The symmetric noise family is Student-t with `df = 5` and positive scale. Five degrees of freedom retains finite variance but materially heavier tails than Gaussian noise. V1 fixes the shape parameter rather than attempting to estimate tail shape from one sparse profile.

The v1 noise-scale prior median is `0.05` log-performance with log-SD `0.45`.

## Slope prior and positivity

Slope is parameterised on the log scale:

```text
log(b) ~ Normal(log(0.16), 0.55^2)
```

so `b` is positive by construction. There is no unconstrained fit followed by a clamp.

`0.16` is a deliberately conservative model prior, not cross-profile transfer and not a population claim stored as user truth. Direct profile evidence can override it only when rep diversity and independent-session support exist.

## Frontier prior

The reference-rep frontier uses a proper broad log-uniform prior over:

```text
0.1 kg <= frontier <= 5000 kg
```

The numerical tensor grid is centred around observed log resistance only to avoid wasting computation. The prior density itself is flat in log resistance within the broad proper bounds; `max(observed load)` is therefore not declared to be the frontier and does not become a narrow prior centre.

## Identifiability / staged freedom

The first candidate refuses to estimate every nuisance parameter from tiny histories.

### Slope

- fewer than 3 independent sessions or insufficient log-rep span: `PRIOR_DOMINATED`;
- at least 3 sessions and log-rep span >= `ln(1.5)`: may become `PARTIALLY_LEARNED`;
- at least 6 sessions and span >= `ln(2)`: may be labelled `DATA_INFORMED`.

The slope is still represented as a posterior at every stage; the labels describe identification support, not a hard confidence score.

### Slack/noise scales

Before both:

```text
independent sessions >= 8
observations >= 12
```

`σ_u` and `σ_e` are fixed at their prior medians and labelled `FIXED_BY_CONFIG`. This deliberately prevents sparse data from confidently decomposing poor performance into slack versus symmetric noise.

After that gate, the scales are allowed to update on a small log-space grid under their proper priors. They become `DATA_INFORMED` only at at least 10 independent sessions and 20 observations; otherwise they remain `PARTIALLY_LEARNED`.

Per-observation `u_s` remains a broad latent posterior when the nuisance decomposition is weak.

## Within-session dependence

V1 uses:

`EQUAL_TOTAL_WEIGHT_PER_SESSION_V1`

If a session contains `n` eligible observations, each contributes likelihood weight `1/n`. Therefore every independent workout contributes total global-parameter likelihood weight 1.

All sets remain useful for load/rep-shape information, but five sets in one workout cannot create the same longitudinal certainty as five independent sessions. Per-observation slack posteriors use the observation's full local likelihood after the global fit; the session balancing applies to global-parameter learning.

## Current-capability / temporal policy

V1 uses the smallest explicit statistical policy:

`RECENT_INDEPENDENT_SESSION_WINDOW_V1`

At an inference horizon, only evidence at/before the horizon is eligible, and only the latest **12 independent sessions** are fitted. All selected sessions are otherwise treated symmetrically; there is no exponential biological decay, fatigue, recovery, detraining half-life, SkillState or Development term.

This local-stationarity window allows repeated newer lower demonstrations to lower the current frontier once older sessions leave the window. Historical maxima are not irreversible.

## Deterministic posterior approximation

V1 uses deterministic tensor-grid integration over global parameters plus deterministic midpoint quadrature over Half-Normal slack:

```text
frontier c grid: 31 points
positive slope log-grid: 15 points
slack-scale log-grid when unlocked: 3 points
noise-scale log-grid when unlocked: 3 points
slack quadrature: 16 midpoint bins over 0..6 prior SD
```

Sparse histories therefore use `31 × 15` global nodes; nuisance-unlocked histories use `31 × 15 × 3 × 3` nodes.

Posterior arithmetic uses stable log densities/log-sum-exp. Student-t normalisation uses a deterministic Lanczos log-gamma implementation. This is **approximate deterministic Bayesian inference**, not an exact posterior.

The immutable config also records the grid radii, quadrature range, top-node count used for per-observation slack reconstruction, numerical resistance domain and maximum grid-evaluation budget.

Failure to obtain finite posterior mass, model-config mismatch, evidence-policy mismatch, zero eligible evidence or an exceeded numerical budget produces an explicit fit failure. No stale/zero capability is returned.

## Capability posterior and predictions

At `r_ref`, the posterior over `exp(c)` populates the existing `PosteriorSummary`/`PosteriorEstimate` contract with finite positive p05/p50/p95 and variance, plus `EvidenceSupport` and `ModelOutputProvenance`.

For arbitrary positive reps:

```text
x = ln(reps / r_ref)
log frontier = c - b*x
```

Every retained posterior node is transformed before quantiles are computed, preserving fitted `c`/`b` dependence within the grid approximation rather than evaluating only median parameters.

## Extrapolation uncertainty

Slope uncertainty naturally widens predictions as `|x|` grows. V1 additionally applies an explicit conservative uncertainty term only outside the observed rep domain:

```text
extraLogSd = 0.18 * log-distance beyond nearest observed rep boundary
```

It widens the posterior interval/variance without changing the median frontier. It does not turn a one-rep query into canonical e1RM.

## Per-observation slack and future SetDemand hook

Each fitted observation retains a deterministic discrete approximation to:

```text
p(u_s | fitted history)
```

plus p05/p50/p95/variance. All support is non-negative and remains on the log-performance-distance scale.

The model exposes a generic query equivalent to:

```text
P(u_s <= delta)
```

but **7B.2 chooses no `delta`** and therefore implements no SetDemand threshold. The retained distribution is only the hook required for later 7B/SetDemand work.

## Frontier versus ordinary-demonstration predictive

The frontier posterior answers latent upper demonstrated-performance capability. The model separately exposes the fitted ordinary-demonstration predictive density, marginalising Half-Normal slack and Student-t noise. This is provided so 7B.3 can evaluate successful lower-bound demonstrations without reconstructing hidden likelihood internals.

7B.2 does not implement the chronological evaluation loop.

## Context and equipment boundaries

Context consumption remains exactly:

`NONE`

No note, sleep, illness, stress, fatigue annotation or Nano interpretation changes evidence weight, slack, noise, frontier or temporal selection.

`PRODUCT_ROADMAP_GATES.md` equipment-instance/calibration requirements are preserved for 7F. 7B.2 consumes only the 7B.1 profile-local resistance coordinate and does not pull equipment translation forward.

## Known 7B.2 candidate limitations

- Half-Normal versus another one-sided slack family is not yet model-compared.
- Session balancing is a deterministic dependence approximation, not a fitted session random-effect model.
- The 12-session window is a statistical local-stationarity assumption, not biology.
- Slack and symmetric noise remain only weakly separable even after the unlock threshold; identification labels must remain visible.
- Tensor-grid resolution is intentionally modest for on-device determinism and will require held-out validation in 7B.3.
- No failure/upper-bound evidence exists because canonical workout data still cannot reliably distinguish true physical failure from stopping/skipping/interruption.
- No cross-profile transfer, equipment-instance translation, RIR, SetDemand threshold, fatigue, recovery, SkillState or Development is present.
- Room remains 14; persistence orchestration of candidate fit state is deferred to 7B.3.

## 7B.3 boundary

7B.3 must chronologically compare this candidate against simpler frontier baselines, evaluate lower-bound calibration/exceedance behaviour, assess predictive uncertainty, persist normal candidate/shadow outputs and expand diagnostics. It must not silently reinterpret the 7B.1 evidence boundary or promote the candidate into normal workout prescriptions without the later acceptance gates.
