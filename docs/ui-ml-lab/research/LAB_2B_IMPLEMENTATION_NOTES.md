# LAB-2B implementation notes

> **Status:** LAB-2B IN PROGRESS. B1 physical Qualcomm reference-app gate passed by user attestation. B2 standalone harness source is prepared. B3 is **REVISE** after the first native/page-size audit exposed one checker defect and genuine 4 KB-aligned upstream HTP payloads.
>
> Nothing here authorises My Mettle `:app`, LAB-1 provider, N-BIO, Room or equipment integration. B4 remains blocked.

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

The frozen AAR remains `0.3.5` while B3 is being classified. Newer AAR inspection is research only and does not silently change the route.

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

Kian ran the prescribed unchanged build/reference-device gate and reported **“Gate passed.”** The B1 result is therefore PASS by physical user attestation.

B3 later supplied the target-device software environment:

```text
Android 16
API 36
Build ID BP4A.251205.006
Samsung S25 Ultra firmware fingerprint supplied in physical-acceptance record
```

The runtime page size is still pending because the Termux PATH used for the first command did not contain `getconf`.

## Exact GenieX 0.3.5 source truth

LAB-2B located the public GenieX source snapshot at:

`da7f27d7f1c6b052153eaa9d59e8aa872c6265a6`

Its `bindings/android/pom.xml` explicitly declares:

```text
com.qualcomm.qti:geniex-android:0.3.5
```

At that exact 0.3.5 source state:

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

The normal My Mettle Android CI run for B2 checkpoint `d999359ad8bb96e64e67950250f2e98e0f176719` completed successfully, confirming the isolated experiment did not break the normal product verification chain.

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

### First execution result

The first physical B3 audit built/located the standalone harness APK and inspected the exact cached `geniex-android:0.3.5` native payload.

It exposed two independent issues.

#### Validator defect

The original audit script incorrectly rejected **valid** `align 2**14` LOAD segments because `${token##*2**}` used shell glob characters while trying to extract the exponent.

The parser was fixed in:

```text
a794e7e22db8b1fda2f840eeb59b9de39ea79993
```

and the audit output was made compact in:

```text
d650e807dc95c19c62855aa13e2435ff7d1d2546
```

The corrected script:

- uses a regex to extract the literal exponent;
- treats `2**14` as PASS;
- fails only values below `2**14`;
- reports ELF machine identity for genuine failures;
- emits a compact `UNALIGNED=...` summary;
- uses non-verbose `zipalign -c -P 16 4` by default.

Full LOAD output remains opt-in with:

```text
LAB2B_VERBOSE_NATIVE=1
```

#### Genuine 4 KB-aligned upstream payloads

The physical output still contains true `align 2**12` files after the false positives are removed conceptually.

The visible APK set includes at least:

```text
libCalculator_skel.so
libQnnHtpV79.so
libQnnHtpV79Skel.so
libQnnHtpV81.so
libQnnHtpV81Skel.so
libQnnNetRunDirectV79Skel.so
libQnnNetRunDirectV81Skel.so
libggml-htp-v68.so
libggml-htp-v69.so
libggml-htp-v73.so
libggml-htp-v75.so
libggml-htp-v79.so
libggml-htp-v81.so
```

The general GenieX / llama.cpp host side visible in the same output — including `libgeniex*`, `libgeniex_plugin_llama_cpp.so`, `libggml.so`, CPU/OpenCL libraries, `libllama*`, `libmtmd.so`, `libnpu_jni.so` and `libomp.so` — reports `2**14`.

This is therefore not evidence that GenieX as a whole is 4 KB-only. The real problem clusters around HTP/QNN/DSP payloads.

`libggml-htp-v81.so` is especially important because the frozen primary route explicitly requests the llama.cpp HTP/NPU lane. The B3 finding is therefore directly relevant to the primary route.

### ZIP packaging result

`zipalign -P 16` passed in the first physical run.

The APK showed native entries as compressed, matching Qualcomm's frozen `jniLibs.useLegacyPackaging = true` setting. That solves/avoids the uncompressed-library ZIP-boundary problem but does not rebuild a 4 KB-aligned ELF.

### Upstream corroboration

Qualcomm GenieX issue `#886` documents the same 4 KB-alignment class in bundled QNN and GGML-HTP files and names several exact libraries seen in the B3 output. A Qualcomm maintainer acknowledged a first attempted SDK update was incomplete and stated another fix would be needed. The issue later closed stale rather than with a demonstrated 16 KB-clean artefact.

Detailed evidence is recorded in:

```text
docs/ui-ml-lab/research/LAB_2B_B3_NATIVE_ALIGNMENT_FINDINGS.md
```

### Newer-version comparison

Qualcomm's latest public GitHub release observed during this review is:

```text
v0.3.19 — 2026-08-07
```

Its Android source uses:

```text
compileSdk = 35
ndkVersion = 29.0.14206865
jniLibs.useLegacyPackaging = true
```

The newer NDK is useful evidence for GenieX-built host code, but the build still copies vendor/HTP prebuilts into the AAR. No public release note or resolved issue currently proves those exact HTP binaries are 16 KB clean.

A research-only helper was therefore added:

```text
tools/compare_geniex_release_16k.sh
```

Default invocation compares Qualcomm's `v0.3.19` GitHub AAR, verifies its published SHA-256, inspects native LOAD alignment, and deletes the temporary download. It does **not** modify the harness dependency.

### Device page size

The first Termux command returned:

```text
getconf: not installed
```

so page size remains unknown. This is a missing tool, not a 4 KB/16 KB result.

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
4. **B3 upstream compatibility evidence:** the frozen 0.3.5 AAR contains genuine `2**12` HTP/QNN/GGML-HTP payloads. This is not yet a route change; it is a required review point.

## Current gate

**B1 PASS. B2 SOURCE PREPARED. B3 REVISE.**

Required before any B4 work:

1. rerun the corrected compact 0.3.5 audit;
2. record actual device page size;
3. run the research-only v0.3.19 AAR static comparison;
4. decide explicitly whether the frozen 0.3.5 route remains acceptable for target-device proof, whether a controlled GenieX version revision is justified, or whether the NPU route must be revised/rejected.

B4 exact model preparation has **not** started.

LAB-2C has **not** started.
