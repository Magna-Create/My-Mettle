# N-BIO-7E — State & Context Implementation Checkpoint

Date: 2026-09-04

Branch: `agent/n-bio-vnext-inference`

Mission start: `487705cc5810ced4da75bb56dd71c1fbcafc348b`

Green implementation checkpoint: `59a47b05e269dc3cdedb553f9dcd00031291675d`

Verdict: **IMPLEMENTATION COMPLETE — HOSTED EXACT-HEAD CI PASS — PHYSICAL INSTALLED-HISTORY ACCEPTANCE PENDING**

This checkpoint records what is implemented and verified without converting missing physical evidence into a structural PASS. The final exact-head SHA will advance when this generated Room15 schema and checkpoint are committed. A successful CI run against that final documentation/schema SHA remains required.

## 1. Phase and authority status

| Item | Status |
|---|---|
| N-BIO-7C | structurally closed; numerical human calibration remains quarantined |
| PD-001 | OPEN |
| N-BIO-7D | structurally closed; accepted physical Room14 evidence retained |
| PD-002 | OPEN |
| N-BIO-7E implementation | complete on the working branch |
| N-BIO-7E structural closure | pending installed-device/history acceptance |
| PD-003 | OPEN; temporal/context calibration and biological interpretation quarantined |
| Product authority | `BENCHMARK_V0`; all 7E outputs remain SHADOW/CANDIDATE |
| N-BIO-7F / 7G / 8 / 9 product integration | not started by this work |

No normal prescription, prefill, set count, exercise selection, routine generation, progression or workout UX path consumes 7E state.

## 2. Implemented temporal model

The selected v1 state is the neutral Gaussian state vector:

```text
x = [persistentComponent, systemicTransientComponent, doseCoefficient]
```

The persistent component is a slowly diffusing unit-root log-performance state. The transient component is a zero-centred AR(1) log-performance state with a fixed three-day half-life. The optional dose coefficient is static, strongly shrunk to zero and receives only strictly prior, resolved SessionDose summaries. These are statistical components, not measured development, fatigue, recovery or physical repair.

The observation model is:

```text
y_t = persistent_t + transient_t + betaDose * priorDose_t
      + acceptedContextLocation_t + epsilon_t
```

Context may also provide a bounded observation log-variance shift. Huber-style variance inflation bounds anomalous-session leverage. The filter retains full covariance between the three coordinates. BASE, DOSE and CONTEXT candidates share the same temporal mathematics so their chronological comparison is meaningful.

Identifiability is constrained by a much smaller fixed persistent process variance, fixed transient mean reversion, fixed base observation variance, a zero-centred dose prior, bounded context effects, robust outlier handling, no separate Skill/Development latent, and no freely moving local latent. Uncertain or competing explanations are allowed to remain broad.

The deterministic Gaussian filter is both the v1 production sequential solver and the high-fidelity reference for this declared linear-Gaussian approximation. A simpler local-level candidate and transparent exponential-dose construction remain challengers; 7E does not reopen the 7B.X backend tournament.

## 3. Implemented context extension surface

The source-of-truth types are in `domain/context/ContextExtensionV7E.kt`.

### Feature definition and evidence

`ContextFeatureDefinitionV7E` contains a versioned feature key, value schema, allowed scopes and source kinds, temporal and missingness semantics, allowed targets, required capabilities, compatible evidence versions and human meaning. It is not an evidence row.

`ContextFeatureEvidenceV7E` retains stable evidence/revision identity, observed/effective chronology, typed value and missingness, source provenance, scope, extraction confidence and inference eligibility. Missingness distinguishes `PRESENT`, `KNOWN_FALSE`, `NOT_REPORTED`, `NOT_MEASURED`, `NOT_APPLICABLE` and `UNKNOWN`. No-mention is never adapted to false.

### Module/provider/registry

`ContextModuleProviderV7E` supplies a zero-argument module. `ContextModuleDescriptor` declares module/protocol/model/config/state-codec identities, the canonical immutable config payload, learner family, consumed features, read capabilities, allowed targets and deterministic-replay claim. `ContextModuleRegistryV1` is an explicit immutable build-time registry sorted by stable identity. Duplicate or incompatible registrations fail closed.

This choice is deterministic and R8-visible, has no mutable global discovery cache, and requires no reflection, `ServiceLoader`, dependency-injection framework or generated-registry toolchain. A generated registry may be reconsidered if provider scale earns it. Arbitrary downloaded code, dynamic DEX loading and a runtime plugin marketplace are explicitly prohibited.

