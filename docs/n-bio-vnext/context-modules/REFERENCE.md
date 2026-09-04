# Context Module SPI reference

This page documents the build-integrated author surface. All author-facing v1 types are **EXPERIMENTAL**: source and tests define them, but the project has not made a long-term binary compatibility promise.

## Author-facing and internal surfaces

| Classification | Main types |
|---|---|
| Author-facing SPI | `ContextFeatureKey`, `ContextFeatureDefinitionV7E`, value/scope/temporal/missingness types, `ContextFeatureEvidenceV7E`, `ContextModuleV7E`, `ContextModuleProviderV7E`, `ContextModuleDescriptor`, `ContextModuleStateV7E`, `ContextModuleStateCodecV7E`, `ContextModuleResultV7E`, `ContextReadViewV1`, `ContextReadCapability`, `ContextModulePhase`, `FrozenContextPrediction`, `ContextSignalV1`, signal target/effect/status/maturity types, `ContextModuleContractTckV1` |
| Author integration/test surface | `ContextModuleRegistryV7E`, `ProductionContextFeaturesV7E`, `ProductionContextModuleRegistryV7E` |
| Internal implementation | `ContextModuleRuntimeV7E`, `ContextSignalValidatorV1`, `ContextSignalArbitratorV1`, `ContextSignalTargetPolicyV1`, `NBio7EShadowRepository`, Room entities/DAO/migration, temporal solver, acceptance runner, developer UI and report code |

Module authors may rely on internal behaviour described here, such as validation and failure isolation, but should not call or persist through those internal classes directly.

## Feature definition and evidence

### `ContextFeatureKey`

```kotlin
data class ContextFeatureKey(
    val featureId: String,
    val schemaVersion: Int,
)
```

`featureId` must be non-blank and `schemaVersion` must be positive. `canonical` returns `featureId@schemaVersion`.

### `ContextFeatureDefinitionV7E`

| Field | Meaning |
|---|---|
| `key` | Stable feature identity and schema version. |
| `humanMeaning` | Literal description of what the evidence means. |
| `valueSchema` | Value kind, optional unit/bounds, or allowed categories. |
| `allowedScopes` | Scope kinds allowed on evidence. |
| `allowedSourceKinds` | Producers allowed to supply evidence. |
| `temporalSemantics` | `INSTANTANEOUS`, `SESSION_SCOPED`, `FIXED_INTERVAL`, `EPISODE_LIKE`, `DECAYING`, or `UNKNOWN_PERSISTENCE`. |
| `missingnessSemantics` | How absence and explicit values are interpreted. |
| `allowedSignalTargets` | Targets a consuming module may publish. |
| `requiredReadCapabilities` | Reads required by the feature's module contract. |
| `compatibleEvidenceVersions` | Positive-version guard on the exact registered definition; defaults to the key version. It does not perform version adaptation. |

`ContextFeatureValueKind` supports `BOOLEAN`, `ORDINAL`, `CONTINUOUS`, `CATEGORICAL`, `ANATOMY_SCOPED`, and `STRUCTURED_REFERENCE`. The matching `ContextFeatureValueV7E` subtype must be used.

### `ContextFeatureEvidenceV7E`

| Field | Meaning |
|---|---|
| `evidenceId` | Stable non-blank row identity used for idempotency/provenance. |
| `featureKey` | Feature and evidence schema identity. |
| `value` | Present only when `missingness == PRESENT`. |
| `missingness` | `PRESENT`, `KNOWN_FALSE`, `NOT_REPORTED`, `NOT_MEASURED`, `NOT_APPLICABLE`, or `UNKNOWN`. |
| `scope` | Typed scope and required ID for non-systemic kinds. |
| `observedAt` | When the source observation was made. |
| `effectiveFrom` / `effectiveUntil` | Interval described by the evidence. The end is optional and cannot precede the start. |
| `sourceKind` | Typed producer category. |
| `sourceRevisionId` | Non-blank source revision/provenance identity. |
| `extractorConfidence` | Optional producer confidence in `[0,1]`; not biological uncertainty. |

