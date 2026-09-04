# N-BIO-7E — Temporal State & Context Contract

Status: **NORMATIVE CANDIDATE CONTRACT — PRE-REGISTERED BEFORE 7E REAL-HISTORY INSPECTION; IMPLEMENTED AT `59a47b05`; PHYSICAL ACCEPTANCE PENDING**

Date pre-registered: 2026-09-03
Starting source head: `487705cc5810ced4da75bb56dd71c1fbcafc348b`

This contract governs the first N-BIO-7E SHADOW candidate. It is written after the authority/source audit and targeted architecture/science reconnaissance, but before any 7E real-history output is inspected or used to choose constants. A later mathematical change requires a new immutable model/config identity; it must not silently mutate this candidate.

Read with `PLAN.md`, `ADAPTIVE_INFERENCE_ARCHITECTURE_PLAN.md`, `CONTEXT_INTERPRETATION_CONTRACT.md`, `CONTEXT_MODULE_ARCHITECTURE.md`, `SET_DEMAND_AND_DOSE_CONTRACT.md`, `POSTPONED_DEVELOPMENT.md` and `PRODUCT_ROADMAP_GATES.md`.

## 1. Scientific and product boundary

7E estimates the smallest neutral statistical state that may improve future performance prediction:

- a slowly moving **persistent performance component**;
- a mean-reverting **transient performance component**;
- robust observation noise;
- a separately scoped local-transient signal route, without adding an independently evolving local latent dimension until identifiable evidence exists;
- optional, strongly-shrunk candidate contributions from prior SessionDose and validated ContextSignals.

These quantities are not measured hypertrophy, neural adaptation, fatigue, physical repair, illness causation or coaching decisions. They remain derived SHADOW/CANDIDATE state. `BENCHMARK_V0` remains normal-product authority.

7E does not start equipment translation (7F), conditioning (7G), decision policy (8), or broader Health/product integration (9).

## 2. Evidence and chronology

The causal sequence for session `t` is:

```text
canonical evidence available strictly before session t
+ prior 7E posterior
+ prior SessionDose only, with 7D provenance and PD-002 status
+ ContextSignals whose effective interval includes the pre-session horizon
→ freeze BASE, DOSE and CONTEXT predictions for t
→ observe session t performance
→ score all frozen predictions
→ update temporal posterior
→ update module memory using the frozen pre-session prediction and realised residual
→ publish signals only for future horizons
```

No outcome from session `t` may enter its own frozen prediction. `evidenceThrough <= predictedAt < observedAt` is mandatory for prospective/fresh predictions. Retrospective development replay uses the same predict-then-update order and is labelled `RETROSPECTIVE_DEVELOPMENT`. A future smoothed analysis, if added, requires a distinct semantics/model identity and may not be reported as causal.

Same-session sets are reduced to one model-specific profile/session observation or otherwise retain a shared session dependency. They do not count as independent longitudinal updates.

## 3. Observation coordinate

For an eligible profile/version/side/session observation, define a dimensionless log-performance residual against the frozen 7C-capability-family or dynamic-capability prediction:

```text
y_t = log(observedPerformanceCoordinate_t / frozenCapabilityMedian_t)
```

The exact family adapter and upstream capability model/solver identity are retained in provenance. Unsupported, non-positive, semantically incompatible or absent coordinates fail closed. 7C-dependent coordinates retain PD-001 quarantine.

The v1 installed-history adapter then takes one arithmetic mean log residual per session. It conservatively retains the mean component predictive variance rather than dividing by profile-row count, because same-session profiles share context and cannot be treated as independent longitudinal draws. This produces a session-level shared residual signal after profile-local capability has already been modelled upstream; it does not translate kilograms across profiles or infer a shared muscle-development quantity. Profile-specific skill/trajectory remains owned by the upstream capability state.

## 4. Candidate model family comparison

Every eligible stream retains these immutable candidate layers:

1. `CAPABILITY_BASELINE`: frozen upstream capability prediction; no new 7E state.
2. `TEMPORAL_BASE`: two-state persistent/transient model below; no dose/context input.
3. `DOSE_TEMPORAL`: identical transition/observation mathematics plus a strongly-shrunk prior-SessionDose covariate.
4. `CONTEXT_TEMPORAL`: identical dose-temporal model plus accepted ContextSignals through central arbitration.

