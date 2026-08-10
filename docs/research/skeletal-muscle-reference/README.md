# Skeletal Muscle Reference Research

This folder is the durable research record for My Mettle's skeletal-muscle ontology and reference-physiology model.

It exists so the Native implementation does not depend on conclusions that only live in ChatGPT conversation history or temporary Deep Research sandboxes.

## Status

**Research stage:** evidence collected; the two Deep Research reports have been reconciled at the architectural level; the production canonical dataset has not yet been rebuilt.

Two research runs exist:

1. **Original research synthesis**
   - Reported a working ontology of 135 canonical muscle objects / 149 segment rows, including 23 independently addressable subsegments.
   - Could not retrieve the sex-specific Riem numerical supplement, so adult-male numerical reference fields were deliberately left null.

2. **Rerun canonical reference report**
   - Retained because it materially improved the evidence base.
   - Reported 144 canonical muscles / 158 canonical muscle-or-segment rows and 113 physiological evidence records.
   - Successfully extracted sex-specific adult-male values from Riem et al. for many resolved muscles and directly resolved heads.

The XLSX/CSV/JSON research-pack files referenced by both reports were hosted in temporary Deep Research sandbox storage and were not recoverable. Their existence must **not** be assumed by the codebase.

## Which report wins?

Neither report is treated as an unquestionable canonical dataset.

The rerun is the preferred numerical evidence source where it contains data absent from the first run, particularly the Riem adult-male MRI values. The two reports strongly agree on the biological architecture and initial segmentation policy. Where they differ in ontology row counts or detail, the difference remains explicit until the production ontology is reconstructed.

See:

- [`RECONCILIATION.md`](RECONCILIATION.md) — agreement, differences and unresolved items.
- [`MODEL_DECISIONS.md`](MODEL_DECISIONS.md) — current My Mettle architectural decisions derived from the research.
- [`REFERENCE_SEED.md`](REFERENCE_SEED.md) — useful directly reported/reconstructed numerical evidence preserved from the rerun.
- [`RESEARCH_BRIEF.md`](RESEARCH_BRIEF.md) — the research requirements that generated the reports.

## Research principles carried forward

- The **muscle** is the canonical biological identity.
- A muscle may expose addressable **segments** of different anatomical status (`HEAD`, `PART`, `FIBRE_REGION`, `WHOLE_MUSCLE`).
- Muscle groups are classifications, not biological identities, and should support many-to-many membership.
- Reference anatomy is side-neutral; user state is bilateral where appropriate.
- Reference morphology and architecture are **population priors**, not immutable properties of a user.
- Volume measures tissue quantity; PCSA is the preferred structural basis for relative force capacity where architecture is available.
- `V^(2/3)` is only a low-confidence geometric-similarity fallback.
- Do not canonicalise a universal specific-tension constant in v1.
- Preserve source, population, method, equation definition and uncertainty for physiological evidence.
- Missing values stay missing unless an explicit, versioned estimation method is permitted.
- Exercise performance is downstream evidence; it is not itself a property of a muscle.

## Next production step

Rebuild the canonical ontology and reference-evidence tables deliberately from the surviving research and cited primary evidence, then implement the Native domain model around that evidence model. Do not attempt to reproduce the vanished Deep Research spreadsheets row-for-row merely for continuity.
