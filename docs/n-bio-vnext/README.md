# N-BIO vNext Documentation Map

Use this directory in the following order.

## Authority

1. [`PLAN.md`](./PLAN.md) — overarching phase/architecture/acceptance contract.
2. [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md) — authoritative additive supplement for N-BIO inference architecture, solver strategy, dynamic latent-state naming, capability-vs-action-policy separation, hierarchical personalisation, prequential evaluation and later decision architecture. Where older planning assumes stationary/batch capability, one universal inference backend, tensor inference as inherently non-sequential, predetermined biological latent-state labels, or capability and action selection as one distribution, this supplement governs.
3. [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md) — normative detailed N-BIO-7/N-BIO-8 mathematical and behavioural supplement except where superseded by the adaptive-inference supplement, `PLAN.md`, or a later phase-specific preregistered contract.
4. [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md) — authoritative additive N-BIO-7E requirements for the extensible context-feature library, stateful per-feature modules, module-owned replayable memory, personalised associative learning, standard ContextSignals and N-BIO Core arbitration.
5. [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md) — authoritative additive product/research/collaboration gates for late N-BIO-7 equipment intelligence, uncertainty communication, N-BIO-8 research/UX design, N-BIO-9 recomputation lifecycle and Native database-safety behaviour. When it marks `COLLABORATION REQUIRED`, stop before behaviour-driving implementation and involve Kian rather than silently choosing product policy.

For N-BIO-7F specifically, [`NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md`](./NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md) is the preregistered implementation boundary and [`RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md`](./RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md) is the curated evidence/design synthesis. They refine the older generic 7F notes without replacing broader product gates.

`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md` does not authorise later biological-state implementations, V8 prescription behaviour or normal-user UX changes.

`CONTEXT_MODULE_ARCHITECTURE.md` likewise does not make tags behaviour-driving. It is the requirement set that kept the implemented 7E platform from degenerating into passive context metadata or hard-coded per-tag logic inside N-BIO Core.

## Context Module authors

Start with the [`context-modules` author documentation](./context-modules/README.md) if you want to add or maintain a module. It provides a compile-tested quickstart, the current author-facing SPI reference, real production examples, versioning/replay guidance, and symptom-based troubleshooting. You do not need to read N-BIO Core or the rough 7E journal first.

## Core biological/performance research

6. [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) — implementation-facing evaluation/navigation.
7. [`RESEARCH_RAW.md`](./RESEARCH_RAW.md) — preserved core DeepResearch source.

## Temporal / Health Connect / conditioning / HR research

8. [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md) — implementation-facing evaluation/navigation.
9. [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md) — raw-report index and integrity metadata; links seven verbatim storage parts.

## Equipment context / cross-profile translation research

10. [`RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md`](./RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md) — curated N-BIO-7F research authority: equipment/free-weight semantics, local physics, historical binding, transfer substrate, champion/challenger design, negative transfer and open empirical questions.
11. [`RESEARCH_EQUIPMENT_TRANSLATION_RAW.md`](./RESEARCH_EQUIPMENT_TRANSLATION_RAW.md) — raw-report index and integrity metadata; links the four preserved parts of the final second-pass research report.

Do not use the raw 7F report as an implementation specification. The curated guide records which raw proposals were accepted, modified, rejected or left open.

## Implementation contracts and notes

