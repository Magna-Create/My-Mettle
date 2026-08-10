# Skeletal Muscle Reference Research

This folder is the durable research workspace for the My Mettle skeletal-muscle reference model.

## Purpose

Keep the scientific basis for anatomy, reference morphology, muscle architecture and structural-capacity modelling in the repository rather than relying on chat history or temporary Deep Research sandboxes.

## Current files

- `RESEARCH_BRIEF.md` — original research proposition and requested deliverables.
- `RECONCILIATION.md` — comparison of the surviving Deep Research runs and unresolved differences.
- `MODEL_DECISIONS.md` — implementation-facing architectural decisions derived from the research and product discussion.
- `REFERENCE_SEED.md` — durable numerical and methodological seed values preserved from the reports.
- `PRODUCTION_DATASET_V0_1.md` — rules, status and limitations of the first reconstructed production dataset.
- `data/anatomical_units_v0_1.csv` — 142 reconstructed anatomical units with stable IDs and serial-family handling.
- `data/segment_overrides_v0_1.csv` — explicit anatomical subdivisions plus independent-state policy.
- `data/reference_observations_v0_1.csv` — source-level adult-male MRI observations preserved from the surviving reports.
- `data/derived_reference_values_v0_1.csv` — transparent derived values currently limited to defensible segment fractions.
- `data/composite_source_mappings_v0_1.csv` — source aggregates that must not be silently divided between canonical muscles.

## Precedence

The rerun/final Canonical Skeletal-Muscle Reference Model report is the newer primary research basis where it contains stronger source resolution, particularly sex-specific Riem adult-male MRI values. The earlier synthesis and first Deep Research report remain important because they contain ontology refinements and methodological remarks omitted from the rerun.

Where the reports disagree, do not silently pick a row count or value. Reconcile the content and preserve the disagreement until it is resolved from the underlying anatomy/source evidence.

## Important status

The original generated XLSX/CSV/JSON research packs were temporary sandbox artifacts and are no longer available. The scientific reports survived. The current `data/` files are therefore a deliberate reconstruction, not a claim to have recovered those vanished files byte-for-byte.

The production reconstruction intentionally prefers nulls or explicit verification flags to invented precision.

## Current model direction

The biological model is layered:

`canonical anatomy → reference morphology → architecture → derived structural capacity → user-specific state → exercise expression`

Exercises do not own muscular progress. Recruitment and prescription belong downstream of the anatomy/user-state model.

## Next work

1. Verify the reconstructed ontology against TA2 and cited anatomy sources.
2. Resolve the remaining ontology-row discrepancies by content rather than matching a historic count.
3. Recover/verify the complete adult-male Riem volume evidence table.
4. Add architecture evidence with method/population provenance.
5. Create a selected healthy-adult-male reference profile.
6. Translate the stabilised model into Native domain/persistence structures.
