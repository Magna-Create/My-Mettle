# N-BIO-7D — Demand & Dose implementation checkpoint

**Date:** 2026-09-03  
**Branch:** `agent/n-bio-vnext-inference`  
**Starting 7D base:** `44471b4008ec3a893d3b0ab330e7f2f479b7b450`  
**Implementation integration head before documentation:** `8811c311db33a62e8966801ba01d4582058aefea`  
**Room schema:** 14  
**Normal product authority:** `BENCHMARK_V0` unchanged  
**N-BIO-7E:** not started

## Status

N-BIO-7D implementation is complete enough for physical installed-device acceptance. It is **not yet a physical acceptance PASS** in this checkpoint: the consolidated action must still be run against the installed Room14 history and its exported JSON reviewed.

Structural success must not be relabelled as empirical calibration. `PD-001` and `PD-002` remain OPEN.

## Implemented 7D contract

The normative contract is [`SET_DEMAND_AND_DOSE_CONTRACT.md`](./SET_DEMAND_AND_DOSE_CONTRACT.md).

The implemented definitions are:

- `SetDemand` = posterior profile-local frontier gap for the performed set, evaluated against a causal **pre-session** capability posterior;
- `Exposure` = the exact immutable historical recruitment weight for that set/profile version, with no cross-muscle normalisation or conservation rule;
- candidate `EffectiveDose` = nodewise `Exposure × I(frontierGap <= delta_family)` over the full SetDemand posterior;
- raw `SessionDose` = posterior sum of resolved EffectiveDose contributions;
- concave candidate session transform = `tau * ln(1 + rawDose / tau)` applied nodewise;
- unresolved SetDemand never becomes fake zero dose;
- partial session dose is explicitly marked partial rather than represented as a complete total.

The initial versioned engineering parameters remain pre-registered rather than tuned on installed history:

- dynamic-resistance delta = `0.05` log units;
- loaded-hold delta = `0.05` log units;
- repeated-contraction delta = `0.05` log units;
- duration-only delta = `0.05` log units;
- frontier-contradiction trigger = `P(gap < 0) >= 0.95`;
- concave-session `tau = 4.0` raw-dose units.

Sensitivity acceptance separately evaluates delta `0.025 / 0.05 / 0.10` and tau `2 / 4 / 8` without retuning.

## Causal replay and capability boundaries

Installed-history replay now:

1. selects correction-aware historical revisions knowable at the target session start;
2. trains capability only on sessions completed strictly before the target session;
3. excludes same-session evidence from the SetDemand baseline;
4. permits a later correction to repair the target performed observation without leaking that correction backwards into the earlier baseline;
5. allows an earlier correction to affect later sessions once the correction is chronologically knowable;
6. uses the already-selected capability implementations rather than reopening the solver decision:
   - dynamic Candidate-v2: corrected-v3 evidence, frozen Candidate-v1 base, Adaptive Sparse forward representation;
   - 7C non-dynamic families: frozen family Adaptive Sparse solver;
7. fits each pre-session profile-version/side stream once and shares that posterior across all same-session sets in the stream.

No generic Half-Normal action-policy probability, RIR, RPE, failure probability or `% max` reconstruction is introduced.

## Same-session dependence and cross-stream approximation

Same capability-stream set contributions are aggregated node-by-node against their shared capability posterior. They are **not** treated as independent marginal draws.

Where one muscle session receives contributions from separate capability streams and no joint cross-stream posterior exists, 7D v1 performs the explicitly declared cross-stream independence approximation only after within-stream joint aggregation. The acceptance JSON exposes when this approximation is used.

## Persistence and replay

Room14 remains sufficient.

`NBio7DShadowRepository` persists one session-scoped SHADOW run containing:

