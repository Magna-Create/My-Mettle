# N-BIO-7B Dynamic Resistance Capability Contract

Status: **7B.1–7B.4 implementation complete / physical installed-history acceptance pending**

This document is the implementation contract for the first N-BIO dynamic-resistance capability candidate. It does not replace the preserved research record in `DeepResearch/`, `RESEARCH_GUIDE.md`, `RESEARCH_RAW.md`, `PLAN.md`, `CORE_MODEL_DETAIL.md` or additive gates in `PRODUCT_ROADMAP_GATES.md`.

The implementation boundary is deliberately conservative:

- canonical workout/performance history remains factual source truth;
- candidate inference remains execution-profile-version and side specific;
- context consumption remains `NONE`;
- the candidate remains `SHADOW` only;
- ordinary product authority remains `BENCHMARK_V0`;
- no 7C behaviour is introduced here.

A green implementation/CI state is not the same thing as empirical closure. N-BIO-7B is not empirically closed until the explicit installed Room14 acceptance action has been run on the real user database and its exported report reviewed.

## Internal 7B checkpoints

N-BIO-7B is split into four implementation checkpoints:

1. **7B.1 — Evidence Eligibility, Resistance Coordinate & Model Contract** — complete.
2. **7B.2 — Stochastic Frontier Inference** — complete and frozen for 7B.3/4 evaluation.
3. **7B.3 — Held-Out Validation, Shadow Persistence & Diagnostics** — implementation complete.
4. **7B.4 — Real-Data / Device Acceptance & 7B Closure** — acceptance tooling complete; physical installed-history execution remains pending.

The 7B.3/4 implementation must evaluate the frozen 7B.2 candidate. Material empirical failure requires a new candidate/config identity rather than same-history hyperparameter tuning disguised as validation.

---

# 7B.1 — canonical evidence and projection contract

## Source and canonical evidence

Canonical performance truth remains N-BIO-6 `PerformanceObservation` / `SetObservation` plus typed `PerformanceMetricValue` / `SetMetricValue` evidence. The 7B projection does not rewrite entered values, canonical values, units, execution semantics, warm-up status or correction history.

Only completed, insight-eligible sessions may contribute capability evidence. Draft values are never capability evidence.

Corrections are append-only. A corrected observation supersedes an earlier observation; the earlier row remains auditable raw history and must not be double-counted.

For current-horizon fitting, only the current unsuperseded observation is projected. For retrospective validation, revision resolution is performed **as of each historical holdout cutoff** using `recordedAt`. A correction recorded later cannot leak backwards and rewrite what was knowable before an earlier held-out session. Supersession is resolved globally before profile/side grouping, including corrections that change execution-profile version or side.

## Dynamic-resistance eligibility v1

A candidate observation must have all of the following:

- exact target `ExecutionProfileVersionId`;
- exact compatible side/laterality;
- eligible metric family: `DYNAMIC_RESISTANCE` or `BODYWEIGHT_RESISTANCE`;
- resistance semantics explicitly compatible with that metric family under the versioned evidence policy;
- completed insight-eligible session ownership;
- non-empty session identity for longitudinal support accounting;
- positive integral repetitions;
- a deterministic finite positive physical challenge coordinate;
- canonical physical resistance in kilograms after deterministic metric conversion;
- non-warm-up status.

The v1 family/semantics gate is deliberately narrow:

- `DYNAMIC_RESISTANCE` → `EXTERNAL`;
- `BODYWEIGHT_RESISTANCE` → `ASSISTANCE`, `BODYWEIGHT`, or `BODYWEIGHT_PLUS_EXTERNAL`.

The following do not enter this frontier:

- duration-only work;
- loaded/static holds;
- repeated-contraction capability;
- power-duration work;
- speed-duration/cardio;
- device ordinal resistance;
- incompatible family/resistance-semantic combinations;
- zero/non-positive repetitions;
- unresolved or non-positive resistance coordinates;
- another execution-profile version;
- incompatible side/laterality;
- explicitly marked warm-ups.

Those exclusions never delete or reinterpret canonical history.

