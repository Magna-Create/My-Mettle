# N-BIO-7B Dynamic Resistance Capability Contract

Status: **Candidate v1 frozen/rejected; Candidate v2 implemented as a bounded development experiment; N-BIO-7B.X adaptive-inference consolidation active; physical adaptive-inference acceptance pending.**

> The complete pre-adaptive 7B contract is preserved verbatim as [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT_LEGACY_PRE_ADAPTIVE.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT_LEGACY_PRE_ADAPTIVE.md). This document is the current forward contract and must be read with [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md).

This contract governs dynamic-resistance capability evidence and the two historical model candidates used to discover the forward architecture. It does not authorise N-BIO-7C, SetDemand, exposure/dose redesign, fatigue/recovery/development inference, cross-profile translation, Health Connect or adaptive prescription behaviour.

## 1. Product and data authority

The following invariants remain unchanged:

- canonical workout/performance history is factual source truth;
- corrections are append-only/superseding and retrospectively resolved as-of the relevant cutoff;
- candidate inference is execution-profile-version and side specific;
- current dynamic candidates consume context `NONE`;
- all v1/v2 inference remains `SHADOW` / development-only;
- normal workout/prescription authority remains `BENCHMARK_V0`;
- no candidate adds normal-user prefill, capability display or prescription changes;
- Room remains 14;
- derived-state deletion cannot cascade into raw evidence;
- Native full backup remains generic and authoritative for safety/replay acceptance.

## 2. Evidence and resistance-coordinate contract

Dynamic-resistance capability consumes only committed, insight-eligible performance evidence whose immutable execution semantics support a deterministic positive physical challenge coordinate.

### Eligible families

Current 7B evidence remains deliberately narrow:

```text
DYNAMIC_RESISTANCE + EXTERNAL
BODYWEIGHT_RESISTANCE + {ASSISTANCE, BODYWEIGHT, BODYWEIGHT_PLUS_EXTERNAL}
```

with positive integral repetitions, compatible laterality/profile version, explicit resistance semantics and non-warm-up status.

Duration-only work, loaded/static holds, repeated-contraction capability, conditioning, power/speed-duration and device-ordinal resistance remain outside this capability family. Their raw evidence remains canonical for later model families.

### Profile-local coordinate

The 7B resolver remains a derived profile-local physical challenge representation. A kilogram coordinate does **not** imply kilograms are exchangeable across unrelated exercises or machines.

Entry basis is preserved. `20 kg PER_HAND` remains a 20 kg profile-local coordinate unless immutable execution semantics explicitly define another relationship.

Bodyweight/assistance uses only explicitly defined coefficients and required body-mass context. Missing context remains unresolved rather than invented.

### Semantic regime rule

A mathematically dynamic model may only operate within a semantically coherent regime.

Known execution/profile/equipment/setup/resistance/laterality boundaries are deterministic metadata boundaries. Statistical discontinuity detection is secondary and derived-only; it cannot rewrite canonical history automatically.

Known legacy semantic mistakes may be split/corrected during a separately reviewed authoritative migration. N-BIO-7B.X does not silently perform that cleanup.

## 3. Capability semantics

For current dynamic resistance, capability means:

> a posterior over performance plausibly available on one exact execution-profile version and side at a stated inference horizon, queryable at supported repetition values.

It does not mean:

- e1RM;
- generic strength;
- muscle development;
- recovery/readiness;
- fatigue;
- skill;
- selected workout load;
- likely user action.

Statistical `trajectory` / `frontierTrend` is deliberately neutral. It must not be renamed development/growth/detraining without independent evidence.

## 4. Candidate v1 — frozen historical candidate

### Identity and mathematics

Candidate v1 is the immutable stochastic-frontier family:

```text
y_s = ln(R_s)
x_s = ln(reps_s / r_ref)

y_s = c - b*x_s - u_s + epsilon_s

b > 0
u_s >= 0
u_s ~ HalfNormal(sigma_u)
epsilon_s ~ StudentT(df = 5, 0, sigma_e)
```

The candidate uses a broad proper log-uniform frontier prior, positive log-space slope prior, staged nuisance learning, equal-total-session weighting, a recent independent-session window and deterministic tensor-grid + slack-quadrature inference.

Those numerical choices are modelling assumptions and remain frozen under the candidate's immutable identity.

