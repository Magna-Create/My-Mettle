# My Mettle Native — N-BIO Adaptive Inference Architecture Consolidation

> **Status:** Authoritative additive supplement to `PLAN.md` and `PRODUCT_ROADMAP_GATES.md` for N-BIO inference architecture, solver strategy, model-learning boundaries and the transition from N-BIO-7B toward later biological inference.
>
> **Authority rule:** Preserve all established raw-evidence, provenance, schema, backup, privacy, context and product-collaboration contracts. Where an older N-BIO plan section assumes a stationary/batch capability model, a single inference backend, or prematurely named biological latent states, this supplement governs the forward architecture.
>
> This document does **not** authorize N-BIO-7C, 7D, 7E, 7F, V8 or normal-user behaviour changes by itself. It defines the consolidated architecture that those phases must build upon.

---

## 1. Why this consolidation exists

N-BIO-7B Candidate v1 proved that the original stochastic-frontier direction was mathematically useful but incomplete.

The real-history acceptance pipeline showed that:

- the latent dynamic-resistance frontier could be approximately centred while the demonstration predictive remained systematically pessimistic;
- ordinary successful training sets are **policy-selected observations**, not random passive draws from a generic sub-frontier distribution;
- progressing profiles can outrun a locally stationary frontier;
- semantic discontinuities can masquerade as biological change unless profile/equipment/execution boundaries are explicit;
- deterministic tensor-grid inference is scientifically valuable but expensive in its current implementation;
- production inference architecture and mathematical model architecture must be treated as separate decisions.

Two independent cross-domain research passes converged on a structured probabilistic direction: hierarchical dynamic latent-state modelling, sequential inference, semantic/regime protection, explicit separation between capability and observed action policy, champion/challenger evaluation, and constrained planning later.

The research was directionally persuasive but too categorical in places, especially where it assumed that tensor inference must be discarded in favour of ADF/EKF-style approximations. This plan therefore separates:

```text
A. WHAT mathematical model is being inferred
B. HOW its posterior is approximated / represented
C. WHERE that computation executes
```

No inference backend wins by architectural decree.

---

## 2. Core architectural principle

N-BIO should become a **decision-relevant structured probabilistic learning system**, not a complete physiological simulator and not an end-to-end black-box predictor.

The model should represent only latent structure that materially improves:

- future performance prediction;
- uncertainty calibration;
- semantic continuity;
- later decision quality;
- transfer of appropriate user-specific statistical information;
- explanation of why an estimate is broad, narrow, stale or unstable.

This borrows the useful philosophy of latent world-model work without importing self-play, huge neural models, unconstrained exploration or opaque state representations.

---

## 3. Hard invariants retained

All existing N-BIO invariants remain in force, including:

- immutable raw evidence;
- reproducible/versioned derived inference;
- historical semantics vs current-model reinterpretation as distinct modes;
- no raw kg transfer between unrelated execution profiles;
- unknown remains unknown;
- weak evidence may produce broad/tentative output but never silent authority;
- context/Nano does not directly become biological inference;
- semantic metadata overrides statistical guessing when the real-world boundary is known;
- normal workout authority does not change merely because a candidate model exists;
- model/config identities are immutable after evaluation;
- Room schema must not change without genuine semantic necessity;
- Native backup and replay remain first-class acceptance gates.

---

## 4. Capability and action-policy must be separate quantities

The system must distinguish:

```text
CAPABILITY MODEL
"What performance is plausibly available on this exact execution profile?"

from

ACTION / OBSERVATION POLICY MODEL
"Given capability, programme intent, previous history, prescription and constraints,
 what load/reps/set behaviour is this user likely to choose or perform?"
```

Candidate v1 demonstrated why this matters: a capability frontier can be approximately centred even when a demonstration predictive is badly pessimistic.

### 4.1 Consequence for frontier slack

The existing one-sided slack variable is **not automatically deleted**.

A one-sided latent distance below the frontier may remain useful for capability inference because a successful ordinary set is lower-bound evidence, not a maximum.

What is rejected is the assumption that the same generic slack distribution is necessarily a valid predictive model of the user’s chosen working set.

Therefore future candidates may compare:

- one-sided capability slack retained solely as an inference nuisance variable;
- censored/lower-bound capability likelihoods with no explicit slack predictive;
- policy-conditioned slack;
- a separate explicit user action-policy distribution.

Do not force RIR/RPE semantics into the model without trustworthy evidence.

---

## 5. Dynamic latent capability

Stationary recent-window capability is no longer the target architecture.

Future capability models should support a dynamic profile-local latent state, starting from the minimum identifiable structure.