An exponential prior-dose impulse is retained as a transparent challenger/input construction, not accepted physiology. A one-state local-level model is retained as a simpler synthetic/reference challenger. Model selection uses chronological proper scores and failure/coverage behaviour, not median MAE alone.

## 5. Selected neutral state mathematics

For elapsed time `Δ_t` in days, the persisted filter state is:

```text
x_t = [p_t, z_t, β_D]ᵀ
```

where:

- `p_t` is the persistent log-performance component;
- `z_t` is the systemic transient log-performance component.
- `β_D` is a static, zero-centred and strongly-shrunk coefficient for the optional prior-dose covariate. It is a statistical regression parameter, not a latent dose or fatigue state.

Transition before session `t`:

```text
p_t^- = p_(t-1)^+
z_t^- = φ(Δ_t) z_(t-1)^+
β_D,t^- = β_D,t-1^+

φ(Δ_t) = exp(-ln(2) Δ_t / h_z)

P_t^- = F_t P_(t-1)^+ F_tᵀ + Q_t
F_t = diag(1, φ(Δ_t), 1)
Q_t = diag(q_p Δ_t, q_z (1 - φ(Δ_t)^2), 0)
```

`d_t` is the pre-session recent-dose covariate computed only from resolved prior SessionDose outputs:

```text
d_t = Σ_j standardise(SessionDose_j) exp(-ln(2) ageDays_j / h_D), j < t
```

For `TEMPORAL_BASE`, the observation design fixes the dose coordinate to zero, so `β_D` cannot influence or learn from that layer. For `DOSE_TEMPORAL` and `CONTEXT_TEMPORAL`, `β_D` is represented in the joint Gaussian posterior and the observation design uses `d_t`. Missing/unresolved dose contributes no fabricated observed dose: the design coordinate is zero, availability is reported false, and the dose-aware candidate falls back to a BASE-equivalent prediction/update for that horizon without changing the dose coefficient.

Observation equation before robustification:

```text
y_t = p_t + z_t + β_D d_t + c_t + ε_t
ε_t ~ Normal(0, r_t)
```

`c_t` is zero for BASE/DOSE and is the validated centrally-arbitrated context location shift for CONTEXT. An accepted `OBSERVATION_VARIANCE` signal modifies `r_t` in log-variance space. Modules never write `p_t`, `z_t`, `P_t` or `r_t` directly.

Equivalently, the filter uses `H_t = [1, 1, 0]` for `TEMPORAL_BASE` and `H_t = [1, 1, d_t]` for dose-aware layers. Context location and context uncertainty enter the observation prediction, not the latent transition. This prevents a module from directly altering either persistent or transient Core state.

The initial production/reference solver is a deterministic Gaussian state-space filter using exact linear-Gaussian predict/update equations after deterministic bounded robust variance inflation. Because each conditional update remains linear-Gaussian, this filter is itself the high-fidelity sequential reference for the declared v1 approximation; independent closed-form transition/observation invariants and latent-truth fixtures check it. 7E makes no claim that Adaptive Sparse, Dense-grid, Laplace or particle backends implement this model, and does not reopen the 7B.X solver tournament.

## 6. Robust observation treatment

Let:

```text
innovation ν_t = y_t - H x_t^- - c_t
innovation variance S_t = H P_t^- Hᵀ + r_t
standardised residual a_t = ν_t / sqrt(S_t)
H_t = [1, 1, 0] for TEMPORAL_BASE; [1, 1, d_t] for dose-aware layers
```

The candidate uses Huber-style variance inflation:

```text
w_t = min(1, k / |a_t|)
r_t* = r_t / w_t²
```

The ordinary Kalman update is then performed with `r_t*`. This retains the sign and existence of an anomalous observation while bounding its leverage. It does not delete or rewrite the raw observation.

## 7. Immutable v1 parameters

All behaviour-relevant constants are stored in the canonical model-config payload.

```text
configSchemaVersion = 1
timeUnit = day
persistentPriorMean = 0.0
persistentPriorVariance = 0.0400
transientPriorMean = 0.0
transientPriorVariance = 0.0225
persistentProcessVariancePerDay = 0.000025
transientStationaryProcessVariance = 0.0025
transientHalfLifeDays = 3.0
observationVariance = 0.0100
huberThresholdStandardDeviations = 3.0
doseHalfLifeDays = 3.0
doseCoefficientPriorMean = 0.0
doseCoefficientPriorVariance = 0.0004
doseStandardisationScale = 4.0
minimumIndependentSessionsForPartialLearning = 3
minimumIndependentSessionsForDataInformed = 8
minimumIndependentEpisodesForDataInformed = 3
signalSchemaVersion = 1
maximumAbsoluteContextLocationShift = 0.20
maximumObservationLogVarianceShift = 1.38629436112
minimumSignalVariance = 0.0001
maximumSignalVariance = 1.0
```

