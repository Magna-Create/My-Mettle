# Optimisation & Security Changelog — 0.1.0-alpha24

## Pass identity

- App version: `0.1.0-alpha24`
- Version code: `23`
- Baseline branch: `main`
- Baseline commit: `fe3b6c4fdd73f46a3708ccd579fe97b600b4adbc`
- Maintenance branch: `agent/optimisation-security-alpha24`
- Scope: dead/scaffolding-code pruning, low-risk architecture cleanup, security/privacy hardening and stronger CI verification.
- Explicit exclusions: destructive Room migration-policy changes and moving Biological Developer tooling out of `src/main` are deferred until after the next N-BIO-Next full development run. Active workout/UI consolidation is reserved for the subsequent UI pass.

## Applied changes

### 1. Prune obsolete alpha24 scaffolding

Commit: `6818c7257fe155ada38c4ca221a61f2d7d7fe3a1`

Removed:

- `sitecustomize.py` — one-off CI bootstrap which dynamically executed code from a historical workflow and wrote Git hooks.
- `FigmaIntensitySelector.kt` — superseded intensity selector; production navigation uses `IntensitySelectorScreenV3`.
- `IntensityBottomToolbarV2.kt` — obsolete wrapper around the global bottom toolbar; the app already configures the global toolbar directly.
- `GraphicsLayerCompat.kt` — selector-prototype compatibility shim superseded by direct Compose `graphicsLayer` usage.

Rollback: revert this commit if compilation reveals an unexpected hidden dependency. Do not restore `sitecustomize.py` unless the historical one-off verifier itself is intentionally restored.

### 2. Disable Android OS backup for private app data

Commit: `076a753e13882acfcdda0021127a638a21047ebc`

Changed `android:allowBackup` from `true` to `false`. This prevents workout history, body/health state, inference data and internal setup-photo files from being included in normal Android application backup flows by default.

Rollback impact: restoring OS backup changes the privacy boundary; treat that as a product/privacy decision rather than a cosmetic revert.

### 3. Verify the custom Gradle bootstrap before execution

Commit: `575abbac6a873e93371021a591ed44b08b73efad`

The Termux-compatible `gradlew` bootstrap now verifies the Gradle 9.3.1 binary distribution against the official SHA-256 before extraction/execution:

`b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`

A mismatching cached ZIP is deleted and the build aborts rather than executing unverified build tooling.

### 4. Harden Android CI and broaden verification

Commit: `e4ed55509ec652d46819f17deda96a4bfa926297`

Changes:

- Removed the obsolete `native/**` branch trigger.
- Added explicit `permissions: contents: read`.
- Pinned `actions/checkout`, `actions/setup-java` and `gradle/actions/setup-gradle` to immutable commit SHAs.
- Expanded the build job from unit-test/debug assembly to:
  - `:app:testDebugUnitTest`
  - `:app:lintDebug`
  - `:app:assembleDebug`
  - `:app:assembleRelease`

This CI change is intentionally separate from app behaviour so it can be reverted independently if CI infrastructure, rather than application code, proves problematic.

### 5. Make rest-timer notifications private on the lock screen

Commit: `ff5a6ba458a658ea810ddeaa6689c8ca27992b27`

Both active-rest and rest-complete notifications now use `Notification.VISIBILITY_PRIVATE` instead of `VISIBILITY_PUBLIC`. Exercise names remain available to the user while unlocked without being explicitly declared safe for public lock-screen display.

### 6. Bound legacy setup-photo import resources

Commit: `c1f84c4b1b63ec38f9d7a8a5f6a1755be4938d88`

Added defensive limits to the Lite setup-photo importer:

- maximum 256 setup photos per imported backup batch;
- maximum declared image dimension of 12,000 px;
- maximum data-URL length of 12 MiB per photo;
- maximum decoded JPEG size of 8 MiB per photo;
- maximum aggregate setup-photo data-URL size of 64 MiB;
- limits are applied both to actual import and isolated validation paths.

The normal in-app camera/photo flow is unchanged; it already re-encodes and downsizes setup photos into app-private storage.

### 7. Prune obsolete current rest-notification preference state

Commit: `83186bdee15c2f318cb153c02f7c6a6741007629`

Removed `backgroundNotificationEnabled` from current `RestTimerPreferences` because the foreground notification is mandatory while the timer runs and the service never honoured the flag.

Compatibility behaviour:

- Lite schema-6 parsing still accepts the historical `backgroundNotificationEnabled` field.
- Import no longer carries the value into current settings.
- Writing current settings removes the old DataStore key.

