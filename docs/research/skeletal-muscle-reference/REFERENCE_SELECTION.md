# Reference selection policy

## Why this exists

The evidence tables store what individual studies measured. My Mettle still needs a separate answer to a different question: **which evidence should initialise a particular reference population model?**

A reference profile must therefore be a versioned selection over evidence, not a second copy of anatomy and not an overwrite of the source records.

## Current profile

`healthy_adult_male_v0_1` is the first partial reference-population profile.

It currently selects only **reference muscle/segment volume** where the source is:

- human in vivo;
- directly measured by MRI;
- sex-specific male data;
- broadly compatible with a healthy adult reference population;
- resolved to the same canonical unit or segment used by My Mettle.

The current selected morphology source is `RIEM_2026`.

## What is deliberately excluded

### Composite source ROIs

A source measurement such as `infraspinatus + teres minor` is useful evidence but cannot initialise either canonical muscle independently. Composite observations therefore remain in the evidence table with `CONTEXT_ONLY_COMPOSITE` and are excluded from the profile.

### Unsupported segment splitting

If Riem gives a whole biceps-brachii volume, that value can initialise the parent muscle reference. It does **not** justify dividing the volume between long and short heads.

The same rule currently affects segment-level morphology for:

- biceps brachii;
- triceps brachii;
- deltoid;
- pectoralis major;
- trapezius;
- gluteus medius;
- adductor magnus.

Biceps-femoris and gastrocnemius heads are different because the source resolves those heads directly.

### Charles 2019 architecture

`CHARLES_2019` is unusually useful because one coherent in-vivo study reports volume, muscle length, optimal-fibre-length estimates, pennation and PCSA across 20 lower-limb muscles.

It is nevertheless a ten-person mixed-sex cohort. Its values therefore remain `CONTEXT_ONLY_MIXED_SEX` in v0.1 rather than being silently promoted into the adult-male profile.

This does **not** mean My Mettle cannot use them later. It means their use must be an explicit model decision, e.g. as a contextual architecture prior with higher uncertainty.

## Future cross-source inference

A future model may combine an adult-male morphology observation from Riem with architecture evidence from Charles or another source to estimate PCSA. Such a quantity must be recorded as a derived model value, not a direct physiological measurement.

For example:

`Riem male volume + selected optimal fibre-length prior -> MY_METTLE_GEOMETRIC_PCSA_V1`

and, when a pennation prior is selected:

`geometric PCSA + pennation prior -> MY_METTLE_EFFECTIVE_PCSA_V1`

That derived value should normally begin as `WEAKLY_ESTIMABLE` where the inputs come from different populations/studies. Its provenance must retain every input observation and the equation version.

## Reference profile versus user state

The reference profile answers:

> What morphology/architecture is a reasonable population prior for this canonical structure?

The user state answers:

> What do we currently infer about this user's left/right structure and development?

The user state can diverge indefinitely from the reference profile without mutating the reference data.

## Current state

`data/reference_profile_healthy_adult_male_v0_1.csv` currently contains **47 selected Riem adult-male volume observations**. This is intentionally partial.

No architecture value is yet promoted into the adult-male profile. No specific-tension value is canonicalised. No arbitrary segment fraction is used.
