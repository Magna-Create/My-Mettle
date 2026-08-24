# N-BIO-6 device verification

This procedure closes the checks that require Android's real Room runtime or a real Lite backup. The verifier uses a fresh in-memory database for every automated check. It does not read or modify the installed Native workout database. Real Lite backup verification also uses an isolated database; setup-photo bytes are validated without writing photo files, and app settings are not imported.

## Build in Termux

Keep My Mettle Lite Legacy installed. From a Termux checkout of this repository:

```sh
pkg update
pkg install -y git openjdk-17 curl unzip
termux-setup-storage

git fetch origin
git switch agent/n-bio-vnext-foundation
git pull --ff-only origin agent/n-bio-vnext-foundation

./gradlew --stop
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace --no-daemon \
  2>&1 | tee /sdcard/Download/my-mettle-n-bio-6-build.log

cp app/build/outputs/apk/debug/app-debug.apk \
  /sdcard/Download/My-Mettle-NBio6-Dev.apk
```

The wrapper requires JDK 17 and automatically selects Termux's `openjdk-17` installation. The project requires Android SDK platform 36. If `ANDROID_HOME` is not already configured, install/configure the Android SDK before running Gradle. On ARM Termux installations, keep any existing platform-native `aapt2` override; the Android Maven artifact is a desktop binary and cannot replace a working Termux-native override.

Install `My-Mettle-NBio6-Dev.apk` from the Android Files/Downloads application. Android may require permission for that file manager to install unknown apps. The debug application ID is separate from Lite Legacy.

An optional compile-only check for the instrumentation package is:

```sh
./gradlew :app:assembleDebugAndroidTest --stacktrace --no-daemon \
  2>&1 | tee /sdcard/Download/my-mettle-n-bio-6-android-test-build.log
```

## Run the checks in the app

1. Open **Settings → Developer → Biological developer tools**.
2. Under **N-BIO-6 device acceptance**, tap **Run automated Room and flow checks**.
3. Confirm that all seven checks report `PASS`:
   - Room 11 schema and foreign keys;
   - dead hang, rep-free end to end;
   - grip hold sides, correction, raw immutability and inference replay;
   - assistance directionality;
   - treadmill physical-unit round trip;
   - stair-machine ordinal evidence;
   - power-duration separation.
4. Tap **Validate a real Lite backup** and select an unmodified schema-6 JSON backup. Confirm `PASS` and review the translated exercise/session/set/observation/metric counts and evidence samples.
5. Tap **Export N-BIO-6 closure report** and save the JSON file.

The real-backup verifier checks exact factual Room persistence, unknown laterality, import provenance, foreign keys, History projection and conservative inference replay. It does not invent a backup when none is available.

## Report a failure

Provide:

- `my-mettle-n-bio-6-build.log` if Gradle fails;
- the exported `my-mettle-n-bio-6-closure-*.json` if any on-device check fails;
- the failing check title and Android device/OS version;
- the first complete `Caused by:` section for build failures, without removing the surrounding task name.

Do not include the original Lite backup unless its workout/profile contents are safe to share. The closure report contains counts and up to five factual evidence summaries, not the source JSON.
