# Skeletal-muscle production dataset v0.1

## Status

This is the first deliberate reconstruction of the My Mettle biological reference dataset from the surviving Deep Research reports **plus direct primary-source verification**. It is not a verbatim recovery of the temporary CSV/XLSX files that disappeared with the Deep Research sandbox.

The purpose of v0.1 is to establish stable IDs, ontology rules, evidence ownership and reference-selection rules before any Native/Room rewrite.

## What is currently represented

### Anatomical units

`data/anatomical_units_v0_1.csv` contains **142 reconstructed anatomical units** across:

- neck;
- back;
- thorax;
- abdomen;
- shoulder;
- upper arm;
- forearm;
- pelvic floor/perineum;
- hip/gluteal region;
- thigh;
- lower leg.

The scope excludes facial-expression, mastication, extraocular and tongue musculature, plus intrinsic hand and intrinsic foot muscles. Forearm and lower-leg muscles acting on the digits remain included.

The exact vanished ontology cannot be proven from prose alone. Every unit remains `RECONSTRUCTED_V0_1` until the full terminology pass is complete.

### TA2 verification

`data/anatomy_verification_v0_1.csv` has begun the direct terminology audit against FIPAT Terminologia Anatomica 2.

The verification layer deliberately distinguishes:

- formal TA2 terms;
- parenthesised/variant TA2 terms;
- My Mettle canonicalisations of side-specific formal terms;
- application fibre regions that are not themselves TA2 terms;
- recognised anatomical subdivisions that are recorded but not independently tracked.

Examples already resolved:

- trapezius descending/transverse/ascending parts are formal TA2 parts;
- deltoid clavicular/acromial/spinal parts are formal TA2 parts;
- biceps/triceps heads are formal TA2 heads;
- pectoralis-major clavicular/sternocostal parts are formal, while the abdominal part is parenthesised in TA2;
- diaphragm lumbar/costal/sternal parts are formal but TA2 records them separately on right and left, so My Mettle's side-neutral reference parts are explicit canonical aggregations;
- adductor-magnus adductor and ischiocondylar parts receive particularly strong support because the TA2 endnote distinguishes their origins, insertions and innervations;
- rectus-femoris straight/reflected heads are recorded anatomically but deliberately do not become independent v1 development states;
- gluteus-medius anterior/middle/posterior regions remain application/research fibre regions pending dedicated regional-source verification.

### Serial muscle families

Repeated structures are represented as `SERIAL_MUSCLE_FAMILY` rather than one user-state object per vertebral/intercostal level. Current examples include intercostals, multifidus, rotatores, interspinales, intertransversarii and levatores costarum.

### Segments

`data/segment_overrides_v0_1.csv` stores only muscles that depart from the default whole-muscle representation.

Generation rule:

1. Every anatomical unit defaults to one `WHOLE_MUSCLE` segment.
2. If a unit appears in `segment_overrides_v0_1.csv`, its default whole segment is replaced by the listed anatomical subdivisions.
3. `state_policy` determines whether the subdivisions receive independent longitudinal state.

Current policies:

- `TRACK` — independent user state is justified for v1.
- `PROVISIONAL_TRACK` — independently addressable, but evidence is weaker and must retain higher uncertainty.
- `SHARED_PARENT` — the subdivision exists anatomically but should not yet receive an independent development state.

An anatomical subdivision existing does not automatically imply that My Mettle can infer its development independently.

The surviving prose explicitly supports 38 override rows. Expanding the current rules produces **164 segment rows**. Historic Deep Research row-count claims differ; the missing rows are intentionally not guessed.

## Independent-state v1 muscles

Strong `TRACK` segmentation currently applies to:

- trapezius — descending / transverse / ascending;
- deltoid — clavicular / acromial / spinal;
- pectoralis major — clavicular / sternocostal / abdominal;
- biceps brachii — long / short heads;
- triceps brachii — long / lateral / medial heads;
- adductor magnus — adductor / hamstring portions;
- biceps femoris — long / short heads;
- gastrocnemius — medial / lateral heads.

