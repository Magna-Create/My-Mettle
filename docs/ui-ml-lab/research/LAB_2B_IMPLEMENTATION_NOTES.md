# LAB-2B implementation notes

> **Status:** LAB-2B IN PROGRESS. B1 physical Qualcomm reference-app gate passed by user attestation. B2 standalone harness source is prepared. Current stop target is B3 static/native/page-size validation.
>
> Nothing here authorises My Mettle `:app`, LAB-1 provider, N-BIO, Room or equipment integration.

## Starting source truth

- Working branch: `agent/ui-ml-lab`.
- LAB-2B starting Lab HEAD: `13fcff8e608e18e3ac4faa232d17c98da25750df` (`Preserve LAB-2A governance detail at closure`).
- Live N-BIO at entry: `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de` (`Clarify Context Module scientific status`).
- N-BIO sync: **none**.
- LAB-2A: explicitly human accepted.

## Frozen primary route

```text
Qwen3-VL-2B-Instruct
→ unsloth/Qwen3-VL-2B-Instruct-GGUF
→ Q4_0 + matching mmproj
→ Qualcomm GenieX Android AAR 0.3.5
→ ModelManager LOCALFS import
→ resolved runtime_id must be llama_cpp
→ explicit compute_unit = npu
→ Samsung Galaxy S25 Ultra / Snapdragon 8 Elite
```

GPU then CPU remain same-stack diagnostics only if the NPU path fails and a controlled fallback is required.

## B0/B1 result

Qualcomm reference app:

- repository `qualcomm/ai-hub-apps`;
- commit `db3f9772d4e423dee2df517335009c703845dba8`;
- app `geniex_chat_android`;
- application ID `com.geniex.demo`;
- AAR `com.qualcomm.qti:geniex-android:0.3.5`.

Reference matrix:

| Component | Pin |
| --- | --- |
| AGP | `8.13.0` |
| Kotlin | `2.2.0` |
| Gradle | `9.1.0` |
| Java | 17; source utility pins `17.0.16-ms` |
| compileSdk / targetSdk | `34 / 34` |
| minSdk | `31` |
| NDK | `27.3.13750724` |
| JNI packaging | `jniLibs.useLegacyPackaging = true` |
| ABI | `arm64-v8a` |

Kian ran the prescribed unchanged build/reference-device gate and reported **“Gate passed.”** The B1 result is therefore PASS by physical user attestation. Raw page-size/build/log values were not pasted and are not fabricated; page size is requested again at B3.

## Exact GenieX 0.3.5 source truth

LAB-2B additionally located the public GenieX source snapshot at:

`da7f27d7f1c6b052153eaa9d59e8aa872c6265a6`

Its `bindings/android/pom.xml` explicitly declares:

```text
com.qualcomm.qti:geniex-android:0.3.5
```

This matters because it proves the harness is not accidentally coded against a later Android API. At that exact 0.3.5 source state:

- `HubSource.LOCALFS` exists;
- `ModelPullInput` includes `local_path`;
- `VlmCreateInput` includes `model_name`, `model_path`, `mmproj_path`, `config`, `runtime_id`, `compute_unit`;
- `ModelConfig` has `nCtx`, `nThreads`, `nBatch`, `nUBatch`, `nGpuLayers`, `enable_thinking`;
- `VlmWrapper` exposes `applyChatTemplate`, `injectMediaPathsToConfig`, `generateStreamFlow`, `reset`, `stopStream`, `destroy`;
- `GenieXSdk.init` initialises native plugins/model manager and loads `npu_jni`.

This removes the main API-version uncertainty left after LAB-2A.

## B2 standalone project

Path:

```text
experiments/lab2b-vlm-harness/
```

It is its own Gradle root and is not included from My Mettle's root `settings.gradle.kts`.

Package/application ID:

```text
dev.kian.lab2b.vlm
```

Label:

```text
LAB-2B VLM Harness
```

No Room, DataStore, DI framework, Retrofit/OkHttp, CameraX, OCR, N-BIO, My Mettle source dependency or product provider exists.

The harness manifest intentionally requests **no INTERNET permission**. Model acquisition remains a developer action outside the app; import is LOCALFS only.

### Gradle launcher decision

The accepted Qualcomm reference app itself does not check in a generated Gradle wrapper. Its source tooling pins Gradle 9.1.0. To preserve that source truth without copying My Mettle's newer 9.3.1 wrapper, the experiment's `gradlew` / `gradlew.bat` are small **pin-enforcing launchers** that refuse anything except Gradle 9.1.0 already present on PATH.

This is an explicit controlled reproduction choice, not a claim that Qualcomm ships a wrapper.

## Runtime ownership

One process-scoped Kotlin object owns the runtime:

```text
HarnessRuntimeOwner
```

States:

```text
IDLE
IMPORTING
IMPORTED
LOADING
READY
GENERATING
STOPPING
UNLOADING
FAILED
```

Rules:

- expensive work uses an application-owned coroutine scope on `Dispatchers.IO`;
- exactly one `VlmWrapper` is owned;
- concurrent generation is rejected;
- Activity recreation changes the listener, not native ownership;
- stop is explicit through `stopStream()`;
- unload is explicit `stopStream()` → `destroy()`;
- destroy/stop errors become visible `FAILED` state rather than silent replacement;
- process death is recovered through `ModelManagerWrapper.getPaths(LOCAL_MODEL_NAME)` after SDK init.

