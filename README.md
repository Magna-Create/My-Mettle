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
- raw set evidence is ready to store RIR and effort provenance.

See [`docs/N_BIO_TARGETS_PRESCRIPTIONS.md`](docs/N_BIO_TARGETS_PRESCRIPTIONS.md).

## Migration rule

We are not translating the Capacitor implementation line-for-line. Existing data and useful behaviour are the compatibility contract; known UX problems are corrected as their native equivalents are built.

See [`docs/MIGRATION.md`](docs/MIGRATION.md).
