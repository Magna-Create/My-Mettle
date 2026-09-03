# N-BIO-7E — Context Module & Personalised Association Architecture

> **Status:** authoritative additive requirements contract for the future N-BIO-7E context-learning substrate.
>
> This document does **not** start N-BIO-7E and does not make any context signal behaviour-driving. It exists so the 7E implementation cannot accidentally reduce the existing tag library to passive metadata.
>
> Read with [`PLAN.md`](./PLAN.md), [`CONTEXT_INTERPRETATION_CONTRACT.md`](./CONTEXT_INTERPRETATION_CONTRACT.md), [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md), [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md), [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md) and the relevant research guides.

---

# 1. Purpose

N-BIO-7A.5 already provides a safe interpretation boundary:

```text
raw review / note
→ versioned structured interpretation
→ typed ContextEvidenceView
```

That remains correct, but it is only the input boundary.

N-BIO-7E must provide the **learning platform** that allows typed context to acquire user-specific predictive meaning over time.

The required long-term architecture is:

```text
RAW / EXPLICIT / SENSOR SOURCE
        ↓
versioned ContextFeature evidence
        ↓
feature-specific ContextModule
        ↓
module-owned derived memory + learner
        ↓
uncertainty-aware ContextSignal
        ↓
N-BIO core arbitration / state inference
        ↓
context-conditioned state / prediction
        ↓
later N-BIO-8 decision policy
```

The tag library is therefore not merely a label registry. It is an extensible family of typed contextual features that can plug into a common learning-and-signal protocol.

---

# 2. Core architectural rule

A context feature is conceptually analogous to an entity implementing a shared engine contract:

- every feature participates in the same module protocol;
- each feature may expose different capabilities;
- each feature may maintain its own derived memory;
- each feature may use its own internal learning model;
- each feature publishes standardised signals rather than directly mutating N-BIO state.

Do **not** hard-code every future context concept into N-BIO Core with feature-specific `if/when` logic.

The core should understand a generic signal language. New context modules should be able to join that language without changing the core combination architecture merely because a new tag ID exists.

---

# 3. Tag / feature / module separation

Keep three concepts distinct.

## 3.1 ContextFeatureDefinition

The versioned schema describing what a feature means and what it is permitted to do.

Conceptually it may contain equivalents of:

```text
featureId
schemaVersion
valueSchema
scope
humanMeaning
sourceKinds
temporalSemantics
allowedAssociationTargets
semanticBoundaryCapabilities
missingnessSemantics
privacy/readPermissions
compatibilityRules
```

Exact class/field names may differ after source audit.

## 3.2 ContextFeatureEvidence

An immutable or correction-aware historical observation such as:

```text
ILLNESS_REPORTED = true
LOCAL_SORENESS(triceps) = moderate
SLEEP_QUALITY = poor
MACHINE_INSTANCE = equipmentInstanceId
SESSION_RUSHED = true
```

The evidence retains source/provenance, timestamp/scope, assertion/currentness and schema identity.

## 3.3 ContextModule

A stateful derived inference component for one feature or one explicitly versioned feature family.

Conceptually:

```text
ContextModule
├── definition
├── approved evidence inputs
├── own derived memory/state
├── own learner / update rule
├── evidence-support accounting
├── temporal state where applicable
└── published ContextSignals
```

A module is not raw truth. Its memory/state is derived and replayable.

---

# 4. Extensible typed feature library

Do not freeze the future library to today's finite tag set.

New features must be addable without redesigning N-BIO Core.

Examples may eventually include:

```text
ILLNESS
SLEEP_QUALITY
LOCAL_SORENESS
GENERAL_ENERGY
TRAVEL
JET_LAG
FASTED_TRAINING
CAFFEINE
SESSION_RUSHED
MACHINE_INSTANCE
GRIP_CHANGE
ROM_CHANGE
ENVIRONMENT_TEMPERATURE
```

This list is illustrative, not an approved ontology expansion.

A feature is not required to be boolean.

Future versioned value schemas may support, where justified:

