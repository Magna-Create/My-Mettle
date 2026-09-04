# Author a Context Module

Use this guide after the [quickstart](./QUICKSTART.md). It covers the complete author workflow without requiring you to read temporal solver or Room code.

## 1. Define the feature

Create a `ContextFeatureDefinitionV7E` with a unique `ContextFeatureKey(featureId, schemaVersion)`.

Choose these fields from the evidence contract, not from the effect you hope to find:

- `valueSchema`: boolean, ordinal, continuous, categorical, anatomy-scoped, or structured reference;
- `allowedScopes` and `allowedSourceKinds`;
- `temporalSemantics` and `missingnessSemantics`;
- `allowedSignalTargets`;
- `requiredReadCapabilities`;
- `compatibleEvidenceVersions` as an explicit guard for the exact registered definition;
- `humanMeaning`, using literal non-causal language.

The current registry checks feature identity/version, scope, source kind, and value shape. Your module must still handle each missingness state according to the definition.

The current registry resolves an exact `ContextFeatureKey` before checking `compatibleEvidenceVersions`. That field does not adapt an old key to a new one. Register or adapt the exact evidence version deliberately.

Do not add a production feature only to make a test pass. Use `TEST_FIXTURE` for a synthetic proof.

## 2. Design module identity and configuration

Create an immutable `ContextModuleDescriptor`.

| Field | Author rule |
|---|---|
| `moduleId` | Use a unique, stable ID. The host sorts modules by this value. |
| `protocolVersion` | Use `CONTEXT_MODULE_PROTOCOL_VERSION`. Unknown versions fail registration. |
| `learnerFamily` | Name the actual learning method, not a biological conclusion. |
| `modelVersion` | Change it when learner mathematics or meaning changes. |
| `configId` / `configPayload` | Bind every behaviour-relevant constant to immutable config. |
| `stateSchemaVersion` | Match the codec. Change it when encoded state changes incompatibly. |
| `consumedFeatures` | List every feature key the module may receive. This also drives targeted invalidation. |
| `requiredReadCapabilities` | Request only the data the learner needs. |
| `allowedTargets` | List only targets the module can represent correctly. |
| `deterministicReplay` | V1 contract tests require `true`. |

Every descriptor must request `OWN_FEATURE_EVIDENCE` and `TIME_AND_SCOPE`; registry construction rejects a module without them.

## 3. Create module-owned state and a codec

Implement `ContextModuleStateV7E` and set `ownerModuleId` to the descriptor's module ID. Keep only derived learner state and replay provenance. Do not copy raw notes into it.

Implement `ContextModuleStateCodecV7E`:

- `moduleId` must match the descriptor;
- `schemaVersion` must match `stateSchemaVersion`;
- `encode` must be deterministic;
- `decode(encode(state))` must reproduce the state;
- malformed or unsupported input must fail;
- arbitrary evidence/session/episode IDs need delimiter-safe encoding.

Track processed evidence IDs when incremental calls might contain repeated rows. Keep row, session, and episode identities separately when they have different statistical meaning.

The host persists encoded state. Modules do not receive a DAO and do not write Room rows themselves.

## 4. Implement `ContextModuleV7E`

The interface is:

```kotlin
interface ContextModuleV7E {
    val descriptor: ContextModuleDescriptor
    val stateCodec: ContextModuleStateCodecV7E
    fun initialState(): ContextModuleStateV7E
    fun evaluate(
        state: ContextModuleStateV7E,
        view: ContextReadViewV1,
    ): ContextModuleResultV7E
}
```

`evaluate` is a synchronous, I/O-free transform. Return the next owned state and zero or more candidate signals. Do not start background work or mutate shared state.

Validate the incoming state type and owner before casting it. Sort new evidence by `observedAt` and a stable ID when order can affect the learner.

## 5. Read only declared data

Call a `ContextReadViewV1` method only when its capability is in the descriptor. The host also checks that it granted every capability the descriptor requires.

| Capability | View method | Availability rule |
|---|---|---|
| `OWN_FEATURE_EVIDENCE` | `ownFeatureEvidence()` | The host limits rows to declared `consumedFeatures`. |
| `TIME_AND_SCOPE` | `horizon()`, `scope()` | Required by every registered module. |
| `FROZEN_PRE_SESSION_PREDICTION` | `frozenPrediction()` | May be `null` when no eligible prediction exists. |
| `REALISED_POST_SESSION_RESIDUAL` | `realisedPostSessionResidual()` | Only allowed in `POST_SESSION_UPDATE`; may still be `null`. |
| `SESSION_DOSE_SUMMARY` | `sessionDoseSummary()` | May be `null`; any value remains a PD-002-quarantined candidate input. |
| `APPROVED_EXECUTION_SEMANTICS` | `approvedExecutionSemantics()` | Only approved key/value semantics supplied by the host. |

