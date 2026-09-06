# N-BIO-7F — Equipment Context & Cross-Profile Translation Contract

> **Status:** preregistered, research-reconciled implementation contract for N-BIO-7F.
>
> Read with [`PLAN.md`](./PLAN.md), [`ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`](./ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md), [`CORE_MODEL_DETAIL.md`](./CORE_MODEL_DETAIL.md), [`PRODUCT_ROADMAP_GATES.md`](./PRODUCT_ROADMAP_GATES.md), [`CONTEXT_MODULE_ARCHITECTURE.md`](./CONTEXT_MODULE_ARCHITECTURE.md), and [`RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md`](./RESEARCH_EQUIPMENT_TRANSLATION_GUIDE.md).
>
> This contract does not promote any 7F model to normal product authority. `BENCHMARK_V0` remains normal product authority unless a later explicit promotion gate says otherwise.

## 1. Mission

N-BIO-7F adds two capabilities without changing normal product behaviour:

1. preserve the equipment/load semantics needed to know what historical resistance evidence meant; and
2. test whether one local capability context improves prediction in another local context.

7F is **not** an equipment converter and does not create a universal resistance scalar.

The governing sequence is:

```text
canonical local evidence
        ↓
local equipment / entry interpretation
        ↓
existing same-profile capability inference
        ↓
versioned directed relationship candidate
        ↓
destination predictive distribution
        ↓
prequential comparison against no-transfer
```

`NO_USEFUL_TRANSFER` is a valid result.

---

# 2. Non-negotiable invariants

1. Raw entered and canonical performance values remain immutable historical evidence.
2. There is no universal `L_true`.
3. There is no global user-strength scalar.
4. `L_Tensor` remains conceptual shorthand only; do not add a type merely because the research used the term.
5. Existing `EntryBasis.TOTAL/PER_HAND/PER_SIDE` meanings do not change.
6. Existing `ResistanceSemantics` and heterogeneous metric families do not collapse into one coordinate.
7. `MACHINE_LEVEL` remains ordinal and is not silently converted to kg.
8. Selector-labelled kg and true ordinal levels remain semantically distinct.
9. Equipment means machines **and** free-weight/simple implements.
10. Exact local physics may inform local interpretation; it never establishes personal cross-profile equivalence by itself.
11. Unknown equipment/mechanics remain unknown. Do not fill missing pulley ratio, starting resistance, friction, model identity or calibration with convenient defaults.
12. Canonical equipment identity/history lives in Core/domain persistence, not ContextModule derived memory.
13. Learned transfer state is derived, versioned, deletable and replayable.
14. Equipment identity and execution-profile identity remain separate. Hardware change alone does not automatically create a new `ExecutionProfileVersion`.
15. Preferred/default equipment is user/product preference state, not immutable execution-profile semantics.
16. Direct destination evidence remains direct local evidence. Transferred evidence cannot rewrite it.
17. Candidate transfer remains SHADOW/developer-only until explicitly promoted.
18. Validation is chronological. Future evidence cannot enter an earlier frozen prediction.
19. Negative results are preserved. Do not retune a frozen candidate after seeing its acceptance failure.
20. No 7F work may implement N-BIO-8 prescription/UX behaviour, camera/OCR equipment recognition, or product-facing equipment setup unless separately requested.

---

# 3. Phase shape

Use these implementation layers. Exact package/class names remain source-led.

```text
7F-A  Canonical equipment and load-semantics substrate
7F-B  Historical equipment binding + local interpretation
7F-C  Typed capability-transfer boundary
7F-D  N0/M0 candidate inference
7F-E  Replay, invalidation, persistence and backup
7F-F  Synthetic + real-history prequential evaluation
7F-G  Documentation / closure / empirical-debt record
```

Do not create extra roadmap phases merely to rename work already inside these layers.

---

# 4. 7F-A — Canonical equipment and load semantics

## 4.1 Source audit first

Before changing schemas, audit the current domain, Room15 entities/DAOs, backup format, execution-profile authoring/import path, session-exercise representation and performance-observation ownership.

Reuse an existing owner when its semantics are honest.

Advance Room15 only if canonical equipment identity/version/binding cannot be represented safely without a new owner. If Room16 is required, the migration must be explicit and additive.

