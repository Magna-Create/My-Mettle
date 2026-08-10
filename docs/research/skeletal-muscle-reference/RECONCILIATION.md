# Reconciliation of the Two Deep Research Runs

This document records what the two surviving research reports agree on, where the rerun improved the evidence, and what remains unresolved. It is intentionally limited to what the reports themselves support.

## Strong agreement

### 1. Biological hierarchy

Both reports converge on:

`Region -> Muscle Group -> Muscle -> Segment`

The **individual muscle** is the canonical biological identity. `MuscleGroup` is a classification convenience and may be many-to-many. `Segment` is subordinate to its parent muscle and does not imply that every segment is the same kind of anatomical entity.

Both recommend segment types equivalent to:

- `HEAD`
- `PART`
- `FIBRE_REGION`
- `WHOLE_MUSCLE`

The original additionally makes `anatomicalStatus` explicit; the rerun conveys the same distinction between formal anatomy and application segmentation. This distinction should survive into production.

### 2. Bilateral state

Both support a side-neutral canonical anatomy with left/right represented in the user's dynamic state rather than duplicated reference objects.

### 3. Initial multi-segment muscles

Both reports converge on the same initial nine independently addressable multi-segment muscles:

- biceps brachii — long/short heads;
- triceps brachii — long/lateral/medial heads;
- deltoid — clavicular/acromial/spinal parts;
- pectoralis major — clavicular/sternocostal/abdominal parts;
- trapezius — descending/transverse/ascending parts;
- gluteus medius — anterior/middle/posterior fibre regions;
- adductor magnus — adductor/hamstring parts;
- biceps femoris — long/short heads;
- gastrocnemius — medial/lateral heads.

Both reject arbitrary gym subdivisions such as inner/outer chest, upper/lower latissimus, upper/lower rectus abdominis and appearance-derived muscle regions as canonical v1 segments.

### 4. Repeated axial structures

Both recognise that structures such as intercostals, rotatores, interspinales and related repeated axial muscles should not automatically explode into hundreds of independently trainable records. A muscle-series/instance-level mechanism should remain possible.

### 5. Morphology versus force

Both strongly reject treating volume or mass as directly proportional to strength.

Both recommend:

- volume as the primary tissue-quantity/morphology variable;
- PCSA/effective PCSA as the more interpretable structural force-area basis where architecture is available;
- preserving PCSA equation semantics and pennation handling;
- using a normalised structural force-capacity index rather than claiming exact canonical Newton force;
- retaining `V^(2/3)` only as a weak geometric-similarity fallback when architecture is unavailable.

### 6. Specific tension

Both reports recommend **no canonical universal or muscle-specific specific-tension table in v1**. Published values remain evidence records because human in-vivo estimates are method-sensitive and materially divergent.

### 7. Static reference versus dynamic user state

Both agree that only anatomical identity/ontology is truly static in this context.

Reference population priors may include:

- volume distributions;
- fascicle/optimal fibre length;
- pennation;
- reference PCSA.

Dynamic user state may include:

- current estimated volume;
- current estimated PCSA;
- development state;
- bilateral asymmetry;
- later, potentially current fascicle length and pennation.

Neural drive, technique, fatigue and task/exercise expression belong downstream rather than in canonical muscle anatomy.

### 8. Evidence must remain first-class

Both reject storing only one unexplained "best number" per muscle. Source population, method, derivation and uncertainty must survive.

Both support the availability states:

- `KNOWN`
- `ESTIMABLE`
- `WEAKLY_ESTIMABLE`
- `UNKNOWN`

Null is preferred over an unsupported imputation.

## Material improvements in the rerun

### Adult-male Riem values were actually recovered

The original report explicitly states that it could not retrieve the sex-specific numerical Riem supplementary material and therefore did not populate adult-male means.

The rerun did retrieve sex-specific male values and reports a 49-man subset. It includes directly resolved mean volumes for many muscles.

It also reconstructs transparent parent fractions where Riem directly resolved heads:

- biceps femoris: long head ~66.5%, short head ~33.5% of summed head volume;
- gastrocnemius: medial head ~62.9%, lateral head ~37.1%.

These are **reconstructed ratios from measured male means**, not source-published canonical fractions.

### Evidence-quality representation is more explicit

The original proposed a useful convenience tier (`A1/A2/B1/B2/C/D/U`).

The rerun improves this by separating independent dimensions:

- measurement tier;
- population compatibility;
- entity compatibility;
- method compatibility;
- value source;
- availability status;
- quantitative/qualitative uncertainty.

Production should use the multidimensional representation. A single tier may later be derived for filtering/UI, but should not be the source of truth.

### PCSA semantics are clearer

The rerun explicitly separates:

- `geometricPcsaCm2` — contractile cross-sectional geometry before fibre-angle projection;
- `effectivePcsaCm2` — tendon/line-of-action projected structural area.

The original also called for versioned PCSA definitions. The rerun's naming is useful and should be retained provisionally.

## Differences that remain unresolved

### Canonical ontology counts

The original reports:

- 135 canonical muscle objects;
- 149 canonical muscle/segment rows;
- 23 independently addressable subsegments.

The rerun reports:

- 144 canonical muscles;
- 158 canonical muscle/segment rows;
- 113 physiological evidence records.

The surviving narrative reports do **not** enumerate enough of the vanished machine-readable ontology to determine exactly which nine additional canonical muscles account for the difference.

Therefore:

- do not adopt either row count as a production invariant;
- do not infer the missing entries;
- reconstruct the ontology from the agreed scope and canonical anatomical terminology before seeding Native.

### Reference data completeness

The rerun materially improves adult-male MRI volume coverage, but both reports still describe incomplete segment-level architecture. The following remain particularly weak/incomplete as harmonised adult-male segment datasets:

- biceps-brachii heads;
- triceps heads;
- deltoid parts;
- pectoralis-major parts;
- trapezius parts;
- gluteus-medius regions;
- adductor-magnus parts;
- compatible head-specific architecture for biceps femoris/gastrocnemius;
- neck, deep axial, respiratory and pelvic-floor architecture.

### Composite MRI regions

The rerun explicitly warns that some Riem ROIs are composites (for example rhomboids together or infraspinatus + teres minor). These are evidence records for a composite ROI and must not be silently divided between canonical muscles.

## Reconciled position

Use the rerun as the preferred source where it contains stronger or more specific evidence, especially adult-male MRI volume values. Use both reports for architectural/methodological decisions. Preserve disagreements and missing values rather than attempting to make the data cosmetically complete.

The production dataset is a **new deliberate reconstruction**, not an attempt to recreate the vanished Deep Research workbook byte-for-byte.