Candidate families may include:

```text
stationary frontier                (reference / rejected-v1 family)
strongly shrunk linear trend       (diagnostic challenger)
random-walk latent capability
local-linear trend / drift state
robust dynamic state-space model
changepoint-aware dynamic model
hierarchical dynamic state model
```

Do not equate a statistical trajectory with biological development, recovery, fatigue, skill or detraining.

A latent state may first mean only:

> "a component that improves prediction of profile-local capability over time."

Biological interpretation must be earned separately.

---

## 6. Semantic regime layer

Dynamic inference may only operate inside a semantically coherent regime.

### 6.1 Known boundaries

Explicit evidence such as:

- exercise/profile change;
- machine/equipment change;
- resistance semantics change;
- entry-basis change;
- unilateral/bilateral/laterality change;
- known ROM/technique-class change;
- explicit migration correction;

must create or resolve an explicit semantic boundary before statistical temporal inference.

The model must never explain a known Machine Chest Press → Incline Chest Press change as rapid biological adaptation merely because legacy history retained one identity.

### 6.2 Unknown boundaries

Only when metadata is insufficient may statistical methods such as:

- robust outlier probabilities;
- Bayesian online changepoint detection;
- switching-state models;
- mixture/regime posteriors;

be used to represent uncertainty that the generating regime changed.

A statistical changepoint is not automatically permission to rewrite canonical exercise history. It is derived inference unless later confirmed/corrected by explicit metadata.

---

## 7. Hierarchical personalisation / learning to learn

N-BIO should learn user-specific priors across profiles where parameters are genuinely exchangeable.

The guiding rule is:

> **Pool statistical behaviour, not physical capability.**

### 7.1 Strictly local by default

Keep profile/version/side local unless an explicit translation model exists:

- absolute capability frontier;
- physical resistance coordinate;
- equipment-specific mechanics;
- side-specific capability;
- profile-specific semantic boundaries.

### 7.2 Candidate poolable quantities

Possible weakly pooled quantities include:

- dimensionless rep-range slope priors;
- observation variability;
- action-policy tendencies;
- process/state volatility;
- robust-noise/outlier prevalence;
- selected nuisance-distribution parameters.

These are hypotheses, not facts.

Compare at least:

```text
no pooling
weak user-level pooling
semantic-family-conditioned pooling
profile-specific deviation from pooled prior
```

Do not hard-code exercise-family pooling merely because two exercises appear similar.

### 7.3 Evidence maturity

Do not create a single magic `EvidenceMaturity = 0.73` score.

Prefer parameter-level identification and posterior support, e.g.:

```text
frontier        DATA_INFORMED
slope           PRIOR_DOMINATED
trajectory      PARTIALLY_LEARNED
policy          WEAKLY_POOLED
semantic regime VERIFIED
```

Posterior uncertainty and identifiability should carry most of the maturity semantics.

---

## 8. Future biological states must be earned

The older conceptual decomposition into explicit states such as:

- `SkillState`;
- `Development`;
- `Fatigue`;
- `Recovery`;

must not be treated as mathematically required merely because the words are biologically meaningful.

Before behaviour-driving implementation, later phases must test whether available evidence can actually identify separate latent components.

A safer progression is initially statistical:

```text
profile-local capability state
slow/persistent latent component
fast/reverting latent component
trajectory / volatility
```

Only after richer evidence and held-out validation should any component earn a biological interpretation.

Even Health Connect, sleep, HR/HRV or timing correlation does not by itself prove that a latent variable is "Recovery" or "Fatigue".

---

## 9. Inference backend competition — do not prematurely discard tensor inference

The mathematical model and the inference backend are independent.

N-BIO must retain a solver hierarchy rather than declare one algorithm authoritative by fashion.

### 9.1 Reference solver

The current deterministic tensor-grid engine remains scientifically valuable as a reference implementation where tractable.

Its purposes include:

- high-fidelity deterministic posterior reference;
- synthetic parameter recovery;
- approximation-validation oracle;
- regression/replay verification;
- detecting whether a fast approximation collapses asymmetry, covariance, tails or multimodality.

Do not delete or architecturally obsolete it during this consolidation.

### 9.2 Candidate production solvers

Benchmark multiple backends against the **same mathematical model**:

```text
A. dense deterministic tensor/grid reference
B. adaptive/sparse-grid deterministic quadrature
C. low-rank/tensor-train representation if posterior structure supports it
D. ADF / moment-matched sequential filtering
E. EKF/UKF/sigma-point filtering where assumptions fit
F. sequential Laplace
G. Rao-Blackwellised / particle or mixture approaches only where required
```

