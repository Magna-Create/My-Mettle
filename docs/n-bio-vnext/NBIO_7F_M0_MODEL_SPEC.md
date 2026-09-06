# N-BIO-7F M0 Model Specification

> **Status:** FROZEN PREREGISTRATION
>
> **Candidate:** M0, directed dynamic-capability transfer challenger
>
> **Execution mode:** SHADOW / developer-only
>
> **Normal product authority:** `BENCHMARK_V0` unchanged
>
> **Real-history fitting at freeze:** NONE

This document freezes the first N-BIO-7F numeric transfer challenger before any M0 fit is made to installed real history.

The governing principle remains:

```text
local capability stays local
        +
versioned directed relationship
        +
strict causal chronology
        +
destination-only comparison
```

M0 is not an equipment converter. It does not create `L_true`, a universal kilogram coordinate, a global user-strength scalar, an e1RM bridge, or a transitive equipment graph.

Any material behaviour change listed in section 15 requires a new immutable candidate/config identity. Negative results for this frozen candidate must survive.

---

## 1. Source basis and synthesis boundary

### Source-derived constraints

This specification preserves the accepted source architecture:

- N0 is the destination-only champion.
- M0 is one directed source to destination relationship.
- source capability is consumed through the typed 7F-C posterior boundary rather than reconstructed from raw working sets;
- destination capability remains the accepted Candidate-v2 dynamic profile-local frontier;
- the selected Candidate-v2 numerical backend is Adaptive Sparse;
- working sets are lower-bound demonstrations, not assumed maxima;
- direct destination evidence remains local and immutable;
- chronology is prequential and future evidence is forbidden;
- source uncertainty must propagate through transfer;
- same-session evidence is not independent calibration truth;
- no hard destination-observation count turns transfer on or off;
- multiple sources are not assumed independent;
- no-transfer is a valid result.

### N-BIO engineering synthesis frozen here

The following are modelling/numerical choices made by N-BIO before real-history M0 fitting. They are **not** physiological facts or published equipment-conversion coefficients:

- the centred source-covariate construction;
- `beta ~ Normal(0, 0.35^2)`;
- fixed 7-point Gauss-Hermite quadrature for beta;
- the deterministic 17-node source-posterior coreset budget;
- the `1e-12` between-session source-anchor variance degeneracy floor;
- likelihood-ratio reweighting of the frozen N0 destination posterior.

The coreset size is a numerical approximation budget, not an evidence-maturity threshold.

---

## 2. Frozen upstream destination champion N0

M0 is nested around the accepted dynamic Candidate-v2 destination model. It does not replace or refit a different destination capability family.

For destination observation `i` in independent destination session `h`:

```text
y_i = log(resistance_kg_i)
x_i = log(repetitions_i / r_D)
z_h = destination independent-session ordinal
```

N0 is:

```text
y_i = c + g z_h - b x_i - u_i + epsilon_i
u_i ~ HalfNormal(sigma_u)
epsilon_i ~ StudentT(df = 5, scale = sigma_e)
```

The latest selected destination session has `z=0`; older selected sessions are `-1,-2,...`; the next independent destination session is `+1`.

Within each destination session, every observation has weight:

```text
a_i = 1 / observationsInDestinationSession(i)
```

so each independent destination session contributes equal total likelihood weight.

### Exact accepted Candidate-v2 mathematical identity

```text
dynamic_profile_local_frontier|candidate-v2-linear-session-trend-math-v1|y=c+g*z-b*x-u+epsilon;trendCoordinate=latest-selected-session-zero-older-negative-next-plus-one-v1;trendPrior=normal(0,0.04);trendUnlock=3;slopePrior=lognormal(0.16,0.55);slack=half_normal(0.12,0.55);noise=student_t_df_5.0;noisePrior=0.05,0.45;sessionWeight=equal_total_weight_per_session_v1;window=12;evidence=6e9d3c2b521daf84d7ca7a8ef9e4abdb3d3e85da10a050b84531c158ef72ed55;context=NONE:013bd35f0e7d21f8595c38d55a8aa6533d6a6d251c02428928b286d69181735d
```

### Exact selected Candidate-v2 solver identity

```text
adaptive_sparse_tensor|candidate-v2-adaptive-sparse-v1|kotlin_jvm|true|candidate-v2-base-posterior-mass-pruned-trend-grid-v1|retainedBaseMass=0.9995|minBaseNodes=64|maxBaseNodes=2048|trendPoints=17|trendRadiusSd=4.0
```

