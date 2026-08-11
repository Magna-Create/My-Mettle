# Skeletal Muscle Reference Research

This folder is the durable research workspace for the My Mettle skeletal-muscle reference model.

## Purpose

Keep the scientific basis for anatomy, reference morphology, muscle architecture and structural-capacity modelling in the repository rather than relying on chat history or temporary Deep Research sandboxes.

## Status

**Biological foundation: implementation-ready for Native N-BIO-1.**

This does not mean the physiology dataset is complete. It means the remaining gaps now have explicit fallback/latent rules and no longer require a change to the foundational ontology or persistence shape before implementation can begin.

Further physiology research should continue in parallel and improve priors without holding the Native rewrite hostage.

## Current files

### Research and decisions

- `RESEARCH_BRIEF.md` — original research proposition and requested deliverables.
- `RECONCILIATION.md` — comparison of the surviving Deep Research runs and unresolved differences.
- `MODEL_DECISIONS.md` — implementation-facing architectural decisions derived from the research and product discussion.
- `REFERENCE_SEED.md` — durable numerical/methodological seed preserved from the reports; retained as research history rather than production truth.
- `PRODUCTION_DATASET_V0_1.md` — reconstructed dataset status, rules and limitations.
- `REFERENCE_SELECTION.md` — separation of source evidence from selected reference-population priors.
- `MODEL_PRIOR_POLICY.md` — permitted evidence-selection, PCSA, fallback and parent-latent inference rules.
- `NATIVE_HANDOFF.md` — implementation contract and proposed N-BIO patch sequence.
- `REMAINING_RESEARCH_GAPS.md` — non-blocking physiology/recruitment/inference research backlog.

### Structured research data

- `data/anatomical_units_v0_1.csv` — 142 reconstructed anatomical units with stable IDs and serial-family handling.
- `data/segment_overrides_v0_1.csv` — explicit subdivisions plus `TRACK` / `PROVISIONAL_TRACK` / `SHARED_PARENT` policy.
- `data/anatomy_verification_v0_1.csv` — direct TA2 terminology/status verification for high-priority and ambiguous structures.
- `data/source_registry_v0_1.csv` — source/provenance registry covering morphology, lower-limb architecture, upper-limb/shoulder, trunk and segment-specific evidence.
- `data/reference_observations_v0_1.csv` — primary adult-male Riem MRI morphology evidence.
- `data/architecture_observations_v0_1.csv` — Charles 2019 in-vivo lower-limb architecture evidence in long format.
- `data/segment_architecture_observations_v0_1.csv` — direct/segment-level architecture observations, including adult-male gastrocnemius data.
- `data/architecture_context_additions_v0_1.csv` — broader lower-limb, upper-limb, forearm, trunk and latissimus fallback evidence.
- `data/segment_reference_policy_v0_1.csv` — one explicit structural-prior/fallback policy per independently addressable segment.
- `data/derived_reference_values_v0_1.csv` — transparent derived values currently limited to defensible reconstructions.
- `data/reference_profile_healthy_adult_male_v0_1.csv` — first materialised adult-male morphology selection.
- `data/equation_registry_v0_1.csv` — versioned PCSA/structural-capacity/fallback equations.
- `data/composite_source_mappings_v0_1.csv` — source aggregates that must not be silently divided between canonical muscles.

## Precedence

The surviving Deep Research reports are the research record, not the production database. Directly checked primary sources take precedence for individual terminology and numerical evidence.

Where sources disagree, preserve the source-specific observations and resolve the disagreement through explicit reference-selection/model rules rather than overwriting one number with another.

## Important historical note

The original generated XLSX/CSV/JSON research packs were temporary sandbox artifacts and are no longer available. The current `data/` files are a deliberate reconstruction, not a claim to have recovered those vanished files byte-for-byte.

The reconstruction prefers nulls, latent variables or explicit estimation states to invented precision.

## Current model direction

The biological model is layered:

`canonical anatomy → source evidence → reference selection → derived structural capacity → user-specific state → exercise expression`

Exercises do not own muscular progress. Recruitment and prescription belong downstream of the anatomy/user-state model.

## Current milestone

The workspace now has:

- a 142-unit reconstructed ontology;
- explicit longitudinal state policy for anatomical subdivisions;
- direct TA2 verification for important/ambiguous segment structures;
- a broad adult-male Riem morphology backbone;
- coherent lower-limb architecture evidence from Charles/Ward;
- additional upper-limb, forearm, trunk and regional architecture context;
- direct adult-male in-vivo architecture for both gastrocnemius heads;
- explicit segment-reference policies for every current `TRACK` / `PROVISIONAL_TRACK` segment;
- a selected `healthy_adult_male_v0_1` morphology profile;
- versioned PCSA/structural-capacity equations;
- an explicit parent-latent strategy where segment physiology is incomplete;
- a Native handoff defining the replacement domain/persistence architecture.

No Native runtime/Room changes have been made on this research branch.

## Native boundary

The next implementation action is **N-BIO-1** from `NATIVE_HANDOFF.md`:

1. introduce anatomy/reference domain models and typed IDs;
2. generate deliberately selected runtime reference assets;
3. replace the relevant Room foundation with `muscle`, `muscle_segment`, `reference_profile` and `reference_physiology_prior`;
4. seed and validate those records;
5. permit destructive development migration while Native is not yet the authoritative workout store.

Exercise/recruitment, target/prescription and user-inference patches follow afterwards.

## Research continuing in parallel

See `REMAINING_RESEARCH_GAPS.md`.

The highest-value remaining physiology work concerns upper-body tracked segments (biceps/triceps/deltoid/pectoralis), adductor-magnus mapping and gluteus-medius regional physiology. Neck/deep-back/respiratory/pelvic-floor work is useful but not required before the foundational rewrite.

A separate exercise-recruitment research pass will be required before My Mettle's final recruitment dataset is considered mature.
