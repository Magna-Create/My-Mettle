# UI Cull & Performance Pass — 0.1.0-alpha24

## Pass identity

- App version: `0.1.0-alpha24`
- Version code: `23`
- Baseline maintenance branch: `agent/optimisation-security-alpha24`
- Baseline commit: `a63dcca091224429f79fb343c0991aff2d9c4b7e`
- Working branch: `agent/ui-performance-alpha24`
- Scope: remove redundant UI implementation paths, preserve the intended visual/interaction contract, and improve frame/input/data-path efficiency for high-refresh Android devices.
- Primary device target: Galaxy S25 Ultra / Android 16, with 120 Hz rendering preferred when platform policy permits.
- Non-goal: no intentional redesign, simplification of visible information, reduced glass quality, changed training logic or speculative caching architecture.

## Applied changes

### 1. Cull the duplicate legacy workout renderer

Commit: `49d16333340d2c732f841eabe7f8e7812d95c932`

`FigmaWorkoutSessionV2` is the canonical alpha24 renderer for workout sets, setup and finish/delete interactions, but the older `FigmaWorkoutSession` file still contained another almost-complete workout UI.

The older implementation was reduced to the only two transient surfaces still delegated to it:

- Quick Select;
- target-compatible exercise substitution.

This removes the duplicate legacy sets/setup/finish implementation while retaining the existing transient surface contract. Lazy list content types were added and substitution filtering is memoised.

Independent CI checkpoint: this commit passed the strengthened unit-test/lint/debug/release workflow before later performance work was added.

### 2. Prefer 120 Hz rendering at the Android window boundary

Commit: `f17f67fb424fa3e7b890600a3fbec66c0d542696`

`MainActivity` now sets `WindowManager.LayoutParams.preferredRefreshRate = 120f` once when the activity is created.

This is deliberately a platform refresh-rate preference rather than an app-owned polling loop. On API 34+ Android can accept the intended frame rate directly and select the best matching display mode. System display, power and thermal policy remain authoritative, so the app can request 120 Hz without trying to force an unsupported or inappropriate mode.

Compose animation remains driven by Android's frame clock/Choreographer; no fixed 120 Hz timer was introduced.

### 3. Move rest-timer progress invalidation into the draw phase

Commits:

- `5feffd0b46079fea996dab371295249e65c8a939` — move toolbar progress reads to Canvas draw;
- `d2f6b6d694d92db4e2aa9ed3404923b6c6cc17a1` — replace timer polling with Compose frame-clock animation.

Previous behaviour:

- the collapsed rest-progress ring was recomputed with `delay(250)`;
- visible progress therefore advanced at only ~4 updates/second;
- each tick wrote state read high in `MyMettleApp`, invalidating more composition than the ring itself needed.

New behaviour:

- an `Animatable` represents elapsed rest progress;
- it animates linearly from the persisted/current fraction to completion using Compose's frame clock;
- `Animatable.asState()` is passed to the toolbar;
- the state value is read inside the `Canvas` draw lambda rather than by the app/navigation composition.

Result: the ring can update at the display cadence while frame-by-frame invalidation is constrained to the visual that actually changes.

Independent CI checkpoint: the combined frame-clock implementation at `d2f6b6d...` passed unit tests, lint, debug assembly and release assembly.

### 4. Keep high-frequency intensity drag state out of composition

Commit: `42a63e0a15b861d77aea1005a480718c76512805`

The intensity selector can receive pointer MOVE events at display/input cadence. Previously the raw drag offset was read while composing the selector, so continuous motion could invalidate the whole screen.

The raw offset is now held as snapshot state whose derived magnetic position is read inside the `Modifier.offset { ... }` layout lambda. Continuous movement therefore updates the moving lens position without requiring full selector recomposition. Mode-zone transitions still update normal compositional state so copy, glow and haptics remain correct.

Magnetic-zone maths, haptics, thresholds and visible behaviour are otherwise unchanged.

### 5. Cull no-op adaptive-layout and obsolete toolbar paths

Commits:

- `357f5871ef005051ecef6a8d6276064b9347088e` — remove dead Daily Update toolbar implementation and collapse identical width-class branches;
- `a7c5fb8a72df03b8991f5eb3fb9a77343196c48d` — remove `AdaptiveLayout.kt`;
- `cbbcb75bc23f5b4039d9475d7e7678c93d2e4c2a` — remove the now-unused Material3 adaptive dependency.

The removed adaptive layer did not currently adapt anything: Compact, Medium and Expanded all resolved Daily Update to the same `minOf(maxWidth, 453.dp)` viewport. Daily Update now performs that calculation directly.

An older four-icon `MettleBottomToolbar` embedded in the Daily Update source was also removed. The production app shell already owns navigation through `MettleBottomToolbarV2`, so the older component was not part of the rendered UI.

### 6. Avoid reconstructing the entire workout after every set save

Commit: `5afab13acf50957de8dec728b6631e54bdca7e15`

