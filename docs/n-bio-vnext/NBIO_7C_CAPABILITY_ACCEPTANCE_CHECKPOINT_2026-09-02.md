# N-BIO-7C capability physical checkpoint — 2026-09-02

This checkpoint records the physical installed-device acceptance for the consolidated **N-BIO-7C** capability-family mission covering `LOADED_HOLD`, `DURATION_ONLY` and `REPEATED_CONTRACTION`.

It closes the **structural/pre-validation** mission only. It does not claim empirical human calibration, does not promote any 7C numerical output to normal-user authority, and does not start N-BIO-7D.

## Physical report

- format: `my-mettle-n-bio-7c-capability-acceptance`
- format version: `1`
- generated: `2026-09-02T12:58:12.460033Z`
- app: `0.1.0-alpha28-dev` / version code `27`
- device class: Samsung Android SDK 36 physical handset
- Room schema: `14`
- total acceptance runtime: `2792 ms`

The full exported personal acceptance JSON is intentionally **not committed to this public repository**. This checkpoint records only the privacy-bounded engineering/scientific verdict needed for project provenance.

## Verdict

```text
structuralPrevalidation = PASS
empiricalAccuracy       = EMPIRICAL_ACCURACY_PENDING
overall7C               = READY_FOR_7C_STRUCTURAL_CLOSURE_EMPIRICAL_ACCURACY_PENDING
```

Therefore:

**N-BIO-7C STRUCTURAL/PRE-VALIDATION: COMPLETE.**

**PD-001: OPEN.**

**N-BIO-7D: NOT STARTED.**

## Family gate

All three family contracts passed structural pre-validation:

- `LOADED_HOLD`: PASS;
- `DURATION_ONLY`: PASS;
- `REPEATED_CONTRACTION`: PASS.

Installed history was sufficient to exercise a real duration-only profile through the structural persistence/replay path. Loaded-hold and repeated-contraction did not have suitable current real-history evidence, so those family empirical statuses remain `NOT_EVALUATED_REAL_HISTORY`. The duration-only family remains `EMPIRICAL_ACCURACY_PENDING` rather than being mislabelled as calibrated from sparse history.

No missing history was fabricated to make the gate pass.

## Synthetic latent-truth pre-validation

The synthetic protocol `n-bio-7c-synthetic-latent-truth-v1` passed all 15 declared cases: stable, upward, downward, sparse and numerical-stress scenarios for each of the three capability families.

Every exported recovery/invariant check was true. The cases established implementation-level recovery against the declared candidate mathematics, including directionality where identifiable, broad/neutral behaviour under sparse histories, finite stress projections, widening out-of-domain/horizon uncertainty and Sparse-vs-Dense fidelity.

This evidence class remains explicitly:

`SYNTHETIC_LATENT_TRUTH_PREVALIDATION_NOT_EMPIRICAL_HUMAN_ACCURACY`

It is not evidence that the chosen priors, slopes, noise/slack assumptions or process dynamics are physiologically calibrated in humans.

## Adaptive Sparse versus Dense

Dense remains the high-fidelity deterministic reference/oracle and Adaptive Sparse remains the forward inference representation inherited from the completed 7B.X solver decision.

The physical synthetic comparison was strong:

- duration-only Sparse and Dense agreed to floating-point precision in the declared fixtures;
- loaded-hold worst exported query-tail relative error was about `0.000976` (~`0.098%`), with worst positive-trajectory probability absolute error about `0.000061`;
- repeated-contraction was the least exact but still passed the pre-registered fidelity gate: worst query-tail relative error about `0.01457` (~`1.46%`) and worst positive-trajectory probability absolute error about `0.00408`.

The largest observed Sparse approximation discrepancy therefore remains bounded, visible and family-specific rather than being hidden as exact equality.

## Persistence, replay and backup safety

The installed-history 7C path passed all required disposable-derived-state checks:

- persist/reload equivalent;
- delete-derived confirmed;
- deterministic full replay equivalent;
- mathematical-model and solver identities retained through persistence;
- Room14 Native backup/restore candidate rows matched;
- raw-evidence state matched through round-trip;
- prescription state matched through round-trip;
- foreign keys were clean.

The acceptance reported no numerical failure for the installed-history profile.

## Product and evidence integrity

Safety/product invariants remained intact:

- raw evidence unchanged;
- prescription state unchanged;
- `BENCHMARK_V0` authority unchanged;
- Candidate-v2 dynamic-resistance foundation remained the frozen 7B.X decision;
- 7C remained SHADOW/developer-only;
- PD-001 downstream quarantine remained present in the exported report;
- N-BIO-7D was not started.

No 7C finite posterior is therefore authorised as normal workout prescription, prefill or downstream empirical truth.

## Closure interpretation

The 7C architecture has now earned what the amended contract allowed it to earn without inventing evidence: family separation, typed semantics, versioned mathematics, deterministic solver provenance, synthetic recovery, bounded Sparse fidelity, persistence/replay safety, Room14 backup safety and product-authority isolation.

What it has **not** earned is real-human calibration of the three family-specific numerical models. That work remains deliberately postponed under **PD-001** until sufficient semantically valid longitudinal personal or external evidence exists. Downstream phases may consume the 7C interfaces/contracts but must not treat these numerical outputs as empirically validated truth while PD-001 is open.

This checkpoint does not authorise the next roadmap phase automatically.
