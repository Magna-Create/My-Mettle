# N-BIO foundation — combined N-BIO-1 / N-BIO-2

## Scope

This checkpoint establishes the biological/reference domain and replaces the exercise-owned muscle/progression model in one destructive development schema change.

Room is now version 5. There is intentionally no v4→v5 preservation migration: Native is not yet the authoritative workout record and Lite Legacy remains the live migration source.

## Runtime reference data

The research evidence corpus remains under `docs/research/skeletal-muscle-reference`. Consumer runtime data is generated deliberately into:

```text
app/src/main/assets/reference/
├── anatomy_v1.json
├── reference_profile_healthy_adult_male_v1.json
└── reference_manifest.json
```

Generate or verify it with:

```sh
python3 tools/reference_data/generate_reference_assets.py
python3 tools/reference_data/generate_reference_assets.py --check
```

The manifest records source-file hashes and exact counts. Runtime validation requires:

- 142 canonical muscles;
- 164 generated segment records;
- 66 reference-prior rows, including 47 selected adult-male morphology values;
- an explicit known, structural-prior or latent policy for every independently tracked child segment.

Parent-only morphology remains a parent reference prior. It does not manufacture child fractions or create a competing parent development state.

## Exercise rewrite

`ExerciseEntity.progressionStep`, `ExerciseTargetMuscleEntity`, `ExerciseMuscleLoadEntity` and the old load-experiment entity are removed.

An exercise now owns one or more execution profiles. A profile may contain:

- equipment context;
- physical load-resolution constraints;
- recruitment allocations addressed by stable `muscleSegmentId` values.

The old `progressionStep` is accepted only at the Legacy import boundary and translated into the default execution profile's load increment. It is not treated as progression.

Legacy structured muscle allocations remain transient free text only while the importer reads the JSON. An explicit versioned alias translator resolves them to stable segments before Room writes anything. Broad compatibility labels are transparently expanded and divided across their resolved segments; unknown labels fail import rather than entering the canonical model.

`ExerciseMemory.targetMuscles` is not promoted when no structured model exists.

## Domain / persistence boundary

Runtime anatomy and physiology are represented under `domain/anatomy` and `domain/physiology`. Exercise Library consumers now receive `domain.exercise.Exercise`, not Room entities. Room remains the storage implementation.

Historical session entities remain unchanged in this checkpoint. Target/prescription separation and richer raw performance evidence belong to N-BIO-3.

## Follow-on structural patch

N-BIO-3 is implemented in [`N_BIO_TARGETS_PRESCRIPTIONS.md`](N_BIO_TARGETS_PRESCRIPTIONS.md): independent programme/session targets now resolve into model-versioned session prescriptions, while prescribed-load history remains on the session snapshot rather than the routine assignment.
