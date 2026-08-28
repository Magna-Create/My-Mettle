# N-BIO vNext Documentation Map

Use this directory in the following order.

## Authority

1. [`PLAN.md`](./PLAN.md) — overarching phase/architecture/acceptance contract.
2. [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md) — normative detailed N-BIO-7/N-BIO-8 mathematical and behavioural supplement. Where temporal research explicitly changed a boundary, `PLAN.md` wins; otherwise this detail remains required.
3. [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md) — authoritative additive product/research/collaboration gates for late N-BIO-7 equipment intelligence, uncertainty communication, N-BIO-8 research/UX design, N-BIO-9 recomputation lifecycle and Native database-safety behaviour. When it marks `COLLABORATION REQUIRED`, stop before behaviour-driving implementation and involve Kian rather than silently choosing product policy.

## Core biological/performance research

4. [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) — implementation-facing evaluation/navigation.
5. [`RESEARCH_RAW.md`](./RESEARCH_RAW.md) — preserved core DeepResearch source.

## Temporal / Health Connect / conditioning / HR research

6. [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md) — implementation-facing evaluation/navigation.
7. [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md) — raw-report index and integrity metadata; links seven verbatim storage parts.

## Implementation contracts and notes

- [`TEMPORAL_FOUNDATION.md`](./TEMPORAL_FOUNDATION.md) — concrete N-BIO-6 Room 12 storage, scope, codec, revision and compatibility decisions.
- [`DEVICE_VERIFICATION.md`](./DEVICE_VERIFICATION.md) — reproducible Termux build and isolated on-device scalar/temporal acceptance procedure.
- [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md) — N-BIO-7A.5 canonical-note ownership, bounded tag ontology, interpreter/fallback/privacy boundary, provenance and zero-effect N-BIO context interface.
- [`EXERCISE_AUTHORING_CONTRACT.md`](./EXERCISE_AUTHORING_CONTRACT.md) — authoritative protocol for externally proposed exercise/execution/recruitment semantics before they enter the existing Native authoring pathway.
- [`DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md`](./DYNAMIC_RESISTANCE_CAPABILITY_CONTRACT.md) — N-BIO-7B evidence/coordinate contract, frozen stochastic-frontier candidate, chronological validation, SHADOW persistence, diagnostics and real-history/device acceptance contract. Implementation is complete; physical installed-history acceptance remains an explicit closure gate.
- [`exercise-import.schema.json`](./exercise-import.schema.json) — Draft 2020-12 machine-readable structural exchange schema, format v1.
- [`exercise-import-example.json`](./exercise-import-example.json) — canonical validated authoring example.

## Current N-BIO-7 phase order

```text
7A    complete — probabilistic/posterior + model/config/inference provenance foundation
7A.5  complete — context interpretation + external exercise-authoring contracts
7B.1  complete — dynamic-resistance evidence eligibility + coordinate/model contract
7B.2  complete — frozen stochastic-frontier inference + synthetic/model verification
7B.3  implementation complete — correction-aware chronological validation + SHADOW persistence + diagnostics
7B.4  implementation complete — installed-history/device acceptance runner + replay/invariance/backup checks; physical acceptance pending
later 7C–7G as defined by PLAN.md — 7C has not started
7F     must also consult PRODUCT_ROADMAP_GATES.md for equipment identity/calibration/session-binding + uncertainty-presentation hooks
```

The 7B candidate remains **SHADOW only**. Normal workout/prescription authority remains `BENCHMARK_V0`; context consumption remains `NONE`; no candidate result is promoted into normal product behaviour by 7B.3/4 implementation. CI establishes deterministic/synthetic/model, persistence, backup and compile-time correctness, but it cannot substitute for the explicit installed Room14 real-history/device acceptance action. N-BIO-7B must therefore not be described as empirically closed until that exported device report has been reviewed.

Before behaviour-driving N-BIO-8 implementation, `PRODUCT_ROADMAP_GATES.md` requires a dedicated research + product/UX design gate rather than marching directly from N-BIO-7H into an optimiser. Programme intent/priorities, in-workout adaptation, editing/regeneration semantics, equipment setup/feasible-load UX and uncertainty presentation require explicit collaboration.

N-BIO-9 remains the later product/integration phase for broader context UX, explicit reannotation/data controls, Nano rollout/download management, notification/dashboard consumers, Health Connect/HR experiments and Analysis Export. The note-annotation architecture itself begins in 7A.5, not N-BIO-9. N-BIO-9 must additionally consult the recomputation/model-upgrade UX gates in `PRODUCT_ROADMAP_GATES.md`.

Native Cutover must consult the database compatibility, downgrade protection, migration-safety and recovery-UX gates in `PRODUCT_ROADMAP_GATES.md` before destructive migration is removed and the first permanent Native schema is declared.

## Reading rule

For a normal task:

```text
PLAN.md
→ relevant CORE_MODEL_DETAIL.md section if implementing N-BIO-7/8
→ PRODUCT_ROADMAP_GATES.md whenever entering late-7 equipment translation, N-BIO-8, N-BIO-9 intelligence/recompute UX or Native Cutover
→ relevant research guide section
→ relevant implementation contract when crossing a stored semantic boundary
→ only targeted raw research when exact evidence/equations/platform wording are needed
→ current source code
```

When `PRODUCT_ROADMAP_GATES.md` says `COLLABORATION REQUIRED`, do not silently infer a final UX/product policy from backend capability. Explicitly remind Kian of the planned research/design gate and ask whether to research, define the interaction contract, or design/provide the relevant Figma flow before implementation.

Do not load both full raw research bodies into ordinary implementation context.

Existing `docs/N_BIO_*.md` files are historical implementation-stage documentation. The vNext files above govern forward work where they conflict.

- **N-BIO-7B.3/4 corrective acceptance:** evidence policy v2 preserves explicitly legacy `UNKNOWN -> UNKNOWN` history as an unsided capability stream; the frozen stochastic model is unchanged. First physical run diagnosed v1 exclusion rather than evaluating the model; corrected physical rerun is the final empirical 7B gate.
