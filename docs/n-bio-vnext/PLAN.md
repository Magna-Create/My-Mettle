# My Mettle Native — N-BIO vNext Execution Plan

> **Authority:** This is the forward N-BIO vNext implementation contract.
>
> The complete pre-adaptive plan is preserved verbatim as [`PLAN_LEGACY_PRE_ADAPTIVE.md`](./PLAN_LEGACY_PRE_ADAPTIVE.md). It remains useful historical planning, but assumptions superseded below are not forward requirements.
>
> Read this document with [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md). The adaptive-inference document is an authoritative additive supplement for mathematical-model, inference-solver and backend architecture. [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md) remains authoritative for collaboration/research/Figma and product-safety gates.

## 1. Purpose

N-BIO exists to turn immutable training/performance evidence into versioned, uncertainty-aware state that may later support safe programme decisions. The system must remain capable of changing its models without rewriting historical facts.

The architectural direction remains:

```text
canonical anatomy / reference physiology
                ↓
exercise + immutable/versioned execution semantics
                ↓
immutable scalar + temporal performance evidence
                ↓
semantic regime / profile continuity
                ↓
versioned probabilistic inference
                ↓
decision-relevant state + uncertainty
                ↓
exercise-independent programme intent
                ↓
constrained programme/session resolution
                ↓
immutable historical prescription/session snapshot
```

Native does not become authoritative merely because one candidate inference model exists. Product authority changes only through explicit accepted gates.

## 2. Decision and learning boundaries

Use the existing decision labels:

- **STRUCTURAL** — architecture/data requirement independent of a particular equation;
- **RESEARCH-BACKED** — direction supported strongly enough to encode;
- **MODELLING-ASSUMPTION** — replaceable/versioned computational choice;
- **PRODUCT-POLICY** — explicit application policy rather than physiology;
- **DEFER** — preserve hooks/interfaces without behaviour-driving implementation;
- **DO-NOT-IMPLEMENT** — rejected for the current generation.

Never promote a modelling assumption to biological fact.

### Level 1 — personal adaptation

A fixed promoted model may update user/profile latent state, parameters, hyperparameters, uncertainty and future validated action-policy behaviour as new evidence arrives.

### Level 2 — model improvement

Changes to equations, latent dimensions, priors, likelihoods, pooling, regime handling or solver semantics require a new immutable model/config identity, validation and explicit promotion. The deployed app never silently mutates its own architecture.

## 3. Non-negotiable invariants

1. **Raw evidence is canonical and immutable.** Corrections append/supersede rather than destructively rewrite history.
2. **Historical semantics are versioned.** An inference model may reinterpret derived state, not retroactively change what equipment/profile/side/value was recorded.
3. **Unknown remains unknown.** Do not fabricate RIR, RPE, failure proximity, body context, equipment translation or missing metrics.
4. **Capability is not action policy.** What performance is plausibly available and what load/reps the user actually chooses/performs are distinct probabilistic questions.
5. **Capability is not automatically biology.** Statistical drift is not automatically development, skill, fatigue or recovery.
6. **Pool statistical behaviour, not physical capability.** Absolute resistance coordinates and profile/side capability remain local unless a separately validated translation model exists.
7. **Context is typed and gated.** Current N-BIO-7B dynamic candidates consume `NONE`; sleep/HRV/illness/stress/notes cannot be used to rescue them.
8. **Derived inference is rebuildable.** Deleting candidate state cannot cascade into raw evidence.
9. **Product authority is explicit.** SHADOW/CANDIDATE runs do not alter normal prescriptions, workout UX or `BENCHMARK_V0` authority.
10. **Room migrations require semantics, not convenience.** Room14 remains sufficient for current 7B.X model/solver state.
11. **Backup/replay are acceptance gates.** Full Native backup remains generic; model/solver state must reload and raw evidence must replay within documented tolerance.
12. **Collaboration gates remain mandatory.** `PRODUCT_ROADMAP_GATES.md` is not superseded by backend capability.

## 4. Corrected forward state architecture