These values are engineering priors selected before 7E real-history inspection. They are not biological constants and remain subject to PD-003.

## 8. Persistent and transient semantics

`persistent` means only the unit-root state `p_t` whose expected transition retains its previous value and whose process variance grows slowly with elapsed days. It may represent any stable performance-relevant change, including profile learning, neural adaptation, morphology or unresolved stable context. It is not named Development or hypertrophy.

`transient` means only the zero-centred AR(1) state `z_t` whose expected magnitude halves every configured `h_z` days in the absence of new evidence/input. It is not named fatigue or recovery. A derived availability index may be calculated for developer diagnostics, but it may not be described as percentage repaired.

## 9. Identifiability constraints

The candidate prevents all explanations moving freely:

- only two dynamic states are enabled in the systemic profile model;
- persistent process variance is fixed and two orders of magnitude smaller than transient variance;
- transient state is zero-centred and mean-reverting with a fixed half-life in v1;
- observation variance is fixed except for bounded, validated variance signals;
- robust outliers are variance-inflated rather than assigned wholly to either state;
- dose coefficient starts at zero with strong shrinkage and is estimated only from prior-dose/future-residual pairs;
- context modules start neutral/broad and cannot update from the same outcome they predicted;
- no separate Skill or Development latent is added in 7E v1;
- local transient signals remain anatomy-scoped and cannot update systemic state directly; v1 does not add a separately evolving local latent dimension;
- semantic profile/version boundaries partition replay;
- broad posterior overlap or competing explanations must remain broad; synthetic truth recovery is not forced.

## 10. Local and systemic scope

`SYSTEMIC_TRANSIENT_STATE` signals may apply across eligible profile streams for the same user and horizon only through central arbitration.

`LOCAL_TRANSIENT_STATE` signals require an anatomy scope ID and may apply only to a profile observation whose immutable historical recruitment semantics include that scope. V1 implements and tests the envelope/validation/arbitration route, but deliberately does not add a separately evolving local latent dimension or promote a production note tag merely to exercise it. A future local learner must first earn identifiability and define the authorised recruitment-scoped read adapter under a new immutable model/config.

Execution-profile-only offsets are not local biological state and remain a later capability/execution/equipment concern.

## 11. Feature definition and evidence

The 7E feature definition is explicitly versioned and separate from evidence. It declares:

```text
FeatureKey(featureId, schemaVersion)
valueSchema
allowedScopes
allowedSourceKinds
temporalSemantics
missingnessSemantics
allowedSignalTargets
requiredReadCapabilities
compatibleEvidenceVersions
humanMeaning
```

Supported value schemas are boolean, ordinal, continuous-with-unit, categorical, anatomy-scoped and bounded structured-reference. Legacy 7A.5 v1 tags are adapted without rewriting their stored annotations.

Evidence distinguishes `PRESENT`, `KNOWN_FALSE`, `NOT_REPORTED`, `NOT_MEASURED`, `NOT_APPLICABLE` and `UNKNOWN`. Absence of a legacy annotation is `NOT_REPORTED`, never `KNOWN_FALSE`. Negated asserted evidence maps to `KNOWN_FALSE`; uncertain wording remains `UNKNOWN` unless a module explicitly supports uncertain evidence.

## 12. Module protocol

A `ContextModuleProvider` exposes an immutable descriptor and creates a `ContextModule`. Providers are assembled through an explicit deterministic in-app/build-time registry, sorted by stable module ID. Duplicate identities, incompatible protocol/config/feature versions and undeclared capabilities fail closed before execution.

No `ServiceLoader`, reflection, dynamic DEX loading, remote marketplace or downloaded executable module is used. Future human-authored modules integrate as controlled source/library dependencies that contribute a provider to the composition-root registry and pass the TCK. N-BIO Core itself receives only validated generic signals and requires no feature-ID branch.

A module declares:

