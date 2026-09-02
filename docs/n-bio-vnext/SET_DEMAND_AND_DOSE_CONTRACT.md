# N-BIO-7D Set Demand & Dose Contract

Status: **NORMATIVE CANDIDATE CONTRACT — N-BIO-7D STRUCTURAL DEVELOPMENT**

Date pre-registered: 2026-09-02

This contract is authoritative for N-BIO-7D implementation unless a later immutable model version explicitly replaces it. It is written before 7D behaviour-bearing implementation or real-history output inspection. N-BIO-7C remains structurally frozen. N-BIO-7E is not started.

## 1. Scientific boundary

N-BIO-7D introduces four deliberately separate layers:

1. performed-set `SetDemand`;
2. conservative muscle `Exposure`;
3. uncertain muscle-local `EffectiveDose` proxy;
4. muscle-local raw and concave `SessionDose`.

No value in this contract is a universal stimulus, effort, fatigue, fitness, development, readiness, effective-repetition, or body-wide training score.

`SetDemand` is not RIR, RPE, failure probability, local muscle activation, local muscle force, percent effort, percent of maximum, or a probability that the user would choose the set. A completed set is not silently interpreted as an RM/failure set.

`Exposure` is not transferred external load, EMG, local force, stimulus, fatigue, or dose.

`EffectiveDose` is a modelled local projection from task demand and authored recruitment semantics. It is not actual fibre stimulus, motor-unit recruitment percentage, local RIR, or hypertrophy caused.

Empirical calibration of the 7D demand/dose mapping is quarantined by PD-002. The 7C families additionally remain quarantined by PD-001.

## 2. Execution mode and causal semantics

The first 7D candidate supports **ONLINE / CAUSAL REPLAY semantics only** for demand-bearing historical reconstruction.

For a session `S`, each capability stream is keyed by the exact historical:

- execution-profile version;
- laterality stream;
- capability family;
- capability mathematical-model identity;
- capability solver identity.

The capability fit/reference used for every set in `S` is built only from eligible observations in strictly earlier sessions for that stream. Evidence from `S` itself is excluded from the persistent demand baseline. Future sessions are excluded.

The target session is evaluated at the next profile-local independent-session coordinate relative to the latest eligible pre-session evidence. Every set from the same stream in `S` therefore uses the same pre-session posterior and target-session offset.

After `S` completes, the capability engine may fit/update from the whole completed session for use by a later session. N-BIO-7D does not implement an acute within-session fatigue or capability transition.

A retrospective/smoothed interpretation that consumes future sessions is **not implemented in 7D v1**. If added later it requires a separate semantics mode/model identity and must never be substituted for causal replay.

This rule makes demand invariant to arbitrary reordering of otherwise identical same-session sets.

## 3. SetDemand semantic definition

For a performed observation `s`, `SetDemand` is the posterior location of that observation relative to the contemporaneous profile-local capability frontier, conditional on the performed observation having occurred.

The primary latent is a **frontier gap** `g_s` in log coordinates. Positive values mean the observation lies below the estimated capability frontier. Values near zero mean it lies near the frontier. Negative posterior mass is preserved diagnostically rather than clamped into a fictitious superhuman score.

### 3.1 Family-specific frontier-gap coordinates

Dynamic resistance:

`g_s = log(R_frontier(reps_s, session=S)) - log(R_observed_s)`

Loaded hold:

`g_s = log(R_frontier(duration_s, session=S)) - log(R_observed_s)`

Repeated contraction:

`g_s = log(R_frontier(cycles_s, session=S)) - log(R_observed_s)`

Duration only:

`g_s = log(T_frontier(session=S)) - log(T_observed_s)`

The exact frontier is evaluated from the accepted family contract and the pre-session joint capability posterior. Dynamic resistance uses the accepted Candidate-v2 profile-local frontier/trajectory representation. Loaded-hold, repeated-contraction and duration-only use the accepted 7C coordinates without inventing cross-family workload dimensions.

No generic Half-Normal slack distribution is used as a working-set action policy. The existing one-sided slack remains a capability-likelihood nuisance where its capability model uses it. 7D demand is derived directly from the conditional capability frontier posterior at the performed coordinate.

### 3.2 Posterior representation

Where a capability fit supplies weighted posterior nodes, 7D transforms those same weighted nodes to frontier-gap nodes. The implementation preserves enough information to report/rebuild:

- `p05(g_s)`;
- `p50(g_s)`;
- `p95(g_s)`;
- `Var(g_s)`;
- `q_s(delta) = P(g_s <= delta_family)`;
- contradiction mass `P(g_s < 0)`.

The persisted `set_demand_estimate` posterior is the frontier-gap summary, not RIR and not the binary high-demand probability. The exact pre-session joint capability snapshot is persisted in the same session-scoped 7D inference run, so `q_s(delta)` is deterministically recomputable without refitting capability.

### 3.3 Delta policy

7D v1 pre-registers separate family configuration keys:

- `dynamicResistanceDeltaLog = 0.05`;
- `loadedHoldDeltaLog = 0.05`;
- `repeatedContractionDeltaLog = 0.05`;
- `durationOnlyDeltaLog = 0.05`.

The equal initial numeric values do **not** assert universal family equivalence. They are four independently versioned modelling hyperparameters in the common log-gap coordinate and may diverge in a future immutable model version.

`0.05` is not “2 RIR”, “RPE 8”, “95% effort”, an effective-reps threshold, or a biological constant. It is a candidate near-frontier band selected before real-history 7D outputs are inspected.

Structural sensitivity diagnostics evaluate at least `0.025`, `0.05`, and `0.10` log-gap units. Sensitivity results do not retune the registered v1 value.

### 3.4 Contradiction diagnostics

Negative gap nodes are retained. `contradictionProbability = P(g_s < 0)` is reported.

A set is typed `FRONTIER_CONTRADICTION` and its EffectiveDose is unresolved if `contradictionProbability >= 0.95`. Lower negative-gap mass remains a warning/uncertainty feature rather than being silently clamped.

The `0.95` contradiction trigger is a versioned numerical safety diagnostic, not physiology.

## 4. Demand support and identifiability

7D uses two axes rather than collapsing empirical status into numerical support.

Structural support:

- `RESOLVED` — finite posterior with adequate in-domain support and no configured broad/prior/contradiction condition;
- `BROAD` — finite posterior but extrapolation or declared capability uncertainty prevents a precise interpretation;
- `PRIOR_DOMINATED` — essential capability coordinates are explicitly prior dominated;
- `UNSUPPORTED` — no eligible pre-session capability fit or unsupported metric/semantic combination;
- `FRONTIER_CONTRADICTION` — strong posterior contradiction as defined above.

Empirical status:

- `EMPIRICAL_CALIBRATION_PENDING` for 7D v1 under PD-002;
- 7C-family outputs also retain `EMPIRICAL_ACCURACY_PENDING` under PD-001.

A broad capability posterior must remain broad after the deterministic gap transform. Missing capability is not replaced by `Demand=0.5`, zero, or a benchmark guess.

Extrapolation is inherited from the capability family’s accepted support/domain contract. 7D does not shrink uncertainty after an extrapolated capability query.

## 5. Working-set eligibility

7D consumes only canonical performed evidence already accepted by the inference substrate and additionally requires:

- completed session;
- session insight eligibility;
- current non-superseded observation as of the replay cutoff;
- non-draft/performed set record;
- non-warmup set;
- exact execution-profile version;
- supported metric family and valid metric values;
- valid historical laterality stream;
- exact historical recruitment-profile version for Exposure/Dose.

Deleted/superseded observations, drafts, warmups, invalid observations and unsupported metric combinations create no Exposure/Dose.

Corrections select the current valid raw revision without mutating the historical raw record. Derived 7D runs are disposable/replayable.

## 6. Exposure

For an eligible performed set `s` and historical recruitment allocation to muscle/segment `m`:

`Exposure_sm = recruitmentWeight_(historical recruitment profile, m)`

This value is deterministic and directly recomputable from immutable historical execution/recruitment semantics.

Recruitment weights are independent local weights. They are not a conserved pie and are never normalised to sum to one. A vector such as `1.0 / 0.7 / 0.4` remains exactly `1.0 / 0.7 / 0.4`. Role labels do not introduce hidden multipliers. External kilograms do not enter Exposure.

Changing capability does not change Exposure.

## 7. Laterality

7D preserves the exact established laterality stream. It does not double unilateral external load, split bilateral capability into fabricated unilateral capability, or reinterpret `UNKNOWN` laterality.

Muscle projection uses the set observation’s historical side semantics and the exact historical recruitment profile. `UNKNOWN` remains `UNKNOWN` unless an explicit raw semantic revision exists.

