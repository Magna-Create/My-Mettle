# N-BIO-7G Conditioning Capability — Post-v1 Deferral

> **Status:** DEFERRED TO POST-v1
>
> **Decision date:** 2026-10-01
>
> **Scope:** advanced modality-specific conditioning/cardio capability inference
>
> **Current product priority:** strength training first

## Decision

The original N-BIO roadmap included a dedicated N-BIO-7G conditioning-capability phase with modality-specific probabilistic models such as speed-duration / critical-speed for running and power-duration / critical-power style models for cycling.

That direction remains a valid future research/architecture option, but it is **not required for the first full v1 release**.

For v1, My Mettle is primarily a strength-training product. Cardio is expected to be secondary/supporting activity such as:

- warm-up;
- cool-down;
- cutting / energy-expenditure support;
- general conditioning alongside strength work.

Building a strength-like probabilistic capability stack for cardio before v1 would add substantial modelling, validation and product complexity without being necessary for the primary release goal.

Therefore:

**N-BIO-7G advanced conditioning capability is postponed until after full v1 release.**

## What remains in v1

The existing generic performance and temporal-evidence foundations remain useful and should not be removed.

Keep support for canonical cardio-relevant evidence already present in the platform, including where applicable:

- duration;
- distance;
- speed / pace;
- power;
- cadence;
- incline/grade;
- steps / floors / elevation;
- device-local machine level;
- temporal traces and provenance.

This preserves future compatibility without requiring a dedicated conditioning latent-state model now.

No new N-BIO conditioning model is required for v1 merely because these metrics exist.

## What is explicitly deferred

Post-v1 work may revisit:

- modality-specific duration-conditioned capability;
- running / treadmill speed-duration models;
- critical-speed / D-prime style candidates;
- cycling power-duration / critical-power / W-prime style candidates;
- rowing erg-specific capability;
- device-local stepmill / elliptical capability;
- trace-aware probabilistic inference;
- cardio-specific prequential validation;
- conditioning-specific state persistence;
- richer cardio prescription logic.

The exact post-v1 model should be researched and preregistered at that time rather than frozen now.

## Boundaries that still apply

Deferral does **not** authorise shortcuts such as:

- one universal cardio/fitness score;
- HR-only external capability;
- automatic VO2max inference from weak evidence;
- machine-level-to-SI conversion without calibration;
- cardio minutes / watts / calories converted into hypertrophy-set units;
- using unvalidated cardio inference to drive strength prescriptions.

The existing research remains useful guidance if and when 7G is reopened.

## Why the original research is retained

The original plan and conditioning research are not being deleted or declared wrong.

They are retained because:

1. the architecture may become valuable after v1;
2. the temporal/performance substrate was deliberately designed to make later conditioning work possible;
3. future product priorities may justify full cardio capability inference;
4. preserving the research avoids rediscovering the same design constraints later.

This document changes **implementation priority**, not the historical research record.

## Revisit trigger

Re-open N-BIO-7G after the first full v1 release when one or more of the following is true:

- cardio becomes a first-class product goal rather than supporting activity;
- users need adaptive cardio progression/prescription;
- enough semantically strong cardio history exists to validate a capability model;
- product scope expands toward hybrid strength + endurance training;
- a post-v1 research/design pass explicitly promotes conditioning inference.

At that point, begin with a fresh source audit and targeted research review before freezing any critical-power, critical-speed or other mathematical candidate.

## Roadmap effect

For the pre-v1 roadmap:

- N-BIO-7F remains structurally closed under PD-004 quarantine;
- N-BIO-7G conditioning inference is skipped/deferred;
- N-BIO-7H is not required merely to close a 7G that was not implemented;
- subsequent pre-v1 work should follow the active roadmap/product gates rather than treating 7G as a blocker.

