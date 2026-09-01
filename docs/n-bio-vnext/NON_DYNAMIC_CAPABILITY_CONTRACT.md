# N-BIO-7C — Non-dynamic Same-profile Capability Contract

Status: **N-BIO-7C ACTIVE — candidate models pre-registered before implementation.**

This is the normative contract for the single consolidated N-BIO-7C mission covering `LOADED_HOLD`, `DURATION_ONLY` and `REPEATED_CONTRACTION`. It consumes the completed N-BIO-7B.X adaptive-inference architecture. It does not reopen the 7B.X solver decision and does not authorise N-BIO-7D or later biological/product work.

Read with `PLAN.md`, `ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`, `CORE_MODEL_DETAIL.md`, `DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md` and `PRODUCT_ROADMAP_GATES.md`.

## 1. Authority and epistemic labels

- **RESEARCH-BACKED:** static/isometric load-duration work is not a dynamic repetition set; force/load and sustainable duration trade off nonlinearly; duration-only tasks require a separate capability quantity; repeated contractions are not a continuous static hold; task specificity forbids cross-profile unit equivalence.
- **STRUCTURAL:** exact execution-profile version, semantic regime and side define the capability stream; raw observations are immutable; successful observations are lower-bound demonstrations; capability and action-selection policy are distinct; uncertainty grows with inferential distance.
- **MODELLING-ASSUMPTION:** every equation, prior, grid, threshold, reference coordinate, window and extrapolation coefficient below is a replaceable versioned first candidate, not physiology.
- **DEFERRED:** hierarchy/pooling, action-policy prediction, cross-profile translation, conditioning, SetDemand, dose, fatigue/recovery/development, SkillState and product prescription behaviour.

No equation in this document is a universal workload law. There is no `kg*seconds`, `rep*seconds`, `kg*cycles` or other family-collapsing score.

## 2. Pre-implementation real-history audit

The current Room14 Native history at 7C start contains:

- `DURATION_ONLY`: one execution-profile version, **Dead Hang — Default**, 5 completed observations across 3 independent sessions, all historical `UNKNOWN` laterality from `corrected_lite_import`, duration domain 23–55 s, resistance semantics `NONE`, and no recorded body-mass context on those observations.
- `LOADED_HOLD`: no current execution-profile/history evidence.
- `REPEATED_CONTRACTION`: no current execution-profile/history evidence.

Therefore loaded-hold and repeated-contraction real-history evaluation is pre-registered as `NOT_EVALUATED_REAL_HISTORY` unless genuine evidence appears before closure. Synthetic validation may establish architecture/numerics but cannot be relabelled as empirical user-history confirmation. No historical record is repaired or invented inside 7C.

Room14 already stores family-keyed `capability_state` and `capability_parameter_state`, so no schema migration is justified by the 7C family split itself.

## 3. Shared 7C evidence semantics

Eligible evidence is performed/completed, insight-eligible, current-as-of-cutoff, non-superseded evidence for one exact execution-profile version and exact side stream. Warm-ups are excluded. Required model metrics must be positive and finite; count/cycle values are positive integers. Canonical units are used without destructively replacing entered values.

Laterality is exact:

```text
LEFT       -> LEFT only
RIGHT      -> RIGHT only
BILATERAL  -> BILATERAL only
UNKNOWN    -> UNKNOWN only
```

Historical `UNKNOWN` is admitted only as an exact UNKNOWN stream for explicitly versioned legacy/corrected import provenance and an UNKNOWN profile mode. It is never reinterpreted as bilateral and bilateral evidence is never decomposed into unilateral evidence.

Known execution/equipment/entry-basis/resistance/laterality/version boundaries are deterministic semantic boundaries. Statistical smoothing never crosses them.

A successful observation is a lower-bound performance demonstration. It is **not** assumed to be a maximum effort, failure test, RIR/RPE observation or random draw from the user's future action-selection distribution.

## 4. Common candidate likelihood and dynamic state

All three first candidates use the same *structural* lower-bound form while retaining different family coordinates:

