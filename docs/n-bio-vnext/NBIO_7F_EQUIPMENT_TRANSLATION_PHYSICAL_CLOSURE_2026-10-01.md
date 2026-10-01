# N-BIO-7F Equipment Translation — Physical Structural Closure

**Date:** 2026-10-01  
**Branch:** `agent/n-bio-vnext-inference`  
**Physical replay branch HEAD:** `c4a8d524eebf9ff3b124844998eaf50f37e57bda`  
**Exact green runtime implementation checkpoint:** `5a98bf6188fe04c0ca6c0ea5bf55eed61a49cb4d`  
**Android CI:** run 35863074752 — SUCCESS  
**Room schema:** 18  
**Final verdict:** **N-BIO-7F STRUCTURALLY CLOSED — EMPIRICAL EQUIPMENT-TRANSFER ACCURACY PENDING (PD-004 OPEN)**

This document closes the structural N-BIO-7F mission defined by `NBIO_7F_EQUIPMENT_TRANSLATION_CONTRACT.md`. It does not promote transfer models, infer missing equipment history or claim real-history equipment-transfer accuracy.

## 1. Scope closed

7F structurally establishes canonical equipment identity, versioned equipment facts/provenance, preferred-versus-actual equipment separation, session actual-use binding plus observation override, orthogonal complete/inclusive versus added-only load semantics, historical as-of reconstruction, exact local interpretation only where semantics permit, append-only/auditable corrections, a typed capability-transfer boundary, frozen N0/M0 identities, deterministic source selection/replay, derived-state persistence/delete/replay, causal prequential scoring, exact-edge negative-transfer aggregation, developer installed-history evaluation, Native backup/restore and exact N0 observation-level diagnostics.

Normal workout behaviour and `BENCHMARK_V0` authority remain unchanged.

## 2. Schema and migration state

7F began from Room15. Source audit proved Room15 had no honest owner for stable physical-equipment identity, versioned equipment facts, preferred-versus-actual equipment separation or historical load-value semantics. The implementation advanced additively through the 7F persistence work to Room18.

Legacy history was not backfilled with guessed equipment identity or load semantics. Absence remains the canonical unknown state.

Canonical ownership covers equipment instances, versioned facts/provenance, preference history, session actual-use binding, observation override, observation load semantics and correction/audit history. Learned transfer/prediction state remains derived, deletable and replayable.

Generated Room18 schema verification plus dedicated canonical Native backup/replay proof passed at the exact runtime implementation checkpoint.

## 3. Frozen candidate/config identities

### N0 — destination-only/no-transfer baseline

Mathematical family: `dynamic_profile_local_frontier|candidate-v2-linear-session-trend-math-v1`

Solver: `adaptive_sparse_tensor|candidate-v2-adaptive-sparse-v1`

Frozen V2 evidence fingerprint: `6e9d3c2b521daf84d7ca7a8ef9e4abdb3d3e85da10a050b84531c158ef72ed55`

Corrected V3 historical availability is used only to decide what was knowable at each historical freeze; it does not replace frozen N0 evidence admissibility.

### M0 — directed source-covariate challenger

Model config: `modelcfg_sha256_f4fa3fb165873df5407da1daefcb9bce3656caa9586ecefe3b35a0ca42c79961`

Mathematical family: `directed_dynamic_capability_transfer|m0-source-covariate-math-v1`

Solver: `sequential_tensor|n-bio-7f-m0-n0-posterior-gh7-source-coreset17-v1`

Scoring protocol: `n-bio-7f-n0-m0-prequential-continuous-v1`

Aggregation protocol: `n-bio-7f-n0-m0-exact-edge-aggregate-v1`

No candidate identity, prior, likelihood, source-selection policy, no-transfer nesting rule or promotion threshold was changed after installed-history inspection.

## 4. Structural validation

The committed validation series proves additive migration with no guessed legacy semantics; free weights as first-class equipment; preference/actual-use separation; override precedence; historical as-of fact selection; exact implement-mass arithmetic only where authorised; fail-closed unknown mechanics/load accounting; no ordinal-to-kg reinterpretation; raw evidence preservation; explicit directional relationships; capability-family gating; source uncertainty propagation; exact no-transfer nesting; deterministic coreset/beta quadrature and source selection; no silent correlated-source precision pooling; destination-session atomic chronology; M0 domain refusal without removing N0; proper-score/PIT/coverage diagnostics; exact-edge/session harm diagnostics; derived-state deletion/replay/invalidation; and Native backup/restore.

Exact runtime checkpoint `5a98bf6188fe04c0ca6c0ea5bf55eed61a49cb4d` passed Android CI run 35863074752, including `:app:testDebugUnitTest :app:assembleDebug`, instrumentation compilation, lint, Room18 schema verification and canonical Native backup/replay proof.

The physical replay was run after the user built branch HEAD `c4a8d524eebf9ff3b124844998eaf50f37e57bda`. That HEAD differs from the exact green runtime checkpoint only by two documentation commits. The private JSON itself does not embed a source SHA.

## 5. Final physical installed-history evidence

Private report: `my-mettle-n-bio-7f-installed-history-1790860887.json`  
Generated: `2026-10-01T13:21:19.435313Z`  
Format version: 2  
Size: 424552 bytes  
SHA-256: `d06255406fe23a28a725bb3dafc6757282d75b71952ea05d44b166c7d15223c4`