Gluteus medius anterior/middle/posterior fibre regions are `PROVISIONAL_TRACK`.

Sternocleidomastoid heads, digastric and omohyoid bellies, pronator-teres heads, flexor-carpi-ulnaris heads, flexor-digitorum-superficialis heads and diaphragm parts are recorded but currently `SHARED_PARENT`.

## Evidence architecture

`data/source_registry_v0_1.csv` is now the root provenance registry.

The first registered sources are:

- `FIPAT_TA2_2019` — ontology authority;
- `RIEM_2026` — primary adult-male morphology backbone;
- `CHARLES_2019` — primary young-adult lower-limb architecture context.

Numerical observations are not written directly onto anatomy objects. Evidence and reference selection remain separate.

### Adult-male morphology

`data/reference_observations_v0_1.csv` now contains **54 primary Riem 2026 male MRI observations**, rather than only the numerical snippets that survived Deep Research.

The records include source-resolved muscles/heads plus composite ROIs. Composite observations remain `CONTEXT_ONLY_COMPOSITE` and cannot be divided between canonical muscles without another resolving source.

`data/derived_reference_values_v0_1.csv` currently contains only defensible same-source segment fractions for biceps femoris and gastrocnemius.

### Lower-limb architecture

`data/architecture_observations_v0_1.csv` contains **99 long-format observations across 20 lower-limb muscles** from Charles et al. 2019:

- measured MRI volume;
- measured muscle length;
- published-derived optimal fibre length;
- measured pennation where reported;
- published-derived PCSA.

These remain `CONTEXT_ONLY_MIXED_SEX`: the cohort is young and healthy but contains five men and five women. They are strong architecture evidence, not an adult-male canonical mean.

The provenance distinction is important: Charles' optimal fibre lengths use DTI tract lengths corrected with sarcomere-length data from Ward et al.; PCSA is therefore not treated as a wholly direct measurement.

## Reference selection

`REFERENCE_SELECTION.md` defines the selection rules.

`data/reference_profile_healthy_adult_male_v0_1.csv` is the first materialised profile and currently selects **47 directly resolved adult-male Riem volume observations**.

It excludes composite ROIs and does not invent segment distributions. Architecture has not yet been promoted into the adult-male profile.

## Derived equations

`data/equation_registry_v0_1.csv` versions the first model equations rather than hiding them in code:

- geometric PCSA from volume / optimal fibre length;
- effective PCSA using an explicit pennation projection;
- normalised structural force-capacity index;
- `V^(2/3)` as a low-confidence geometric-similarity fallback only.

Source-native published PCSA values remain separate evidence and are never overwritten by My Mettle reconstructions.

## Composite observations

`data/composite_source_mappings_v0_1.csv` records source measurements that map onto several canonical units. The default rule is `DO_NOT_SPLIT`.

Where a source aggregate's exact component set still needs checking, it is stored without a guessed mapping using `DO_NOT_MAP_UNTIL_COMPONENTS_VERIFIED`.

## What is deliberately missing

v0.1 does not yet contain:

- completed TA2 verification for all 142 reconstructed units;
- origins/insertions/innervation topology for the full ontology;
- functional group/tag relationships;
- every one of Riem's source ROIs;
- adult-male-compatible architecture for most muscles;
- selected architecture/PCSA values in `healthy_adult_male_v0_1`;
- user-state or progression equations;
- exercise recruitment data.

These are subsequent passes, not reasons to contaminate the current evidence with invented values.

## Next passes

1. Complete TA2/source verification across the reconstructed ontology.
2. Resolve remaining ontology-content discrepancies by anatomy rather than historic row count.
3. Complete Riem source-ROI coverage and composite mappings.
4. Add further primary architecture sources, prioritising adult-male and segment-specific evidence.
5. Define the explicit rule for using contextual architecture as a prior where adult-male architecture is absent.
6. Populate selected architecture/derived PCSA in `healthy_adult_male_v0_1` only where that rule permits it.
7. Then translate the stabilised model into Native domain models and persistence entities.
