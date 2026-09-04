# LAB-2A Android VLM implementation research

> **Status:** LAB-2A research report. Research only; no My Mettle runtime integration is performed here.
>
> **Target device:** Samsung Galaxy S25 Ultra, Snapdragon 8 Elite (`SM8750` class).
>
> Source IDs resolve through [`LAB_2A_SOURCE_LEDGER.md`](./LAB_2A_SOURCE_LEDGER.md). Failure IDs resolve through [`LAB_2A_FAILURE_ARCHAEOLOGY.md`](./LAB_2A_FAILURE_ARCHAEOLOGY.md).

## 1. Executive conclusion

LAB-2A recommends one primary implementation route for LAB-2B:

```text
Qwen3-VL-2B-Instruct
→ GGUF Q4_0 + model-manager-resolved mmproj
→ Qualcomm GenieX Android SDK (Maven AAR)
→ GenieX runtime_id = llama_cpp
→ arm64-v8a
→ Snapdragon 8 Elite / SM8750
→ explicit NPU proof first
→ same runtime + same model on GPU, then CPU, only as controlled fallback
```

This route wins because it is the only route found that combines all of the following in current first-party evidence:

- an exact **Qwen3-VL-2B-Instruct** multimodal example;
- a current **Kotlin Android VLM API**;
- a maintained **working Android reference app** from Qualcomm;
- a Maven **AAR** that hides the consumer app's JNI/CMake integration;
- one model/runtime family that can target Hexagon NPU, Adreno GPU and ARM CPU;
- a model manager that supports downloaded or local-file-imported model bundles outside the APK;
- explicit streaming, cancellation, reset and destroy APIs;
- current Snapdragon 8 Elite (`SM8750`) documentation. [SRC-A01, SRC-A03, SRC-A04, SRC-A09, SRC-A11]

The most important qualification is that the exact target combination — **Qwen3-VL-2B-Instruct Q4_0 + current GenieX Android AAR + Samsung Galaxy S25 Ultra NPU** — has not yet been physically proven in this project. That is precisely the narrow purpose of LAB-2B.

The current evidence does **not** justify using QAIRT as the primary route for the 2B model. Qualcomm's Android API currently documents Qwen3-VL-2B through GGUF/`llama_cpp`, while the documented precompiled QAIRT VLM table names Qwen2.5-VL-7B. Newer QAIRT source/model activity includes Qwen3-VL-family work, but **Qwen3-VL-2B QAIRT Android is NOT YET ESTABLISHED**. [SRC-A04, SRC-A17, SRC-F10]

LAB-2A also rejects the old Nexa Android/NPU path. Qualcomm maintainers explicitly state the `NexaAI/*-NPU` packages and old `com.nexa.demo` route predate current GenieX. [SRC-A16]

## 2. My Mettle constraints

The deployment route must satisfy product constraints rather than merely produce a benchmark:

1. Kotlin-first Android integration.
2. Samsung Galaxy S25 Ultra / Snapdragon 8 Elite is the immediate target.
3. VLM must accept image + text locally.
4. Model assets must not turn the app APK into a multi-gigabyte package.
5. Local provider remains replaceable behind LAB-1's `LOCAL` provider boundary.
6. Model lifecycle must map cleanly to `NOT_INSTALLED → INSTALLING → READY_VERIFIED`, plus incompatible/corrupt/removal/failure states.
7. Unknown support must remain unknown rather than optimistic.
8. One user-facing AI experience; backend selection remains infrastructure.
9. No equipment semantics, N-BIO logic or OCR belongs in the runtime.
10. First prove one known-good path; benchmarking and backend comparisons come later.

The LAB-1 contract is compatible with GenieX. No architectural contradiction was found. GenieX-specific runtime/model details can remain below the future `LOCAL` adapter; its manager paths, runtime ID, model identity, byte size and integrity information map naturally into `LocalModelMetadata` without changing the generic provider contract.

## 3. Physical S25 Ultra evidence

LAB-1 was physically exercised before LAB-2A on the target device.

| Field | Physical result |
| --- | --- |
| Device | Samsung Galaxy S25 Ultra |
| Chipset class | Snapdragon 8 Elite |
| System runtime | ML Kit GenAI Prompt API 1.0.0-beta4 |
| System provider | `UNAVAILABLE` |
| Base model | not reported |
| TEXT | `UNSUPPORTED` |
| IMAGE_INPUT | `UNKNOWN / UNVERIFIED` |
| STRUCTURED_OUTPUT | `UNKNOWN / UNVERIFIED` |
| SYSTEM_INSTRUCTIONS | `UNKNOWN / UNVERIFIED` |
| MULTI_IMAGE | `UNKNOWN / UNVERIFIED` |
| AUTO resolver | `LOCAL_INSTALL_REQUIRED` |
| SYSTEM override | `PROVIDER_UNAVAILABLE` |
| LOCAL override | `LOCAL_INSTALL_REQUIRED` |
| Force-close/restart | unchanged on repeat probe |