- boolean;
- ordinal;
- continuous numeric + unit;
- categorical;
- anatomy-scoped structured value;
- equipment/profile reference;
- bounded structured object.

Existing 7A.5 v1 annotations remain historical evidence under their original schema. A richer v2/v3 feature definition must not silently rewrite old values.

---

# 5. Versioning and schema evolution

Changing what a tag records must not destroy historical interpretability.

Example:

```text
SLEEP_QUALITY_V1
  category = POOR

SLEEP_QUALITY_V2
  subjective = 3/7

SLEEP_QUALITY_V3
  subjective = 3/7
  measuredDuration = 6.1 h
  timingDeviation = +1.4 h
```

Old evidence remains associated with the definition/version that produced it.

A new module/config may choose to:

- consume several compatible feature versions;
- translate an older version through an explicit versioned adapter;
- ignore an unsupported historical version;
- fail closed when semantics are incompatible.

Never reinterpret a historical value under a newer schema merely because the human-readable feature name is similar.

---

# 6. Independent module memory

Each context module may maintain its own derived memory so it can learn from repeated user-specific evidence.

Examples of module-owned learned state may include:

- association magnitude posterior;
- association direction uncertainty;
- persistence/duration posterior;
- observation-noise association;
- scope-specific effects;
- independent episode count;
- evidence maturity;
- recency;
- model residual history or sufficient statistics;
- module-specific latent episode state.

This memory must be:

- explicitly model/config versioned;
- derived rather than canonical;
- deletable/rebuildable;
- backup-safe;
- replayable from authorised canonical evidence plus declared upstream derived inputs;
- incapable of rewriting raw ContextFeatureEvidence.

A module may learn over months/years without forcing N-BIO Core itself to contain feature-specific learning equations.

---

# 7. Controlled read boundary

A ContextModule may read only explicitly authorised, typed views required by its model.

Potential read classes include:

```text
its own ContextFeatureEvidence
elapsed time / timestamps
profile/session/side scope
capability posterior summaries
performance residuals
SessionDose / later state summaries
prediction errors
approved equipment/profile semantics
```

Availability does not imply permission.

Every module/config must declare its consumed inputs and versions.

Modules must not receive unrestricted database access merely for convenience.

Raw note text is not a generic module input. Language interpretation remains the job of the 7A.5 interpreter boundary unless a future explicitly reviewed model requires otherwise.

Nano must not be given wider N-BIO history merely because the downstream module can use structured signals.

---

# 8. Controlled write boundary

Context modules must **not directly mutate N-BIO Core latent state**.

Never allow behaviour conceptually equivalent to:

```text
IllnessModule:
    recovery -= 0.17
```

A module publishes a versioned uncertainty-aware `ContextSignal` instead.

Conceptually:

```text
ContextSignal
├── sourceFeature / module identity
├── target
├── scope
├── direction / effect representation
├── posterior or sufficient uncertainty summary
├── temporal extent / persistence
├── evidence support
├── provenance
├── model/config version
└── applicability / failure status
```

N-BIO Core decides how, or whether, to combine that signal with other evidence.

This boundary prevents one poorly calibrated module from unilaterally poisoning the central state estimate.

---

# 9. Association targets

A feature definition must declare which classes of association it is allowed to learn/publish.

Candidate target families include:

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

These are capability categories, not automatic enabled effects.

N-BIO-7E should initially enable only targets actually within the accepted 7E state/recovery scope.

Equipment translation/recruitment capabilities may exist in the protocol so later N-BIO-7F/equipment work can plug in, but 7E must not silently implement 7F mechanics.

A feature may support more than one target where evidence justifies it.

Examples:

- illness may plausibly publish systemic transient-state/persistence signals;
- local soreness may publish anatomy-scoped transient-state signals;
- poor form may primarily affect observation reliability/noise;
- machine identity may later affect equipment translation/capability offset/recruitment rather than systemic state.

Do not force every feature into one universal `stateModifier` scalar.

---

# 10. Personalised associative learning