## 8. EffectiveDose candidate

The first 7D candidate intentionally implements the conservative research candidate `Exposure × Q`, but does so over the full shared posterior rather than multiplying medians.

For each capability posterior node `j`:

`Q_sj = 1 if g_sj <= delta_family else 0`

`EffectiveDose_smj = Exposure_sm * Q_sj`

The posterior node weights are inherited from the pre-session capability posterior. Therefore the marginal mean probability of the high-demand band is `q_s(delta)`, while the full discrete dose posterior is retained during session aggregation.

This transform is:

- monotone in demand at fixed Exposure;
- monotone in Exposure at fixed demand;
- bounded between `0` and `Exposure` for this candidate;
- explicitly a modelling assumption under PD-002.

It does not claim that muscles recruited by a demanding whole task were equally close to their local failure frontiers.

If SetDemand is `UNSUPPORTED` or `FRONTIER_CONTRADICTION`, EffectiveDose is unresolved/null. If demand is `BROAD` or `PRIOR_DOMINATED`, the dose remains a broad/prior-dominated candidate posterior; it is not replaced by Exposure, zero, or a fixed 0.5 demand.

## 9. Same-session uncertainty and dependence

Set doses sharing a capability stream also share its pre-session latent posterior. 7D therefore aggregates them **node by node on the same posterior node**, preserving covariance induced by frontier, slope and trajectory uncertainty.

For a muscle/side and one capability stream:

`X_mj = sum_s EffectiveDose_smj`

The same node `j` is used for every set in that stream. Marginal set variances are never summed as if independent.

Different profile-local capability streams do not currently have a learned joint cross-profile posterior. 7D v1 therefore combines already-joint within-stream distributions under an explicit **cross-stream independence approximation**. Discrete stream distributions are convolved deterministically. The implementation must report this approximation and compare representative production results with a high-fidelity Dense reference. No claim is made that human capability across exercises is biologically independent.

If deterministic support compression is required for pathological Cartesian growth, its cap/algorithm is part of the immutable session-dose model config and its approximation error must be measured against an uncompressed reference fixture.

## 10. Raw SessionDose

For each session, muscle/segment and side:

`RawSessionDose_m = sum_s EffectiveDose_sm`

The sum includes only eligible resolved/broad candidate set-dose inputs. It is muscle-local. Cardio/conditioning is not converted into this resistance-training dose family. There is no body-wide total score.

The raw posterior is the canonical persisted `muscle_session_dose.posterior` in Room14. This keeps the raw derived evidence independent of the later concave-transform version.

## 11. Unresolved session inputs

Session status is:

- `FULLY_RESOLVED` — every otherwise eligible contributing set has a usable EffectiveDose candidate;
- `PARTIALLY_RESOLVED` — at least one contributing set has a usable dose and at least one is unresolved;
- `UNRESOLVED` — no complete total can be formed because all relevant dose-bearing inputs are unresolved or capability is unavailable.

Unresolved set dose is never substituted with zero.

For `PARTIALLY_RESOLVED`, a resolved raw subtotal posterior may be reported, but it must be labelled as a subtotal and accompanied by unresolved contributing-set counts. It must not masquerade as a posterior for the complete session total. For `UNRESOLVED`, total dose remains null/unresolved.

The Room14 posterior `evidenceFamily` descriptor carries the typed 7D support/status metadata required to distinguish complete totals from resolved subtotals without changing the schema.

## 12. Concave SessionDose

For every raw-dose posterior node/value `X >= 0`:

`ConcaveSessionDose = tau * ln(1 + X / tau)`

The nonlinear transform is applied to the raw distribution, not merely to the median.

7D v1 pre-registers:

`tau = 4.0` raw-dose units.

This is an **engineering candidate curvature scale**, selected before 7D history inspection because current evidence does not establish a biologically authoritative numeric tau. It is not MRV, maximum recoverable volume, failure capacity, a learned personal limit, or a statement that a particular numbered set has a known hypertrophic percentage.

Structural sensitivity diagnostics evaluate at least `tau = 2.0`, `4.0`, and `8.0` without retuning the registered v1 value. Empirical calibration remains PD-002.

Required mathematical properties:

- zero input maps to zero;
- non-negative input remains non-negative;
- monotone increasing;
- strictly concave for finite positive tau;
- an identical added raw dose gives a smaller transformed increment at larger `X`;
- increasing tau makes behaviour more nearly linear over a fixed finite raw-dose range.

