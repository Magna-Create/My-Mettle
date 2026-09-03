# N-BIO-7E — Context Extension Working Journal

> **Status:** living rough implementation journal for N-BIO-7E.
>
> This file is intentionally not polished. It exists to capture extension/API/SPI decisions while they are still fresh, so a later post-7E cleanup mission can turn the proven implementation into a clean human/third-party integration contract without reverse-engineering intent from source code.
>
> Do not delete rough history merely because a later decision supersedes it. Mark superseded entries clearly and point to the newer decision.

Read with:

- [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md)
- [`NBIO_7E_WORK_PROTOCOL.md`](./NBIO_7E_WORK_PROTOCOL.md)
- [`PLAN.md`](./PLAN.md)
- [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md)
- [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md)
- [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md)

---

# 0. Journal rules

During 7E implementation, update this file whenever a material code-communication decision is made.

Material means anything another module, future human contributor or N-BIO Core consumer could plausibly depend on, including:

- interfaces;
- message/envelope shapes;
- registration/discovery;
- lifecycle;
- allowed reads;
- allowed writes/publications;
- versioning;
- compatibility;
- timing/chronology;
- missingness;
- replay;
- persistence;
- threading/coroutines;
- failure isolation;
- capability permissions;
- module state ownership;
- signal arbitration;
- test/contract harnesses;
- future third-party extension implications.

Prefer a rough note now over a polished explanation later.

---

# 1. Current implementation status

```text
N-BIO-7C: structurally closed; PD-001 open
N-BIO-7D: structurally closed; PD-002 open
N-BIO-7E: not started at creation of this journal
```

Starting documentation head before 7E work should be recorded by the implementation agent here.

```text
7E_START_HEAD = <fill when implementation begins>
CURRENT_HEAD = <update at meaningful checkpoints>
```

---

# 2. Extension-surface map

Keep this table current as the implementation develops.

| Surface | Owner | Producer/caller | Consumer | Versioned? | Replay role | Human/3P relevance | Status/notes |
|---|---|---|---|---|---|---|---|
| ContextFeatureDefinition | TBD | registry/authoring | interpreter/modules/core | TBD | TBD | high | |
| ContextFeatureEvidence | existing 7A.5 boundary + extensions TBD | Nano/rules/explicit/future producers | modules | yes | canonical/derived boundary must remain explicit | high | |
| ContextModule | TBD | registered implementation | module runtime/core bridge | yes | derived/rebuildable | very high | |
| ContextReadView / equivalent | TBD | N-BIO typed evidence/state provider | module | yes | input provenance | very high | |
| ContextSignal | TBD | module | N-BIO Core arbitration | yes | derived/rebuildable | very high | |
| Context arbitration/combiner | N-BIO Core | signals + base state | 7E state inference | yes | derived/replayable | medium | |
| Module memory/state codec | module/runtime | module learner | module/replay | yes | required | high | |
| Module registry/discovery | TBD | app/build integration | runtime | yes/compatibility TBD | startup/replay identity | very high | |

Add/remove rows as real interfaces emerge.

---

# 3. API / SPI decision log

Use one entry per material decision.

Template:

```text
DECISION 7E-API-XXX
Date / HEAD:
Component:
Decision:
Why:
Alternatives considered:
Compatibility impact:
Chronology/replay impact:
Failure behaviour:
Human/3P extension impact:
Internet/source input if any:
Supersedes / superseded by:
```

## Entries

_No entries yet._

---

# 4. What a feature/tag is allowed to plug into

Keep this as the live implementation truth, not merely the aspirational architecture.

Potential association/output targets from the architecture include:

```text
SYSTEMIC_TRANSIENT_STATE
LOCAL_TRANSIENT_STATE
OBSERVATION_RELIABILITY
OBSERVATION_VARIANCE
PROCESS_VOLATILITY
RECOVERY_DYNAMICS
CAPABILITY_CONDITIONING
EXECUTION_CONTEXT
EQUIPMENT_TRANSLATION
RECRUITMENT_CONTEXT
```

For each target, record whether 7E actually implements the route now, only reserves the capability in the protocol, or prohibits it until a later phase.

| Target/capability | 7E implemented? | Who may publish | Who consumes | Required scope | Notes / later phase boundary |
|---|---|---|---|---|---|
| SYSTEMIC_TRANSIENT_STATE | TBD | | | | |
| LOCAL_TRANSIENT_STATE | TBD | | | | |
| OBSERVATION_RELIABILITY | TBD | | | | |
| OBSERVATION_VARIANCE | TBD | | | | |
| PROCESS_VOLATILITY | TBD | | | | |
| RECOVERY_DYNAMICS | TBD | | | | |
| CAPABILITY_CONDITIONING | TBD | | | | |
| EXECUTION_CONTEXT | TBD | | | | |
| EQUIPMENT_TRANSLATION | protocol reservation only unless 7F explicitly begins | | | | do not implement 7F silently |
| RECRUITMENT_CONTEXT | protocol reservation only unless accepted scope says otherwise | | | | |

