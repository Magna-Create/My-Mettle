# N2 — native workout slice

N2 rebuilds the daily workout loop natively before the visual system is customised heavily.

## Material baseline

For this stage the app intentionally stays close to Google's current Material 3 / Material You behaviour, including dynamic system colour on Android 12+. The existing Compose BOM remains the stable dependency baseline. My Mettle-specific styling can change independently later.

## Four-mode policy

Legacy data stores A/B/C prescriptions. Native My Mettle exposes four runtime modes through one policy layer rather than hard-coding mode behaviour throughout the app:

| Native mode | Current meaning | Source |
|---|---|---|
| A | Full day | Legacy A |
| B | Focused day; deliberately above Busy Day but below Full Day | Session-level midpoint between Legacy A and B |
| C | Busy day | Legacy B |
| D | Can't be arsed; minimum viable session and may omit whole lower-priority exercises | Legacy C plus whole-exercise reduction |

There is often no integer prescription between three sets in A and two sets in old B. Native B therefore resolves across the session rather than pretending that 2 = 2. It starts from old Busy Day total volume, restores roughly half the sets that separate it from Full Day, and allocates those additional sets to principal/core movements first.

The implementation lives in `workout/WorkoutModes.kt`. Future mode tuning or a user-facing mode editor should alter an equivalent configuration there rather than creating mode-specific branches across workout code.

Completed Legacy sessions are not rewritten to the new semantics. Their stored session/exercise prescription snapshots remain the historical truth.

## First interactive checkpoint

The first N2 checkpoint exercises:

- programme-day selection;
- A/B/C/D planning and mode-relative exercise/set counts;
- changing mode during the active interaction prototype while retaining entered exercise state;
- calculator-style load entry with arithmetic and `×2`;
- numeric repetition entry;
- explicit set logging before rest begins, avoiding keyboard/timer races;
- native rest presentation with `−15`, `+15`, pause/resume, minimise and protected end;
- exercise completion and reopening;
- session completion state.

The UI still contains a demo routine fallback so the shell remains inspectable before a real Legacy dataset is installed.

## Room-backed workout lifecycle

`RoomWorkoutRepository` now provides the persistence boundary underneath that prototype:

1. Read the active immutable routine version and selected programme day from Room.
2. Reconstruct the imported Legacy A/B/C anchors for each slot.
3. Resolve native A/B/C/D through `WorkoutModePolicy`.
4. Snapshot the resolved native prescription into a new `SessionExerciseEntity`.
5. Create prescribed `SetRecordEntity` rows and update `AppStateEntity.activeSessionId` atomically.
6. Persist set values/completion timestamps immediately.
7. Query previous completed sets by exercise identity rather than assuming the previous calendar week/day.
8. Allow completed exercises to be reopened in persistence.
9. Complete a session without falsely marking unperformed exercises as completed; remaining planned movements become `skipped`.
10. Advance the ψ/φ/π/& training cycle without rewriting historical routine/session snapshots.

The resolved session snapshot carries mode `A`, `B`, `C` or `D`, even though the imported routine continues to store its three Legacy anchors. This separation is intentional: future mode redesign should not require a database migration or reinterpret completed workouts.

## Remaining N2 work

1. Bind the Material 3 workout screen to `RoomWorkoutRepository` when a real dataset is present, retaining the demo only as a fresh-install fallback.
2. Persist active-session mode changes safely: newly required exercises/sets can be added, while already logged work must never be deleted when moving to an easier mode.
3. Add mode-relative completion scoring and whole-session review.
4. Promote the rest timer from in-process prototype state to Android-native background/notification integration.
5. Add the user-facing path for the one-time Legacy data transfer/manual migration checkpoint.