- module/protocol/model/config/state-codec identities;
- consumed feature definition versions;
- required read capabilities;
- allowed signal targets/scopes;
- deterministic replay claim;
- learner family.

The runtime grants a narrow immutable `ContextReadView`. A module receives only capabilities it both declares and the host approves.

The two initial production modules persist state schema v2. Arbitrary evidence/session/episode identifiers are base64url-framed inside the module-owned codec. The earlier pre-acceptance v1 checkpoint layout is not silently compatible: reload fails closed and canonical replay rebuilds derived state.

## 13. Read capabilities

7E v1 defines typed capabilities:

```text
OWN_FEATURE_EVIDENCE
TIME_AND_SCOPE
FROZEN_PRE_SESSION_PREDICTION
REALISED_POST_SESSION_RESIDUAL
SESSION_DOSE_SUMMARY
APPROVED_EXECUTION_SEMANTICS
```

Availability is not permission. Raw note text, unrestricted database access, unrelated feature evidence, Health data and direct mutable core state are not capabilities. `REALISED_POST_SESSION_RESIDUAL` is exposed only in the post-session learning phase and can never be present in the pre-session publication phase.

## 14. ContextSignal envelope

Signal schema v1 contains:

```text
signalId
signalSchemaVersion
sourceModuleId / moduleModelVersion / moduleConfigId
sourceFeatureKey
target
scope
effectiveFrom / effectiveUntil
effectRepresentation
locationMean / variance
evidenceRowCount / independentSessionCount / independentEpisodeCount
evidenceMaturity
correlationGroupId
episodeId?
sourceEvidenceIds
upstreamModelIdentities
publishedAt
status / failureCode?
```

Signals are immutable derived outputs, rebuilt/superseded through replay. Unknown versions, duplicate IDs, non-finite values, illegal intervals, unsupported targets, incompatible scopes, excessive bounds, permission violations and stale model/config identities fail closed. Rejection creates typed failure provenance; it never creates a plausible default effect.

## 15. Targets and v1 authority

Implemented in 7E v1:

- `SYSTEMIC_TRANSIENT_STATE`;
- `OBSERVATION_VARIANCE`.

Accepted as a protocol-only route in 7E v1:

- `LOCAL_TRANSIENT_STATE` (scope validation and central arbitration are implemented, but no production module or evolving local latent/filter consumer exists yet).

Reserved in the protocol but rejected by the v1 target policy:

- `OBSERVATION_RELIABILITY`;
- `PROCESS_VOLATILITY`;
- `RECOVERY_DYNAMICS`;
- `CAPABILITY_CONDITIONING`;
- `EXECUTION_CONTEXT`;
- `EQUIPMENT_TRANSLATION`;
- `RECRUITMENT_CONTEXT`.

Equipment translation remains 7F, conditioning remains 7G and coaching/action policy remains 8.

## 16. Central arbitration

Core validates every signal generically, then groups by target + exact scope + effective horizon. Signals sharing a `correlationGroupId` are treated as potentially duplicate explanations: one representative with the strongest evidence maturity/support and smallest valid variance is retained; their location means are never summed.

Representatives from distinct groups are precision-weighted into one bounded location posterior, not additively stacked. Between-signal disagreement inflates the combined variance. Opposing signals whose uncertainty overlaps zero therefore remain broad. Observation-variance signals combine in the same guarded mean/log-variance space. No feature ID appears in arbitration logic.

The context-free prediction is always preserved and scored alongside the context candidate. A signal is empirically useful only if chronological scoring improves enough under a future promotion rule; 7E structural completion alone does not grant product authority.

## 17. Representative module learners

Two different learner families prove the protocol.

### Episode association module

Production representative: legacy `ILLNESS_REPORTED` v1 adapter.

The module clusters temporally compatible positive evidence into derived episodes, tracks explicit resolution, maintains a Beta-Bernoulli persistence posterior and a conjugate normal association posterior over future frozen residuals. Rows inside one episode increase row/session support but only one independent-episode count. Missing evidence does not resolve an episode. It may publish `SYSTEMIC_TRANSIENT_STATE` only.

Immutable config `context-module:illness-episode:v1` is exported canonically with: maximum episode gap 168 hours; maximum episode age 14 days; association half-life 3 days; association prior variance 0.0100; residual observation variance 0.0400; persistence Beta prior `(1,1)`; partial/data-informed thresholds 1/3 independent episodes; maximum absolute log-location shift 0.20; and signal variance bounds `[0.0001, 1.0]`.