No destructive fallback migration.

## 4.2 Equipment is a general domain concept

The substrate must be able to represent at least the semantics needed for:

- barbell;
- dumbbell;
- kettlebell;
- plates / loaded free-weight implements;
- Smith machine;
- cable / pulley system;
- selectorised machine;
- plate-loaded lever equipment;
- sled / rail equipment;
- assisted resistance/bodyweight hardware;
- unknown/other equipment without invented mechanics.

Do not force free weights through machine-only fields.

## 4.3 Stable identity

Support stable anonymous identity where useful.

A physical equipment instance may exist with:

```text
manufacturer = unknown
model = unknown
```

and still accumulate direct local history.

Later identity enrichment must not replace or rewrite historical bindings.

Exact model identity does not imply two physical instances are identical.

## 4.4 Specification/configuration/calibration provenance

The old conceptual roadmap name `EquipmentCalibrationVersion` is not binding.

Source audit must choose naming that does not imply instrumented calibration for ordinary OEM declarations.

At minimum preserve epistemic provenance between facts conceptually equivalent to:

```text
OEM_DECLARED_SPECIFICATION
USER_CONFIRMED_CONFIGURATION
DETERMINISTIC_DERIVATION
MEASURED_INSTANCE_CALIBRATION
```

These do not require four separate tables. They require distinguishable meaning and provenance.

Potential behaviour-relevant fields are optional and nullable unless source/product semantics require otherwise:

- manufacturer/model/family;
- local display/load semantics;
- implement/base/starting resistance;
- pulley/mechanical ratio;
- rail/glide angle;
- independent-arm/coupling structure;
- loading mechanism;
- local increments/allowed selections;
- source/provenance/quality/version.

Do not add guessed friction, complete machine geometry or mandatory cam curves.

## 4.5 Load-value semantic gap

Keep `EntryBasis` unchanged as the existing aggregation convention.

Add only the minimum orthogonal semantic needed to distinguish, where relevant:

```text
complete/inclusive external load
vs
added-only external load
```

Exact enum/type/storage names follow source audit.

Legacy observations whose convention cannot be reconstructed remain unknown. Migration must not guess.

Assistance remains owned by existing resistance semantics/metrics; do not duplicate it into a new flat entry-basis enum.

## 4.6 Mass-labelled selector versus ordinal

A device value labelled `40 kg` remains mass-dimensional local evidence.

`Level 7` remains ordinal device evidence.

Do not reinterpret one as the other.

---

# 5. 7F-B — Historical binding and local interpretation

## 5.1 Binding invariant

Every equipment-sensitive performance observation must be able to resolve the equipment interpretation that applied when the observation occurred.

History must never be reconstructed from today's preferred/default equipment.

A candidate topology is:

```text
ExecutionProfileVersion
    immutable execution semantics

PreferredEquipmentBinding
    current preference / regular equipment

SessionExerciseEquipmentBinding
    actual historical equipment for the occurrence

set/observation override
    only when actual equipment differs within the occurrence
```

The exact foreign-key layout is not preregistered. The invariant is.

A session-only equipment choice must not silently become a persistent preference. A preference change must not rewrite historical actual-use bindings.

## 5.2 Local deterministic interpretation

Allow deterministic local arithmetic only when the meaning is exact.

Examples:

```text
known 20 kg bar + known 60 kg added plates → 80 kg configured local mass
```

or a genuinely documented device-local pulley relationship whose scope and label semantics are known.

Preserve:

- raw entered quantity;
- deterministic canonical unit conversion;
- equipment fact/configuration used;
- derived local interpretation;
- model/interpretation version and provenance.

Do not turn the result into universal resistance.

## 5.3 Starting resistance

Represent known starting/base resistance as a versioned equipment fact with scope, meaning and provenance.

It may be used deterministically only when its algebraic relationship to the entered coordinate is exact.

Otherwise it may be an admissibility/relationship feature or prior input.

Do not add an unconditional `globalInterceptKg`.

---

# 6. Execution-profile boundary

Current `ExecutionProfileVersion` semantics remain authoritative.

Changing equipment does not automatically require a new profile/version.