## Warm-up policy

The evidence policy uses:

`warmUpPolicy = EXCLUDE`

Warm-ups are deliberately submaximal by design and are not treated as ordinary frontier-defining demonstrations in v1. Any future weaker/lower-bound use requires a new versioned evidence policy.

## Profile/version and side isolation

Capability is keyed to one immutable execution-profile version and one exact side/laterality state. Different exercises, profile versions, LEFT/RIGHT observations and bilateral observations do not silently update one another.

There is no 7B cross-profile transfer. Cross-profile translation remains later N-BIO work.

## Profile-local resistance coordinate

The frontier requires `R > 0` because it models `ln(R)`.

The resolver is:

`n-bio-7b1-profile-local-positive-resistance-v1`

This coordinate is a derived profile-local physical challenge representation, not canonical raw evidence and not a statement that kilograms are comparable across unrelated exercises.

### External resistance

For `DYNAMIC_RESISTANCE + EXTERNAL`, the profile's explicit external-load coefficient is applied to canonical external load. Ordinary profiles normally use coefficient `1.0`.

`EntryBasis` is preserved. `20 kg PER_HAND` remains a profile-local coordinate of `20 kg`; it is not silently totalised to `40 kg`.

### Bodyweight and assistance

Body mass is consumed only when the immutable execution profile explicitly defines the required bodyweight coefficient. Assistance requires a complete deterministic mapping and preserves the direction:

`more assistance → lower challenge`

`less assistance → higher challenge`

Missing body mass, missing assistance, inconsistent coefficients or a non-positive result is unresolved evidence, not a reason to invent an offset or epsilon.

`BODYWEIGHT_PLUS_EXTERNAL` likewise requires explicit positive bodyweight and external-load coefficients.

These coefficients are performance-coordinate conventions, not claims about muscle force.

### Device ordinal

`DEVICE_ORDINAL` is never converted to kilograms. Machine levels remain ordinal evidence for a different capability family.

## Units

Raw entered units remain unchanged. The model consumes deterministic canonical units. Equivalent kg/lb entries therefore resolve to the same physical coordinate within numerical tolerance.

## Reference-rep policy

The v1 reference policy is:

`MEDIAN_OBSERVED_LOWER_V1`

Eligible rep counts are sorted and the lower median observed rep count is selected. It is deterministic, positive, actually observed and inside the observed rep domain.

Changing this algorithm is behaviour-changing and requires new model/config identity.

## Capability-state semantics

For `DYNAMIC_RESISTANCE`, canonical candidate `CapabilityState` means:

> posterior frontier resistance at the model reference rep count for this execution-profile version and side at the inference horizon

It does **not** mean:

- e1RM;
- generic strength;
- muscle development;
- fatigue;
- recovery;
- chosen workout load.

No e1RM field is introduced.

## Context policy

7A.5 context architecture exists, but this candidate explicitly consumes:

`NONE`

Sleep, illness, fatigue, stress, notes, Nano interpretations and other context tags do not change evidence eligibility, weighting, resistance coordinates, frontier mathematics, slack, noise or temporal selection.

A future context-consuming candidate requires a new immutable behaviour-driving config and independent evaluation.

---

# 7B.2 — frozen stochastic-frontier candidate

## Research-backed boundaries versus modelling assumptions

The following are treated as research-backed implementation boundaries from the preserved research record:

- an ordinary completed load×rep set does not identify RM or RIR;
- capability is execution-profile/task specific;
- capability should be queried around directly supported rep regions rather than routed through generic e1RM;
- uncertainty should widen away from direct support;
- robust/heavy-tailed observation handling is preferable to allowing one poor session to catastrophically rewrite state.

The following are explicit modelling assumptions of candidate v1:

- stochastic-frontier likelihood;
- Half-Normal non-negative slack;
- Student-t symmetric performance noise with fixed degrees of freedom;
- all prior families and numeric hyperparameters;
- staged parameter-learning thresholds;
- equal-total-weight-per-session dependence approximation;
- bounded recent-session local-stationarity policy;
- deterministic tensor-grid / midpoint-quadrature posterior approximation;
- explicit extrapolation-uncertainty increment.