Do not compare different solvers while silently changing priors, likelihoods or latent equations.

### 9.3 Sequential tensor inference is allowed

Tensor/grid inference does not have to mean full-history refit.

A valid architecture may perform:

```text
posterior(k)
    ↓ state transition
prior(k+1)
    ↓ multiply by new likelihood
posterior(k+1)
```

with the posterior represented by a grid, sparse grid or compressed tensor.

The production competition is therefore not "batch tensor vs online filtering". It includes online tensor/sparse-tensor filtering.

---

## 10. Solver evaluation contract

For a fixed mathematical candidate, compare solver backends on:

### Scientific fidelity

- posterior quantiles vs reference;
- posterior moments;
- covariance/dependence retention;
- tail probability error;
- multimodality/asymmetry preservation where present;
- prediction distribution divergence;
- replay determinism.

### Predictive usefulness

- CRPS;
- log score where supported;
- PIT/reliability;
- interval coverage;
- interval sharpness / WIS;
- signed bias;
- catastrophic frontier contradictions;
- profile-level results, not only global average.

### Engineering

- cold full-replay runtime;
- incremental-session update runtime;
- peak RAM;
- persistent-state size;
- battery/energy proxy where measurable;
- thermal behaviour for long developer runs;
- deterministic behaviour across supported devices;
- implementation/maintenance complexity.

A more exact solver does not win merely because it is exact.
A faster solver does not win merely because it is fast.

Production selection requires a material end-to-end advantage.

---

## 11. Hardware/backend optimisation ladder

Do not jump directly from Kotlin loops to NPU assumptions.

Benchmark the same numerical workload through a staged ladder:

```text
1. current Kotlin/JVM implementation
2. algorithmic reuse / incremental update
3. native C++ scalar
4. compiler-vectorised / ARM NEON-friendly native implementation
5. multicore tiled native implementation
6. Vulkan/mobile-GPU compute for genuinely data-parallel kernels
7. NPU/LiteRT-style paths only if the operation graph genuinely maps to supported acceleration
```

Hardware acceleration is subordinate to algorithmic suitability.

### 11.1 GPU candidate

Tensor likelihood evaluation and reductions are plausible GPU candidates because many parameter states execute identical maths in parallel.

Benchmark rather than assume:

- dispatch overhead;
- CPU↔GPU transfer;
- memory bandwidth;
- precision;
- deterministic reductions;
- thermal throttling;
- device fragmentation.

### 11.2 NPU caveat

Do not assume a mobile neural accelerator is a generic Bayesian-compute coprocessor. Only use NPU/LiteRT paths where the actual supported op graph maps cleanly.

---

## 12. Factor graphs and incremental recomputation

Borrow the factor-graph **decomposition** and incremental-update ideas from robotics without prematurely committing to iSAM2 itself.

The future architecture should make dependencies explicit enough that:

- adding one session updates only affected state where possible;
- correcting one old observation invalidates/recomputes the affected downstream trace rather than the entire database;
- model-version reanalysis can replay deterministically;
- semantic boundaries partition state histories cleanly.

Whether the implementation uses a literal factor-graph library, custom message passing, sequential filtering plus replay, or another representation is an empirical engineering decision.

---

## 13. Evaluation becomes permanently prequential

Candidate promotion must use immutable champion/challenger model identities.

The system should increasingly operate as:

```text
before session k:
    Champion predicts
    Challenger predicts
    freeze predictions

session k occurs
    score both
    only then update both
```

Track:

- CRPS as a primary proper scoring rule where a full predictive distribution exists;
- PIT/reliability;
- WIS/interval sharpness;
- median/MAE as secondary point diagnostics;
- availability/unknown rate;
- numerical failure rate;
- profile-level and global summaries.

Previously inspected history may be used for model development but must not be relabelled as pristine confirmatory evidence.

Fresh future workouts become the strongest promotion evidence.

---

## 14. Level-1 vs Level-2 learning

Separate two kinds of learning permanently.

### Level 1 — personal adaptation

Fixed mathematical/model architecture.

New evidence updates:

- latent state;
- user-level hyperparameters;
- profile-specific parameters;
- uncertainty;
- policy behaviour.

This may happen continuously on-device.

### Level 2 — model improvement

Changes:

- latent-state definitions;
- priors;
- likelihood families;
- pooling structure;
- changepoint model;
- inference approximation/backend.

These changes require:

- new immutable model/config identity;
- offline/synthetic + retrospective challenger evaluation;
- fresh prequential confirmation where appropriate;
- explicit promotion.

