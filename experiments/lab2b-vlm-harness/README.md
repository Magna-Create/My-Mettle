> **0.3 extension:** see `docs/ui-ml-lab/research/LAB_2B_THINKING_CROPS.md` for thinking controls, E4B, reviewed crops, measured result export and current Termux commands. Earlier build hashes and instructions below describe 0.2. Physical acceptance of the extension is pending.

# LAB-2B VLM Harness

**Cancellation/rejection lifecycle:** Stop or a failed turn unloads the runtime after the current native operation returns. Tap Load before another turn; model files remain installed. MNN clears multimodal embeddings in prefill, while `reset()` alone does not clear pending embeddings from a turn rejected/cancelled before prefill. Disposal prevents historical-image reuse on that path. Completed turns retain the loaded engine and reset conversation state.


Standalone Android Gradle root. **Never include it from My Mettle root settings.** LAB-2C is not started. Physical S25 Ultra acceptance remains pending.

## Install and exercise

Install the debug APK from [successful run 33942676654](https://github.com/Magna-Create/My-Mettle/actions/runs/33942676654), artifact `lab2b-vlm-harness-debug`. Final APK SHA-256: `b670336d205837e71d9c0633ec2029f7a0922fb7978792e14b9a9a4edaddb1a2`. Package: `dev.kian.lab2b.vlm`, label **LAB-2B VLM Harness**, Android 12+ ARM64. If Android reports a signing mismatch against an older harness, uninstall that harness first (this removes its private downloads).

1. Choose **Qwen3.5-2B**, **CPU**, and **Download** (1.381 GB; Wi-Fi recommended). Wait for INSTALLED after SHA verification. Downloads may use metered data and continue through process death; system-paused transfers show their reason.
2. **Load**. Weights are rehashed before native load; LOADING includes that verification, while cold-load timing measures the native constructor only.
3. Keep **ENGLISH GROUNDED** and **VISION + OCR**. Tap the built-in **HELLO / 1234** control (or select your image), then **Run OCR**. Inspect/copy the raw OCR and open both the OCR image and exact prepared model input.
4. Enter an instruction and **Send**. The model receives a real image plus explicitly labelled OCR candidate evidence. The response streams into the text area. **Stop** is cooperative between native steps; vision/prefill must return before cancellation finishes.
5. Repeat with **VISION ONLY**, then **OCR ONLY**. OCR ONLY sends text evidence, with no image. Turns are stateless; the visible transcript is not model history.
6. Test the red-square control, a real object and an equipment placard. Establish CPU correctness before selecting experimental **GPU** (OpenCL text, CPU vision). Model/backend switches unload first; then tap Load. If initialization fails, explicitly select CPU. If content is wrong, use **GPU result incorrect → mark failed**; the per-model observation persists.
7. **Unload**, force-close/relaunch, confirm INSTALLED, reload without downloading. Download a second model and switch back and forth. Remove deletes only the selected model and its staging/cache files.

Other models: **Qwen3-VL-2B-Instruct** (1.474 GB), **Gemma 4 E2B IT** (3.144 GB). All three have pinned upstream MNN artefacts and implemented routes; none is labelled physically validated without a device result. No weights are inside the APK. Allow the model size plus 256 MiB free storage for downloads, and additional space for MNN runtime caches.

## Reproducibility and diagnostics

`model-registry.json` records exact repository, immutable revision, every required filename, byte size and SHA-256. `ModelRegistry.kt` is the typed application registry. Model format is MNN graph + external weights + tokenizer + separate vision graph; Gemma additionally uses PLE embeddings. Model source cards declare Apache-2.0. No accounts or credentials are used for downloads.

System presets: NONE, ENGLISH GROUNDED, JSON TEST (instruction following, not schema enforcement). Import `.txt`/`.md` through the Android document picker, UTF-8 <=64 KiB; name, bytes, SHA and active/inactive status are displayed. Clear selects NONE. Imported text persists, but the active preset resets to ENGLISH GROUNDED after process restart. All three installed templates accept a system role; explicit user-preface compatibility support is tested for any future model requiring it, never silently enabled.

OCR uses bundled Latin ML Kit v2 `16.0.1`. The orientation-normalised, white-composited full-frame OCR PNG is capped at 16 MP / 4096 pixels per edge for memory safety. A separate full-frame PNG, maximum edge 1600, is passed by exact private path to MNN. No square crop. Original dimensions, bytes, hash, EXIF orientation, both derived paths/dimensions/hashes are visible. MNN still applies its internal model-specific image resampling/patching. OCR retains blocks, lines, rectangles, corner points, exposed language metadata, processing time, source hash and dimensions. No confidence is fabricated. Four OCR results are cached in memory by normalized image SHA-256; stale evidence cannot be sent.

Diagnostics show requested backend, effective text/vision evidence, native timings, TTFT, generation time, PSS before/load/unload, installed bytes, staging/cache bytes, last assembled prompt and last system-role mode. CPU is explicit; OpenCL configuration cannot prove per-operator GPU placement, so effective text is UNVERIFIED for GPU requests. GPU is experimental and disabled by default for every model. Prepared images and transcript are session-only; force-close orphans are reclaimed on restart. Model files persist under `getExternalFilesDir(null)/lab2b/models/<model-id>`.

## Desktop / CI build

Pins: Java 17, Gradle 9.1.0, AGP 8.13.0, Kotlin 2.2.0, SDK/target 35, minimum 31, NDK 27.3.13750724, CMake 3.22.1, ARM64 only. `gradlew` is a pin-checking launcher and requires Gradle on PATH.

```bash
cd experiments/lab2b-vlm-harness
sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.3.13750724' 'cmake;3.22.1'
python3 tools/prepare_mnn.py
./gradlew --no-daemon testDebugUnitTest assembleDebug lintDebug
bash tools/verify_source_safety.sh
python3 tools/inspect_native_16k.py
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

Set ANDROID_HOME or local.properties; install ripgrep for the isolation check. The bootstrap downloads only the SHA-pinned MNN 3.6.1 official Android prebuilt and matching headers, not weights. It compiles one small JNI bridge, not MNN. The final runtime excludes GenieX. Native validation checks every APK `.so`, ARM64 machine, all LOAD alignments and congruence, and `zipalign -c -P 16 4`. This is static compatibility, not a 16 KB device boot test.

## Termux rebuild fallback

The Actions artifact also contains `lab2b-native.zip` and `lab2b-native.sha256`, exported from the audited APK. Use the native bundle from the **same source revision**. The importer verifies the published ZIP hash, each library hash and the C++/adapter source hashes before activation. This avoids attempting to execute Linux x86_64 NDK tools on an ARM64 phone. It is a rebuild using verified native prebuilts; changing the native bridge requires desktop/CI rebuilding.

With the existing Android SDK platform 35/build-tools and Gradle 9.1.0 configured in Termux, place these two artifact files in this harness directory, then run exactly:

```bash
pkg install openjdk-17 aapt2 python
cd ~/My-Mettle/experiments/lab2b-vlm-harness
python tools/native_bundle.py import lab2b-native.zip --sha256 "$(awk '{print $1}' lab2b-native.sha256)"
./gradlew --no-daemon -Plab2bPrebuiltNative=true \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" \
  testDebugUnitTest assembleDebug lintDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/LAB-2B-VLM-Harness-debug.apk
```

Open the copied APK from Samsung My Files to install. `~/storage/downloads` requires Termux storage access (`termux-setup-storage` if not already granted).

The prebuilt Gradle path is validated on the build host; physical Termux execution still needs Kian's device. These commands assume the same existing SDK/Gradle environment used for the earlier harness; `pkg` alone does not install Android SDK platforms or the pinned Gradle distribution. Downloading the already-built APK is the immediate installation route.

## Acceptance records

See `docs/ui-ml-lab/research/LAB_2B_RUNTIME_RESELECTION.md`, `LAB_2B_IMPLEMENTATION_NOTES.md`, `LAB_2B_PHYSICAL_ACCEPTANCE.md` and `LAB_2B_CHECKPOINTS.md`. Unit tests cover infrastructure and prompt semantics. They do not simulate model output and do not prove inference correctness. The historical Qualcomm image failures are not attributed to MNN.
