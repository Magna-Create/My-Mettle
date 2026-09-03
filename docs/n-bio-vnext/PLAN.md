# My Mettle Native — N-BIO vNext Execution Plan

> **Authority:** This is the forward N-BIO vNext implementation contract.
>
> The complete pre-adaptive plan is preserved verbatim as [`PLAN_LEGACY_PRE_ADAPTIVE.md`](./PLAN_LEGACY_PRE_ADAPTIVE.md). It remains useful historical planning, but assumptions superseded below are not forward requirements.
>
> Read this document with [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md). The adaptive-inference document is an authoritative additive supplement for mathematical-model, inference-solver and backend architecture. [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md) is an authoritative additive supplement for the future N-BIO-7E context-feature/module/associative-learning architecture. [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md) remains authoritative for collaboration/research/Figma and product-safety gates.

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
7. **Context is typed, modular and gated.** Current N-BIO-7B Candidate-v2 and N-BIO-7D consume `NONE`; sleep/HRV/illness/stress/notes cannot be used to rescue them. Future N-BIO-7E context consumption must flow through versioned `ContextFeature`/`ContextModule` contracts with module-owned replayable memory, explicit allowed inputs/targets and standard uncertainty-aware signals. No tag may directly mutate N-BIO core state merely because it exists.
8. **Derived inference is rebuildable.** Deleting candidate state cannot cascade into raw evidence.
9. **Product authority is explicit.** SHADOW/CANDIDATE runs do not alter normal prescriptions, workout UX or `BENCHMARK_V0` authority.
10. **Room migrations require semantics, not convenience.** Room14 remains sufficient for the completed 7B.X model/solver state, the completed N-BIO-7C structural/pre-validation mission and the implemented N-BIO-7D SHADOW pipeline; PD-001/PD-002 remain explicit deferred empirical-calibration boundaries where applicable.
11. **Backup/replay are acceptance gates.** Full Native backup remains generic; model/solver/module state must reload and raw evidence must replay within documented tolerance.
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

Context follows the same rule. An extracted tag is not automatically a biological modifier. N-BIO-7E may let a feature-specific module learn a user-specific predictive association with transient state, observation reliability, recovery dynamics or another explicitly allowed target, but the module must publish uncertainty-aware evidence to N-BIO Core rather than directly write a biological state. Association is not automatically causation.

`SetDemand`, `Exposure`, `EffectiveDose` and `SessionDose` now exist as N-BIO-7D SHADOW/candidate derived state under their own contract and PD-002 quarantine. They remain non-authoritative and must not be treated as calibrated physiology merely because the structural pipeline exists.

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

ContextModule learning follows the same maturity discipline: direct personal context episodes should dominate generic priors as evidence accumulates, repeated rows from one episode must not masquerade as independent evidence, and unsupported modules remain broad/neutral rather than producing dramatic user-specific effects.

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

The same principle applies to future context modules: interpreter/extractor certainty is distinct from the module's predictive evidence maturity, and neither may be silently relabelled as biological certainty.

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

Solver selection remains empirical per mathematical model. For the completed N-BIO-7B.X Candidate-v2 decision specifically, **Adaptive Sparse is selected for forward inference, Dense is retained as the deterministic high-fidelity reference/oracle, and Conditional Laplace is rejected as the production solver while remaining available only as a diagnostic/research challenger where useful.** This solver/backend decision does not promote Candidate-v2 mathematics to normal product authority.

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

Future ContextModules must be judged similarly where their output is predictive: compare a context-free baseline with the context-aware candidate chronologically. A tag does not earn state influence because it is intuitive, common or confidently extracted.

## 12. N-BIO-7B record and completed 7B.X mission

### Candidate v1

Candidate v1 is frozen as `REJECTED_EMPIRICAL_CALIBRATION_V1`.

Preserve it and its tests. Do not retune/mutate the same immutable candidate after seeing its real-history failure.

The important retained discovery is that its latent capability frontier could be better centred than its ordinary-demonstration predictive. This is direct motivation for capability/action-policy separation.

### Candidate v2

Candidate v2 is the bounded development experiment that added a neutral statistical frontier trajectory to the frozen v1 base. Its trajectory is not labelled biological development.

During 7B.X development, the same-mathematics comparison used the Dense full-support trend-grid reference and Conditional-Laplace approximation, while Adaptive Sparse was validated against Dense as the forward representation. The corrected physical acceptance established the final solver decision: Adaptive Sparse remains sufficiently faithful for forward Candidate-v2 inference with an explicitly recorded difficult-history upper-tail limitation; Dense remains the high-fidelity oracle; Conditional Laplace correctly fails closed on unstable projections but is not robust enough for production use.

Generic sequential dense-grid, low-rank-screen and Gaussian sigma-point implementations remain solver-architecture substrates. They are not automatically Candidate-v2-equivalent solvers merely because they share an interface; a Candidate-v2 comparison is valid only when mathematical identity, priors, transition, likelihood, horizon and observations are genuinely identical.

### N-BIO-7B.X — Adaptive Inference Architecture Consolidation

