# LAB-2B standalone VLM harness

This directory is an isolated Android Gradle root for UI/ML LAB-2B. It is **not** a My Mettle module and must never be included from the repository root `settings.gradle.kts`.

## Frozen reproduction matrix

- Qualcomm reference: `qualcomm/ai-hub-apps` commit `db3f9772d4e423dee2df517335009c703845dba8`
- GenieX AAR: `com.qualcomm.qti:geniex-android:0.3.5`
- AGP: `8.13.0`
- Kotlin: `2.2.0`
- Gradle: `9.1.0`
- Java: 17
- compileSdk / targetSdk: 34 / 34
- minSdk: 31
- NDK declaration: `27.3.13750724`
- ABI: `arm64-v8a`
- JNI packaging: `jniLibs.useLegacyPackaging = true`

The accepted Qualcomm reference app does not check in a Gradle wrapper. Its build tooling pins Gradle 9.1.0. The `gradlew` / `gradlew.bat` files here are therefore **pin-enforcing launchers**, not generated Gradle Wrapper JAR launchers: they require `gradle 9.1.0` on `PATH` and refuse another version.

## Scope

The harness proves only:

1. GenieX initialises;
2. an externally staged Qwen3-VL-2B-Instruct Q4_0 GGUF + matching mmproj can be imported through `HubSource.LOCALFS`;
3. `ModelManagerWrapper.getPaths()` resolves the managed model and `runtime_id`;
4. one `VlmWrapper` can load with explicit `compute_unit = npu`;
5. one image + one short prompt can produce a response;
6. stop / unload / reload / process restart can be tested;
7. requested accelerator and proven accelerator remain separate evidence.

There is no Room, N-BIO, OCR, CameraX, production download code, server code, My Mettle module dependency or product AI adapter.

The manifest intentionally has **no `INTERNET` permission**. Initial model acquisition happens outside the harness; the harness only imports an already-local folder.

## Before any model import

LAB-2B requires the B3 native/page-size gate first.

From this directory on the build device:

```bash
AAPT2="$(command -v aapt2)"
./gradlew -Pandroid.aapt2FromMavenOverride="$AAPT2" clean testDebugUnitTest assembleDebug lintDebug

./tools/inspect_native_16k.sh
```

Also record the target phone page size:

```bash
getconf PAGE_SIZE
```

Do not select or import model files until B3 is reviewed.

## Model source-control rule

Never place model weights in this repository. `*.gguf`, `*.bin`, APKs, native dumps, device logs and profiling captures are ignored at repository level and by this experiment.

## Physical backend proof

The UI displays `requested=npu`, resolved `runtime_id`, and an explicit `REQUESTED != PROVEN` state. The harness does **not** convert the requested compute unit into backend proof. LAB-2B B5 must retain physical GenieX/native log evidence showing which backend actually initialised and executed.

## Third-party source

`GgufVisionConfig.kt` and the centre-crop strategy in `ImagePreprocessor.kt` are adapted from Qualcomm AI Hub Apps at the accepted reference commit. See `THIRD_PARTY_NOTICE.md` and `third_party/QUALCOMM_AI_HUB_APPS_BSD3.txt`.