The desired behaviour is user-specific learning.

N-BIO must be able to discover patterns such as:

```text
poor sleep
→ little consistent median-performance effect for this user
→ possibly higher variance

illness
→ meaningful systemic suppression for this user
→ often persists across several days

local soreness
→ modest local suppression
→ little systemic effect

"felt tired"
→ weak/noisy predictor
```

These are examples of possible learned outcomes, not hard-coded rules.

A new module starts with broad/neutral uncertainty.

With insufficient evidence the correct result is effectively:

```text
stored context
association unknown
no authoritative effect
```

As independent observations accumulate the module may become `PARTIALLY_LEARNED` / `DATA_INFORMED` or equivalent.

A negative result is useful: repeated evidence may support shrinking a feature toward negligible predictive influence.

Do not require every stored tag to become biologically influential.

---

# 11. Association is not causation

Ordinary personal training history is observational and highly confounded.

A module may learn:

> `ILLNESS_PRESENT` improves prediction of transient suppression.

It usually may not claim:

> illness alone caused exactly 7.2% suppression.

Documents, diagnostics and naming must prefer:

- association;
- predictive contribution;
- explanatory evidence;
- conditional effect estimate under the declared model;

rather than unsupported causal language.

If causal identification is ever attempted, it requires a separate research/model contract.

---

# 12. Temporal context episodes

Context is not always session-local.

Some features may have temporal semantics such as:

```text
instantaneous
session-scoped
fixed interval
episode-like persistent
decaying
unknown persistence
```

For example, an illness report on Monday may still be relevant on Wednesday even if Wednesday's note does not repeat the word `ill`.

Do not achieve this by copying the raw tag onto future sessions.

Instead, an eligible module may infer a derived uncertainty-aware context episode/state:

```text
source illness evidence
        ↓
module episode posterior
        ├── onset
        ├── persistence
        ├── intensity/effect uncertainty
        └── resolution probability
```

The episode is derived state and can be revised/replayed. The original evidence remains unchanged.

Persistence parameters should become user-specific only when evidence supports learning them.

---

# 13. Bidirectional learning protocol

The information flow is controlled but can be bidirectional.

```text
N-BIO approved evidence/state summaries
        ↓
ContextModule learns/updates
        ↓
ContextSignal
        ↓
N-BIO Core arbitration/state inference
```

Examples of feedback useful to a module may include:

- realised performance residual after a prediction was frozen;
- whether suppression was systemic or profile-local under the core model;
- later observed recovery trajectory;
- model prediction error.

Circular inference must be prevented.

The module/config must specify chronology and whether an upstream state is pre-session, posterior-after-session, smoothed retrospective, or another explicitly named view.

No module may train on future outcomes and then retroactively claim a causal/prequential prediction for the same timestamp.

---

# 14. Core arbitration and correlated context

Independent modules do not mean independent effects.

Common contexts often co-occur:

```text
ILLNESS
POOR_SLEEP
LOW_ENERGY
```

N-BIO Core must not blindly add three module medians and triple-count one latent situation.

The central combination layer owns:

- correlation/dependence handling;
- competing explanations;
- duplicated information;
- contradictory signals;
- interaction terms where evidence supports them;
- shared uncertainty;
- precedence between semantic facts and statistical associations.

The conceptual responsibility split is:

```text
ContextModule:
"What has my own historical evidence taught me about this feature?"

N-BIO Core:
"Given all current evidence and modules, what combined state is most plausible?"
```

A first 7E candidate may use a strongly regularised/simple combination model. Complexity must be earned by held-out predictive benefit.

---

# 15. Semantic context versus learned context effect

A feature can have more than one role.

`MACHINE_DIFFERENT`, `GRIP_CHANGED`, `ROM_MODIFIED` and similar evidence may:

1. warn that semantic continuity may need review;
2. provide context that explains an observed performance difference;
3. later participate in equipment translation or recruitment modelling where appropriate.

Therefore do not classify execution-related context as merely `valid/invalid observation` metadata.

