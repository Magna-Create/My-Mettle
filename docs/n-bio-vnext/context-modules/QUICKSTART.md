# Build your first Context Module

This tutorial runs a small module through the real Context Module SPI and contract test kit. The example uses synthetic evidence, returns a neutral candidate signal, and never enters the production feature registry.

The complete source is in:

- [`DocsExampleContextModuleV1.kt`](../../../app/src/test/java/dev/kian/mymettle/context/example/DocsExampleContextModuleV1.kt)
- [`DocsExampleContextModuleV1Test.kt`](../../../app/src/test/java/dev/kian/mymettle/context/example/DocsExampleContextModuleV1Test.kt)

Use those files as the copyable example. The snippets below only highlight the important parts.

## Before you start

You need JDK 17 and the Android build dependencies used by this repository. Run commands from the repository root.

Do not add the example feature to `ProductionContextFeaturesV7E`. It is a test fixture, not a user-facing ontology entry.

## 1. Define the feature

The example creates a boolean, session-scoped feature called `DOCS_SESSION_FLAG@1`. Its source is restricted to `TEST_FIXTURE`, and no missing mention becomes a false value.

The definition also declares the signal target and the reads the module needs:

```kotlin
allowedSignalTargets = setOf(ContextSignalTarget.OBSERVATION_VARIANCE)
requiredReadCapabilities = setOf(
    ContextReadCapability.OWN_FEATURE_EVIDENCE,
    ContextReadCapability.TIME_AND_SCOPE,
)
```

These are fields from the complete source, not extra registration metadata.

## 2. Give the module its own state

`DocsExampleModuleStateV1` stores processed evidence IDs, distinct session keys, and separate present and known-false row counts. The processed IDs make repeated evaluation idempotent.

`DocsExampleModuleStateCodecV1` encodes and decodes that state. Its `moduleId` and `schemaVersion` match the module descriptor. The TCK checks this relationship and checks an initial-state round trip.

## 3. Implement the module

The implementation provides the four members required by `ContextModuleV7E`:

```kotlin
class DocsExampleContextModuleV1 : ContextModuleV7E {
    override val descriptor = ContextModuleDescriptor(/* complete value in source */)
    override val stateCodec: ContextModuleStateCodecV7E = DocsExampleModuleStateCodecV1
    override fun initialState(): ContextModuleStateV7E = DocsExampleModuleStateV1()
    override fun evaluate(
        state: ContextModuleStateV7E,
        view: ContextReadViewV1,
    ): ContextModuleResultV7E = /* complete implementation in source */
}
```

This is an explicitly incomplete excerpt. Open the linked source for the compile-tested implementation.

The example reads only `view.ownFeatureEvidence()`, `view.horizon()`, and `view.scope()`. It returns a `PRIOR_DOMINATED` `OBSERVATION_VARIANCE` signal whose location shift is `0.0`. The signal proves publication and validation without claiming a biological or predictive effect.

## 4. Add a provider

A provider must create a new module instance:

```kotlin
object DocsExampleContextModuleProviderV1 : ContextModuleProviderV7E {
    override fun create(): ContextModuleV7E = DocsExampleContextModuleV1()
}
```

The test registers that provider and feature in an isolated registry:

```kotlin
val registry = ContextModuleRegistryV7E(
    providers = listOf(DocsExampleContextModuleProviderV1),
    featureDefinitions = listOf(DocsExampleContextFeatureV1.definition),
)
```

For a reviewed production module, add its provider to `ProductionContextModuleRegistryV7E.providers` and its feature definition to `ProductionContextFeaturesV7E.all`. Registration is build-time source composition. It is not runtime plug-in discovery.

## 5. Run the contract test

Every provider should call the reusable TCK from a unit test:

```kotlin
val result = ContextModuleContractTckV1.verify(DocsExampleContextModuleProviderV1)
check(result.checks.size == 9)
```

Run the focused example tests:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'dev.kian.mymettle.context.example.DocsExampleContextModuleV1Test' \
  --stacktrace --no-daemon
```

A successful run reports `BUILD SUCCESSFUL`. It proves that the example compiles, registers, passes the TCK, keeps missing evidence distinct from false evidence, and returns a valid neutral signal.

Passing the TCK does not prove that a learner is scientifically correct. Add domain tests for chronology, missingness, scope, replay, and the relationship your learner estimates.

## What you built

You built the minimum complete extension path used by this repository:

1. a versioned feature definition;
2. module-owned state and a codec;
3. a descriptor and module implementation;
4. a zero-argument provider;
5. controlled registry composition;
6. a TCK test and module-specific behaviour tests.

Next, use the [authoring guide](./AUTHORING.md) to choose capabilities and implement a real learner. Use the [SPI reference](./REFERENCE.md) when you need exact fields or target status.