**Strong physical conclusion:** the current ML Kit Prompt provider is unavailable on this device, therefore My Mettle currently needs a local fallback if it is to provide local AI.

**Non-conclusion:** the `UNKNOWN / UNVERIFIED` capability states do **not** prove the hardware itself lacks those capabilities. LAB-2A does not reinterpret them.

## 4. Research methodology

Research prioritised implementation archaeology rather than benchmark ranking.

The process was:

1. lock Lab/N-BIO heads and re-read the LAB contracts;
2. trace Qwen3-VL-2B from model page → model format → runtime → Android API → reference app;
3. inspect the actual Qualcomm Kotlin source rather than relying on quick-start prose;
4. search issues for exact Android/native/runtime failures;
5. inspect alternative maintained runtimes only far enough to determine whether they offer a comparably proven target path;
6. search community reports for cross-device pain that official docs may omit;
7. convert failures into preventative LAB-2B rules;
8. select one primary route and one same-stack functional fallback policy.

The detailed source ledger records 44 source entries across official Qualcomm/model/platform material, issue trackers, alternative runtimes and community reports. Search-result snippets that did not materially affect the recommendation are not promoted into the ledger.

## 5. Evidence hierarchy

LAB-2A uses the programme hierarchy:

- **Tier A:** current working Android repository/sample, source demonstrating inference, or maintainer-confirmed fix.
- **Tier B:** official runtime/model/platform documentation.
- **Tier C:** technically detailed issue/forum evidence with credible configuration or resolution.
- **Tier D:** anecdotal community reports useful mainly for identifying failure classes.

The primary route rests on Tier A/B evidence: Qualcomm's current GenieX Android reference app and source, current Android API, current model documentation and exact Qwen3-VL example. Tier D evidence does not decide the route.

## 6. Qwen3-VL Qualcomm artefact findings

### 6.1 Exact preferred model

Original model:

```text
Qwen/Qwen3-VL-2B-Instruct
license: Apache-2.0
modality: image + text → text
```

[SRC-B01]

Qualcomm's current model page exposes Qwen3-VL-2B-Instruct using **GenieX - llama.cpp** with `q4_0` and a quick-start model identifier `unsloth/Qwen3-VL-2B-Instruct-GGUF`. [SRC-A01]

GenieX's CLI docs also use a Qwen-hosted GGUF identifier for the same model family. This is a repository/source naming difference, not evidence of a different runtime contract. The Android API's concrete Qwen3-VL-2B sample uses the Unsloth GGUF identifier, so LAB-2B should begin with that exact identifier unless the current reference-app catalog at implementation time resolves a newer canonical alias. [SRC-A04, SRC-A05]

### 6.2 Quantisation

**Recommended:** `Q4_0`.

GenieX explicitly recommends Q4_0 for the Hexagon NPU path. A historical/current manager issue also demonstrates that quantisation spelling has mattered across bindings, so the harness should use the canonical uppercase string exactly. [SRC-A05, SRC-A15]

Do not begin LAB-2B by comparing Q4_K_M, Q8, F16 and multiple model sizes. If Q4_0 cannot load through the intended NPU path, diagnose that path first.

### 6.3 Model files

A GGUF VLM requires at least:

- main LLM GGUF weights (`model_path`);
- multimodal projection/vision file (`mmproj_path`).

The Android API supplies both through `ModelManagerWrapper.getPaths()`. [SRC-A04]

Direct repository inspection shows a Q4_0 main file on the order of ~1.06 GB and projector artefacts that can be hundreds of MB (for example ~819 MB for an F16 projector in the direct repository). That suggests a direct pair can approach ~1.9 GB. However, the **exact cached bundle selected by GenieX for the LAB-2B import is NOT YET ESTABLISHED**. LAB-2B must record actual `ModelPaths`, file hashes and directory size rather than reporting a web-page aggregate as installed truth. [SRC-B02]

### 6.4 Intended chipset and compute path

Current GenieX Android install documentation explicitly targets Snapdragon 8 Elite `SM8750` and Snapdragon 8 Elite Gen 5 `SM8850`. The `llama_cpp` runtime supports NPU/GPU/CPU compute-unit aliases. [SRC-A03, SRC-A04]