M0 consumes the frozen N0 joint weighted posterior nodes produced by that accepted mathematical/solver identity. It must not silently substitute the historical Conditional-Laplace solver or reduce N0 to marginal mean/variance.

---

## 3. Directed edge identity and admissibility

One M0 candidate instance represents exactly one directed edge:

```text
source profile/version/equipment context
        ->
destination profile/version/equipment context
```

`A -> B` and `B -> A` are separate candidate edges.

An edge is admissible only when all of the following are true at the frozen prediction/fitting cutoff:

1. source and destination capability family are exactly `dynamic_resistance`;
2. source and destination are external, positive, mass-dimensional dynamic-resistance coordinates;
3. source and destination laterality/side match exactly;
4. exact source and destination `ExecutionProfileVersion` identities are known;
5. source and destination historical equipment context is resolved for the evidence that produced each capability snapshot;
6. each edge side has one stable known load-accounting meaning for the evidence used by the candidate: `INCLUSIVE` or `ADDED_ONLY`;
7. unknown or mixed load-accounting meaning is inadmissible;
8. `DEVICE_ORDINAL`, bodyweight, assistance and other non-M0 resistance semantics are inadmissible;
9. a versioned **explicit directed source-to-destination relationship description** authorises the edge;
10. name similarity, target-muscle similarity, recruitment similarity or broad equipment-family membership alone cannot authorise the edge.

Local equipment arithmetic may establish what a historical local coordinate meant. It does not prove the personal source-to-destination relationship.

M0 v1 does not implement transitive `A -> B -> C` transfer.

---

## 4. Causal chronology and session pairing

The destination independent session is the atomic prequential event.

For a destination session `h` with first observation time `t_h`:

1. freeze the destination N0 state before `t_h`;
2. construct the source capability snapshot using only source evidence strictly available before `t_h`;
3. exclude source observations that share the destination workout/session identity, even if their recorded timestamp happens to be earlier inside that workout;
4. freeze M0 before any observation in destination session `h` is scored;
5. score the complete held-out destination session;
6. only after scoring may destination/source state be updated for later events.

A source snapshot therefore requires:

```text
source.evidenceThrough < destinationSession.firstObservationTime
```

and must not contain the destination session itself.

This deliberately refuses same-session A/B observations as clean calibration truth in M0 v1. They can be studied by a separately preregistered candidate later.

### Unpaired destination sessions

A destination training session without an admissible prior source snapshot is **not deleted** from N0.

For M0 likelihood-ratio learning it contributes:

```text
Delta_h = 0
```

and remains fully represented in the frozen destination N0 posterior.

Only destination sessions with an admissible prior source snapshot form source-relationship pairs.

---

## 5. Source posterior anchor

For paired destination session `h`, source posterior node `k` carries the accepted joint dynamic posterior tuple:

```text
(logFrontierAtLatestSession_hk,
 slope_hk,
 frontierTrend_hk,
 slackScale_hk,
 noiseScale_hk,
 posteriorWeight_hk)
```

Let:

```text
r_D   = destination N0 reference repetitions
r_S,h = source snapshot reference repetitions
```

The source node is queried at the destination reference-repetition count without creating a universal load scale:

```text
S_hk = logFrontierAtLatestSession_hk
       - slope_hk * ln(r_D / r_S,h)
```

This is still a **source-profile-local log capability** evaluated at the same repetition count. It is not destination kg and is never copied into the destination coordinate.

The query is admissible only when `r_D` lies inside the source snapshot's observed repetition domain. M0 does not extrapolate the source anchor beyond that domain.

### No synthetic source time projection

`logFrontierAtLatestSession_hk` is the source capability at the source snapshot's own latest independent source session.

M0 does not apply `frontierTrend * +1` merely because a destination session occurs. Source trend advances only through legitimate source independent-session evidence and the upstream source model's own chronology.

---

## 6. Centred source covariate

For each paired destination session:

```text
mu_h = sum_k w_hk * S_hk
```

Let `H` be the paired destination sessions available in the frozen training history. Define one fixed training-history centre:

```text
m_S = (1 / |H|) * sum_h mu_h
```

Every paired source node becomes:

```text
q_hk = S_hk - m_S
```

The centre is frozen from training pairs only. It is never recomputed using a held-out destination outcome or the current source snapshot used for a future prediction.