### 8. Require HTTPS for exercise reference links

Commit: `4d514b72d699510a17121ab4e3ed3724e1bc6791`

Newly saved workout setup/reference links must now use `https://`. Plain `http://` links are rejected.

Existing stored links are not rewritten by this change.

### 9. Report the live Room schema version in diagnostics

Commit: `2b2384328fcddbf0fec2390ae747147904f79000`

Removed the stale hard-coded Biological Developer schema value (`11`). Diagnostics now read `database.openHelper.readableDatabase.version`, so exported diagnostic JSON reports the schema actually opened by Room rather than a duplicated constant that can drift during N-BIO development.

### 10. Bound the production legacy JSON import entry point

Commit: `4e49de07189cb5f05a8e92199207721d96c3401e`

`LegacyV6Importer.importJson` now rejects backup strings larger than 64 MiB before parsing/projecting/persisting them.

This complements the more specific setup-photo limits. The developer-only file-validation screen still reads a user-selected file into memory before validation; see deferred work below.

## Maintenance ledger

Commit: `4fa25b607e820bcd9be283837a18a0828b273e50`

Added `docs/optimisation-security/README.md` defining the versioned maintenance-log convention and separating optimisation/security passes from UI passes.

## Deliberately deferred

These are known items, not forgotten findings.

### Room migration policy

Current Room database version is 12, but the production builder only registers explicit migrations through 3→4 and still allows destructive fallback for unsupported migration paths.

Decision for alpha24: **defer**. Data preservation is not yet the release constraint and N-BIO-Next is expected to evolve the schema again. Revisit immediately after the next full N-BIO-Next development run, before treating Native history as durable user data.

### Biological Developer tooling in `src/main`

The developer screen, N-BIO verifiers and development database reset path remain in the main source set.

Decision for alpha24: **retain intentionally**. They are useful during active N-BIO development. Move them to a debug-only source set as part of release hardening rather than during this pass.

### Workout renderer consolidation

`FigmaWorkoutSessionV2` still delegates Quick Select/substitution behaviour to the older `FigmaWorkoutSession` implementation. The older renderer therefore cannot yet be deleted safely.

Decision: move this into the subsequent UI pass because it changes active interaction code and should receive its own `UI_PASS_0.1.0-alpha24.md` record.

### Canonical production naming

Names such as `N2WorkoutViewModel`, `FigmaWorkoutSessionV2`, `IntensitySelectorScreenV3` and `MettleBottomToolbarV2` are historical implementation names even where the implementation is now canonical.

Decision: rename only after renderer/UI consolidation so the rename does not obscure behavioural diffs.

### Adaptive width-class plumbing

Daily Update consumes `LocalMettleWindowWidthClass`, but Compact/Medium/Expanded currently execute the same viewport calculation. The layer is redundant today but still wired into active UI.

Decision: evaluate/remove during the UI pass rather than deleting app-level adaptive plumbing inside the security/pruning pass.

### Release minification and developer-source stripping

R8/resource shrinking remain disabled and developer utilities remain packaged in release builds.

Decision: defer to the release-hardening milestone; the app is still well before production release.

### Developer Lite-backup file read bound

The production `LegacyV6Importer` and setup-photo decoding paths are bounded by this pass. The Biological Developer file picker still calls `readText()` on an explicitly user-selected validation file before the verifier sees it.

Decision: low-priority follow-up while the tool remains debug/development-oriented; add a bounded stream read when developer tooling is hardened for release.

## Validation

The branch uses the repository Android CI workflow. The workflow itself was strengthened during this pass to run unit tests, lint, debug assembly and release assembly on every `agent/**` push.

Final alpha24 optimisation CI result: **pending at the time this log was first written**. Update this section with the final workflow run and conclusion before merge.

No instrumentation/emulator suite is run by the hosted workflow; N-BIO device acceptance remains a separate on-device/developer verification path.

## Rollback strategy

The maintenance pass intentionally uses small commits rather than one squashed implementation commit. Prefer reverting the specific offending commit first.

Recommended order if a regression is found:

1. Identify whether the regression is build/CI-only, privacy/security behaviour, legacy import, or UI/runtime behaviour.
2. Revert the smallest matching commit from the sections above.
3. Keep unrelated hardening changes in place.
4. If the regression cannot be isolated, reset the maintenance branch to baseline `fe3b6c4fdd73f46a3708ccd579fe97b600b4adbc` and reapply known-good commits individually.

The subsequent UI pass must use a separate versioned UI log so optimisation/security rollbacks do not require visually diffing unrelated UI work.