```text
y_s = c + g*z_s - b*x_s - u_s + epsilon_s     (families with a slope)
y_s = c + g*z_s       - u_s + epsilon_s         (duration-only)

u_s >= 0
u_s ~ HalfNormal(sigma_u)
epsilon_s ~ StudentT(df=5, 0, sigma_e)
```

`z_s` is an independent-session coordinate anchored at zero for the latest selected session and negative for older selected sessions. `g` is neutral statistical trajectory in log performance per independent-session step. It is not development, recovery, fatigue, skill or adaptation rate.

The one-sided `u_s` is capability-likelihood nuisance slack only. It is not used as an authoritative distribution of what hold/set the user will choose next.

Within a session, observations share equal total session weight so repeated sets are not treated as fully independent longitudinal evidence.

Current-state fitting uses at most the most recent **12 independent sessions**. This is a computational/model assumption, not a recovery or biological window.

Trajectory is fixed at zero below 3 independent sessions. When unlocked, the first candidate uses a zero-centred Normal trajectory prior; its family-specific scale is below. Parameter evidence maturity is reported separately.

## 5. LOADED_HOLD candidate

### Capability question

> What profile-local physical resistance is currently plausible at a specified hold duration for this exact execution-profile version, semantic regime and side?

### Exact candidate

```text
y_s = ln(R_s)
x_s = ln(T_s / T_ref)

y_s = c + g*z_s - b*x_s - u_s + epsilon_s
b > 0
```

`R_s` is a positive profile-local physical resistance coordinate resolved only from explicit versioned `ResistanceModel` semantics. `T_s` is duration in seconds.

### Reference duration

`T_ref = 30 seconds`, fixed by immutable model config (`FIXED_30_SECONDS_V1`). Research used 30 s as a numerically convenient reference example; 7C adopts it for conditioning/interpretability, **not** as a physiological threshold. Arbitrary-duration predictions always integrate the joint posterior over level, slope, trajectory and nuisance parameters.

### First prior/identification configuration

- slope `b`: LogNormal(median **0.55**, log-SD **0.75**). This is deliberately broad. The centre is only a weak power-law-shaped starting assumption informed by static force-duration literature; it is not a universal human coefficient.
- trajectory `g`: Normal(0, **0.05**) log-performance per independent-session step once trajectory learning is unlocked.
- slack-scale prior: LogNormal(median **0.15**, log-SD **0.65**).
- observation-noise-scale prior: LogNormal(median **0.06**, log-SD **0.55**), Student-t df 5.
- slope `PARTIALLY_LEARNED`: >=3 independent sessions and log-duration span >= ln(1.5).
- slope `DATA_INFORMED`: >=6 independent sessions and log-duration span >= ln(2.0).
- nuisance learning unlock: >=8 sessions and >=12 observations; `DATA_INFORMED`: >=10 sessions and >=20 observations.

Physical resistance prior/numerical support is 0.05–5000 kg-equivalent **only when the profile's explicit resistance semantics genuinely resolve canonical kilograms**. This domain is a numerical safety bound, not cross-profile equivalence.

### Resistance/bodyweight semantics

Allowed loaded-hold resistance semantics are explicit `EXTERNAL`, `BODYWEIGHT`, `BODYWEIGHT_PLUS_EXTERNAL` or `ASSISTANCE` relationships where all required inputs exist and the versioned resolver produces a finite positive coordinate. Missing body mass/assistance/load remains unresolved. `NONE` and `DEVICE_ORDINAL` are not silently converted to kilograms.

## 6. DURATION_ONLY candidate

### Capability question

> Given this exact execution-profile version/semantic regime/side, what duration is currently plausibly available?

### Exact candidate

The first candidate intentionally has **no fake load coordinate and no slope**:

```text
y_s = ln(T_s)

y_s = c + g*z_s - u_s + epsilon_s
```

