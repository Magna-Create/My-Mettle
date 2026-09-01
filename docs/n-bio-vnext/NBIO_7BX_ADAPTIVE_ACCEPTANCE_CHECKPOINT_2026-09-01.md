# N-BIO-7B.X adaptive-inference physical checkpoint — 2026-09-01

This remains part of the existing **N-BIO-7B.X adaptive-inference architecture consolidation mission**. It is not 7C, Candidate v3, V8 or a new physiology phase.

## Pre-observability Room14 run

The earlier physical acceptance run completed the full installed-device workflow after the fail-closed numerical projection patch.

Preserved interpretation before the corrected run:

- **Safety / integrity:** PASS.
- **Unstable-projection fail-closed behaviour:** VALIDATED.
- **Adaptive Sparse:** PROMISING development evidence only.
- **Conditional Laplace:** solver-specific numerical instability plus identity-propagation issue under investigation.
- **Replay result:** INVALIDATED BY COMPARATOR BUG because operational runtime telemetry participated in equality.
- **Runtime comparison:** NOT YET CLEANLY INTERPRETABLE because predictive-scoring timing was omitted and run order / thermal conditions were not captured.
- **N-BIO-7B.X closure:** PENDING corrected physical acceptance.

The run was performed on the real Room14 dataset under normal phone thermal constraints. Wall-clock timing was not treated as a clean solver benchmark.

## Bounded correction pass

The correction pass was deliberately limited to acceptance-harness correctness and observability:

- exact mathematical-model and solver-identity propagation;
- deterministic scientific replay equality that excludes runtime / hardware telemetry;
- predictive-scoring and per-stage timing export;
- run-order and best-effort Android thermal telemetry;
- representative bounded Dense sampling across stable, strongly progressing and numerical-stress histories;
- stronger exported Dense fidelity details.

Candidate-v2 mathematics, priors, evidence policy, Adaptive-Sparse algorithm and the fail-closed numerical-domain guard remained frozen. BENCHMARK/product authority, raw evidence, prescription state, Room14, Native backup semantics and normal workout behaviour remained unchanged.

---

# Corrected physical acceptance result

Corrected physical report:

- format: `my-mettle-n-bio-adaptive-inference-acceptance`
- format version: `7`
- generated at: `2026-09-01T11:13:43.476119Z`
- app: `0.1.0-alpha28-dev` / version code `27`
- device: Samsung `SM-S938B`, Android SDK 36
- Room schema: `14`

## Safety and integrity

**PASS.** The corrected physical run reported:

- raw-evidence fingerprint unchanged;
- prescription-state fingerprint unchanged;
- BENCHMARK_V0 authority unchanged;
- Native backup/restore round-trip passed;
- candidate rows matched after restore;
- foreign keys clean;
- scientific evaluation non-vacuous;
- product authority unchanged;
- normal workout behaviour unchanged;
- N-BIO-7C not started.

The previously validated Conditional-Laplace numerical-domain guard remained active and failed closed rather than manufacturing a finite answer.

## Identity and deterministic replay correction

The earlier solver-identity propagation defect is resolved. The corrected run contains no `Fit mathematical identity does not match the selected Candidate-v2 solver` failures.

Scientific replay comparison now behaves as intended:

- **Adaptive Sparse:** persist/reload equivalent on all 21 profile/sides with current eligible evidence; full deterministic replay equivalent on all 21/21.
- **Dense reference:** persist/reload and replay equivalent on all three representative sampled profiles.
- **Conditional Laplace:** replay/persistence is equivalent where a valid next-session projection exists; six profile/sides correctly return unavailable because the validated numerical-domain guard rejects the projection.

Operational runtime/worker/hardware telemetry therefore no longer contaminates scientific deterministic equality.

## Representative Dense fidelity

Dense remains the high-fidelity deterministic reference. Three current posteriors were sampled deliberately:

### Strongly progressing — Bayesian Cable Curls

Adaptive Sparse vs Dense:

- next-session p05/p50/p95: exact on the exported quantiles;
- trend-positive probability absolute error: `0.000861`;
- maximum standardised marginal W1: `0.2815`;
- maximum covariance/correlation-scale error: `0.0678`.

Conditional Laplace is not acceptable here:

- trend-positive probability error: `0.9714`;
- next-session median relative error: `1.0`;
- next-session predictive resistance collapsed to approximately zero;
- projection persistence/replay failed closed at the configured numerical domain.

### Stable — Incline Bench Press

Adaptive Sparse vs Dense:

- next-session p05/p50/p95: exact on the exported quantiles;
- trend-positive probability absolute error: `0.000115`;
- maximum standardised marginal W1: `0.1558`;
- maximum covariance/correlation-scale error: `0.00964`.

Conditional Laplace is numerically valid on this profile and reasonably close on the next-session median, but is less faithful than Adaptive Sparse:

- trend-positive probability absolute error: `0.1453`;
- next-session median relative error: `0.00776`;
- maximum standardised marginal W1: `0.2587`;
- maximum covariance/correlation-scale error: `0.1287`.

### Previously numerically difficult — Neutral-Grip Lat Pulldown

Adaptive Sparse vs Dense:

- next-session p05: exact on the exported quantile;
- next-session p50: exact on the exported quantile;
- next-session p95: `66.74 kg` vs Dense `72.15 kg` (about `7.5%` low);
- trend-positive probability absolute error: `0.00539`;
- maximum standardised marginal W1: `0.4487`;
- maximum covariance/correlation-scale error: `0.2625`.

This is the most important remaining approximation discrepancy: Adaptive Sparse slightly under-represents the upper tail/dependence on the difficult progressing history. It is retained as a known approximation limitation, not hidden as exact equality.

Conditional Laplace remains unavailable on this history under the numerical guard; the posterior-fidelity calculation also fails closed because the candidate projection is not a valid finite predictive distribution.

## Retrospective predictive evidence

Across the 16 profiles with evaluable held-out dynamic-resistance predictions, derived from the corrected report:

- frozen Candidate-v1 weighted CRPS: about `0.15387`;
- Adaptive Sparse weighted CRPS: about `0.14422`;
- relative weighted CRPS improvement: about **6.27%**;
- Adaptive Sparse produced all `126/126` evaluable held-out predictions with zero model-failure rate;
- weighted interval coverage improved from about `59.5%` to `69.8%`;
- weighted WIS improved from about `0.11566` to `0.10794`;
- weighted mean log predictive density improved from about `-0.471` to `-0.290`;
- weighted signed log-residual bias reduced from about `0.0863` to `0.0635`.

At profile level, treating numerical-noise-scale changes as ties rather than wins, Adaptive Sparse materially improves CRPS on 10/16 evaluable profiles, is effectively tied on 4/16, and is materially worse on 2/16. This is stronger and more honest than the earlier superficial `13/16` positive-sign count.

Conditional Laplace is not competitive as the Candidate-v2 production solver on this evidence. It has retrospective numerical failures on real profiles and severe outlier predictive degradation, including order-of-magnitude CRPS failures on some histories. Its fail-closed behaviour is correct; the approximation itself is not sufficiently robust.

## Runtime and thermal interpretation

Total physical acceptance wall time was about `1,224,647 ms` (`20m 24.6s`). The new decomposition shows the dominant cost clearly:

- historical predictive scoring: `1,163,439 ms` (~95% of total wall time);
- historical frozen-v1 + Candidate-v2 fit runtime: only about `7.17 s` combined;
- current Candidate-v2 fitting: about `4.17 s` total, including Dense samples;
- persistence/reload: `9.49 s`;
- deterministic replay: `7.81 s`;
- posterior fidelity: `7.35 s`;
- generic solver-substrate benchmark: `22.29 s`;
- Native backup round-trip: `0.44 s`.