Known canonical equipment/execution metadata still outranks a text-extracted warning when both exist.

A Nano-extracted `MACHINE_DIFFERENT` report must not silently mutate canonical `EquipmentInstance`, `SessionEquipmentBinding` or `ExecutionProfileVersion`.

Later confirmed machine selection can become canonical equipment evidence through the dedicated equipment pathway.

---

# 16. Nano remains an extractor, not a learner/controller

The invariant remains:

> Nano extracts structured context. N-BIO decides what context means mathematically.

Nano may produce a typed feature observation under the existing privacy/provenance contract.

Nano must not output:

- capability penalties;
- recovery percentages;
- fatigue multipliers;
- predicted illness duration;
- programme changes;
- dose corrections.

The module learner and N-BIO Core own those later probabilistic relationships.

---

# 17. Multiple producers, one context protocol

Nano is only one producer.

The same future feature/module system should be capable of accepting typed evidence from:

- explicit user input;
- deterministic app events;
- equipment selection/binding;
- imported structured data;
- Health Connect;
- wearables/sensors;
- future privacy-reviewed on-device models.

A producer should not need bespoke N-BIO Core code if it can emit evidence conforming to a registered feature definition.

N-BIO-9 remains the later product/integration phase for bringing many of these producers online, reannotation/recompute controls and broader data lifecycle UX.

N-BIO-7E owns the core module/learning/signal infrastructure so N-BIO-9 does not have to retrofit intelligence onto passive metadata.

---

# 18. Context-conditioned state versus decision policy

N-BIO-7E may infer something conceptually like:

```text
persistent capability: stable
current transient systemic state: suppressed
context association: illness-compatible
likely persistence: several days, uncertain
```

It must **not** decide the coaching action.

N-BIO-8 later decides whether that state should cause:

- maintained targets;
- load reduction;
- volume reduction;
- optional work;
- exercise substitution;
- deload-like behaviour;
- no change.

Thus:

```text
7E = infer context-conditioned state
8  = choose action under programme intent / constraints / uncertainty
```

A context tag must never directly encode `REMOVE_TWO_SETS` as biological inference.

---

# 19. Motivation-sensitive use case without hard-coded coaching

The architecture must support situations such as:

```text
normal latent capability
≈ stable

Monday observed performance
↓ while ILLNESS_PRESENT

personal history
→ illness has previously predicted temporary systemic suppression
→ effect often persists for several days
```

7E may then maintain a distinction between persistent capability and temporary context-conditioned availability.

This prevents ordinary capability learning from necessarily interpreting an illness week as structural regression.

Later N-BIO-8 may use that state to avoid repeatedly demanding frontier progression while the user is temporarily suppressed.

This requirement is architectural. It does not pre-authorise a specific deload rule or illness penalty.

---

# 20. Module model diversity

Different modules may legitimately need different internal learners.

Examples:

- illness: episode/persistence state-space model;
- machine instance: relatively stable translation/offset learner later;
- local soreness: local short-lived association model;
- caffeine: dose/timing model if reliable evidence ever exists;
- session rushed: observation-noise/variance association.

They must share the external module/signal contract, not the same internal equation.

Do not create a universal `tagCoefficient` table and call the architecture complete if that prevents feature-specific temporal/state semantics.

Conversely, do not build custom complexity for every tag before data justify it. A simple generic learner may be the default implementation class where appropriate.

---

# 21. Evidence maturity and promotion

Module influence must be earned.

At minimum preserve concepts equivalent to:

```text
NO_EVIDENCE
PRIOR_DOMINATED
PARTIALLY_LEARNED
DATA_INFORMED
EMPIRICALLY_USEFUL
REJECTED / NO_PREDICTIVE_BENEFIT
```

Exact enums may reuse the existing parameter-level evidence framework.

Normal candidate evaluation should compare, where feasible:

```text
Model A = relevant state/performance model without the module signal
Model B = A + module signal
```

using chronological held-out/prequential scoring.

A context feature does not earn influence because it is intuitively plausible or because Nano extracted it confidently.

