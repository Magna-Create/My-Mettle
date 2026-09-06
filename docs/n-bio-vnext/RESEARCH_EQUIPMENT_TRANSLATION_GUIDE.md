# N-BIO-7F — Equipment Context & Cross-Profile Translation Research Guide

> **Purpose:** implementation-facing evaluation and navigation guide for the N-BIO-7F equipment-context and cross-profile-transfer research pass.
>
> **Authority:** [`PLAN.md`](./PLAN.md) remains the forward implementation contract. [`NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md`](./NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md) preregisters the 7F implementation boundary. This guide explains what the research supports, what N-BIO adopts, what remains experimental, and what must not be implemented. The full research report is preserved in [`RESEARCH_EQUIPMENT_TRANSLATION_RAW.md`](./RESEARCH_EQUIPMENT_TRANSLATION_RAW.md).
>
> **Raw-report integrity:** original upload `N-BIO-7F_Final_Research_Report.md`, SHA-256 `7b5b4e194a860ba4f3c5479af35e072a972bad6ceb0bc325b9920817c9e4ea7e`, 69,382 bytes, 2,049 rendered lines.

## 1. How to use this guide

For N-BIO-7F implementation work:

```text
PLAN.md
   ↓
NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md
   ↓
this guide: relevant research question
   ↓
only the required part of the raw report
   ↓
current source code
```

Keep these categories separate:

- **ESTABLISHED PHYSICAL FACT** — local mechanics or measurement semantics supported directly.
- **SUPPORTED EMPIRICAL FINDING** — observed equipment/performance relationship in published data.
- **SUPPORTED STATISTICAL PRINCIPLE** — modelling/evaluation principle supported by methodological literature.
- **N-BIO DESIGN SYNTHESIS** — My Mettle architecture chosen by reconciling evidence with current source.
- **OPEN EMPIRICAL QUESTION** — must be tested rather than decided by taste.
- **REJECTED** — do not implement in 7F.

The research is evidence for design. It is not a substitute for the repository contract.

---

# 2. Executive evaluation

The research supports one central 7F model:

```text
canonical local evidence
        ↓
exact local interpretation where genuinely known
        ↓
profile-local probabilistic capability
        ↓
directed, uncertainty-aware relationship inference
        ↓
destination predictive distribution
```

with:

```text
NO USEFUL TRANSFER
```

remaining a first-class result.

The research does **not** support a universal equipment load, universal biological kilogram, global user-strength scalar, fixed machine conversion table, or one deterministic physics equation that normalises all equipment.

`L_Tensor` is useful only as conceptual shorthand for the wider N-BIO structure: heterogeneous local probabilistic states connected by typed uncertain relationships. It is not a required Kotlin tensor, a single random variable, or a common physical unit.

The implementation target is therefore not an equipment converter. It is an equipment-aware relationship-learning layer over already-local N-BIO capability.

---

# 3. Decision ledger

## REQUIRED NOW

- preserve immutable entered/canonical performance evidence;
- preserve existing `EntryBasis` meanings: `TOTAL`, `PER_HAND`, `PER_SIDE`;
- preserve existing `ResistanceSemantics` and heterogeneous metric families;
- add the ability to distinguish complete/inclusive load from added-only load where that distinction changes historical meaning;
- allow that new load-value semantic to remain unknown for legacy evidence;
- represent equipment as a general concept covering free weights and machines;
- preserve stable anonymous equipment identity where useful;
- preserve optional manufacturer/model identity without requiring it;
- make actual historical equipment context resolvable for equipment-sensitive observations;
- keep user preference/default equipment separate from immutable execution-profile semantics;
- version behaviour-relevant equipment interpretation facts and their provenance;
- keep canonical equipment history in Core/domain data, not ContextModule private memory;
- keep learned translation as derived, versioned, replayable state;
- consume existing profile-local capability posterior/predictive information as the first production transfer substrate;
- include a destination-only/no-transfer champion;
- learn directed relationships chronologically;
- evaluate negative transfer explicitly.

## OPTIONAL NOW WHEN GENUINELY KNOWN

- manufacturer/model;
- declared implement or starting resistance;
- documented pulley/mechanical ratio;
- rail/glide angle;
- independent-arm structure;
- loading mechanism;
- feasible local increments/selections;
- other local equipment facts with clear meaning and provenance.

Optional does not mean guessed. Unknown stays unknown.

## OPEN CHALLENGERS