### Identifiability fail-close

M0 is unavailable when the paired expected source anchors contain no finite between-session variation:

```text
Var_h(mu_h) <= 1e-12
```

With no pair, or only one effective/constant paired anchor, the variance condition naturally fails.

This is a numerical/information degeneracy check, not an arbitrary observation-count promotion threshold.

---

## 7. Frozen M0 observation model

For destination observation `i` belonging to paired destination session `h`, destination N0 posterior node `j`, source node `k`, and relationship coefficient `beta`:

```text
y_i = c_j + g_j z_h - b_j x_i
      + beta q_hk
      - u_i + epsilon_i

u_i ~ HalfNormal(sigma_u,j)
epsilon_i ~ StudentT(df = 5, scale = sigma_e,j)
```

The source term is a covariate on the **destination log-frontier**. It does not convert source kg into destination kg.

When `beta = 0`, the M0 observation model is exactly N0.

No new destination slack/noise family is introduced. M0 inherits the N0 HalfNormal slack and Student-t(5) ordinary performance noise.

---

## 8. Source uncertainty propagation inside the likelihood

For one destination N0 node `j` and beta node `m`, source uncertainty is marginalised inside the destination likelihood:

```text
p_M0(y_i | theta_j, beta_m)
    = sum_k w_hk * p_frontier(
          y_i |
          c_j + g_j z_h - b_j x_i + beta_m q_hk,
          sigma_u,j,
          sigma_e,j
      )
```

where `p_frontier` is the same HalfNormal-slack plus Student-t-noise observation density used by N0.

The corresponding N0 likelihood is:

```text
p_N0(y_i | theta_j)
    = p_frontier(
          y_i |
          c_j + g_j z_h - b_j x_i,
          sigma_u,j,
          sigma_e,j
      )
```

Independent marginal source quantiles must not be substituted for the joint source nodes.

---

## 9. Frozen beta prior and quadrature

The directed relationship coefficient is regularised around no transfer:

```text
beta ~ Normal(mean = 0, sd = 0.35)
```

This is an N-BIO engineering prior selected before real-history M0 fitting. It is not an empirical equipment coefficient.

M0 v1 integrates beta with exactly seven fixed Gauss-Hermite standard-normal mass points.

### Beta nodes

```text
-1.3126539012040097
-0.8283657937570895
-0.40404188815898884
 0
 0.40404188815898884
 0.8283657937570895
 1.3126539012040097
```

### Normalised beta weights

```text
0.0005482688559722182
0.030757123967586515
0.24012317860501273
0.45714285714285713
0.24012317860501273
0.030757123967586515
0.0005482688559722182
```

The weights sum to exactly `1.0` at the stored precision above.

---

## 10. Frozen source-posterior coreset

M0 v1 has a deterministic source-posterior integration budget of `K = 17` joint nodes per source snapshot.

If the source snapshot contains at most 17 posterior nodes, retain all nodes and renormalise their existing posterior weights.

Otherwise:

1. compute `S_hk` for every original source node;
2. sort complete joint tuples lexicographically by:
   - `sourceAnchor S_hk`;
   - `logFrontierAtLatestSession`;
   - `slope`;
   - `frontierTrend`;
   - `slackScale`;
   - `noiseScale`;
   - stable original node index;
3. form the normalised posterior-weight CDF in that order;
4. for `l = 0..16`, use target cumulative mass:

```text
(l + 0.5) / 17
```

5. choose the first complete source node whose CDF is greater than or equal to that target;
6. when several targets select the same joint node, coalesce it and assign:

```text
posteriorWeight = selectionCount / 17
```

The coreset therefore retains complete upstream joint tuples. It does not construct independent marginal quantiles.

The source anchor is first in the deterministic ordering because it is the source-posterior transform actually consumed by M0. The remaining fields keep replay ordering deterministic and preserve the selected upstream joint tuple.

Changing `K`, the sort key, target rule or coalescing rule creates a new candidate/config identity.

---

## 11. Posterior construction by N0 likelihood-ratio reweighting

Let destination N0 posterior node `j` have weight `w_j`, and beta quadrature node `m` have prior mass `pi_m`.

For paired destination observations only:

```text
Delta_jm
  = sum_i a_i * [
        log p_M0(y_i | theta_j, beta_m)
        - log p_N0(y_i | theta_j)
    ]
```

with:

```text
a_i = 1 / observationsInDestinationSession(i)
```