For QAIRT bundles, Android requires an explicit chipset string because that artefact is hardware-targeted. The primary GGUF route is not a per-chipset QAIRT binary, but the underlying accelerator still needs target-device validation. [SRC-A04]

### 6.5 QAIRT status

There are two separate GenieX runtime families:

| Runtime | Format | Android compute | Exact Qwen3-VL-2B evidence |
| --- | --- | --- | --- |
| `llama_cpp` | GGUF | Hexagon NPU / Adreno GPU / CPU | **YES — direct Android VLM example** |
| `qairt` | AI Hub precompiled `.bin` bundle | Hexagon NPU only | **NOT YET ESTABLISHED for 2B** |

Current Android API docs list `Qwen2.5-VL-7B-Instruct` as the supported QAIRT VLM example. Newer plugin/model activity means QAIRT support is evolving, but it is not acceptable to infer a 2B bundle from a Qwen3-VL class or a 4B release. [SRC-A04, SRC-A17]

## 7. Successful Android implementation patterns

The strongest real implementation is Qualcomm's **GenieX Chat Android** sample at `qualcomm/ai-hub-apps`, release commit `db3f9772...`. It is a Kotlin/Java Android application that loads LLMs/VLMs through the GenieX Maven AAR and supports NPU/GPU/CPU. Model weights are downloaded separately rather than bundled in the APK. [SRC-A09]

### 7.1 Concrete build matrix from the reference app

At the researched revision:

| Setting | Reference app value |
| --- | --- |
| AGP | 8.13.0 |
| Kotlin Android plugin | 2.2.0 |
| Java/Kotlin target | 17 |
| `compileSdk` | 34 |
| `targetSdk` | 34 |
| `minSdk` | 31 |
| declared NDK | 27.3.13750724 |
| GenieX AAR | 0.3.5 |
| JNI packaging | `jniLibs.useLegacyPackaging = true` |
| ABI support relevant here | arm64-v8a |

[SRC-A10]

These are **reproduction pins**, not claimed universal requirements. The install docs independently state a consumer does not need to write its own NDK/CMake integration because the AAR contains the arm64 native libraries. [SRC-A03]

### 7.2 Kotlin call shape that actually exists

For VLM generation the current source/API follows this shape:

```text
ModelManagerWrapper.getPaths(model)
→ VlmWrapper.builder() + VlmCreateInput
→ VlmChatMessage(
     image absolute file path,
     text
  )
→ applyChatTemplate(...)
→ injectMediaPathsToConfig(current turn only)
→ generateStreamFlow(formattedText, config)
→ Token / Completed / Error
```

The reference app handles model operations on an IO coroutine scope, blocks re-entry while generation is active, exposes stop, and unloads via `stopStream()` then `destroy()`. [SRC-A04, SRC-A11]

### 7.3 Download/import pattern

The reference application uses the model manager rather than asking app code to reason about random model files. Current GenieX also supports `HubSource.LOCALFS`, which imports a local GGUF directory into the SDK cache without network access. [SRC-A06]

That is particularly useful to LAB-2B: the runtime proof can use a pre-provisioned local model directory and import it once, avoiding network/download UX while still using the official manager/cache contract.

## 8. Viable runtime routes

Only routes with credible Android relevance were retained.

### Route R1 — GenieX Android AAR + llama.cpp GGUF

**Status: PRIMARY.**

Strengths:

- exact Qwen3-VL-2B VLM Android example;
- Kotlin API and official Android app;
- AAR consumption, no custom consumer JNI/CMake;
- same model/runtime can target NPU/GPU/CPU;
- external/local model manager;
- streaming/cancellation/destroy lifecycle;
- explicit SM8750 support. [SRC-A03, SRC-A04, SRC-A09]

Risks:

- Hexagon backend is evolving quickly;
- official docs/reference/release versions are not perfectly aligned;
- exact S25 Ultra/Qwen3-VL-2B NPU proof is still pending;
- native AAR 16 KB page-size compliance must be verified before production shipping.

### Route R2 — GenieX + QAIRT / Qualcomm AI Hub bundle

**Status: viable family, not selected for the 2B primary proof.**

Strengths:

- Qualcomm-native precompiled NPU route;
- same high-level GenieX SDK/provider shape;
- explicit SM8750 bundles;
- no custom app QNN glue when a supported bundle exists. [SRC-A04, SRC-A17]

Reason not primary:

- exact Qwen3-VL-2B QAIRT Android support is not currently documented;
- documented Android VLM is Qwen2.5-VL-7B;
- using a Qwen3-VL-4B bundle would change the preferred model and likely storage/memory profile solely to gain the precompiled path;
- QAIRT is NPU-only, giving less same-model fallback flexibility.

