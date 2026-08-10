# Remaining skeletal-muscle research gaps

## Status

These items are **no longer blockers for the foundational Native rewrite**.

They should continue in parallel and improve reference/model priors without changing the canonical architecture described in `NATIVE_HANDOFF.md`.

---

## Priority A — useful before serious inference/progression tuning

### Biceps brachii head morphology

Current state:

- canonical long/short heads are strong;
- adult-male whole-parent volume exists through Riem;
- head-specific architecture exists in cadaver/ultrasound literature;
- no selected adult-male long/short **volume split** is currently trusted.

Needed:

- compatible head-specific volume fractions, ideally living adult males;
- head-specific fibre architecture with clearly stated posture/measurement convention.

Until then: `PARENT_LATENT_ALLOCATION`.

### Triceps brachii three-head physiology

Current state:

- canonical anatomy strong;
- adult-male parent volume strong;
- partial head architecture exists;
- no coherent three-head adult-male volume + PCSA partition has been selected.

Needed:

- long/lateral/medial head volume fractions;
- compatible head PCSA/fascicle data.

Until then: `PARENT_LATENT_ALLOCATION`.

### Deltoid parts

Current state:

- clavicular/acromial/spinal segmentation is strong;
- adult-male parent volume exists;
- primary shoulder architecture literature has been identified.

Needed:

- durable extraction of part-specific architecture from primary papers;
- ideally living-adult part-specific morphology.

Until then: `PARENT_LATENT_ALLOCATION`.

### Pectoralis-major parts

Current state:

- clavicular/sternocostal/abdominal parts retained;
- whole-parent adult-male volume exists;
- clavicular-versus-sternal architecture evidence exists;
- source partitions do not cleanly map onto My Mettle's three-part model.

Needed:

- part-specific volume/PCSA compatible with the three-part ontology;
- especially the abdominal part.

Until then: `PARENT_LATENT_ALLOCATION`.

### Adductor magnus

Current state:

- two-part adductor/ischiocondylar distinction is anatomically strong;
- whole-parent adult-male volume exists;
- modern in-vivo DTI evidence exists but uses a different three-part partition.

Needed:

- an explicit validated crosswalk or a two-part compatible dataset.

Until then: independent state, latent absolute share.

### Gluteus medius

Current state:

- regional organisation is clearly real;
- My Mettle deliberately uses a coarse anterior/middle/posterior application model;
- primary anatomy describes a more complex compartment pattern.

Needed:

- evidence that can be mapped reproducibly to the coarse three-region model;
- or a later decision to adopt a more anatomically faithful compartment model.

Until then: `PROVISIONAL_TRACK`, high uncertainty, no fixed absolute region shares.

---

## Priority B — useful for improving whole-body structural priors

### Neck

Deep/major neck architecture exists in cadaver literature, but coherent young-adult-male in-vivo morphology is weak.

Future pass:

- SCM/scalene/deep-flexor/suboccipital volume and architecture;
- retain variable anatomical presence where relevant.

### Deep back / axial structures

Current broad MRI evidence frequently uses aggregate ROIs.

Future pass:

- resolve erector-spinae components where defensible;
- improve multifidus and other deep intrinsic architecture priors;
- preserve serial-family representation rather than multiplying user-state objects by vertebral level.

### Respiratory thorax

Future pass:

- diaphragm morphology/architecture;
- intercostal layers and smaller thoracic muscles only if the training/respiratory model eventually makes them useful.

### Pelvic floor / perineum

Dedicated male-specific reference search remains useful.

This is low priority for the first gym-focused inference engine unless user goals expand into pelvic-floor training.

### Small/accessory muscles and unresolved Riem composites

Continue resolving source composites only when another compatible source can genuinely separate them.

Never divide aggregate ROIs by count.

---

## Priority C — separate research domain: exercise recruitment

This is the next major research programme after the biological substrate.

It should investigate:

- exercise → muscle-segment recruitment;
- prime/synergist/stabiliser roles;
- execution-profile effects;
- joint angle and muscle-length effects;
- resistance curves / moment arms;
- machine/cable/free-weight differences;
- what EMG can and cannot justify;
- how recruitment weightings should be normalised;
- confidence/provenance per allocation.

This work should populate `ExecutionProfile` / `RecruitmentProfile`, **not alter canonical anatomy**.

---

## Priority D — inference/progression research

Do after the data/domain scaffolding exists so formulas can be tested against real app data.

Open questions include:

- target-dose units;
- set stimulus function;
- effect of RIR, reps, load and ROM;
- decay/recovery model;
- long-term development update;
- relationship between structural capacity and exercise-specific capability;
- learning rate / uncertainty update for new exercises;
- how to infer latent segment allocation from multiple exercise observations;
- how rapidly architecture itself should be allowed to adapt.

These are model choices, not canonical-data questions.

---

## Already sufficiently resolved for Native foundation

The following should not be reopened merely because better studies may later appear:

- canonical anatomy versus user state separation;
- stable segment IDs;
- side-neutral reference anatomy + bilateral user state;
- TRACK / PROVISIONAL_TRACK / SHARED_PARENT distinction;
- source evidence versus selected reference profile;
- volume versus architecture versus derived PCSA separation;
- no universal specific-tension constant in v1;
- versioned PCSA/effective-PCSA equations;
- explicit `V^(2/3)` low-confidence fallback;
- parent-latent segment allocation;
- raw performance as immutable evidence;
- derived user physiology as versioned/recomputable state;
- exercise recruitment separate from target intent;
- progression owned by the user model rather than the exercise.

These form the implementation contract unless later evidence reveals a genuinely fundamental error.