## Model import workflow

B2 implements only the mechanism; B4 exact files remain gated.

```text
Android ACTION_OPEN_DOCUMENT_TREE
→ copy top-level *.gguf to app-private inflight staging
→ calculate SHA-256 while copying
→ require exactly one main GGUF + one mmproj GGUF
→ atomically rename staging directory
→ ModelManagerWrapper.pullFlow(
     model_name = local/qwen3-vl-2b-instruct-q4_0,
     precision = Q4_0,
     hub = LOCALFS,
     local_path = private staging directory
   )
→ getPaths()
→ require model_path + mmproj_path + nonblank runtime_id
→ require runtime_id == llama_cpp for the primary route
```

A completed file copy is not `READY`: manager import and resolved runtime paths are required before the harness reaches `IMPORTED`.

No code writes directly into GenieX internal cache directories.

## NPU load configuration

The initial load deliberately mirrors the accepted 0.3.5 reference semantics rather than modernising them:

```text
compute_unit = ComputeUnitValue.NPU.value  // "npu"
nGpuLayers = 999
nCtx = 4096
nThreads = 4
nBatch = 1
nUBatch = 1
enable_thinking = false
```

`nGpuLayers = 999` is retained because the exact Qualcomm reference app sets it when NPU is selected on its GGUF path. It is a reproduction pin, not a performance recommendation.

## Image path

One image only:

```text
ACTION_OPEN_DOCUMENT
→ copy source into app-private files
→ read projector geometry from resolved mmproj
→ centre crop/resize to projector-declared image_size
→ write one private JPEG
→ use absolute filesystem path in VlmContent("image", ...)
```

The harness refuses to load the VLM when usable projector geometry cannot be read from the mmproj. Unlike the reference demo, it does not use a hard-coded fallback image size, because LAB-2B is validating correctness rather than maximising demo tolerance.

`GgufVisionConfig.kt` and the centre-crop strategy are adapted from Qualcomm AI Hub Apps at the accepted reference revision under BSD-3; attribution and licence text are retained inside the experiment.

## Prompt / generation

Default developer prompt:

```text
Describe the equipment in this image briefly.
```

One-turn call order:

```text
VlmChatMessage(image + text)
→ applyChatTemplate(...)
→ injectMediaPathsToConfig(current turn only)
→ generateStreamFlow(formattedText, config)
```

Generation uses the same minimal 0.3.5-era bridge shape as the Qualcomm reference and limits LAB-2B to one current-turn image.

## Backend evidence

The harness records separately:

- requested compute unit: `npu`;
- manager-resolved `runtime_id`;
- backend proof state.

Initial proof state is deliberately:

```text
UNPROVEN
REQUESTED != PROVEN
```

The application does not infer NPU use from the requested enum, speed, heat or model support page. B5 must retain actual `GenieXSdk`/native runtime log evidence before NPU is marked proven.

## B3 native/page-size audit

`tools/inspect_native_16k.sh` is designed to run only after the nested app builds. It:

1. locates the exact resolved Maven AAR `geniex-android:0.3.5`;
2. records AAR/APK SHA-256;
3. inventories AAR and APK `.so` files;
4. requires the built harness APK to contain only `arm64-v8a`;
5. applies Android's current `llvm-objdump -p <so> | grep LOAD` criterion and rejects LOAD alignment below `2**14`;
6. reports RELRO presence diagnostically;
7. runs `zipalign -v -c -P 16 4 <apk>`.

Android's current guidance requires Build-Tools 35.0.0+ for that inspection even though the frozen app compile/target SDK remains 34. Installing inspection tooling does not modernise the app matrix.

The actual S25 page-size command remains:

```text
getconf PAGE_SIZE
```

B3 is not pre-filled.

## Pure unit tests

The harness adds tests for:

- legal state gating;
- concurrent generation rejection;
- no unload race during active generation;
- one-main + one-mmproj bundle validation;
- non-empty file/hash validation;
- explicit distinction between requested and proven backend.

No mock claims to prove GenieX inference or NPU execution.

## Source safety

`tools/verify_source_safety.sh` verifies the LAB-2B diff does not touch My Mettle build/runtime paths, the root settings do not include the harness, model/runtime binaries are not tracked, INTERNET is absent, and the experiment does not import product/My Mettle/N-BIO/Room/dependency shortcuts.

Root and experiment `.gitignore` rules aggressively exclude model files, APK/AAB/native outputs, device logs, traces and dumps.

## Deviations from LAB-2A playbook

1. **Gradle pin completed:** the accepted reference source establishes Gradle 9.1.0; LAB-2A had not named it.
2. **No generated wrapper:** because the accepted Qualcomm app has no wrapper, the harness uses a 9.1.0 pin-enforcing launcher rather than importing My Mettle's newer wrapper.
3. **Projector metadata failure is hard:** the harness refuses magic image-size fallback during proof.

No runtime/model/framework deviation has occurred.

## Current gate

**B1 PASS. B2 SOURCE PREPARED. B3 STATIC/NATIVE/PAGE-SIZE VALIDATION PENDING.**

B4 exact model preparation has **not** started. LAB-2C has **not** started.