Calling a method without its capability throws `ContextCapabilityViolationException`. The runtime isolates the module failure, keeps its previous state, and emits no signal from that call.

The SPI does not provide DAOs, arbitrary database access, raw notes, Health data, unrestricted history, or mutable Core state.

## 6. Respect pre-session and post-session order

In `PRE_SESSION_PUBLICATION`, use only evidence and frozen predictions available at the horizon. The view constructor rejects a realised residual in this phase.

In `POST_SESSION_UPDATE`, use an allowed realised residual to update learning for future calls. Never publish the updated relationship as though it had predicted the session that produced the residual.

Replay must reproduce the same order and result. See [concepts](./CONCEPTS.md#chronology) for the short mental model.

## 7. Handle missingness and episodes

Switch on the actual `ContextEvidenceMissingness` value. Do not place `NOT_REPORTED`, `NOT_MEASURED`, `NOT_APPLICABLE`, or `UNKNOWN` into a false/control group unless a separate accepted feature contract says so.

For an episode-like feature:

- open or continue derived episode state from explicit positive evidence;
- let missing evidence remain missing;
- close the episode only through the feature's declared resolution rule;
- keep onset, last positive time, and episode identity in module-owned state;
- count several rows in one episode as one independent episode;
- publish a bounded future signal only while the derived episode applies.

Episode state is replayable derived memory. Do not create future raw evidence rows.

## 8. Return a signal

Construct `ContextSignalV1` with identities copied from your descriptor and a source feature in `consumedFeatures`. Use the target's required effect representation and scope.

For an applicable or prior-dominated signal, `locationMean` and `variance` must be finite and within validator bounds. For `UNAVAILABLE` or `REJECTED`, both values must be `null` and `failureCode` must be non-blank.

Support fields have different meanings:

- `evidenceRowCount`: accepted rows;
- `independentSessionCount`: distinct sessions used as support;
- `independentEpisodeCount`: distinct episodes used as support.

Do not use `extractorConfidence` as signal variance or evidence maturity. Do not claim causation.

See the [signal reference](./REFERENCE.md#contextsignalv1) and [target matrix](./REFERENCE.md#signal-targets) before selecting a target.

## 9. Add the provider and registration entry

Implement a zero-argument provider that returns a fresh module instance:

```kotlin
object MyModuleProviderV1 : ContextModuleProviderV7E {
    override fun create(): ContextModuleV7E = MyModuleV1()
}
```

This is a focused snippet; `MyModuleV1` is your implementation.

For production composition:

1. add the feature definition to `ProductionContextFeaturesV7E.all`;
2. add the provider to `ProductionContextModuleRegistryV7E.providers`;
3. keep IDs unique;
4. build the app and run the TCK and learner tests.

`ContextModuleRegistryV7E` creates modules, sorts them by `descriptor.moduleId`, and rejects duplicate IDs or incompatible protocol/codec declarations. There is no `ServiceLoader`, reflection scan, downloaded module, or dynamic DEX path.

## 10. Test the contract and learner

Start with:

```kotlin
ContextModuleContractTckV1.verify(MyModuleProviderV1)
```

The TCK checks provider ownership, deterministic descriptors and initial state, protocol version, required declarations, state ownership, and codec round trips.

Then add module-specific tests for:

- missing versus explicit false evidence;
- pre-session and post-session chronology;
- future-data leakage;
- repeated evidence and independent support counts;
- signal target, scope, effect representation, bounds, and maturity;
- malformed state and codec version rejection;
- deterministic replay;
- expected inert behaviour when the context is irrelevant;
- domain-specific synthetic truth and uncertainty;
- failure isolation when the module throws or a capability is denied.

Use the [test fixture](../../../app/src/test/java/dev/kian/mymettle/context/example/DocsExampleContextModuleV1Test.kt) and [production module tests](../../../app/src/test/java/dev/kian/mymettle/context/ContextModuleV7ETest.kt) as concrete starting points.

## 11. Rebuild after evidence or version changes

Feature reannotation invalidates the consuming modules named by descriptor `consumedFeatures`, their signals/status, and the combined context temporal candidate. It preserves unrelated module memory and context-free/dose temporal candidates.

If state cannot be decoded under the current owner, model, config, or codec version, fail closed and rebuild derived state from canonical evidence. Do not guess what an old payload meant. See [versioning and replay](./VERSIONING_AND_REPLAY.md).