- shared same-profile capability with equipment-conditioned observation mappings (M1);
- equipment-local capability facets joined by directed translation (M2);
- exact transfer-message representation;
- exact simple relationship parameterisation;
- correlated multi-source combination;
- equipment-edge temporal dynamics;
- ordinal cross-device transfer;
- central relationship hypermodels / sparse graphs;
- cross-user relationship priors or meta-learning.

## DO NOT BUILD YET

- full machine digital twins;
- exhaustive lever geometry;
- mandatory cam/resistance curves;
- formal user calibration workflow;
- measured endpoint calibration as a normal-user requirement;
- cross-user meta-learning without defensible data;
- central graph complexity before simple relationships earn it.

## REJECTED

- universal `L_true`;
- global human-strength scalar;
- raw-kilogram transfer between unrelated contexts;
- fixed Smith/free-weight multiplier;
- generic cable ratio assumptions;
- `2:1` pulley mechanics as `0.5 × personal capability`;
- free weights modelled as trivial machines with ratio 1;
- guessed friction or `friction = 1` defaults;
- fixed 1–5 stability/DOF score as a universal numeric feature;
- universal cam-curve scalar;
- hard one/three/N-observation transfer gates;
- one destination observation instantly deleting transferred information;
- hierarchical pooling treated as automatically safe;
- arbitrary uncertainty penalties for missing OEM metadata;
- mandatory OEM identity;
- selector-labelled kilograms treated as the same semantic type as ordinal `MACHINE_LEVEL`;
- generic e1RM/load-velocity formulas replacing existing N-BIO capability inference.

---

# 4. Physical equivalence is not predictive transfer

The strongest scientific correction is:

> **Physical non-equivalence does not imply predictive uselessness, and predictive usefulness does not imply physical equivalence.**

Smith/free-weight studies show that two contexts can have a strong performance relationship while still having systematic and task-dependent differences. Machine-mechanics studies show that cams, lever geometry and loading architecture can materially alter local mechanics. Neither result produces a universal conversion rule.

Therefore 7F asks:

> Given source capability, destination semantics, equipment context, time and uncertainty, does source evidence improve the destination predictive distribution?

It does not ask:

> What universal physical scalar makes the two pieces of equipment equal?

Selected evidence:

- Cotterman et al. 2005 — Smith vs free-weight bench/squat: <https://doi.org/10.1519/14433.1>
- Saeterbakken et al. 2011 — dumbbell/barbell/Smith chest press: <https://doi.org/10.1080/02640414.2010.543916>
- Haugen et al. 2023 — free-weight vs machine systematic review/meta-analysis: <https://doi.org/10.1186/s13102-023-00713-4>
- Folland & Morris 2008 — variable-cam torque profiles: <https://doi.org/10.1080/02640410701370663>
- Dalleau et al. 2010 — variable moment arm and knee-extensor performance: <https://doi.org/10.1080/02640411003631976>

---

# 5. Equipment means more than machine

Free weights are first-class equipment.

7F must naturally support contexts such as:

```text
barbell
dumbbell
kettlebell
plates / loaded implement
Smith machine
cable / pulley system
selectorised machine
plate-loaded lever
sled / rail system
assisted resistance equipment
```

Known free-weight mass can support exact local accounting. For example, `60 kg` added plates plus a known `20 kg` bar can produce `80 kg` configured mass if the historical entry convention is known.

That local arithmetic does not establish equivalence with an `80 kg` machine coordinate.

The ontology must not force every free-weight object through machine-specific fields merely because those fields are nullable.

---

# 6. Load-entry and ordinal semantics

Current `EntryBasis` remains an aggregation axis:

```text
TOTAL
PER_HAND
PER_SIDE
```

It must not be redefined.

7F additionally needs a separate historical semantic for whether a recorded external-load value is complete/inclusive or added-only where that distinction matters. Exact enum and storage names remain a source-audit decision.

Example:

```text
20 kg bar + 60 kg plates
```

may be recorded as:

```text
80 kg  = complete/inclusive load
60 kg  = added load only
```

Both can currently appear as `EntryBasis.TOTAL + ResistanceSemantics.EXTERNAL`. 7F must be able to preserve the distinction without rewriting legacy rows whose convention is unknown.

Unit identity remains separate from entry semantics. UCUM is useful support for this general separation: annotations do not change the underlying unit identity. See <https://ucum.org/ucum>.

## Selector-labelled mass versus ordinal level

Keep these separate:

```text
40 kg printed on a selector stack
```

and:

```text
Level 7
```

The first is a manufacturer/device-local mass-labelled coordinate. Its mechanical meaning may still be equipment-specific.

The second is genuinely ordinal. It must not silently become kg. The VIM ordinal-quantity definition is the relevant metrology reference: <https://www.bipm.org/en/committees/jc/jcgm/publications>.

---

# 7. Equipment identity and historical binding

Stable identity is useful before OEM identity is known.

A valid context may initially be only:

```text
instance = E17
label = "Chest press by the window"
manufacturer = unknown
model = unknown
```

Direct personal history can accumulate on E17. Later manufacturer/model identification should enrich the same historical identity rather than replacing the observations that already used it.

Two physical instances of the same model may remain distinct. The research does not establish that every instance requires its own statistical random effect.

## Historical invariant

The required invariant is:

> Every equipment-sensitive observation can resolve which equipment interpretation generated it, without reconstructing history from today's default or preference.

A practical candidate topology is:

```text
ExecutionProfileVersion
    immutable execution semantics

PreferredEquipmentBinding
    current user/product preference

session-exercise occurrence
    actual equipment binding

PerformanceObservation / set
    inherits actual binding
    with explicit override only when needed
```

This topology is **N-BIO design synthesis**, not an externally established database fact. Source audit decides the exact owner/foreign-key layout.

Preferred/default equipment must not be made part of immutable `ExecutionProfileVersion` merely because the current roadmap once described the relationship there.

---

# 8. Specification, configuration, interpretation and calibration

The older roadmap used the conceptual name `EquipmentCalibrationVersion`. The research shows that one name can easily conflate different epistemic classes:

- OEM-declared specification;
- user-confirmed configuration;
- deterministic local derivation;
- measured instance calibration.

7F must preserve these distinctions in provenance even if the implementation uses one shared versioned structure.

Do not keep the word `calibration` if source audit shows that it would misrepresent ordinary OEM specification as measured calibration.

A starting resistance can be highly valuable for **local semantic interpretation** when applicable. Its incremental cross-profile predictive value remains unproven.

Do not force starting resistance into one global additive intercept. Use deterministic arithmetic only when its relation to the entered coordinate is exact and understood.

## Useful OEM examples

These examples show that ordinary manufacturer documentation can expose useful local facts. They are model-specific declarations, not universal calibration.

- Life Fitness Adjustable Cable Crossover LCM-CC — 92.5 kg stack, 46.25 kg user-effective resistance, 2:1 cable ratio: <https://www.lifefitness.com/en-gb/catalog/strength-training/cable-machines-functional-trainers/adjustable-cable-crossover>
- Hammer Strength Iso-Lateral Row — starting resistance 5.4 kg per workarm: <https://www.lifefitness.com/en-gb/catalog/strength-training/plate-loaded/plate-loaded-iso-lateral-row>
- Hammer Strength Linear Leg Press — starting resistance 53 kg: <https://www.lifefitness.com/en-gb/catalog/strength-training/plate-loaded/plate-loaded-linear-leg-press>
- Precor DPL0601 Angled Leg Press — starting weight 62 kg: <https://www.precor.com/en-GB/products/DPL0601>
- Precor DPL0802 Smith Machine — current product documentation lists an 11° path and approximately 15–16 kg starting bar assembly: <https://www.precor.com/en-GB/products/DPL0802>

The Precor DPL0802 historical/current documentation is also a useful reminder that a stable product code does not guarantee one eternal mechanical specification. Model/specification provenance must therefore be version-aware.

---

# 9. Physics treatment ladder

Use physics according to the strength of the local fact.

| Information | 7F treatment |
|---|---|
| lb↔kg conversion | deterministic unit conversion |
| known implement mass | deterministic local arithmetic |
| complete vs added-load accounting | deterministic once semantics are known |
| exact documented local cable relationship | deterministic local relation when scope is clear |
| starting resistance | deterministic only if algebra/scope are exact; otherwise feature/prior |
| independent arms | structural relationship/execution feature |
| loading mechanism | relationship/gating feature |
| counterbalance | feature unless sufficiently quantified |
| sled/rail angle | partial physical constraint |
| detailed lever geometry | future, if available and useful |
| full cam/resistance curve | future structured data, not scalar collapse |
| friction | unknown unless measured/otherwise defensibly established |
| endpoint force calibration | optional future pathway |

The output of this ladder remains local. It does not create `L_true`.