### Route R3 — direct llama.cpp Hexagon/OpenCL/CPU

**Status: technically viable, rejected for LAB-2B.**

Strengths:

- underlying exact GGUF model path exists;
- direct access to Hexagon/OpenCL/CPU;
- active upstream Qualcomm work. [SRC-B03, SRC-B04]

Reason rejected:

- requires native cross-build/toolchain and a Kotlin/JNI wrapper or command bridge;
- duplicates the same runtime work GenieX packages in an Android AAR;
- recreates the exact native integration surface LAB-2A is meant to avoid.

### Route R4 — ONNX Runtime + QNN EP

**Status: rejected.**

Official Android QNN instructions require building ORT with the QNN SDK and static QNN support. No maintained exact Qwen3-VL-2B multimodal Android implementation was found. [SRC-C01]

### Route R5 — ExecuTorch + Qualcomm backend

**Status: rejected for this mission.**

ExecuTorch has a good generic Android AAR/Java API, but its Qualcomm path requires dedicated QNN integration/export and native libraries, and no exact Qwen3-VL-2B VLM deployment recipe was found. [SRC-C02, SRC-C03]

### Route R6 — LiteRT-LM

**Status: rejected for the Qwen3-VL Qualcomm proof.**

LiteRT-LM has useful Kotlin/mobile design qualities, but current issue evidence shows Qwen-family/runtime regressions, multimodal native crashes and difficulty establishing SM8750 QNN matrices. No evidence was found that outweighs GenieX's exact model + Qualcomm Android sample for this target. [SRC-C04, SRC-C05, SRC-C06]

### Route R7 — old Nexa Android SDK / manually assembled QNN

**Status: explicitly rejected.**

Qualcomm maintainers state this route is obsolete. [SRC-A16]

## 9. Route comparison

Scale: **Excellent / Good / Mixed / Poor / Unproven**. Major scores cite source IDs.

| Criterion | R1 GenieX + GGUF | R2 GenieX + QAIRT | R3 direct llama.cpp | R4 ORT+QNN | R5 ExecuTorch+QNN | R6 LiteRT-LM |
| --- | --- | --- | --- | --- | --- | --- |
| Real Android success | **Excellent** [A09-A11] | Good [A09, A04] | Mixed [B03, D01] | Mixed [C01] | Good generic, Mixed Qualcomm [C02-C03] | Good generic, Mixed target [C04-C06] |
| Qwen3-VL-2B compatibility | **Excellent** [A01,A04] | **Unproven** [A04] | Good [B03] | Unproven | Unproven | Unproven |
| Multimodal support | **Excellent** [A04,A11] | Good for supported VLMs [A04] | Good upstream | Unproven exact model | Unproven exact model | Mixed [C05] |
| Snapdragon 8 Elite relevance | **Excellent** [A03,A04] | **Excellent** [A04] | Good [B03] | Good QNN family | Good QNN family | Mixed [C04] |
| Kotlin integration | **Excellent** [A04,A11,A13] | **Excellent** | Poor without custom JNI | Good Java API but custom QNN build | Good generic API | Good Kotlin API |
| Native build complexity | **Good/low** [A03] | Good when bundle supported | **Poor/high** | **Poor/high** | Mixed/high for QNN | Mixed |
| Version fragility | Mixed [A10,A14,A15] | Mixed/high | Mixed/high | High | High | High [C04,C06] |
| Stability evidence | Good but target still unproven [A11] | Mixed | Mixed/community | Unproven target | Unproven target | Mixed [C05,C06] |
| NPU access | **Yes** [A04] | **Yes / NPU-only** [A04] | Yes [B03] | Yes | Yes | Possible but target matrix problematic [C04] |
| Same-model fallback | **NPU→GPU→CPU** [A04] | Poor; NPU-only | NPU/GPU/CPU | Depends build/provider | Depends exported delegates | Depends backend/model |
| Model download friendliness | **Excellent** [A06,A09] | **Excellent** [A04] | Manual | Manual/custom | Custom | Varies |
| APK-size impact | AAR-sized, model external [A09,A14] | AAR + runtime, model external | Custom native bundle | Custom AAR/native | AAR + backend libs | AAR/native |
| Debuggability | **Good**: runtime/model manager/logs | Good but QNN-specific | Powerful but low-level | Low-level | Medium | Medium |
| Maintenance burden | **Lowest of target-proven routes** | Medium/vendor bundle dependence | High | High | High | Medium-high |
| Official support | **Excellent** [A02-A11] | **Excellent** for supported bundles | Good upstream | Good generic | Good | Good generic |

