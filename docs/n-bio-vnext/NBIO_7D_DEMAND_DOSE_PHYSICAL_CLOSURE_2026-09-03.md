# N-BIO-7D — Demand & Dose physical structural closure

**Date:** 2026-09-03  
**Branch:** `agent/n-bio-vnext-inference`  
**Accepted device build:** `0.1.0-alpha31-dev (30)`  
**Accepted source head:** `f8a084dd5ea82dcfedf8bb5508499ffaf52356b7`  
**Room schema:** 14  
**Normal product authority:** `BENCHMARK_V0` unchanged  
**Structural verdict:** `PASS`  
**Empirical calibration:** `EMPIRICAL_CALIBRATION_PENDING`  
**N-BIO-7E:** not started

## Closure decision

N-BIO-7D has completed **physical installed-history structural/pre-validation acceptance** on Room14.

This closes the N-BIO-7D engineering/structural gate for the implemented SetDemand → Exposure → EffectiveDose → SessionDose SHADOW pipeline. It does **not** close empirical calibration and does not grant normal workout prescription or prefill authority.

The accepted device report returned:

- `structural = PASS`;
- `overall7D = READY_FOR_7D_STRUCTURAL_CLOSURE_EMPIRICAL_CALIBRATION_PENDING`;
- `PD-001 = OPEN` for 7C capability empirical accuracy;
- `PD-002 = OPEN` for 7D SetDemand / EffectiveDose empirical calibration;
- `BENCHMARK_V0 = PRESENT_AND_UNCHANGED`;
- `nBio7EStarted = false`.

The full personal acceptance export remains outside the repository. This checkpoint records only privacy-bounded aggregate acceptance facts.

## Installed-history coverage

The accepted replay covered:

- 18 planned sessions;
- 257 planned performed non-warmup target sets;
- 18 evaluated sessions;
- 236 evaluated sets;
- 1,849 historical muscle exposures;
- 1,169 resolved EffectiveDose outputs;
- 680 explicitly unresolved EffectiveDose outputs;
- 701 muscle SessionDose outputs.

Evaluated families were:

- dynamic resistance: 231 sets;
- duration only: 5 sets.

No loaded-hold or repeated-contraction target sets happened to be present in this installed-history sample; those families remain covered by the frozen 7C structural suite and 7D synthetic/downstream-fidelity validation rather than by this particular personal-history replay.

## Structural support observed

Real-history SetDemand structural-support classifications were:

- `RESOLVED`: 52;
- `BROAD`: 2;
- `PRIOR_DOMINATED`: 95;
- `FRONTIER_CONTRADICTION`: 27;
- `UNSUPPORTED`: 60.

SessionDose resolution classifications were:

- `FULLY_RESOLVED`: 396;
- `PARTIALLY_RESOLVED`: 81;
- `UNRESOLVED`: 224.

Observed stream failures were limited to typed fail-closed insufficiency conditions:

- `no_eligible_dynamic_pre_session_evidence`: 8;
- `no_pre_session_training_evidence`: 29.

These counts are **not calibration targets** and were not tuned away to obtain structural PASS. They remain useful evidence for PD-001/PD-002 and future empirical work.

## Structural validation PASS

All 14 pre-registered synthetic structural cases passed:

1. high demand;
2. sub-frontier;
3. broad capability;
4. sparse/prior-dominated capability;
5. positive trajectory projection;
6. declining trajectory projection;
7. repetition extrapolation;
8. loaded-hold duration extrapolation;
9. repeated-contraction extrapolation;
10. duration-only capability;
11. semantic boundary fail-closed behaviour;
12. side isolation;
13. numerical stress;
14. frontier contradiction fail-closed behaviour.

The additional validation bundle also passed:

- delta sensitivity at `0.025 / 0.05 / 0.10`;
- tau sensitivity at `2 / 4 / 8`;
- causal late-correction / target-correction / later-session boundary tests;
- dynamic Dense-vs-Adaptive-Sparse downstream fidelity on stable, progressing and repetition-extrapolation stress fixtures;
- loaded-hold 7C Dense-vs-Adaptive-Sparse downstream fidelity.

No biological thresholds or solver identities were changed in response to installed-history support proportions.

## Persistence, replay and backup PASS

For all 18 evaluated sessions:

- Room14 persist → reload equivalence passed;
- derived-run deletion passed;
- capability snapshots and encoded parameter states were preserved where available;
- unresolved posteriors retained their fail-closed Room14 representation without zero filling.

Representative full replay equivalence passed.

The isolated Native backup round-trip also passed with exact source/restored candidate counts:

- SHADOW runs: 18 / 18;
- capability states: 78 / 78;
- capability parameter states: 78 / 78;
- SetDemand estimates: 236 / 236;
- MuscleSetDose rows: 1,849 / 1,849;
- MuscleSessionDose rows: 701 / 701;
- AdaptiveMuscleState rows: 0 / 0;
- SkillState rows: 0 / 0.

Backup verification additionally confirmed:

- raw evidence matched exactly;
- prescription state matched exactly;
- foreign keys were clean;
- no N-BIO-7E state appeared.

## Authority and mutation invariants PASS

The acceptance run preserved all protected authority/state boundaries:

- canonical raw-evidence fingerprint unchanged;
- prescription-state fingerprint unchanged;
- exact `BENCHMARK_V0` run ID unchanged before/after;
- foreign-key integrity clean after completion;
- all 7D rows remained `SHADOW` candidate state;
- no normal workout prescription or prefill authority was granted;
- no RIR/RPE/failure-probability reconstruction was introduced;
- no fatigue, recovery, readiness, development, skill or decay state was introduced;
- no note/vibe/form/comfort/sleep/HR/HRV context was consumed.

## Performance observation

The installed-history acceptance completed in approximately 18.2 seconds on the accepted device build, with aggregate real-history capability-stream fitting taking approximately 7.3 seconds and a maximum single stream fit below one second.

The downstream oracle comparisons continued to show Adaptive Sparse materially faster than Dense while remaining within the pre-registered 7D downstream-fidelity guardrails. Performance evidence remains engineering evidence, not biological validation.

## Remaining quarantine

Structural closure does not establish that:

- frontier gap is a physiologically calibrated effort/failure-proximity quantity;
- delta `0.05` is the correct biological EffectiveDose boundary;
- historical recruitment weight is a validated stimulus magnitude;
- candidate EffectiveDose is a validated hypertrophy, fatigue or adaptation dose;
- the SessionDose concavity transform or `tau = 4` is physiologically calibrated;
- 7C capability is empirically accurate for the five 7C-dependent target sets in this replay.

Therefore:

- **PD-001 remains OPEN**;
- **PD-002 remains OPEN**;
- all 7D outputs remain SHADOW / developer-only;
- `BENCHMARK_V0` remains normal product authority;
- N-BIO-7E remains NOT STARTED.

## Forward state

N-BIO-7D structural/pre-validation is now physically closed.

Do not reopen its structural definitions merely because later empirical evidence changes calibration. Future empirical work should version calibration/model decisions through PD-001/PD-002 and immutable model/config identities.

Do not begin N-BIO-7E as an implicit continuation of this closure record. N-BIO-7E remains a separate mission governed by `CONTEXT_MODULE_ARCHITECTURE.md`, the wider N-BIO authority stack, and explicit user direction.