Unpaired destination sessions contribute zero likelihood-ratio increment and remain represented through the N0 posterior `w_j`.

The frozen M0 posterior over destination-node/beta-node pairs is:

```text
W_jm proportional to w_j * pi_m * exp(Delta_jm)
```

Normalisation must use log-sum-exp.

### Exact nested no-transfer state

At `beta = 0`:

```text
p_M0 = sum_k w_hk * p_N0 = p_N0
Delta_j0 = 0
```

because source-node weights sum to one.

Therefore beta zero reproduces the exact frozen N0 destination model for every destination node. No hidden fallback or outcome-count switch is required to define no transfer.

---

## 12. Prediction

For a future destination independent session, freeze:

- the current destination N0 posterior;
- the fitted M0 destination-node/beta-node posterior;
- one admissible current source capability snapshot using only evidence available at the prediction cutoff;
- the training-history centre `m_S` from the fitted edge.

For current source coreset node `k`:

```text
S_k = logFrontierLatest_k - slope_k * ln(r_D / r_S)
q_k = S_k - m_S
```

For destination query repetition count `r`:

```text
log F_D,jmk(r, next)
  = c_j + g_j
    - b_j * ln(r / r_D)
    + beta_m * q_k
```

Predictive mixture weight is:

```text
W_jm * w_source,k
```

Observation prediction then applies the same destination HalfNormal slack and Student-t(5) noise as N0.

### Prediction domains

M0 fails closed when:

- `r_D` lies outside the current source snapshot observed rep domain; or
- destination query `r` lies outside the destination N0 observed rep domain.

This no-extrapolation rule is specific to M0 v1. N0 remains separately available under its own accepted upstream extrapolation behaviour.

---

## 13. Multiple sources, directionality and no-transfer comparison

One frozen M0 candidate instance consumes one source edge only.

For several potential sources:

- fit/score each directed edge independently; or
- later apply a deterministic source-selection policy frozen before the destination outcome.

M0 v1 does **not**:

- average several source posteriors;
- precision-combine them as independent;
- infer source-source independence;
- compose A->B and B->C into A->C;
- infer B->A from A->B.

N0 must always be generated and scored separately for every evaluable destination event. M0 is promoted nowhere by this specification.

Negative transfer means the frozen M0 candidate predicts worse than N0 under the preregistered prequential score. That result is retained rather than hidden by post-outcome gating.

---

## 14. Corrections, invalidation and replay provenance

Every M0 fit/prediction must retain enough immutable provenance to replay the exact edge, including at least:

- source and destination execution-profile IDs and version IDs;
- source and destination equipment-context fingerprints;
- source and destination load-accounting meanings;
- side/laterality;
- capability family;
- explicit directed relationship descriptor/policy identity;
- source and destination causal cutoffs;
- source capability IDs, run IDs and model-config IDs;
- destination N0 capability/run/config IDs;
- exact selected source and destination observation/session IDs;
- source coreset algorithm/version and selected original nodes;
- beta prior/quadrature identity;
- equipment/load correction dependency IDs;
- M0 model-config ID and mathematical/solver identities.

Canonical performance rows remain untouched.

A canonical equipment binding, equipment fact or load-semantics correction invalidates dependent derived M0 state through the existing dependency-scoped invalidation path. It does not delete unrelated raw evidence.

Preference changes cannot rewrite historical M0 evidence context.

---

## 15. Immutable M0 identities

### Mathematical identity

Family:

```text
directed_dynamic_capability_transfer
```

Semantic version:

```text
m0-source-covariate-math-v1
```

Exact identity:

```text
directed_dynamic_capability_transfer|m0-source-covariate-math-v1|destinationBase=dynamic_profile_local_frontier|candidate-v2-linear-session-trend-math-v1|y=c+g*z-b*x-u+epsilon;trendCoordinate=latest-selected-session-zero-older-negative-next-plus-one-v1;trendPrior=normal(0,0.04);trendUnlock=3;slopePrior=lognormal(0.16,0.55);slack=half_normal(0.12,0.55);noise=student_t_df_5.0;noisePrior=0.05,0.45;sessionWeight=equal_total_weight_per_session_v1;window=12;evidence=6e9d3c2b521daf84d7ca7a8ef9e4abdb3d3e85da10a050b84531c158ef72ed55;context=NONE:013bd35f0e7d21f8595c38d55a8aa6533d6a6d251c02428928b286d69181735d;equation=y=c+g*z-b*x+beta*q_source-u+epsilon;sourceAnchor=S=logFrontierLatest-slope*ln(rD/rS);sourceCenter=mean_paired_destination_sessions(E_source[S]);sourceUncertainty=joint_source_node_mixture_inside_destination_likelihood;betaPrior=normal(0,0.35);betaQuadrature=gauss_hermite_7_fixed;n0Reuse=frozen_destination_n0_posterior_likelihood_ratio_reweight;sessionWeight=equal_total_weight_per_session_v1;chronology=destination_session_atomic_source_prior_sessions_only;repDomain=no_extrapolation_source_anchor_or_m0_destination_prediction
```

