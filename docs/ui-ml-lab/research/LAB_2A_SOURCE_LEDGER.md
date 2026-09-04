# LAB-2A source ledger

> **Status:** research evidence ledger for UI/ML LAB-2A.
>
> **Access date for every source:** 2026-09-04.
>
> Evidence tiers follow the LAB-2A mission: **A** = working/reproducible implementation or confirmed maintainer fix; **B** = authoritative runtime/model documentation; **C** = detailed issue/forum evidence with credible technical detail; **D** = anecdotal community experience useful mainly for discovering traps.

## How to read this ledger

A source can prove only what is stated in **What it proves**. A different model/device/runtime remains a transferability limit, even when the engineering lesson is useful. Moving branch URLs are avoided where a practical commit permalink is available.

## Qualcomm / GenieX primary evidence

| ID | Source type | Title / author | URL | Date | Model/runtime relevance | Android/Kotlin / hardware relevance | What it proves | What it does **not** prove | Tier | Confidence / notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SRC-A01 | Official model page | Qwen3-VL-2B-Instruct — Qualcomm AI Hub | https://aihub.qualcomm.com/models/qwen3_vl_2b_instruct?runtime=geniex_qairt%2Cgeniex_llamacpp | Current at access | Qwen3-VL-2B-Instruct; GenieX llama.cpp; Q4_0 | Snapdragon-class profiles; page exposes Android integration entry points | Qualcomm currently publishes Qwen3-VL-2B-Instruct as a GenieX/llama.cpp GGUF path and recommends Q4_0; quick start names `unsloth/Qwen3-VL-2B-Instruct-GGUF` | The selected UI profile at access was not sufficient evidence that the exact phone/S25 artefact has already been physically proven | B | High for artefact/runtime family; device proof still belongs to LAB-2B |
| SRC-A02 | Official repository | GenieX — Qualcomm | https://github.com/qualcomm/GenieX/tree/d38369a19af007bc164986b4415d9f8952a97301 | Commit 2026-09-03 | GenieX core; `llama_cpp`; `qairt` | Android Kotlin/Java documented; Snapdragon NPU/GPU/CPU | GenieX intentionally exposes two model/runtime families: GGUF through llama.cpp and AI Hub precompiled bundles through QAIRT | It does not mean every model exists in both runtime families | A/B | High; repository revision frozen for this report |
| SRC-A03 | Official Android docs | Android install — GenieX | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/docs/en/run/android/install.mdx | Commit 2026-09-03 | GenieX Android SDK | Explicit SM8750/SM8850; Kotlin; Maven AAR | Consumer app can use `com.qualcomm.qti:geniex-android`; AAR carries arm64 native libraries; consumer does not need to author NDK/CMake; Qualcomm says try reference app first | The page currently shows AAR `0.3.1`, which conflicts with the reference app pin and therefore is not by itself the chosen version pin | B | High; version drift explicitly tracked |
| SRC-A04 | Official Android API | GenieX Android API reference | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/docs/en/run/android/api-reference.mdx | Commit 2026-09-03 | `VlmWrapper`; ModelManager; GGUF; QAIRT | Kotlin API; SM8750; NPU/GPU/CPU | Exact Android Qwen3-VL-2B GGUF shape: `getPaths`, `VlmCreateInput`, image path + text `VlmContent`, chat template, media injection, streaming; `llama_cpp` supports CPU/GPU/NPU; `qairt` is NPU-only | Current supported QAIRT VLM table lists Qwen2.5-VL-7B, not Qwen3-VL-2B | A/B | Very high; primary implementation contract |
| SRC-A05 | Official quickstart | GenieX CLI quickstart | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/docs/en/run/cli/quickstart.mdx | Commit 2026-09-03 | Qwen3-VL-2B GGUF | Transferable to Android same GenieX runtime/model manager | Explicit multimodal example `Qwen/Qwen3-VL-2B-Instruct-GGUF`; select VLM and Q4_0; Q4_0 is recommended for Hexagon | CLI success alone is not Android app success | B | High for model/runtime/quantisation |
| SRC-A06 | Official model docs | GenieX supported models | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/docs/en/models/supported.mdx | Commit 2026-09-03 | GGUF / QAIRT / localfs | Android Kotlin local import examples | Local GGUF directories can be imported with `HubSource.LOCALFS`; VLM projector belongs beside weights; manager copies/imports to SDK cache | Does not establish My Mettle's future network/download UX | B | High; key to LAB-2B no-network first proof |
| SRC-A07 | Official troubleshooting | GenieX troubleshooting | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/docs/en/resources/troubleshooting.mdx | Commit 2026-09-03 | GGUF + QAIRT | Android; SM8750/SM8850 | Android QAIRT pulls need explicit chipset; generation must receive chat-templated text; QAIRT model registry and fixed KV/context constraints are real failure points | These QAIRT constraints do not automatically apply to llama.cpp GGUF | B | High; confirmed documented traps |
| SRC-A08 | Official design/run notes | GenieX runtime notes | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/notes/run.md | Commit 2026-09-03 | `llama_cpp` vs `qairt` | Qualcomm NPU/GPU/CPU | GGUF/llama.cpp and QAIRT bundles are separate stacks and formats; runtime is chosen per model | Does not provide Kotlin lifecycle guidance | B | High |
| SRC-A09 | Official reference app | GenieX Chat Android README — Qualcomm AI Hub Apps | https://github.com/qualcomm/ai-hub-apps/blob/db3f9772d4e423dee2df517335009c703845dba8/geniex_chat_android/README.md | Release commit 2026-09-02 | GenieX LLM/VLM | Kotlin/Java; Snapdragon 8 Elite / 8 Elite Gen 5; ARM64 | Real Android app exists, supports VLMs and NPU/GPU/CPU, and keeps model weights out of APK | README alone does not prove exact Qwen3-VL-2B on Kian's S25 | A | Very high; strongest real Android implementation |
| SRC-A10 | Official reference source | GenieX Chat Android Gradle | https://github.com/qualcomm/ai-hub-apps/blob/db3f9772d4e423dee2df517335009c703845dba8/geniex_chat_android/build.gradle | Release commit 2026-09-02 | GenieX AAR 0.3.5 | AGP 8.13.0; Kotlin 2.2.0; NDK 27.3.13750724; Java 17; minSdk31; target34 | A concrete build matrix that Qualcomm's current reference app actually pins; AAR `0.3.5`; legacy JNI packaging enabled | It does not prove these are universal minimums; docs separately say consumers need no NDK/CMake | A | Very high for reproduction baseline |
| SRC-A11 | Official reference source | GenieX Chat Android `MainActivity.kt` | https://github.com/qualcomm/ai-hub-apps/blob/db3f9772d4e423dee2df517335009c703845dba8/geniex_chat_android/src/main/java/com/geniex/demo/MainActivity.kt | Release commit 2026-09-02 | `VlmWrapper`, ModelManager | Kotlin; actual Android lifecycle/UI | Load/generate/destroy run off main thread; one model at a time; concurrent generation on one native handle is explicitly guarded because it can crash; unload uses `stopStream()` then `destroy()`; current-turn media only; resumable model-manager download; runtime_id from paths is authoritative | Activity ownership in a demo is not proof that Activity is the best production lifetime owner | A | Very high; strongest Kotlin-specific source |
| SRC-A12 | Official reference source | `GgufVisionConfig.kt` | https://github.com/qualcomm/ai-hub-apps/blob/db3f9772d4e423dee2df517335009c703845dba8/geniex_chat_android/src/main/java/com/geniex/demo/utils/GgufVisionConfig.kt | Release commit 2026-09-02 | GGUF VLM projector | Kotlin; vision preprocessing | Reference app reads `clip.vision.*` geometry from mmproj; preprocessing should use the tower's declared image size; image token count affects context budget | It does not establish a universal crop policy for all future product tasks | A | Very high |
| SRC-A13 | Official bindings docs | GenieX Android binding architecture | https://github.com/qualcomm/GenieX/blob/d38369a19af007bc164986b4415d9f8952a97301/bindings/android/README.md | Commit 2026-09-03 | Android AAR / native bridge | Kotlin/Java/JNI | Intended integration is public Kotlin wrapper → packaged JNI/C++ bridge → GenieX core; app consumers should not invent a second JNI layer | Does not remove the need to test native ABI/page-size compatibility | B | High |
| SRC-A14 | Official releases | GenieX releases | https://github.com/qualcomm/GenieX/releases/tag/v0.3.17 | 2026-07-24 | GenieX 0.3.17 | Android AAR | v0.3.17 publishes a 77.2 MB Android AAR and checksum sidecar | It does not prove v0.3.17 is the current reference app's tested pin or final APK delta | B | High for artefact size/release existence |
| SRC-A15 | Official issue + fix | GenieX #1116 / PR #1117 — quant case handling | https://github.com/qualcomm/GenieX/issues/1116 | Opened 2026-06-28 | GGUF model manager | Android/Python bindings | Lowercase `q4_0` failed while CLI uppercased it; linked fix confirms version-specific manager behaviour | Does not imply uppercase `Q4_0` fails in older versions | A/C | High; pinning/canonical-name rule |
| SRC-A16 | Maintainer resolution | GenieX #1067 | https://github.com/qualcomm/GenieX/issues/1067 | Updated/closed 2026-07-16 | Old Nexa NPU vs current GenieX | Android | Maintainer explicitly states `NexaAI/*-NPU` packages and old `com.nexa.demo` predate GenieX; current NPU models come from Qualcomm AI Hub and current Android quickstart should be used | Does not prove current Qwen3-VL-2B QAIRT availability | A/C | Very high anti-archaeology guard |
| SRC-A17 | Official QAIRT plugin | `geniex-qairt-plugin` | https://github.com/qualcomm/geniex-qairt-plugin | Current at access | GenieX QAIRT/QNN | Android arm64; SM8750 HTP v79 / newer chips | QAIRT plugin is the precompiled Qualcomm AI Engine Direct path and has chipset/runtime-specific native requirements | It does not establish an Android Qwen3-VL-2B QAIRT bundle | B | High |