A profile/version boundary is warranted only when the equipment change materially changes capability-defining semantics already represented by the profile ontology, such as relevant:

- mechanism/path;
- ROM geometry;
- unilateral/bilateral or side semantics;
- resistance-curve regime;
- grip/support constraints;
- another explicit execution semantic.

Do not create a universal rule such as `Smith = new profile` or a fixed stability/DOF threshold.

A 20 kg bar changing to a 15 kg bar is normally an equipment/load interpretation change, not automatically a new RDL execution profile.

A free-barbell RDL versus Smith RDL requires source-led semantic review; the contract does not predetermine the answer.

---

# 7. 7F-C — Typed capability-transfer boundary

7F should consume existing capability posterior/predictive information rather than rebuild capability from raw working sets in the first production candidate.

Create the minimum typed boundary that can communicate, as applicable:

```text
source profile/version
source equipment context / interpretation version
capability family
side/laterality
as-of timestamp
causal evidence cutoff
posterior/predictive representation
supported observation/query domain
material dependence/covariance information
upstream model/config/solver identity
provenance/support status
```

The contract is conceptual. Do not automatically serialise a giant generic tensor.

Do not reduce an upstream posterior to only mean/variance unless fidelity tests show that representation is sufficient for the candidate and query family.

Raw-history one-stage transfer may exist only as a clearly separate offline challenger with its own model identity.

Different capability families must fail closed unless an explicit compatible translation contract exists.

---

# 8. Semantic relationship/admissibility

Translation requires an explicit versioned source→destination relationship description.

Do not author numeric transfer merely because:

- display names are similar;
- both exercises target the same muscle;
- recruitment cosine similarity is high;
- both equipment items belong to a broad family.

Those may be relationship features, not proof of exchangeability.

The relationship description may use existing execution features, equipment features and capability-family compatibility.

Directionality matters. `A→B` does not automatically equal `B→A`.

No transitive `A→B→C` inference in v1 unless a separately preregistered model earns it.

---

# 9. 7F-D — Candidate models

## N0 — destination-only / no-transfer champion

Mandatory for every evaluable destination.

N0 receives every legitimate destination-side input except source-profile performance/capability evidence.

It may therefore use:

- destination execution/equipment semantics;
- direct destination history;
- destination temporal capability;
- normal upstream priors already available without transfer.

This is the baseline that answers whether transfer helped.

## N1 — equipment-blind same-profile diagnostic

Where repeated same-profile/multi-equipment history exists, optionally compare equipment-aware candidates with an equipment-blind pooled diagnostic.

N1 is not product authority.

## M0 — first transfer challenger

Implement one simple directed robust relationship family before any central graph/hypermodel.

Preregister its exact mathematics before fitting real history.

The implementation spec must define:

- the exact source capability quantity/representation;
- the destination prediction/query family;
- source uncertainty propagation;
- relationship parameters and priors;
- residual/noise family;
- semantic admissibility;
- what no-transfer means inside/alongside the candidate;
- destination evidence combination;
- extrapolation/domain behaviour;
- numerical representation/solver;
- immutable config identity.

The research's affine form is only an example. Do not copy it without reconciling it with 7B/7C capability semantics.

Use robust residual behaviour and strong shrinkage unless preregistered evidence justifies otherwise.

## Multi-source v1 rule

Do not naively precision-combine several source profiles as independent evidence.

Until dependence-aware combination is specified, use one of these preregistered safe forms:

1. score source relationships independently; or
2. select one admissible source using a deterministic policy frozen before outcome observation.

Any later multi-source combination requires explicit dependence handling and its own model/config identity.

---

# 10. Same profile, different equipment experiment

The major open architecture question remains M1 vs M2.

## N0-E — equipment-local no transfer

Separate direct equipment-local predictions. Safety champion for the experiment.

## N1-E — equipment-blind pooling

Ignore equipment identity. Diagnostic only.

## M1 — shared profile capability + equipment observation mappings

One profile capability state, separate equipment observation mappings.

Potential advantage: compact state and efficient shared temporal progression.

Risk: assumes one coherent latent capability is sufficient across the equipment contexts.