### Numerical solver identity

```text
sequential_tensor|n-bio-7f-m0-n0-posterior-gh7-source-coreset17-v1|kotlin_jvm|true|n0-posterior-likelihood-ratio|beta=gauss-hermite-7|source=joint-systematic-weighted-cdf-midpoint-k17-v1|logsumexp=true
```

### ModelConfig descriptor

```text
component=translation
modelFamily=directed_dynamic_capability_transfer
modelName=destination_frontier_source_covariate
semanticVersion=n-bio-7f-m0-directed-source-covariate-v1
configSchemaVersion=1
```

### Expected immutable ModelConfig ID

Using `ModelConfigDefinition.create` canonical sorting/escaping/fingerprinting rules:

```text
modelcfg_sha256_f4fa3fb165873df5407da1daefcb9bce3656caa9586ecefe3b35a0ca42c79961
```

Implementation must reproduce this exact ID from the parameter map below before M0 may fit real history.

### Exact parameter map

```text
candidateRole=directed_source_challenger
capabilityFamily=dynamic_resistance
translationMathematicalIdentity=<exact mathematical identity above>
translationSolverIdentity=sequential_tensor|n-bio-7f-m0-n0-posterior-gh7-source-coreset17-v1|kotlin_jvm|true|n0-posterior-likelihood-ratio|beta=gauss-hermite-7|source=joint-systematic-weighted-cdf-midpoint-k17-v1|logsumexp=true
destinationBaseMathematicalIdentity=<exact Candidate-v2 mathematical identity in section 2>
destinationBaseModelVersion=n-bio-7b2-half-normal-student-t-session-trend-frontier-v2
destinationBaseSolverIdentity=adaptive_sparse_tensor|candidate-v2-adaptive-sparse-v1|kotlin_jvm|true|candidate-v2-base-posterior-mass-pruned-trend-grid-v1|retainedBaseMass=0.9995|minBaseNodes=64|maxBaseNodes=2048|trendPoints=17|trendRadiusSd=4.0
equation=y=c+g*z-b*x+beta*q_source-u+epsilon
slackDistribution=half_normal_destination_n0
noiseDistribution=student_t_df_5_destination_n0
sessionWeighting=equal_independent_session;observation_weight=1/observations_in_destination_session
sourceAnchor=S=logFrontierLatest-slope*ln(destinationReferenceReps/sourceReferenceReps)
sourceCenter=mean_over_paired_destination_sessions_of_source_posterior_expected_anchor
sourceCenterFreeze=fit_time_training_pairs_only;never_recenter_on_held_out_or_prediction_source_snapshot
sourceCovariate=q=S-sourceCenter
sourceSessionPairing=paired_when_admissible_prior_source_snapshot_exists;unpaired_destination_session_delta_zero
sourceSnapshotCutoff=strictly_before_destination_session_first_observation;exclude_same_session_id
sourceTrendProjection=none_without_source_independent_session
betaPrior=normal(mean=0,sd=0.35)
betaQuadratureRule=gauss_hermite_7_standard_normal_fixed_v1
betaQuadratureNodes=-1.3126539012040097,-0.8283657937570895,-0.40404188815898884,0,0.40404188815898884,0.8283657937570895,1.3126539012040097
betaQuadratureWeights=0.0005482688559722182,0.030757123967586515,0.24012317860501273,0.45714285714285713,0.24012317860501273,0.030757123967586515,0.0005482688559722182
sourcePosteriorCoreset=joint_systematic_weighted_cdf_midpoint_k17_v1
sourcePosteriorCoresetSort=sourceAnchor,logFrontierAtLatestSession,slope,frontierTrend,slackScale,noiseScale,stableOriginalIndex
sourcePosteriorCoresetSize=17
sourcePosteriorCoresetWeighting=retain_all_normalised_if_nodes<=17;otherwise_coalesced_selection_count/17
sourceUncertainty=marginalize_joint_source_nodes_inside_destination_likelihood
n0Reuse=frozen_destination_n0_posterior_likelihood_ratio_reweight
nestedNoTransfer=beta_zero_exact_n0
identifiabilityFloor=between_session_source_expected_anchor_variance_gt_1e-12
chronology=destination_session_atomic_freeze;source_prior_sessions_only;score_then_update
sourceRepDomain=no_extrapolation
destinationRepDomain=no_extrapolation_for_m0
laterality=exact_match
equipmentContext=resolved_exact_source_and_destination_history
loadAccounting=stable_known_inclusive_or_added_only_per_edge_side;unknown_or_mixed_inadmissible
resistanceScope=external_mass_dimensional_dynamic_only;device_ordinal_bodyweight_assistance_inadmissible
relationshipAdmissibility=explicit_versioned_directed_relationship_required;no_implicit_name_muscle_or_equipment_family_match
multipleSources=one_directed_edge_per_candidate;no_precision_combination;no_transitivity
predictionSourceSnapshot=current_admissible_source_snapshot_frozen_at_destination_prediction_cutoff
predictionEquation=logF_D=c+g-b*ln(r/rD)+beta*q_source
predictionWeighting=fitted_destination_beta_posterior_x_current_source_joint_nodes
correctionInvalidation=equipment_and_load_semantic_dependencies_invalidate_derived_m0_only
executionMode=shadow_developer_only
productAuthority=benchmark_v0_unchanged
```

