# Skeletal Muscle Reference Research

This folder is the durable research workspace for the My Mettle skeletal-muscle reference model.

## Purpose

Keep the scientific basis for anatomy, reference morphology, muscle architecture and structural-capacity modelling in the repository rather than relying on chat history or temporary Deep Research sandboxes.

## Current files

### Research and decisions

- `RESEARCH_BRIEF.md` — original research proposition and requested deliverables.
- `RECONCILIATION.md` — comparison of the surviving Deep Research runs and unresolved differences.
- `MODEL_DECISIONS.md` — implementation-facing architectural decisions derived from the research and product discussion.
- `REFERENCE_SEED.md` — durable numerical/methodological seed preserved from the reports.
- `PRODUCTION_DATASET_V0_1.md` — current reconstructed dataset status, rules and limitations.
- `REFERENCE_SELECTION.md` — how source evidence is selected into a reference-population profile without overwriting provenance.

### Structured research data

- `data/anatomical_units_v0_1.csv` — 142 reconstructed anatomical units with stable IDs and serial-family handling.
- `data/segment_overrides_v0_1.csv` — explicit subdivisions plus `TRACK` / `PROVISIONAL_TRACK` / `SHARED_PARENT` policy.
- `data/anatomy_verification_v0_1.csv` — direct TA2 terminology/status verification for high-priority and ambiguous structures.
- `data/source_registry_v0_1.csv` — source/provenance registry.
- `data/reference_observations_v0_1.csv` — primary adult-male Riem MRI morphology evidence.
- `data/architecture_observations_v0_1.csv` — primary Charles 2019 lower-limb architecture evidence in long format.
- `data/derived_reference_values_v0_1.csv` — transparent derived evidence values currently limited to defensible segment fractions.
- `data/reference_profile_healthy_adult_male_v0_1.csv` — first materialised adult-male reference selection; currently morphology only.
- `data/equation_registry_v0_1.csv` — versioned PCSA/structural-capacity/fallback equations.
- `data/composite_source_mappings_v0_1.csv` — source aggregates that must not be silently divided between canonical muscles.

## Precedence

The surviving Deep Research reports are the research record, not the production database. Directly checked primary sources now take precedence for individual terminology and numerical evidence.

Where sources or reports disagree, do not silently pick a row count or value. Preserve the disagreement until it can be resolved from anatomical/methodological content.

## Important status

The original generated XLSX/CSV/JSON research packs were temporary sandbox artifacts and are no longer available. The current `data/` files are a deliberate reconstruction, not a claim to have recovered those vanished files byte-for-byte.

The reconstruction prefers nulls or explicit verification/estimation states to invented precision.

## Current model direction

The biological model is layered:

`canonical anatomy → source evidence → reference selection → derived structural capacity → user-specific state → exercise expression`

Exercises do not own muscular progress. Recruitment and prescription belong downstream of the anatomy/user-state model.

## Current milestone

The project now has:

- a 142-unit reconstructed ontology;
- explicit state-tracking policy for anatomical subdivisions;
- direct TA2 verification for the important/ambiguous segment structures;
- 54 primary adult-male Riem morphology observations;
- 99 Charles 2019 lower-limb architecture observations;
- a 47-record `healthy_adult_male_v0_1` morphology selection;
- versioned derived-equation definitions.

No Native/Room rewrite has started from this work yet.

## Next work

1. Finish terminology/anatomy verification across the full ontology.
2. Complete Riem source-ROI coverage and unresolved composite mappings.
3. Add further architecture sources, prioritising adult-male and segment-specific data.
4. Define when mixed-population architecture can become a higher-uncertainty model prior.
5. Populate selected architecture/derived PCSA where justified.
6. Then redesign Native domain/persistence structures around the stabilised model.
