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
- Jetpack Compose
- Room
- DataStore
- Navigation Compose
- compile/target SDK 36
- min SDK 28

AGP 8.13.2 is intentionally used for the migration foundation because it has a known stable path with Kotlin 2.3 and KSP while the Room schema is established. Toolchain modernization can happen independently after the data migration layer is proven.

## Migration rule

We are not translating the Capacitor implementation line-for-line. Existing data and useful behaviour are the compatibility contract; known UX problems are corrected as their native equivalents are built.

See [`docs/MIGRATION.md`](docs/MIGRATION.md).