The host-owned feature registry first resolves the exact key/version, then checks the compatibility set, scope, source kind, and value shape. To consume old evidence, supply an explicit registered definition/adapter and declare its exact key in the module. `compatibleEvidenceVersions` alone does not map an old key to a new key.

### Scope

`ContextScope` contains a `ContextScopeKind` and an optional ID. `SYSTEMIC` is the only kind without an ID. `SESSION`, `SESSION_EXERCISE`, `EXECUTION_PROFILE`, `SIDE`, `ANATOMY`, `EQUIPMENT`, and `EPISODE` require a non-blank ID.

Evidence scope must be allowed by its feature definition. Signal scope is checked again against target rules. The host arbitrates signals only within an exact target and scope pair.

## Module and provider

### `ContextModuleV7E`

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

`evaluate` is synchronous and I/O-free. It returns a module-owned next state and a list of candidate signals. The host calls modules in stable module-ID order and owns persistence, transactions, coroutine lifetime, and cancellation.

### `ContextModuleProviderV7E`

```kotlin
fun interface ContextModuleProviderV7E {
    fun create(): ContextModuleV7E
}
```

Each call must return an independently owned module instance with the same descriptor and initial-state semantics.

### `ContextModuleDescriptor`

| Field | Rule |
|---|---|
| `moduleId` | Non-blank and unique in the registry. |
| `protocolVersion` | Must equal `CONTEXT_MODULE_PROTOCOL_VERSION` (`1`). |
| `learnerFamily` | Non-blank description of the learner. |
| `modelVersion` | Non-blank learner model identity. |
| `configId` / `configPayload` | Non-blank immutable configuration identity and canonical payload. |
| `stateSchemaVersion` | Positive and equal to `stateCodec.schemaVersion`. |
| `consumedFeatures` | Non-empty set; limits evidence and drives invalidation. |
| `requiredReadCapabilities` | Must include `OWN_FEATURE_EVIDENCE` and `TIME_AND_SCOPE`. |
| `allowedTargets` | Non-empty set of signal targets. |
| `deterministicReplay` | The v1 TCK requires `true`. |

### State and result

`ContextModuleStateV7E` exposes `ownerModuleId`. `ContextModuleStateCodecV7E` exposes `moduleId`, `schemaVersion`, `encode`, and `decode`. The host checks ownership and exact version identities before loading state.

`ContextModuleResultV7E(state, signals)` is the only module return boundary. A module does not receive a mutable Core object.

## Read view and capabilities

`ContextReadViewV1` is an immutable host-created view for one phase and horizon. A method throws `ContextCapabilityViolationException` when its capability was not granted.

| Capability | Method(s) | Returned value |
|---|---|---|
| `OWN_FEATURE_EVIDENCE` | `ownFeatureEvidence()` | Immutable list limited to descriptor features. |
| `TIME_AND_SCOPE` | `horizon()`, `scope()` | Current horizon and target scope. |
| `FROZEN_PRE_SESSION_PREDICTION` | `frozenPrediction()` | `FrozenContextPrediction?` |
| `REALISED_POST_SESSION_RESIDUAL` | `realisedPostSessionResidual()` | `Double?`; forbidden in a pre-session view. |
| `SESSION_DOSE_SUMMARY` | `sessionDoseSummary()` | `Double?`; candidate 7D input under PD-002. |
| `APPROVED_EXECUTION_SEMANTICS` | `approvedExecutionSemantics()` | Immutable `Map<String, String>`. |

All capability methods are **IMPLEMENTED** in the SPI. Availability is separate: nullable values may be absent, and no current production module requests SessionDose or approved execution semantics.

`FrozenContextPrediction` contains `predictionId`, `predictedAt`, optional `evidenceThrough`, `meanLogResidual`, positive `variance`, and `modelIdentity`.

`ContextModulePhase` is either `PRE_SESSION_PUBLICATION` or `POST_SESSION_UPDATE`.

## `ContextSignalV1`

`ContextSignalV1` is the immutable publication envelope from a module to the host.