When a new target is added, document the reason and interface semantics before relying on it.

---

# 5. Feature-definition contract log

Record the concrete implemented fields/semantics of the feature definition.

Questions that must eventually be answerable from this section:

- How is a feature uniquely identified?
- How is schema/model version represented?
- Which value types are supported?
- How are set/exercise/session/day/episode/muscle/equipment scopes represented?
- How are `unknown`, `not observed`, `not mentioned`, `false`, `negated`, `not applicable` distinguished?
- How are allowed association targets declared?
- How are source kinds/producers declared?
- How are temporal semantics declared?
- How are permissions/read capabilities declared?
- How does a richer future feature version coexist with old evidence?
- How does a future human-written feature register without editing N-BIO Core feature-specific logic?

### Current concrete shape

_TBD during implementation._

---

# 6. Module lifecycle and registration

Document the real lifecycle once implemented.

At minimum track decisions around:

```text
registration/discovery
construction
configuration/model identity
input subscription/read-view acquisition
initial replay/bootstrap
incremental update
signal publication
persistence/checkpoint
invalidation/reanalysis
shutdown/disposal if applicable
```

Questions to answer:

- Is registration compile-time, generated, ServiceLoader-like, DI registry, explicit manifest, or another mechanism?
- Can duplicate feature/module IDs exist?
- How are incompatible versions rejected?
- What happens if a module is missing when replay encounters its prior state?
- What happens if a module throws/fails numerical checks?
- Is one bad module isolated from other modules/core state?
- Is arbitrary runtime external code loading explicitly absent?

### Current lifecycle

_TBD during implementation._

---

# 7. Approved module read contract

Modules must not receive unrestricted database access.

For every concrete read view/interface, record:

```text
name
owner
fields/data types
chronology semantics
whether raw/derived
model/config identity
privacy restrictions
which module capabilities may request it
whether future outcome leakage is possible
how replay obtains the same view
```

Potential classes from the architecture include:

- own ContextFeatureEvidence;
- elapsed time/timestamps;
- session/profile/side/anatomy scope;
- pre-session capability posterior summaries;
- frozen prediction and later realised residuals;
- SessionDose summaries;
- later accepted state summaries;
- equipment/profile semantics;
- prediction errors.

Availability does not imply permission.

### Implemented read views

_TBD during implementation._

---

# 8. ContextSignal / publication contract

Document the actual implemented signal envelope in detail.

Questions to answer:

- signal identity;
- source module/feature identity;
- model/config version;
- target/capability;
- scope;
- timestamp/effective interval;
- posterior representation or sufficient uncertainty summary;
- support/evidence maturity;
- applicability/fail-closed status;
- provenance;
- correlation/grouping information if supported;
- whether signals are immutable/recomputed;
- how obsolete signal versions are handled;
- ordering/idempotency rules;
- whether a module may retract/supersede a signal through replay rather than mutation.

### Current signal envelope

_TBD during implementation._

---

# 9. N-BIO Core consumption / arbitration contract

Record how Core consumes signals without feature-specific hard-coded branches.

Questions to answer:

- How are target-specific signals routed?
- How are multiple signals combined?
- How are correlated/co-occurring tags prevented from naive double counting?
- How are contradictory signals handled?
- What happens when no module has enough evidence?
- What happens when a module is prior-dominated?
- How is base context-free state preserved for champion/challenger comparison?
- How are context-aware and context-free predictions frozen/scored chronologically?
- Which outputs remain SHADOW/candidate?

### Current arbitration path

_TBD during implementation._

---

# 10. Independent module memory / learner state

For every implemented module family, record:

```text
module ID/family
what it learns
state representation
update chronology
priors/initial state
how evidence support is counted
persistence format
model/config identity
replay behaviour
what causes invalidation
what it publishes
what it explicitly cannot change
```

Do not describe association parameters as causal effects unless a separate causal model contract exists.

### Module memory implementations

_TBD during implementation._

---

# 11. Temporal/episode semantics

Record the concrete framework for features that can persist beyond one source observation.

Questions to answer:

- How are onset/effective intervals represented?
- How can a Monday illness observation remain probabilistically relevant on Wednesday without duplicating raw evidence?
- How is episode persistence learned per user/module?
- How is resolution represented?
- What happens when evidence explicitly negates/resolves an episode?
- How are overlapping episodes handled?
- How are retrospective/smoothed views distinguished from causal/pre-session views?

### Current episode implementation

_TBD during implementation._

---

# 12. Persistence, invalidation and replay

Record all 7E persistence decisions.

Must eventually cover:

- raw evidence ownership remains unchanged;
- module memory is derived/rebuildable;
- signals are derived/rebuildable;
- core 7E state is derived/rebuildable;
- corrections invalidate only affected downstream state where possible;
- full replay remains canonical rebuild path;
- deterministic versus approximate replay tolerance;
- backup/restore semantics;
- model/schema incompatibility fail-closed behaviour.

### Current implementation

_TBD during implementation._

---

# 13. Failure isolation and defensive behaviour

Future third-party/human-authored modules make this important even if all current modules ship in-tree.

