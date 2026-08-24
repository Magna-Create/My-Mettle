# N-BIO vNext — Health / Temporal DeepResearch Raw Index

> **Status:** source research; not an implementation specification.
>
> This report is preserved verbatim from the DeepResearch Markdown supplied on 2026-08-24. It is split into seven storage-only parts to keep targeted agent reads inexpensive. Concatenate the parts below in order to reconstruct the original Markdown byte-for-byte.
>
> Implementation decisions live in [`PLAN.md`](./PLAN.md). Interpretive/navigation guidance lives in [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md).

## Integrity

- Original filename: `deep-research-report-health.md`
- Original SHA-256: `ab7c7019b9c7d839a8ee3203b2ec55501ac0f107cd14724641c1eb36fc7de7a3`
- Original byte count: `88766`
- Original rendered line range: 1–1557 (final line has no trailing newline)
- The split adds no explanatory text inside the raw part files.

## Raw parts

1. [`RESEARCH_HEALTH_TEMPORAL_RAW_01.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_01.md) — original lines 1–250: architectural decision, Health Connect topology, provenance, Samsung sync, candidate matching, start of domain model.
2. [`RESEARCH_HEALTH_TEMPORAL_RAW_02.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_02.md) — original lines 251–500: temporal domain model, trace chunks, evidence resolution, raw/derived/cache boundary.
3. [`RESEARCH_HEALTH_TEMPORAL_RAW_03.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_03.md) — original lines 501–750: trace storage/Room architecture, immutable source revisions, modality-specific conditioning capability.
4. [`RESEARCH_HEALTH_TEMPORAL_RAW_04.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_04.md) — original lines 751–1000: cardio→muscle evidence, TRIMP/context, trace-summary registry, resistance-training HR feature registry and set-performance framing.
5. [`RESEARCH_HEALTH_TEMPORAL_RAW_05.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_05.md) — original lines 1001–1250: HR residuals/readiness, systemic-vs-local decomposition, confounders, cardio UX start.
6. [`RESEARCH_HEALTH_TEMPORAL_RAW_06.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_06.md) — original lines 1251–1500: cardio bout semantics, predictive validation, validation vectors, N-BIO-6 freeze requirements, explicit rejections.
7. [`RESEARCH_HEALTH_TEMPORAL_RAW_07.md`](./RESEARCH_HEALTH_TEMPORAL_RAW_07.md) — original lines 1501–1557: evidence-quality ledger, open platform/research questions, source map and final foundation judgement.

## Agent reading rule

Do **not** load all seven parts for ordinary implementation work.

Read:

1. [`PLAN.md`](./PLAN.md) first;
2. the relevant section of [`RESEARCH_HEALTH_TEMPORAL_GUIDE.md`](./RESEARCH_HEALTH_TEMPORAL_GUIDE.md);
3. only the raw part(s) routed by the guide when exact wording, evidence caveats, platform behaviour, equations or citations are required.

The raw report contains research findings, modelling proposals and product-policy suggestions. None becomes implementation authority merely by appearing in the raw report.