---

# 10. What 7F should transfer

The preferred production input is **typed existing capability posterior/predictive information**.

A transfer message must retain enough information to preserve the meaning and uncertainty of the upstream capability, conceptually including:

```text
profile/version
equipment context
capability family
as-of timestamp / evidence cutoff
posterior or predictive state
supported load/rep/duration domain
material covariance/dependence if required
model/config/solver versions
provenance
```

Do not silently reduce this contract to `mean + variance` if the upstream posterior requires more structure.

Raw working-set history remains a credible offline challenger, but it is not the first production architecture because it would duplicate same-profile capability, temporal state, robust noise and action-policy work already owned upstream.

The first 7F contract must also state exactly what source/destination capability object a simple relationship consumes. Do not let a generic vector mapping accidentally duplicate the 7B rep slope or temporal model.

---

# 11. Candidate relationship models

## N0 — destination-only / no-transfer champion

Mandatory.

It uses all legitimate destination information except source-profile performance evidence.

A transfer candidate has not succeeded unless it beats this baseline chronologically without unacceptable calibration or tail harm.

## N1 — equipment-blind pooling diagnostic

For same-profile/multiple-equipment histories, N1 can test whether distinguishing equipment improves prediction at all.

It is a diagnostic, not a preferred architecture.

## M0 — directed robust pairwise relationship

The strongest simple challenger family.

Required properties:

- directed source→destination relationship;
- source-posterior uncertainty propagation;
- robust residual;
- shrinkage;
- direct destination evidence retained;
- explicit no-transfer possibility;
- chronological learning;
- exact semantic admissibility.

The research's affine equation is illustrative only. The preregistered implementation must define the actual capability quantity/domain before fitting.

## M1 vs M2 — deliberately open

For one execution profile used on stable equipment A and B:

**M1** uses one shared profile capability with equipment-conditioned observation mappings.

**M2** uses equipment-local capability facets connected by directed translation relationships.

M1 is compact and can share temporal progression. M2 preserves local coordinates more conservatively but creates sparser states.

Neither wins by architecture preference. Compare them only when genuine repeated-equipment history exists.

If current installed history cannot support this comparison, record `NOT_EVALUATED_REAL_HISTORY` rather than choosing a winner synthetically.

---

# 12. Hierarchy, relatedness and multi-source transfer

Partial pooling is not automatically safe.

The statistical principle from exchangeability/gating literature is:

> Relatedness is itself a model assumption.

Useful references:

- Bakker & Heskes 2003 — task clustering/gating: <https://www.jmlr.org/papers/v4/bakker03a.html>
- Kaizer et al. 2018 — multisource exchangeability: <https://doi.org/10.1093/biostatistics/kxx031>
- Neuenschwander et al. 2016 — exchangeable/non-exchangeable robust designs: <https://doi.org/10.1002/pst.1730>

7F therefore needs semantic admissibility before statistical borrowing and explicit comparison with no-transfer.

## Multi-source warning

Several individually useful sources can be strongly correlated. Do not precision-combine them as independent evidence by default.

For the first simple candidate, prefer either:

- evaluating source relationships independently; or
- one source selected by a deterministic preregistered policy;

until a dependence-aware combination model is justified.

Unsupported transitive chaining such as `A→B→C` is not authorised.

---

# 13. Time and evidence dependence

Reuse the existing N-BIO temporal capability state. Do not invent a second 7F capability clock unless held-out residuals show a need.

Same-session A/B evidence reduces slow temporal confounding but adds fatigue, potentiation and order effects. Sets within one session are not independent confirmations.

Direct destination evidence should gain influence according to information content, not raw observation count. One observation need not erase transfer, and three observations are not a universal gate.

Cross-user population information is optional future relationship/noise prior information. It is not required for cold start.

---

# 14. Validation contract

Use prequential chronology:

```text
past evidence only
→ freeze destination predictions for all candidates
→ observe future destination event
→ score frozen predictions
→ update candidates
```

Do not use retrospective smoothed state containing later evidence as an earlier prediction.

For continuous outputs, evaluate where supported:

- CRPS;
- log predictive score;
- WIS / interval score;
- p05/p50/p95 and coverage;
- PIT/reliability;
- signed bias;
- MAE as secondary point diagnostic;
- prediction availability;
- numerical stability;
- runtime/RAM.

