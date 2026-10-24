# N-BIO posterior portfolio visualisation (developer-only)

## Goal

Export the real joint posterior support of the frozen **N0 dynamic-resistance Candidate-v2 Adaptive Sparse** model in a form suitable for Blender point clouds and scientific 2D overlays.

This feature does not implement new fitness intelligence, adjust product prescriptions, claim biological calibration, or change canonical evidence. It runs isolated, in-memory capability fits and streams their results through Android's document-save picker.

## Developer workflow

1. Build and run the `agent/portfolio-posterior-visual-export` branch.
2. Open **Biological developer tools → N-BIO · Portfolio Posterior Visualisation**.
3. Select an execution profile/version **and side**. The first entry is recommended automatically using **independent eligible sessions**, followed by observed repetition-domain width and eligible observation count. Heavy absolute kilogram values do not affect the ranking.
4. Tap **Generate & save posterior JSON**. Choose a location in Android's document picker.
5. Send the JSON to your computer. It contains a set of snapshots that can be imported to Blender or analysed with Python.

Only dynamic/bodyweight-resistance profiles whose historical evidence fits the strict N0 local coordinate contract are offered. No cardio, ordinal machine levels or fabricated historical equipment conversion.

## What "historical snapshots" means

The exporter gathers actual historical completion times for the selected exact execution-profile version and side, selects at most **six evenly spaced session-finalisation cutoffs** (including first and last), and processes each snapshot independently:

1. Exclude sessions not completed at the cutoff.
2. Apply the corrected-Lite V3 **recorded/known-as-of** availability rule, including correction/supersession history.
3. Keep only the selected execution profile/version and side.
4. Apply the frozen N0 resistance-eligibility projection.
5. Fit frozen N0 Candidate-v2 using the existing Adaptive Sparse backend, with **no context inputs**.
6. Stream the **complete joint posterior nodes**, including original normalised probability weights.

A session that was completed historically but not knowable until later does not leak into an earlier snapshot. `NO_ELIGIBLE_EVIDENCE` and `FIT_UNAVAILABLE` are legitimate status results, not silently interpolated art.

The cutoffs are chosen for **visual sampling**. They are not evenly spaced in physical time, and no artificial training sessions are generated. The parameter `frontierTrend` is relative to the model's independent-session coordinate, not calendar days.

## JSON schema v1

Document-level fields include `format`, `formatVersion`, `modelIdentity`, `solverIdentity`, `exerciseLabel`, `executionProfileVersionId`, `side`, `snapshotPolicy` and `historicalAvailabilityPolicy`.

Each `snapshots[]` record has `cutoff`, `historicalSessionIndex`, `eligibleObservations`, `independentSessions`, `excludedObservations`, and `status`.

Resolved snapshots include `referenceRepetitions`, `inferenceHorizon`, `modelConfigId`, `posteriorEffectiveNodeCount`, `posteriorNodeCount`, `frontierMedianAtReferenceKg`, the observed rep/load domain and `nodes`.

Each node is a six-element array:

```json
[logFrontierAtLatestSession, slope, frontierTrend, slackScale, noiseScale, posteriorWeight]
```

- Parameter-space 3D geometry: **X = logFrontierAtLatestSession, Y = slope, Z = frontierTrend**.
- Alternate channels: `slackScale`, `noiseScale`; brightness/size can encode `posteriorWeight`.
- All nodes share a **single normalised probability distribution within that snapshot**; do not renormalise a visual subset without explicitly recording the omitted tail mass.
- To compare snapshots spatially, apply **one coordinate-axis transform computed across all snapshots** rather than recentering every frame independently.
- Using colour/light for mass is a graphical encoding choice, not a statement about objective model confidence or physiological certainty.

The frontier curve represented by a posterior node, at repetition value `r` and independent-session offset `h`, has log-resistance coordinate:

```text
logFrontier(r, h) = logFrontierAtLatestSession
                  + frontierTrend * h
                  - slope * ln(r / referenceRepetitions)
```

Converting the log coordinate back via `exp` gives a **profile-local frontier capability** estimate, **not a predicted chosen working-set load** or a safe prescription. Retain the model's one-sided slack/noise and uncertainty distinctions if making a demonstrated-performance predictive.

## Privacy, performance and authority

- The saved JSON includes the exercise/profile label and exact historical dates: treat it as personal training information before publishing.
- There are no user names, raw workout notes, original observation IDs, biometrics or raw load/repetition pairs in the payload.
- Streams directly to a user-selected Android document instead of serialising the entire node matrix in a ViewModel state object.
- Up to six independent fits may take substantial time. Run explicitly from developer tools, never from normal navigation or a workout.
- The JSON output is a disposable derivative. There are **no Room writes**, `BENCHMARK_V0` remains the workout authority, and candidate numerical fidelity/empirical-calibration debt remain open.
- A finite posterior or visually attractive point cloud is not proof of accurate predictive calibration.

## Verification

A dedicated unit test covers chronological cutoff selection, endpoint retention, duplicate elimination and the absence of invented time points. Android CI still needs to pass before device-level acceptance. On-device inspection should confirm the exported JSON parses, that resolved nodes have nonnegative weights summing to approximately one, and that normal workout/prescription state remains unchanged.
