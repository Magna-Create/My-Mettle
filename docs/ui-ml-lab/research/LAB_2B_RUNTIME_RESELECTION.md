# LAB-2B runtime reselection — 5 September 2026

Status: **MNN 3.6.1 selected for implementation; physical acceptance pending.**

The 5 September CPU/GPU mission supersedes the original LAB-2A/playbook rules that excluded OCR, downloads and a small native bridge. It does not authorise LAB-2C or product integration.

## Decision

Use one MNN runtime for all three exact target models. Its official 3.6.1 Android release provides the inference engine as prebuilt libraries, including `libllm.so`, CPU, OpenCL and Vulkan. A small harness JNI bridge calls the public `Llm` API; it does not build MNN, patch its kernels, use QNN or compile model weights.

| Candidate | Current source evidence | Decision |
|---|---|---|
| LiteRT-LM | Official Kotlin `Engine`/`Conversation`, CPU/GPU, separate vision backend, image content and Gemma 4 E2B `.litertlm` route. Exact public Qwen3.5-2B and Qwen3-VL-2B multimodal exports compatible with its current Android runtime were not established. Qwen3.5 issue #1658 is a warning, not proof of permanent lack of support. | Strong Gemma fallback if MNN fails; not selected as the single runtime. |
| MNN 3.6.1 | Official Android prebuilt release; public Android LlmSession implementation; exact `taobao-mnn` Gemma E2B, Qwen3.5-2B and Qwen3-VL-2B model exports with vision assets. Release includes Qwen3.5 correctness fixes. All nine ARM64 release objects passed the initial static ELF check. | **Selected.** Lowest demonstrated integration burden for all three together. |
| llama.cpp | Current upstream supports Gemma4/Qwen3.5/Qwen3-VL multimodal processing, CPU and Vulkan. Android JNI/native assembly remains a larger consumer burden. A third-party Android prebuilt route exists but has weaker provenance/maintenance evidence than MNN's official release. | Credible alternative; no implementation attempt needed while MNN remains viable. |

This selection establishes an evidence-supported implementation route, **not tested S25 inference correctness**. No runtime can be called physically reliable on the target without the device tests.

## Exact pins and sources

- MNN release: <https://github.com/alibaba/MNN/releases/tag/3.6.1>
- Android archive: `mnn_3.6.1_android_armv7_armv8_cpu_opencl_vulkan.zip`, 6,197,903 bytes, published SHA-256 `46dc7e86d45b8d4e957db81d2603e0b7f6c9ce9b84092ffdcee1b843cbfc9d71`.
- Matching public headers: <https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/include/llm/llm.hpp>
- Android reference stepping/cancellation: <https://github.com/alibaba/MNN/blob/3.6.1/apps/Android/MnnLlmChat/app/src/main/cpp/llm_session.cpp>
- Actual multimodal parser/vision loader: <https://github.com/alibaba/MNN/blob/3.6.1/transformers/llm/engine/src/omni.cpp>
- Model sources: <https://huggingface.co/taobao-mnn/gemma-4-E2B-it-MNN>, <https://huggingface.co/taobao-mnn/Qwen3.5-2B-MNN>, <https://huggingface.co/taobao-mnn/Qwen3-VL-2B-Instruct-MNN>.
- LiteRT Kotlin: <https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md>
- Google Gemma support: <https://developers.google.com/edge/litert-lm/models/gemma-4>
- Exact-Qwen warning: <https://github.com/google-ai-edge/LiteRT-LM/issues/1658>
- llama.cpp: <https://github.com/ggml-org/llama.cpp/blob/master/docs/multimodal.md>
- Third-party discovery only: <https://github.com/xentron-bit/llama-android-prebuilt>
- Bundled OCR: <https://developers.google.com/ml-kit/vision/text-recognition/v2/android>, `com.google.mlkit:text-recognition:16.0.1`.

All model filenames, lengths, revisions and SHA-256 values are committed in `experiments/lab2b-vlm-harness/model-registry.json` and the typed Kotlin registry. Large-file pins came from publisher LFS metadata; small-file SHA-256 values were calculated from downloaded files at the exact revision. No model weight was needed to establish those metadata pins.

## Backend honesty

All models default to CPU. GPU requests OpenCL text decoding. The publisher configurations have a separate CPU vision backend, which the harness preserves. MNN's final per-operator placement is not exposed through this public Llm interface: GPU text remains explicitly **UNVERIFIED**, with possible per-op CPU fallback. Configured OpenCL is never relabelled as proven GPU execution.

The archive links Vulkan as a dependency of `libllm`; it remains packaged for native dependency closure, but no separate Vulkan selector is introduced. No NPU libraries or GenieX AAR are retained. `libmnncore.so`, the unused upstream general JNI bridge, is omitted.

## Model-specific choices

Gemma requires its PLE embedding file as well as decoder/vision assets. Its export also advertises audio. The harness sets `is_audio=false` before `load()`, as supported by MNN's runtime config, and does not download its unused audio graph/weights. Vision remains enabled. No source model metadata is rewritten.

The exact model chat template is applied to explicit system + current user messages by `apply_chat_template(ChatMessages)`, followed by one multimodal tokenization and `response(tokens)` after the explicit context-budget check. Only one current image path is added. Every turn resets native state and keeps KV/prompt reuse disabled. Decode uses the upstream Android `generate(1)` stepping pattern with its terminal-step restoration, allowing explicit cancellation between native operations. A running vision/prefill operation must finish before cancellation returns; destroying its handle concurrently is prohibited.

## NPU history remains closed

GenieX standard AARs 0.3.5, 0.3.19 and 0.6.1 failed the required static gate on Qualcomm Hexagon payloads. See `LAB_2B_B3_ROUTE_DECISION.md` for exact historical evidence. No Qualcomm version cycling was performed in this mission.

Historical user-reported Qwen3.5 HELLO/1234 results in the Qualcomm reference app: NPU-selected grossly incorrect block/logo interpretation; GPU-selected blank image; CPU-selected HELLO and 1924. These findings **are not attributed to MNN**. Repeat the controls on MNN CPU and GPU independently.

## Final validation

Implementation HEAD `34fcadf8e25387f4afb534811124fbd6fd456081` passed dedicated Actions run `33942676654`: testDebugUnitTest (19 declared tests), assembleDebug, lintDebug, source/binary safety and every packaged ELF/APK static 16 KB check. Artifact: `lab2b-vlm-harness-debug`, ID `9962381669`. The final APK has 36,326,209 bytes and SHA-256 `b670336d205837e71d9c0633ec2029f7a0922fb7978792e14b9a9a4edaddb1a2`. See `LAB_2B_FINAL_NATIVE_AUDIT.txt`.

Stopped or failed turns dispose the engine after the current native operation returns: MNN clears pending vision embeddings in prefill, while reset alone does not clear a prefill-aborted turn. This deliberately requires Load again and prevents old-image reuse. Completed turns retain the loaded model and reset context. No S25 inference result is claimed.
