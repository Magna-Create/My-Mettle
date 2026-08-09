# Native migration contract

## Source of truth

The migration source is `Magna-Create/My-Mettle-Lite-Legacy`.

A frozen reference branch was created at:

`archive/migration-baseline-2026-08-09`

The native application must preserve user data and useful behaviour, not the Capacitor implementation itself. Known interaction problems should be corrected as each feature is rebuilt.

## Legacy persistence baseline

Lite Legacy currently stores the primary application state as one `AppDatabase` object in IndexedDB:

- database: `kian-gym-app`
- object store: `app-state`
- key: `primary`
- application schema version: `4`

The legacy backup restore path accepts the same application-state shape and migrates it through the legacy migration functions before validation.

The Kotlin app will not copy this single-object persistence pattern. It will import the legacy representation into a normalized Room database and use DataStore only for lightweight preferences.

## Identity

The old Capacitor Android package is `dev.kian.gymapp` and still contains historical `Gym App` naming. The native application deliberately starts with a clean identity:

- release: `dev.kian.mymettle` / `My Mettle`
- debug: `dev.kian.mymettle.dev` / `My Mettle Dev`

This allows the native debug app and Lite Legacy to remain installed side-by-side during migration.

## Legacy model families to preserve

The schema-v4 source contains the following persistent model families:

- user profile
- app/rest-timer settings
- body measurements
- exercises and tracking profiles
- exercise memory/setup metadata
- versioned routines, days, slots and A/B/C prescriptions
- training cycles
- sessions
- session exercises
- set records
- exercise reflections
- experiments
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

Setup-photo media and the richer `muscleLoadModel` source-pack object are migration requirements, but their current persistence/import paths need to be pinned separately before the importer is considered complete.

## Native rollout

### N0 — foundation

- Native Gradle/Compose project builds.
- Correct application identity.
- Lite Legacy baseline frozen.
- Visual language smoke-tested in Compose.
- Migration contract documented.

### N1 — data foundation

- Define normalized Room entities and relationships.
- Define stable domain models separately from database entities.
- Add versioned database migrations from database version 1 onward.
- Define the canonical My Mettle interchange archive.
- Build and test a schema-v4 Lite Legacy importer.

No native screen should become the daily-driver source of truth until this layer is reliable.

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
- Migrate setup photographs and horizontal gallery behaviour.
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