## 10. Kotlin integration findings

### Recommended shape

LAB-2B should use the **direct Kotlin API exposed by the GenieX AAR**. It should not create its own JNI wrapper.

Conceptually:

```text
Compose/minimal Activity
        ↓
HarnessRuntimeOwner (Kotlin, single owner)
        ↓
GenieX ModelManagerWrapper + VlmWrapper
        ↓
AAR-packaged JNI/native GenieX runtime
        ↓
llama.cpp → Hexagon/OpenCL/CPU
```

The official binding already owns the Kotlin→JNI→native bridge. [SRC-A13]

### Lifetime ownership

The reference demo stores wrappers on its Activity, but LAB-2A does **not** elevate Activity ownership into a production rule. What the evidence strongly establishes is:

- one live model wrapper at a time;
- one active inference per wrapper;
- explicit stop/reset/destroy;
- work off main thread. [SRC-A11]

For LAB-2B, use one application/process-scoped `HarnessRuntimeOwner` or repository object rather than making Activity recreation itself own the native handle. This is an architectural inference intended to make lifecycle testing clearer, not a claim that Qualcomm mandates process scope.

### Kotlin-facing future LAB-2C shape

The eventual adapter should be task-specific and typed, for example conceptually:

```text
LocalVlmProvider
  probe()
  ensureInstalled()/verify()/remove() through LocalModelLifecycle
  interpretImage(request: TypedImagePromptRequest): TypedResult
```

Do not expose GenieX classes through the generic LAB-1 provider contract.

## 11. Model storage/download findings

### APK requirement

The official Android reference app keeps model weights outside the APK and downloads them at runtime. GenieX itself supports external model management. [SRC-A09]

The latest visible official v0.3.17 AAR release asset is **77.2 MB**. That is an artefact size, not an exact compressed APK delta. [SRC-A14]

Therefore the likely product shape is compatible with:

```text
normal app APK/AAB
+ runtime AAR/native libraries
+ separately installed app-private model bundle
```

### LAB-2B strategy

LAB-2B should **not** begin by implementing production model downloading. Use a pre-downloaded GGUF directory and import it through:

```text
HubSource.LOCALFS
+ ModelPullInput.local_path
→ ModelManagerWrapper cache
→ getPaths()
```

This is a network-free supported path and avoids manually writing into SDK cache internals. [SRC-A06]

### Future product lifecycle

For LAB-2C/product integration, wrap GenieX's manager inside the existing LAB-1 lifecycle:

1. download/import to staging or manager inflight state;
2. verify expected files and integrity identity;
3. load-test before declaring `READY_VERIFIED`;
4. atomically make the verified version current;
5. retain/rollback previous verified version until switch is safe;
6. remove obsolete versions through the lifecycle owner;
7. clean partial/corrupt assets explicitly.

The exact relationship between GenieX's cache hashes and My Mettle's `integritySha256` is an implementation decision for LAB-2C, not LAB-2A.

## 12. Lifecycle/stability findings

Repeated successful/current patterns:

- initialise runtime once for the app session, not per prompt;
- load a single model wrapper off the UI thread;
- serialise inference against a wrapper;
- stream result through Flow;
- call `stopStream()` for cancellation;
- call `reset()` when deliberately clearing conversation/KV state;
- call `destroy()` when unloading;
- do not reload merely because Activity recomposed;
- repeat load/infer/unload and force-close/relaunch in LAB-2B. [SRC-A11]

Unresolved lifecycle questions that LAB-2B must measure:

- behaviour over Activity recreation while process survives;
- behaviour across background/foreground;
- whether process death leaves only clean model-manager state;
- peak/cold/steady memory;
- whether explicit unload returns meaningful RSS;
- thermal behaviour over repeated requests.

No evidence justified inventing a background-unload timeout in LAB-2A.

## 13. Accelerator verification findings

"Requested NPU" is not enough.

LAB-2B should require all of the following for an NPU PASS:

1. `ModelPaths.runtime_id` resolves to `llama_cpp` for the primary GGUF path. [SRC-A04]
2. `compute_unit` is explicitly requested as `"npu"` for the proof rather than relying on default/auto.
3. runtime/debug logs are captured from `adb logcat` during model creation and generation.
4. logs show the Hexagon/HTP route or equivalent runtime-resolved NPU device identity for the pinned GenieX version.
5. run the same model/image/prompt deliberately with `"cpu"`; verify logs/backend identity change.
6. performance differences may corroborate but do not establish backend identity.