### Observation variance association module

Production representative: legacy `TIME_PRESSURE_REPORTED` v1 adapter.

This module does not create episodes or learn a median penalty. It maintains separate robust residual second-moment sufficient statistics for explicit-present versus explicitly-known-false evidence and publishes a bounded log observation-variance ratio only when both groups have support. Unmentioned evidence is not placed in the false/control group. It may publish `OBSERVATION_VARIANCE` only.

Immutable config `context-module:time-pressure-variance:v1` is exported canonically with: prior count 2.0; prior variance sum 0.02; squared-residual cap 0.25; maximum absolute log-variance shift 1.38629436112; partial/data-informed thresholds 1/8 distinct sessions per explicit group; signal variance bounds `[0.0001, 1.0]`; and persisted signal-envelope validity 86,400 seconds. The source feature itself remains session-scoped, so later pre-session publication does not carry it forward.

A synthetic anatomy-scoped module fixture proves `LOCAL_TRANSIENT_STATE` scope enforcement without inventing a new production ontology tag.

## 18. Module lifecycle and threading

The host owns coroutines/dispatchers. Modules expose deterministic suspend-free state transformations and may not create scopes, threads, global mutable state or perform I/O. For one user/replay, events are ordered by `(effectiveAt, sourceEvidenceId, moduleId)` and each module instance executes serially. Different modules may be evaluated concurrently only if the host collects immutable results and publishes/persists them later in deterministic module-ID order.

Cancellation occurs only between event/module operations. One transaction atomically persists an inference run, module states, accepted signals and temporal state. A cancelled/failed run publishes none of those partial outputs. A module exception is captured as failure provenance; other modules and BASE inference continue.

## 19. Persistence, invalidation and replay

Room14 cannot represent module-owned state, generic signals and neutral temporal dimensions without abusing capability/profile or biologically named adaptive-state rows. Room15 is therefore semantically required with five explicit additive tables for:

- 7E run/provenance identity;
- neutral temporal state estimates;
- module-owned state;
- ContextSignals;
- module execution/failure status.

Feature definitions remain immutable build-integrated provider metadata in v1; they are not falsely represented as database observations. The 14→15 migration is additive and preserves every existing table/row. Full backup remains generic and round-trips the five tables. Derived-run deletion cascades only 7E derived rows. Feature reannotation maps the affected feature identity through the immutable provider registry, deletes only the consuming module state/status/signals plus the combined `CONTEXT_TEMPORAL` state, and preserves unrelated module memory and the context-free/dose temporal candidates. Deleting the complete annotation substrate clears all module-derived/context-conditioned rows while still preserving context-free/dose state. Raw notes, annotations, workouts and 7C/7D rows retain their existing owners. Full replay is canonical.

## 20. Evidence maturity

Module parameters and signals use:

```text
NO_EVIDENCE
PRIOR_DOMINATED
PARTIALLY_LEARNED
DATA_INFORMED
EMPIRICALLY_USEFUL
NO_PREDICTIVE_BENEFIT
REJECTED
```

`EMPIRICALLY_USEFUL` and `NO_PREDICTIVE_BENEFIT` require chronological comparison; row count alone cannot produce them. Extractor confidence is stored only as source provenance and never maps into these states.

## 21. Evaluation and closure

Chronological evaluation reports, where supported: CRPS, log predictive density, PIT/reliability, WIS, 90% coverage, sharpness, signed bias, MAE secondary, catastrophic contradictions, availability and module ablations. Metrics remain separated by measurement family/profile where units differ.

Synthetic suites must include all temporal and module cases listed in the mission, including irrelevant context, correlated dose/context, competing explanations, scope, missingness, leakage, codec/replay and failure isolation. Known truth may legitimately remain weakly identified; the expected safe result is broad uncertainty rather than forced attribution.

## 22. PD quarantine

PD-001 and PD-002 remain OPEN. 7E provenance retains all upstream model/config/status identities.

PD-003 is required at structural closure unless fresh prospective human evidence establishes calibration. It will quarantine:

- persistent/transient decomposition calibration;
- latent local/systemic interpretation;
- personal dose coefficient;
- personal context association and persistence;
- prospective usefulness of context-conditioned predictions.

Later phases may consume 7E interfaces and explicitly quarantined SHADOW candidates, but may not treat them as validated biological truth or coaching authority.
