# LAB-2A failure archaeology

> **Status:** LAB-2A research deliverable. This file turns observed failures in Qualcomm/mobile native inference work into preventative rules for LAB-2B.
>
> Evidence IDs resolve through [`LAB_2A_SOURCE_LEDGER.md`](./LAB_2A_SOURCE_LEDGER.md).

## Reading rule

A failure from an older Nexa SDK, another model or another Snapdragon generation is **not** silently attributed to current GenieX. Historical failures are retained when they expose a recurring integration class: native-version mismatch, bad artefacts, accelerator-specific failure, lifecycle races, incorrect prompt/media handling, or Android packaging constraints.

## Failure table

| ID | Symptom | Runtime / model / device | Root cause status | Confirmed fix / strongest evidence | Source | Relevance to My Mettle | Preventative rule |
| --- | --- | --- | --- | --- | --- | --- | --- |
| FAIL-001 | Model creation returns opaque negative/large error code after files appear present | Historical Nexa Android / OmniNeural-4B; Snapdragon phones including S25-family discussion | Multiple causes were mixed: wrong main-file path, incomplete/wrong downloaded file, license-era SDK behaviour; one maintainer re-download worked while another user still failed | Maintainer corrected expected model anchor and later reported a fresh Hugging Face download working; issue remained device-sensitive | SRC-F01 | High as historical warning, low as current-runtime diagnosis | Never debug a generic create error by changing random SDK flags first. Record exact runtime version, model manifest/paths/hash and device before changing code. |
| FAIL-002 | Dynamic loader cannot resolve native symbol / app fails before inference | Historical Nexa Android native bundle | Native library set/version mismatch/regression | Issue demonstrates failure at `dlopen`/symbol resolution layer rather than model logic | SRC-F02 | High pattern | Treat the AAR/runtime/native library set as one versioned unit. Do not mix `.so` files from different releases or manually replace one backend library. |
| FAIL-003 | Play/Android warns native libraries do not support 16 KB pages | Historical Nexa native Android package | Prebuilt `.so` alignment incompatibility was reported | Android platform docs require 16 KB-compatible prebuilt native libraries; old issue was not a current GenieX confirmation | SRC-F03, SRC-B05 | High future shipping risk | Before LAB-2C/Play integration, inspect the **exact chosen AAR** with Android's 16 KB tooling. Do not infer current compliance from an obsolete package or from debug-device success. |
| FAIL-004 | Model download UI completes but NPU model later cannot load | GenieX/Nexa transition-era Android model manager | Download/URL/artefact mismatch; "downloaded" was not equivalent to verified runtime-ready | Model-manager issues and later design make completed-cache state/paths authoritative | SRC-F04, SRC-A04 | High | Installation state becomes `READY_VERIFIED` only after model-manager completion **and** integrity/load verification. Progress UI is not truth. |
| FAIL-005 | `q4_0` requested through Android/Python fails while CLI succeeds | GenieX model manager, GGUF | Case-sensitive quant key mismatch | Issue #1116 linked fix #1117; CLI uppercased while bindings passed raw case | SRC-A15 | High | Pin a fixed runtime or use canonical `Q4_0`. Never vary quant spelling during debugging and never assume all bindings normalise identically. |
| FAIL-006 | Android app cannot initialise QNN/HTP despite Snapdragon hardware | Old Nexa/QNN Android; issue #1067 logs include FastRPC/SELinux and QNN backend init error | Obsolete package path plus accelerator access/runtime mismatch; exact original device root cause not fully established | Maintainer later states old `NexaAI/*-NPU` + `com.nexa.demo` path is obsolete; current route is GenieX/AI Hub | SRC-A16 | Very high anti-pattern | Do not follow pre-GenieX Nexa Android tutorials. Start from current GenieX reference source and current bundle format. |
| FAIL-007 | NPU/QNN initialization fails and there is no transparent rescue | Qualcomm accelerator route, different device/model | Accelerator availability/runtime-specific failure | Issue evidence shows no safe assumption of automatic CPU fallback | SRC-F05 | High | Fallback belongs to the LAB-1 provider/runtime policy. LAB-2B must log actual backend failure and explicitly run a controlled GPU/CPU path; never label failed NPU as "local unavailable" without checking functional fallback. |
| FAIL-008 | Model works on CPU but accelerated NPU path crashes | GenieX GGUF, different Qwen/model/device | Backend-specific runtime defect, later fixed | Issue #1178 demonstrates same model/runtime family can diverge by compute unit | SRC-F06 | High | Prove model functionality and compute-unit functionality separately. One successful CPU answer does not prove NPU; one NPU crash does not prove the model artefact is corrupt. |
| FAIL-009 | OpenCL/GPU backend crashes while NPU/CPU survive | GenieX/Qualcomm device, different chipset | GPU backend defect; fixed independently | Issue #1250 | Medium-high | Treat NPU, GPU and CPU as independently smoke-tested backends. Do not change model files just because one accelerator fails. |
| FAIL-010 | Stateful accelerated generation/server path crashes after additional prompt/session state | GenieX accelerated server path | State/lifecycle/backend defect; exact relevance to Android direct wrapper limited | Issue #1186 | Medium | LAB-2B must use direct `VlmWrapper`, not add a local HTTP server. Keep the first proof one session, one turn, then repeat under controlled state. |
| FAIL-011 | NPU inference is functional but clock/power behaviour is unexpectedly poor | QAIRT Qwen3-VL-4B, non-Android evidence | Accelerator clock/runtime policy, not correctness | Issue #1330 | Medium | Profile power/thermal **after** correctness/restart/repeatability. "NPU" is not itself a power guarantee. |
| FAIL-012 | Documentation/user expects Qwen3-VL QAIRT Android support but exact model path is unclear | GenieX Qwen3-VL support evolution | Documentation/runtime registry evolves faster than assumptions | Current Android docs show Qwen3-VL-2B GGUF but only Qwen2.5-VL-7B as QAIRT VLM; older issue captured the gap | SRC-F10, SRC-A04, SRC-A17 | Very high | Do not infer model support from family names. Record exact repo ID + runtime_id + supported registry entry. Qwen3-VL-2B QAIRT remains NOT YET ESTABLISHED. |
| FAIL-013 | Raw prompt yields broken/degenerate QAIRT VLM output | GenieX Android QAIRT | Application passed raw user text instead of model chat-template output | Official troubleshooting says use `applyChatTemplate().formattedText` | SRC-A07 | High, transferable prompt-shape rule | Always apply the runtime/model chat template. Generation input is formattedText, not the user's raw string. |
| FAIL-014 | Second image turn dies in `mtmd_tokenize` / failed to initialise batch | Official GenieX Android reference app, GGUF VLM | Too-small context and/or replaying historical media markers/bitmaps | Reference source increased context headroom and injects **current-turn media only** | SRC-A11, SRC-A12 | Very high | LAB-2B starts with **one image**. Use current-turn media only, nCtx 4096 baseline, and size preprocessing/context from mmproj metadata. Multi-image/multi-turn comes later. |
| FAIL-015 | Second Generate click can race native handle and crash app | Official GenieX Android reference app | Concurrent generation on same native wrapper | Current source explicitly guards `isGenerating` with a crash warning | SRC-A11 | Critical | One wrapper = one serial inference lane. Disable/reject concurrent generate; do not launch two coroutines against one handle. |
| FAIL-016 | Wrong image resolution silently throws away detail or causes context assumptions to be wrong | Official GenieX Android VLM reference | Hard-coded vision size does not match model's mmproj geometry | Current reference app reads GGUF `clip.vision.image_size`, patch and spatial merge directly | SRC-A12 | Critical for vision | Read/use the selected projector's declared geometry; never reuse 448/560/768 just because a different VLM used it. |
| FAIL-017 | QAIRT Android pull fails/loads wrong target because chipset is omitted | GenieX Android QAIRT | Android cannot auto-detect AI Hub compilation target in this path | Official API/troubleshooting requires `chipset="SM8750"` for Snapdragon 8 Elite | SRC-A04, SRC-A07 | High if QAIRT fallback is ever tried | Every AI Hub precompiled Android pull records explicit chipset. Do not rely on Windows auto-detection semantics. |
| FAIL-018 | QAIRT model load/generation breaks after app tunes context/GPU-layer knobs | GenieX QAIRT | AI Hub bundle has fixed KV/context; llama.cpp tuning fields are invalid | Official reference source and troubleshooting zero/default `nCtx`/`nGpuLayers` for QAIRT | SRC-A07, SRC-A11 | Medium for primary, high if QAIRT later | Keep runtime-specific config below provider boundary. Never reuse llama.cpp `ModelConfig` tuning on QAIRT. |
| FAIL-019 | Native runtime crashes on repeat conversation/process ownership | LiteRT-LM Android, other devices/models | Native lifecycle/process-state bug | LiteRT-LM issue #2028 supplies a repeat-create SIGSEGV case | SRC-F11 | Transferable lifecycle warning | LAB-2B must force-close/relaunch and explicitly destroy/recreate. A one-shot demo is insufficient evidence of a production-safe native runtime. |
| FAIL-020 | Qwen-family runtime version "supports Qwen" but a newer Qwen model fails engine creation | LiteRT-LM Qwen3.5 | Model/version incompatibility | LiteRT-LM issue #1658 | Transferable versioning warning | Pin **exact model family + export + runtime version**, not just "Qwen supported". |
| FAIL-021 | Qualcomm NPU setup fails across several QAIRT versions because runtime/model/manager versions disagree | LiteRT-LM on SM8750 | QNN/QAIRT version matrix mismatch / manager setup failure | Detailed issue tried multiple QAIRT versions and requested a known-good matrix | SRC-C04 | High transferability | Do not solve NPU failures by cycling SDK versions. Begin from a source-proven matrix and change one component at a time. |
| FAIL-022 | Multimodal CPU path SIGSEGV on second image turn; GPU path has additional native-library prerequisites | LiteRT-LM / Gemma 4 | Vision state/lifetime plus vendor driver loading requirements | Detailed issue documents second-turn crash and GPU workaround prerequisites | SRC-C05 | Medium transferability | One-image success precedes multi-turn. Vendor driver visibility/native packaging is a separate test axis from model correctness. |
| FAIL-023 | Runtime upgrade causes ~3× GPU decode regression in Android/Kotlin | LiteRT-LM/Qwen on Samsung S26 Ultra | Version regression | Issue #2472 compares concrete versions | SRC-C06 | High versioning lesson | Do not upgrade model runtime inside a feature patch. Every runtime bump repeats the minimal acceptance matrix before integration. |
| FAIL-024 | Product deploys Hexagon NPU then disables it due cross-device compatibility/performance | Community open-source app report | Reported device compatibility, speed inversions and HTP/GPU conflicts | Author reports feature was rolled back behind flag; external code not independently audited here | SRC-D03 | Medium, Tier D | Keep local backend replaceable and feature-gated. LAB-2C must retain explicit functional fallback and retirement; never make NPU a user-data invariant. |