Underlying GenieX/llama.cpp documentation describes NPU aliases resolving to HTP devices and distinguishes runtime/compute unit. [SRC-A04, SRC-A08, SRC-B03]

If the Android AAR does not expose a stable programmatic backend ID beyond model paths/compute request, LAB-2B must preserve the relevant log lines as acceptance evidence. A deep QNN profiler is optional after baseline correctness; it is not required before one reliable inference exists.

### Legitimate fallback

If NPU init fails but the GGUF model is valid:

1. record NPU failure exactly;
2. use the **same GenieX `llama_cpp` runtime and same Q4_0 model** with `compute_unit="gpu"`;
3. if GPU also fails, use `compute_unit="cpu"` as the diagnostic/functional path;
4. do not silently report the fallback as NPU.

The goal is to keep functional model proof separate from accelerator proof while avoiding a second runtime/model. [SRC-A04, SRC-F05, SRC-F06, SRC-F07]

## 14. Version/toolchain findings

### Known-good reference matrix

The strongest concrete Kotlin Android matrix is SRC-A10:

```text
ai-hub-apps release commit: db3f9772d4e423dee2df517335009c703845dba8
AGP: 8.13.0
Kotlin: 2.2.0
JDK/JVM: 17
compileSdk/targetSdk: 34
minSdk: 31
ndkVersion declared: 27.3.13750724
GenieX Android AAR: 0.3.5
jniLibs.useLegacyPackaging: true
ABI: arm64-v8a
```

### Version conflict

Current install docs show AAR **0.3.1**, while the official current reference source pins **0.3.5**, and official release assets include **0.3.17**. [SRC-A03, SRC-A10, SRC-A14]

Therefore:

- **REQUIRED PIN:** exact GenieX AAR version used in a proven LAB-2B run.
- **RECOMMENDED STARTING PIN:** `0.3.5` because it is the current reference app's explicit pin.
- **REQUIRED PIN:** exact reference-app commit/configuration used for baseline.
- **RECOMMENDED PIN:** AGP 8.13.0, Kotlin 2.2.0, JVM 17 for the standalone proof, because that avoids introducing My Mettle's newer build toolchain as another variable.
- **SOURCE DOES NOT ESTABLISH:** CMake version for an AAR consumer; no CMake project should exist in the harness.
- **SOURCE DOES NOT ESTABLISH:** that declared NDK 27.3 is strictly required to consume the AAR; docs say consumer-side NDK/CMake is unnecessary, but matching the reference Gradle file initially is cheap.

If 0.3.5 is blocked by a defect already fixed upstream, advance once to a specifically identified stable release and freeze it. Do not try 0.3.6, 0.3.7, 0.3.8… as a debugging strategy.

### 16 KB page-size risk

My Mettle eventually targets modern Android/Play requirements and the GenieX AAR includes native libraries. Android requires native/prebuilt libraries to be 16 KB-compatible for target API 35+ Play submissions from 2027-02-01. Historical Nexa packages had a 16 KB issue; that does not prove current GenieX is affected. [SRC-B05, SRC-F03]

**Action:** LAB-2B packaging audit should inspect the exact pinned AAR/APK for 16 KB alignment. Failure here is a packaging/version blocker for LAB-2C even if the S25 itself runs the debug APK.

## 15. Licensing/distribution findings

This is factual licence tracking, not legal advice.

| Component | Source-stated licence/terms | Distribution implication |
| --- | --- | --- |
| Qwen3-VL-2B-Instruct | Apache-2.0 [SRC-B01] | Redistribution/modification generally allowed subject to Apache notice/licence terms; preserve required notices |
| Unsloth Qwen3-VL GGUF repo | model repo states Apache-2.0 / inherits model licensing context [SRC-B02] | Keep model licence/notice with any hosted/downloaded derivative; verify exact repository notice before production hosting |
| GenieX repository | BSD-3-Clause plus Qualcomm repository/terms context [SRC-A02] | Include required BSD copyright/notice for redistributed covered code; AAR/service terms require separate review |
| Qualcomm AI Hub | service/model artefacts subject to Qualcomm AI Hub terms in addition to underlying model licence [SRC-A01] | Do not assume an AI Hub download URL can simply be mirrored by My Mettle |
| ai-hub-apps sample | BSD-3-Clause [SRC-A09] | Reference source can inform implementation subject to notice requirements |

Before My Mettle hosts model files itself, perform a dedicated distribution review of the exact GGUF repo, model licence, GenieX AAR licence/notice and hosting source terms. LAB-2A does not assert that Qualcomm/Hugging Face service URLs may be redistributed as product infrastructure.