### Important retained discovery

Candidate v1 demonstrated that its **latent capability frontier** could be materially better centred than its **ordinary demonstration predictive**.

This is not evidence that the frontier idea is useless. It is evidence that:

```text
CAPABILITY
!=
USER ACTION / WORKING-SET SELECTION POLICY
```

The Half-Normal slack variable may therefore remain scientifically useful as one-sided lower-bound capability evidence, a nuisance variable or latent distance below a frontier.

What is rejected is the assumption that generic stochastic-frontier slack automatically equals the distribution of loads/reps the user chooses or performs.

### Final status

Candidate v1 is permanently:

`REJECTED_EMPIRICAL_CALIBRATION_V1`

Do not retune/mutate it after seeing the real-history failure. New mathematical changes require new identities.

## 5. Candidate-v1 installed-history evidence

The corrected Room14 installed-history run produced 105 evaluable held-out demonstrations.

The versioned Stage-1 temporal diagnostic reported:

```text
evaluable events                    105
mean signed log residual            +0.0954
median signed log residual          +0.1265
positive residual proportion         0.7143
mean predictive log width            0.4596
mean CRPS (log resistance)           0.1667
trend-classified events             14
trend residual correlation           0.4844
```

For the 13 upward-trend-classified events:

```text
positive residual proportion         0.7692
predictive coverage                   0.6154
catastrophic contradiction rate      0.3077
mean CRPS                             0.1222
```

Serial diagnostics reported:

```text
profile-session count               44
adjacent comparable pairs           28
same-sign adjacent rate              0.6786
positive-positive adjacent rate      0.6429
longest positive residual run        5
lag-1 residual correlation          -0.2221
```

The Stage-1 diagnostic verdict itself was `insufficient_diagnostic_evidence`, chiefly because only 14 held-out demonstrations had enough comparable prior same-rep history for trend classification and there was essentially no stable-history comparison group.

That verdict did **not** prove temporal lag as the sole defect. It provided enough development evidence to justify the deliberately bounded Candidate-v2 trend experiment while keeping broader architecture open.

The raw/prescription/backup integrity checks remained separate from model calibration. Candidate v1's calibration failure is scientific/model evidence, not a database-safety failure.

## 6. Capability vs action/observation policy

The forward architecture requires two distinct contracts.

### Dynamic capability model

Answers what performance is plausibly available under one coherent execution regime.

### Training action / observation policy model

Would answer what load/reps/action the user is likely to select or perform given capability and factual policy inputs such as:

- prior successful load/reps;
- previous performed sets;
- current prescription;
- target rep range;
- set ordinal/exercise order;
- recent progression;
- manual override;
- feasible equipment increments;
- programme intent;
- session modifications.

Current 7B.X implementation deliberately uses an **unmodelled policy placeholder**. It returns no load/repetition distribution.

RIR/RPE remain unknown where not observed. Do not invent `RIR=2`, `RPE=8`, `85% of maximum` or similar constants.

## 7. Candidate v2 — bounded temporal development experiment

### Purpose

Candidate v2 asks one narrow question raised by Candidate v1's real-history residuals:

> Does allowing a profile-local statistical frontier trajectory improve historical predictive calibration enough to justify moving from a locally stationary capability model toward genuinely dynamic state?

It is not an attempt to rescue v1 at any cost and does not determine the final dynamic-state family.

### Mathematics

Candidate v2 retains the frozen-v1 parameter base and adds a neutral per-independent-session trajectory:

```text
y_s = c0 + g*z_s - b*x_s - u_s + epsilon_s
```

where `z_s` is the ordered independent-session coordinate and `g` is `frontierTrend` on the log-performance scale.

`g` means statistical trajectory only.

The mathematical model identity is independent of the solver identity.

### Sparse-history reduction

When independent longitudinal support is insufficient to learn trajectory, the Candidate-v2 implementation fixes trend at zero rather than manufacturing motion. In that regime it reduces toward the frozen v1 base.

### Candidate-v2 solvers

The valid same-mathematics comparison currently includes:

1. **Dense reference** — complete frozen-v1 posterior support crossed with a deterministic trend grid; high-fidelity Kotlin/JVM reference.
2. **Conditional Laplace** — fast approximation over the same Candidate-v2 mathematical model and same frozen-v1 proposal.

