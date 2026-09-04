# UI/ML Lab

> **Status:** authoritative landing page for the long-lived `agent/ui-ml-lab` development line. LAB-0 is closed; LAB-1 provides the typed AI provider/lifecycle shell. LAB-2A remains the next gated phase.
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

The Lab uses an additional Android **build type** rather than a product flavour or second application module.

This remains the least disruptive mechanism for this repository because:

1. the existing `debug` build type and its ordinary Gradle task names remain intact;
2. release continues to use the existing base application identity and release assumptions;
3. all production Kotlin/Compose source remains in the existing `:app` module;
4. Lab-only build configuration can be added without multiplying every source set by a flavour dimension;
5. a second app module would duplicate application configuration and create avoidable drift.

Current identities are:

| Build | Application ID | Launcher label |
| --- | --- | --- |
| Base/release | `dev.kian.mymettle` | `My Mettle` |
| Existing debug | `dev.kian.mymettle.dev` | `My Mettle Dev` |
| UI/ML Lab | `dev.kian.mymettle.ailab` | `My Mettle AI Lab` |

The existing debug identity is deliberately preserved. The Lab does not rename Kotlin/Java packages or the `dev.kian.mymettle` namespace.

Code identifies the Lab at compile time through `BuildConfig.UI_ML_LAB`. It is `false` for ordinary builds and `true` for the Lab build. LAB-1 centralises its process-start activation in `LabAiRuntime`; normal builds do not probe the system AI provider.

## Android sandbox isolation

Android assigns each application ID its own application sandbox. Therefore the installed debug app and Lab app do not share app-private state even when code uses the same logical filenames or keys.

This isolates, without a second schema or renamed database:

- Room database files such as `my-mettle.db`;
- Preferences DataStore files such as `my_mettle_settings`;
- SharedPreferences if later/elsewhere used through the normal app sandbox;
- future app-private model files;
- internal files;
- cache files.

Room remains at schema version 15 and the database filename remains unchanged.

## Android component coexistence audit

At the initial LAB-0 checkpoint the application manifest declared one exported launcher activity and a non-exported rest-timer service. It declared no `FileProvider`, custom content provider, provider authority, custom package-scoped permission, deep link or custom URI scheme. No WorkManager provider was explicitly declared.

LAB-1 adds the shared `MyMettleApplication` class solely to provide a process-start hook. The hook delegates to the central Lab activation boundary and performs no AI probe in ordinary builds. It adds no exported component or provider authority.

Provider authorities added in future must derive from `${applicationId}` (or another collision-safe variant-specific value) unless a reviewed interoperability contract requires otherwise.

The manifest contains no `INTERNET` permission. LAB-1 does not add one.

## Data and backup behaviour

The Lab uses the same production database and backup code inside its own Android sandbox. The Native full-backup format remains current-schema-only. A Lab export is therefore data from the Lab sandbox, not an implicit view into the normal/debug app's database.

Do not create a separate Room schema merely to isolate Lab data. Canonical shared schema changes require the LAB-5 cross-branch database gate.

LAB-1 does not persist transient provider probe snapshots or the developer provider override. `AUTO` remains the normal process default.

## AI provider baseline

The upstream branch already contains ML Kit Prompt API dependencies for existing N-BIO context interpretation. LAB-1 does not add or upgrade an AI/ML dependency and does not alter `NanoNoteInterpreter`.

LAB-1 adds a separate Lab-owned provider/capability/lifecycle shell around a **read-only** system Prompt API probe plus a no-op local-fallback lifecycle. The system probe uses the existing pinned ML Kit API only to inspect status/capabilities/optional model identity; it does not generate content or call the model-download API.

The local side remains a contract only. There is no local model, local runtime, model URL, downloader or network path. Local-runtime research begins only at LAB-2A, followed by the isolated LAB-2B proof and LAB-2C integration gate.

See [`AI_RUNTIME_CONTRACT.md`](./AI_RUNTIME_CONTRACT.md) for the implemented LAB-1 semantics.

## Where to go next

- [`PLAN.md`](./PLAN.md): programme phases and STOP gates; LAB-2A is next only after LAB-1 closure.
- [`INTEGRATION_LEDGER.md`](./INTEGRATION_LEDGER.md): intentional loose seams and future owners.
- [`AI_RUNTIME_CONTRACT.md`](./AI_RUNTIME_CONTRACT.md): implemented provider/capability/lifecycle contract.
- [`EQUIPMENT_VISION_CONTRACT.md`](./EQUIPMENT_VISION_CONTRACT.md): observation → interpretation → derivation → validation contract for later phases.
- [`UX_DECISIONS.md`](./UX_DECISIONS.md): agreed UX decisions that remain unimplemented until their gates.
- [`SYNC_POLICY.md`](./SYNC_POLICY.md): N-BIO ↔ Lab checkpoint and history policy.

LAB-1 stops after its provider/lifecycle shell, diagnostics, tests, documentation and verification are complete. It does **not** start LAB-2A automatically.