**Status: COMPLETE.** Do not invent 7B.5/7B.6/7B.7 roadmap phases.

The completed mission covers Candidate-v2 closure, capability/policy separation, dynamic-state contracts, semantic regimes, hierarchy contracts, solver competition, proper-scoring evaluation, persistence/replay, performance profiling and the production/reference/reanalysis/backend recommendation. Its physical checkpoint is preserved in [`NBIO_7BX_ADAPTIVE_ACCEPTANCE_CHECKPOINT_2026-09-01.md`](./NBIO_7BX_ADAPTIVE_ACCEPTANCE_CHECKPOINT_2026-09-01.md).

N-BIO-7C is a separate subsequent mission and must not be retroactively folded into 7B.X.

## 13. Completed N-BIO-7C device acceptance gate

The consolidated Biological Developer action used for physical 7C structural closure was:

**Run N-BIO 7C Capability Acceptance**

It operates on installed Room14 data for `LOADED_HOLD`, `DURATION_ONLY` and `REPEATED_CONTRACTION` and exports one privacy-bounded structural/pre-validation JSON containing:

- family/model/solver identities;
- exact profile-version/side evidence counts and exclusions;
- synthetic latent-truth pre-validation;
- Adaptive-Sparse versus Dense posterior/query fidelity where evaluable;
- persistence/reload, delete-derived and deterministic full-replay checks;
- per-stage runtime diagnostics;
- raw-evidence, prescription-state and `BENCHMARK_V0` authority fingerprints;
- Native Room14 backup/restore candidate-row verification and foreign-key integrity;
- explicit limitations, structural verdict and PD-001 empirical-accuracy status.

The physical gate established structural closure only. Under PD-001, insufficient real longitudinal evidence remains `EMPIRICAL_ACCURACY_PENDING` / `NOT_EVALUATED_REAL_HISTORY`; synthetic recovery or structural success must not be relabelled as empirical human calibration. The accepted physical checkpoint is preserved in [`NBIO_7C_CAPABILITY_ACCEPTANCE_CHECKPOINT_2026-09-02.md`](./NBIO_7C_CAPABILITY_ACCEPTANCE_CHECKPOINT_2026-09-02.md).

The earlier **N-BIO Adaptive Inference Acceptance** action is retained as historical/developer evidence for the completed 7B.X mission, not an active product gate.

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

Room remains **14** through the implemented N-BIO-7D SHADOW demand/dose pipeline unless a genuine semantic impossibility is demonstrated.

Reuse existing inference run/model config/manifest/capability/parameter-state storage where semantically sufficient.

Solver/model/module parameter codecs require:

- explicit schema/version;
- deterministic encode/decode where claimed;
- unknown versions fail closed;
- explicit mathematical-model and solver/module identity;
- frozen v1 state remains readable;
- Candidate-v2 state remains readable;
- no duplication of raw evidence as derived state.

For deterministic solvers/modules, replay equivalence must be explicit. Approximate solvers/modules must document both replay behaviour and approximation tolerance.

Future ContextModule memory and ContextSignals are derived state. They must be deletable/rebuildable from authorised canonical evidence plus declared upstream versions and must never become a hidden non-replayable memory store.

## 16. Factor/dependency architecture

N-BIO retains the minimum explicit dependency/invalidation abstraction required for:

- local invalidation after correction;
- semantic boundaries;
- model-version reanalysis;
- context reannotation/module-model reanalysis;
- incremental recomputation.

A literal factor-graph/iSAM2 dependency is not required unless future posterior/factor structure demonstrates a material benefit.

## 17. Context / Health boundary

Current Candidate-v2 context consumption remains `NONE`. N-BIO-7D also does not add note/sleep/HR/HRV context consumption.

Do not use sleep, HRV, illness, stress, Nano interpretations or review notes to repair Candidate v1/v2 or retroactively justify 7D.

Future separately versioned models may test typed exogenous evidence only after independent evidence and privacy/type-boundary review.

### 17.1 N-BIO-7E ContextModule platform

N-BIO-7E is the first phase that must provide a mathematical path from typed context evidence into state inference. It must do this through the modular architecture in [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md), not through one giant hard-coded tag coefficient table.

Required shape:

```text
ContextFeatureDefinition
        ↓
ContextFeatureEvidence / ContextEvidenceView
        ↓
stateful versioned ContextModule
        ↓
module-owned replayable memory + learner
        ↓
ContextSignal
        ↓
N-BIO Core arbitration / context-conditioned state
```

Each module may read explicitly authorised typed N-BIO evidence/state summaries to improve its own learner. Each module may use a different internal model where semantics justify it. Each module must publish through a common signal protocol and may not directly mutate core latent state.

This allows new tags/features to be added over time, allows their schemas to evolve through explicit versions, and allows feature-specific associative learning without hard-coding every context concept into N-BIO Core.

The core owns combination/arbitration because modules can be correlated or redundant. `ILLNESS`, `POOR_SLEEP` and `LOW_ENERGY`, for example, must not be naively added as three independent penalties.