## Highest-risk traps for LAB-2B

### 1. Starting from the old Nexa/Genie/QNN tutorials

This is the clearest avoidable error. Qualcomm maintainers explicitly say the old `NexaAI/*-NPU` package format and `com.nexa.demo` route predate current GenieX. Following those threads recreates licence-path, manually-pushed-file and QNN-loader problems that the current AAR/model manager is designed to remove. **Use current GenieX + current reference app.** [SRC-A16]

### 2. Treating "model downloaded" as "model ready"

A model lifecycle needs at least three independent facts: the expected artefacts are complete, their identity/integrity is known, and the selected runtime can load them. LAB-1 already has `READY_VERIFIED`; LAB-2B should exercise that idea rather than collapse it to a progress callback. [SRC-F01, SRC-F04, SRC-A06]

### 3. Racing the native wrapper

The strongest Kotlin app in the evidence base says a second generate against the same native handle can crash. This is not a theoretical concurrency preference. Serial inference is the baseline contract. [SRC-A11]

### 4. Mixing runtime configuration

GenieX deliberately supports GGUF/llama.cpp and QAIRT bundles, but their configuration semantics differ. QAIRT has fixed compile-time context/KV characteristics; GGUF exposes context/GPU-layer controls. A provider abstraction does not mean the adapter can share one native config object. [SRC-A04, SRC-A07]