## Model / format / accelerator evidence

| ID | Source type | Title / author | URL | Date | Relevance | What it proves | What it does **not** prove | Tier | Confidence / notes |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SRC-B01 | Official model repo | Qwen/Qwen3-VL-2B-Instruct | https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct | Current | Original model | Image-text-to-text model; Apache-2.0 license | Does not define Android runtime/export | B | High |
| SRC-B02 | GGUF model repo | unsloth/Qwen3-VL-2B-Instruct-GGUF | https://huggingface.co/unsloth/Qwen3-VL-2B-Instruct-GGUF | Current | Primary GGUF artefact | Publishes Qwen3-VL-2B GGUF quantisations and mmproj artefacts; direct repo sizes can be inspected | Qualcomm's model-manager selection may not equal an arbitrary manual pair of files | B | High |
| SRC-B03 | Upstream runtime docs | llama.cpp Snapdragon backend | https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/snapdragon/README.md | Current | GenieX llama.cpp NPU foundation | Hexagon backend supports modern Snapdragon classes and quantised GGUF paths; backend is still actively evolving | Direct llama.cpp CLI success is not GenieX Android app success | B | High for underlying backend, not app integration |
| SRC-B04 | Upstream runtime docs | llama.cpp Qualcomm OpenCL backend | https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/opencl/README.md | Current | GenieX GPU fallback foundation | Adreno OpenCL route exists and Snapdragon 8 Elite-class GPU is relevant | Does not prove Qwen3-VL-2B VLM stability through GenieX GPU on Kian's phone | B | Medium-high |
| SRC-B05 | Android platform docs | Support 16 KB page sizes — Android Developers | https://developer.android.com/guide/practices/page-sizes | Current | Any native AAR | Apps using native libraries must ensure prebuilt `.so` compatibility; Play requirement applies to target API 35+ from 2027-02-01 | Does not establish whether current GenieX AAR already passes 16 KB alignment | B | Very high; explicit open verification item |