## 16. Failure archaeology summary

The detailed table lives in [`LAB_2A_FAILURE_ARCHAEOLOGY.md`](./LAB_2A_FAILURE_ARCHAEOLOGY.md).

The dominant recurring categories are:

1. obsolete SDK/tutorial paths;
2. mixed native runtime/library versions;
3. wrong/incomplete model artefacts;
4. model-manager/version-specific quant bugs;
5. QNN/HTP init and device-permission/runtime mismatch;
6. silent/assumed accelerator behaviour;
7. backend-specific crashes with CPU success;
8. native-handle concurrency races;
9. repeat-init/lifecycle crashes;
10. incorrect chat-template/media injection;
11. insufficient multimodal context budget;
12. hard-coded image preprocessing geometry;
13. runtime/model registry mismatch;
14. Android native packaging/page-size incompatibility;
15. performance/power regressions after runtime upgrades.

These are implementation-control problems, not evidence that one benchmark winner should replace the model.

## 17. Anti-patterns

### DO NOT DO THIS

- Do not use old Nexa Android/NPU tutorials. [SRC-A16]
- Do not mix arbitrary QNN/GenieX/native library versions. [SRC-F02]
- Do not manually mutate GenieX's model cache. Use ModelManager/LOCALFS. [SRC-A06]
- Do not bundle multi-gigabyte model weights into the APK. [SRC-A09]
- Do not mark a partial/completed download as `READY_VERIFIED` without verification. [SRC-F04]
- Do not launch model creation or generation on the main thread. [SRC-A11]
- Do not create a runtime per prompt. Reuse one loaded wrapper. [SRC-A11]
- Do not run concurrent generation on one native handle. [SRC-A11]
- Do not feed raw text where chat-templated text is required. [SRC-A07]
- Do not replay historical images into current media injection. [SRC-A11]
- Do not hard-code image dimensions from another VLM. [SRC-A12]
- Do not start with multi-image or long conversation state. [SRC-A11]
- Do not assume NPU execution because `npu` was requested or because the phone stayed cool. [SRC-A04, SRC-D01]
- Do not infer Qwen3-VL-2B QAIRT support from another Qwen3-VL artefact. [SRC-A04]
- Do not tune QAIRT like llama.cpp. [SRC-A07]
- Do not adopt "latest everything" after one failure. [SRC-A10, SRC-C04, SRC-C06]
- Do not run broad model/backend benchmark suites before restart/repeat reliability.

## 18. Primary recommendation

### PRIMARY ROUTE

| Field | Recommendation |
| --- | --- |
| MODEL | `Qwen3-VL-2B-Instruct` |
| ARTEFACT | `unsloth/Qwen3-VL-2B-Instruct-GGUF` as used by current Android API/model page; resolve exact files through ModelManager |
| QUANTISATION | `Q4_0` |
| RUNTIME | Qualcomm GenieX `llama_cpp` |
| RUNTIME VERSION | Begin with reference-app pin `com.qualcomm.qti:geniex-android:0.3.5`; final accepted pin is the exact version physically proven in LAB-2B |
| ANDROID API | GenieX Maven AAR; `ModelManagerWrapper` + `VlmWrapper` |
| KOTLIN/NATIVE SHAPE | Kotlin direct API → AAR-packaged JNI/native runtime; **no custom JNI/CMake** |
| TARGET ABI | `arm64-v8a` |
| TARGET CHIPSET | Snapdragon 8 Elite / `SM8750` class |
| NDK | Reference app declares `27.3.13750724`; consumer-native build not required by GenieX docs |
| CMAKE | **NOT REQUIRED / NOT YET ESTABLISHED** for AAR consumer; do not add it |
| AGP/KOTLIN | Reproduction baseline AGP 8.13.0 / Kotlin 2.2.0 / JVM 17; do not begin on My Mettle's newer toolchain |
| MODEL LOCATION | Pre-provisioned local GGUF directory → `HubSource.LOCALFS` import → GenieX app-private/cache-managed paths |
| NATIVE LIB PACKAGING | Use AAR as published; mirror reference `jniLibs.useLegacyPackaging=true` initially; separately audit 16 KB compatibility |
| VISION INPUT | App-private image file absolute path; read projector geometry; image + text `VlmContent`; one image initially |
| THREADING | One owner; model IO on dedicated IO/coroutine context; one inference at a time |
| RUNTIME OWNERSHIP | Single process-scoped harness owner; reuse wrapper across prompts; explicit destroy on unload/test teardown |
| LOAD/UNLOAD | Lazy explicit load; keep loaded through repeat inference; cancellation `stopStream`; unload `stopStream → destroy` |
| BACKEND | Explicit NPU first; same runtime/model GPU fallback; CPU diagnostic/fallback |
| BACKEND VERIFICATION | runtime ID + explicit compute request + logcat accelerator/session evidence + CPU control |
| MODEL UPDATE | Not part of first LAB-2B proof; future versioned verified model lifecycle |
| KNOWN RISKS | AAR/version drift; rapidly evolving Hexagon backend; exact S25 proof pending; exact installed footprint pending; 16 KB native alignment pending |

