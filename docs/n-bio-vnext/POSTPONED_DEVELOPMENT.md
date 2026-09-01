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

# Future entries

Use this file for later deliberately postponed work where forward development is safe only under explicit quarantine. Every entry should state:

- what is postponed;
- why;
- what still must be validated now;
- downstream behaviours that may not rely on the deferred claim;
- what evidence will trigger a revisit;
- exact closure criteria.