## M2 — equipment-local capability facets + directed relation

Separate profile-by-equipment capability facets joined by directed translation.

Potential advantage: strongest preservation of local coordinates.

Risk: sparse evidence and more state.

## Decision rule

Do not select M1 or M2 from synthetic fit quality alone.

Use genuine repeated-equipment history when available.

If current installed history lacks canonical repeated A/B equipment bindings, record:

```text
NOT_EVALUATED_REAL_HISTORY
```

and keep the architecture question open for prospective evidence.

Structural closure does not require inventing a winner.

---

# 11. Direct destination evidence

Direct destination evidence is more directly relevant than transferred evidence, but do not use a hard observation-count switch.

Its influence should increase with information content, including:

- predictive precision;
- observation noise;
- temporal freshness;
- supported domain overlap;
- independent-session support;
- semantic certainty.

No rule such as:

```text
1 observation → transfer off
3 observations → transfer off
```

is authorised.

A source may remain useful after destination history exists if it improves future prediction under the frozen candidate. That must be demonstrated, not assumed.

---

# 12. Hierarchical borrowing and future central models

M0 must work or fail on its own before adding a central graph/hypermodel.

A later hierarchy may pool **relationship behaviour**, not absolute capability.

Relatedness/exchangeability is a model assumption. Partial pooling can cause negative transfer.

Any hierarchy must retain:

- semantic gating;
- local relationship diagnostics;
- explicit no-transfer behaviour;
- relationship-family stratified evaluation;
- failure isolation.

A future central feature-conditioned hypermodel may learn relationship priors over a sparse directed graph only if simple pair relationships demonstrate sufficient density/data to justify it.

The centre must never become global strength.

Cross-user priors/meta-learning are deferred until defensible population data exist.

---

# 13. Temporal chronology

Reuse existing N-BIO temporal capability state.

Do not add a second equipment-time latent state unless residual diagnostics and held-out evaluation demonstrate a need.

Same-session A/B evidence is not independent calibration truth. It reduces slow time confounding but may introduce order, fatigue, potentiation and carryover.

For all historical evaluation at event time `t`, source and destination state must use only evidence available before `t`.

Retrospective smoothed states containing later observations are not valid prequential inputs for that prediction.

---

# 14. Persistence and replay

Canonical equipment/history and derived translation state must have different ownership.

## Canonical

- equipment identity;
- equipment model/specification/configuration facts accepted as historical truth;
- version/provenance;
- actual historical binding;
- load-value semantics where known;
- preference history if persisted as canonical user preference.

Corrections append/supersede or otherwise preserve auditable history. Do not mutate old observations to today's interpretation.

## Derived

- deterministic local interpretation caches if not canonical facts;
- transfer relationship state;
- transfer/no-transfer gate state;
- relationship diagnostics;
- candidate destination predictions;
- equipment-local capability facets if an accepted challenger uses them.

Derived state must be deletable and rebuildable from authorised canonical evidence plus declared upstream versions.

Unknown codec/model/schema versions fail closed.

Dependency-scoped invalidation is required. An equipment correction must invalidate consumers of that binding/interpretation without deleting unrelated raw evidence.

Backup/restore must preserve all new canonical data and any derived state that the generic Native backup contract includes.

---

# 15. Synthetic validation

Build a deterministic synthetic suite before installed-history acceptance.

Cover at least these behaviours:

1. raw entered load remains unchanged after local interpretation;
2. known 20 kg bar + added plates resolves locally when semantics are explicit;
3. unknown complete-vs-added convention remains unknown;
4. `PER_HAND` and `PER_SIDE` historical semantics remain unchanged;
5. selector-labelled kg does not become ordinal;
6. ordinal level does not become kg;
7. unknown pulley ratio does not default to 1:1/2:1;
8. known device-local mechanical ratio can affect local interpretation without universal transfer;
9. starting resistance scope/version is respected;
10. free-weight equipment works without machine-only metadata;
11. stable anonymous equipment accumulates history;
12. later OEM enrichment does not rewrite old binding identity;
13. preference change does not rewrite historical actual equipment;
14. session-only equipment override does not change preferred equipment;
15. equipment correction invalidates dependent derived state only;
16. source→destination relationship is directional;
17. unsupported capability-family bridge fails closed;
18. no-transfer prediction remains available when transfer is unsupported;
19. source posterior uncertainty propagates;
20. destination evidence changes the destination posterior without hard count thresholds;
21. extrapolation outside supported source/destination domain widens/fails according to config;
22. correlated sources are not silently treated as independent;
23. future observation cannot affect an earlier frozen prediction;
24. delete-derived + replay reproduces deterministic outputs within tolerance;
25. backup/restore preserves canonical equipment semantics/bindings;
26. old Room15 data migrate without guessed equipment/load semantics;
27. raw evidence and existing 7B/7C/7D/7E fingerprints remain unchanged;
28. `BENCHMARK_V0` authority remains unchanged.