Scaled CRPS is a useful secondary diagnostic across heterogeneous numerical scales, not evidence that those scales share one meaning. See Bolin & Wallin 2023: <https://doi.org/10.1214/22-STS864>.

For ordinal outputs use ordinal-aware scores such as Ranked Probability Score/categorical log score and appropriate ordinal calibration diagnostics.

## Negative transfer

For lower-is-better score `S`:

```text
ΔS = S_transfer - S_no-transfer

ΔS < 0  transfer helped
ΔS > 0  transfer hurt
```

Report more than the mean:

- mean/cumulative paired difference;
- median difference;
- fraction of events made worse;
- relationship/equipment-family strata;
- severe tail losses;
- catastrophic overconfidence;
- availability;
- numerical failure;
- runtime/memory.

Do not tune a gate repeatedly on the same stream and then call that stream confirmatory acceptance evidence. Candidate/gate changes require new immutable config identity and fresh or otherwise genuinely held-out evaluation.

Core scoring references:

- Dawid 1984 — prequential approach: <https://doi.org/10.2307/2981683>
- Gneiting & Raftery 2007 — proper scoring rules: <https://doi.org/10.1198/016214506000001437>
- Bracher et al. 2021 — WIS/interval forecasts: <https://doi.org/10.1371/journal.pcbi.1008618>

---

# 15. Empirical gaps

The research leaves these questions genuinely unresolved:

1. M1 shared capability vs M2 equipment-local facets.
2. Incremental predictive value of exact OEM identity after stable direct-instance history matures.
3. Whether starting resistance improves transfer or mainly fixes local load interpretation.
4. Whether an OEM-effective cable coordinate should be explicitly derived or retained as local metadata.
5. Dependence-aware combination of multiple correlated source profiles.
6. Whether relationship parameters need temporal dynamics as familiarity changes.
7. Incremental value of equipment identity beyond existing execution semantics.
8. Whether arbitrary ordinal-device relationships can ever transfer usefully.
9. Practical availability/value of full resistance curves.
10. The relationship density at which a central hypermodel beats simple local relationships.
11. Whether cross-user priors improve cold start without excess negative transfer.
12. Magnitude of same-model/different-instance personal-performance effects.
13. Generic normal-training working-set variability. Formal 1RM reliability is only a rough reference, not an N-BIO noise prior.

These are empirical debts, not missing architecture answers.

---

# 16. Architecture boundary with ContextModules

Canonical equipment identity/history belongs to Core/domain data because it changes the meaning of historical performance evidence.

ContextModules are derived learners. They may consume confirmed equipment semantics or publish auxiliary context effects through their normal capability boundary, but they do not own:

- the canonical equipment instance;
- actual historical equipment binding;
- canonical equipment specification/configuration history.

The 7F translation engine owns its own versioned derived relationship state.

This is now the accepted 7F ownership boundary. Exact package/table names remain source-led.

---

# 17. Raw research routing

The full report is intentionally retained rather than rewritten as source authority.

Use it when exact research wording, evidence caveats, bibliography or rejected alternatives are needed:

- practical biomechanics/equipment effects — raw sections `Practical Magnitude` through `Sleds and Assisted Systems`;
- load semantics / equipment identity — `Load-Entry Semantics` through `Historical Binding Granularity`;
- OEM/mechanics — `OEM Metadata Availability` through `Friction`;
- transfer object / `L_Tensor` — `What Quantity Should Transfer?` and `L_Tensor Analogies`;
- candidate models / negative transfer — `Candidate Relationship Models` through `Partial Pooling and Negative Transfer`;
- M1/M2 and chronology — `Same Profile, Different Equipment` through `Negative-Transfer Evaluation`;
- final decision table/open questions/bibliography — final sections.

Do not promote a raw-report modelling suggestion merely because it appears in the source research.

---

# 18. 7F implementation consequence

The smallest useful implementation should proceed in this order:

```text
canonical equipment + load semantics
        ↓
historical binding / replay
        ↓
typed capability-transfer boundary
        ↓
N0 no-transfer champion
        ↓
M0 simple directed relationship challenger
        ↓
prequential scoring + negative-transfer diagnostics
        ↓
M1/M2 only where evidence exists
        ↓
richer hierarchy/graph only if simpler candidates earn the need
```

A structurally correct 7F may close with an empirical result of `INCONCLUSIVE`, `NO_USEFUL_TRANSFER`, or `NOT_EVALUATED_REAL_HISTORY` for unsupported relationships. Do not invent transfer merely to make the phase appear successful.
