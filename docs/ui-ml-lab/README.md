# UI/ML Lab

> **Status:** authoritative landing page for the long-lived `agent/ui-ml-lab` development line.
>
> **Initial upstream checkpoint:** `agent/n-bio-vnext-inference` at `ec1406fcaa371241974031a4c2740d433a9e8f55`.
>
> **Scope:** parallel product UI and ML experimentation. This directory does not redefine N-BIO scientific/runtime contracts.

## Purpose

The UI/ML Lab lets My Mettle explore final-intent product UX and replaceable ML infrastructure without blocking biological/backend work on N-BIO. It runs in parallel with `agent/n-bio-vnext-inference` and periodically consumes coherent upstream checkpoints.

The Lab is **not** an alternative permanent My Mettle architecture. Production source remains shared. Successful Lab work is expected to connect to, or be reconciled with, canonical My Mettle/N-BIO contracts at explicit integration gates.

## Core rules

- Intentionally disconnected, fixture-backed or mock UI is allowed when a canonical backend does not exist yet.
- Fake biology is not allowed. Compose/UI code must not invent pseudo-N-BIO calculations, heuristics or state transitions to make a mock surface appear intelligent.
- The Lab may implement the final intended UX before the corresponding N-BIO capability exists. When it does, the UI displays mock/fixture state through an explicit provider boundary.
- ML experiments must remain replaceable. A successful spike does not automatically become the permanent production runtime.
- Loose integration seams are recorded in [`INTEGRATION_LEDGER.md`](./INTEGRATION_LEDGER.md), not hidden in UI code or informal notes.
- Future phases are gated by [`PLAN.md`](./PLAN.md). A STOP gate is an implementation boundary, not a reminder that may be skipped.

## Build isolation

LAB-0 uses an additional Android **build type** rather than a product flavour or second application module.

This is the least disruptive mechanism for this repository because:

1. the existing `debug` build type and its ordinary Gradle task names remain intact;
2. release continues to use the existing base application identity and release assumptions;
3. all production Kotlin/Compose source remains in the existing `:app` module;
4. Lab-only build configuration can be added later without multiplying every source set by a flavour dimension;
5. a second app module would duplicate application configuration and create avoidable drift.

Current identities are:

| Build | Application ID | Launcher label |
| --- | --- | --- |
| Base/release | `dev.kian.mymettle` | `My Mettle` |
| Existing debug | `dev.kian.mymettle.dev` | `My Mettle Dev` |
| UI/ML Lab | `dev.kian.mymettle.ailab` | `My Mettle AI Lab` |

The existing debug identity is deliberately preserved. LAB-0 does not rename Kotlin/Java packages or the `dev.kian.mymettle` namespace.

Code can identify the Lab at compile time through `BuildConfig.UI_ML_LAB`. It is `false` for ordinary builds and `true` for the Lab build. LAB-0 does not attach product behaviour to this flag.

## Android sandbox isolation

Android assigns each application ID its own application sandbox. Therefore the installed debug app and Lab app do not share app-private state even when code uses the same logical filenames or keys.

This isolates, without a second schema or renamed database:

- Room database files such as `my-mettle.db`;
- Preferences DataStore files such as `my_mettle_settings`;
- SharedPreferences if later/elsewhere used through the normal app sandbox;
- app-private model files;
- internal files;
- cache files.

LAB-0 deliberately leaves Room at schema version 15 and leaves the database filename unchanged.

## Android component coexistence audit

At the initial LAB-0 checkpoint the application manifest declares one exported launcher activity and a non-exported rest-timer service. It declares no `FileProvider`, custom content provider, provider authority, custom package-scoped permission, deep link or custom URI scheme. No WorkManager provider is explicitly declared. The project also has no Lab-specific manifest placeholder or provider authority to rewrite.

Provider authorities added in future must derive from `${applicationId}` (or another collision-safe variant-specific value) unless a reviewed interoperability contract requires otherwise.

The manifest contains no `INTERNET` permission. LAB-0 does not add one.

## Data and backup behaviour

The Lab uses the same production database and backup code inside its own Android sandbox. The Native full-backup format remains current-schema-only. A Lab export is therefore data from the Lab sandbox, not an implicit view into the normal/debug app's database.

Do not create a separate Room schema merely to isolate Lab data. Canonical shared schema changes require the LAB-5 cross-branch database gate.

## AI dependency baseline

The upstream branch already contains ML Kit Prompt API dependencies for existing N-BIO context-interpretation work. LAB-0 does not add, upgrade or repurpose any AI/ML runtime dependency. New Lab runtime choices are deferred to LAB-1 and the LAB-2A research gate.

## Where to go next

- [`PLAN.md`](./PLAN.md): programme phases and STOP gates.
- [`INTEGRATION_LEDGER.md`](./INTEGRATION_LEDGER.md): intentional loose seams and future owners.
- [`AI_RUNTIME_CONTRACT.md`](./AI_RUNTIME_CONTRACT.md): replaceable prompt-provider principles.
- [`EQUIPMENT_VISION_CONTRACT.md`](./EQUIPMENT_VISION_CONTRACT.md): observation → interpretation → derivation → validation contract.
- [`UX_DECISIONS.md`](./UX_DECISIONS.md): agreed UX decisions that LAB-0 records but does not implement.
- [`SYNC_POLICY.md`](./SYNC_POLICY.md): N-BIO ↔ Lab checkpoint and history policy.

LAB-0 ends after build isolation, governance, verification and CI protection are complete. It does **not** start LAB-1 automatically.