### Controlled reads and lifecycle

Modules receive only `ContextReadViewV1`, with host-granted capabilities selected from:

```text
OWN_FEATURE_EVIDENCE
TIME_AND_SCOPE
FROZEN_PRE_SESSION_PREDICTION
REALISED_POST_SESSION_RESIDUAL
SESSION_DOSE_SUMMARY
APPROVED_EXECUTION_SEMANTICS
```

Raw notes, DAOs, unrestricted workout history, Health data and mutable Core state are absent. The host owns coroutine cancellation, ordering and the persistence transaction. Modules execute serially per module/user as deterministic I/O-free transforms; ordinary exceptions are isolated, while cancellation propagates.

The causal lifecycle is prior evidence/state → pre-session publication → frozen prediction → realised observation/update → post-session module learning → future signal publication. A current outcome cannot train the signal that claims to predict that outcome.

### Module-owned memory and representative learners

Two production modules demonstrate different mathematics behind the same protocol:

1. `ILLNESS_REPORTED` uses an episode-association learner with separate row, session and independent-episode support; it learns a shrunk systemic location association and probabilistic persistence/resolution state.
2. `TIME_PRESSURE` uses a conditional observation-variance learner based on explicit present versus explicit-false sessions; it does not use the episode-location update or treat missing as a control.

Each owns a distinct state class and codec. Current state codecs are schema v2, use delimiter-safe base64url framing for external identities, validate finite/count/interval invariants, and fail closed on unknown or malformed state. Memory is derived, replayable, deletable and backup-covered.

### Signal and central arbitration

`ContextSignalV1` carries signal/module/config/feature identity, target, scope, effective interval, effect representation, posterior mean/variance, support counts, maturity, correlation group, optional episode, source evidence and upstream provenance. The validator rejects unknown schema/model/config, unsupported targets, wrong scope/effect pairing, permission violations, duplicate IDs, illegal intervals, non-finite values and out-of-bounds effects.

Modules cannot mutate Core. `ContextSignalArbitratorV1` collapses declared correlated explanations before precision combining independent groups; it does not sum multiple medians. Between-signal disagreement inflates uncertainty. Invalid modules/signals contribute no substitute effect and cannot poison unrelated modules.

## 4. Plug-in target truth

| Target | 7E status | Effect in v1 | Boundary |
|---|---|---|---|
| `SYSTEMIC_TRANSIENT_STATE` | IMPLEMENTED | bounded context location contribution | generic signal route; no feature switch |
| `OBSERVATION_VARIANCE` | IMPLEMENTED | bounded log-variance contribution | generic signal route |
| `LOCAL_TRANSIENT_STATE` | PROTOCOL-ONLY | validation/arbitration, no evolving local latent/filter consumer | future model must earn identifiability |
| `OBSERVATION_RELIABILITY` | RESERVED/REJECTED BY V1 POLICY | none | later model/version |
| `PROCESS_VOLATILITY` | RESERVED/REJECTED BY V1 POLICY | none | later model/version |
| `RECOVERY_DYNAMICS` | RESERVED/REJECTED BY V1 POLICY | none | later calibrated semantics |
| `CAPABILITY_CONDITIONING` | LATER PHASE | none | N-BIO-7G |
| `EXECUTION_CONTEXT` | LATER PHASE | none | dedicated execution contract |
| `EQUIPMENT_TRANSLATION` | LATER PHASE | none | N-BIO-7F |
| `RECRUITMENT_CONTEXT` | RESERVED/LATER | none | requires authorised historical recruitment view |

A future human author implements a definition, provider, module, state/codec and domain-specific fixtures; adds the provider/definition to the controlled composition root; and runs `ContextModuleContractTckV1` plus chronology, missingness, scope, replay and scientific fixtures. Adding a module does not require a feature-specific branch in the temporal filter or arbitrator.

## 5. Persistence and invalidation

Room15 is justified by five additive derived concepts that Room14 could not represent honestly:

- 7E run/provenance;
- temporal posterior state;
- module-owned state;
- published signal;
- module status/failure.

Migration 14→15 is explicit and additive. The checked-in generated schema is `app/schemas/dev.kian.mymettle.data.local.MyMettleDatabase/15.json`, SHA-256 `d0a6103e72b11dd4c84d418bf9fd08e50cb06869d378f329d50ed66f4d44b6cd`.

