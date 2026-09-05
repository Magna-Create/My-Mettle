# LAB-2B implementation notes

## Current CPU/GPU implementation — 2026-09-05

The authorised CPU/GPU + OCR mission supersedes the historical implementation below. One MNN 3.6.1 runtime now serves exact pinned exports for all three target models; see `LAB_2B_RUNTIME_RESELECTION.md` and the harness `model-registry.json`. There is no GenieX dependency or packaged Qualcomm binary. The standalone root and product source isolation remain intact.

Android DownloadManager owns persistent transfers into `<external-files>/lab2b/models/<id>.partial`. Every required file must complete, match its pinned size and SHA-256, then a verified manifest marker and same-filesystem directory rename activate the installation. Restart discovery checks marker and sizes; load rehashes all assets. No second copy of weights is needed during activation. Remove affects only that model and its staging/runtime cache. Download-state tests distinguish complete transfers from verified installation.

Bundled Latin ML Kit `16.0.1` runs on a separate orientation-normalized full-frame PNG capped at 16 MP / 4096 edge. MNN receives an inspectable full-frame PNG capped at 1600 edge. Both derive from the same copied source and preserve dimensions/hashes/path diagnostics. OCR retains full text, blocks/lines, boxes/corners, exposed language metadata and elapsed time; unknown confidence stays unknown. Deterministic labelled candidate evidence warns about OCR errors and image authority. Cache identity is the normalized image SHA, with stale-image rejection. The three pipeline modes are implemented, VISION + OCR default, auto-OCR before inference when required.

A process singleton owns one engine; blocking work is off-main-thread, phase guards reject a second generation or destructive switch while busy. Conversation state is reset every turn and only the current image is supplied. Model/backend switches dispose the owner first, retain downloads, and require explicit Load. Stop uses an atomic cancellation flag between native steps; uninterruptible current vision/prefill work must return. Activity recreation reattaches a snapshot listener. Process death releases the engine; installations are discovered again. No background inference guarantee is claimed.

System presets and safe UTF-8 `.txt`/`.md` import (64 KiB) are implemented. Model templates accept true system messages; explicit user-preface fallback is tested but not needed by this registry. JSON TEST is not schema-constrained. Decoder input + 512 output tokens must fit the harness 8192-token budget; oversized turns fail explicitly. The transcript is display-only and bounded to 20 turns.

CPU is the default for all models. GPU configures OpenCL text and retains each export's separate CPU vision configuration. MNN's public config dump does not attest actual GPU per-op placement/fallback: diagnostics say UNVERIFIED. A manual failed-correctness observation persists per model. No speed or correctness result is inferred from successful initialization.

Initial real dependency build passed 15 tests and lint (warnings, zero errors); all ten packaged native ELF objects and APK ZIP layout passed static 16 KB checks. Later checkpoint/handoff records contain the final build, added tests and artifact hashes. The first CI run failed because Ubuntu lacked ripgrep; the dedicated workflow now installs it explicitly. There were no runtime native compile/link failures in the first complete build.

The workflow builds only the experiment, never model weights, and uploads the debug APK, audit, reports and a source-matched native bundle for Termux rebuilding. The existing repository-wide Android workflow may also trigger on branch pushes; it was not modified.

**Physical acceptance remains pending.** No S25 device or actual model inference result was available in this workspace. Historical records below are retained as evidence and are not current build instructions.

## Historical GenieX implementation record


> **Status:** LAB-2B IN PROGRESS. B1 physical Qualcomm reference-app gate passed by user attestation. B2 standalone harness source is prepared. B3 is **REVISE** after corrected native/page-size validation confirmed genuine 4 KB-aligned Qualcomm Hexagon payloads in GenieX `0.3.5`.
>
> Nothing here authorises My Mettle `:app`, LAB-1 provider, N-BIO, Room or equipment integration. B4 remains blocked pending the controlled newer-AAR comparison and route review.

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

The harness is still pinned to `0.3.5`. Newer-AAR inspection is research only and does not silently change the route.

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

Physical B3 environment:

```text
Samsung Galaxy S25 Ultra
Android 16
API 36
Build ID BP4A.251205.006
PAGE_SIZE 4096 bytes
```

The exact firmware fingerprint is retained in `LAB_2B_PHYSICAL_ACCEPTANCE.md`.

## Exact GenieX 0.3.5 source truth

LAB-2B located the public GenieX source snapshot at:

```text
da7f27d7f1c6b052153eaa9d59e8aa872c6265a6
```

Its `bindings/android/pom.xml` explicitly declares:

```text
com.qualcomm.qti:geniex-android:0.3.5
```

At that exact source state:

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

