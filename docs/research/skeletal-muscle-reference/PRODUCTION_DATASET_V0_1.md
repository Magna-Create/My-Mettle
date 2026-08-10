# Skeletal-muscle production dataset v0.1

## Status

This is the first deliberate reconstruction of the My Mettle biological reference dataset from the surviving Deep Research reports. It is **not** a verbatim recovery of the temporary CSV/XLSX files that disappeared with the Deep Research sandbox.

The purpose of v0.1 is to establish stable IDs, ontology rules and evidence ownership before any Native/Room rewrite.

## What is currently represented

### Anatomical units

`data/anatomical_units_v0_1.csv` contains **142 anatomical units** across:

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

The 142-unit count agrees with the first surviving Deep Research report, but the exact vanished ontology cannot be proven from prose alone. Every unit is therefore marked `RECONSTRUCTED_V0_1` until terminology and coverage are checked against TA2 and the research sources.

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

This distinction is essential. An anatomical subdivision existing does not automatically imply that My Mettle can infer its development independently.

The surviving prose explicitly supports 38 override rows. Expanding the current rules produces **164 segment rows**. One earlier Deep Research run claimed 166 rows, while later runs claimed different totals. The missing two rows are intentionally **not guessed**. We will resolve them during the TA2/source verification pass rather than chase a historical row count.

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

## Reference evidence

`data/reference_observations_v0_1.csv` preserves numerical adult-male MRI observations that survived in the reports.

The initial evidence layer deliberately stores observations rather than writing numbers directly onto anatomical units. This allows multiple studies, populations and methods to coexist without overwriting one another.

The current Riem 2026 records include source-resolved means for major muscles plus directly resolved biceps-femoris and gastrocnemius heads. Missing SD values remain null where the surviving prose did not print them.

`data/derived_reference_values_v0_1.csv` currently contains only four derived parent-volume fractions:

- biceps femoris long head;
- biceps femoris short head;
- gastrocnemius medial head;
- gastrocnemius lateral head.

These are allowed because both sibling head volumes were measured in the same male MRI source. No equivalent fractions are fabricated for biceps brachii, triceps, deltoid, pectoralis major, trapezius, gluteus medius or adductor magnus.

## Composite observations

`data/composite_source_mappings_v0_1.csv` records source measurements that map onto several canonical units. The rule is `DO_NOT_SPLIT` unless another compatible source resolves the components.

This prevents mistakes such as dividing an `infraspinatus + teres minor` MRI value equally between the two muscles.

## What is deliberately missing

v0.1 does not yet contain:

- verified Latin/TA2 names for every unit;
- origins/insertions/innervation topology;
- functional group/tag relationships;
- the full Riem male volume table;
- complete fascicle-length, pennation or PCSA evidence;
- a selected `healthy_adult_male_v1` reference profile;
- user-state or progression equations;
- exercise recruitment data.

Those are subsequent passes, not reasons to delay stabilising the ontology.

## Next passes

1. Verify every anatomical unit and named subdivision against TA2 / cited anatomy sources.
2. Resolve the remaining ontology-count discrepancy by content, not by target count.
3. Recover/verify the full sex-specific Riem volume table from the primary source/supplement.
4. Add source registry and architecture observations (fascicle length, pennation, PCSA) with population/method metadata.
5. Build a reference-selection layer that chooses observations for `healthy_adult_male_v1` without destroying the underlying evidence.
6. Only then translate the dataset into Native domain models and persistence entities.