### 5. Assuming NPU because it was requested

LAB-2B has to establish actual `runtime_id`, resolved compute unit and accelerator logs. Throughput or low heat alone is corroboration, not backend identity. [SRC-A04, SRC-A08, SRC-D01]

### 6. Hard-coding VLM image geometry

The current Qualcomm sample had to add projector metadata parsing precisely because different VLMs use different image/patch/merge geometry. Hard-coded image dimensions can silently degrade input before inference even appears broken. [SRC-A12]

### 7. Multi-image/multi-turn before one-image reliability

The reference app itself documents `mtmd_tokenize` failures from context/media-history mistakes. LAB-2B must prove one image + one prompt + one response + restart before adding history. [SRC-A11]

### 8. Using "latest everything"

The official docs, reference app and releases currently expose different Android AAR versions. Other mobile runtimes show concrete version regressions and QNN matrix failures. The correct pin is the matrix we reproduce, not the newest number in isolation. [SRC-A03, SRC-A10, SRC-A14, SRC-C04, SRC-C06]

## Confirmed fixes and shortcuts worth carrying forward

| Rule | Evidence | LAB-2B action |
| --- | --- | --- |
| Start with Qualcomm's working reference app before writing the harness | SRC-A03, SRC-A09 | Step 0 is reference-app reproduction on the S25 Ultra |
| Consume Maven AAR; do not author JNI/CMake for the proof | SRC-A03, SRC-A13 | `implementation("com.qualcomm.qti:geniex-android:<pin>")`; no CMake project |
| Use exact Qwen3-VL-2B GGUF via `llama_cpp` | SRC-A01, SRC-A04, SRC-A05 | Model `unsloth/Qwen3-VL-2B-Instruct-GGUF`, precision `Q4_0` |
| Import local bundle through model manager instead of manually copying into its cache | SRC-A06 | `HubSource.LOCALFS` + `local_path`; then `getPaths()` |
| Treat returned `ModelPaths.runtime_id` as authoritative | SRC-A04, SRC-A11 | Log and assert it is `llama_cpp` for the primary path |
| Serialise inference and stop/destroy explicitly | SRC-A11 | One owner, one active request, `stopStream()` → `destroy()` |
| Apply chat template, inject current-turn media, then stream | SRC-A04, SRC-A11, SRC-A07 | Mirror the exact call order |
| Size image from mmproj metadata | SRC-A12 | Reuse/reference the parsing rule; do not pick a magic dimension |
| Context budget must include image tokens | SRC-A11, SRC-A12 | Begin at nCtx 4096; one image only |
| Canonical quant spelling matters | SRC-A15 | Pin `Q4_0` exactly |
| For QAIRT Android, explicit chipset is mandatory | SRC-A04, SRC-A07 | If QAIRT is later tested, use `SM8750` |
| Verify prebuilt `.so` page-size compatibility | SRC-B05, SRC-F03 | Add a 16 KB alignment check before LAB-2C, and preferably during LAB-2B packaging audit |

