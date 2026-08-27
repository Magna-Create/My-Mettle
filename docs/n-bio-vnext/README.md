# N-BIO vNext Documentation Map

Use this directory in the following order.

## Authority

1. [`PLAN.md`](./PLAN.md) — overarching phase/architecture/acceptance contract.
2. [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md) — normative detailed N-BIO-7/N-BIO-8 mathematical and behavioural supplement. Where temporal research explicitly changed a boundary, `PLAN.md` wins; otherwise this detail remains required.

## Core biological/performance research

3. [`RESEARCH_GUIDE.md`](./RESEARCH_GUIDE.md) — implementation-facing evaluation/navigation.
4. [`RESEARCH_RAW.md`](./RESEARCH_RAW.md) — preserved core DeepResearch source.

## Temporal / Health Connect / conditioning / HR research

5. [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md) — implementation-facing evaluation/navigation.
6. [`RESEARCH_HEALTH_TEMPORAL_RAW.md`](./RESEARCH_HEALTH_TEMPORAL_RAW.md) — raw-report index and integrity metadata; links seven verbatim storage parts.

## Implementation contracts and notes

- [`TEMPORAL_FOUNDATION.md`](./TEMPORAL_FOUNDATION.md) — concrete N-BIO-6 Room 12 storage, scope, codec, revision and compatibility decisions.
- [`DEVICE_VERIFICATION.md`](./DEVICE_VERIFICATION.md) — reproducible Termux build and isolated on-device scalar/temporal acceptance procedure.
- [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md) — N-BIO-7A.5 canonical-note ownership, bounded tag ontology, interpreter/fallback/privacy boundary, provenance and zero-effect N-BIO context interface.
- [`EXERCISE_AUTHORING_CONTRACT.md`](./EXERCISE_AUTHORING_CONTRACT.md) — authoritative protocol for externally proposed exercise/execution/recruitment semantics before they enter the existing Native authoring pathway.
- [`exercise-import.schema.json`](./exercise-import.schema.json) — Draft 2020-12 machine-readable structural exchange schema, format v1.
- [`exercise-import-example.json`](./exercise-import-example.json) — canonical validated authoring example.

## Current N-BIO-7 phase order

```text
7A    probabilistic/posterior + model/config/inference provenance foundation
7A.5  context interpretation + external exercise-authoring contracts
7B    dynamic-resistance capability model
later 7C–7G as defined by PLAN.md
```

N-BIO-9 remains the later product/integration phase for broader context UX, explicit reannotation/data controls, Nano rollout/download management, notification/dashboard consumers, Health Connect/HR experiments and Analysis Export. The note-annotation architecture itself begins in 7A.5, not N-BIO-9.

## Reading rule

For a normal task:

```text
PLAN.md
→ relevant CORE_MODEL_DETAIL.md section if implementing N-BIO-7/8
→ relevant research guide section
→ relevant implementation contract when crossing a stored semantic boundary
→ only targeted raw research when exact evidence/equations/platform wording are needed
→ current source code
```

Do not load both full raw research bodies into ordinary implementation context.

Existing `docs/N_BIO_*.md` files are historical implementation-stage documentation. The vNext files above govern forward work where they conflict.