None is described as a discovered physiological constant.

## Exact candidate equation

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

`c` is log frontier resistance at reference reps. `u_s` is distance below the frontier on the log-performance scale. It is **not RIR** and is never converted into an integer reps-in-reserve estimate.

## Slack/noise priors

The v1 slack-scale prior median is `0.12` log-performance with log-SD `0.55`.

The symmetric noise family is Student-t with fixed `df = 5`; its v1 noise-scale prior median is `0.05` log-performance with log-SD `0.45`.

These are regularising assumptions, not user facts.

## Slope prior and positivity

Slope is parameterised on the log scale:

```text
log(b) ~ Normal(log(0.16), 0.55^2)
```

so positivity is structural rather than imposed by post-fit clamping.

The `0.16` median is a conservative model prior, not cross-profile transfer and not a population fact stored as user truth.

## Frontier prior

The reference-rep frontier uses a broad proper log-uniform prior over:

```text
0.1 kg <= frontier <= 5000 kg
```

The numerical grid may be centred around observed log resistance for computational efficiency, but the prior density is flat in log resistance within those proper bounds. `max(observed load)` is not declared to be the frontier.

## Identifiability / staged freedom

### Slope

- fewer than 3 independent sessions or insufficient log-rep span → `PRIOR_DOMINATED`;
- at least 3 sessions and log-rep span ≥ `ln(1.5)` → may become `PARTIALLY_LEARNED`;
- at least 6 sessions and span ≥ `ln(2)` → may be labelled `DATA_INFORMED`.

### Slack/noise scales

Before both:

```text
independent sessions >= 8
observations >= 12
```

`σ_u` and `σ_e` remain fixed at prior medians and are labelled `FIXED_BY_CONFIG`.

After that gate they may update on their configured log-space grids. They become `DATA_INFORMED` only at at least 10 independent sessions and 20 observations; otherwise they remain `PARTIALLY_LEARNED`.

Per-observation slack remains a posterior distribution even when nuisance decomposition is weak.

## Within-session dependence

V1 uses:

`EQUAL_TOTAL_WEIGHT_PER_SESSION_V1`

If a session contains `n` eligible observations, each contributes global-parameter likelihood weight `1/n`. Each independent workout therefore contributes total global-parameter weight 1. Multiple sets in one workout cannot manufacture the longitudinal certainty of multiple independent sessions.

## Current-capability temporal policy

V1 uses:

`RECENT_INDEPENDENT_SESSION_WINDOW_V1`

At an inference horizon only evidence recorded/available by the appropriate historical cutoff and completed at/before the horizon is eligible. Current fitting uses the latest **12 independent sessions**. Selected sessions are otherwise treated symmetrically.

There is no biological decay, fatigue, recovery, detraining half-life, SkillState or Development term in this candidate.

## Deterministic posterior approximation

V1 uses deterministic tensor-grid integration plus deterministic Half-Normal midpoint quadrature:

```text
frontier c grid: 31 points
positive slope log-grid: 15 points
slack-scale log-grid when unlocked: 3 points
noise-scale log-grid when unlocked: 3 points
slack quadrature: 16 midpoint bins over 0..6 prior SD
```

Sparse histories therefore use `31 × 15` global nodes; nuisance-unlocked histories use `31 × 15 × 3 × 3` global nodes.

Posterior arithmetic uses stable log-density/log-sum-exp calculations. Failure to obtain finite posterior mass, config/evidence-policy mismatch, zero eligible evidence or an exceeded numerical budget produces an explicit failure rather than stale/zero capability.

This is approximate deterministic Bayesian inference, not an exact posterior.

## Capability posterior and arbitrary-rep predictions

At `r_ref`, posterior `exp(c)` populates the existing posterior/evidence/provenance contract.

For positive queried repetitions:

```text
x = ln(reps / r_ref)
log frontier = c - b*x
```

Retained posterior nodes are transformed before quantiles are computed so fitted `c`/`b` dependence is preserved within the grid approximation.