## DO NOT DO THIS

- **Do not** use the obsolete `NexaAI/*-NPU` / old `com.nexa.demo` path. [SRC-A16]
- **Do not** manually mix QNN/GenieX/native `.so` files across versions. [SRC-F02]
- **Do not** call a model `READY_VERIFIED` because a progress bar reached 100%. [SRC-F01, SRC-F04]
- **Do not** run two generations concurrently on one `VlmWrapper`. [SRC-A11]
- **Do not** create/destroy the runtime for every token or every prompt. The reference implementation reuses one loaded wrapper and destroys explicitly. [SRC-A11]
- **Do not** run model load/generation on the UI thread. [SRC-A11]
- **Do not** feed raw user text directly into generation when the runtime expects the chat template. [SRC-A07]
- **Do not** replay all historical image paths on each turn. [SRC-A11]
- **Do not** hard-code a VLM image dimension copied from another model. [SRC-A12]
- **Do not** start LAB-2B with multi-image, conversation history, CameraX, OCR, Room or equipment interpretation. [SRC-A11, LAB programme gate]
- **Do not** infer NPU execution from performance or from requesting `npu`; prove the resolved runtime/backend. [SRC-A04, SRC-A08]
- **Do not** assume a QAIRT Qwen3-VL-4B artefact proves Qwen3-VL-2B QAIRT support. [SRC-A04, SRC-F10]
- **Do not** tune `nCtx`/`nGpuLayers` on QAIRT as though it were llama.cpp. [SRC-A07]
- **Do not** use an unverified giant APK model asset. Current reference architecture keeps weights separate. [SRC-A09]
- **Do not** start with a broad benchmark matrix. One load → one image → one prompt → one response → restart → repeat comes first.
- **Do not** upgrade the runtime because a newer version exists. First reproduce the pinned matrix, then make one controlled version change if evidence requires it. [SRC-A10, SRC-C06]

## Failure-archeology conclusion

The recurring failure pattern is not "Qualcomm NPU is unreliable". The recurring pattern is **uncontrolled integration state**: obsolete package formats, mixed native versions, unverified artefacts, accelerator assumptions, concurrent native-handle use, incorrect model-specific preprocessing, and broad version churn.

The current GenieX AAR + ModelManager + Kotlin VLM API removes a large amount of the manual surface that caused earlier pain, but it does not eliminate the need to pin a matrix, verify artefact identity, serialize native access and prove the actual backend on the target device.