The deployed app must never silently rewrite its own mathematical structure.

---

## 15. Offline model laboratory

A future development/research system may generate and benchmark challenger models automatically.

Possible search dimensions:

- state transition family;
- observation policy family;
- robust-noise family;
- pooling structure;
- changepoint model;
- posterior approximation;
- numerical/hardware backend.

Candidate generation may use Bayesian optimisation, evolutionary search, program synthesis or other automated methods.

But promotion remains gated:

```text
automated candidate generation
        ↓
synthetic identifiability/recovery
        ↓
historical development evaluation
        ↓
fresh/shadow prequential evidence
        ↓
human/engineering review
        ↓
immutable promoted model
```

No on-device autonomous model-architecture mutation.

---

## 16. V8 direction

Do not freeze V8 as reinforcement learning.

The leading direction is:

> **constrained Bayesian decision-making, with stochastic/robust Model Predictive Control as the leading architecture once a credible multi-step transition model exists.**

N-BIO should answer:

- where are we;
- what can plausibly happen;
- how uncertain are we;
- what changes under candidate actions.

V8 should combine that with:

- programme intent;
- priorities;
- user preferences;
- equipment feasibility;
- hard scientific/product constraints;
- uncertainty-aware utility.

Active learning / value of information may only be a secondary/tie-break objective among already reasonable training actions.

The existing required human research/Figma collaboration gate before behaviour-driving V8 remains unchanged.

---

## 17. Consolidated implementation mission

Rather than splitting this architecture transition into many tiny roadmap phases, execute one consolidated engineering/research mission after the current bounded Candidate-v2 experiment is brought to a coherent stopping point.

Call the mission:

# **N-BIO-7B.X — Adaptive Inference Architecture Consolidation**

It should cover, in one branch mission with checkpoint/resume as needed:

1. preserve/finalise Candidate-v1 rejection record;
2. finish the current Candidate-v2 bounded experiment honestly;
3. codify capability-vs-action-policy separation;
4. introduce a model-agnostic dynamic-state inference contract;
5. introduce semantic-regime boundaries/interfaces;
6. introduce hierarchical user/profile prior contracts without forcing unvalidated pooling;
7. preserve the dense tensor solver as reference;
8. implement at least one credible sequential approximate solver;
9. investigate/implement adaptive or sparse tensor inference where practical;
10. investigate low-rank/tensor-train viability rather than assuming it;
11. build a solver bake-off using identical mathematical candidates;
12. build native/SIMD acceleration for hot numerical kernels where justified by profiling;
13. prototype Vulkan compute for tensor likelihood/reduction only if CPU/native profiling leaves a meaningful gap;
14. persist/replay solver/model identities cleanly without making candidate state authoritative;
15. retain Room14 unless a genuine semantic contradiction is proven;
16. extend prequential CRPS/PIT/WIS champion/challenger evaluation;
17. leave clear empirical selection criteria for production solver/model promotion;
18. revise later N-BIO roadmap language so Skill/Development/Fatigue/Recovery are hypotheses to be earned rather than predetermined latent variables;
19. preserve PRODUCT_ROADMAP_GATES collaboration requirements;
20. STOP before N-BIO-7C behaviour implementation.

This is intentionally one fat architecture mission, not twenty disconnected implementation tickets.

---

## 18. Completion criteria for the consolidation mission

The mission is complete when:

- Candidate-v1 remains frozen/rejected and reproducible;
- Candidate-v2 has a truthful documented verdict;
- mathematical-model contracts are separated from inference-backend contracts;
- capability inference is architecturally distinct from action-policy prediction;
- dynamic-state and semantic-regime contracts exist;
- hierarchical pooling is possible but no unsupported pooling rule is silently authoritative;
- dense tensor inference remains available as a reference solver;
- at least one fast sequential solver exists for comparison;
- at least one richer deterministic efficiency path (adaptive/sparse grid or equivalent) has been meaningfully evaluated;
- tensor-train/low-rank viability has been measured or explicitly ruled out with evidence, not assumption;
- solver comparisons use the same mathematical candidate;
- posterior-fidelity and predictive metrics are both recorded;
- runtime/RAM are measured on realistic fixtures;
- hardware optimisation claims are benchmark-backed;
- normal workout authority remains unchanged;
- context consumption remains explicitly controlled;
- Room/backups/replay remain safe;
- docs truthfully reflect what is proven vs provisional;
- N-BIO-7C has not started.

The result should tell us not only **which mathematical candidate is better**, but also **which inference representation/backend earns production use and why**.
