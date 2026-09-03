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
7E_START_HEAD = 487705cc5810ced4da75bb56dd71c1fbcafc348b
CURRENT_HEAD = 487705cc5810ced4da75bb56dd71c1fbcafc348b
```

---

# 2. Extension-surface map

Keep this table current as the implementation develops.

| Surface | Owner | Producer/caller | Consumer | Versioned? | Replay role | Human/3P relevance | Status/notes |
|---|---|---|---|---|---|---|---|
| ContextFeatureDefinition | feature registry | controlled author/provider | adapter/modules/runtime | yes: feature + schema | definition snapshot retained | high | concrete code pending |
| ContextFeatureEvidence | existing 7A.5 + 7E adapter | Nano/rules/explicit/future producers | own-feature module only | yes | canonical/correction-aware input | high | legacy rows never rewritten |
| ContextModule | module provider | registered implementation | host runtime | protocol/model/config/state versions | derived/rebuildable | very high | pure state transform |
| ContextReadView | host | typed evidence/state provider | one module | view/capability version | deterministic replay input | very high | no DB/raw-note handle |
| ContextSignal | module through host validator | module | Core arbitrator | schema v1 + module/config | derived/rebuildable | very high | immutable envelope |
| Context arbitration/combiner | N-BIO Core | validated signals + base state | 7E state inference | immutable config | derived/replayable | medium | no feature-ID branch |
| Module memory/state codec | module/runtime | module learner | module/replay | independent codec version | required | high | Room15 dedicated row |
| Module registry/discovery | app composition root | controlled build integration | runtime | protocol/descriptor compatibility | canonical deterministic order | very high | explicit registry; no runtime loading |

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

DECISION 7E-API-001
Date / HEAD: 2026-09-03 / 487705c
Component: provider discovery
Decision: use a deterministic explicit build-time provider registry, sorted by stable module ID. Providers are controlled source/library integrations; there is no arbitrary runtime code loading.
Why: deterministic replay, R8 transparency, absence/incompatibility handling and testability matter more than zero-touch runtime discovery in this offline app.
Alternatives considered: ServiceLoader (runtime resource/class-loader failure surface and lazy instantiation errors); reflection scanning (R8 and hidden discovery); generated KSP registry (useful later if provider count grows, but new build tooling is not currently earned); DI multibinding (no current DI framework and would add a dependency for one registry).
Compatibility impact: provider/module/protocol/config identities are explicit; duplicate or unsupported identities fail closed.
Chronology/replay impact: canonical provider order is module ID order.
Failure behaviour: bad provider rejected before execution; absence yields no signal, never a substitute.
Human/3P extension impact: add a provider at the composition root and pass the TCK; no Core feature branch.
Internet/source input if any: Android `ServiceLoader` API and JetBrains extension-point documentation; both reinforce explicit service contracts and host handling of missing/failing providers, while My Mettle deliberately chooses a smaller static registry.

DECISION 7E-API-002
Date / HEAD: 2026-09-03 / 487705c
Component: feature/evidence/missingness boundary
Decision: introduce versioned FeatureKey/FeatureDefinition and adapt legacy v1 annotations without rewriting them. Evidence distinguishes PRESENT, KNOWN_FALSE, NOT_REPORTED, NOT_MEASURED, NOT_APPLICABLE and UNKNOWN.
Why: legacy absence-of-annotation is not a negative observation; richer future producers need versioned schemas/scopes.
Alternatives considered: extend the current one-version ContextTagRegistry in place (would silently reinterpret history); infer false from missing (rejected leakage/bias).
Compatibility impact: explicit adapters only; unknown versions fail closed.
Chronology/replay impact: original evidence identity/revision remains the replay input.
Failure behaviour: incompatible feature/evidence version is unavailable with provenance.
Human/3P extension impact: feature schema and allowed targets/capabilities are declared outside Core.

DECISION 7E-API-003
Date / HEAD: 2026-09-03 / 487705c
Component: ContextSignal/Core write boundary
Decision: modules publish immutable schema-v1 posterior envelopes; they cannot obtain or mutate Core state. Core validates, groups and arbitrates generic targets.
Why: prevents a module from unilaterally poisoning state and permits failure isolation/replay.
Alternatives considered: direct callbacks such as `adjustTransient` (rejected); universal tagCoefficient table (cannot prove learner diversity or episode semantics).
Compatibility impact: unknown signal/target versions fail closed.
Chronology/replay impact: signals effective for future horizons only and are rebuilt/superseded through replay.
Failure behaviour: invalid/NaN/scope/version/permission output becomes typed rejection, not a default.
Human/3P extension impact: module authors implement the protocol and emit only allowed targets.

DECISION 7E-API-004
Date / HEAD: 2026-09-03 / 487705c
Component: persistence
Decision: Room15 is semantically required; add dedicated feature-definition, module-state, signal/failure and neutral temporal-state tables.
Why: Room14 has generic configs/runs but no semantic home for module identity/state/signals; abusing profile capability state or biologically named adaptive_muscle_state would destroy scope and ownership meaning.
Alternatives considered: opaque all-module blob in capability_parameter_state (wrong FK/scope); adaptive_muscle_state columns (premature biology and no systemic/module identity); memory-only 7E (fails replay/backup).
Compatibility impact: additive tested 14→15 migration; old rows unchanged.
Chronology/replay impact: all new rows derived and inference-run owned.
Failure behaviour: migration is explicit; no destructive migration is introduced for 14→15.
Human/3P extension impact: module codecs remain independently versioned inside a dedicated row.

DECISION 7E-API-005
Date / HEAD: 2026-09-03 / 487705c
Component: execution lifecycle
Decision: host owns coroutine/transaction lifecycle; module transforms are serial per module/user, I/O-free and deterministic. Cross-module concurrency is permitted only with immutable collection and deterministic publication order.
Why: removes hidden thread/global-state ownership and makes replay stable.
Alternatives considered: module-owned CoroutineScope/background jobs (lifecycle leaks/non-atomic writes); arbitrary module DB transactions (permission and ordering ambiguity).
Compatibility impact: blocking/I/O modules violate the TCK/contract.
Chronology/replay impact: events sort by time, evidence ID, module ID.
Failure behaviour: module exception isolated; failed atomic run persists no partial 7E state.
Human/3P extension impact: authors write pure transformations/codecs, not Android lifecycle services.

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
| SYSTEMIC_TRANSIENT_STATE | IMPLEMENTED target | compatible state-association modules | 7E generic arbitrator/filter | systemic/user + interval | bounded log-performance location shift; SHADOW |
| LOCAL_TRANSIENT_STATE | IMPLEMENTED target | compatible anatomy-scoped modules | 7E generic arbitrator/filter | anatomy ID required | synthetic probe initially; no invented production tag |
| OBSERVATION_RELIABILITY | RESERVED | none in v1 | none | — | target policy rejects |
| OBSERVATION_VARIANCE | IMPLEMENTED target | compatible variance modules | 7E generic arbitrator/filter | exact signal scope | bounded log-variance shift |
| PROCESS_VOLATILITY | RESERVED | none in v1 | none | — | target policy rejects; identifiability risk |
| RECOVERY_DYNAMICS | RESERVED | none in v1 | none | — | target policy rejects; naming not earned |
| CAPABILITY_CONDITIONING | LATER-PHASE | none | 7G | modality/profile | prohibited in 7E |
| EXECUTION_CONTEXT | RESERVED | none in v1 | later capability model | profile/version | no canonical mutation |
| EQUIPMENT_TRANSLATION | LATER-PHASE | none | 7F | equipment/profile | prohibited in 7E |
| RECRUITMENT_CONTEXT | LATER-PHASE | none | 7F+ | profile/anatomy | prohibited in 7E |

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

RESEARCH 7E-R-001
Date / HEAD: 2026-09-03 / 487705c
Question: should Android/JVM ServiceLoader be the 7E discovery mechanism?
Source/project: Android `java.util.ServiceLoader` API reference — https://developer.android.com/reference/kotlin/java/util/ServiceLoader
What problem they were solving: locate service implementations without callers depending on concrete classes.
Useful lesson: callers must handle zero providers and provider load/instantiation failures; loader caching/class-loader semantics become lifecycle state.
Known caveat / mismatch with My Mettle: Android build/R8 resources and replay determinism add failure surface while 7E does not need runtime installation.
Action taken / no action: selected an explicit deterministic build-time registry; retained provider interface.
Related API decision: 7E-API-001.

RESEARCH 7E-R-002
Date / HEAD: 2026-09-03 / 487705c
Question: what can a mature extension-point system teach without importing its framework?
Source/project: IntelliJ Platform extension points/plugin structure — https://plugins.jetbrains.com/docs/intellij/plugin-extensions.html and https://plugins.jetbrains.com/docs/intellij/plugin-structure.html
What problem they were solving: declarative registration, lifecycle, dependency and extension ownership across many independently versioned plugins.
Useful lesson: extension contracts, registration metadata and lifecycle ownership must be explicit and separate from implementation classes.
Known caveat / mismatch with My Mettle: IntelliJ has class loaders/marketplace/runtime plugin concerns that are explicitly outside 7E.
Action taken / no action: kept descriptor/provider separation and host-owned lifecycle; rejected runtime plugin machinery.
Related API decision: 7E-API-001, 7E-API-005.

RESEARCH 7E-R-003
Date / HEAD: 2026-09-03 / 487705c
Question: how should independently evolving signal producers identify schemas?
Source/project: OpenTelemetry schema/versioning specifications — https://opentelemetry.io/docs/specs/otel/schemas/ and https://opentelemetry.io/docs/specs/otel/versioning-and-stability/
What problem they were solving: producers and consumers evolve at different rates without silently reinterpreting old signals.
Useful lesson: every emitted envelope needs an explicit schema identity/version; translations must be explicit and incompatibility is a valid outcome.
Known caveat / mismatch with My Mettle: OpenTelemetry's distributed compatibility promises exceed an in-app build-integrated SPI.
Action taken / no action: signal/feature/module codec versions are independent; unknown versions fail closed.
Related API decision: 7E-API-002, 7E-API-003.

RESEARCH 7E-R-004
Date / HEAD: 2026-09-03 / 487705c
Question: should one scalar impulse-response model be the accepted temporal truth?
Source/project: Kontro et al., three-dimensional impulse-response model (2025) — https://arxiv.org/abs/2503.14841
What problem they were solving: single-load/single-response impulse models erase modality-specific acute/chronic responses.
Useful lesson: impulse kernels remain transparent challengers; do not force heterogeneous training stress into one causal scalar.
Known caveat / mismatch with My Mettle: energy-system conditioning model, not resistance-profile residual inference; proposal is not personal validation.
Action taken / no action: SessionDose is an optional shrunk covariate and BASE always survives; 7G remains separate.
Related API decision: normative contract §§4–7.

RESEARCH 7E-R-005
Date / HEAD: 2026-09-03 / 487705c
Question: how should sparse time-varying covariates avoid automatic influence?
Source/project: Uribe & Lopes, dynamic sparsity on dynamic regression models — https://arxiv.org/abs/2009.14131
What problem they were solving: time-varying regression with shrinkage/variable selection.
Useful lesson: dynamic covariates need strong zero shrinkage and must earn non-zero behaviour; adding a predictor is not evidence of usefulness.
Known caveat / mismatch with My Mettle: their MCMC/spike-slab system is too heavy for the Android sequential v1 and the dataset is different.
Action taken / no action: zero-centred dose/context priors, inert/no-benefit states and chronological ablation are mandatory; no MCMC dependency added.
Related API decision: normative contract §§7, 20–21.

RESEARCH 7E-R-006
Date / HEAD: 2026-09-03 / 487705c
Question: who owns coroutine lifetime and how should cancellation cross a module boundary?
Source/project: Kotlin structured-concurrency and cancellation documentation — https://kotlinlang.org/docs/coroutines-basics.html and https://kotlinlang.org/docs/cancellation-and-timeouts.html
What problem they were solving: bound child work to an explicit scope and preserve cooperative cancellation through suspending call stacks.
Useful lesson: the host owns the scope/lifetime; modules do not create global scopes; `CancellationException` must propagate rather than becoming an ordinary module failure.
Known caveat / mismatch with My Mettle: 7E v1 invokes modules sequentially for deterministic replay, so it does not need concurrent child jobs yet.
Action taken / no action: module calls are suspendable, host-ordered, and cancellation is rethrown while ordinary module failures are isolated.
Related API decision: 7E-API-005.

RESEARCH 7E-R-007
Date / HEAD: 2026-09-03 / 487705c
Question: should future human-written modules be dynamically downloaded or loaded?
Source/project: Android dynamic-code-loading security guidance — https://developer.android.com/privacy-and-security/risks/dynamic-code-loading
What problem they were solving: remotely sourced executable code can violate integrity assumptions and expose host/user data.
Useful lesson: keep the v1 extension surface build-integrated and reviewable; do not treat a clean SPI as authority for arbitrary runtime code.
Known caveat / mismatch with My Mettle: controlled same-build providers do not incur the remote-code threat model, but still require least-privilege views and failure isolation.
Action taken / no action: no DexClassLoader, remote marketplace or runtime plugin installer; explicit generated/build-time registration remains the boundary.
Related API decision: 7E-API-001.

RESEARCH 7E-R-008
Date / HEAD: 2026-09-03 / 487705c
Question: how should the additive Room schema be verified?
Source/project: Android Room migration testing guide / `MigrationTestHelper` — https://developer.android.com/training/data-storage/room/migrating-db-versions
What problem they were solving: migration SQL can appear valid while failing to reproduce the schema Room expects.
Useful lesson: retain exported schemas and exercise the exact migration path with `MigrationTestHelper`, including final schema validation.
Known caveat / mismatch with My Mettle: the repository's old pre-7E migration chain is incomplete and uses a development destructive fallback; 7E must not broaden that behaviour.
Action taken / no action: add and register an explicit 14→15 migration plus an instrumentation schema test.
Related API decision: 7E-API-004.

RESEARCH 7E-R-009
Date / HEAD: 2026-09-03 / 487705c
Question: may absent longitudinal context be carried forward or treated as a negative/control observation?
Source/project: Roy & Lin, “Missing covariates in longitudinal data with informative dropouts,” Biometrics 2005 — https://pubmed.ncbi.nlm.nih.gov/16135036/; Proust-Lima et al., longitudinal missing-data review — https://pmc.ncbi.nlm.nih.gov/articles/PMC3016756/
What problem they were solving: longitudinal covariates/outcomes can be absent under mechanisms that are not ignorable, and naive last-observation carry-forward or negative imputation biases estimates.
Useful lesson: missingness is a typed observation about availability, not the opposite feature value; persistence requires a feature-specific episode model or explicit interval.
Known caveat / mismatch with My Mettle: clinical dropout models are richer than the sparse offline v1 module learners, so 7E does not claim to solve MNAR.
Action taken / no action: no-mention remains `NOT_REPORTED`; session-scoped time pressure expires at its declared interval; only explicit false is a control row. Missingness modelling remains a future empirical problem under PD-003.
Related API decision: 7E-API-002, 7E-API-003.

RESEARCH 7E-R-010
Date / HEAD: 2026-09-03 / 487705c
Question: can missing performance/history patterns themselves carry predictive information in sport?
Source/project: “Missing data patterns in runners’ careers: do they matter?”, JRSS C 2023 — https://academic.oup.com/jrsssc/article/72/1/213/7045949
What problem they were solving: sparse longitudinal athletics histories where participation/dropout patterns may improve out-of-sample performance prediction.
Useful lesson: an availability pattern can be predictive without licensing the system to fabricate the missing measurement or call it a causal state.
Known caveat / mismatch with My Mettle: runner career/discipline data is population-level and unlike one user's resistance sessions.
Action taken / no action: 7E reports availability and typed missingness; it does not add a missingness latent or population hierarchy without evidence.
Related API decision: normative contract §§12, 20–21.

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

- Rejected a mutable singleton registry: registration is an immutable, sorted provider list with duplicate identity failure.
- Rejected `ServiceLoader`/runtime classpath discovery for v1: build integration is sufficient and deterministic; arbitrary downloaded code remains prohibited.
- Rejected one universal tag-coefficient row: episode location learning and conditional observation-variance learning use separate state codecs and updates.
- Rejected giving providers DAOs/raw notes: modules receive capability-checked typed views; availability is not permission.
- Rejected catching coroutine cancellation as module failure: host cancellation propagates.
- Rejected treating all annotations as inference evidence: the legacy adapter requires `CANDIDATE_COVARIATE` eligibility.
- Rejected additive context medians: Core collapses a declared correlation group and combines independent groups by precision, with disagreement widening uncertainty.

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

REVIEW 1 — 2026-09-03T17:05Z / 487705c

Block objective: verify remote/authority, audit the current context/inference/Room substrate, research registration/state candidates, and pre-register 7E before behaviour code.

Completed: remote equals prepared head; read authority stack; audited live v1 tag IDs/eligibility, NoteInterpreter boundary, ContextEvidenceView, generic configs/manifests/runs, capability/dose/adaptive-state tables, dependency index, backup shape and Room14. Wrote the normative state/context contract before real-history output inspection.

Diff / architecture notes: documentation only. Room15 decision is based on semantic ownership, not convenience. Selected neutral two-state robust Gaussian filter and explicit provider registry.

Tests/builds/background tasks: no build started; repository initially clean. Process inspection printed `fatal library error, lookup self` after the required git checks; no Gradle/Java task was running and no code workaround was made.

Errors/warnings: the process-list helper failure needs no product-code change; continue using direct Gradle/session inspection. Existing `fallbackToDestructiveMigration(true)` predates 7E and remains a later Native-cutover concern, not broadened by 7E.

Extension/API decisions recorded: 7E-API-001 through 005; target table and research entries updated.

Internet check: Android ServiceLoader, IntelliJ extension points, OpenTelemetry schema evolution, impulse-response limitations and dynamic shrinkage materially informed registry/versioning/baseline decisions.

Scope check: SHADOW only; no normal UX/prescription, 7F, 7G, 8 or 9 work.

Next block: implement domain-level feature/module/signal/read-view/registry/arbitration contracts plus pure temporal solver and focused unit/TCK tests before persistence.

REVIEW 2 — 2026-09-03T17:36Z / 487705c

Block objective: implement and test the preregistered neutral filter and the first build-integrated context-module protocol without persistence.

Completed: implemented the versioned two-component robust sequential filter; optional shrunk SessionDose covariate; feature/evidence/missingness definitions; capability-checked read view; provider/registry/runtime; signal envelope/validator/arbitrator; illness episode association learner; time-pressure observation-variance learner; legacy evidence adapter; and focused temporal/module tests.

Diff / architecture notes: Core has no feature-ID branch. Two learner state/codecs are genuinely different. Correlated signals collapse by declared group before precision combination; contradiction widens uncertainty. The production ontology was not expanded to manufacture local evidence; local targeting is exercised synthetically.

Tests/builds/background tasks: direct Android Gradle resolution stopped before compilation because `com.android.application:9.1.1` was unavailable from the reachable plugin repositories. A Gradle-bundled Kotlin compiler plus JUnit 4 compiled the pure domain/module sources and ran 25 tests: PASS. No Gradle/Java background process remained.

Errors/warnings: review found cancellation would have been swallowed by the generic failure boundary, a provider could theoretically be handed another feature's evidence, and the variance learner selected one latest row rather than folding all unseen rows chronologically. All three were corrected; the legacy adapter was also tightened to inference-eligible annotations.

Extension/API decisions recorded: cancellation propagates; descriptor/evidence ownership is host-checked; chronology is ascending and idempotent by stable evidence ID; target/effect-representation compatibility is validated before arbitration.

Internet check:
- question: coroutine ownership/cancellation, dynamic-code safety, and Room migration verification;
- source/project: Kotlin official docs, Android dynamic-code-loading guidance, Android Room migration guide;
- lesson: host-owned structured concurrency, fail closed against runtime code, and test exported schema migrations;
- implementation effect: rethrow cancellation; retain compile-time registry; plan explicit 14→15 migration test.

Scope check: all outputs remain shadow/candidate; no prescription/UI authority and no 7F/7G/8/9 semantics.

Reset: reread mission objective, target matrix and next-block scope before resuming implementation.

Next block: add narrowly semantic Room15 entities/DAO/migration, atomic persistence, deletion/replay and invalidation tests; do not reuse unrelated adaptive biological state tables.

REVIEW 3 — 2026-09-03T18:12Z / 487705c

Block objective: add derived persistence/replay/invalidation and expand the preregistered synthetic/TCK surface before device integration.

Completed: added five additive Room15 tables, explicit 14→15 SQL migration and migration test; atomic run persistence; module/signal/run deletion; registry/version-checked reload; deterministic delimiter-safe provenance encoding; Native backup coverage; reannotation invalidation; 17 temporal and 25 context-module device-runnable fixtures; reusable provider TCK; and the causal installed-history replay/report foundation.

Diff / architecture notes: 7E rows cascade from a source inference run and contain only derived state/provenance. Reannotation transactionally deletes 7E runs but not raw annotations, notes, workouts or 7C/7D rows. Feature definitions are now an explicit registry and the production runtime validates value/source/scope/version before module execution.

Tests/builds/background tasks: pure Kotlin compilation and 44 JUnit tests PASS, including all 17+25 device-mirror cases. Android Gradle remains blocked at plugin resolution before project compilation; no Gradle daemon/background job remains. `git diff --check` is clean.

Errors/warnings: review found two chronology/idempotency problems: session-scoped time-pressure presence could have been carried into future sessions, and a repeated post-update call could count the same residual again despite processed evidence. State now stores the evidence interval and publishes only inside it; variance learning requires newly seen explicit present/false evidence. The review also confirmed that source-run cascade is desirable invalidation, not data loss.

Extension/API decisions recorded: Room15 state ownership; atomic reannotation invalidation; strict production feature registry; persistence compatibility checks; module diagnostic text bounded to 240 characters; no stack/raw-note persistence.

Internet check:
- question: informative longitudinal missingness and sport-specific predictive missing patterns;
- source/project: Biometrics/PubMed longitudinal covariate study, longitudinal missing-data review, JRSS C runner state-space work;
- lesson: neither carry-forward nor false imputation is justified; availability can be reported/predictive without fabricating evidence;
- implementation effect: session context expires, no-mention stays unknown, no missingness latent was added.

Scope check: retained neutral naming and shadow authority; SessionDose remains optional/PD-002; no equipment translation, conditioning, coaching or normal UI path.

Reset: reread mission, target matrix and journal; next work is limited to the consolidated developer action, schema/build repair, docs and exact-head evidence.

Next block: compile-check the installed-history runner, wire the single developer card, generate/verify Room15 schema, complete PD-003/closure docs, then run full CI.

REVIEW 4 — 2026-09-03T21:41Z / 64cea60

Block objective: inspect the first pushed foundation checkpoint against exact-head Android CI and audit the committed contract/persistence/device-integration diff while the build ran.

Completed: reconciled local and remote at the same tree and commit; reviewed the 4,723-line additive diff; inspected the Room entities/DAO/migration, generic backup path, acceptance runner, developer card, repository codecs and context-reannotation invalidation. Corrected the one whitespace defect found by `git diff --check` and the Kotlin receiver defect reported by CI.

Diff / architecture notes: the compile failure was local to reporting mathematics: a nested metrics accumulator called two acceptance-runner instance helpers without an outer receiver. Making the accumulator `inner` preserves the preregistered equations and changes no inference behaviour. Room15 still contains derived-only state and no module receives a DAO or raw note.

Tests/builds/background tasks: GitHub Android CI run 667 reached all pre-build guards, reference validation, exercise schema validation, context/privacy validation and whitespace validation successfully. `:app:compileDebugKotlin` then failed at `NBio7EStateContextAcceptance.kt:381`; instrumentation compile, lint and Room-schema verification were correctly skipped. The exact log was inspected; there was no hidden second compiler error.

Errors/warnings: Kotlin nested classes do not hold an outer instance, so the CRPS accumulator could not resolve `normalCdf`/`normalPdf`. The source-only test harness did not compile the Android acceptance runner, which is why focused pure tests did not expose this. Keep exact-head Android CI as the integration authority. Also removed trailing whitespace from the preregistration date line.

Extension/API decisions recorded: none changed. This was an Android integration repair; provider, read-view, signal, lifecycle and persistence contracts are unchanged.

Internet check: no lookup was justified for a compiler receiver rule already demonstrated by the exact compiler diagnostic. The next external check remains contingent on a substantive migration/lint/API issue rather than generic searching.

Scope check: SHADOW only; PD-001/PD-002 remain open; no product authority, 7F, 7G, 8 or 9 work.

Reset: reread the mission’s exact-head/physical acceptance conditions and the current journal target matrix. The next block is restricted to integration repair, schema capture, explicit PD-003 and evidence-backed checkpoint documentation.

Next block: push the two-line repair plus this review, let the full workflow reach instrumentation/lint/schema stages, then consume the generated Room15 schema artifact and address any further exact errors.

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