## Extrapolation uncertainty

Slope uncertainty naturally widens predictions as rep distance grows. V1 additionally applies a conservative term only outside the observed rep domain:

```text
extraLogSd = 0.18 * log-distance beyond nearest observed rep boundary
```

It widens interval/variance without changing median frontier. A one-rep query is still not canonical e1RM.

## Per-observation slack and SetDemand boundary

Each fitted observation retains a deterministic approximation to:

```text
p(u_s | fitted history)
```

and the model can answer a generic probability of the form:

```text
P(u_s <= delta)
```

7B chooses no behaviour-driving `delta`, defines no SetDemand threshold and introduces no RIR semantics.

## Ordinary-demonstration predictive

A successful `60 kg × 8` set does not establish `frontier(8) = 60 kg`. It demonstrates that the performed resistance was achievable.

The model therefore exposes a separate ordinary-demonstration predictive distribution that marginalises non-negative slack and Student-t noise. Held-out successful sets are scored against this observable predictive distribution, **not** against the latent frontier credible interval as if each chosen load were a maximum test.

---

# 7B.3 — chronological validation, SHADOW persistence and diagnostics

## Whole-session chronological holdout

Retrospective evaluation holds out whole sessions in chronological order. For held-out session `S_k`, candidate fitting uses only eligible observations from earlier sessions. No member of `S_k` becomes training evidence until every evaluable member of that session has been scored.

The reference-rep anchor is recomputed from training evidence only.

Correction history is resolved as of the held-out session cutoff, so a later recorded correction cannot alter an earlier training/holdout state.

## Held-out diagnostics

For evaluable successful held-out sets the implementation records candidate observable-predictive diagnostics including:

- mean log predictive density;
- predictive interval coverage;
- mean predictive log-width;
- candidate predictive median MAE in kilograms;
- coarse PIT calibration bins/error where sample size permits;
- descriptive probability that the latent frontier is at/above the successful observation;
- catastrophic-frontier-contradiction count/rate;
- model-failure count/rate;
- availability/evaluable rate.

A simple historical latest-resistance-anchor MAE is retained as a benchmark diagnostic. The implementation does **not** fabricate benchmark predictive density, predictive coverage or PIT values where that benchmark does not define a predictive distribution; exported fields remain explicitly unavailable instead.

The versioned validation/verdict policy consumes the summary without silently retuning the candidate.

## Synthetic scientific gates

Deterministic tests cover, among other invariants:

- latent frontier/slope recovery under rich synthetic evidence;
- sparse versus rich prior sensitivity;
- uncertainty tightening with independent evidence;
- same-session duplication not manufacturing longitudinal certainty;
- extrapolation uncertainty widening;
- repeated higher demonstrations moving frontier upward;
- repeated newer lower demonstrations allowing current frontier decline;
- bounded influence of an adversarial poor set;
- non-negative slack behaviour;
- heavy-tail/varying-slack observable-predictive calibration fixtures;
- correction-as-of-cutoff non-leakage;
- deterministic replay/input-order invariance;
- finite positive numerical output;
- bounded several-year one-profile runtime.

These gates passed for the frozen candidate during 7B.3/4 implementation. They are implementation evidence, not a substitute for actual-user-history acceptance.

## SHADOW persistence

Room remains **14**. No schema bump is required.

Existing v7 inference tables are used:

- `inference_run` — run provenance;
- immutable `model_config_definition` / model manifest tables — behaviour/config identity;
- `capability_state` — reference-rep frontier posterior;
- `capability_parameter_state` — versioned joint dynamic-capability parameter state.

7B.3 persistence uses explicit `HISTORICAL_SEMANTICS` and `SHADOW` execution mode. `DynamicCapabilityShadowRepository` validates model-config identity and manifest binding before reload.

Normal workout/product reads continue to select `BENCHMARK_V0`; 7B does not promote SHADOW state into ordinary prescriptions.

The versioned parameter codec preserves the fitted joint state needed for deterministic representative predictions, including per-observation slack state. Acceptance verifies both the persisted `capability_state` frontier posterior and the encoded parameter state so one cannot mask corruption of the other.

