# Troubleshoot a Context Module

Start with what you observe. The runtime fails a bad module call closed so that peer modules and the context-free baseline can continue.

## My module does not register

**Likely causes:** duplicate `moduleId`; unsupported `protocolVersion`; codec module/schema mismatch; missing `OWN_FEATURE_EVIDENCE` or `TIME_AND_SCOPE` capability; provider absent from `ProductionContextModuleRegistryV7E.providers`.

**Check:** construct `ContextModuleRegistryV7E` in a focused unit test and run `ContextModuleContractTckV1.verify(provider)`.

**Fix:** make the identities unique and exact, request the two required capabilities, and add the provider at the build-time composition root. The registry rejects an incompatible module before execution to avoid ambiguous state ownership.

## My module sees no evidence

**Likely causes:** the feature key/version is not in `consumedFeatures`; the feature is not registered; source/scope/value validation failed; the host has no eligible current evidence; the row is `NOT_REPORTED` or `UNKNOWN` rather than `PRESENT`.

**Check:** inspect the module descriptor, feature definition, and typed evidence. Do not infer a false value from an empty list.

**Fix:** align the exact feature key and allowed source/scope. Fix the evidence adapter or source contract instead of broadening module access.

## My module cannot read SessionDose

**Likely causes:** `SESSION_DOSE_SUMMARY` is not declared or granted, or no resolved prior dose exists for the horizon.

**Check:** confirm the capability is in `requiredReadCapabilities`. If the call does not fail but returns `null`, the data is unavailable rather than denied.

**Fix:** request the capability only when the learner needs it and handle `null` without fabricating zero dose. Any available value remains a PD-002-quarantined candidate, not calibrated physiology.

## My realised residual is unavailable

**Likely causes:** the phase is `PRE_SESSION_PUBLICATION`, the capability was not granted, or no eligible frozen prediction/outcome pair exists.

**Check:** inspect `view.phase`, the descriptor, and the nullable result. A pre-session view cannot be constructed with a realised residual.

**Fix:** learn only in `POST_SESSION_UPDATE` and treat `null` as unavailable. Do not move the outcome into the pre-session phase.

## My signal was rejected

**Likely causes:** unknown signal schema; descriptor identity mismatch; undeclared feature or target; target rejected by policy; future effective start; wrong local/systemic scope; wrong effect representation; non-finite or out-of-bounds mean/variance.

**Check:** compare every `ContextSignalV1` identity with the descriptor, then use the [signal field table](./REFERENCE.md#contextsignalv1) and [target matrix](./REFERENCE.md#signal-targets). The runtime failure code is the thrown exception class name; there is no separate author-defined validator code list.

**Fix:** publish the declared target with the correct scope and effect coordinate. Keep numeric values inside validator bounds. Do not substitute a plausible fallback signal.

## My signal is accepted but does not affect the model

**Likely cause:** the target is `LOCAL_TRANSIENT_STATE`. Its envelope, validation, and arbitration route exist, but the current temporal candidate has no evolving local latent consumer.

**Check:** read the target's capability status, not only whether validation passed.

**Fix:** use an effectful target only when it matches the feature and learner. Do not relabel a local effect as systemic to force influence. A local model requires a later authorised candidate.

## My module stays `NO_EVIDENCE`

**Likely causes:** no eligible `PRESENT` or `KNOWN_FALSE` rows; current installed evidence does not contain the tag; the learner requires support in both groups; repeated rows belong to one session/episode.

**Check:** report rows, distinct sessions, and independent episodes separately. Inspect missingness rather than raw note presence.

**Fix:** keep the neutral state until semantically valid evidence arrives. Do not count missing mentions as false or duplicate rows as independent support.

## My saved module state will not load

**Likely causes:** absent module; stale `modelVersion`; stale `configId`; unknown `stateSchemaVersion`; malformed encoded state; decoded owner mismatch.

**Check:** compare the persisted identities with the current descriptor and codec. The repository requires exact matches.

**Fix:** if you cannot interpret the old derived format safely, delete that module's derived state and replay canonical evidence. Do not guess.

## My module stopped loading after I changed its version

**What happened:** exact compatibility checks detected a deliberate model, config, or codec change.

**Fix:** follow [versioning and replay](./VERSIONING_AND_REPLAY.md). Keep the new identity, reject incompatible derived state, and rebuild it from original evidence.

## Replay gives a different result

**Likely causes:** hidden mutable/global state; nondeterministic iteration; I/O or current-time reads inside the module; evidence applied twice; unstable ordering; a config payload changed without a new identity.

**Check:** replay the same ordered fixture twice from `initialState()`, compare encoded state and signals, and verify processed evidence IDs. Run the TCK, then add a full learner replay test.

**Fix:** make `evaluate` a deterministic synchronous transform, sort inputs with stable tie-breakers, store idempotency keys, and bind constants to immutable config.

## My module was invalidated after context evidence changed

**What happened:** the host found the feature in descriptor `consumedFeatures` and removed dependent module state/signals plus the combined context temporal candidate.

**Check:** inspect which feature was reannotated and which module descriptors consume it.

**Fix:** replay the invalidated module from canonical evidence. Unrelated modules and context-free/dose temporal state should remain intact; report a host bug if they do not.

## One module failed but others continued

**What happened:** this is the failure-isolation contract. The runtime retained the failing module's previous state, returned no signal from it, recorded a bounded failure, and continued peer modules.

**Check:** inspect `ContextModuleFailureV7E.moduleId`, `phase`, exception-class `code`, and message. Persisted host status is `FAILED_CLOSED`.

**Fix:** correct the module error. Do not rely on peer continuation as a substitute for tests.

## See also

- [Quickstart](./QUICKSTART.md)
- [Authoring guide](./AUTHORING.md)
- [SPI reference](./REFERENCE.md)
- [Versioning and replay](./VERSIONING_AND_REPLAY.md)
- [Production module examples](./EXAMPLES.md)