## Alternative-runtime comparison evidence

| ID | Source type | Title / author | URL | Date | Relevance | What it proves | Why it is not primary | Tier |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| SRC-C01 | Official docs | ONNX Runtime — Build for Android / QNN EP | https://onnxruntime.ai/docs/build/android.html | Current | ORT + QNN | QNN EP on Android requires a custom ORT build with QAIRT/QNN SDK (`--use_qnn static_lib --qnn_home`) | No exact maintained Qwen3-VL-2B Android VLM route found; materially more native/toolchain work than GenieX AAR | B |
| SRC-C02 | Official docs | ExecuTorch on Android | https://docs.pytorch.org/executorch/stable/using-executorch-android.html | Current | ExecuTorch AAR | Generic Java/Kotlin AAR exists | Qualcomm backend still requires dedicated QNN integration/export path; no exact Qwen3-VL-2B path found | B |
| SRC-C03 | Official docs | ExecuTorch Qualcomm AI Engine backend | https://docs.pytorch.org/executorch/stable/backends-qualcomm.html | Current | ExecuTorch QNN | Qualcomm backend needs QNN libraries, Qualcomm-specific build/export and verification; docs give QNN 2.37-era examples | Higher integration burden and no exact target VLM evidence | B |
| SRC-C04 | Official/community issue | LiteRT-LM #1121 — Unable to run Qualcomm NPU | https://github.com/google-ai-edge/LiteRT-LM/issues/1121 | Opened 2025-12-15 | LiteRT-LM + SM8750 QNN | Developer tried multiple QAIRT versions on SM8750 and could not establish a working NPU setup | Different model/runtime; does not mean LiteRT-LM can never use Qualcomm NPU | C |
| SRC-C05 | Issue | LiteRT-LM #2056 — Android multimodal second-turn SIGSEGV | https://github.com/google-ai-edge/LiteRT-LM/issues/2056 | 2026 | LiteRT-LM VLM lifecycle | Native multimodal lifecycle/second-image failures exist; vendor-library declarations and shader cache details can matter | Different model/device; useful as failure pattern, not direct GenieX evidence | C |
| SRC-C06 | Issue | LiteRT-LM #2472 — GPU decode regression | https://github.com/google-ai-edge/LiteRT-LM/issues/2472 | Opened 2026-06-04 | Android Kotlin Qwen GPU | Real Samsung Android Kotlin version regression after runtime upgrade | Different Qwen model and Samsung generation; does not establish GenieX behaviour | C |