## Replay / discard semantics

Candidate derived state is disposable. Deleting an `inference_run` cascades candidate-derived rows only; canonical workout/performance evidence does not depend on candidate inference rows.

Installed-history acceptance performs:

1. frozen final-horizon fit;
2. temporary SHADOW persist;
3. reload and equivalence check;
4. deletion of the temporary SHADOW run;
5. full replay from canonical evidence;
6. replay equivalence check;
7. final SHADOW persistence.

Representative frontier predictions are compared across in-memory, reloaded and replayed state under strict numerical tolerance.

---

# 7B.4 — installed-history/device acceptance

## Explicit foreground action

Real-history acceptance is an explicit developer action, not a background task and not normal app behaviour.

The debug UI exposes the flow under Biological Developer tools as:

```text
Run N-BIO-7B real-history acceptance
→ inspect profile/group diagnostics
→ export N-BIO-7B acceptance JSON
```

The action reads the installed Room14 history, evaluates discovered dynamic-resistance profile-version/side groups, persists only SHADOW derived state and leaves `BENCHMARK_V0` authoritative.

## Canonical evidence invariance

Before/after acceptance, the implementation hashes canonical workout/performance rows rather than exporting their raw contents. The fingerprint covers:

- `session`;
- `session_exercise`;
- `set_record`;
- `set_observation`;
- `set_metric_value`.

Text fields are hashed only and are not emitted in the diagnostic JSON.

A separate persisted-prescription fingerprint covers:

- `session_set_prescription`;
- `session_metric_target`.

The explicit action fails its closure integrity check if those fingerprints/counts change during candidate work.

## Benchmark authority invariance

The latest authoritative `BENCHMARK_V0` run ID is captured before and after acceptance. A change is a failure. Candidate rows remain `SHADOW` only.

## Native full-backup acceptance

The existing generic `NativeFullBackupRepository` remains authoritative; no candidate-specific backup format is introduced.

After real-history SHADOW persistence, the closure runner:

1. exports the **installed** Native Room14 database to the normal typed full-backup JSON in memory;
2. creates a separate in-memory `MyMettleDatabase` using the current Room14 schema;
3. restores the exported JSON into that isolated database;
4. compares canonical raw-evidence fingerprints;
5. compares persisted-prescription fingerprints;
6. compares SHADOW dynamic-capability run/state/parameter row counts;
7. runs `PRAGMA foreign_key_check` on the isolated restore;
8. closes the isolated database.

The live installed database is **never restored, cleared or replaced** by this verification. Its backup participation is export-only.

If no installed history produces candidate SHADOW rows, generic backup round-trip can still pass, but candidate-row empirical coverage is reported `INSUFFICIENT` rather than falsely `PASS`.

Separate Android instrumentation also covers a fully FK-valid Room14 dynamic-capability backup fixture with model config/manifest, inference run, `capability_state` and `capability_parameter_state`.

## Acceptance report v2

The privacy-bounded export remains:

`kind/format = my-mettle-n-bio-7b-acceptance`

Format v2 extends the base acceptance report with closure-integrity evidence, including:

- candidate model/config/protocol identity;
- Room schema version;
- context consumption;
- discovered profile/group counts;
- evidence/session/rep/resistance support summaries;
- fitted frontier/slope/slack/noise summaries and identification labels;
- representative predictions;
- chronological held-out metrics and candidate verdict;
- persistence/reload/replay equivalence;
- raw-evidence fingerprint/count invariance;
- persisted-prescription fingerprint/count invariance;
- benchmark authority before/after;
- isolated Native-backup restore status;
- candidate-row backup counts/coverage;
- foreign-key integrity;
- per-profile and total runtime;
- explicit limitations;
- combined pass/fail state.

Raw notes, Health Connect traces and a raw set-by-set history dump are not added to this closure export.

## Empirical closure rule

CI can establish implementation correctness, deterministic synthetic behaviour, backup/instrumentation behavior, lint/build correctness and Room schema stability. It cannot inspect the user's installed Room14 history.