- [`TEMPORAL_FOUNDATION.md`](./TEMPORAL_FOUNDATION.md) — concrete N-BIO-6 temporal storage, scope, codec, revision and compatibility decisions. The live development database has since advanced to Room15; historical schema numbers describe the stage at which the contract was introduced.
- [`DEVICE_VERIFICATION.md`](./DEVICE_VERIFICATION.md) — reproducible Termux build and isolated on-device scalar/temporal acceptance procedure.
- [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md) — N-BIO-7A.5 canonical-note ownership, bounded tag ontology, interpreter/fallback/privacy boundary, provenance and zero-effect N-BIO context interface.
- [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md) — implemented N-BIO-7E context-learning requirements: versioned feature definitions, independent module memory/learners, authorised read/write boundaries, temporal episodes, personalised association learning, generic signal protocol and central correlation/arbitration.
- [`EXERCISE_AUTHORING_CONTRACT.md`](./EXERCISE_AUTHORING_CONTRACT.md) — authoritative protocol for externally proposed exercise/execution/recruitment semantics before they enter the existing Native authoring pathway.
- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen Candidate-v1 record, chronological validation, SHADOW persistence and Candidate-v2 development evidence. Read it together with the adaptive-inference supplement for current capability/policy and solver semantics.
- [`NON_DYNAMIC_CAPABILITY_CONTRACT.md`](./NON_DYNAMIC_CAPABILITY_CONTRACT.md) — normative N-BIO-7C loaded-hold, duration-only and repeated-contraction capability contract.
- [`SET_DEMAND_AND_DOSE_CONTRACT.md`](./SET_DEMAND_AND_DOSE_CONTRACT.md) — normative N-BIO-7D SetDemand, exact historical Exposure, candidate EffectiveDose, same-session dependence and SessionDose contract.
- [`NBIO_7E_STATE_CONTEXT_CONTRACT.md`](./NBIO_7E_STATE_CONTEXT_CONTRACT.md) — preregistered and source-reconciled neutral temporal-state, modular context, chronology, arbitration and Room15 contract.
- [`NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md`](./NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md) — preregistered research-reconciled N-BIO-7F contract for canonical equipment/load semantics, historical binding, typed capability transfer, N0/M0 and the open M1/M2 experiment.
- [`context-modules/README.md`](./context-modules/README.md) — source-verified Context Module author entry point and reading paths.
- [`NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md`](./NBIO_7E_CONTEXT_EXTENSION_JOURNAL.md) — intentionally rough historical implementation/API/SPI/research/review record. Use the Context Module author docs for the final source-verified extension surface.
- [`NBIO_7E_STATE_CONTEXT_IMPLEMENTATION_CHECKPOINT_2026-09-04.md`](./NBIO_7E_STATE_CONTEXT_IMPLEMENTATION_CHECKPOINT_2026-09-04.md) — historical green implementation checkpoint and format-v2 installed-device acceptance procedure.
- [`NBIO_7E_STATE_CONTEXT_PHYSICAL_CLOSURE_2026-09-04.md`](./NBIO_7E_STATE_CONTEXT_PHYSICAL_CLOSURE_2026-09-04.md) — physical Room15 N-BIO-7E structural/pre-validation PASS record; the context-free capability baseline remained the real-history champion and PD-001/PD-002/PD-003 remain open.
- [`POSTPONED_DEVELOPMENT.md`](./POSTPONED_DEVELOPMENT.md) — deferred-validation register; PD-001 quarantines 7C empirical accuracy, PD-002 quarantines 7D dose calibration and PD-003 quarantines 7E temporal/context calibration and biological interpretation. Add any 7F empirical debt only after implementation/acceptance demonstrates that it is genuinely deferred.
- [`NBIO_7C_CAPABILITY_ACCEPTANCE_CHECKPOINT_2026-09-02.md`](./NBIO_7C_CAPABILITY_ACCEPTANCE_CHECKPOINT_2026-09-02.md) — physical Room14 structural/pre-validation closure record; the full personal acceptance export remains outside the public repository.
- [`NBIO_7D_DEMAND_DOSE_IMPLEMENTATION_CHECKPOINT_2026-09-03.md`](./NBIO_7D_DEMAND_DOSE_IMPLEMENTATION_CHECKPOINT_2026-09-03.md) — historical implementation-complete checkpoint and physical installed-device acceptance procedure written before the acceptance run.
- [`NBIO_7D_DEMAND_DOSE_PHYSICAL_CLOSURE_2026-09-03.md`](./NBIO_7D_DEMAND_DOSE_PHYSICAL_CLOSURE_2026-09-03.md) — physical Room14 N-BIO-7D structural/pre-validation PASS record; PD-001/PD-002 remain open and the full personal export remains outside the repository.
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
7C      complete structural/pre-validation — physical Room14 capability acceptance PASS; PD-001 empirical accuracy remains pending where evidence is insufficient
7D      complete structural/pre-validation — physical Room14 demand/dose acceptance PASS; PD-001/PD-002 remain OPEN and all 7D output remains SHADOW
7E      complete structural/pre-validation — physical Room15 state/context acceptance PASS; capability baseline retained as real-history champion and all 7E outputs remain SHADOW under PD-003
7F      research-reconciled + preregistered — equipment semantic substrate and directed cross-profile transfer implementation is next; SHADOW only
```

### Candidate-v1 record

Candidate v1 is frozen as `REJECTED_EMPIRICAL_CALIBRATION_V1`. The installed-history acceptance established a genuine empirical rejection: the latent capability frontier was materially better centred than the working-set demonstration predictive, while predictive calibration remained unacceptable. Preserve the model and its tests; do not mutate it to repair later candidates.

### Candidate-v2 status

Candidate v2 has been implemented as a bounded **development experiment**, not product authority. It adds a neutral statistical frontier trajectory to the frozen v1 base and deliberately does not label that trajectory development, recovery, fatigue, skill or biological growth.

The completed same-mathematics Candidate-v2 solver comparison uses **Dense** as the bounded high-fidelity oracle, **Adaptive Sparse** as the selected forward inference representation, and **Conditional Laplace** as a rejected approximation challenger. All direct Candidate-v2 comparisons receive the same frozen-v1 proposal and share the corrected mathematical identity. Generic dense-sequential-grid, low-rank-screen and Gaussian sigma-point implementations remain solver-architecture substrates unless a mathematical adapter makes them genuinely Candidate-v2-equivalent.

The consolidated **N-BIO Adaptive Inference Acceptance** installed-Room14 evidence established that solver decision while preserving SHADOW/development-only authority. Historical results remain development evidence; fresh future workouts are stronger confirmatory evidence.

### N-BIO-7D structural closure status

N-BIO-7D consumes the already-frozen 7B.X/7C capability contracts without treating capability as an action policy or treating PD-001 as closed.

The physically accepted implementation provides causal pre-session capability replay, posterior frontier-gap SetDemand, exact historical recruitment-weight Exposure, candidate EffectiveDose, same-stream shared-posterior aggregation, raw/concave SessionDose, correction-aware replay, Room14 persistence and Native backup verification. Structural closure does not establish physiological calibration: PD-001 and PD-002 remain OPEN and all 7D outputs remain SHADOW/developer-only.

### N-BIO-7F research/preregistration status

The 7F research pass rejects universal equipment normalisation and instead preserves local evidence, local equipment/load semantics and local capability while learning directed uncertain source→destination relationships.

The implementation starts with:

```text
canonical equipment + load semantics
→ historical binding / replay
→ typed capability-transfer boundary
→ N0 destination-only champion
→ M0 directed robust relationship challenger
→ chronology-safe negative-transfer evaluation
```

M1 shared same-profile capability versus M2 equipment-local capability facets remains deliberately open. If real repeated-equipment history is insufficient, `NOT_EVALUATED_REAL_HISTORY` is the correct outcome rather than a synthetic winner.

## Current product and safety authority

All current candidates remain **SHADOW / CANDIDATE** only.

- Normal workout/prescription authority remains `BENCHMARK_V0`.
- Context consumption remains `NONE` for the current dynamic candidate and 7D does not add note/sleep/HR/HRV context.
- The implemented 7E context architecture learns through versioned ContextModules and standard ContextSignals; no tag or module has permission to mutate N-BIO Core state directly.
- N-BIO-7F canonical equipment history belongs to Core/domain persistence; ContextModule private state does not own historical equipment truth.
- 7F must support free weights as first-class equipment and may use exact local physics only where meaning is known; no universal `L_true` or global strength scalar is authorised.
- No RIR/RPE/failure-probability or `% max` value may be fabricated.
- Raw performance evidence remains immutable and separate from derived inference.
- Semantic metadata beats statistical changepoint suspicion when a real execution boundary is known.
- Room15 is the current development schema. 7F advances beyond it only if source audit identifies a genuine new owner; any migration must be additive and must not guess legacy equipment/load semantics.
- N-BIO-7C numerical outputs remain SHADOW/developer-only under PD-001 and are not normal-user prescription or prefill authority.
- N-BIO-7D demand/dose outputs remain SHADOW/developer-only under PD-002; structural success does not establish calibrated physiology.
- N-BIO-7E implements neutral persistent/transient statistical state and context-module learning in SHADOW only. It does not claim measured fatigue, recovery, readiness, development, skill or decay; PD-003 remains OPEN.
- N-BIO-7F does not implement camera/OCR equipment recognition, final equipment setup UX, cross-user cloud learning or N-BIO-8 prescription behaviour.

Before behaviour-driving N-BIO-8 implementation, `PRODUCT_ROADMAP_GATES.md` still requires the dedicated research + product/UX design gate. Programme intent/priorities, in-workout adaptation, editing/regeneration semantics, equipment setup/feasible-load UX and uncertainty presentation require explicit collaboration.

N-BIO-9 remains the later product/integration phase for broader context UX, additional producers such as Health Connect/wearables, explicit reannotation/data controls, Nano rollout/download management, notification/dashboard consumers and Analysis Export. It must plug those sources into the 7E context-module protocol rather than inventing a second context-learning stack. N-BIO-9 must additionally consult the recomputation/model-upgrade UX gates in `PRODUCT_ROADMAP_GATES.md`.

Native Cutover must consult the database compatibility, downgrade protection, migration-safety and recovery-UX gates in `PRODUCT_ROADMAP_GATES.md` before destructive migration is removed and the first permanent Native schema is declared.

## Reading rule

For inference-history, capability and 7D demand/dose work:

```text
PLAN.md
→ ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md
→ relevant CORE_MODEL_DETAIL.md section where not superseded
→ DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md for 7B evidence/model history
→ NON_DYNAMIC_CAPABILITY_CONTRACT.md for the completed 7C structural capability contract
→ SET_DEMAND_AND_DOSE_CONTRACT.md for 7D semantics
→ POSTPONED_DEVELOPMENT.md for PD-001 / PD-002 quarantine
→ PRODUCT_ROADMAP_GATES.md whenever crossing a product/research collaboration gate
→ relevant research guide
→ targeted raw research only when exact evidence/equations/platform wording are needed
→ current source code and immutable model/config identities
```

For N-BIO-7E context/state work, read:

```text
PLAN.md
→ CONTEXT_INTERPRETATION_CONTRACT.md
→ CONTEXT_MODULE_ARCHITECTURE.md
→ ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md
→ relevant CORE_MODEL_DETAIL.md acute/recovery/state sections
→ PRODUCT_ROADMAP_GATES.md
→ relevant research guides
→ current source code
```

For N-BIO-7F equipment/translation work, read:

```text
PLAN.md
→ NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md
→ RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md
→ ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md
→ relevant CORE_MODEL_DETAIL.md translation/capability sections
→ PRODUCT_ROADMAP_GATES.md
→ CONTEXT_MODULE_ARCHITECTURE.md for the canonical-vs-derived boundary
→ targeted RESEARCH_EQUIPMENT_TRANSLATION_RAW.md sections only when evidence detail is needed
→ current source code
```

If your task is only to author a Context Module, begin with [`context-modules/README.md`](./context-modules/README.md) instead of this full architecture path.

For later non-inference work, start with `PLAN.md` and follow the relevant authority links above.

When `PRODUCT_ROADMAP_GATES.md` says `COLLABORATION REQUIRED`, do not silently infer a final UX/product policy from backend capability. Explicitly involve Kian before behaviour-driving implementation.

Do not load all full raw research bodies into ordinary implementation context.

Existing `docs/N_BIO_*.md` files remain historical implementation-stage documentation. The vNext authority stack above governs forward work where they conflict.

N-BIO-7C, N-BIO-7D and N-BIO-7E structural/pre-validation are physically closed under PD-001/PD-002/PD-003 quarantine. The 7E installed-history comparison retained the context-free capability baseline as champion; temporal, dose and context candidates remain SHADOW and PD-003 keeps their calibration and biological interpretation quarantined. N-BIO-7F is now research-reconciled/preregistered and is the next backend implementation mission.