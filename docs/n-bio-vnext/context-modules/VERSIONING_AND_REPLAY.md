# Version a module and rebuild its state

Context Modules have several independent version axes. Change the axis that matches the meaning you changed.

## Version axes

| Axis | Source field | Change it when | Current compatibility rule |
|---|---|---|---|
| Feature schema | `ContextFeatureKey.schemaVersion` | The feature value or meaning changes incompatibly | Only versions in `compatibleEvidenceVersions` are accepted |
| Evidence compatibility | `compatibleEvidenceVersions` | The exact registered evidence definition needs an additional version guard | No inferred compatibility or adaptation |
| Module protocol | `ContextModuleDescriptor.protocolVersion` | The host-wide SPI contract changes | Exact protocol `1` only |
| Learner model | `modelVersion` | Learner mathematics or interpretation changes | Exact match on reload |
| Module config | `configId`, `configPayload` | A behaviour-driving constant or configuration changes | Exact config ID on reload |
| State codec | `stateSchemaVersion`, codec `schemaVersion` | Encoded module state changes incompatibly | Exact match; codec must reject unknown payloads |
| Signal schema | `ContextSignalV1.signalSchemaVersion` | The host-wide signal envelope changes | Exact signal schema `1` only |
| Host storage | Room/app version | Internal persistence schema changes | Owned by the host, not module authors |

Do not change an immutable config payload while keeping the same config identity. Do not silently reinterpret old state under a new codec.

## Change a stored state format

1. Define the new state class or fields.
2. Increase `stateSchemaVersion` in the descriptor.
3. Make the codec report the same new `schemaVersion`.
4. Decode only formats whose meaning is known.
5. Add round-trip and malformed/old-version tests.
6. Delete incompatible derived module state and replay canonical evidence.

The current SPI has no generic state-migration callback. If an old format cannot be read safely, rejection plus replay is the supported recovery.

The two production modules use codec schema `2`. Their earlier pre-acceptance schema `1` layout is intentionally rejected. Raw evidence remains available for replay.

## Change learner mathematics or configuration

For a mathematical change, create a new immutable `modelVersion`. For a constant/configuration change, create a new `configId` and canonical `configPayload`. Change both when both changed.

Persisted state and signals bind to the module ID, model version, config ID, state codec version, and signal schema. A mismatch fails closed. Recompute derived state and signals from canonical evidence under the new identities.

## Change a feature schema

Create a new `ContextFeatureKey` version when an old observation could be interpreted differently. Preserve old evidence. The current registry resolves exact keys, so add an explicit adapter/registered definition and list the exact consumed key in the module. `compatibleEvidenceVersions` is an additional guard; it does not perform the mapping.

Do not rewrite old tag observations to look like the new schema.

## Replay order

Canonical replay follows the same causal sequence as live use:

1. expose evidence available before the session;
2. freeze pre-session predictions and module signals;
3. observe and score the session;
4. update temporal and module state for future sessions;
5. persist the complete derived run.

For one user/replay, the host orders evidence chronologically and modules by stable module ID. Module transforms must be deterministic and idempotent for repeated evidence.

## Invalidation

A changed feature maps to consuming modules through descriptor `consumedFeatures`. The host removes:

- those modules' derived state and status;
- their signals;
- the combined `CONTEXT_TEMPORAL` state.

It preserves unrelated module memory, the context-free and dose temporal candidates, raw notes, annotations owned by the context system, workouts, and earlier N-BIO state. Deleting all derived interpretations removes all context-conditioned module state but still preserves non-context temporal candidates.

## Room15

Room15 stores five 7E-derived concepts: run provenance, temporal state, module state, signals, and module status. The 14→15 migration is automatic and additive. Module authors do not migrate the database manually and do not write these tables directly.

See the [authoring state workflow](./AUTHORING.md#3-create-module-owned-state-and-a-codec), [persistence source](../../../app/src/main/java/dev/kian/mymettle/inference/NBio7EShadowRepository.kt), and [Room migration test](../../../app/src/androidTest/java/dev/kian/mymettle/data/local/NBio7ERoomMigrationTest.kt).