Record decisions around:

- module exception isolation;
- invalid output rejection;
- NaN/Inf handling;
- malformed posterior/scope handling;
- timeout/cancellation policy if applicable;
- memory/compute limits if applicable;
- duplicate publication;
- stale model/version output;
- unsupported target requests;
- permission/read-view violations;
- one module failing without poisoning unrelated state.

Do not silently replace a failed module signal with a plausible default.

### Current behaviour

_TBD during implementation._

---

# 14. Threading / coroutine / compute ownership

Record decisions that future module authors must know.

Questions:

- Which dispatcher/executor owns module updates?
- Are modules allowed to block?
- Are module updates serial per module/user?
- Is publication ordered?
- Can two modules update concurrently?
- How are cancellation and app/process interruption handled?
- What state transitions must be atomic?
- What must remain deterministic despite concurrency?

### Current rules

_TBD during implementation._

---

# 15. Compatibility / versioning matrix

Track concrete compatibility rules as they appear.

| Component | Identity/version field | Backward compatible? | Forward compatible? | Unknown version behaviour | Migration/reanalysis rule |
|---|---|---|---|---|---|
| Feature definition | TBD | | | fail closed? | |
| Feature evidence | TBD | | | | |
| Module model/config | TBD | | | | |
| Module state codec | TBD | | | | |
| ContextSignal | TBD | | | | |
| Core arbitration config | TBD | | | | |

Do not make silent compatibility assumptions.

---

# 16. Human / third-party module author notes

This section should become increasingly concrete as 7E matures.

Record what a future human author would need to know to add a module **without modifying N-BIO Core feature-specific logic**.

Eventually answer:

1. What interface must be implemented?
2. How is the module registered?
3. How does it declare the feature/version(s) it consumes?
4. How does it request approved read capabilities?
5. What state may it persist?
6. What output/signal types may it publish?
7. What targets may it plug into?
8. How is its config/model identity versioned?
9. What contract tests must it pass?
10. How is deterministic replay tested?
11. How are privacy boundaries tested?
12. How does failure degrade safely?
13. What code must *not* be touched to add it?
14. Which later phases own capabilities not available in 7E?

Important: this does not authorise arbitrary downloaded runtime code. Document the actual supported integration mode.

### Current authoring path

_TBD during implementation._

---

# 17. Contract-test / TCK notes

If 7E introduces reusable module interfaces, record the test harness a future module implementation should pass.

Potential invariants:

- registration identity uniqueness;
- unsupported version fail-closed;
- no raw mutation;
- no direct Core-state mutation;
- permission/read-boundary enforcement;
- deterministic/reproducible replay where claimed;
- future-data leakage prevention;
- malformed signal rejection;
- missingness semantics preserved;
- module failure isolated;
- state codec round-trip;
- invalidation/reanalysis correctness;
- no product-authority side effects.

### Current harness

_TBD during implementation._

---

# 18. External research / best-practice log

At periodic review windows, append only useful findings.

Template:

```text
RESEARCH 7E-R-XXX
Date / HEAD:
Question:
Source/project:
What problem they were solving:
Useful lesson:
Known caveat / mismatch with My Mettle:
Action taken / no action:
Related API decision:
```

Prefer lessons from mature extension/plugin architectures, platform documentation and postmortems over generic tutorial snippets.

## Entries

_No entries yet._

---

# 19. Rejected approaches / mistakes avoided

Keep a lightweight record of approaches considered and rejected, especially when online research or tests reveal common extensibility mistakes.

Examples of categories worth recording:

- global mutable registry;
- feature-specific switches inside N-BIO Core;
- unversioned message DTOs;
- unrestricted DB access for modules;
- hidden future-data leakage;
- module outputs directly mutating state;
- broad exception swallowing/default substitution;
- runtime remote code loading without a security/product contract;
- assuming all modules use the same learning algorithm;
- assuming all module signals are statistically independent.

### Entries

_None yet._

---

# 20. Periodic review entries

Append the ~25-minute review summaries required by [`NBIO_7E_WORK_PROTOCOL.md`](./NBIO_7E_WORK_PROTOCOL.md).

Template:

```text
REVIEW <N> — <timestamp / HEAD>

Block objective:

Completed:

Diff / architecture notes:

Tests/builds/background tasks:

Errors/warnings:

Extension/API decisions recorded:

Internet check:
- question:
- source/project:
- lesson:
- implementation effect:

Scope check:

Next block:
```

## Entries

_No entries yet._

---

# 21. Post-7E cleanup handoff

Do **not** try to turn this entire file into polished public documentation during the main 7E mission.

At 7E closure, make sure it is complete enough that a second dedicated prompt can:

- reconcile superseded decisions;
- extract the final extension/API contract;
- create a clean module-authoring/integration guide;
- produce diagrams/examples;
- separate internal implementation detail from stable extension surface;
- document supported plug-in targets and phase boundaries;
- preserve a shorter architectural decision record where useful;
- verify documentation against final source/tests.

The cleanup mission should use this journal as evidence, not treat every rough note as final truth.
