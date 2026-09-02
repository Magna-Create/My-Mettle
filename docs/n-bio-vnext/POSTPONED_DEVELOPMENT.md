# My Mettle Native — Postponed Development Register

> **Purpose:** Track deliberately deferred validation/refinement work that is not currently blocking forward N-BIO architecture work, while preventing later phases from accidentally treating deferred behaviour as empirically validated.
>
> An entry in this file is **not forgotten work** and is **not a pass**. It is an explicit state: the structural/model framework may proceed, but the listed empirical claims remain unavailable until the entry is revisited and closed.

---

# PD-001 — N-BIO-7C empirical capability accuracy validation

## Status

**OPEN — POSTPONED UNTIL SUFFICIENT REAL / EXTERNAL LONGITUDINAL EVIDENCE EXISTS**

Applies to the N-BIO-7C capability families:

- `LOADED_HOLD`;
- `DURATION_ONLY`;
- `REPEATED_CONTRACTION`.

This postponement concerns **empirical data accuracy/calibration**, not structural correctness.

## Why this is postponed

Current personal history is expected to be too sparse for a meaningful real-history validation of these families. Historical dead-hang / hold evidence is limited, repeated-contraction evidence may be limited or absent, and conditioning/cardio history is not relevant to 7C and will be handled separately in its later capability phase.

Synthetic latent-truth fixtures can establish whether the implementation behaves according to its declared mathematics, but they cannot prove that the chosen model/priors accurately describe real human performance for these capability families.

Rather than tune a model against tiny or semantically weak history and create false confidence, N-BIO will complete the 7C framework, pre-validate it for critical errors, and explicitly defer empirical refinement.

## What MUST be completed before 7C may close

7C must still validate all architecture/safety properties that can be tested without a large real dataset:

- exact capability-family boundaries;
- immutable/versioned model/config identities;
- correct load/duration/cycle/unit semantics;
- laterality isolation;
- execution-profile version and semantic-regime isolation;
- no hidden universal workload scalar;
- no fabricated RIR/RPE/max-effort labels;
- successful observations remain lower-bound evidence;
- capability remains separate from action/observation policy;
- dynamic statistical-state transitions satisfy declared invariants;
- synthetic latent-truth recovery across stable/upward/downward/sparse cases;
- adversarial/outlier and semantic-boundary fixtures;
- extrapolation widens uncertainty;
- Adaptive Sparse numerical stability and representative fidelity against Dense;
- Dense remains the high-fidelity reference/oracle;
- persistence/reload correctness;
- delete-derived/full-replay correctness;
- raw evidence remains unchanged;
- prescription/product authority remains unchanged;
- Native full backup/restore remains safe;
- Room schema integrity remains valid;
- typed/fail-closed behaviour for unsupported or non-identifiable cases.

This is the **pre-validation gate**. Critical mathematical, persistence, semantic, replay or numerical failures are NOT deferrable.

## What is explicitly deferred

The following do **not** need to be claimed as complete during the initial 7C pass if sufficient real evidence does not exist:

- real-history predictive calibration for loaded holds;
- real-history predictive calibration for duration-only performance;
- real-history predictive calibration for repeated contractions;
- empirical confirmation/refinement of family-specific slope priors;
- empirical confirmation/refinement of process-volatility priors;
- empirical confirmation/refinement of observation-noise/slack assumptions;
- reliable profile-level calibration metrics from tiny histories;
- empirical accuracy of far-domain load-duration or load-cycle extrapolation;
- evidence-backed action-policy modelling for these families;
- product-authority promotion of 7C capability outputs.

Where real evidence is insufficient, acceptance must report:

`EMPIRICAL_ACCURACY_PENDING`

or the closest existing typed status such as:

`NOT_EVALUATED_REAL_HISTORY`

rather than `PASS`.

## Downstream quarantine / no-knock-on rule

This postponement is allowed only because 7C remains non-authoritative and later phases can consume its **interfaces/contracts** without treating its family-specific numerical outputs as validated truth.

Until PD-001 is closed:

1. `BENCHMARK_V0` / existing normal-product authority remains unchanged.
2. 7C outputs must not become normal-user prescriptions or authoritative prefill values.
3. N-BIO-7D and later phases may implement family-generic plumbing against the 7C contracts.
4. N-BIO-7D must **not** claim empirical validation of SetDemand / EffectiveDose for a 7C family merely because the 7C model returns a finite posterior.
5. Scientific acceptance of downstream dose/fatigue/recovery behaviour for these families must remain synthetic/structural or explicitly `EMPIRICAL_ACCURACY_PENDING` until this entry is closed.
6. Dynamic-resistance evidence may continue to carry the real-history empirical burden for later generic inference architecture where appropriate.
7. No later phase may tune itself around apparent 7C numeric accuracy from sparse personal history and then use that as evidence that 7C was correct.
8. Raw evidence and model versions must remain replayable so later 7C refinements can recompute all derived states without destructive migration.

If a later phase discovers that its architecture fundamentally depends on empirically accurate 7C numerical outputs rather than only the contract/interface, that dependency re-opens PD-001 as a blocker before that behaviour may be declared validated.

