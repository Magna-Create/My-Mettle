# LAB-2B implementation playbook

> **Status:** LAB-2A research handoff. **LAB-2B IS NOT STARTED.**
>
> Use this only after human review explicitly authorises LAB-2B.
>
> Source IDs resolve through [`LAB_2A_SOURCE_LEDGER.md`](./LAB_2A_SOURCE_LEDGER.md). Failure IDs resolve through [`LAB_2A_FAILURE_ARCHAEOLOGY.md`](./LAB_2A_FAILURE_ARCHAEOLOGY.md).

## Mission for LAB-2B

Prove one boringly reliable local multimodal path on the target Samsung Galaxy S25 Ultra before My Mettle integrates any local VLM.

The proof is deliberately narrow:

```text
Android app launches
→ GenieX initialises
→ Qwen3-VL-2B Q4_0 bundle is imported from local storage
→ model loads
→ one image is selected
→ one short prompt is supplied
→ image + text reaches VlmWrapper
→ sensible response streams/returns
→ actual backend identity is evidenced
→ process is force-closed/relaunched
→ the same path succeeds again
→ repeated serial inference succeeds
→ stop/unload/reload remain stable
```

No benchmark matrix comes before that sequence.

## 1. Frozen starting route

### Primary route

```text
MODEL
Qwen3-VL-2B-Instruct

MODEL REPOSITORY / ARTEFACT
unsloth/Qwen3-VL-2B-Instruct-GGUF

QUANTISATION
Q4_0

RUNTIME
Qualcomm GenieX Android SDK
runtime_id = llama_cpp

COMPUTE UNIT
npu (explicit for proof)

TARGET
Samsung Galaxy S25 Ultra
Snapdragon 8 Elite / SM8750 class
arm64-v8a
```

Why: current Qualcomm Android API documents this exact Qwen3-VL-2B GGUF VLM path, and Qualcomm's current Android reference app proves the Kotlin/AAR runtime architecture. [SRC-A01, SRC-A04, SRC-A09, SRC-A11]

### Functional fallback inside the same route

If NPU fails after the model artefact is independently shown valid:

```text
same GenieX version
same Q4_0 model files
same image
same prompt
compute_unit = gpu
```

If GPU also fails:

```text
same GenieX version
same Q4_0 model files
same image
same prompt
compute_unit = cpu
```

Do not introduce QAIRT, a second VLM, a second quant or another runtime in the same diagnostic loop.

## 2. Version matrix

### Step-0 reproduction matrix

Begin from Qualcomm's current reference-app source revision and its explicit pins:

```text
qualcomm/ai-hub-apps release commit
  db3f9772d4e423dee2df517335009c703845dba8

AGP
  8.13.0

Kotlin
  2.2.0

JVM / Java
  17

compileSdk / targetSdk
  34 / 34

minSdk
  31

NDK declared by reference app
  27.3.13750724

GenieX Android AAR
  0.3.5

JNI packaging
  jniLibs.useLegacyPackaging = true

ABI
  arm64-v8a
```

[SRC-A10]

### Why `0.3.5` first when later releases exist

The current install docs show `0.3.1`; the current official reference app pins `0.3.5`; official releases include `0.3.17`. That is evidence of documentation/release drift, not permission to choose a version by recency. [SRC-A03, SRC-A10, SRC-A14]

The first physically successful matrix becomes the LAB-2B pin. Start at `0.3.5` because that is the exact current reference-app pin. If a defect is encountered and an upstream issue/PR proves it is fixed in a later release, make **one controlled upgrade** to that specific stable release, record the reason, and restart the acceptance sequence.

Do not iterate versions blindly.

### NDK / CMake rule

GenieX documentation states the Android Maven artefact ships arm64 native libraries and a consumer does not need to author NDK or CMake integration. [SRC-A03]

Therefore:

- it is acceptable for the standalone Gradle project to declare the reference app's NDK version initially;
- do **not** create `CMakeLists.txt`;
- do **not** compile GenieX/QNN/llama.cpp from source;
- do **not** create custom JNI.

## 3. Step 0 — reproduce Qualcomm's reference app before writing the harness

This is mandatory implementation archaeology validation.

