# N2 — native workout slice

N2 rebuilds the daily workout loop natively before the visual system is customised heavily.

## Material baseline

For this stage the app intentionally stays close to Google's current Material 3 / Material You behaviour, including dynamic system colour on Android 12+. The existing Compose BOM remains the stable dependency baseline. My Mettle-specific styling can change independently later.

## Four-mode policy

Legacy data stores A/B/C prescriptions. Native My Mettle exposes four runtime modes through one policy layer rather than hard-coding mode behaviour throughout the app:

| Native mode | Current meaning | Source |
|---|---|---|
| A | Full day | Legacy A |
| B | Focused day; near-full, generally one set lighter than A | Derived between Legacy A and B |
| C | Busy day | Legacy B |
| D | Can't be arsed; minimum viable session and may omit whole lower-priority exercises | Legacy C plus whole-exercise reduction |

The implementation lives in `workout/WorkoutModes.kt`. Future mode tuning or a user-facing mode editor should alter an equivalent configuration there rather than creating mode-specific branches across workout code.

Completed Legacy sessions are not rewritten to the new semantics. Their stored session/exercise prescription snapshots remain the historical truth.

## First interactive checkpoint

The first N2 checkpoint is deliberately an interaction prototype before Room is allowed to become the daily-driver workout source of truth. It now exercises:

- programme-day selection;
- A/B/C/D planning and mode-relative exercise/set counts;
- changing mode during an active session while retaining already-entered exercise state;
- calculator-style load entry with arithmetic and `×2`;
- numeric repetition entry;
- explicit set logging before rest begins, avoiding keyboard/timer races;
- native rest presentation with `−15`, `+15`, pause/resume, minimise and protected end;
- exercise completion and reopening;
- session completion state.

The demo exercise list is temporary scaffolding. The next N2 increment replaces it with the Room-backed current routine and persists session/set state using the existing normalized schema.

## Next N2 increment

1. Read current programme/routine slots from Room.
2. Resolve A–D through `WorkoutModePolicy` at session start/change.
3. Persist the resolved prescription snapshot on each `SessionExerciseEntity`.
4. Persist set edits and completion timestamps immediately.
5. Use the existing latest-completed-set query for previous-weight context.
6. Add mode-relative completion scoring and whole-session review.
7. Promote the rest timer from in-process prototype state to Android-native background/notification integration.
