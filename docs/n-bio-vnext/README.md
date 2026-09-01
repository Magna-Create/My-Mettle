# N-BIO vNext Documentation Map

Use this directory in the following order.

## Authority

1. [`PLAN.md`](./PLAN.md) — overarching phase/architecture/acceptance contract.
2. [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md) — authoritative additive supplement for N-BIO inference architecture, solver strategy, dynamic latent-state naming, capability-vs-action-policy separation, hierarchical personalisation, prequential evaluation and later decision architecture. Where older planning assumes stationary/batch capability, one universal inference backend, tensor inference as inherently non-sequential, predetermined biological latent-state labels, or capability and action selection as one distribution, this supplement governs.
3. [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md) — normative detailed N-BIO-7/N-BIO-8 mathematical and behavioural supplement except where superseded by the adaptive-inference supplement or later explicit PLAN corrections.
4. [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md) — authoritative additive product/research/collaboration gates for late N-BIO-7 equipment intelligence, uncertainty communication, N-BIO-8 research/UX design, N-BIO-9 recomputation lifecycle and Native database-safety behaviour. When it marks `COLLABORATION REQUIRED`, stop before behaviour-driving implementation and involve Kian rather than silently choosing product policy.

`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md` does not authorise N-BIO-7C, later biological-state implementations, V8 prescription behaviour or normal-user UX changes. It corrects the inference architecture used to reach those later gates.

## Core biological/performance research

5. [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) — implementation-facing evaluation/navigation.
6. [`RESEARCH_RAW.md`](./RESEARCH_RAW.md) — preserved core DeepResearch source.

## Temporal / Health Connect / conditioning / HR research

7. [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md) — implementation-facing evaluation/navigation.
8. [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md) — raw-report index and integrity metadata; links seven verbatim storage parts.

## Implementation contracts and notes

- [`TEMPORAL_FOUNDATION.md`](./TEMPORAL_FOUNDATION.md) — concrete N-BIO-6 temporal storage, scope, codec, revision and compatibility decisions. The live development database has since advanced to Room14; historical schema numbers in this document describe the stage at which the contract was introduced.
- [`DEVICE_VERIFICATION.md`](./DEVICE_VERIFICATION.md) — reproducible Termux build and isolated on-device scalar/temporal acceptance procedure.
- [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md) — N-BIO-7A.5 canonical-note ownership, bounded tag ontology, interpreter/fallback/privacy boundary, provenance and zero-effect N-BIO context interface.
- [`EXERCISE_AUTHORING_CONTRACT.md`](./EXERCISE_AUTHORING_CONTRACT.md) — authoritative protocol for externally proposed exercise/execution/recruitment semantics before they enter the existing Native authoring pathway.
- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen Candidate-v1 record, chronological validation, SHADOW persistence and Candidate-v2 development evidence. Read it together with the adaptive-inference supplement for current capability/policy and solver semantics.
- [`NON_DYNAMIC_CAPABILITY_CONTRACT.md`](./NON_DYNAMIC_CAPABILITY_CONTRACT.md) — normative N-BIO-7C loaded-hold, duration-only and repeated-contraction capability contract.
- [`POSTPONED_DEVELOPMENT.md`](./POSTPONED_DEVELOPMENT.md) — deferred-validation register; PD-001 permits 7C structural closure while explicitly quarantining unearned empirical claims.
- [`INFERENCE_BACKEND_AUDIT.md`](./INFERENCE_BACKEND_AUDIT.md) — current Android numerical-backend audit and profiling-first JVM/native/SIMD/Vulkan/LiteRT decision ladder.
- [`exercise-import.schema.json`](./exercise-import.schema.json) — Draft 2020-12 machine-readable structural exchange schema, format v1.
- [`exercise-import-example.json`](./exercise-import-example.json) — canonical validated authoring example.

## Current N-BIO-7 status

```text
7A      complete — probabilistic/posterior + model/config/inference provenance foundation
7A.5    complete — context interpretation + external exercise-authoring contracts
7B.1    complete — dynamic-resistance evidence eligibility + coordinate/model contract
7B.2    Candidate v1 frozen/rejected — implementation retained as historical/reference evidence
7B.3/4  complete as historical validation infrastructure — correction-aware chronology, SHADOW persistence, diagnostics, installed-history acceptance
7B.X    complete — Adaptive Sparse selected for forward Candidate-v2 inference; Dense retained oracle; Conditional Laplace rejected production
7C      ACTIVE — loaded-hold, duration-only and repeated-contraction structural/pre-validation; PD-001 empirical accuracy pending where evidence is insufficient
```