## Data collection plan

### Personal longitudinal evidence

Create/retain one or more suitable 7C exercises in normal training when practical and record them consistently over time.

Useful evidence should preserve:

- exact exercise / execution-profile version;
- exact equipment/setup semantics;
- side/laterality;
- load/resistance where applicable;
- duration;
- cycles/repetitions where applicable;
- cadence/duration where genuinely observed for repeated contractions;
- completed/performed status;
- timestamps/session identity;
- corrections/supersession;
- body-mass context only where semantically relevant.

The priority is **multiple independent sessions with stable semantics**, not generating lots of same-session sets purely to inflate sample count.

Where reasonable training naturally creates variation across load/duration/cycle regions, that is more informative than repeatedly observing one narrow point. Do not alter training recklessly just to excite the model.

### External evidence

Search for suitable public or permissioned longitudinal datasets after the main N-BIO implementation is substantially complete.

External data is useful only if its semantics are strong enough to map honestly into the N-BIO evidence contract. Prefer datasets containing dates/session ordering plus exact load/duration/repetition/setup information.

Do not treat anonymous anecdotes, isolated records, leaderboards or semantically ambiguous spreadsheets as quantitative validation truth. They may be useful only as qualitative/edge-case material.

## Revisit trigger

Re-open this entry when one or more of the following is true:

- enough new personal 7C history exists to support meaningful chronological evaluation;
- a suitable external longitudinal dataset has been found and semantically audited;
- N-BIO core implementation is complete and the project enters final model-refinement / pre-cutover validation;
- a downstream phase genuinely requires empirically accurate 7C outputs to validate its own behaviour.

The intended default is to revisit **after the main N-BIO implementation is complete but before Native cutover / any product-authority promotion that depends on these families**.

## Required deferred-validation pass

When PD-001 is reopened:

1. freeze the then-current 7C model/config before inspecting new confirmatory outcomes;
2. audit personal/external data semantics and split invalid regime continuity before inference;
3. run whole-session prequential / chronological validation where applicable;
4. distinguish capability validation from action-policy prediction;
5. compare Adaptive Sparse against Dense on representative real histories;
6. inspect calibration, coverage, tail behaviour, signed bias and failure rates using metrics whose semantics are valid for the model output;
7. test whether current priors/transition/noise assumptions need a new immutable candidate rather than mutating the old one;
8. rerun downstream derived-state validation for any later phase materially affected by a changed 7C model;
9. preserve the old candidate/results as historical evidence;
10. close this entry only when the empirical status is honestly resolved.

## Closure condition

PD-001 closes when the project has sufficient semantically valid longitudinal evidence to make an evidence-backed decision for each applicable 7C family:

- retain current model;
- replace with a new immutable candidate;
- narrow/widen priors;
- retain as explicitly uncertain/unsupported;
- or keep a family non-authoritative because evidence remains insufficient.

There is no requirement that every family produce a confident model. A scientifically honest `unsupported / broad / null` result is acceptable.

---

# PD-002 — N-BIO-7D empirical SetDemand / EffectiveDose calibration

## Status

**OPEN — STRUCTURAL CANDIDATE MAY PROCEED; EMPIRICAL NUMERIC CALIBRATION IS NOT ESTABLISHED**

This entry applies to the N-BIO-7D chain:

`contemporaneous capability posterior -> frontier-gap SetDemand -> high-demand-band projection -> muscle EffectiveDose -> SessionDose`.

It is separate from PD-001. PD-001 concerns empirical capability accuracy for the 7C non-dynamic families. PD-002 concerns whether 7D's latent demand/dose mapping itself corresponds quantitatively to human task demand and biologically meaningful training dose.

## Why this is postponed

Ordinary workout history directly observes performed task outcomes such as resistance, repetitions, duration and cycles. It does not directly observe:

- the true latent distance from the user's task-specific capability frontier on that set;
- local muscle-specific failure proximity;
- motor-unit recruitment percentage;
- fibre-level stimulus;
- the hypertrophic contribution caused by one set;
- the correct numerical threshold separating a candidate high-demand frontier band;
- the exact within-session diminishing-return curve or its `tau`.

Synthetic latent-truth fixtures can prove that the implementation recovers and propagates quantities according to its declared mathematics. They cannot establish that `delta`, the high-demand-band EffectiveDose mapping, or `tau` is biologically calibrated.

Retrospectively making values “look right” against the developer's expectations would not solve this identifiability problem and would create circular evidence. N-BIO therefore pre-registers the first 7D candidate before inspecting 7D history outputs and keeps its empirical calibration explicitly pending.

## What is structurally validated in 7D

PD-002 does **not** waive any structural requirement. N-BIO-7D must still prove, before structural closure:

