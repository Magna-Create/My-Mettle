# LAB-2B morning handoff — 5 September 2026

**LAB-2B IMPLEMENTATION READY / PHYSICAL ACCEPTANCE PENDING. LAB-2B is not COMPLETE. LAB-2C NOT STARTED.**

## Install first

[Download from successful Actions run 33942676654](https://github.com/Magna-Create/My-Mettle/actions/runs/33942676654). Open its **lab2b-vlm-harness-debug** artifact (ID **9962381669**), extract it, and install `outputs/apk/debug/app-debug.apk` through Samsung My Files. The package is `dev.kian.lab2b.vlm`, label **LAB-2B VLM Harness**, Android 12+ / ARM64. It is separate from My Mettle. An older harness signed by a different debug key may need uninstalling first, which removes that harness's private data.

**APK size:** 36,326,209 bytes. **APK SHA-256:**

```text
b670336d205837e71d9c0633ec2029f7a0922fb7978792e14b9a9a4edaddb1a2
```

The complete downloadable artifact ZIP is 17,996,004 bytes, SHA-256 `d365ad3e18906eb742af7442cdd4e643586b289aa11d4945954ab79fb894a25e`. It contains the APK, native audit, test/lint reports and the matching Termux native bundle/checksum. No model weights are included.

Start with **Qwen3.5-2B → CPU → Download → Load → ENGLISH GROUNDED → HELLO/1234 control → VISION + OCR → Run OCR → Send**. Repeat in VISION ONLY and OCR ONLY. Open the exact prepared image before judging visual correctness. Then use the red-square control, a real object and an equipment placard.

The code builds with real runtime/OCR dependencies. **No S25 launch or actual local-model response has been observed in this workspace.** The three routes are implemented from current upstream exports, not physically certified. CPU is the baseline; GPU remains experimental.

## Branches and checkpoints (requested items 1–4)

| Record | SHA |
|---|---|
| Starting Lab HEAD | `3f25ea228dfca24bc898a7c312015d2df0f3ab2d` |
| Built implementation HEAD | `34fcadf8e25387f4afb534811124fbd6fd456081` |
| Final Lab HEAD | The documentation commit containing this handoff; its full SHA is supplied in the final chat. No runtime source changed after the built implementation. |
| Refreshed live N-BIO HEAD | `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de` |
| Checkpoint 1: portable MNN + OCR | `bd1a6f8e235390a40a1937d9efc57791ef669ed9` |
| Checkpoint 2: lifecycle/download/CI/Termux hardening | `37025f4b96a1c23b1bf8f07779f99e73791833bc` |
| Checkpoint 3: dispose aborted image turns | `34fcadf8e25387f4afb534811124fbd6fd456081` |

Routine checkpoints continued automatically. GitHub's Git-data connection published identical local trees because command-line Git had no push credentials. The authoritative remote SHAs are listed above. N-BIO was refreshed, never synced into Lab or edited. Account interruption cleared the scratch checkout after implementation; the committed source and completed CI artifact were recovered from GitHub. A direct attachment could not be recovered into the resumed workspace because its download reference returned HTTP 403; the successful Actions artifact is the delivery route.

## Runtime decision (items 5–9)

| Candidate researched | Evidence and decision |
|---|---|
| LiteRT-LM | Official Kotlin API, CPU/GPU, separate multimodal backend and Gemma 4 E2B artefacts provide a credible Gemma route. Exact Android multimodal exports for both requested Qwen variants were not established in the short gate. Not selected as a single runtime; this is not a claim of permanent lack of support. |
| MNN 3.6.1 | Official Android CPU/OpenCL/Vulkan prebuilt with `libllm.so`, public Llm and vision APIs, and exact `taobao-mnn` exports for all three targets. Initial ARM64 release ELF inspection passed. Selected as one runtime with one small JNI bridge. |
| llama.cpp / Android prebuilt routes | Upstream multimodal support covers the families. Android JNI/Vulkan consumer assembly and third-party prebuilt provenance create more native integration burden than MNN's official release. Credible alternative, no implementation attempt needed while MNN passed. |

**Chosen runtime: MNN 3.6.1.** The engine is the official prebuilt; only a small public-API JNI bridge is compiled. No model conversion or runtime-engine source compilation is required. No hybrid adapter stack was needed. CPU is vendor-independent ARM64; GPU requires usable OpenCL drivers and is not promised on every device.

Runtime archive: `mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan.zip`, 6,197,903 bytes, SHA-256 `46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71`. Matching source-header archive SHA-256: `4b6065c4e2674318f5bf1dc75836ce4d30c17bfe598c4a1b11b7d0b2092b06e6`.

Primary sources: [MNN release](https://github.com/alibaba/MNN/releases/tag/3.6.1), [public Llm/vision source](https://github.com/alibaba/MNN/tree/3.6.1/transformers/llm/engine), [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), [llama.cpp multimodal](https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md), and the pinned model repositories below. Full reasoning/source links are in `LAB_2B_RUNTIME_RESELECTION.md`.

GenieX/NPU remains rejected because the standard AAR packages Qualcomm Hexagon objects with insufficient LOAD alignment for the mandatory 16 KB gate. No further Qualcomm version cycling or HTP debugging occurred. **GenieX and Qualcomm accelerator binaries are absent from the APK.** Historical Qwen3.5 HELLO/1234 findings remain documented: selected NPU grossly misinterpreted blocks/logo; GPU described blank; CPU recognized HELLO and approximately 1924. None of those conclusions is transferred to MNN.

## Exact models and backends (items 10–16 and 20–23)

Every entry uses the MNN 3.6.1 adapter, supports image + text, defaults to CPU, has an 8192-token harness budget and maximum 512 output tokens, and uses greedy decoding. All three pinned source cards declare Apache-2.0; [Google also identifies Gemma 4 as Apache-2.0](https://ai.google.dev/gemma/docs/gemma_4_license). No credentials are used. Anonymous HTTP range probes of each pinned main weight succeeded with matching total lengths; full app downloads still hash every byte before activation.

| Display name / stable ID | Source at exact revision | Selected asset bytes | CPU | GPU | Status |
|---|---|---:|---|---|---|
| Gemma 4 E2B IT / `gemma4-e2b` | [taobao-mnn/gemma-4-E2B-it-MNN](https://huggingface.co/taobao-mnn/gemma-4-E2B-it-MNN/tree/ce18884f154ce405545f1acda5c5c8fdd9c1280c) @ `ce18884f154ce405545f1acda5c5c8fdd9c1280c` | 3,143,793,636 | Text + vision | OpenCL text, CPU vision; experimental | Implemented; physical inference pending |
| Qwen3.5-2B / `qwen35-2b` | [taobao-mnn/Qwen3.5-2B-MNN](https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN/tree/35781816d7b6a9dcb273a6765ac9563401951c3c) @ `35781816d7b6a9dcb273a6765ac9563401951c3c` | 1,381,346,269 | Text + vision | OpenCL text, CPU vision; experimental | Implemented; physical inference pending |
| Qwen3-VL-2B-Instruct / `qwen3vl-2b` | [taobao-mnn/Qwen3-VL-2B-Instruct-MNN](https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-MNN/tree/9e49ec71ded22500a997ed0f9961e1e92b85bbc9) @ `9e49ec71ded22500a997ed0f9961e1e92b85bbc9` | 1,474,252,555 | Text + vision | OpenCL text, CPU vision; experimental | Implemented; physical inference pending |

No entry is artificially disabled as ROUTE_UNAVAILABLE: exact upstream source and downloadable vision exports support all three. Equally, none is claimed physically working merely because a button is enabled. The registry can record an unavailable route with its diagnostic reason if subsequent evidence rejects it.

**Format:** MNN graph + external weights + tokenizer + separate vision graph/weights. Gemma uses `tokenizer.mtok` and additionally `ple_embeddings_int4.bin`; Qwen uses `tokenizer.txt`. Gemma audio is disabled before load and its unused audio model files are not downloaded. Tied text embeddings are in the main weights. The external filenames are runtime-specific, not forced into GGUF or raw TFLite.

| Model ID | Exact filename | Bytes | SHA-256 |
|---|---|---:|---|
| `gemma4-e2b` | `config.json` | 678 | `3b1c8caafa2792a64b81d2ef47d3e6afc1c250b280389e77d0d25628108c87a7` |
| `gemma4-e2b` | `llm.mnn` | 2,276,992 | `7115ecd7a66332d8a14c9d6467d560baec33c9650174cbb2f0e7641a69999216` |
| `gemma4-e2b` | `llm.mnn.weight` | 1,436,474,178 | `8d4b0fabb015da09a820fab22714f392b9e73f8f2fc7175dea7ef4f581d03881` |
| `gemma4-e2b` | `llm_config.json` | 1,415 | `7096f286d274bee7f374b7d06533d5a611f6d678b119fa9542e74e65fd8a5379` |
| `gemma4-e2b` | `ple_embeddings_int4.bin` | 1,468,006,400 | `c76e660ca418790bde8757099af0144488ece631dcd612245f1e1bf801f9e1e3` |
| `gemma4-e2b` | `tokenizer.mtok` | 10,068,633 | `e08a1293e250750949bb1f543edd626cc6cf9f039a2e461958d20f33407d26b9` |
| `gemma4-e2b` | `visual.mnn` | 1,060,528 | `759a3fa521cbb9e4bcf877769524faa41f0e1288a61d664cb9656f3e70f61fb0` |
| `gemma4-e2b` | `visual.mnn.weight` | 225,904,812 | `308e356f5a8527c28c1caba233b8d3521d4ba558b56cbcb8a53ed103d73ae1af` |
| `qwen35-2b` | `config.json` | 652 | `92853033efe602f95efca3e1c05cd8b108f973c8beed417843a9671f8147ed8d` |
| `qwen35-2b` | `llm.mnn` | 2,148,136 | `23df98f8b341b277365e0bbca025c1d192939e3d32d7f79776352c6f32e77960` |
| `qwen35-2b` | `llm.mnn.weight` | 1,176,647,702 | `c93f71a2dbecf9328782bd38861656d8faa82e95e7f99607350074768a482054` |
| `qwen35-2b` | `llm_config.json` | 8,692 | `a88234b36c2af0eff8e5c89667011badf71c15e30459eb0e21030a8f3f9ed240` |
| `qwen35-2b` | `tokenizer.txt` | 6,465,727 | `7e75de1f279a10b65bd9dc1a5207205cb8993823861c4c42bbbd74e48e1c23a4` |
| `qwen35-2b` | `visual.mnn` | 488,096 | `88fc40a7b676e90eb2cb86d854db15cb90b9eb1f34087ab0f48c5e43572c8dac` |
| `qwen35-2b` | `visual.mnn.weight` | 195,587,264 | `8f90e106f5b9ae9a939faed240305cfdd5c6740ae91d3fc418a990bee0cce36b` |
| `qwen3vl-2b` | `config.json` | 605 | `1ed5c6e65459fdc4b0c33319715b763005013ba8580dd3c687bd2651546ca2a4` |
| `qwen3vl-2b` | `llm.mnn` | 462,464 | `c2286f60cbd56a82f26bfeac92f6a96e9690889b1939346abfe9e1fae996a8f3` |
| `qwen3vl-2b` | `llm.mnn.weight` | 1,231,860,194 | `1554f9ce71743b56c2d7fba4cb0c2a31c7cddf4f21e1a2ff5a2e85b9a316a29f` |
| `qwen3vl-2b` | `llm_config.json` | 6,445 | `5408721c81cc9a7ea8aa485a0652e5e1a47dd5ea5bbd5af2e1f16bc4f6358699` |
| `qwen3vl-2b` | `tokenizer.txt` | 3,193,555 | `7119de4966cc6a8ae87d7f083e65b315282d06c3122fdd41ce783fdd2d3c1ca2` |
| `qwen3vl-2b` | `visual.mnn` | 502,512 | `c489c1f65dc6aa5bcee42b3e291f7987df1111423c1fe570d0f3394e1207d2bb` |
| `qwen3vl-2b` | `visual.mnn.weight` | 238,226,780 | `9feb04848cafad1117a510b43d6c2b58d6c31bef1040598156d266f9b42f581f` |

For every row the URL is `https://huggingface.co/<source>/resolve/<revision>/<filename>`. Exact machine-readable pins are also in `experiments/lab2b-vlm-harness/model-registry.json`; `ModelRegistry.kt` is the typed application registry.

Requested backend is separate from effective text and vision. CPU explicitly selects CPU for both. GPU configures OpenCL for text while preserving each export's separate CPU vision backend. MNN's public config does not attest per-operation GPU execution or internal CPU fallback, so GPU effective text reads **UNVERIFIED: OpenCL configured; per-op placement/fallback not reported**. GPU initialization failures offer explicit CPU selection, not a silently relabelled run. The per-model manual FAILED CORRECTNESS annotation persists; every GPU path remains experimental and off by default. GPU vision is not integrated in this pin.

## Downloads and storage (items 17–19 and 43)

Android DownloadManager owns persistent transfers and progress, system pause/failure reasons and retries. Queries use this app's private download destinations; recovery does not depend on in-memory IDs. Partial files live at `getExternalFilesDir(null)/lab2b/models/<id>.partial`, normally `/storage/emulated/0/Android/data/dev.kian.lab2b.vlm/files/lab2b/models/`.

All required transfers must complete, then each file must match its pinned byte size and SHA-256. A VERIFIED registry-fingerprint marker and same-filesystem directory rename activate `<id>`. Partial download completion alone never means INSTALLED. Restart discovery checks marker and lengths; Load rehashes every installed asset. Switching retains downloads. Remove cleans only the selected model's DownloadManager rows, installed/staging directory and runtime cache.

Space checking requires remaining download bytes plus 256 MiB reserve. Activation renames weights rather than duplicating them. Staging overhead is the unfinished selected asset set; MNN runtime mmap/rearrangement caches require additional variable space. The UI measures installed files and staging/runtime-cache bytes. All three selected sets total **5,999,392,460 bytes**, plus small markers/filesystem allocation and runtime caches. Actual phone storage peaks are pending. No multi-GB model weights were downloaded in the build workspace or bundled in CI/APK.

## System prompts, context and prepared images (items 24–30)

Presets: NONE, ENGLISH GROUNDED, JSON TEST. JSON TEST is instruction following, not schema-constrained generation. Custom `.txt`/`.md` uses the Android document picker, bounded <=64 KiB reads, strict UTF-8 validation, binary-NUL rejection, safe private copying and hash verification. Name, size, SHA and active/inactive status are visible. Clear selects NONE. Custom bytes persist, while the selected preset defaults to grounded after process restart.

All three exact installed templates accept a true system message, kept separate from user content. The typed USER_PREFACE_FALLBACK path uses clear delimiters and visible diagnostics; it is tested but not selected for this registry. Blank text is NONE. The final template is tokenized once including the current image; input plus 512 output tokens must fit 8192 before decoder prefill.

The scrollable developer UI has an instruction field, image attachment, Send, Stop, Clear and streamed output. The transcript contains at most 20 turns for human viewing. **Every model turn is stateless:** current system + current user instruction + optional current image + current OCR only; no transcript or historical image is replayed.

One process singleton owns one heavyweight engine. Blocking work runs off-main-thread; phase guards prevent concurrent inference/switch/unload. Activity recreation reattaches to snapshots. Model/backend switches dispose first, preserve files and require Load. Successful turns reuse the model with native context reset. **Stopped or failed turns unload the model** after the current native operation returns, then ask for Load again: MNN clears pending image embeddings during prefill, while reset alone cannot clear an image from a prefill-aborted turn. This avoids stale-image reuse. Stop is cooperative, not immediate interruption of vision/prefill kernels.

Images are privately copied (64 MiB source cap), EXIF-normalized, white-composited and kept full-frame. The OCR PNG is bounded at 16 MP / 4096 edge for memory; the separate model PNG is maximum edge 1600, without square cropping. Original name, dimensions, bytes, SHA, orientation and normalization are shown alongside both derived dimensions, hashes and paths. **Open prepared image** exposes the exact supplied PNG through a viewer/in-app fallback. MNN's internal model-specific resampling/patching is explicitly distinguished from that file. Session images/transcript are not restored; orphan image directories are reclaimed after process death.

## Bundled OCR and pipeline modes (items 31–39)

Dependency: **`com.google.mlkit:text-recognition:16.0.1`**, bundled Latin Text Recognition v2, per [Google's Android documentation](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). OCR is available without a separate Play Services recognizer download. It reads `files/lab2b/images/<uuid>/normalised-ocr.png`, not the smaller model input.

Typed OCR preserves full text, blocks, lines, bounding rectangles, corner points, exposed language metadata, processing milliseconds, normalized-source SHA and dimensions. No confidence is invented. UI shows status, raw text, counts, timing, dimensions/hash, and Run/Copy/Clear. A four-entry memory cache uses normalized-image SHA; stale evidence cannot be sent for another image.

| Mode | Image to final model | OCR to final model |
|---|---|---|
| VISION ONLY | Current prepared image | None |
| VISION + OCR, default | Current prepared image | Current evidence; OCR runs automatically if missing/stale |
| OCR ONLY | None | Current evidence; OCR runs automatically if missing/stale |

Deterministic Kotlin assembles USER INSTRUCTION, then `[OCR_CANDIDATE_EVIDENCE]` with explicit error/authority warnings, quoted full text, line/block data, pixel boxes and language, then a closing delimiter. Coordinates refer to normalized source dimensions. OCR is supplementary evidence, never instructions; the image remains authoritative when present. OCR ONLY cannot independently verify visual details. JSON-style escaping prevents OCR from creating media tags/layout fields. No other LLM processes OCR.

Built-in clean red-square and HELLO/1234 images, document/gallery selection, all three modes and exact prepared-image inspection support the requested controls. Ordinary objects/placards remain manual inputs. No automated benchmark matrix or mocked inference output is presented as model validation.

## Diagnostics, build, APK and Termux (items 40–51)

Cold load measures native construction after integrity checks; LOADING includes rehashing. TTFT starts after OCR/prompt assembly and includes native vision/prefill until first nonempty output. Generation timing spans native inference. Native metrics include prompt/generated token counts and vision/prefill/decode microseconds. PSS is recorded before load, after load and after unload. Actual timings/memory require device execution; none are fabricated.

**Final dedicated CI run 33942676654 passed** `testDebugUnitTest`, `assembleDebug`, `lintDebug`, source isolation, binary/model safety, ELF and APK alignment. All **19 declared unit tests** were included in the successful test task. This is not a zero-warning lint build: the local offline audit included developer-text localisation, Kotlin idiom and application-context singleton warnings; no lint errors blocked CI. Toolchain: Java 17, Gradle 9.1.0, AGP 8.13.0, Kotlin 2.2.0, SDK/target 35, minimum 31, NDK 27.3.13750724, CMake 3.22.1, ARM64 only.

**Packaged native size:** 20,327,800 bytes. ML Kit contributes 11,064,544 bytes; MNN/STL/JNI together contribute 9,263,256 bytes. Libraries:

- `libMNN.so`, `libMNN_Express.so`, `libMNN_CL.so`, `libMNN_Vulkan.so`;
- `libMNNOpenCV.so`, `libMNNAudio.so`, `libllm.so`, publisher `libc++_shared.so`;
- small `liblab2b_mnn.so` and bundled `libmlkit_google_ocr_pipeline.so`.

Vulkan/audio libraries remain in libllm's native dependency closure; no audio or Vulkan-specific option is exposed. The unused general upstream `libmnncore.so` is omitted. No GenieX/HTP/QNN/Hexagon binary is packaged.

Every packaged ELF is ARM64 with all LOAD alignments >=16384 and matching offset/address congruence. Android `zipalign -c -P 16 4` passed on the final APK. The recorded native audit includes each size and SHA. This is **static 16 KB compatibility**, not execution on a 16 KB device. The historical S25 runtime page size is 4096. A previous local APK also passed Android signature verification; the delivered CI artifact is the standard Gradle signed debug APK.

Tests cover registry/pins/backends, partial/verified/restarted/corrupt installations, hash verification and remove isolation; successful/paused/failed/missing download states; selected versus loaded models, busy-state exclusion, disposal and aborted-prefill policy; prompt file limits/UTF-8, system/preface semantics, OCR formatting, modes/cache/stale-image invalidation, and stateless prompt assembly. No test returns invented model text and calls that inference.

The isolated workflow downloads runtime libraries/headers only, never weights, uses no secrets, and uploads **lab2b-vlm-harness-debug**. The pre-existing repository-wide Android CI also triggered and succeeded; it was not modified. The first harness CI failure was missing ripgrep, fixed before subsequent successful runs. There was no repeated native-link/conversion rescue loop.

Exact Termux rebuild fallback, with the matching `lab2b-native.zip` and `lab2b-native.sha256` copied from the Actions artifact into the harness directory, and your existing Android SDK/Gradle 9.1.0 setup:

```bash
pkg install openjdk-17 aapt2 python
cd ~/My-Mettle
# Keep the working tree clean before switching branches.
git fetch origin
git switch agent/ui-ml-lab
git pull --ff-only
cd experiments/lab2b-vlm-harness
python tools/native_bundle.py import lab2b-native.zip --sha256 "$(awk '{print $1}' lab2b-native.sha256)"
./gradlew --no-daemon -Plab2bPrebuiltNative=true \
  -Pandroid.aapt2FromMavenOverride="$(command -v aapt2)" \
  testDebugUnitTest assembleDebug lintDebug
sha256sum app/build/outputs/apk/debug/app-debug.apk
cp app/build/outputs/apk/debug/app-debug.apk ~/storage/downloads/LAB-2B-VLM-Harness-debug.apk
```

Open the copied APK in Samsung My Files. If storage access was not granted, run `termux-setup-storage` first. The importer verifies the archive SHA, every library SHA and native/adapter source hashes; Gradle rechecks them. The bundle avoids executing desktop NDK tools on ARM64 Termux. Native edits require desktop/CI rebuilding. The host prebuilt path was verified with **all 55 tasks rerun successfully**, not just cached packaging. Physical Termux execution remains pending. Installing the CI APK needs no rebuild.

## Physical tests and limits (items 52–53)

Still pending on the S25: launch; DownloadManager interruption/recovery and full hash activation; force-close/relaunch INSTALLED state; CPU visual content for all three models; OCR on controls/placards; system prompt/file picker; all pipeline comparisons; GPU initialization/actual execution/visual parity; Stop and required reload; rotation/background/resume; memory reclamation; switching between installed models; and storage/cache peaks. Test separate 16 KB Android and other SoC devices before broader compatibility claims.

OpenCL driver support and operator fallback vary. GPU vision is not provided here. Latin OCR and bounded source resolution may require tighter photographs for tiny text. Stops wait for native kernels; failed/stopped turns require reload. There is no foreground-service guarantee that background inference survives process killing. Audio/video, multi-turn context, arbitrary model IDs, schema-constrained output and production integration remain outside LAB-2B. Exact model physical correctness is not established by this implementation.

## Changed files and isolation (items 54–59)

Changed paths, including historical source removals (A/M/D):

```text
A	.github/workflows/lab2b-harness.yml
M	docs/ui-ml-lab/PLAN.md
A	docs/ui-ml-lab/research/LAB_2B_CHECKPOINTS.md
M	docs/ui-ml-lab/research/LAB_2B_IMPLEMENTATION_NOTES.md
M	docs/ui-ml-lab/research/LAB_2B_PHYSICAL_ACCEPTANCE.md
A	docs/ui-ml-lab/research/LAB_2B_RUNTIME_RESELECTION.md
M	experiments/lab2b-vlm-harness/.gitignore
M	experiments/lab2b-vlm-harness/README.md
M	experiments/lab2b-vlm-harness/THIRD_PARTY_NOTICE.md
M	experiments/lab2b-vlm-harness/app/build.gradle
M	experiments/lab2b-vlm-harness/app/src/main/AndroidManifest.xml
A	experiments/lab2b-vlm-harness/app/src/main/cpp/CMakeLists.txt
A	experiments/lab2b-vlm-harness/app/src/main/cpp/mnn_bridge.cpp
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/BackendEvidence.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/DownloadState.kt
D	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/GgufVisionConfig.kt
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/HarnessRuntimeOwner.kt
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/HarnessState.kt
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/ImagePreprocessor.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/LocalInferenceEngine.kt
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/MainActivity.kt
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/ModelBundleInfo.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/ModelDownloads.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/ModelInstallation.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/ModelRegistry.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/OcrProcessor.kt
A	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/PromptPipeline.kt
M	experiments/lab2b-vlm-harness/app/src/main/java/dev/kian/lab2b/vlm/StorageIo.kt
D	experiments/lab2b-vlm-harness/app/src/main/res/layout/activity_main.xml
A	experiments/lab2b-vlm-harness/app/src/main/res/xml/image_paths.xml
M	experiments/lab2b-vlm-harness/app/src/test/java/dev/kian/lab2b/vlm/BackendEvidenceTest.kt
A	experiments/lab2b-vlm-harness/app/src/test/java/dev/kian/lab2b/vlm/DownloadStateTest.kt
M	experiments/lab2b-vlm-harness/app/src/test/java/dev/kian/lab2b/vlm/HarnessStateTest.kt
D	experiments/lab2b-vlm-harness/app/src/test/java/dev/kian/lab2b/vlm/ModelBundleInfoTest.kt
A	experiments/lab2b-vlm-harness/app/src/test/java/dev/kian/lab2b/vlm/ModelInstallationTest.kt
A	experiments/lab2b-vlm-harness/app/src/test/java/dev/kian/lab2b/vlm/PromptPipelineTest.kt
M	experiments/lab2b-vlm-harness/gradlew
A	experiments/lab2b-vlm-harness/model-registry.json
A	experiments/lab2b-vlm-harness/third_party/MNN_APACHE2.txt
A	experiments/lab2b-vlm-harness/tools/inspect_native_16k.py
M	experiments/lab2b-vlm-harness/tools/inspect_native_16k.sh
A	experiments/lab2b-vlm-harness/tools/native_bundle.py
A	experiments/lab2b-vlm-harness/tools/prepare_mnn.py
A	experiments/lab2b-vlm-harness/tools/verify_binary_safety.py
M	experiments/lab2b-vlm-harness/tools/verify_source_safety.sh
A	docs/ui-ml-lab/research/LAB_2B_MORNING_HANDOFF.md
A	docs/ui-ml-lab/research/LAB_2B_FINAL_NATIVE_AUDIT.txt
```

Final `git diff --check`, source-isolation and binary/model safety checks pass. **No model binaries, APKs, AARs or native libraries are committed.** Changes are limited to the standalone harness, its dedicated workflow and UI/ML documentation. My Mettle `app/`, product runtime/root build settings, N-BIO, Room, workout/equipment/database code and LAB-1 LocalPromptProvider are unchanged. The LAB-2C section/code was not advanced.

**LAB-2B IMPLEMENTATION READY / PHYSICAL ACCEPTANCE PENDING. LAB-2C NOT STARTED.**
