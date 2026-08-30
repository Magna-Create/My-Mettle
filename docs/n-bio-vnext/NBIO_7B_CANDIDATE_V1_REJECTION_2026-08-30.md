# N-BIO-7B Candidate-v1 empirical rejection — 2026-08-30

Status: `REJECTED_EMPIRICAL_CALIBRATION_V1`

This is a privacy-bounded aggregate rejection record. The user's raw acceptance JSON is not committed.

The first genuine installed-history evaluation of `n-bio-7b2-half-normal-student-t-frontier-v1` used the frozen Candidate-v1 configuration without same-history retuning. It produced approximately:

- 19 dynamic-resistance profile/version groups;
- 141 eligible observations;
- 44 chronological fits;
- 105 evaluable held-out demonstrations;
- zero model failures;
- predictive coverage about 56.2%;
- 27/105 catastrophic frontier contradictions (about 25.7%);
- coarse PIT counts 23 low / 18 middle / 64 high;
- candidate demonstration-median MAE about 6.90 kg versus about 7.00 kg for the supported BENCHMARK_V0 latest-anchor MAE.

Interpretation: central point accuracy was roughly competitive, but the probabilistic prediction was unacceptably miscalibrated and systematically pessimistic toward later successful demonstrations. Candidate v1 therefore remains frozen as rejected evidence and must not be rescued by changing its priors, noise/slack family, temporal window, weighting or thresholds under the same identity.

Stage 1 of the next 7B pass adds event-level residual, sharpness, CRPS, recent-trend and serial-error diagnostics. The temporal-lag hypothesis remains a hypothesis until that diagnostic is executed on installed history; Candidate v2 is gated on that result.