An atomic repository transaction writes the run and its derived children. Run deletion cascades only through derived 7E rows. Feature reannotation maps dependencies through module descriptors: consuming module state/status/signals and combined CONTEXT state are invalidated while unrelated modules, BASE/DOSE state and canonical notes/context/workouts remain. Full replay is canonical. Native full backup includes all five 7E tables and validates owner identities during restore.

## 6. Verification evidence at the green implementation checkpoint

GitHub Android CI run 670 (`33812338221`) executed against `59a47b05e269dc3cdedb553f9dcd00031291675d` and completed SUCCESS.

| CI stage | Result |
|---|---|
| Generated biological reference assets | PASS |
| Exercise-authoring JSON Schema | PASS |
| Context/privacy architecture guard | PASS |
| Whitespace/diff check | PASS |
| `:app:testDebugUnitTest :app:assembleDebug` | PASS; Gradle build 5m08s |
| `:app:assembleDebugAndroidTest` | PASS; Gradle build 37s |
| `:app:lintDebug` | PASS; Gradle build 2m39s |
| Exported Room15 schema verification | PASS |
| Debug APK and Room schema artifacts | uploaded |

The device-mirror consolidated synthetic runner reports 17/17 temporal cases and 25/25 context-module cases. Focused JVM tests additionally exercise 19 temporal test methods and 28 context-module test methods, while instrumentation source covers Room migration, state/signal persistence/reload/deletion, descriptor-targeted reannotation invalidation and Native backup integration.

The synthetic suites include stable/improving/declining persistent state, anomalies/outliers, transient suppression/recovery, dose effect/no-effect, high noise, sparse history, regime boundaries, systemic/local scope, simultaneous persistent/transient movement, identifiability stress and no-evidence failure. Module fixtures include registration/version failures, two learners, state isolation, malformed/NaN/permission/scope rejection, missing/false/negated evidence, episode persistence/resolution, row/session/episode accounting, inert/predictive context, correlation/contradiction, delete/replay, invalidation, failure isolation, BASE-versus-CONTEXT comparison and extraction-confidence separation.

The one failed integration checkpoint before run 670 exposed a stale test-double codec schema after the production codec advanced to v2. The fixture was corrected to advertise its actual state schema; no production compatibility check was weakened.

## 7. Physical acceptance still required

Hosted CI cannot open the user's installed database or prove real-history counts, fingerprints, foreign keys and Native backup/restore on the target device. Run the single developer action:

```text
N-BIO 7E State & Context Acceptance
```

It exports one privacy-bounded JSON without note text. The required result includes app/device/schema and model/config/solver identities; PD status; 17+25 synthetic results; registered modules/learner types/support/episodes/target declarations; chronological BASE/DOSE/CONTEXT metrics and ablations; future-leakage guard; persistence/reload/deletion/replay/invalidation; raw/context/prescription/`BENCHMARK_V0` fingerprints; Native backup/restore; foreign keys; runtime; normal-behaviour and later-phase flags.

Until that JSON passes review, the exact structural verdict is:

```text
READY_FOR_7E_PHYSICAL_ACCEPTANCE_EMPIRICAL_CALIBRATION_PENDING
```

It is not `N-BIO-7E STRUCTURAL PASS`. Sparse or absent real context is an acceptable empirical result and must remain `NO_EVIDENCE`/`PRIOR_DOMINATED`; outputs must not be tuned to look intuitively clean.

## 8. Known limitations and handoff

- No prospective external validation establishes that the decomposition improves future human performance prediction.
- Latent persistent/transient truth is not directly observed; synthetic recoverability does not prove biological interpretation.
- SessionDose remains a candidate input under PD-002; downstream fit cannot retroactively validate it.
- Existing typed illness/time-pressure evidence may be too sparse for data-informed personal learning.
- Within-session profile residuals are conservatively dependent; richer joint modelling is deferred.
- Local transient is deliberately protocol-only; equipment translation, conditioning and coaching policy remain later phases.
- The rough extension journal intentionally retains chronology and superseded reasoning. A separate documentation-cleanup mission should turn it into a stable author guide only after physical acceptance confirms the implemented surface.

Fresh causal sessions with explicit present/false/resolution semantics, multiple independent episodes, stable semantic profiles, prior-dose coverage and frozen pre-session predictions are the most valuable evidence for PD-003.
