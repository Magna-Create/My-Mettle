# Research Brief — Skeletal Muscle Reference Model

## Purpose

Establish the canonical anatomical and physiological reference substrate for My Mettle so muscular progress can belong to the person rather than to individual exercises.

The research is intended to support, later:

- muscle- and segment-specific training targets;
- exercise recruitment models;
- transfer of capability estimates between exercises;
- dynamic workout prescription;
- regional development and failure analysis;
- longitudinal muscle-state modelling;
- future visual/morphological representations.

This research does **not** define exercise recruitment or the final progression algorithm.

## Anatomical scope

Include neck, shoulder girdle, thorax, respiratory musculature, back, abdomen, pelvic/pelvic-floor musculature where relevant, upper arm, forearm, hip/gluteal region, thigh and lower leg.

Exclude facial-expression muscles, mastication, extraocular muscles, tongue, intrinsic hand muscles and intrinsic foot muscles. Retain forearm/lower-leg extrinsic digit muscles.

Initial reference population: healthy adult male, favouring young/general adults over elderly, pathological or specialised athletic cohorts.

## Canonical hierarchy to investigate

`Region -> Muscle Group -> Muscle -> Muscle Segment`

The individual muscle remains the canonical biological object.

A segment may be:

- `HEAD`
- `PART`
- `FIBRE_REGION`
- `WHOLE_MUSCLE`

The research must distinguish formally named anatomy from experimentally useful application segmentation and informal fitness terminology.

## Required anatomy questions

For each included muscle determine:

- canonical English name and useful Latin name;
- region and applicable muscle groups;
- whether subdivision is justified;
- names and anatomical status of heads/parts/regions;
- whether subdivisions meaningfully differ in origin, insertion, line of action, moment arm, architecture, innervation, joint function or recruitment behaviour;
- useful positional descriptors;
- treatment of bilateral anatomy and repeated anatomical series.

Do not invent segments solely because fitness terminology uses upper/lower/inner/outer labels.

## Reference morphology

Investigate, preferably from primary human in-vivo MRI/CT evidence:

- absolute muscle volume (`cm3`);
- segment volume where available;
- relative parent-muscle volume;
- regional/whole-body relative volume where scientifically coherent;
- muscle mass only where independently useful;
- population distributions and asymmetry.

Do not pool male and female measurements into an adult-male reference merely to fill gaps.

## Architecture

For each muscle/segment where possible collect:

- measured fascicle length;
- optimal fibre/fascicle length;
- sarcomere-normalised fibre length where distinct;
- pennation angle with anatomical region, joint position and contraction state;
- muscle/tendon lengths and aponeurotic architecture where they materially affect modelling.

Do not collapse measurements from incompatible definitions into one field.

## PCSA and force scaling

Investigate physiological cross-sectional area as the principal candidate for structural force normalisation.

For every PCSA record preserve:

- source definition/equation;
- volume/mass input;
- fibre/fascicle-length input;
- pennation treatment;
- whether the value was measured, published-derived, reconstructed or estimated.

Resolve the roles of:

- muscle volume;
- anatomical CSA;
- PCSA;
- effective PCSA;
- `V^(2/3)` geometric scaling;
- specific tension;
- normalised structural force-capacity indices.

Do not store one opaque scaling constant when meaningful biological inputs can be retained instead.

## Training-induced change

Determine which quantities are immutable anatomy, population priors or dynamic user state.

Particular questions:

- how volume/CSA/PCSA change with resistance training;
- whether fascicle length and pennation adapt;
- whether hypertrophy preserves architecture proportionally;
- how structural adaptation differs from neural/task-specific strength adaptation;
- whether specific tension can be treated as fixed.

## Evidence and provenance

Prefer:

1. human in-vivo MRI/CT volumetry;
2. human in-vivo architecture/physiology;
3. high-quality human cadaver architecture where required;
4. systematic reviews/meta-analyses;
5. validated musculoskeletal models only when their derivation is clear.

Every numerical record should retain population, sample size, sex, age, training status, measurement method, entity compatibility, source and uncertainty.

Do not average incompatible studies merely to produce a complete table.

## Missing-data policy

Each desired value should be classifiable as:

- `KNOWN`
- `ESTIMABLE`
- `WEAKLY_ESTIMABLE`
- `UNKNOWN`

Every estimate must name a versioned derivation method. Unknown values should remain null rather than receiving invented precision.

## Requested deliverables

The original research request asked for:

- detailed narrative research report;
- canonical anatomy table;
- physiological evidence table;
- source/provenance registry;
- per-segment data-gap table;
- machine-readable JSON representation;
- explicit synthesis of recommended My Mettle variable classes and modelling boundaries.

The temporary machine-readable outputs were lost; the two surviving reports are being used to rebuild the production dataset deliberately.