| Field | Meaning / rule |
|---|---|
| `signalId` | Non-blank signal identity; duplicates are rejected during arbitration. |
| `signalSchemaVersion` | Must equal `CONTEXT_SIGNAL_SCHEMA_VERSION` (`1`). |
| `sourceModuleId` | Must match the descriptor. |
| `moduleModelVersion` | Must match the descriptor model version. |
| `moduleConfigId` | Must match the descriptor config ID. |
| `sourceFeatureKey` | Must be in descriptor `consumedFeatures`. |
| `target` | Must be allowed by the descriptor and current target policy. |
| `scope` | Typed signal scope. Local transient requires `ANATOMY`; systemic transient requires `SYSTEMIC`. |
| `effectiveFrom` / `effectiveUntil` | Application interval. A signal starting after the current horizon is rejected. |
| `effectRepresentation` | A `ContextSignalEffectRepresentation`: `LOG_PERFORMANCE_LOCATION_SHIFT` or `LOG_OBSERVATION_VARIANCE_SHIFT`; must match the target. |
| `locationMean` | Mean effect in the representation's coordinate. It is not a generic confidence score. |
| `variance` | Posterior/estimate variance. For applicable/prior signals it must be finite in `[0.0001, 1.0]`. |
| `evidenceRowCount` | Non-negative supporting rows. |
| `independentSessionCount` | Between `0` and row count. |
| `independentEpisodeCount` | Between `0` and session count. |
| `evidenceMaturity` | Evidence status listed below. |
| `correlationGroupId` | Non-blank dependence group used to avoid naive double counting. |
| `episodeId` | Optional derived episode identity. |
| `sourceEvidenceIds` | Non-blank evidence IDs used as derived provenance. |
| `upstreamModelIdentities` | Non-blank upstream model/config identities when present. |
| `publishedAt` | Publication time. |
| `status` | `APPLICABLE`, `PRIOR_DOMINATED`, `UNAVAILABLE`, or `REJECTED`. |
| `failureCode` | Required for unavailable/rejected signals; absent for applicable/prior signals. |

For `APPLICABLE` and `PRIOR_DOMINATED`, `locationMean` and `variance` are required and `failureCode` must be absent. For `UNAVAILABLE` and `REJECTED`, both numeric fields must be `null` and `failureCode` must be non-blank.

The validator bounds absolute log-performance shifts to `0.20` and absolute log-observation-variance shifts to `1.38629436112`.

### Evidence maturity

`ContextEvidenceMaturity` values are:

- `NO_EVIDENCE`;
- `PRIOR_DOMINATED`;
- `PARTIALLY_LEARNED`;
- `DATA_INFORMED`;
- `EMPIRICALLY_USEFUL`;
- `NO_PREDICTIVE_BENEFIT`;
- `REJECTED`.

`EMPIRICALLY_USEFUL` and `NO_PREDICTIVE_BENEFIT` require chronological comparison. A row count alone cannot establish them.

## Signal targets

The table keeps software stability, capability, and scientific status separate.

| Target | API stability | Capability status | Current consumer/effect | Scientific status |
|---|---|---|---|---|
| `SYSTEMIC_TRANSIENT_STATE` | EXPERIMENTAL | IMPLEMENTED | Changes the context candidate's log-performance location and uncertainty | STRUCTURALLY VALIDATED; CALIBRATION PENDING |
| `OBSERVATION_VARIANCE` | EXPERIMENTAL | IMPLEMENTED | Changes the context candidate's observation variance in log-variance space | STRUCTURALLY VALIDATED; CALIBRATION PENDING |
| `LOCAL_TRANSIENT_STATE` | EXPERIMENTAL | PROTOCOL-ONLY | Envelope, anatomy-scope validation, and arbitration exist; no evolving local latent consumer exists | STRUCTURALLY VALIDATED route; effect model NOT YET EVALUATED |
| `OBSERVATION_RELIABILITY` | EXPERIMENTAL | RESERVED | Rejected by target policy v1 | NOT YET EVALUATED |
| `PROCESS_VOLATILITY` | EXPERIMENTAL | RESERVED | Rejected by target policy v1 | NOT YET EVALUATED |
| `RECOVERY_DYNAMICS` | EXPERIMENTAL | RESERVED | Rejected by target policy v1 | NOT YET EVALUATED |
| `EXECUTION_CONTEXT` | EXPERIMENTAL | RESERVED | Rejected by target policy v1 | NOT YET EVALUATED |
| `EQUIPMENT_TRANSLATION` | EXPERIMENTAL | LATER PHASE | N-BIO-7F; rejected by target policy v1 | NOT YET EVALUATED |
| `CAPABILITY_CONDITIONING` | EXPERIMENTAL | LATER PHASE | N-BIO-7G; rejected by target policy v1 | NOT YET EVALUATED |
| `RECRUITMENT_CONTEXT` | EXPERIMENTAL | LATER PHASE | Requires a later authorised contract; rejected by target policy v1 | NOT YET EVALUATED |