The old plan presented `SkillState`, `Development`, `Fatigue` and `Recovery` as an unconditionally required mathematical decomposition. That is **superseded**.

The forward rule is:

> Learn the minimum predictive statistical state first; earn biological interpretation later.

Initially admissible state concepts include neutral quantities such as:

```text
profileLocalCapability
stateLevel / frontierState
trajectory / drift
persistentComponent
transientComponent
processVariance / volatility
observationVariance
semanticRegime
```

Candidate mathematical families may include:

```text
stationary
strongly-shrunk linear trend
random walk
local linear trend
robust state-space
switching/changepoint
hierarchical dynamic state
```

No family is authoritative by naming convention.

Later evidence may test whether statistically identifiable components deserve biological interpretation as:

```text
SkillState
Development
Fatigue
Recovery
```

Those names remain useful future semantic destinations/interfaces, but their presence in persistence/domain scaffolding does not require an inference candidate to populate them. Health Connect or HR correlation alone does not prove a latent component is recovery or fatigue.

Likewise, `SetDemand`, `Exposure`, `EffectiveDose` and downstream muscle-state redesign remain later work. N-BIO-7B.X does not implement them.

## 5. Capability and action/observation policy

Dynamic capability asks:

> What performance is plausibly available on this exact execution-profile version and side at this horizon?

The action/observation policy asks:

> Given capability, previous actions, current prescription, programme intent and feasible equipment constraints, what action is likely to be chosen/performed?

These must not silently share one distribution.

Candidate-v1's one-sided Half-Normal slack may remain useful as lower-bound capability evidence or nuisance distance from a frontier. What is rejected is the claim that generic frontier slack is automatically the user's working-set selection distribution.

The current action-policy implementation is intentionally `UNMODELLED`. No RIR/RPE or `% of maximum` is invented.

## 6. Semantic regimes

Dynamic inference operates only within a coherent semantic regime.

Known metadata boundaries—execution-profile version, equipment/setup, entry basis, resistance semantics, laterality or explicit migration correction—win over statistical smoothing and produce deterministic regime boundaries.

Unknown discontinuities may generate a derived changepoint/regime suspicion. Statistical suspicion never rewrites canonical history automatically.

The eventual authoritative Lite→Native migration may manually correct/split known historical semantic mistakes after explicit review. N-BIO-7B.X does not perform that migration cleanup.

## 7. Hierarchical personalisation

The hierarchy contract may compare:

```text
NO_POOLING
USER_LEVEL_WEAK_POOLING
SEMANTIC_FAMILY_WEAK_POOLING
PROFILE_SPECIFIC
```

Strictly local by default:

- absolute resistance capability;
- equipment-specific coordinate;
- side-specific capability;
- execution-profile semantic identity.

Possible future weakly poolable hypotheses include dimensionless rep slope, observation variability, process volatility, action-policy behaviour, outlier prevalence and selected nuisance parameters. None is enabled as a behavioural truth merely because the contract supports it. Semantic-family pooling requires an explicit versioned mapping and held-out evidence.

## 8. Parameter-level evidence maturity

Do not collapse support into a single confidence/maturity scalar.

Use parameter-level status such as:

```text
FIXED_BY_CONFIG
PRIOR_DOMINATED
PARTIALLY_LEARNED
DATA_INFORMED
```

plus explicit semantic-regime continuity and solver diagnostics. Different parameters may be at different evidence maturity simultaneously.

## 9. Mathematical model vs inference solver vs compute backend

N-BIO permanently separates:

### A. Mathematical model

Latent states, transitions, priors, likelihoods and action-policy relationships.

### B. Inference representation / algorithm

Dense/sparse tensors, low-rank factors, Gaussian moments, sigma points, Laplace, particles or mixtures.

### C. Compute backend

Kotlin/JVM, native C++, vectorised ARM CPU, multicore CPU, Vulkan compute or an accelerator path that genuinely supports the numerical graph.

A slow solver does not justify changing the model. A fashionable solver does not justify changing the mathematics.

