# My Mettle

Native Android rebuild of My Mettle, written in Kotlin with Jetpack Compose.

## Repository role

This repository is the canonical native application. The first product scope is the existing My Mettle Lite experience: workouts, exercise library, profile/history, local backup, progression and Android-native integrations. It is deliberately the foundation for the wider My Mettle product rather than a temporary Lite fork.

Migration reference: `Magna-Create/My-Mettle-Lite-Legacy`.

## Android identity

- Release: `dev.kian.mymettle` — **My Mettle**
- Debug: `dev.kian.mymettle.dev` — **My Mettle Dev**

The debug build is intentionally installable alongside Lite Legacy during migration.

## Current stack

- Kotlin 2.3.21
- Jetpack Compose / Material 3
- Room
- DataStore
- Navigation Compose
- compile/target SDK 36
- min SDK 28

AGP 8.13.2 is intentionally used for the migration foundation because it has a known stable path with Kotlin 2.3 and KSP while the Room schema is established. Toolchain modernization can happen independently after the data migration layer is proven.

## Termux build

The repository includes a small POSIX `gradlew` bootstrap pinned to Gradle 8.13. It downloads that distribution into the normal Gradle cache on first use and reuses it afterwards; no globally installed Gradle version is required.

The Android build targets JVM 17. In Termux, the bootstrap automatically uses `$PREFIX/lib/jvm/java-17-openjdk` when `openjdk-17` is installed, avoiding Gradle desktop-Linux toolchain discovery on Android.

From the repository root:

```sh
pkg install -y openjdk-17 curl unzip
./gradlew :app:assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/My-Mettle-Dev.apk
```

The debug application can be installed alongside My Mettle Lite Legacy. Do not uninstall Lite Legacy during migration.

On first launch, an empty native database presents **Bring over My Mettle Lite**. Choose a Lite JSON backup once; subsequent native records are not overwritten by that import flow.

## Current native workout checkpoint

N2 currently includes:

- Room-backed ψ / φ / π / & programme selection;
- four runtime modes A / B / C / D through a central policy layer;
- persisted mid-session mode switching;
- load calculator and load/repetition/duration/distance set entry;
- Android-native foreground rest timer and notification controls;
- exercise completion/reopen and per-exercise reflection;
- mode-relative session achievement and whole-session review;
- completed-workout History.

The current visual layer intentionally stays near standard Material 3 / Material You while interaction and data behaviour are validated on-device.

## Biological foundation checkpoint

N-BIO-1 and N-BIO-2 now share one foundation:

- typed anatomy, reference-physiology and exercise domain models;
- generated runtime assets for 142 canonical muscles, 164 segment records and the selected healthy-adult-male v0.1 profile;
- Room `muscle`, `muscle_segment`, `reference_profile` and `reference_physiology_prior` tables seeded from those assets;
- exercise execution profiles with equipment/load-resolution constraints;
- recruitment allocations addressed only by stable muscle-segment IDs;
- no exercise-owned progression field or free-text target-muscle persistence.

See [`docs/N_BIO_FOUNDATION.md`](docs/N_BIO_FOUNDATION.md).

N-BIO-3 adds the first independent programme/session targets and generated prescription boundary:

- Room `programme_target`, `session_target` and `session_exercise_target` tables;
- exercise prescriptions bound to execution profiles and target IDs;
- `RoutineSlot` no longer owns a planned load;
- prescribed load/model provenance remain immutable session snapshots;
- raw set evidence stores objective performed work and session context.

See [`docs/N_BIO_TARGETS_PRESCRIPTIONS.md`](docs/N_BIO_TARGETS_PRESCRIPTIONS.md).

N-BIO-4 adds the recomputable user-state and inference scaffold:

- immutable, versioned inference runs tied to reference/recruitment/component model versions;
- per-set, per-segment stimulus estimates kept separate from raw workout evidence;
- user muscle-state snapshots with development, morphology, stimulus and recovery fields kept distinct;
- per-execution-profile observed performance anchors behind a replaceable translation boundary;
- an explicit full-history replay path that is never triggered by ordinary navigation.

See [`docs/N_BIO_USER_STATE_INFERENCE.md`](docs/N_BIO_USER_STATE_INFERENCE.md).

N-BIO-5 makes workout resolution target-driven:

- Room `programme_mode_constraint` and `session_constraint` records replace persisted per-slot mode recipes;
- modes are whole-session target, working-set, exercise and optional time budgets;
- pinned routine slots are candidate preferences rather than the programme's only identity;
- target coverage selects whole movements before dose is distributed, so reduced modes can omit
  low-priority exercises instead of prescribing one set everywhere;
- N-BIO-4 same-profile performance anchors now feed the prescription boundary without inventing progression.

See [`docs/N_BIO_PROGRAMME_RESOLVER.md`](docs/N_BIO_PROGRAMME_RESOLVER.md).

N-BIO-5.1 makes that stack observable and testable:

- Room v9 snapshots the same-profile evidence behind every generated load suggestion;
- Settings exposes a debug-only biological developer screen with reference counts, targets,
  constraints, selected/rejected candidates and inference outputs;
- full-history inference replay is an explicit visible task rather than a navigation side effect;
- working sets record performed work without a subjective effort rating;
- an unstarted exercise can be replaced by a target-compatible execution profile, receiving a
  suggestion only from that replacement profile's own evidence;
- resolver/inference state can be exported without exporting the full workout backup.

See [`docs/N_BIO_OBSERVABILITY.md`](docs/N_BIO_OBSERVABILITY.md).

## Migration rule

We are not translating the Capacitor implementation line-for-line. Existing data and useful behaviour are the compatibility contract; known UX problems are corrected as their native equivalents are built.

See [`docs/MIGRATION.md`](docs/MIGRATION.md).