`NOT YET EVALUATED` means there is no effect model to calibrate yet. It should not be read as a failed or weak calibration result.

No `ContextSignalTarget` has normal product authority. A valid protocol-only signal may still have no effect on the current model.

### Arbitration behaviour authors need to know

The host owns arbitration. For one target and exact scope, signals in the same `correlationGroupId` are treated as possible duplicate explanations and only one deterministic representative survives. Representatives from different groups are precision-combined rather than added. Disagreement widens variance, and opposite signs set an internal contradiction flag.

Choose a correlation group that truthfully describes dependence. Do not split one explanation into several group IDs to increase its weight. Modules cannot call arbitration to write Core state themselves.

## Registration and compatibility

`ProductionContextModuleRegistryV7E.providers` is an explicit build-time list. `ProductionContextModuleRegistryV7E.create()` combines it with `ProductionContextFeaturesV7E.all`.

`ContextModuleRegistryV7E`:

- calls each provider;
- sorts modules by `descriptor.moduleId`;
- rejects duplicate IDs;
- rejects unsupported protocol versions;
- rejects descriptor/codec owner or schema mismatch;
- requires own-evidence and time/scope capabilities.

Unknown feature keys or incompatible evidence versions fail feature validation. Persisted state also requires an installed module plus exact model, config, and codec versions. There is no runtime code discovery or loading.

## Failure behaviour

During host execution, an ordinary exception, denied capability, wrong state owner, foreign evidence row, or invalid signal fails that module call closed. The host keeps the previous state, emits no signal from the call, and records `ContextModuleFailureV7E` with the exception class name and message. Peers continue.

Persisted internal module status is `OK` or `FAILED_CLOSED`; failure summaries are capped at 240 characters. These are host diagnostics, not author-defined error codes.

Malformed/unknown persisted state fails load. The safe recovery is deletion of the incompatible derived state and chronological replay from canonical evidence.

## TCK

Call:

```kotlin
ContextModuleContractTckV1.verify(provider)
```

The current TCK returns these nine check names:

1. `independent_instances`;
2. `deterministic_descriptor`;
3. `protocol_version`;
4. `declared_capabilities`;
5. `declared_features`;
6. `declared_targets`;
7. `state_owner`;
8. `state_codec_roundtrip`;
9. `deterministic_initial_state`.

It does not test scientific usefulness, all signal validation rules, full replay, or domain-specific missingness/chronology. Add module-specific tests.

## View source

- [Author-facing domain types and host validation](../../../app/src/main/java/dev/kian/mymettle/domain/context/ContextExtensionV7E.kt)
- [Production features, modules, codecs, providers, and registry](../../../app/src/main/java/dev/kian/mymettle/context/modules/ContextModulesV7E.kt)
- [Reusable TCK](../../../app/src/test/java/dev/kian/mymettle/context/ContextModuleContractTckV1.kt)
- [Production module tests](../../../app/src/test/java/dev/kian/mymettle/context/ContextModuleV7ETest.kt)
- [Compile-tested documentation fixture](../../../app/src/test/java/dev/kian/mymettle/context/example/DocsExampleContextModuleV1.kt)

See the [authoring guide](./AUTHORING.md), [versioning and replay](./VERSIONING_AND_REPLAY.md), and [troubleshooting](./TROUBLESHOOTING.md).