This is the main backend optimisation for workout interaction.

Previous hot path for every draft save or logged set:

1. persist the changed set;
2. call `activeWorkout(sessionId)`;
3. reconstruct the whole workout from Room, including session targets, all exercises, profile/version bundles, prescriptions, all sets, previous completed-set history, exercise memory/cues and setup media;
4. replace the complete UI workout snapshot.

`RoomWorkoutRepository.saveSet()` already returns the complete persisted `PerformanceSetRecord` that changed. The ViewModel now merges that returned immutable set record into the current in-memory `ActiveWorkout` snapshot instead of immediately reading the whole workout again.

Safeguards:

- Room remains the authoritative persistence layer;
- a returned set is only applied if the currently displayed workout still has the same session id;
- structural operations such as mode changes, substitutions and setup-media changes continue to use full reloads where their wider state changes justify it;
- rest-timer start logic evaluates the returned persisted set record.

This removes substantial database work and object allocation from the most frequently used workout write path without changing stored data or training logic.

### 7. Preserve transient workout material parity after the cull

Commit: `17c6db45618fbc6ef21297ae60e9ce1863896074`

A direct parity review against the pre-cull transient renderer found that the substitution return action had temporarily inherited the shared action button's default white glass rather than the existing cyan tint/outline.

The original cyan container tint, outline, minimum height and centred copy were restored. Quick Select/substitution dimensions, colours, copy, search field and control geometry therefore remain aligned with the pre-cull implementation; the intended differences are implementation culling and LazyColumn/filter efficiency only.

## Haze / glass performance decision

The shared glass implementation remains on Haze's `HazePerformanceMode.Default` adaptive behaviour.

This is intentional. The performance scan found no evidence that a fixed lower optical tier should be applied globally before physical-device measurement. Haze cost depends heavily on changing source content, affected area, device/GPU and display resolution/refresh rate. This pass therefore reduces unnecessary recomposition/invalidation and backend work around the glass instead of visibly degrading blur/refraction quality.

Any Haze quality-mode change should be justified by release-like frame measurements on the Galaxy S25 Ultra, not by assumption.

## 120 Hz semantics

The 120 Hz change is a **request to Android**, not a promise that every rendered frame will be 120 fps.

For a 120 Hz frame budget, app work ideally needs to stay within roughly 8.3 ms per frame. Android may still select a different refresh rate because of device settings, adaptive-refresh policy, battery saving, thermal state or competing surface requirements. The app should not repeatedly call frame-rate APIs in an attempt to override that decision.

There is no application-level 'poll rate' that should be mechanically changed to 120 Hz. High-frequency visual state now follows the platform frame clock where appropriate; persisted/domain data remains event-driven rather than being polled every frame.

## Deliberately not changed

### Haze optical quality

No fixed low/performance glass quality was forced. Preserve clarity until an S25 Ultra frame trace demonstrates a real glass bottleneck.

### Low-frequency structural reloads

Mode changes, exercise substitutions, setup-photo edits, exercise completion and session completion may still rebuild larger workout state. These are discrete operations rather than every-frame/every-keystroke hot paths, so keeping them simple and authoritative is preferable to adding broad cache invalidation complexity prematurely.

### Historical implementation names

Names such as `N2WorkoutViewModel`, `FigmaWorkoutSessionV2`, `IntensitySelectorScreenV3` and `MettleBottomToolbarV2` remain. Renaming them now would generate a noisy source diff with effectively zero runtime benefit. Rename when a future architecture milestone materially changes their public role.

### Release benchmarking infrastructure

No Macrobenchmark/Baseline Profile module was added in this pass. The immediate target is a clean, physically testable alpha24 base. A release-like Macrobenchmark/frame-timing harness is a sensible next optimisation layer after the current UX is verified on-device.

## Validation

Final implementation head before this log: `17c6db45618fbc6ef21297ae60e9ce1863896074`.

GitHub Actions run #348 is the full strengthened alpha24 validation run for that head and was still executing when this document was first written. It runs:

- generated biological reference-asset verification;
- `:app:testDebugUnitTest`;
- `:app:lintDebug`;
- `:app:assembleDebug`;
- `:app:assembleRelease`.

Update this section with the final result before considering the pass closed.

Hosted CI proves build/test/lint integrity, not physical 120 fps. Final acceptance should include a quick S25 Ultra build with attention to workout scrolling, intensity dragging, the collapsed rest ring, Quick Select/substitution and repeated set entry/logging.

## Rollback strategy

The pass remains intentionally split into small commits. If a regression appears:

1. distinguish visual parity, refresh-rate policy, animation/input invalidation or workout persistence/state behaviour;
2. revert the smallest matching commit rather than rolling back the entire pass;
3. use baseline `a63dcca091224429f79fb343c0991aff2d9c4b7e` for a complete second-pass rollback;
4. do not roll back the preceding optimisation/security pass unless the failure is demonstrably below this branch boundary.