- SetDemand is conditional frontier distance, not a user action-policy probability;
- historical sets use contemporaneous/pre-session capability only;
- same-session sets share one persistent pre-session capability reference;
- no future evidence enters causal replay;
- no RIR/RPE/failure label is fabricated from normal set completion;
- frontier-gap uncertainty and contradiction diagnostics are preserved;
- `delta` is immutable/versioned and family-scoped;
- Exposure uses exact immutable historical recruitment semantics and is independently recomputable;
- recruitment weights are independent/non-conserved and never normalised;
- EffectiveDose remains separate from Exposure and propagates the demand posterior;
- unresolved demand remains unresolved rather than becoming zero, 0.5, or Exposure;
- task demand is not presented as local muscle failure/stimulus truth;
- same-session shared capability uncertainty is propagated jointly rather than as independent marginal variances;
- raw SessionDose and the separately named concave transform are reproducible;
- `tau` is immutable/versioned and not labelled MRV/failure capacity;
- unresolved session inputs remain typed;
- Adaptive Sparse downstream behaviour is checked against Dense;
- persistence/reload, invalidation and delete-derived/replay are deterministic where claimed;
- raw evidence, prescriptions and BENCHMARK_V0 product authority are unchanged;
- Room/backup integrity remains valid;
- no across-session fatigue/recovery/development biology is introduced.

A failure in those properties is a 7D blocker and cannot be deferred under PD-002.

## What remains empirically unvalidated

Until PD-002 closes, N-BIO must not claim empirical human truth for:

- the absolute numerical calibration of frontier gap as perceived or physiological set demand;
- the v1 `delta` values;
- equivalence of equal `delta` values across capability families;
- the binary high-demand-band candidate used inside v1 EffectiveDose;
- the magnitude of EffectiveDose as actual local muscle stimulus;
- the quantitative relationship between recruitment weights and biological dose;
- the v1 `tau` value;
- the exact logarithmic concave transform as the true human within-session dose-response curve;
- any marginal “set N is X% less effective” interpretation;
- product-authority use of 7D dose outputs.

The accepted status is therefore:

`EMPIRICAL_CALIBRATION_PENDING`

not `PASS`, even when structural tests and numerical fixtures pass.

## Downstream quarantine

Until PD-002 closes:

1. all 7D SetDemand, EffectiveDose and SessionDose outputs remain derived SHADOW/CANDIDATE state;
2. `BENCHMARK_V0` remains normal-product authority;
3. normal workout prescription, prefill, set count, exercise selection, routine generation and progression logic must not consume 7D values as validated truth;
4. N-BIO-7E may later consume the 7D **interface and candidate outputs** for structural/model-development work only;
5. 7E must not claim its fatigue/recovery/development model is validated because 7D EffectiveDose is assumed correct;
6. later predictive validation may evaluate the 7D dose model and an acute-state model jointly, but it must state that joint identifiability limitation;
7. if a replacement 7D model materially changes dose, all dependent derived state must be replayable from raw evidence and historical semantics;
8. 7C-family downstream values additionally remain subject to PD-001.

## Evidence that can revisit PD-002

No single future evidence source is guaranteed to identify every latent 7D quantity. Useful evidence may include a combination of:

- semantically strong longitudinal training histories with pre-registered later predictive outcomes;
- controlled or externally published datasets with known set-to-failure/proximity protocols where those labels are actually observed rather than reconstructed;
- repeated-session designs that vary set number and proximity while retaining interpretable exercise/recruitment semantics;
- suitably designed studies linking resistance-training dose manipulations to later performance, fatigue/recovery or hypertrophy outcomes;
- later My Mettle data where acute-state and longer-term predictive models can be evaluated prospectively without tuning against the same outcomes;
- external evidence capable of discriminating plausible `delta`, demand-to-dose and within-session saturation candidates.

Subjective free-text notes, generic vibe/form/comfort scores, anonymous anecdotes and visual plausibility are not sufficient calibration targets.

## Revisit protocol

When sufficient evidence exists:

1. freeze the then-current 7D candidate/config before examining confirmatory outcomes;
2. preserve the v1 results as historical candidate evidence;
3. define the observable validation target separately from latent demand/dose semantics;
4. evaluate calibration/sensitivity prospectively or on held-out evidence where possible;
5. test alternative immutable `delta`, demand-to-dose and session-transform candidates rather than mutating the historical model;
6. retain Dense as the high-fidelity numerical reference for capability-driven comparisons where applicable;
7. evaluate whether apparent downstream predictive gains identify the dose model or only compensate through another model layer;
8. rerun all materially dependent derived state after any accepted model replacement;
9. close PD-002 only for claims actually supported by the evidence.

## Closure condition

PD-002 closes only when there is sufficient semantically valid evidence to make an evidence-backed decision about the numerical 7D mapping. Valid outcomes include:

- retain the candidate with defensible calibration bounds;
- replace one or more family-specific `delta` values;
- replace the demand-to-dose transform;
- replace/refine the session concavity model or `tau`;
- keep part of the chain deliberately broad/non-authoritative because it remains unidentifiable.

Closure does not require pretending every latent quantity is directly measurable. It requires that any promoted numeric interpretation has a defensible empirical chain and that unresolved parts remain explicitly uncertain.

---

# Future entries

Use this file for later deliberately postponed work where forward development is safe only under explicit quarantine. Every entry should state:

- what is postponed;
- why;
- what still must be validated now;
- downstream behaviours that may not rely on the deferred claim;
- what evidence will trigger a revisit;
- exact closure criteria.