Therefore the truthful state after implementation is:

**7B.3/4 implementation complete → physical real-history/device acceptance pending.**

Only after the installed report is exported and reviewed may an empirical 7B closure verdict be recorded. Possible outcomes include insufficient evidence, acceptable for SHADOW, acceptable with limitations, rejection or a requirement for a new candidate according to the versioned verdict contract.

A material real-history failure must not be repaired by tuning the same candidate against the same evaluation history and then calling that tuned result independently validated.

---

# Known limitations retained after 7B.3/4 implementation

- Half-Normal versus alternative one-sided slack families has not been established as a physiological truth.
- Session balancing is a deterministic dependence approximation, not a fitted session random-effect model.
- The 12-session window is a local-stationarity modelling assumption, not biology.
- Slack and symmetric noise remain weakly separable in sparse histories; identification labels remain essential.
- Tensor-grid resolution is deliberately modest for deterministic on-device inference.
- Current canonical evidence does not reliably distinguish true physical failure from stopping/skipping/interruption, so v1 consumes no upper-bound failure evidence and never infers failure from absence.
- There is no cross-profile transfer or equipment-instance translation in this phase.
- There is no RIR model, SetDemand threshold, fatigue, recovery, SkillState or Development process in this candidate.
- Context consumption is `NONE`.
- Candidate output remains SHADOW and does not drive normal workout prescriptions.
- Real-device runtime and real-history predictive/calibration evidence remain unknown until the physical acceptance action is run.

## Next-phase boundary

N-BIO-7C has **not** started. Do not use successful 7B implementation CI as implicit permission to start 7C, promote candidate authority or cross a `PRODUCT_ROADMAP_GATES.md` collaboration boundary.

## 7B.4 corrective evidence policy v2 — legacy unsided history

The first installed-history acceptance run (2026-08-28) was an integrity/safety pass but did not evaluate the stochastic frontier: all discovered resistance groups were `Laterality.UNKNOWN`, so evidence-policy v1 excluded every observation before fitting. This was an integration/evidence-admissibility defect, not an empirical failure of the Half-Normal/Student-t candidate.

The persisted provenance is sufficient to correct this without inventing history. Lite-translated observations are explicitly stored with `source = lite_legacy_v6_import`, and their translated execution-profile versions use `lateralityMode = unknown`. Native manual observations use a different source and unilateral Native entry requires explicit LEFT/RIGHT.

Evidence policy `n-bio-7b1-dynamic-resistance-evidence-v2` therefore permits an **UNKNOWN → UNKNOWN** capability stream only when all of the following hold:

- requested capability side is `UNKNOWN`;
- factual observation side is `UNKNOWN`;
- immutable execution-profile laterality mode is `UNKNOWN`;
- factual observation source is exactly `lite_legacy_v6_import`.

This does **not** reinterpret UNKNOWN as BILATERAL. UNKNOWN observations cannot enter LEFT, RIGHT or BILATERAL capability state. Native `UNKNOWN` observations without the explicit legacy-import provenance remain ineligible. Any future broadening requires another evidence-policy/config identity.

The 7B.2 stochastic mathematics is unchanged: Half-Normal slack, Student-t noise (`df=5`), all priors, session balancing, 12-session statistical window, deterministic tensor grid and extrapolation policy remain frozen. Only the immutable evidence-policy identity/config binding changes.

Acceptance export format v3 reports per-profile and global exclusion-reason counts and separates three verdicts:

- `integritySafety`: raw/prescription/authority/context/FK/backup safety;
- `empiricalModelEvaluation`: whether the stochastic candidate was actually evaluated and its validation outcome;
- `overall7BClosure`: whether N-BIO-7B is empirically ready to close.

Candidate-dependent checks with no fitted candidate report `not_evaluated`, never a vacuous pass. The corrected implementation remains `SHADOW`, Room14, context `NONE`, and `BENCHMARK_V0` remains normal product authority.

**Current gate:** corrected 7B.3/4 implementation complete after CI; physical installed-history acceptance must be rerun before empirical N-BIO-7B closure.