## 10. Solver strategy

Dense deterministic tensor/grid inference remains the high-fidelity reference wherever tractable.

Forward solver candidates include:

```text
DENSE_TENSOR_REFERENCE
SEQUENTIAL_TENSOR
ADAPTIVE_SPARSE_TENSOR
LOW_RANK_TENSOR if viability is demonstrated
MOMENT_MATCHING / SIGMA_POINT
SEQUENTIAL_LAPLACE
PARTICLE / MIXTURE only where posterior structure requires it
```

Tensor inference may be sequential:

```text
posterior(k)
    ↓ transition
prior(k+1)
    ↓ new likelihood
posterior(k+1)
```

The production decision is empirical. Dense tensor may remain reference-only, a sparse tensor may become a rich solver, a Gaussian/Laplace approximation may become a live solver, or tensor inference may remain production if optimisation makes its scientific advantage worth the cost.

A dual-solver architecture is permitted only with explicit provenance/precedence so two posteriors cannot become contradictory co-authorities.

## 11. Evaluation contract

Champion/challenger evaluation is permanently prequential where possible:

```text
before session:
  freeze prediction for every candidate
session occurs
  score predictions
then update each candidate
```

Track at minimum where the predictive representation supports them:

- CRPS;
- log predictive score;
- PIT/reliability;
- WIS / interval score and sharpness;
- p05/p50/p95;
- variance/covariance/dependence;
- tail probabilities and asymmetry/multimodality where relevant;
- median/MAE as secondary point diagnostics;
- signed residual bias;
- catastrophic frontier contradictions;
- model availability/numerical failure;
- profile-level results.

Historical data already inspected during development remains development evidence. Fresh future workouts provide the strongest confirmatory evidence.

## 12. N-BIO-7B record and current mission

### Candidate v1

Candidate v1 is frozen as `REJECTED_EMPIRICAL_CALIBRATION_V1`.

Preserve it and its tests. Do not retune/mutate the same immutable candidate after seeing its real-history failure.

The important retained discovery is that its latent capability frontier could be better centred than its ordinary-demonstration predictive. This is direct motivation for capability/action-policy separation.

### Candidate v2

Candidate v2 is a bounded development experiment adding a neutral statistical frontier trajectory to the frozen v1 base. Its trajectory is not labelled biological development.

The same-mathematics solver bake-off currently compares the dense full-support trend-grid reference with the conditional-Laplace approximation. Both receive the same frozen-v1 proposal at a historical/current comparison cutoff.

Generic sequential dense-grid, adaptive sparse-grid, low-rank-screen and Gaussian sigma-point implementations are solver-architecture substrates. They are not Candidate-v2 solvers merely because they share an interface; a Candidate-v2 comparison is valid only when mathematical identity, priors, transition, likelihood, horizon and observations are genuinely identical.

### N-BIO-7B.X — Adaptive Inference Architecture Consolidation

This is one consolidated mission. Do not invent 7B.5/7B.6/7B.7 roadmap phases.

The mission covers Candidate-v2 closure, capability/policy separation, dynamic-state contracts, semantic regimes, hierarchy contracts, solver competition, proper-scoring evaluation, persistence/replay, performance profiling and a production/reference/reanalysis/backend recommendation.

Do **not** begin N-BIO-7C inside this mission.

## 13. Current device acceptance gate

The single Biological Developer action is:

**N-BIO Adaptive Inference Acceptance**

It operates on installed Room14 data and exports one privacy-bounded JSON containing:

- model/solver identities;
- profile/side counts;
- Candidate-v2 historical predictive bake-off;
- current posterior-fidelity comparison;
- persistence/reload and delete-derived/replay checks;
- solver runtime diagnostics;
- a separately labelled synthetic same-problem dense/sequential/sparse/sigma/low-rank substrate benchmark;
- raw-evidence/prescription/benchmark-authority fingerprints;
- Native backup round-trip;
- memory/process snapshots and limitations.

The synthetic solver-substrate benchmark is feasibility evidence, not Candidate-v2 validation.

