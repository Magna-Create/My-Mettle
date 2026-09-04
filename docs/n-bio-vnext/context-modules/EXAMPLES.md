# Production module examples

The two production modules use the same SPI and different learner mathematics. They are examples of extension flexibility, not templates that every new module should copy.

Both are build-integrated and SHADOW-only. At 7E physical closure, installed history contained no eligible context evidence, so both remained `NO_EVIDENCE`. Their structural and synthetic behaviour passed; their personal effects remain uncalibrated under PD-003.

## Illness episode association

**Module ID:** `context.illness.episode.v1`

**Implementation:** `EpisodeAssociationModuleV1`

**Learner family:** `episode_persistence_conjugate_association`

**Feature:** `ILLNESS_REPORTED@1`

**Target:** `SYSTEMIC_TRANSIENT_STATE`

### What it reads

The descriptor requests own illness evidence, time/scope, the frozen pre-session prediction, and the realised post-session residual. The residual is only available in the post-session phase and may still be absent.

### What it stores

`EpisodeAssociationStateV2` keeps:

- processed evidence IDs;
- learned episode IDs;
- distinct session keys;
- active episode ID, start, and last-positive time;
- last evidence ID;
- row, session, and independent-episode counts;
- association mean and variance;
- Beta persistence parameters.

`EpisodeAssociationStateCodecV2` owns schema version `2` and delimiter-safely encodes arbitrary IDs.

### What it learns

Positive observations within seven days continue one derived episode. A later positive can increase row and session support without increasing independent-episode support. The association learner uses at most one realised residual per episode.

The module also updates a persistence estimate from continued positive evidence and explicit resolution. This is a predictive episode model, not a diagnosis.

### What it publishes

While an episode is active and no more than 14 days past its last positive evidence, it can return a bounded log-performance location signal. The association contribution decays with a three-day half-life and is scaled by the persistence estimate. The signal belongs to correlation group `systemic_episode_context`.

The signal starts at the evaluated horizon and ends at the last-positive time plus 14 days. It is `PRIOR_DOMINATED` until enough independent episode support exists.

### Missingness and resolution

`NOT_REPORTED`, `NOT_MEASURED`, and `UNKNOWN` do not close an episode. `KNOWN_FALSE` closes the active episode and updates resolution evidence. No raw illness row is copied into a later session.

### What it does not claim

It does not claim that illness caused a performance change, measure illness severity, estimate physical recovery, or change a workout prescription.

[View source](../../../app/src/main/java/dev/kian/mymettle/context/modules/ContextModulesV7E.kt) · [View tests](../../../app/src/test/java/dev/kian/mymettle/context/ContextModuleV7ETest.kt)

## Time-pressure observation variance

**Module ID:** `context.time_pressure.observation_variance.v1`

**Implementation:** `ObservationVarianceAssociationModuleV1`

**Learner family:** `two_group_robust_variance_ratio`

**Feature:** `TIME_PRESSURE_REPORTED@1`

**Target:** `OBSERVATION_VARIANCE`

### What it reads

The descriptor requests own time-pressure evidence, time/scope, the frozen pre-session prediction, and the realised post-session residual.

### What it stores

`ObservationVarianceStateV2` keeps:

- processed evidence IDs;
- row count;
- distinct present and explicit-false session counts;
- bounded squared-residual sums for each group;
- distinct session-key sets for both groups;
- current missingness state and evidence ID;
- the current evidence interval.

`ObservationVarianceStateCodecV2` owns schema version `2`.

### What it learns

The learner compares robust residual second moments between sessions where time pressure is explicitly present and sessions where it is explicitly false. Repeated rows from one session cannot add another residual or independent-session count.

It does not create episodes and does not learn a median performance penalty.

### What it publishes

When time pressure is `PRESENT` at the current horizon, the module can return a bounded log observation-variance ratio. Both explicit groups need support before the learned location can move away from the neutral prior. Its signal belongs to correlation group `session_observation_quality` and has a persisted envelope validity of 86,400 seconds.

The source evidence remains session-scoped. A later pre-session call does not carry the old time-pressure observation forward outside its evidence interval.

### Missingness

Only `KNOWN_FALSE` enters the false/control group. No mention, no measurement, and unknown evidence do not become false observations.

### What it does not claim

It does not claim that time pressure caused variability, infer a systemic biological state, or change workout actions.

[View source](../../../app/src/main/java/dev/kian/mymettle/context/modules/ContextModulesV7E.kt) · [View tests](../../../app/src/test/java/dev/kian/mymettle/context/ContextModuleV7ETest.kt)

## Why the learners differ

| Question | Illness module | Time-pressure module |
|---|---|---|
| Unit of support | Independent episode | Explicit-present or explicit-false session |
| Memory | Episode timing, persistence, location association | Two residual-variance groups and current interval |
| Effect coordinate | Log-performance location | Log observation variance |
| Persistence | Derived multi-session episode with decay | Session-scoped evidence only |
| Explicit false | Resolves an episode | Supplies control-group support |

The shared SPI standardises ownership, reads, versioning, and publication. It does not require one universal learning equation.
