# My Mettle biological prior policy v0.1

## Purpose

The research corpus will never provide a perfectly harmonised young-adult-male measurement of volume, fibre length, pennation and PCSA for every canonical muscle and every independently tracked segment.

That is no longer treated as a blocker.

This document defines what the first My Mettle model is allowed to infer when evidence is incomplete, and — equally importantly — what it is **not** allowed to fabricate.

The objective is to make missing biology a model-state problem rather than a database-design problem.

---

## 1. Evidence is not the reference profile

All published values remain source observations.

A reference profile is a versioned selection and/or model prior built from those observations.

```text
source evidence
    ↓ selection / transformation rule
reference prior
    ↓ personal scaling
user prior state
    ↓ workout observations
user posterior state
```

Changing a selection rule must not overwrite the underlying source evidence.

---

## 2. Morphology selection order

For a desired anatomical entity, prefer in order:

1. exact entity, healthy/general adult male, in-vivo direct volume;
2. exact entity, sex-stratified population containing a usable male estimate;
3. exact entity, healthy mixed-sex in-vivo volume, retained with population uncertainty;
4. compatible parent-muscle volume where the child segment is known but not separately measured;
5. no numerical morphology prior.

Riem 2026 currently supplies the main level-1/2 morphology backbone.

A parent value **must not** be divided equally among children merely because the number of children is known.

---

## 3. Architecture selection order

For fascicle/fibre length, pennation and PCSA, prefer:

1. exact entity/segment measured in vivo in a compatible adult-male population;
2. exact entity/segment measured in vivo in a healthy mixed-sex population;
3. exact entity/segment from a high-quality cadaver architecture study;
4. compatible whole-parent architecture where child architecture is unavailable;
5. transparent geometric fallback where volume exists;
6. null.

The `referenceEligibility` and population/method fields remain attached throughout.

Cadaver evidence can be a useful structural prior without becoming an alleged young-male measurement.

---

## 4. PCSA derivation rules

Two concepts remain separate:

```text
geometricPcsa = volume / optimalFibreLength

effectivePcsa = geometricPcsa × pennationProjection
```

The exact projection equation is versioned in the equation registry.

A source-native published PCSA is stored as its own observation with its own source definition. It is not overwritten by a My Mettle reconstruction.

A reconstructed PCSA requires compatible inputs. Combining an adult-male MRI volume with a cadaver fibre length is permitted only as an explicit **model prior**, never as a measured population value.

---

## 5. The volume^(2/3) fallback

`V^(2/3)` is permitted only when:

- a usable volume prior exists;
- no compatible fibre architecture is available;
- a structural-capacity proxy is still useful to the downstream model.

It must be tagged:

```text
valueSource = ESTIMATED
estimationMethod = GEOMETRIC_SIMILARITY_VOLUME_2_3
availability = WEAKLY_ESTIMABLE
uncertainty = HIGH
```

It is a dimensional fallback, not a physiological claim that differently shaped muscles are geometrically similar.

---

## 6. Tracked segments with unknown absolute shares

This is the most important rule for Native.

A segment may be a legitimate independent longitudinal target **without** having a known adult-male share of its parent's volume or PCSA.

For example:

```text
Pectoralis major
├── clavicular
├── sternocostal
└── abdominal
```

can have independent development states even though current evidence does not justify assigning each part a fixed percentage of the parent's 424 cm³ reference volume.

In this situation:

```text
parent structural capacity = known / estimable
segment absolute share = LATENT
segment developmentIndex = independently estimable
```

The model must not initialise the latent shares as canonical 1/N anatomical fractions.

### Native v0 behaviour

Until segment-share evidence or sufficient user observations exist:

- the parent muscle owns the absolute structural-capacity prior;
- each independently tracked segment begins with a dimensionless relative development state centred on the reference condition;
- exercise recruitment can target those segment states independently;
- absolute parent-to-segment capacity allocation remains latent;
- user performance can later update the latent allocation if the inference model supports it.

This lets targeting exist before perfect segment morphometry exists.

---

## 7. Segment prior tiers

### Tier S1 — direct morphology + usable segment architecture

Examples currently closest to this tier:

- biceps femoris long/short heads;
- gastrocnemius medial/lateral heads.

A provisional segment structural-capacity estimate is allowed, while retaining cross-source uncertainty.

### Tier S2 — parent morphology + quantitative segment structural proportions

Example:

- trapezius descending/transverse/ascending parts via weak part-specific PCSA evidence.

A provisional structural share is allowed but carries high uncertainty.

### Tier S3 — strong segment anatomy + partial/incompatible quantitative physiology

Current examples:

- biceps brachii heads;
- triceps heads;
- deltoid parts;
- pectoralis-major parts;
- adductor-magnus parts.

Track segment development independently, but keep absolute capacity allocation latent unless a compatible source is selected later.

### Tier S4 — useful application fibre regions with non-matching anatomical compartment data

Current example:

- gluteus medius anterior/middle/posterior regions.

Independent state is provisional and uncertainty should remain higher than for formal heads/parts.

---

## 8. Whole muscles without good architecture

If a whole muscle has a usable reference volume but no suitable architecture:

1. store volume normally;
2. leave fibre length/pennation/PCSA null;
3. optionally derive a low-confidence `V^(2/3)` structural proxy for exercise translation;
4. allow user performance to recalibrate the exercise-to-body translation over time.

If even volume is absent, the canonical muscle still exists. Its structural prior is simply `UNKNOWN` until evidence or user inference becomes available.

No dummy physiological number is required to satisfy the schema.

---

## 9. Parent/child state relationship

A segmented muscle must not have a competing whole-muscle development state that can drift independently from its children.

Conceptually:

```text
parent morphology/state = aggregate(children + latent allocation model)
```

The parent may own shared absolute reference information while child absolute shares are latent, but longitudinal development should remain coherently derived rather than duplicated.

---

## 10. User-state uncertainty is computational, not a UX warning system

The model should retain uncertainty because it determines how quickly beliefs change after new observations.

It does **not** imply routine user-facing confidence disclaimers.

For example, a new exercise may receive a concrete prescription even when its internal translation variance is broad. The result of the set then updates the model.

---

## 11. Recalculation rule

Raw workout evidence is immutable historical evidence.

Derived physiology is versioned and recomputable:

```text
raw set history
    + reference model version
    + recruitment model version
    + inference model version
        ↓
recomputed muscle-state history / posterior
```

This allows early formulas to be replaced without losing the user's actual training history.

---

## 12. Research-completion criterion for Native

The biology layer is ready for implementation when every `TRACK` / `PROVISIONAL_TRACK` segment has one of:

- a selected direct structural prior;
- a selected weak structural-share prior;
- an explicit `PARENT_LATENT_ALLOCATION` policy.

We **do not** need every segment to possess a precise adult-male PCSA before writing Native.

The current `segment_reference_policy_v0_1.csv` satisfies this criterion for all current independently addressable segments.

Further research should improve priors without changing the fundamental data architecture.