1. Fetch/checkout `qualcomm/ai-hub-apps` at commit `db3f9772d4e423dee2df517335009c703845dba8`.
2. Open only `geniex_chat_android`.
3. Do not modify My Mettle.
4. Confirm Gradle resolves the pinned GenieX AAR.
5. Build/install the official debug reference APK on the S25 Ultra.
6. If its catalog currently exposes Qwen3-VL-2B, run the exact model. If it does not, do **not** edit the app yet; record that catalog limitation and move to the documented API/localfs path in the standalone harness.
7. Record:
   - Android build/firmware;
   - exact AAR version;
   - app commit;
   - whether GenieX SDK initialises;
   - whether model-manager operations work;
   - whether any native-load error occurs before model inference.

**PASS:** reference app itself launches and GenieX initialises on the target phone.

If the untouched official app cannot initialise, diagnose that before creating the harness. Do not hide a runtime/platform failure inside new Kotlin code.

## 4. Model preparation — outside the APK

### Required files

Prepare the exact Qwen3-VL-2B-Instruct GGUF `Q4_0` bundle outside the Android project.

A VLM requires:

- the Q4_0 main model GGUF;
- the appropriate `mmproj-*.gguf` projection file.

Do not guess projector pairing from another VLM. Preserve the repository layout/filenames and record SHA-256 for every imported file.

### Preferred LAB-2B import path

Use GenieX's documented local filesystem import:

```kotlin
ModelManagerWrapper.pullFlow(
    ModelPullInput(
        model_name = "local/qwen3-vl-2b-instruct-q4_0",
        precision = "Q4_0",
        hub = HubSource.LOCALFS,
        local_path = sourceDirectory,
    )
)
```

Then:

```kotlin
val paths = ModelManagerWrapper.getPaths(
    "local/qwen3-vl-2b-instruct-q4_0"
) ?: error("model not imported")
```

The current GenieX model docs say LOCALFS import copies/imports a local GGUF directory into the SDK cache and that a VLM projector should live beside the GGUF. [SRC-A06]

### How to get the source directory onto the phone

This is a developer harness, so use the least product-like mechanism that keeps the runtime test clean:

1. place the downloaded model directory in a developer-accessible device location such as `/data/local/tmp/lab2b-qwen3-vl-2b/` via `adb push`, **or** select a local directory/file through a minimal storage picker and copy it to a harness-controlled staging directory;
2. call ModelManager LOCALFS import from that source;
3. after successful manager import and path verification, the source copy may be removed to avoid double storage if GenieX has fully copied it.

Do **not** manually write into GenieX's cache/data structure. [SRC-A06]

### Initial downloader rule

No production network downloader belongs in LAB-2B. If the current Android binding cannot read the developer-staged local path due sandbox/storage constraints, add only the minimum developer file-import step necessary to copy the source into app-accessible storage. Do not build resumable product download UX yet.

## 5. Minimal Android project

Create a standalone repository/directory outside My Mettle, or another isolated workspace explicitly approved for LAB-2B. Do not add an Android module to `Magna-Create/My-Mettle` during the proof.

Minimum files:

```text
lab2b-vlm-harness/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/dev/kian/lab2b/
      MainActivity.kt
      HarnessRuntimeOwner.kt
      HarnessState.kt
      BackendEvidence.kt
    src/main/res/...
```

No Room. No DataStore unless absolutely required for a one-line developer preference. No Hilt/Koin. No networking library. No CameraX. No OCR. No N-BIO.

## 6. Kotlin runtime owner

Create one Kotlin class/object that is the sole owner of `VlmWrapper`.

Conceptual state:

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

This state machine is harness-local diagnostics, not a new My Mettle provider contract.

Responsibilities:

- initialise GenieX once;
- import/resolve model paths;
- create one VLM wrapper;
- expose one serial generate operation;
- expose stop;
- expose reset;
- destroy wrapper on explicit unload/test teardown;
- emit diagnostic state/timing/backend evidence;
- never expose raw native handles to Compose.

### Threading

Use one dedicated coroutine scope or serial dispatcher for model operations. The reference app uses `Dispatchers.IO`; matching that is the minimum-risk first implementation. [SRC-A11]

One request at a time. An active request disables/rejects another request. Qualcomm's reference code explicitly notes that concurrent generation on the same native handle can crash. [FAIL-015]

Do not attempt parallel image encoding/generation in the first harness.

## 7. Model load configuration

Resolve model paths first and log them:

```text
model_name
model_path
mmproj_path
runtime_id
compute_unit if reported
file sizes
SHA-256 identities
```

For primary GGUF path:

```kotlin
VlmCreateInput(
    model_name = paths.model_name,
    model_path = paths.model_path,
    mmproj_path = requireNotNull(paths.mmproj_path),
    config = ModelConfig(
        nCtx = 4096,
        // keep remaining tuning minimal
    ),
    runtime_id = paths.runtime_id,
    compute_unit = "npu",
)
```

Do not hard-code `runtime_id="llama_cpp"` without also verifying `paths.runtime_id`; the manager's resolved runtime is authoritative. [SRC-A04, SRC-A11]

### Context

Start with `nCtx = 4096`, consistent with current Android API guidance. Do not increase it until one-image reliability is proven.

The reference app documents real multi-image/context failures at 1024 and now sizes context based on image token cost. [SRC-A11, SRC-A12]

## 8. Image path and preprocessing

### Input acquisition

Use Android's system photo/document picker. Do not add camera capture.

Copy the selected image into app-private harness storage so inference receives a stable ordinary filesystem path. Record original dimensions and copied path in diagnostics but do not log unrelated user media metadata.

### Projector geometry

Use the model's actual vision-projector geometry.

The current Qualcomm sample reads these GGUF keys from `mmproj_path`:

```text
clip.vision.image_size
clip.vision.patch_size
clip.vision.spatial_merge_size
```

[SRC-A12]

For the initial harness either:

- reuse the same small read-only GGUF-header approach from the BSD-3 reference source with proper attribution; or
- if the pinned GenieX AAR has since exposed equivalent geometry, use the SDK API instead.

Do not assume 448, 560 or 768 merely from a model family name.

### Transform

For the first proof:

1. decode the selected image;
2. make one square crop/resize to the projector's declared size, following the reference sample;
3. save a high-quality JPEG into app-private storage;
4. supply the absolute file path to GenieX.

This mirrors the current reference implementation and eliminates URI/provider ambiguity from the native path. [SRC-A11, SRC-A12]

Do not optimise crop semantics for equipment recognition during LAB-2B.

## 9. Prompt and media call order

Construct exactly one user turn:

```kotlin
val message = VlmChatMessage(
    role = "user",
    contents = listOf(
        VlmContent("image", imageFile.absolutePath),
        VlmContent("text", prompt),
    ),
)
```

Then:

```text
applyChatTemplate([message], ...)
→ formattedText
→ injectMediaPathsToConfig([message], generationConfig)
→ generateStreamFlow(formattedText, configWithMedia)
```

This ordering is directly supported by current Qualcomm Android source/docs. [SRC-A04, SRC-A11]

**Do not pass raw prompt text to generation.** [FAIL-013]

**Do not pass historical images.** Only the current turn's media enters `injectMediaPathsToConfig`. [FAIL-014]

## 10. Streaming and cancellation

Collect `generateStreamFlow` serially.

Record:

- load elapsed time;
- time to first token if exposed;
- prompt token count;
- generated token count;
- prefill/decode rate if the pinned SDK exposes the same profile fields as the reference app;
- completion/error/cancel state.

These metrics are diagnostics, not a benchmark leaderboard.

Cancellation test:

1. start one generation;
2. after visible output, call `stopStream()` once;
3. await terminal state;
4. submit a new normal request;
5. it must either succeed or produce a deterministic documented reset requirement.

Do not cancel by killing the coroutine while leaving the native generation uncoordinated unless the SDK contract for the pinned version explicitly says that is safe.

## 11. Backend selection and proof

### NPU acceptance evidence

A LAB-2B NPU PASS requires:

```text
requested compute_unit = npu
AND
resolved model runtime_id = llama_cpp
AND
GenieX/native logs show Hexagon/HTP backend/session/device activity
AND
the image+text response completes
```

Use `adb logcat` during load and generation. Enable the highest documented useful GenieX debug level available to the pinned Android build without modifying native code.

Preserve the exact log excerpt showing the resolved accelerator. Likely markers include HTP/Hexagon device/session/backend creation; exact strings must be taken from the pinned version rather than hard-coded into this playbook.

### CPU control

After NPU success, or to distinguish model validity from NPU failure, repeat with:

```text
compute_unit = cpu
same model cache
same image
same prompt
```