The full personal export remains outside the public repository.

### Integrity PASS

- raw evidence before/after: `db01d423019d5eccec5254d4b82dfc1910f0736833394afa321b5458b12f22bf`;
- prescription before/after: `acaa58bee3e00fcae12abe6556f68db0c6147a5ced404f6f0c5843ecbd6bed12`;
- benchmark run before/after: `inference_run_70b1828d-179f-494b-98b4-92062455560d`;
- `BENCHMARK_V0` authority unchanged.

### N0 availability

- 112 destination events;
- 76 available (67.86%);
- 173 held-out observations scored;
- zero numerical failures;
- 25 `NO_PRIOR_DESTINATION_EVIDENCE`;
- 11 `NO_ELIGIBLE_HELD_OUT_EVIDENCE`;
- held-out exclusions: 21 `missing_body_mass`, one `warm_up_excluded`;
- destination-training exclusions: none.

### Exact N0 diagnostics

Overall observation-weighted NLS = 0.4486658546, log-CRPS = 0.1531903030, log-WIS = 0.1191754877, 90% coverage = 0.7109826590, mean interval log width = 0.4844452460, signed log residual = +0.0641206788.

PIT low/middle/high thirds = 40 / 52 / 81; mean PIT = 0.6062724549. The nominal 90% interval covers only about 71.1% of this development stream and PIT is high-tail skewed. Frozen N0 is visibly imperfect/overconfident here; structural closure does not reinterpret that as calibration success.

Inside the historical training repetition domain: 137 observations, NLS 0.1866065261, coverage 74.45%, mean PIT 0.5855621875.

Outside the historical training repetition domain: 36 observations, NLS 1.4459471881, coverage 58.33%, mean PIT 0.6850865278.

| Selected prior independent sessions | Observations | Mean NLS | 90% coverage | Mean PIT |
| --- | ---: | ---: | ---: | ---: |
| 1 | 48 | 1.70898 | 60.4% | 0.66085 |
| 2 | 44 | 0.84296 | 52.3% | 0.60990 |
| 3 | 35 | -0.39102 | 88.6% | 0.64400 |
| 4-5 | 39 | -0.54487 | 84.6% | 0.52233 |
| 6+ | 7 | -0.93807 | 100% | 0.48828 |

These support-depth and repetition-domain effects remain diagnostics only. No post-hoc hard threshold or N0 retuning is authorised.

The largest shallow-history losses include abrupt local-resistance-coordinate changes. Because canonical historical equipment/load semantics are absent, the replay cannot identify whether those discontinuities reflect equipment changes, load-accounting changes, data-entry differences or genuine capability change. No causal interpretation is authorised.

## 6. Real-history transfer status

Canonical equipment-history coverage is exactly zero: 0 equipment instances, 0 fact versions, 0 session actual-equipment bindings, 0 observation overrides and 0 observation load-semantics rows. Therefore 0 N0 destination contexts are M0-ready.

The harness also has no explicit versioned directed 7F relationship registry. Accordingly:

- M0 relationship descriptors: 0;
- M0 available destination events: 0;
- M0 scored event edges: 0;
- all 76 N0-available events: `NO_EXPLICIT_RELATIONSHIP`;
- N1/M1/M2: `NOT_EVALUATED_REAL_HISTORY`.

This is the correct fail-closed result. Retrospectively assigning today's equipment assumptions would violate chronology and fabricate evidence.

No real-history negative-transfer statistic exists because no M0 edge was causally evaluable. The negative-transfer machinery itself is structurally validated synthetically.

## 7. Runtime / memory

Physical format-v2 evaluator:

- runtime: 130106 ms (~2m 10.1s);
- heap before: 21826560 bytes;
- heap after: 43920144 bytes;
- peak observed heap: 48958224 bytes (~46.69 MiB).

This is a developer acceptance/replay action, not an interactive workout-latency claim.

## 8. Empirical debt and quarantine

PD-004 remains OPEN. Real-history transfer claims require prospective stable equipment-instance identity, actual-use bindings/overrides, load-accounting semantics, time-valid facts, repeated independent sessions and explicit versioned directed relationships frozen before confirmatory outcomes.

Until then M0/N1/M1/M2 remain SHADOW/development-only; missing historical truth stays missing; relationships are not inferred from labels/muscles/equipment family; N-BIO-8 may not consume 7F transfer outputs as validated prescription truth; and `BENCHMARK_V0` remains normal-product authority.

## 9. Final structural verdict

```text
N-BIO-7F STRUCTURALLY CLOSED
EMPIRICAL EQUIPMENT-TRANSFER ACCURACY PENDING
PD-004 OPEN
BENCHMARK_V0 REMAINS NORMAL-PRODUCT AUTHORITY
```

Later phases may use the versioned equipment/history ownership model, local interpretation boundary, typed transfer interfaces, replay/invalidation infrastructure and developer diagnostics. They may not treat M0 as human-validated transfer or use it as normal prescription authority.

Do not reopen 7F merely to manufacture a transfer result from legacy history. Revisit PD-004 only when prospective evidence can make a frozen directed relationship genuinely testable.