Interpreter confidence and N-BIO predictive evidence are separate quantities.

---

# 22. Sparse evidence, regularisation and pooling

Personal context evidence will often be sparse.

Requirements:

- new modules start broad/neutral;
- independent episodes matter more than many repeated annotations from one episode;
- avoid treating five sessions during the same illness as five independent illness events;
- strong regularisation/shrinkage is preferred to dramatic estimates from tiny histories;
- optional hierarchy/population priors require explicit provenance and validation;
- direct personal evidence should dominate generic priors as it accumulates;
- unsupported module output remains neutral/broad rather than fabricated.

Do not create a false impression of precision because a tag has many rows.

---

# 23. Missingness semantics

Every feature definition must specify whether absence means:

```text
known false
not reported
not measured
not applicable
unknown
```

These are not interchangeable.

For example, no illness tag in a review does not automatically prove `ILLNESS=false` unless the collection surface explicitly establishes that semantics.

The learner must not train unmentioned context as negative examples by default.

---

# 24. Scope

Feature/module scope may include:

```text
set
session exercise
execution profile
side
session
day
episode
muscle / anatomical region
systemic
equipment instance
```

A module must not broaden scope silently.

For example:

- triceps soreness must not automatically become whole-body suppression;
- a machine-instance effect must not automatically transfer across gyms/equipment models;
- a systemic illness signal may influence several profiles only through the core's accepted systemic-state model.

---

# 25. Provenance and identity

Every behaviour-relevant module signal must be reproducible from explicit provenance.

At minimum retain identities equivalent to:

```text
feature definition/schema version
source evidence ids/revisions
interpreter/source provenance
module model/config version
module state version
consumed upstream model versions
run/replay mode
produced signal version
timestamp/scope
```

If a module's mathematics changes, create a new immutable model/config identity rather than mutating old learned state in place.

---

# 26. Persistence and replay

The eventual implementation must support:

```text
canonical evidence
+ feature definitions
+ upstream approved state/evidence
+ module model/config
→ module memory
→ ContextSignals
→ core context-conditioned inference
```

Deleting derived module memory/signals must not delete raw annotations, notes, workouts or equipment evidence.

Full replay must rebuild module state deterministically where deterministic semantics are claimed, or within explicit approximation tolerance otherwise.

Corrections/reannotations must invalidate only affected downstream derived state through the existing dependency architecture.

No feature module may become a hidden non-replayable memory store.

---

# 27. Privacy and security boundary

The modular architecture must not become a reason to widen sensitive-data access.

Requirements:

- least-privilege typed reads;
- no unrestricted raw-note access by default;
- no module-to-module arbitrary database reads;
- no hidden external network requirement for core learning;
- clear provenance for externally imported structured evidence;
- future Health data enters only through the Health/privacy contract;
- developer exports remain privacy-bounded.

If a future third-party/licensed integration supplies context, it must conform to the same typed/provenance/privacy boundary rather than injecting arbitrary code/data into the core state.

---

# 28. N-BIO-7E implementation boundary

7E must provide the platform for:

- context-feature definition/version compatibility;
- stateful ContextModule lifecycle;
- module-owned derived memory;
- module-specific learning/update interfaces;
- authorised bidirectional evidence/state views;
- standard ContextSignal publication;
- central correlation/arbitration hooks;
- temporal episode/persistence support;
- evidence maturity/support accounting;
- replay/invalidation/provenance;
- shadow evaluation of context-conditioned state/prediction.

7E does **not** need to implement every conceivable tag learner.

A small representative set should prove the architecture, for example one systemic episode-like feature, one local feature and one observation-quality feature, subject to research and available evidence.

Do not choose the final representative modules silently if the scientific/UX semantics are underdetermined.

---

# 29. N-BIO-7F / equipment boundary

7E's generic module protocol may declare equipment-related signal targets, but equipment identity/calibration/translation remains governed by the late-7 equipment contract in [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md).

A confirmed machine change may later teach a machine module about:

- equipment-specific capability offset;
- resistance/challenge translation;
- observation variance;
- recruitment differences;

but those relationships must use canonical equipment bindings/calibration where available.

Text-extracted `EQUIPMENT_DIFFERENCE_REPORTED` is contextual evidence, not canonical machine identity.

---

# 30. N-BIO-9 boundary

N-BIO-9 does not own creation of the context-learning architecture.

Its later responsibilities may include:

- additional producers such as Health Connect/wearables;
- richer explicit context UX;
- Nano/model rollout and reannotation controls;
- module/model recomputation UX;
- dashboards/notifications;
- analysis export;
- privacy/permission lifecycle.

Those systems should plug into the 7E context protocol rather than inventing parallel context stores.

---

# 31. N-BIO-8 consumer boundary

N-BIO-8 should preferentially consume N-BIO's **combined context-conditioned state/prediction**, not implement a second ad-hoc per-tag learning system.

Raw/explicit context may still enter product-safety rules where separately approved, but biological/programme inference must preserve the 7E signal/state boundary.

V8 must not contain an ever-growing hard-coded table such as:

```text
if ILLNESS then reduce load
if POOR_SLEEP then remove set
if TRAVEL then deload
```

Context-informed actions must flow through explicit state inference plus versioned decision policy.

---

# 32. Validation requirements

Before 7E structural closure, prove at minimum:

1. a new feature/module can be registered without adding feature-specific branching to N-BIO Core;
2. two modules can use different internal learner implementations while publishing the same signal protocol;
3. module state persists/reloads with immutable model/config identity;
4. delete-derived/full-replay reconstructs module memory and signals;
5. raw note/context evidence is unchanged by module learning;
6. missing tag does not become a false negative when missingness semantics are `UNKNOWN/NOT_REPORTED`;
7. module scope is enforced;
8. temporal episode state can persist beyond one source session without copying raw annotations forward;
9. unsupported/sparse modules publish broad/neutral/unavailable signals rather than precise effects;
10. a module cannot directly write core latent state;
11. central arbitration prevents naive additive double-counting in a correlated synthetic case;
12. chronology prevents future-outcome leakage into prequential module learning;
13. feature-schema upgrade preserves historical evidence interpretation;
14. Nano extraction confidence cannot masquerade as biological confidence;
15. context-aware candidate can be compared against a context-free baseline;
16. a no-benefit context feature can remain/shrink effectively inert;
17. corrections/reannotations invalidate only dependent derived module state;
18. Native backup/replay remains valid;
19. normal product authority remains unchanged;
20. N-BIO-8 behaviour has not been silently implemented.

---

# 33. Acceptance diagnostics

A future 7E developer acceptance should report, privacy-bounded:

- registered feature/module identities and versions;
- module state/evidence-support counts;
- independent episode/session counts;
- target/scope declarations;
- signal support states;
- context-free vs context-aware synthetic/prequential metrics where evaluable;
- temporal-persistence recovery fixtures;
- correlated-module arbitration fixtures;
- persistence/reload/replay;
- correction/reannotation invalidation;
- raw-evidence fingerprints;
- product-authority fingerprints;
- backup/foreign-key/schema checks;
- numerical/runtime failures;
- empirical limitations;
- whether any module is behaviour-driving;
- confirmation N-BIO-8 has not started.

Synthetic structural success must not be presented as proof that a particular context feature is biologically causal or human-calibrated.

---

# 34. Architectural summary

The intended contract is:

```text
FEATURE DEFINITION
= what this contextual entity is allowed to represent/do

FEATURE EVIDENCE
= what was actually observed/reported/measured

CONTEXT MODULE
= this feature's own replayable memory + learner

CONTEXT SIGNAL
= standard uncertainty-aware message to N-BIO

N-BIO CORE
= arbitration and combined state inference

N-BIO-8
= coaching/programme decision policy
```

This preserves the desired modularity:

> more tags/features can be created over time, their schemas can evolve through explicit versions, and their own learning engines can be added or replaced without hard-coding every contextual association into N-BIO Core.