Room14 persists the raw session posterior as the durable session-dose evidence. The concave posterior is a **separate named 7D output** deterministically rebuilt from the same persisted session-scoped joint substrate plus immutable session-dose config. This avoids pretending the one-posterior Room14 row contains two quantities and avoids Room15. Persistence/reload tests must compare both raw and reconstructed concave summaries exactly within deterministic numerical tolerance.

A later tau/model version may replace the concave interpretation without rewriting Exposure, SetDemand, EffectiveDose, or the persisted raw SessionDose.

## 13. Room14 persistence and provenance

Room schema remains 14.

Each causal historical 7D session analysis is a SHADOW/CANDIDATE session-scoped inference run. It persists enough provenance to identify:

- source session and set observations;
- exact execution-profile versions;
- exact recruitment-profile versions;
- capability mathematical-model identity;
- capability solver identity;
- the pre-session capability snapshot/parameter state used by the session;
- demand model/config identity;
- exposure model/config identity;
- EffectiveDose model/config identity;
- session-dose model/config identity, including tau and dependence approximation;
- causal historical semantics mode;
- source evidence horizon and timestamps.

Existing generic Room14 tables are reused:

- `capability_state` + `capability_parameter_state` for exact pre-session capability snapshots;
- `set_demand_estimate` for frontier-gap posterior summaries;
- `muscle_set_dose` for exact Exposure and EffectiveDose marginal summaries;
- `muscle_session_dose` for raw session-dose posterior/subtotal summaries;
- `model_config_definition`, manifest and inference-run tables for immutable identities/provenance.

No 7D state is raw truth. `adaptive_muscle_state` and `skill_state` remain untouched in this phase.

Native full backup already dumps all Room14 application tables generically, so 7D derived state participates in the existing exact-schema backup/restore path without a bespoke backup schema.

## 14. Invalidation and replay

A correction/supersession never mutates canonical prior raw evidence. Any affected 7D session-scoped derived run is invalidated/discarded and replayed from:

raw evidence + historical execution/recruitment semantics + capability model/config/solver + 7D model/config.

Replay must reproduce deterministic identities, posterior summaries, raw sums, reconstructed concave summaries, provenance and statuses where deterministic solver paths are claimed.

If a correction changes an earlier capability stream, every later causal session whose pre-session capability snapshot depends on that evidence is downstream and must be replayable.

## 15. Dense / Adaptive Sparse fidelity

Adaptive Sparse remains the selected Candidate-v2 forward representation and Dense remains the deterministic high-fidelity reference. 7D does not reopen the 7B.X solver tournament.

Representative downstream checks compare frontier-gap quantiles, `q(delta)`, EffectiveDose quantiles, raw SessionDose quantiles, concave SessionDose quantiles and tails on stable, progressing, difficult-tail and at least one 7C synthetic case.

Any material downstream discrepancy is recorded rather than tuned away.

## 16. Empirical status and PD quarantine

Structural validation asks whether the declared mathematics, uncertainty, semantics, persistence and replay are implemented safely.

Empirical calibration asks whether the latent demand and dose values correspond quantitatively to human demand/stimulus. Ordinary workout history does not directly observe that target. PD-002 therefore remains OPEN until suitable evidence can discriminate/calibrate the mapping.

PD-001 remains binding for loaded hold, duration-only and repeated contraction capability accuracy. A finite 7C posterior permits structural plumbing only; it does not promote those families to empirical demand/dose validation.

Later 7E work may consume the 7D candidate interface for structural development only. It must not claim validation because 7D EffectiveDose is correct. If a future 7D model changes materially, dependent derived state must be replayed.

## 17. Product and context quarantine

All 7D outputs remain DERIVED, SHADOW/CANDIDATE and NON-AUTHORITATIVE.

7D does not change workout prescription, load/repetition prefill, set count, exercise selection, routine generation, progression logic or normal workout UX. BENCHMARK_V0 remains normal-product authority.

7D does not use review notes, vibe, form, comfort, Nano, sleep, HR, HRV, illness or stress to infer demand/dose. Subjective text does not backfill RIR.

## 18. Explicit 7E exclusion

N-BIO-7D stops at the session. It does not implement RecentStimulus, Fatigue, Recovery, Readiness, Development, SkillState, slow/fast biological state, decay kernels or session-to-session biological dynamics.

At 7D closure `nBio7EStarted=false` must remain true.