- trajectory `g`: Normal(0, **0.06**) log-duration per independent-session step when unlocked.
- slack-scale prior: LogNormal(median **0.20**, log-SD **0.70**).
- observation-noise-scale prior: LogNormal(median **0.08**, log-SD **0.60**), Student-t df 5.
- nuisance thresholds are the same explicit 8 sessions/12 observations unlock and 10/20 data-informed thresholds, but belong to this separate config identity.
- numerical duration support: 1–86,400 seconds.

The execution profile must use `ResistanceSemantics.NONE`. An external-load/assistance metric in a duration-only profile is an unsupported metric combination rather than a reason to invent a loaded coordinate.

### Body-mass/context treatment

Body mass is preserved as observation-level factual context but is **not model-driving in the first duration-only candidate**. The current real Dead Hang history has no body-mass snapshots for these observations and cannot identify a body-mass coefficient. If future body-mass variation becomes consequential, it requires a new immutable mathematical candidate/config and validation; 7C v1 does not silently add `bodyMass = load`.

Grip/support/equipment/angle/technique that defines task capability belongs in versioned execution semantics, not a generic context vector.

## 7. REPEATED_CONTRACTION candidate

### Capability question

> What profile-local physical resistance is currently plausible at a specified positive cycle count for this exact repeated-contraction execution-profile version/semantic regime/side?

### Exact candidate

```text
y_s = ln(R_s)
x_s = ln(C_s / C_ref)

y_s = c + g*z_s - b*x_s - u_s + epsilon_s
b > 0
```

`C_s` is the positive integral cycle/repetition count **inside the REPEATED_CONTRACTION family**. It is not reclassified as `DYNAMIC_RESISTANCE` merely because the raw count metric is `REPETITIONS`.

### Reference cycle policy

`C_ref` uses deterministic `MEDIAN_OBSERVED_LOWER_V1`: sort eligible cycles and select the lower median. The actual selected value is persisted with fit provenance. This keeps the reference in-domain without inventing a universal physiologic cycle count.

### First prior/identification configuration

- slope `b`: LogNormal(median **0.30**, log-SD **0.90**). This is intentionally much broader/weaker than the loaded-hold prior because current research/history does not identify a universal repeated-contraction load-cycle slope.
- trajectory `g`: Normal(0, **0.05**) log-performance per independent-session step when unlocked.
- slack-scale prior: LogNormal(median **0.15**, log-SD **0.75**).
- observation-noise-scale prior: LogNormal(median **0.07**, log-SD **0.65**), Student-t df 5.
- slope partial/data-informed thresholds: same explicit >=3 sessions + ln(1.5) cycle span / >=6 + ln(2.0) span, under this separate family config identity.
- nuisance learning: explicit 8/12 unlock and 10/20 data-informed thresholds.

### Cadence and duration

Cadence is preserved typed evidence but **not a free latent parameter in v1**. If cadence is present and materially varies within one profile stream, the v1 fit fails closed as `UNSUPPORTED_CONTEXT`; a capability-defining cadence regime should instead be explicit/versioned or a later model must earn a cadence covariate. One fixed cadence value may be retained as diagnostic context.

Optional duration is preserved and validated as positive but is not an additional v1 latent dimension. When cycles, cadence and duration coexist, 7C does not pretend all three independently identify separate parameters. No `cycles*seconds` scalar is created.

## 8. Extrapolation and horizon uncertainty

No query is silently clamped.

For `LOADED_HOLD` and `REPEATED_CONTRACTION`, prediction is generated from each joint posterior node at the requested duration/cycle coordinate. Outside the observed duration/cycle domain, extra log-scale uncertainty grows with log-distance using coefficient **0.28 per unit log-distance**. If the resulting median lies beyond the observed resistance domain, an additional **0.18 per unit log-output-distance** uncertainty term is added.

For future session horizons, all families add process uncertainty rather than treating the fitted linear trajectory as certain forever:

- loaded hold: 0.05 log-SD per square-root independent-session step;
- duration-only: 0.06;
- repeated contraction: 0.05.