The accepted Qualcomm reference app does not check in a generated Gradle wrapper. Its source tooling pins Gradle 9.1.0. The experiment's `gradlew` / `gradlew.bat` are therefore small **pin-enforcing launchers** that refuse anything except Gradle 9.1.0 already present on PATH.

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

The initial load deliberately mirrors the accepted `0.3.5` reference semantics rather than modernising them:

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

Generation uses the same minimal `0.3.5`-era bridge shape as the Qualcomm reference and limits LAB-2B to one current-turn image.

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

The application does not infer NPU use from the requested enum, speed, heat or model support page. B5 must retain actual GenieX/native runtime log evidence before NPU is marked proven.

## B3 native/page-size audit

### Corrected physical result

The corrected audit inspected the exact cached `geniex-android:0.3.5` AAR and built harness APK.

Artefacts:

```text
AAR_SHA256=4a6ad5697bded1ce66ee3e691b2ce49fb2b7f5783db61b413b95b2222f1cb653
APK_SHA256=f845a43bd596ef657fa57178198a7be2387ec7a07019f46e56973d7295bcbcbc
APK_ABIS=arm64-v8a
PAGE_SIZE=4096
```

The initial parser defect that falsely rejected valid `2**14` segments was fixed in:

```text
a794e7e22db8b1fda2f840eeb59b9de39ea79993
```

The corrected rerun confirms exactly 13 failing files in both the AAR and APK. All are identified as:

```text
Machine: Qualcomm Hexagon
minimum LOAD alignment: 2**12
```

Failing set:

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

The general GenieX / llama.cpp Android host side is not implicated by the corrected output. The failing class is specifically Qualcomm Hexagon/HTP payloads.

`libggml-htp-v81.so` is directly relevant to the accepted primary NPU route, so the result cannot be dismissed as unused QAIRT-only content.

### ZIP packaging

The corrected audit reports:

```text
ZIP_ALIGNMENT=PASS
LAB2B_NATIVE_16K=FAIL_ELF_ALIGNMENT
```

The APK retains `jniLibs.useLegacyPackaging = true`, making the native entries compressed. This satisfies the observed ZIP-alignment check but does not alter a precompiled Hexagon ELF whose LOAD alignment is `2**12`.

### Device-runtime interpretation

The physical S25 Ultra reports:

```text
PAGE_SIZE=4096
```

That explains why the earlier reference application can load/init on the target device despite the 16 KB portability failure: this physical phone is currently running a 4 KB page-size environment.

LAB-2B nevertheless made native 16 KB compatibility a hard acceptance item, so the selected `0.3.5` AAR does not pass B3.

### Upstream corroboration

Qualcomm GenieX issue `#886` documents the same 4 KB-alignment class in bundled QNN/GGML-HTP files and names several exact libraries seen here. A Qualcomm maintainer acknowledged that an initial SDK-side update had not fully fixed the problem; the issue later closed stale rather than with a demonstrated clean artefact.

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

The newer NDK may improve libraries rebuilt by GenieX, but the build still packages vendor/HTP prebuilts. No release note is accepted as proof that those exact Hexagon payloads are clean.

A research-only helper therefore exists at:

```text
tools/compare_geniex_release_16k.sh
```

It compares Qualcomm's published `v0.3.19` AAR, verifies its published SHA-256, inspects the native LOAD alignment, and deletes the temporary download. It does **not** modify the harness dependency.

The script was initially created without an executable bit; that repository-mode defect was corrected after the first physical invocation returned `Permission denied`.

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
4. **B3 upstream compatibility evidence:** the frozen `0.3.5` AAR contains 13 genuine `2**12` Qualcomm Hexagon payloads, including `libggml-htp-v81.so`. This is not yet a route change; it is a required review point.

## Current gate

**B1 PASS. B2 SOURCE PREPARED. B3 REVISE.**

Established:

- current target-device page size is `4096` / 4 KB;
- `0.3.5` APK ZIP alignment passes;
- `0.3.5` native 16 KB ELF acceptance fails on 13 Qualcomm Hexagon payloads;
- current target-device runtime viability is not disproven by that portability failure;
- B4 remains blocked because the mission explicitly requires the 16 KB item to be resolved or reviewed before model work proceeds.

Required next step:

1. run the research-only `v0.3.19` static AAR comparison;
2. record whether the exact Hexagon payload class is fixed;
3. make one explicit route decision: keep `0.3.5` only if the hard acceptance item can be legitimately satisfied, adopt one evidence-backed newer GenieX pin after review, or return the NPU route for human review.

B4 exact model preparation has **not** started.

LAB-2C has **not** started.