Therefore the physical evidence does **not** justify a native/GPU/NPU rewrite of Candidate-v2 fitting in this mission. The developer acceptance harness is dominated by repeated predictive scoring, not the chosen fit solver. Any later performance work should first remove/reuse redundant predictive-scoring work or profile that scoring kernel directly.

Thermal telemetry was useful rather than alarming:

- run started at thermal status `NONE`, headroom about `0.567`;
- most of the run remained `NONE`, generally around `0.71–0.78` headroom at sampled boundaries;
- the final numerical-stress profile and substrate benchmark reached `LIGHT`, around `0.82–0.83` headroom;
- power-save mode remained off.

The late Dense samples therefore have mild thermal/run-order confounding for runtime interpretation, but this does not affect the scientific posterior-fidelity comparison itself.

The generic solver-substrate benchmark still supports sparse deterministic inference as a useful architecture substrate: it retained 110 nodes, matched Dense level/drift quantiles on the fixture, showed very small mean/covariance error, and was roughly 5x faster than the generic dense sequential fixture. Sigma-point inference was much faster but is not a Candidate-v2 adapter and is not promoted by this benchmark alone.

---

# N-BIO-7B.X solver verdict

## Dense deterministic tensor

**Status: RETAIN AS HIGH-FIDELITY REFERENCE / ORACLE.**

Dense remains necessary for regression, approximation validation, difficult-history sampling and future solver development. It is not selected as the ordinary Candidate-v2 device solver because the sparse representation materially reduces work while preserving the scientifically important current posterior sufficiently well on the representative physical sample.

## Adaptive Sparse

**Status: SELECTED CANDIDATE-v2 INFERENCE REPRESENTATION/BACKEND FOR FORWARD DEVELOPMENT.**

Reasons:

- same Candidate-v2 mathematics as Dense;
- exact mathematical/solver identity persistence;
- 21/21 current eligible profile persist/reload and replay equivalence;
- no numerical failures in the corrected Room14 run;
- material end-to-end efficiency advantage over Dense on representative current fits;
- representative Dense fidelity is strong on centre, trend direction and predictions, with one explicitly recorded upper-tail limitation;
- retrospective predictive metrics are directionally better overall without hiding profile-level losses.

This is a **solver/backend selection**, not a promotion of Candidate-v2 mathematics to normal workout authority.

## Conditional Laplace

**Status: REJECT AS THE CANDIDATE-v2 PRODUCTION SOLVER; RETAIN ONLY AS A DIAGNOSTIC/RESEARCH CHALLENGER IF USEFUL.**

The identity bug is fixed, but the corrected physical run proves that the solver-specific approximation can still produce extreme or invalid trajectory projections on ordinary real histories. The fail-closed guard is doing the right thing; the approximation is not robust enough to earn production use for Candidate-v2.

No attempt is authorised here to redesign Laplace mathematics or replace it with Candidate v3.

---

# Mission closure

**N-BIO-7B.X Adaptive Inference Architecture Consolidation: COMPLETE.**

Closure means:

- Candidate-v1 remains frozen/rejected and reproducible;
- Candidate-v2 has an honest development verdict;
- mathematical model and inference backend identities are explicitly separate;
- Dense remains the high-fidelity reference;
- Adaptive Sparse is the selected Candidate-v2 inference backend for forward development;
- Conditional Laplace is not selected for production use;
- low-rank and sigma-point results remain measured architecture evidence, not automatic promotions;
- replay, persistence, Room14 backup and raw/prescription integrity passed physically;
- runtime/thermal observability now explains the acceptance bottleneck;
- no unsupported hardware-acceleration claim is made;
- BENCHMARK_V0 remains product authority;
- normal workout behaviour is unchanged;
- Candidate-v2 still requires fresh future/prequential evidence before any product-authority promotion;
- N-BIO-7C has **not** started.

The next roadmap phase may begin only under its own explicit instruction. This closure does not authorise 7C, Candidate v3, new physiology, V8 or normal-user behaviour changes.