## Failure archaeology / issue-tracker evidence

| ID | Source type | Title | URL | Date | Runtime/model/device relevance | What it proves | Tier |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SRC-F01 | GitHub issue | GenieX #864 — historical Nexa Android model creation failure | https://github.com/qualcomm/GenieX/issues/864 | Historical / pre-GenieX | Old Nexa Android; Samsung/Nubia Snapdragon devices | Model path/file completeness, download source, license path and SDK-version problems can all present as generic model-create failures; maintainers/users reported device-dependent results | C |
| SRC-F02 | GitHub issue | GenieX #915 — unresolved native symbol / Android SDK regression | https://github.com/qualcomm/GenieX/issues/915 | Historical / pre-current GenieX | Old Nexa Android | Mixed native-library/runtime versions can fail at dynamic linking rather than graceful API error | C |
| SRC-F03 | GitHub issue | GenieX #886 — 16 KB Android page-size compliance | https://github.com/qualcomm/GenieX/issues/886 | Opened 2025-11-22 | Old Nexa bundled native libs | Historical Qualcomm/Nexa native packages were reported non-compliant with 16 KB page alignment; current GenieX must be independently checked | C |
| SRC-F04 | GitHub issue | GenieX #1054 — Android download/load mismatch | https://github.com/qualcomm/GenieX/issues/1054 | 2026 | Android model-manager / NPU | A download UI completing is not proof the exact required artefact is valid/loadable | C |
| SRC-F05 | GitHub issue | GenieX #1154 — DSP/NPU initialization / fallback question | https://github.com/qualcomm/GenieX/issues/1154 | 2026 | Qualcomm accelerator | Accelerator initialization can fail without a transparent automatic CPU rescue; app/provider must represent fallback explicitly | C |
| SRC-F06 | GitHub issue | GenieX #1178 — NPU backend crash while CPU path works | https://github.com/qualcomm/GenieX/issues/1178 | 2026 | GenieX GGUF / different Qwen generation | Backend-specific stability differs even when model functionality is otherwise valid | C |
| SRC-F07 | GitHub issue | GenieX #1250 — OpenCL/GPU crash fixed separately | https://github.com/qualcomm/GenieX/issues/1250 | 2026 | Different Qualcomm chipset | GPU path can fail independently of NPU/CPU; compute-unit smoke tests must be separate | C |
| SRC-F08 | GitHub issue | GenieX #1186 — DSP/server state crash | https://github.com/qualcomm/GenieX/issues/1186 | 2026 | GenieX accelerated generation | Accelerator/server state can crash under stateful prompt/session use; local HTTP server is unnecessary extra state for LAB-2B | C |
| SRC-F09 | GitHub issue | GenieX #1330 — QAIRT Qwen3-VL-4B power/clock behaviour | https://github.com/qualcomm/GenieX/issues/1330 | 2026 | Qwen3-VL QAIRT, non-Android evidence | NPU use is not synonymous with automatic power efficiency; profiling follows stability | C |
| SRC-F10 | GitHub issue | GenieX #1095 — Qwen3-VL Android/NPU support uncertainty | https://github.com/qualcomm/GenieX/issues/1095 | 2026 | Qwen3-VL Android | Documentation/support changed rapidly; unanswered/older support assumptions can become stale | C |
| SRC-F11 | GitHub issue | LiteRT-LM #2028 — second createConversation SIGSEGV | https://github.com/google-ai-edge/LiteRT-LM/issues/2028 | 2026-04-22 | Android native runtime lifecycle | Process/service ownership can expose repeat-init native crashes | C |
| SRC-F12 | GitHub issue | LiteRT-LM #1658 — Qwen3.5 model initialization failure | https://github.com/google-ai-edge/LiteRT-LM/issues/1658 | 2026-03-18 | Qwen-family LiteRT | "Qwen supported" is not a sufficient model/version compatibility statement | C |