### Candidate-v1 record

Candidate v1 is frozen as `REJECTED_EMPIRICAL_CALIBRATION_V1`. The installed-history acceptance established a genuine empirical rejection: the latent capability frontier was materially better centred than the working-set demonstration predictive, while predictive calibration remained unacceptable. Preserve the model and its tests; do not mutate it to repair later candidates.

### Candidate-v2 status

Candidate v2 has been implemented as a bounded **development experiment**, not product authority. It adds a neutral statistical frontier trajectory to the frozen v1 base and deliberately does not label that trajectory development, recovery, fatigue, skill or biological growth.

The valid same-mathematics Candidate-v2 solver comparison currently consists of:

```text
DENSE full-support trend-grid reference
vs
CONDITIONAL-LAPLACE approximation
```

Both receive the same frozen-v1 proposal when directly compared. Generic dense-sequential-grid, adaptive-sparse-grid, low-rank-screen and Gaussian sigma-point implementations also exist as solver-architecture substrates, but they are not to be misreported as Candidate-v2-equivalent solvers unless a mathematical adapter makes their state/transition/likelihood problem genuinely identical.

The consolidated **N-BIO Adaptive Inference Acceptance** developer action is the current installed-Room14 evidence gate. It combines Candidate-v2 retrospective/current-state solver evidence with a separately labelled synthetic same-problem solver-substrate benchmark and exports one privacy-bounded JSON. Historical results remain development evidence; fresh future workouts are stronger confirmatory evidence.

## Product and safety authority during 7B.X

All current candidates remain **SHADOW / CANDIDATE** only.

- Normal workout/prescription authority remains `BENCHMARK_V0`.
- Context consumption remains `NONE` for the current dynamic candidate.
- No RIR/RPE value may be fabricated.
- Raw performance evidence remains immutable and separate from derived inference.
- Semantic metadata beats statistical changepoint suspicion when a real execution boundary is known.
- Room remains 14 unless a genuine semantic impossibility requires another migration.
- N-BIO-7C exercise-family capability work is not authorised by 7B.X.

Before behaviour-driving N-BIO-8 implementation, `PRODUCT_ROADMAP_GATES.md` still requires the dedicated research + product/UX design gate. Programme intent/priorities, in-workout adaptation, editing/regeneration semantics, equipment setup/feasible-load UX and uncertainty presentation require explicit collaboration.

N-BIO-9 remains the later product/integration phase for broader context UX, explicit reannotation/data controls, Nano rollout/download management, notification/dashboard consumers, Health Connect/HR experiments and Analysis Export. N-BIO-9 must additionally consult the recomputation/model-upgrade UX gates in `PRODUCT_ROADMAP_GATES.md`.

Native Cutover must consult the database compatibility, downgrade protection, migration-safety and recovery-UX gates in `PRODUCT_ROADMAP_GATES.md` before destructive migration is removed and the first permanent Native schema is declared.

## Reading rule

For inference work during N-BIO-7B.X:

```text
PLAN.md
→ ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md
→ relevant CORE_MODEL_DETAIL.md section where not superseded
→ DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md for current 7B evidence/model history
→ PRODUCT_ROADMAP_GATES.md whenever crossing a product/research collaboration gate
→ relevant research guide
→ targeted raw research only when exact evidence/equations/platform wording are needed
→ current source code and immutable model/config identities
```

For later non-inference work, start with `PLAN.md` and follow the relevant authority links above.

When `PRODUCT_ROADMAP_GATES.md` says `COLLABORATION REQUIRED`, do not silently infer a final UX/product policy from backend capability. Explicitly involve Kian before behaviour-driving implementation.

Do not load both full raw research bodies into ordinary implementation context.

Existing `docs/N_BIO_*.md` files remain historical implementation-stage documentation. The vNext authority stack above governs forward work where they conflict.

N-BIO-7C is the active consolidated capability-family mission; N-BIO-7D has not started.
