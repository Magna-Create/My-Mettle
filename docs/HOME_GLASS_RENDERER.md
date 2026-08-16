# Daily Update adaptive layout and glass renderer

## Layout contract

The Daily Update prototype is authored against a 453-unit-wide frame. The Android implementation reads the current app window through Material 3 Adaptive rather than inspecting physical display pixels:

- compact windows scale the complete 453-unit composition down proportionally;
- the scale is recomputed whenever the app window changes, including multi-window and any display-size change that alters the app's dp bounds;
- medium and expanded windows use separate layout-family branches but currently keep the validated frame at 453 dp and centre it;
- visual values never scale above the reference frame until wider prototypes have been tested.

This keeps spacing, type, cards, controls and glass geometry in the same proportions on the S25 Ultra while leaving explicit extension points for later tablet and landscape work.

## Renderer decision

The selected renderer is Haze `2.0.0-alpha05`, using the matching `haze` and `haze-glass` artifacts. General glass surfaces share an app-owned `HazeState`, while each destination registers the live Compose artwork that should be sampled into that state. `MettleGlassSurface` owns tint, depth blur, refraction, Fresnel/specular lighting, chromatic aberration, press response, shape and fallback styling.

The global hotbar deliberately uses a second app-owned `HazeState`. Its source is the complete rendered destination content beneath the bar, and only the hotbar receives that state through a nested `LocalMettleHazeState` provider. This lets the hotbar continuously sample opaque Train/Library/History surfaces, scrolling cards, Daily Update and the animated Intensity field exactly as rendered, without causing destination glass such as header controls or the selector lens to sample themselves.

Daily Update registers its normal app gradient for general glass. The Intensity destination keeps a full-window `MettleBackground` source for wide-layout gutters and additionally registers the exact animated selector Canvas as a general-glass Haze source. That Canvas uses the same live `ambientColour`, `warmPulse` and `activeMode` values that are visible on screen, so stationary glass continues to update while the backdrop animation changes. Do not introduce a separate static reconstruction of a live backdrop for Haze sampling.

### Haze 2 UI API contract

All Compose UI glass must use the typed Haze 2 Glass path exposed through `MettleGlassSurface`:

- register sampled artwork with `Modifier.hazeSource(HazeState)`;
- keep shared states as `rememberHazeState()` without the removed `blurEnabled` constructor argument;
- define glass as an immutable/replayable `GlassStyle`;
- render it with `Modifier.hazeGlass(input = HazeInput.Sources(state), style = style, ...)`;
- replace a style through recomposition when its parameters change rather than mutating an effect object;
- keep one renderer/style path rather than platform-specific Glass fallbacks in app UI code.

Removed or pre-typed-Glass APIs must not be reintroduced into `app/src/main/java/dev/kian/mymettle/ui`. This includes v1 aliases such as `HazeTint`, `HazeStyle`, `LocalHazeStyle`, `haze` and `hazeChild`, the removed `rememberHazeState(blurEnabled = ...)` form, the old `tints = ...` property, and the earlier Glass effect/renderer types (`HazeGlassStyle`, `GlassVisualEffect`, `glassEffect`, `GlassRenderer*`, grouped `GlassLighting`/`GlassColor`/`GlassRendering`, and `GlassStyle.Unspecified`).

The Gradle `verifyHaze2Ui` task scans every Kotlin source under the UI package for these legacy patterns and is attached to `preBuild`. Therefore the normal `./gradlew :app:assembleDebug` path fails before compilation if an obsolete Haze API is reintroduced.

| Candidate | Useful capability | Decision |
| --- | --- | --- |
| [Haze](https://github.com/chrisbanes/haze) | Compose backdrop capture plus blur, refractive glass, interaction and graceful optical fallback | Selected and isolated behind `MettleGlassSurface` |
| [AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) | Strong low-level liquid-glass optics | Retained as a future renderer option; it intentionally supplies no high-level components |
| [Cloudy](https://github.com/skydoves/Cloudy) | Self/backdrop blur, liquid glass and older-device CPU paths | Useful alternative, but adding a second alpha renderer now would duplicate the same responsibility |

Haze captures content inside the app's Compose render tree. It does not use Android's cross-window background-blur API, so an OEM or power-saving mode disabling window-level blur does not switch these surfaces off. The explicit tint/background remains readable if an individual optical feature is unavailable.

## Build contract

Haze alpha05's published Android metadata requires compile SDK 37. The app therefore uses the coherent supported toolchain below while retaining target SDK 36 runtime behaviour:

- Android Gradle Plugin 9.1.1;
- Gradle 9.3.1;
- Kotlin 2.4.10 and KSP 2.3.10;
- JDK 17;
- Android platform `37.0` and SDK Build Tools `36.0.0`.

The renderer is experimental upstream by definition. Keeping glass material use in `MettleGlassSurface` limits future Haze API migrations to one component boundary, while destination-owned source registration keeps each glass surface tied to the artwork the user actually sees.
