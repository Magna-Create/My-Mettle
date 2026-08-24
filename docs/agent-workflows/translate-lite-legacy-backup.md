# AI-assisted Lite Legacy backup translation

Use the installed `translate-my-mettle-backup` skill when asked to prepare or enrich a Lite Legacy JSON export for the Kotlin Native app. This repository file preserves the project-specific contract beside its changing implementation.

## Source of truth

Read the current implementation before changing an export:

- `app/src/main/java/dev/kian/mymettle/data/migration/LegacyV6BackupReader.kt`
- `app/src/main/java/dev/kian/mymettle/data/migration/LegacyRecruitmentResolver.kt`
- `app/src/main/assets/reference/anatomy_v1.json`
- `docs/MIGRATION.md`

Code takes priority over this workflow if they differ.

## Translation boundary

Accept only the Lite envelope `my-mettle-backup`, export version `1`, source `my-mettle-lite-legacy`, and database schema `6`. Duplicate the supplied JSON. Preserve all IDs and all profile, routine, cycle, session, set, reflection, health and setup-photo data unchanged.

The only permissible content change is adding a missing `muscleLoadModel` to an exercise, plus an ignored root-level `nativeTranslation` provenance note.

Never:

- infer recruitment from `targetMuscles`, an exercise name, or a broad display label;
- overwrite an existing model;
- fabricate allocations for unknown or archived exercises;
- rewrite workout history or programme data.

## Reviewed recruitment mapping

Map by the exact exercise ID, never a fuzzy name. Reuse a previous accepted model only when that ID is unchanged. Otherwise research/review it first.

A model requires:

```json
{
  "version": 1,
  "basis": "Evidence and any compatibility assumption.",
  "confidence": 0.6,
  "allocations": [
    {
      "muscle": "stable_segment_id_from_anatomy_v1",
      "proportion": 0.75,
      "role": "prime"
    }
  ]
}
```

Every allocation must use a current stable segment ID, `prime`/`synergist`/`stabiliser` role, a 0–1 proportion, and the allocations must total 1 (allowing normal floating-point tolerance). The `basis` must say whether a split is a compatibility reconstruction rather than a direct measured result.

The importer can expand known broad Legacy aliases for compatibility. New translations should prefer direct stable segment IDs.

## Validate and hand off

1. Run the reusable skill's static preflight and enrichment script against the current anatomy asset.
2. Read the output through `LegacyV6BackupReader` or the Native import path; static JSON validity alone is insufficient.
3. Compare source/output counts for exercises, sessions, sets and setup photos.
4. State the models enriched, deliberately untouched exercises, mapping provenance and reader/import result.
5. Return the translated file separately from the original Lite export.

The first accepted N-BIO-5.1 translation retained 24 exercises, 8 sessions, 88 sets and 3 setup photos while adding reviewed structured models to 18 active exercises. That is an acceptance reference, not a reason to copy its recruitment allocations to unmatched future exercise IDs.
