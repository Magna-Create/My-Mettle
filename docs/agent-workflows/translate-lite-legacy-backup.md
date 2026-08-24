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

Do not edit the Lite `database` object. Add one root-level `nativeTranslation` supplement containing reviewed Native recruitment profiles. The Lite `muscleLoadModel.proportion` field is historical input only: it represented a conserved share and is not N-BIO's independent muscle-local exposure coefficient.

Never:

- infer recruitment from `targetMuscles`, an exercise name, a broad display label, or an old conserved `proportion`;
- overwrite any Lite field;
- fabricate allocations for unknown or archived exercises;
- rewrite workout history or programme data.

## Reviewed recruitment mapping

Map by the exact exercise ID, never a fuzzy name. Reuse a previous accepted model only when that ID is unchanged. Otherwise research/review it first.

A translation supplement requires:

```json
{
  "version": 1,
  "recruitmentSemantics": "independent-muscle-local-exposure-v1",
  "recruitmentProfiles": [
    {
      "exerciseId": "exact_lite_exercise_id",
      "modelVersion": "reviewed-profile-v1",
      "basis": "Evidence, execution conditions and uncertainty.",
      "confidence": 0.6,
      "allocations": [
        {
          "muscleSegmentId": "stable_segment_id_from_anatomy_v1",
          "weighting": 0.75,
          "role": "prime",
          "applicableRom": null,
          "applicableTechnique": null,
          "resistanceCurveClass": null
        }
      ]
    }
  ]
}
```

Every allocation must use a current stable segment ID, `prime`/`synergist`/`stabiliser` role, and a 0–1 independent weighting. Allocations do not need to sum to 1 and must not be normalised into a conserved pie. The `basis` must identify evidence, uncertainty and execution assumptions. Role remains descriptive metadata; do not apply an automatic stabiliser multiplier.

The Native reader rejects aliases and unknown segment IDs in this supplement. Research/review each current-routine exercise against its exact execution conditions.

## Validate and hand off

1. Run the reusable skill's static preflight and enrichment script against the current anatomy asset.
2. Read the output through `LegacyV6BackupReader` or the Native import path; static JSON validity alone is insufficient.
3. Confirm every exercise in the current routine has a reviewed recruitment profile; archived/historical unknowns may remain explicitly unknown.
4. Compare source/output counts for exercises, sessions, sets and setup photos.
5. State the models enriched, deliberately untouched exercises, evidence/provenance and reader/import result.
6. Return the translated file separately from the original Lite export.

The prior N-BIO-5.1 translation is historical context only. Its conserved proportions must not be copied into N-BIO-6 independent weighting fields.
