# N-BIO-7B Dynamic Resistance Capability Contract

Status: **7B.1 implemented contract / pre-fit boundary**

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

7B.1 supplies **no implementation** and therefore cannot fabricate a stochastic frontier value. 7B.2 must implement a real fit behind this interface.

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

## 7B.2 boundary

The next phase must implement the actual stochastic-frontier mathematics behind `DynamicCapabilityModel`, including the likelihood/prior structure for frontier level, slope, submaximality slack, ordinary performance noise, temporal/recency treatment, posterior inference, and prediction uncertainty.

7B.2 must not redefine the factual evidence/coordinate semantics established here unless a contradiction is found and versioned explicitly.