The backend log must change accordingly. Different timing is corroboration only.

### GPU fallback

Only use `gpu` after the NPU path either passes and you intentionally exercise fallback, or fails with evidence that the model itself is valid.

Do not start profiling all three devices until the primary reliability acceptance is complete.

## 12. Lifecycle acceptance

The following must pass before LAB-2B can say the runtime is stable enough for LAB-2C consideration.

### A. Fresh launch

- launch app;
- initialise GenieX;
- resolve/import model;
- load;
- one image + prompt succeeds.

### B. Force-close/relaunch

- force-stop app;
- relaunch;
- model manager sees complete cached model;
- wrapper loads again;
- same inference succeeds.

### C. Two serial requests

- keep wrapper loaded;
- run request 1;
- wait complete;
- run request 2 with one image;
- no race/crash.

Prefer a fresh single-turn message/reset for this proof rather than growing conversation history.

### D. Stop/recover

- cancel one generation using SDK stop;
- run another request;
- document whether reset is needed.

### E. Explicit unload/reload

- `stopStream()` if needed;
- `destroy()`;
- clear reference;
- create wrapper again;
- inference succeeds.

### F. Activity recreation

Once A-E pass, trigger Activity recreation/rotation once. The process-scoped owner should keep or deliberately recreate its wrapper without duplicate native initialisation. Record behaviour.

### G. Background/resume

Once A-F pass, background for several minutes and resume. No automatic unloading policy is invented yet; simply observe stability.

## 13. Memory and thermal observations

Only after correctness and restart/repeatability:

Record at minimum:

```text
APK installed size
GenieX model-cache size
cold load duration
RSS/PSS before load
RSS/PSS after load
peak during one image inference
memory after destroy
TTFT
prefill speed if exposed
decode speed if exposed
battery/temperature before and after a small repeated set
```

Use ordinary Android diagnostics such as `adb shell dumpsys meminfo <package>` plus SDK profile output.

Do not create a benchmark framework. A handful of controlled repeats is enough to expose catastrophic memory/thermal behaviour.

## 14. Native packaging audit

Before calling LAB-2B integration-ready:

1. inspect APK/AAR native entries;
2. verify only intended ABIs are packaged for the harness;
3. record GenieX AAR/native library versions/hashes where practical;
4. run Android's 16 KB page-alignment check against the exact packaged `.so` set or a 16 KB test environment.

Android requires 16 KB-compatible native libraries for modern target/Play compatibility. [SRC-B05]

If the AAR is not 16 KB-compatible, the runtime may still pass the S25 proof but LAB-2B verdict must be **REVISE ROUTE / WAIT FOR FIX**, not production-ready.

## 15. Model-integrity evidence

LAB-2B should record a small machine-readable acceptance note, not a Room database:

```text
model repo/id
quantisation
main GGUF filename + bytes + SHA256
mmproj filename + bytes + SHA256
GenieX AAR version
reference-app/source commit
runtime_id
requested compute unit
resolved/backend log evidence
Android build/device model/SOC
first successful timestamp
```

The harness may calculate hashes once on import and cache them in a small developer file if hashing ~2 GB every launch is wasteful. Do not build the final My Mettle lifecycle persistence in LAB-2B.

## 16. Failure decision tree

### App fails before GenieX init

Check:

1. AAR/native packaging;
2. ABI;
3. Android 16 KB/native loading/logcat;
4. untouched reference app on same device.

Do not change model files yet.

### GenieX init succeeds, LOCALFS import fails

Check:

1. local path access;
2. complete GGUF + mmproj files;
3. canonical `Q4_0`;
4. file SHA256/size;
5. model manager error event.

Do not switch runtimes.

### Import succeeds, wrapper build fails

Check:

1. `ModelPaths` contents;
2. `runtime_id`;
3. non-null mmproj;
4. NPU backend logs.

Then run **same model on CPU**. If CPU loads, the artefact is probably functional and the NPU path is isolated for diagnosis.

### Wrapper loads, output is nonsense/empty

Check:

1. `applyChatTemplate().formattedText` is being passed;
2. media paths injected for current turn;
3. image file readable;
4. projector geometry/preprocessing;
5. context budget.

Do not immediately change quant/model.

### First request works, second crashes

Check:

1. accidental concurrency;
2. historical media replay;
3. wrapper reset/state;
4. context growth;
5. whether destroy/recreate restores stability.

Treat this as a lifecycle failure, not a pass.

## 17. Exact first implementation sequence

When LAB-2B is authorised, do these steps in order:

1. **Freeze evidence:** record current GenieX main/release app commit, model repo revision and AAR availability.
2. **Build untouched Qualcomm reference app** at `db3f977...` with AAR 0.3.5 on the S25 Ultra.
3. **Record runtime-init result** before editing or creating a harness.
4. **Prepare Qwen3-VL-2B Q4_0 + mmproj** outside the APK; record SHA256 and sizes.
5. **Create tiny standalone Kotlin Android project** matching reference toolchain pins; add only GenieX AAR.
6. **Initialise GenieX** and show init success/failure in one diagnostic screen.
7. **Import model through LOCALFS**; resolve `ModelPaths`; print/log paths/runtime_id.
8. **Build one `VlmWrapper` off main thread** with explicit `compute_unit="npu"` and nCtx 4096.
9. **Select one image** with system picker; copy/preprocess to app-private file using projector geometry.
10. **Send one fixed short prompt** through chat template + current-turn media injection.
11. **Stream one response** and record completion/profile.
12. **Capture backend log evidence** proving or disproving HTP/NPU.
13. **Force-close/relaunch and repeat** the exact same path.
14. **Run a second serial request** without concurrent access.
15. **Test stop → recovery**.
16. **Destroy/unload → reload → infer**.
17. **Only now** collect memory/timing/thermal observations.
18. If NPU failed but model works, repeat **one** controlled GPU run; CPU only if required.
19. Produce `PASS`, `REVISE ROUTE` or `REJECT ROUTE` with exact evidence.
20. **STOP. Do not integrate My Mettle.** LAB-2C requires separate approval.

## 18. Things LAB-2B must NOT do initially

- Do not modify My Mettle.
- Do not add Room.
- Do not add N-BIO.
- Do not add equipment schemas or semantics.
- Do not add ML Kit OCR.
- Do not add CameraX.
- Do not create polished UI.
- Do not create a production downloader.
- Do not add cloud/server code.
- Do not add QAIRT just in case.
- Do not add ONNX Runtime, LiteRT-LM, ExecuTorch or direct llama.cpp alongside GenieX.
- Do not build custom JNI/CMake/QNN libraries.
- Do not try multiple Qwen sizes.
- Do not compare five quantisations.
- Do not multi-image benchmark.
- Do not add conversation memory/history before one-turn repeat reliability.
- Do not optimise prompt quality.
- Do not profile power before correctness.
- Do not assume NPU execution from speed.
- Do not upgrade all Gradle/Kotlin/runtime versions at once.

## 19. LAB-2B verdict rules

### PASS

Requires:

- exact model files identified;
- one-image VLM response succeeds;
- force-close/relaunch succeeds;
- repeated serial inference succeeds;
- stop/unload/reload is stable;
- actual backend is evidenced;
- memory/storage are plausible for the device;
- no unresolved native packaging blocker prevents future app integration.

### REVISE ROUTE

Use when the basic route is viable but a bounded issue exists, such as:

- reference AAR version needs one documented upgrade;
- NPU fails while GPU same-stack path is stable and evidence suggests a fixable Hexagon issue;
- exact AAR has a 16 KB packaging blocker awaiting upstream fix;
- localfs/model-manager path needs a documented current API adjustment.

### REJECT ROUTE

Use only when evidence shows the selected GenieX/Qwen3-VL route cannot be made stable on the target device without replacing major components or entering open-ended native integration work.

Do not call a single NPU failure a route rejection if the exact model has not yet been proven on CPU/GPU.

## 20. Handoff to LAB-2C

Even a LAB-2B PASS does not mean GenieX becomes product architecture automatically.

LAB-2C must still:

- implement a `LOCAL` adapter behind LAB-1 types;
- map install/verify/remove to `LocalModelLifecycle`;
- preserve task-specific capabilities;
- keep provider/runtime types out of Compose/product contracts;
- make app-private model lifecycle/version/integrity explicit;
- handle system-provider future sufficiency and local retirement;
- repeat build/ABI/page-size checks inside the real app toolchain;
- remain Lab-only until separately reviewed.

**STOP after LAB-2B evidence.**