Context modules learn predictive associations, not automatic causal effects. Unsupported tags remain broad/neutral. A confidently extracted tag has not earned biological influence until its module has evidence.

Some modules may infer temporal episodes/persistence so a Monday illness report can remain relevant later in the week without copying the raw annotation onto future sessions. Such episode state is derived, uncertain and replayable.

Execution/equipment context is not merely an observation-validity flag. A machine/grip/ROM difference may later explain performance offsets, variance or recruitment/translation differences through permitted domain-specific modules, while canonical execution/equipment metadata remains authoritative and text-extracted warnings never silently rewrite it.

### 17.2 Nano boundary

The invariant remains:

> Nano extracts structured context. N-BIO decides what context means mathematically.

Nano is one producer of typed evidence. It does not learn the user's biological/context association, does not receive unrestricted N-BIO history and does not output recovery penalties, performance modifiers or programme actions.

### 17.3 N-BIO-9 boundary

N-BIO-9 remains the later product/integration phase for broader context UX, additional producers such as Health Connect/wearables, Nano rollout/reannotation controls, recomputation lifecycle, dashboards/notifications and analysis export.

It must plug those sources into the 7E context-module protocol rather than inventing a parallel context-learning architecture. N-BIO-9 no longer owns creation of the core context-learning platform.

## 18. Forward N-BIO roadmap

```text
N-BIO-6   complete foundation — generic scalar + temporal evidence/provenance
N-BIO-7A  complete — probabilistic/provenance foundation
N-BIO-7A.5 complete — context interpretation + exercise-authoring boundaries
N-BIO-7B.1 complete — dynamic-resistance evidence/coordinate contract
N-BIO-7B.2 Candidate v1 frozen/rejected
N-BIO-7B.3/4 historical validation + installed-history acceptance infrastructure complete
N-BIO-7B.X complete — corrected adaptive-inference consolidation; Sparse selected, Dense oracle retained, Laplace rejected production
N-BIO-7C   complete structural/pre-validation — loaded-hold, duration-only and repeated-contraction contracts accepted physically; numerical outputs remain SHADOW under PD-001
PD-001     OPEN — empirical human calibration postponed where longitudinal evidence is insufficient; downstream quarantine remains binding
N-BIO-7D   implementation complete — SetDemand/Exposure/EffectiveDose/SessionDose SHADOW pipeline implemented; consolidated installed-device physical acceptance still pending; PD-002 OPEN
PD-002     OPEN — 7D empirical SetDemand/EffectiveDose calibration remains quarantined from structural success
N-BIO-7E   NOT STARTED — next state phase; context-module/associative-learning platform is a mandatory infrastructure requirement, not optional metadata enrichment
N-BIO-7F   later cross-profile/equipment translation under dedicated equipment contracts
N-BIO-7G   later conditioning capability
N-BIO-7H   later replay/validation closure where retained by the active roadmap
N-BIO-8    later constrained programme-resolution/decision layer; collaboration gates mandatory
N-BIO-9    later context/Health/product integration and recomputation lifecycle; must consume the 7E context protocol
Native Cutover last
```

N-BIO-7E must establish acute/transient/recovery state and the generic context-association substrate without silently starting N-BIO-8 coaching policy. Later Skill/Development naming remains evidence-dependent rather than mandatory merely because persistence scaffolding exists.

Cross-profile translation, equipment intelligence, conditioning, broader Health Connect/HR integration and product-facing adaptive programme behaviour remain later phases under their own gates.

## 19. N-BIO-8 / V8 direction

Do not freeze V8 as model-free reinforcement learning.

The leading architecture is **constrained Bayesian decision-making**, with stochastic/robust Model Predictive Control a leading multi-step option once N-BIO has earned a credible action→state transition model.

N-BIO-8 should preferentially consume N-BIO's combined context-conditioned state/prediction rather than contain a second ad-hoc per-tag learner or a growing hard-coded table such as `ILLNESS → reduce load`.

No V8 prescription behaviour is implemented merely by establishing the 7E context platform.

`PRODUCT_ROADMAP_GATES.md` collaboration/research/Figma requirements remain mandatory before behaviour-driving programme-resolution work.

## 20. Global completion rule

An N-BIO phase/candidate is not complete because code compiles. Closure requires the relevant combination of:

- immutable identity;
- mathematical specification;
- synthetic recovery/invariants;
- retrospective development evaluation;
- fresh prequential evidence where required;
- solver/module fidelity diagnostics;
- performance/RAM/device evidence;
- persistence/replay;
- backup safety;
- Room/schema verification;
- product-authority invariants;
- documentation;
- exact-head CI.

For N-BIO-7E specifically, structural closure also requires proving that context modules are truly modular/stateful/replayable, new features do not require feature-specific branching in N-BIO Core, module scope/missingness/temporal semantics are enforced, correlated signals are not naively double-counted, and context-aware candidates can be compared against context-free baselines.

When empirical evidence is insufficient, the correct result is `INCONCLUSIVE`/deferred—not invented certainty.