At each direct comparison cutoff, v1 is fitted once and the identical frozen posterior is supplied to both v2 solvers. Timing of the v2 extension is recorded separately from the shared v1 proposal cost.

### Candidate-v2 status

Candidate v2 remains a **development candidate** until the consolidated installed-device acceptance report is executed and reviewed.

Its current verdict is therefore:

`INCONCLUSIVE_PENDING_ADAPTIVE_INFERENCE_ACCEPTANCE`

A future result may be PASS, FAIL or remain INCONCLUSIVE. The broader adaptive architecture must not be distorted merely to promote v2.

## 8. Solver architecture

The mathematical model is not the solver and the solver is not the hardware backend.

### Dense tensor reference

The deterministic dense engine remains a reference/oracle wherever tractable. It is retained for:

- synthetic parameter recovery;
- posterior-fidelity comparisons;
- tail/covariance checks;
- replay regression;
- selected-profile offline validation.

High runtime alone does not obsolete it.

### Generic sequential solver substrate

The codebase now contains model-agnostic implementations for:

- dense sequential fixed-grid Bayesian update;
- posterior-focused adaptive sparse-grid update;
- Gaussian sigma-point/moment-matching update;
- low-rank posterior viability screening;
- minimum dependency/invalidation indexing.

These generic substrates demonstrate the forward solver architecture. They are **not automatically Candidate-v2 solvers**. A same-model bake-off is valid only when mathematical identity, priors, latent transitions, likelihood, horizon and observations are identical.

### Sequential tensor principle

Tensor inference is allowed to reuse posterior state sequentially:

```text
posterior(k)
  → transition
prior(k+1)
  → likelihood(new evidence)
posterior(k+1)
```

It need not mean refitting S1..Sk from scratch.

### Sparse/adaptive principle

Sparse/adaptive tensor methods must measure posterior error against dense reference. Uniformly lowering all grid resolution is not considered an adequate adaptive solver.

### Low-rank / tensor-train status

A targeted posterior-matricisation viability screen exists. It measures compression ratio, L1 probability error, marginal error and KL divergence before any production tensor-train implementation is justified.

A poor compression result is a valid reason to stop. No TT production solver is assumed.

### Fast approximation status

A generic Gaussian sigma-point solver exists as a fast sequential challenger for models whose posterior shape makes Gaussian moment representation defensible.

Candidate v2's actual same-math fast approximation is currently conditional Laplace. A generic sigma-point success on a Gaussian fixture is not evidence that sigma points preserve the real Candidate-v2 one-sided/heavy-tail posterior.

## 9. Posterior-fidelity contract

For identical mathematical candidates, compare more than means.

At minimum where representable:

- p05/p50/p95;
- variance;
- covariance/dependence;
- frontier/slope/trajectory relationships;
- tail probabilities;
- asymmetry/multimodality;
- arbitrary-rep predictions;
- predictive-distribution divergence;
- approximation failure.

Candidate-v2 dense-vs-Laplace current-state diagnostics explicitly record posterior fidelity and solver identities.

## 10. Predictive evaluation contract

Historical/prequential development scoring records, where supported:

- CRPS on log resistance;
- log predictive density;
- PIT/reliability summaries;
- p05–p95 coverage;
- weighted interval score / interval sharpness;
- predictive median MAE as a secondary point metric;
- signed residual bias;
- catastrophic frontier contradictions;
- availability/model failure;
- profile-level results.

A solver that is posterior-faithful but attached to a bad mathematical model does not become good merely because the approximation is exact.

Historical data already inspected is development evidence. Fresh future workouts are the strongest confirmatory evidence.

## 11. Parameter-level identification and hierarchy

Do not emit one scalar confidence/maturity score.

Per-parameter support uses statuses such as:

```text
FIXED_BY_CONFIG
PRIOR_DOMINATED
PARTIALLY_LEARNED
DATA_INFORMED
```

The adaptive hierarchy contract explicitly prohibits cross-profile pooling of raw physical capability/coordinates.

Possible future weakly poolable statistical parameters include rep-range slope, observation variability, process volatility, action-policy behaviour, outlier prevalence and selected nuisance terms. These are hypotheses only; no semantic-family transfer is enabled without explicit versioned mapping and held-out evidence.

## 12. Persistence codec and Room14

Room remains **14**.

