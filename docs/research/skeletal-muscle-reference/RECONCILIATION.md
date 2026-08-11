# Deep Research Reconciliation

## Why this exists

Two Deep Research runs produced overlapping but not identical skeletal-muscle reference models. Their temporary XLSX/CSV/JSON files were lost with the research sandboxes, while the narrative reports survived.

This document records where the surviving reports agree, where they differ, and which differences must remain unresolved until checked against the underlying anatomy/source evidence.

## Strong agreement across the reports

The reports consistently support the following architecture:

`canonical anatomy → reference morphology → architecture → derived structural capacity → user-specific state → exercise expression`

They also agree that:

- the individual muscle is the canonical biological identity;
- muscle groups are classification/tagging constructs rather than strict parents;
- left/right should be instantiated in user state rather than by duplicating reference anatomy;
- reference volume, fascicle length, pennation and PCSA are population priors rather than immutable user constants;
- volume measures tissue quantity, while PCSA is the more interpretable structural force-normalisation variable;
- `V^(2/3)` is only a weak geometric-similarity fallback;
- exact Newton-valued force and universal specific tension should not become canonical biological truth in v1;
- evidence observations must retain population, method, provenance and uncertainty;
- missing values should remain null unless an explicit derivation/imputation rule permits estimation;
- exercise performance belongs downstream and must not define the muscle ontology.

## Ontology-count disagreement

The surviving reports mention several different generated-pack counts:

- original synthesis: **135 canonical muscle objects / 149 segment rows**, including 23 independently addressable subsegments;
- first Deep Research report: **142 anatomical units / 166 proposed segment rows**;
- rerun canonical report: **144 canonical muscles / 158 canonical muscle/segment rows**, with 113 physiological evidence records.

These numbers must not be treated as targets to reproduce.

The first report introduced two useful ideas that help explain why counts can legitimately differ:

1. `unitKind` distinguishes a normal `MUSCLE` from a `SERIAL_MUSCLE_FAMILY` such as intercostals, multifidus or rotatores.
2. An anatomical subdivision can exist without receiving independent longitudinal user state.

The later rerun simplified its independently addressable segmentation to nine muscles, while the first run recorded additional true subdivisions such as sternocleidomastoid heads, digastric/omohyoid bellies, forearm heads and diaphragm parts but recommended shared parent state.

## Current production reconstruction

The v0.1 reconstruction therefore separates:

- **anatomical-unit existence**;
- **segment existence**;
- **independent-state policy**.

`data/anatomical_units_v0_1.csv` currently contains 142 reconstructed anatomical units, matching the first report's unit count.

`data/segment_overrides_v0_1.csv` contains only subdivisions explicitly recoverable from surviving prose. All other units default to one whole-muscle segment.

Expanding those rules currently yields 164 segment rows rather than the first report's claimed 166. The remaining two historical rows are intentionally not guessed. Their identities will be resolved during TA2/source verification.

## Segmentation agreement

The later reports strongly agree on the main independent-state candidates:

- trapezius;
- deltoid;
- pectoralis major;
- biceps brachii;
- triceps brachii;
- adductor magnus;
- biceps femoris;
- gastrocnemius.

Gluteus medius anterior/middle/posterior regions are consistently treated as useful but provisional `FIBRE_REGION` objects.

The first Deep Research report additionally identified true anatomical subdivisions that should be recorded but not independently progressed yet:

- sternocleidomastoid sternal/clavicular heads;
- digastric anterior/posterior bellies;
- omohyoid superior/inferior bellies;
- pronator-teres humeral/ulnar heads;
- flexor-carpi-ulnaris humeral/ulnar heads;
- flexor-digitorum-superficialis humeroulnar/radial heads;
- diaphragm sternal/costal/lumbar parts.

These are represented with `state_policy = SHARED_PARENT` in v0.1.

## Numerical-evidence improvement in the rerun

The original synthesis could not access the sex-specific Riem supplement and therefore left adult-male numerical values null.

The rerun successfully preserved a number of sex-specific male MRI means and SDs from Riem et al. These are now stored in `data/reference_observations_v0_1.csv` as source-level evidence rather than copied directly onto the anatomy objects.

The reports also preserved directly resolved male head volumes for biceps femoris and gastrocnemius. Their parent fractions are the only current segment-volume fractions stored in `data/derived_reference_values_v0_1.csv`, because both siblings were measured within the same compatible source.

## Composite-source handling

The reports explicitly warn that source ROIs such as rhomboids, infraspinatus + teres minor, extensor-carpi-radialis longus + brevis and other aggregates must not be split by arithmetic convenience.

Those relationships are now preserved in `data/composite_source_mappings_v0_1.csv` with `DO_NOT_SPLIT` rules.

## Remaining reconciliation work

- Verify all 142 reconstructed units and naming against TA2 / cited anatomy sources.
- Identify the two segment rows present in the first lost 166-row pack but not recoverable from prose.
- Determine why the rerun reported 144 canonical muscles rather than 142 anatomical units.
- Recover/verify the complete sex-specific Riem volume table from the primary source/supplement.
- Reconcile architecture observations and population compatibility before selecting a `healthy_adult_male_v1` profile.

Until then, the current reconstruction is versioned research data, not immutable production truth.
