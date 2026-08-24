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

## Reading rule

For a normal task:

```text
PLAN.md
→ relevant CORE_MODEL_DETAIL.md section if implementing N-BIO-7/8
→ relevant research guide section
→ only targeted raw research when exact evidence/equations/platform wording are needed
→ current source code
```

Do not load both full raw research bodies into ordinary implementation context.

Existing `docs/N_BIO_*.md` files are historical implementation-stage documentation. The vNext files above govern forward work where they conflict.