Confidence: **high that this is the best route to test; medium-high that NPU will work first attempt on the exact S25/Qwen3-VL combination.** The uncertainty is why LAB-2B exists.

## 19. Fallback recommendation

Do **not** choose a second model/runtime as the immediate fallback.

Use the same GenieX AAR + same Qwen3-VL-2B Q4_0 GGUF:

1. **NPU (`npu`)** — target path.
2. **GPU (`gpu`)** — first functional fallback if NPU initialization fails.
3. **CPU (`cpu`)** — diagnostic/compatibility fallback if GPU also fails.

This keeps every variable except the compute unit constant and lets LAB-2B answer whether a failure is model-level or accelerator-level. [SRC-A04]

A future QAIRT Qwen3-VL route may be reconsidered if Qualcomm publishes an exact supported 2B Android bundle. It is not the LAB-2B fallback today.

## 20. LAB-2B minimal harness specification

LAB-2B should be a separate, tiny Android project, not a My Mettle module.

Minimum project structure:

```text
lab2b-vlm-harness/
  settings.gradle(.kts)
  build.gradle(.kts)
  app/
    build.gradle.kts
    src/main/AndroidManifest.xml
    src/main/java/.../
      MainActivity.kt
      HarnessRuntimeOwner.kt
      HarnessState.kt
    src/main/res/...
```

No Room, no N-BIO, no equipment types, no OCR, no CameraX.

### Minimal screen

- runtime/model status;
- `Import model` or fixed localfs import action;
- `Load`;
- `Select image` using system picker;
- short prompt field with fixed default;
- `Run`;
- streamed/final output;
- backend/runtime/log evidence summary;
- elapsed load/TTFT/decode metrics;
- `Stop` and `Unload` developer buttons.

### Acceptance sequence

```text
BUILD
↓
LOAD
↓
ONE IMAGE
↓
ONE SHORT PROMPT
↓
ONE RESPONSE
↓
FORCE-CLOSE / RELAUNCH
↓
REPEAT
↓
SECOND SERIAL INFERENCE
↓
EXPLICIT UNLOAD / RELOAD
↓
ONLY THEN PROFILE
```

### Test matrix before profiling

1. Q4_0 / NPU, one image, one prompt.
2. Repeat same path after process restart.
3. Repeat serial generation in one loaded session.
4. Stop during generation once; then prove next request remains valid.
5. Unload/destroy then reload.
6. Only if NPU fails: same exact bundle with GPU.
7. Only if GPU fails: same exact bundle with CPU.

## 21. Open questions

1. Which exact GenieX Android AAR version is the best LAB-2B freeze? `0.3.5` is the strongest current reference-app pin; later official releases exist. Step 0 must convert this into a physically proven pin.
2. Does Qwen3-VL-2B Q4_0 execute successfully on the S25 Ultra's Hexagon NPU through the current AAR?
3. What exact `ModelPaths`/projector variant does the model manager select for this bundle?
4. What is the exact cached model footprint on Android after localfs import?
5. What are cold-load, steady RSS, peak RSS, TTFT and decode rates on the 12 GB S25 Ultra?
6. How does the exact AAR expose/log resolved Hexagon/HTP device identity on Android?
7. Do the pinned AAR's native libraries satisfy Android 16 KB page-size alignment?
8. Does Activity recreation/background-resume leave the process-scoped wrapper stable?
9. How completely does `destroy()` return native memory to the process?
10. Is an exact Qwen3-VL-2B QAIRT Android bundle published later? If so, it should be evaluated as a separate route update, not assumed now.
11. What exact notices/terms are required if My Mettle later hosts the GGUF bundle itself rather than relying on user/local import or upstream hosting?

## 22. Explicit STOP decision

**LAB-2A research is complete enough to hand one route to human review.**

The route is not authorised for implementation by this document alone.

LAB-2B must remain **NOT STARTED** until the research/playbook is reviewed and explicitly accepted.

No My Mettle runtime code, dependency, JNI, NDK/CMake configuration, model file, download code, equipment UI or N-BIO behaviour was added during this research phase.