These are versioned modelling assumptions. They widen uncertainty; they do not create a biological decay/growth law.

## 9. Solver pre-registration

The 7B.X solver decision remains frozen:

- **Adaptive Sparse** is the forward Candidate-v2-style inference representation/backend.
- **Dense deterministic grid** remains the high-fidelity reference/oracle.
- **Conditional Laplace remains rejected** for Candidate-v2 production and is not resurrected for 7C.

7C uses the same-mathematics comparison pattern: a base grid over family parameters is evaluated, then trajectory is expanded. Adaptive Sparse deterministically retains posterior-dominant base support before trajectory expansion; Dense keeps all base support. Sparse configuration starts at retained base mass **0.9995**, minimum 32 nodes, maximum 1024 nodes. This approximation must be checked against Dense for quantiles, variance, slope/trajectory dependence, tails and arbitrary queries before family acceptance.

The first numerical grids are:

- frontier level: 17 points;
- slope where present: 11 points;
- trajectory when unlocked: 11 points across +/-3 prior SD;
- nuisance scales when unlocked: 3 x 3 points;
- Half-Normal slack quadrature: 12 midpoint points out to 6 SD.

Changing these numerical choices requires a new solver identity, not a new physiology claim.

## 10. Hierarchy / cold start

7C first candidates use **NO_POOLING**. Physical capability is profile/side local. No duration, load, slope or trajectory is transferred between Dead Hang, loaded grip hold, suitcase hold, repeated grip roll or unrelated profiles.

The hierarchy contract remains available for later evidence-backed experiments, but 7C does not enable semantic-family pooling. Sparse real evidence is a reason for broader priors/uncertainty, not a reason to invent transfer.

## 11. Capability versus action policy

The action-policy model remains `UNMODELLED`. 7C may expose developer-only capability posterior queries, but it does not predict the user's next chosen hold duration/load/cycle count and does not prefill a workout.

Capability slack is never scored as though it were an authoritative performed-action distribution. Retrospective evaluation therefore uses lower-bound consistency/frontier contradictions and posterior behavior, not fake CRPS against user-selected successful actions.

## 12. Persistence and Room14

Derived 7C state is disposable/recomputable and should use the existing generic Room14 capability/config/manifest/run storage. Family-specific parameter codecs must store enough joint posterior state for supported arbitrary queries, carry mathematical and solver identities separately, fail closed on unknown versions, and never duplicate raw observations as canonical truth.

No table-per-family design is authorised. No Room migration is authorised by this contract.

## 13. Typed failure semantics

7C implementations must fail closed using explicit states equivalent to:

```text
NO_ELIGIBLE_EVIDENCE
INSUFFICIENT_IDENTIFIABILITY
MODEL_CONFIG_MISMATCH
EVIDENCE_POLICY_MISMATCH
UNSUPPORTED_METRIC_COMBINATION
INVALID_RESISTANCE_SEMANTICS
UNSUPPORTED_CONTEXT
NUMERICAL_BUDGET_EXCEEDED
NON_FINITE_POSTERIOR
DEGENERATE_POSTERIOR
SOLVER_FIDELITY_REJECTED
```

An empty projection is not a successful fit.

## 14. Validation contract

Synthetic validation must cover family monotonicity, positive/negative/static trajectory behavior, sparse-history breadth, robust-outlier behavior, extrapolation widening, laterality isolation, semantic-profile isolation, deterministic input/revision ordering, persistence/reload/replay and Dense-vs-Sparse fidelity.

Real history uses whole-session chronological evaluation only where sufficient. Because action policy is intentionally unmodelled, successful held-out observations are evaluated as lower-bound capability consistency rather than a claim that the performed observation should equal the frontier median.

## 15. Product boundary

N-BIO-7C remains SHADOW/developer inference foundation. `BENCHMARK_V0` remains normal product authority. No normal workout/prescription/order/prefill or normal-user capability display changes are authorised.

N-BIO-7D SetDemand/dose work and every later biological/translation/conditioning/V8 feature remain strictly out of scope.