## 14. Numerical backend policy

Profile before porting kernels.

The current order is:

```text
algorithmic/sequential reuse
→ Kotlin/JVM baseline
→ portable native C++ scalar
→ compiler-vectorised / NEON-friendly C++
→ native multicore/tiled execution
→ Vulkan for a measured data-parallel hotspot
→ LiteRT/NPU only if a future solver genuinely maps to supported model/delegate operations
```

Do not port backup/acceptance overhead and claim inference improved. Model compute and harness compute must be reported separately.

`INFERENCE_BACKEND_AUDIT.md` records the current Android platform conclusions.

## 15. Persistence and replay

Room remains **14** during 7B.X unless a genuine semantic impossibility is demonstrated.

Reuse existing inference run/model config/manifest/capability/parameter-state storage where semantically sufficient.

Solver/model parameter codecs require:

- explicit schema/version;
- deterministic encode/decode where claimed;
- unknown versions fail closed;
- explicit mathematical-model and solver identity;
- frozen v1 state remains readable;
- Candidate-v2 state remains readable;
- no duplication of raw evidence as derived state.

For deterministic solvers, replay equivalence must be explicit. Approximate solvers must document both deterministic replay behaviour and approximation tolerance.

## 16. Factor/dependency architecture

N-BIO retains the minimum explicit dependency/invalidation abstraction required for:

- local invalidation after correction;
- semantic boundaries;
- model-version reanalysis;
- incremental recomputation.

A literal factor-graph/iSAM2 dependency is not required unless future posterior/factor structure demonstrates a material benefit.

## 17. Context / Health boundary

Current Candidate-v2 context consumption remains `NONE`.

Do not use sleep, HRV, illness, stress, Nano interpretations or review notes to repair Candidate v1/v2.

Future separately versioned models may test typed exogenous evidence only after independent evidence and privacy/type-boundary review.

## 18. Forward N-BIO roadmap

```text
N-BIO-6   complete foundation — generic scalar + temporal evidence/provenance
N-BIO-7A  complete — probabilistic/provenance foundation
N-BIO-7A.5 complete — context interpretation + exercise-authoring boundaries
N-BIO-7B.1 complete — dynamic-resistance evidence/coordinate contract
N-BIO-7B.2 Candidate v1 frozen/rejected
N-BIO-7B.3/4 historical validation + installed-history acceptance infrastructure complete
N-BIO-7B.X ACTIVE — adaptive inference architecture consolidation
N-BIO-7C   NOT STARTED
later 7C–7G retain their intended product/domain destinations but must consume this corrected inference architecture
N-BIO-8   later constrained programme-resolution/decision layer; collaboration gates mandatory
N-BIO-9   later context/Health/intelligence/data integration
Native Cutover last
```

Later loaded-hold, duration-only, repeated-contraction, SetDemand, Exposure/Dose, Fatigue, Recovery, SkillState, Development, cross-profile translation, equipment intelligence, Health Connect and conditioning implementations remain outside 7B.X.

## 19. N-BIO-8 / V8 direction

Do not freeze V8 as model-free reinforcement learning.

The leading architecture is **constrained Bayesian decision-making**, with stochastic/robust Model Predictive Control a leading multi-step option once N-BIO has earned a credible action→state transition model.

No V8 prescription behaviour is implemented in 7B.X.

`PRODUCT_ROADMAP_GATES.md` collaboration/research/Figma requirements remain mandatory before behaviour-driving programme-resolution work.

## 20. Global completion rule

An N-BIO phase/candidate is not complete because code compiles. Closure requires the relevant combination of:

- immutable identity;
- mathematical specification;
- synthetic recovery/invariants;
- retrospective development evaluation;
- fresh prequential evidence where required;
- solver fidelity diagnostics;
- performance/RAM/device evidence;
- persistence/replay;
- backup safety;
- Room/schema verification;
- product-authority invariants;
- documentation;
- exact-head CI.

When empirical evidence is insufficient, the correct result is `INCONCLUSIVE`/deferred—not invented certainty.