## Community experience — discovery evidence only

| ID | Source type | Title | URL | Date | What it contributes | What it cannot establish | Tier |
| --- | --- | --- | --- | --- | --- | --- | --- |
| SRC-D01 | Reddit developer report | Running llama.cpp on Snapdragon Hexagon NPU seems promising | https://www.reddit.com/r/LocalLLaMA/comments/1t0k6fj/running_llamacpp_on_snapdragon_hexagon_npu_seems/ | 2026-05-01 | OnePlus 12 developer reports successful cross-compiled Hexagon Q4_0 inference and lower heat; highlights quantisation/address-space constraints | Not a Kotlin app, not Qwen3-VL-2B, not S25 | D |
| SRC-D02 | Reddit user report | Qwen3 9B on Android at Q4_0 | https://www.reddit.com/r/LocalLLaMA/comments/1rktgha/qwen3_9b_can_run_fine_on_android_phones_at_q4_0/ | 2026-03-04 | S25 Ultra / Snapdragon 8 Elite report using Hexagon NPU at >6 tok/s | Model/app/backend verification details insufficient; anecdotal | D |
| SRC-D03 | Reddit developer report | Shipped Hexagon NPU acceleration, then disabled it | https://www.reddit.com/r/qualcomm/comments/1tq3oet/we_shipped_hexagon_npu_acceleration_for_text_llms/ | 2026-05-28 | Reports real product compatibility/performance problems and need for feature-gated rollback across Snapdragon models | External PRs were not independently audited in LAB-2A; not Qwen3-VL | D |
| SRC-D04 | Reddit user report | Snapdragon 8 Elite MoE experience | https://www.reddit.com/r/LocalLLaMA/comments/1tg3ssd/llms_on_android_snapdragon_8_elite_moe_experience/ | 2026-05-17 | Shows CPU/GPU/NPU relative performance can invert by model/runtime and that GPU offload crashes can occur | Anecdotal; not exact target VLM | D |

## Source conflicts and drift

### GenieX Android AAR version

- **SRC-A03** (current Android install docs) shows `geniex-android:0.3.1`.
- **SRC-A10** (current official Android reference app at release commit `db3f977...`) pins `0.3.5`.
- **SRC-A14** confirms a later official `0.3.17` AAR exists and is 77.2 MB.

**LAB-2A decision:** do not resolve this by guessing that "latest" is best. The strongest reproducible Android source is the reference app, so LAB-2B starts by reproducing its exact pin/configuration. A newer AAR may be adopted only as one controlled change if a known fixed defect blocks the exact Qwen3-VL-2B path. The final LAB-2B pin is therefore an observed successful matrix, not a marketing/latest-version choice.

### Qwen3-VL QAIRT support

- **SRC-A04** currently documents Android QAIRT VLM support with `Qwen2.5-VL-7B-Instruct` while documenting Qwen3-VL-2B under GGUF/llama.cpp.
- **SRC-A17** and newer GenieX/QAIRT source activity show the QAIRT stack is evolving and Qwen3-VL implementations exist in the plugin family.
- Older issue **SRC-F10** reflects earlier uncertainty.

**LAB-2A decision:** exact Qwen3-VL-2B QAIRT Android support is **NOT YET ESTABLISHED**. Do not infer it from a Qwen3-VL class name or a different 4B bundle. The primary 2B route remains the directly documented GGUF/llama.cpp path.

### Model footprint

Qualcomm/GenieX UI and the direct GGUF repositories can expose different aggregate sizes depending on which projector/quantisation is selected. The exact app-private installed footprint after `ModelManagerWrapper` imports the chosen Q4_0 VLM bundle is **NOT YET ESTABLISHED** and must be measured in LAB-2B. Direct repository inspection establishes the likely order of magnitude, not the final cached byte count.

## Ledger coverage summary

This ledger records **44 materially important source entries**:

- 17 Qualcomm/GenieX/model-path primary sources (`SRC-A*`);
- 5 model/format/platform sources (`SRC-B*`);
- 6 alternative-runtime sources (`SRC-C*`);
- 12 issue/failure sources (`SRC-F*`);
- 4 community experience sources (`SRC-D*`).

The recommendation in the main report cites these IDs rather than relying on untracked search snippets.
