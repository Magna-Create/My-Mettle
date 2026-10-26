# My Mettle — recordable portfolio hero loop

## Goal
Record real Android UI on Samsung S25 Ultra and composite over a black-and-white graphic systems backplate. Only the app content is a real session; inference animation outside the phone is an explicitly illustrative reconstruction.

## Figma storyboard

| Phase | Figma IDs | Sequence |
|---|---|---|
| Daily Update | 99:15003, 99:13069 | Begin Session |
| Intensity | 99:13175 | Select mode |
| Active set and target-versus-entry | 99:14146, 99:12325, 99:11066 | Type and log set |
| Exercise completion | 99:11720 | Show completed exercise |
| Substitute or switch | 99:13923, 99:13817, 99:13459 | Explore replacements |
| Exit | 99:14750, 99:14824 | Complete session, return home |

Design reference: https://www.figma.com/design/QEDB30HX0ou1gNN4icrCet/UI-UX-Ideation-2

## Implementation status

- Workout SETS and SETUP use FigmaWorkoutSessionV2 in Kotlin; QUICK_SELECT, SUBSTITUTION and finish gestures still delegate to an older renderer. The Figma frames are a design reference, not proof of one-to-one current implementation.
- This branch adds a read-only left-hand per-set Reference load / Target reps display backed by existing session prescriptions, alongside the separately editable actual value. No target is shown as a fabricated N0 estimate.
- The load target can come from a plan or compatible prior observation; N0/7F experimental capability candidates remain SHADOW and do not become workout prescriptions.

## Suggested animation timing (adapt to the real interaction latency)

1. 0–3 s: Daily Update to Begin Session. Black-and-white circuitry fades behind the device.
2. 3–6 s: Mode gesture to workout. Brief illustrative PROGRAMME RESOLVED animation.
3. 6–11 s: Enter and log actual set. A single observed-data signal enters the backplate, then fades.
4. 11–15 s: Complete an exercise and optionally open substitution. Show a clearly conceptual exercise relationship, never fabricated target-load transfer.
5. 15–20 s: Complete session and return to Daily Update. End frame visually matches start for looping.

The backplate may use real precomputed historical posterior snapshots, but must be labelled as retrospective visualisation rather than live in-workout inference.

## Capture acceptance

- Use real screen recording and actual set events; no staged model authority or fake achievements.
- Suppress notifications and personal information.
- Keep screen imagery tack-sharp; apply DoF/glow to the backplate, not text fields.
- Record after Android CI and on-device checks; check that completion and escape really return to the starting point.