Candidate-v2 reuses existing inference/model-config/manifest/capability/parameter-state persistence rather than adding solver-specific tables.

The Candidate-v2 parameter codec is solver-aware and versioned. Requirements are:

- explicit codec schema/version;
- mathematical-model identity;
- solver identity;
- deterministic encode/decode where claimed;
- unknown versions fail closed;
- v1 state remains readable;
- v2 state remains readable;
- raw evidence is not duplicated into derived state.

The SHADOW repository checks model/config identity before reload.

## 13. Replay and backup

The installed-device acceptance must prove for each evaluated Candidate-v2 solver where available:

```text
raw evidence
→ fit
→ persisted derived state
→ reload
→ equivalent prediction
```

and:

```text
delete/recompute derived candidate state
→ replay raw evidence
→ equivalent output within solver tolerance
```

Deterministic and approximate solver tolerances are reported explicitly rather than conflated.

Native full backup/restore must preserve raw evidence, prescription state and candidate-derived rows without requiring a new special-case backup format.

## 14. N-BIO Adaptive Inference Acceptance

One Biological Developer action performs the current physical gate against installed Room14 history.

It exports a privacy-bounded JSON containing:

- Room/app/device identity;
- Candidate-v1 frozen status;
- Candidate-v2 mathematical identity;
- dense/Laplace solver identities;
- profile/side counts;
- historical whole-session solver bake-off;
- CRPS/PIT/WIS/coverage/bias/MAE/catastrophic-contradiction metrics;
- current posterior-fidelity comparison;
- solver runtime diagnostics;
- persistence/reload and replay results;
- memory/process snapshots;
- raw-evidence/prescription/benchmark-authority fingerprints;
- Native backup round-trip;
- separately labelled generic solver-substrate benchmark;
- limitations.

The generic solver-substrate benchmark compares dense sequential grid, adaptive sparse grid and Gaussian sigma-point inference on one shared controlled dynamic problem and screens the dense posterior for low-rank structure. It is labelled synthetic feasibility evidence and must not be presented as Candidate-v2 validation.

## 15. Numerical backend policy

Profile actual model kernels before porting them.

The current ladder is:

```text
algorithmic/sequential reuse
→ Kotlin/JVM
→ native C++ scalar
→ compiler-vectorised / NEON-friendly C++
→ multicore/tiled native
→ Vulkan compute for a proven highly parallel hotspot
→ LiteRT/NPU only if a future solver genuinely maps to supported model/delegate operations
```

Do not optimise backup/restore or acceptance harness work and claim model inference improved.

The current platform audit is documented in [`INFERENCE_BACKEND_AUDIT.md`](./INFERENCE_BACKEND_AUDIT.md).

No C++/Vulkan/LiteRT implementation is justified until the installed acceptance isolates a material model-compute hotspot and gives a JVM/algorithmic baseline.

## 16. Factor/dependency decision

N-BIO currently uses a minimum dependency/invalidation abstraction rather than importing a literal factor-graph library.

This is sufficient to represent downstream invalidation after source correction, model-version reanalysis and semantic boundaries. iSAM2 or another factor-graph implementation must earn itself against the actual one-sided/heavy-tail/hierarchical/policy-conditioned problem.

## 17. Context boundary

Candidate v1 and Candidate v2 consume:

`NONE`

for context.

Sleep, HRV, illness, stress, Nano output and review notes cannot alter their evidence, likelihood, trajectory or verdict.

A future context-consuming candidate requires a new immutable identity and independent validation.

## 18. Forward biological-state rule

Do not force `SkillState`, `Development`, `Fatigue` or `Recovery` into the capability model because persistence interfaces or older planning named them.

First learn the minimum predictive statistical state. Only later, with richer evidence and held-out identifiability, may a statistical component earn one of those biological labels.

## 19. N-BIO-7B.X completion gate

N-BIO-7B.X closes only when the mission can make evidence-backed recommendations for:

1. mathematical capability/policy architecture;
2. production live solver;
3. dense/high-fidelity reference solver;
4. optional reanalysis/smoothing solver;
5. hardware backend;
6. remaining fresh-data uncertainties.

If installed-device evidence is still missing or insufficient, the correct candidate/backend verdict is `INCONCLUSIVE`, not an invented production choice.

N-BIO-7C has not started.
