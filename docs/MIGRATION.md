# Native migration contract

## Source of truth

The migration source is `Magna-Create/My-Mettle-Lite-Legacy`.

The frozen reference branch is:

`archive/migration-baseline-2026-08-09`

It points at commit `8bfcd6d31211e1d0035b463606e18ea12dca3245` (`Rebase workout usability patches onto Lite app`). This is important: Lite Legacy `main` is older and does **not** contain the current workout-usability/setup-photo/muscle-load work.

The native application must preserve user data and useful behaviour, not the Capacitor implementation itself. Known interaction problems should be corrected as each feature is rebuilt.

## Legacy persistence baseline

The frozen Lite Legacy baseline stores the primary application state as one `AppDatabase` object in IndexedDB:

- database: `kian-gym-app`
- object store: `app-state`
- key: `primary`
- application schema version: `6`

The current backup format wraps that database in this envelope:

```text
format: "my-mettle-backup"
exportVersion: 1
exportedAt: ISO timestamp
source: "my-mettle-lite-legacy"
database: AppDatabase
```

The Legacy restore path also accepts older bare-database backups, migrates them to the current schema, then validates current routine/cycle/active-session references.

The Kotlin app will not copy the single-object persistence pattern. It imports the Legacy representation into normalized Room tables and uses DataStore only for lightweight preferences.

### Routine-history identity discovered from a real export

Lite Legacy treats a routine slot id as the stable logical identity of that slot and deliberately reuses it across immutable routine versions. A slot can therefore appear in several routine versions while its position or A/B/C prescription changes.

Native persistence must **not** key routine history by `slotId` alone. A historical slot occurrence is keyed by:

```text
(routineVersionId, slotId)
```

A mode prescription is keyed by:

```text
(routineVersionId, slotId, mode)
```

This was verified against a real field export before the native workout slice was built. It prevents newer routine versions from overwriting older slot/prescription history during migration.

## Identity

The active Lite Legacy Android application is `dev.kian.mymettle.litelegacy` / `My Mettle Lite`. The native application deliberately starts with a separate clean identity:

- release: `dev.kian.mymettle` / `My Mettle`
- debug: `dev.kian.mymettle.dev` / `My Mettle Dev`

This allows the native debug app and Lite Legacy to remain installed side-by-side during migration.

## Schema-v6 model families to preserve

- user profile
- app/rest-timer settings
- body measurements
- exercises and tracking profiles
- exercise memory/setup metadata
- setup photographs
- per-exercise `muscleLoadModel`
- versioned routines, days, slots and A/B/C prescriptions
- training cycles
- sessions
- session exercises
- set records
- exercise reflections
- historical load experiments (readable in Legacy, but intentionally not copied into the new exercise-owned model)
- health observations
- health integration state

Important workout semantics that must survive migration include:

- day symbols `ψ`, `φ`, `π`, `&`
- modes `A`, `B`, `C`
- importance `principal`, `core`, `accessory`
- tracking metrics `load_reps`, `reps`, `duration`, `distance`
- load relationships `external`, `assistance`, `bodyweight`, `bodyweight_plus_external`, `none`
- entry basis `total`, `per_hand`, `per_side`
- prescribed/additional/warm-up sets
- exercise and session status values
- historical snapshots of exercise name, importance, tracking and bodyweight

### Setup photographs

Legacy stores setup photos inside `ExerciseMemory.setupPhotos` as:

- `id`
- JPEG `dataUrl`
- `createdAt`
- `width`
- `height`

Capture currently scales the longest edge to at most 1600 px, encodes JPEG at quality 0.72, falls back to 0.58 if needed, and rejects data URLs still above 2,500,000 characters. Each exercise currently permits up to 12 setup photos.

Native migration should decode each JPEG data URL once into app-private media storage and keep only its relative file reference plus metadata in Room. The canonical My Mettle backup/archive must include those media files rather than reinflating them into the database.

### Legacy muscle-load model

Schema v6 supports an optional model on each exercise:

```text
version: 1
basis: string
confidence: number
allocations[]:
  muscle: string
  proportion: number
  role: prime | synergist | stabiliser
```

Room v5 translates this through an exercise execution profile into `recruitment_allocation` rows addressed by stable `muscleSegmentId` values. The free-text label exists only inside the transient import snapshot. An explicit versioned alias translator handles known broad Legacy labels; an unknown label fails import rather than becoming new canonical anatomy.

Legacy `progressionStep` is translated only into the default execution profile's physical load increment. It no longer represents progression.

Room v6 does not persist `RoutineSlot.plannedLoad`. Historical Legacy session recommendations are
imported as `SessionExercise.prescribedLoad`; future prescriptions carry forward performed evidence
through the selected execution profile. Because Legacy had no separate target-intent model, PRIME
recruitment on pinned routine assignments is projected once into independent programme/session
targets with explicit `legacy-prime-recruitment-projection-v1` provenance.

The importer must also accept exports where this optional model is absent. `ExerciseMemory.targetMuscles` is not silently promoted into recruitment.

## Native rollout

### N0 — foundation

- Native Gradle/Compose project builds.
- Correct application identity.
- Correct Lite Legacy baseline frozen.
- Visual language smoke-tested in Compose.
- Normalized Room v1 foundation created.
- Android CI established.

### N1 — data foundation

- Define stable native domain models separately from database entities.
- Add versioned Room migrations from database version 1 onward.
- Define the canonical My Mettle interchange archive.
- Build and test a schema-v6 / backup-envelope-v1 Lite Legacy importer.
- Decode Legacy setup-photo data URLs into native app-private JPEG files.
- Translate `muscleLoadModel` into execution-profile recruitment without persisting free-text anatomy.
- Preserve stable logical routine-slot identities without collapsing immutable routine-version history.
- Validate an actual Lite Legacy export by counts, IDs and relationships before cutover.

No native screen becomes the daily-driver source of truth until this layer is reliable.

### N2 — first workout vertical slice

`programme → mode → workout → set → rest timer → complete exercise → complete session → history`

Implement outstanding interaction changes while rebuilding:

- calculator load entry (for example `6.5 × 2 → 13`)
- timer `−15s / +15s`
- safer cancel/minimise placement and easier thumb reach
- remove obsolete timer-popup delay
- keyboard/focus behaviour that does not trap incorrect values
- proper Android back navigation
- completed exercise can be reopened/marked undone
- session mode can change during an active workout
- corrected previous-weight lookup for repeated exercises/sessions
- whole-session review alongside per-exercise reflection
- session celebration scored relative to the selected mode

### N3 — exercise library and information

- Rebuild exercise/routine editing.
- Replace overlong workout cards with progressive disclosure.
- Rework setup, hints and notes.
- Native setup-photo capture/gallery using the migrated media model.
- Sticky close/back affordance for detailed setup views.
- Add embedded external-reference viewing later for supported YouTube/TikTok/Instagram links.

### N4 — profile, history, settings and backup

- User/body measurements.
- Editable session history.
- Timer and app preferences.
- Export/import using the canonical archive.
- Validate an actual Lite Legacy export against native counts and relationships.

### N5 — progression foundation

- Deterministic progression page.
- Load/repetition trends and PBs.
- Body metrics.
- Consistency and workload views.
- New carry-forward weight suggestion structure:

`body capability → muscle group capability → per-muscle activation → exercise translation`

### N6 — structured progress capture

Start with reproducibility rather than depth:

- body-area capture modes
- framing/distance guidance
- ghost overlays
- consistent crop/orientation
- exposure/lighting normalization experiments
- aligned before/after comparison

Depth, multi-camera, ARCore and Samsung-specific capture experiments follow only after the baseline capture flow works well.

## Cutover rule

Lite Legacy remains usable during migration. It is archived only after the native app has been used for real workouts and has demonstrated complete data transfer, reliable backup/restore and parity for the daily-driver flows.
