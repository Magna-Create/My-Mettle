# N2 — native workout slice

N2 rebuilds the daily workout loop natively before the visual system is customised heavily.

## Material baseline

For this stage the app intentionally stays close to Google's current Material 3 / Material You behaviour, including dynamic system colour on Android 12+. My Mettle-specific styling can change independently after the workout/data behaviour is validated on-device.

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

Completed sessions are not reinterpreted by later policy changes. Their stored session/exercise prescription snapshots remain historical truth.

## Room-backed workout lifecycle

`RoomWorkoutRepository` is the persistence boundary for the live workout:

1. Read the active immutable routine version and selected programme day from Room.
2. Reconstruct imported Legacy A/B/C anchors for each slot.
3. Resolve native A/B/C/D through `WorkoutModePolicy`.
4. Snapshot the resolved native prescription into a new `SessionExerciseEntity`.
5. Create prescribed `SetRecordEntity` rows and update `AppStateEntity.activeSessionId` atomically.
6. Persist set values/completion timestamps.
7. Query previous completed sets by exercise identity rather than assuming the previous calendar week/day.
8. Allow completed exercises to be reopened.
9. Persist mid-session mode changes without deleting performed work.
10. Complete a session without falsely marking unperformed exercises as completed; remaining target movements become `skipped`.
11. Advance the ψ/φ/π/& training cycle without rewriting historical routine/session snapshots.

When a mode changes mid-session, the same immutable routine version is re-resolved. Moving upward can add missing exercises/sets. Moving downward can hide untouched excluded movements, while performed surplus work remains in the session as additional work.

## Live Material workout UI

The current Train screen is backed directly by Room rather than demo data. It supports:

- ψ / φ / π / & day selection;
- A / B / C / D planning with exercise/set counts;
- one-time Lite JSON import into an empty native database;
- load/repetition logging;
- calculator load entry such as `6.5 × 2`;
- duration logging in seconds;
- distance logging in metres;
- previous-set context;
- exercise complete/reopen;
- persisted mid-session mode switching;
- session completion.

The workout ViewModel is hoisted above Navigation so Train, reflection and completion overlays share one owner/state instance.

## Native rest timer

The rest timer is intentionally separate from Compose workout state:

- a user-started Android foreground service owns active countdown state;
- target time is stored using elapsed realtime;
- the Android notification renders its own countdown chronometer rather than waking Kotlin every second in background;
- notification controls provide −15, pause/resume, +15 and End;
- a separate Ready notification is posted on completion;
- the in-app Material surface can minimise to a lozenge and recover after UI/process recreation.

## Reflection and session outcome

Completing an exercise can open the optional native reflection sheet:

- target-muscle engagement 1–7;
- form clean/mixed/poor;
- vibe/enjoyment 1–7;
- comfort good/fine/uncomfortable/pain;
- optional note.

Completing the session produces a deterministic achievement score against that session's stored target. A perfect D target and a perfect A target both score 100. Additional work can create a bounded bonus only after the selected target is met; bonus work cannot erase skipped target work.

The optional whole-session review stores exercise order, organisation, pacing, delay impact and a note.

## History

Navigation currently exposes Train and History. History reads completed sessions from Room and shows:

- original day/mode snapshots;
- achievement score;
- logged exercise/set detail;
- per-exercise reflection;
- whole-session review.

Imported historical A/B/C sessions are shown using their original stored mode code rather than being silently relabelled to the new native semantics.

## Device-test checkpoint

N2 is now intended to be exercised on the target Galaxy device before expanding into N3. The first field test should cover:

1. one-time Lite backup import;
2. A/B/C/D exercise/set counts across ψ/φ/π;
3. calculator, reps, duration and distance entry;
4. background/minimised rest timer and notification controls;
5. mode switch down and back up mid-session;
6. exercise reflection and reopen;
7. partial vs perfect session outcome;
8. History contents;
9. Android back behaviour.

After this checkpoint, N3 moves into the Exercise Library, setup/hints progressive disclosure and native setup-photo capture/gallery rather than polishing the temporary Material visual treatment.
