# N-BIO-3 — independent targets and generated prescriptions

## Scope

N-BIO-3 separates programme/session intent from the exercises used to satisfy it. Room is now
version 6 with the same intentional destructive-development policy used by the biological
foundation: Native is not yet the authoritative workout store and Lite Legacy remains the live
migration source.

## Target persistence

The new target path is:

```text
programme_target
        ↓ snapshot at session resolution
session_target
        ↓ many-to-many binding
session_exercise_target ← session_exercise
```

A target owns a stable muscle-segment ID, priority, optional desired stimulus and provenance. It
does not own an exercise. `RoutineSlotEntity` temporarily remains as a pinned exercise assignment
for the current N2 workout UI, but no longer contains or supplies a planned load.

Lite Legacy had no independent target model. Import therefore performs one explicit compatibility
projection: PRIME recruitment on pinned routine assignments becomes target intent with source
`legacy-prime-recruitment-projection-v1`. Desired stimulus remains null. This is migration
provenance, not a claim that recruitment and target intent are intrinsically the same.

## Prescription boundary

`domain.training.ExercisePrescription` is generated through `PrescriptionEngine`. It binds:

- an exercise and execution profile;
- the independent target IDs it is resolving;
- sets, rep range, target RIR, load and rest;
- the model version that produced the recommendation.

The N-BIO-3 implementation is deliberately conservative. It carries forward the latest performed
load for the same execution profile and conforms it to that profile's physical load resolution. It
does not perform progression or muscle-state inference. With no prior evidence, prescribed load is
null rather than restored from a routine slot or invented.

`SessionExerciseEntity` now snapshots `executionProfileId`, execution-profile name,
`prescribedLoad`, target RIR and prescription-model version. Completed sessions therefore keep the
recommendation actually made at that time even after later model changes.

## Raw performance evidence

`SetRecordEntity` now has nullable `rir` and `effortSource` fields alongside the existing performed
load, reps, duration, distance, set kind, completion time and notes. N-BIO-3 only makes the evidence
RIR-ready; capture UX and interpretation follow separately.

## Deliberate limits

- Legacy A/B/C anchors still provide temporary dose constraints while N2 remains operational.
- Routine slots still act as pinned exercise assignments.
- N-BIO-3 itself performs no user-state, exercise-translation or progression inference; N-BIO-4
  now provides that downstream scaffold independently.
- Workout modes do not become target/dose/time budgets until N-BIO-5.

N-BIO-4 is implemented in [`N_BIO_USER_STATE_INFERENCE.md`](N_BIO_USER_STATE_INFERENCE.md):
recomputable user-state/inference records now sit downstream of immutable raw evidence without
changing programme intent or historical prescription identity.