- immutable 7D model/config definitions and manifest;
- exact pre-session capability-state summaries;
- encoded capability parameter snapshots required to rebuild joint 7D posteriors without refitting;
- SetDemand summary rows;
- exact historical muscle Exposure and optional EffectiveDose summary rows;
- canonical raw muscle SessionDose posterior summaries.

The concave session posterior is deterministically rebuilt from the raw joint substrate and immutable tau rather than requiring a new Room column.

Derived 7D runs are cascade-deletable without touching canonical observations or historical recruitment versions. Correction/replay utilities invalidate candidate-derived horizons while preserving raw evidence.

Native full backup remains generic. Dedicated 7D instrumentation and installed-device verification cover backup/restore candidate rows and foreign-key integrity.

## Structural validation implemented

The implementation contains a consolidated 14-case structural suite covering:

1. high-demand set;
2. clearly sub-frontier set;
3. broad capability posterior;
4. sparse/prior-dominated capability;
5. positive trajectory;
6. declining trajectory;
7. repetition extrapolation;
8. loaded-hold duration extrapolation;
9. repeated-contraction extrapolation;
10. duration-only capability;
11. semantic boundary isolation;
12. side isolation;
13. numerical stress;
14. frontier contradiction / fail-closed behaviour.

Additional validation layers cover:

- exact Exposure preservation and no cross-muscle conservation;
- full-posterior EffectiveDose transforms;
- same-stream covariance preservation and set-order invariance;
- partial/unresolved SessionDose semantics;
- delta/tau sensitivity without fitting or retuning;
- causal correction boundaries;
- dynamic Dense-vs-Adaptive-Sparse downstream 7D fidelity on stable, progressing and extrapolation-stress fixtures;
- 7C loaded-hold Dense-vs-Adaptive-Sparse downstream fidelity;
- Room14 persistence/reload/delete/full replay;
- Native backup/restore;
- raw-evidence, prescription-state and `BENCHMARK_V0` authority invariance.

## Consolidated developer action

The installed app now exposes one dedicated developer surface:

**N-BIO-7D Demand & Dose Acceptance**

Path:

`Settings → Biological developer tools → N-BIO-7D → Run N-BIO-7D Demand & Dose Acceptance`

The action exports one privacy-bounded JSON containing structural/sensitivity/correction/fidelity results, real-history aggregate demand/dose diagnostics, persistence/replay/backup checks, model/config/solver identities, integrity fingerprints, PD-001/PD-002 status and an explicit `nBio7EStarted=false` marker.

The real-history export deliberately omits exercise names, free text/notes, individual loads and session timestamps.

## Quarantine still binding

The implementation does **not** establish that:

- frontier gap is a biologically calibrated measure of effort or failure proximity;
- the current delta values are physiologically correct;
- recruitment weight equals biological stimulus;
- the candidate EffectiveDose transform is a validated hypertrophy or fatigue dose;
- the logarithmic SessionDose transform or tau is empirically calibrated;
- 7C capability is empirically accurate where PD-001 remains pending.

Therefore:

- `PD-001` remains OPEN for 7C capability empirical accuracy;
- `PD-002` remains OPEN for 7D SetDemand / EffectiveDose calibration;
- all 7D output remains SHADOW/CANDIDATE;
- normal workout prescription/prefill behaviour remains unchanged;
- fatigue, recovery, readiness, development, skill and decay remain unimplemented;
- N-BIO-7E has not started.

## Closure procedure

Before N-BIO-7D can receive a physical structural closure checkpoint:

1. exact branch-head Android CI must pass unit tests, debug APK, instrumentation compile, lint and Room14 schema verification;
2. build/install the branch on the target device;
3. open the developer action above and run it against installed Room14 history;
4. export the N-BIO-7D JSON;
5. review the JSON for structural PASS, unchanged raw/prescription/BENCHMARK fingerprints, backup PASS, no unexpected numerical failures and explicit PD-001/PD-002 quarantine;
6. only then record the physical structural closure result.

No N-BIO-7E work should begin as part of this checkpoint.