### Material changes that require a new identity

At minimum, any change to one of these requires a new immutable candidate/config identity rather than editing M0 v1 in place:

- observation equation;
- destination N0 mathematical or selected solver identity;
- beta prior family or `0.35` SD;
- beta quadrature node count, nodes or weights;
- source anchor definition;
- source-centering definition or freeze rule;
- historical source snapshot cutoff or same-session exclusion;
- treatment of unpaired destination sessions;
- source uncertainty marginalisation;
- source coreset size, ordering, target rule or weighting;
- likelihood-ratio reweighting construction;
- within-session likelihood weighting;
- source/destination repetition-domain rule;
- identifiability floor;
- capability/resistance/laterality admissibility;
- equipment/load-semantics requirements;
- relationship authorisation rule;
- multi-source policy;
- prediction equation or mixture weighting.

---

## 16. Required implementation tests before real-history M0 fitting

Implementation must prove at least:

1. the exact frozen ModelConfig parameter map reproduces `modelcfg_sha256_f4fa3fb165873df5407da1daefcb9bce3656caa9586ecefe3b35a0ca42c79961`;
2. `beta=0` reproduces N0 likelihood/prediction numerically within deterministic floating-point tolerance;
3. future source evidence cannot enter an earlier destination freeze;
4. same-session source evidence is excluded;
5. unpaired destination sessions remain in N0 and add zero M0 likelihood-ratio increment;
6. source posterior node tuples remain joint through coreset selection;
7. coreset replay is deterministic;
8. unknown/mixed load semantics fail closed;
9. unresolved equipment context fails closed;
10. ordinal/bodyweight/assistance evidence cannot enter M0 v1;
11. source-anchor repetition extrapolation fails closed;
12. destination M0 repetition extrapolation fails closed while N0 remains independently available;
13. A->B cannot be reused as B->A;
14. no A->B->C transitive composition exists;
15. multiple sources are not precision-combined;
16. an equipment/load correction invalidates derived M0 dependencies without mutating raw performance;
17. deterministic replay reproduces the same candidate posterior/prediction from the same canonical evidence and frozen identities.

Only after these structural/synthetic tests pass may M0 be fitted to real history for development evaluation.

---

## 17. Evaluation remains preregistered and asymmetric

N0 remains the champion M0 must beat.

Real-history evaluation must be chronological and event-frozen. M0 is not accepted because it can explain the training data or because beta moves away from zero.

It must improve future destination prediction under the later preregistered evaluation surface without unacceptable negative transfer or calibration damage.

A valid outcome is:

```text
NO_USEFUL_TRANSFER
```

Another valid outcome is that M0 helps only some directed edges and fails others.

Those outcomes do not authorise retuning this frozen M0 identity after the fact.