Add more cases when source audit reveals additional invariants.

---

# 16. Real-history development evaluation

Use installed Native history only after candidate/config preregistration.

Report separately:

- relationship/source/destination eligibility;
- independent-session counts;
- missing/unknown equipment semantics;
- capability-family coverage;
- N0 availability;
- M0 availability;
- any N1/M1/M2 evaluability;
- correction exclusions;
- extrapolation domains;
- runtime/memory.

Do not fabricate equipment identity for legacy history merely to increase sample size.

Historical data already inspected while developing M0 is development evidence. Do not repeatedly modify M0 against that same stream and present the final result as independent acceptance.

---

# 17. Prequential metrics and negative transfer

For continuous predictive outputs evaluate where supported:

- CRPS;
- log predictive score;
- WIS / interval score;
- coverage and sharpness;
- PIT/reliability;
- p05/p50/p95;
- signed bias;
- MAE as secondary;
- prediction availability;
- numerical failure;
- runtime/RAM.

Scaled CRPS may be a secondary cross-scale diagnostic. Never average unlike raw-unit scores and interpret the result as one physical quantity.

For ordinal outputs use ordinal-aware proper scoring and calibration diagnostics.

For lower-is-better score `S` report:

```text
ΔS = S_transfer - S_N0
```

including:

- mean/cumulative ΔS;
- median ΔS;
- fraction of predictions made worse;
- severe tail losses;
- catastrophic overconfidence;
- per relationship/profile/equipment-family strata.

A favourable global mean cannot hide a repeatedly harmful relationship family.

---

# 18. Acceptance and authority

7F may reach structural closure while empirical questions remain open.

Valid closure states include:

```text
STRUCTURALLY_CLOSED
NO_USEFUL_TRANSFER
EMPIRICAL_ACCURACY_PENDING
NOT_EVALUATED_REAL_HISTORY
INCONCLUSIVE
```

as applicable to individual candidates/relationship families.

Do not relabel synthetic recovery as human calibration.

Do not promote an M0/M1/M2 candidate merely because it exists.

If longitudinal/repeated-equipment evidence is insufficient, record the empirical debt in `POSTPONED_DEVELOPMENT.md` during closure rather than forcing a model decision.

Normal product behaviour remains unchanged throughout 7F.

---

# 19. Documentation and physical closure

Maintain an intentionally rough:

```text
NBIO_7F_EQUIPMENT_TRANSLATION_JOURNAL.md
```

through implementation/review windows.

Before physical closure create source-verified checkpoint/closure documentation recording:

- exact source head;
- migration/schema status;
- candidate/config identities;
- synthetic results;
- real-history evaluation status;
- negative-transfer results;
- replay/delete-derived status;
- backup/restore and FK status;
- raw-evidence and product-authority fingerprints;
- device runtime/RAM;
- unresolved empirical debts;
- exact-head CI.

The full personal acceptance export remains outside the public repository if it contains private history.

---

# 20. Scope exclusions

N-BIO-7F does **not** include:

- camera/OCR/vision equipment recognition;
- final equipment setup UX;
- user-facing uncertainty copy;
- N-BIO-8 load/rep prescription;
- in-workout adaptation;
- programme editing/regeneration;
- conditioning capability (7G);
- universal equipment catalogue service;
- cloud population learning;
- automatic exercise-profile rewriting from equipment selection.

Backend hooks may support later product work, but 7F must not silently decide that UX.
