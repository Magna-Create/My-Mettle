# LAB-2B physical acceptance

> **Status:** LAB-2B IN PROGRESS — B0/B1 reference-app physical gate pending.
>
> **Rule:** no physical result is marked PASS without evidence from the target Samsung Galaxy S25 Ultra. Build success is not native-runtime success.

## Target device

| Field | Evidence |
| --- | --- |
| Device | Samsung Galaxy S25 Ultra |
| SoC | Snapdragon 8 Elite / SM8750 class |
| Android build | **PENDING PHYSICAL RESULT** |
| Device page size | **PENDING PHYSICAL RESULT** |
| Official page-size command | `adb shell getconf PAGE_SIZE` |
| Direct-on-device Termux equivalent | `getconf PAGE_SIZE` |

Android's current 16 KB guidance uses `adb shell getconf PAGE_SIZE`; `16384` denotes a 16 KB environment. The exact S25 Ultra result must be recorded rather than assumed.

## B0 — Qualcomm reference-app reproduction

| Field | Evidence |
| --- | --- |
| Repository | `qualcomm/ai-hub-apps` |
| Accepted commit | `db3f9772d4e423dee2df517335009c703845dba8` |
| App | `geniex_chat_android` |
| Application ID | `com.geniex.demo` |
| GenieX AAR | `com.qualcomm.qti:geniex-android:0.3.5` |
| AGP | `8.13.0` |
| Kotlin | `2.2.0` |
| Java | 17; Qualcomm build tooling pins `17.0.16-ms` |
| Gradle | `9.1.0` from the reference app's `scripts/versions.env` |
| compileSdk / targetSdk | `34 / 34` |
| minSdk | `31` |
| NDK | `27.3.13750724` |
| JNI packaging | `jniLibs.useLegacyPackaging = true` |
| ABI relevant to target | `arm64-v8a` |
| Unchanged dependency resolution | **PENDING B0 BUILD** |
| Unchanged reference build | **PENDING B0 BUILD** |
| APK path | expected `geniex_chat_android/build/outputs/apk/debug/app-debug.apk`; **PENDING** |
| APK size | **PENDING** |

### Reproduction note

The execution environment used for repository work has neither outbound Git/network access nor an Android SDK, so an agent-side Android compile could not be truthfully performed. Qualcomm's exact source/configuration was inspected through GitHub instead. B0 therefore remains pending until the unchanged source is compiled in Kian's Android build environment. This is an environment limitation, not a reference-app failure.

Qualcomm's own `build.sh` at the accepted commit is Docker-only and builds by running `gradle assembleDebug assembleAndroidTest` inside a pinned Ubuntu toolchain. The source project itself contains no Gradle wrapper; its toolchain script pins Gradle `9.1.0`.

## B1 — physical reference-app gate

**Overall:** **PENDING**.

Required evidence:

| Acceptance item | Result | Evidence / notes |
| --- | --- | --- |
| APK installs | **PENDING** | |
| App launches | **PENDING** | |
| No immediate `UnsatisfiedLinkError` / `dlopen` / ABI crash | **PENDING** | |
| GenieX SDK initialisation path reached | **PENDING** | |
| ModelManager JNI path reached without downloading a model | **PENDING** | |
| Reference UI remains usable | **PENDING** | |
| Android build recorded | **PENDING** | |
| Device page size recorded | **PENDING** | |

### Least-expensive B1 runtime action

The accepted reference source calls `GenieXSdk.getInstance().init(...)` from `MainActivity.onCreate()`. To additionally exercise the ModelManager/JNI path without downloading a multi-gigabyte model, select a catalog item and press **Load** before downloading it. The reference source then calls `ModelManagerWrapper.getPaths(...)`; the expected non-model result is the UI message that the model is not downloaded / should be downloaded first. Do **not** start a model download for B1.

Capture logcat around launch and that Load action. A native/linker/runtime error is a B1 failure and must be preserved verbatim.

## B2 — standalone harness

**Status:** NOT STARTED. B1 must pass first.

| Field | Result |
| --- | --- |
| Harness path | `experiments/lab2b-vlm-harness/` — **NOT CREATED** |
| Package | planned `dev.kian.lab2b.vlm` |
| App label | planned `LAB-2B VLM Harness` |
| Frozen AAR/toolchain | pending B1 |

## B3 — native / 16 KB validation

**Status:** NOT STARTED.

| Item | Result |
| --- | --- |
| AAR native `.so` inventory | **PENDING** |
| APK native `.so` inventory | **PENDING** |
| ABI inventory | **PENDING** |
| ELF LOAD alignment | **PENDING** |
| `zipalign -c -P 16 -v 4` | **PENDING** |
| Actual S25 page size | **PENDING B1 PHYSICAL RESULT** |

## B4 — model bundle

**Status:** NOT STARTED.

| Field | Result |
| --- | --- |
| Model | Qwen3-VL-2B-Instruct |
| Repository | `unsloth/Qwen3-VL-2B-Instruct-GGUF` |
| Quantisation | `Q4_0` |
| Main GGUF filename | **PENDING EXACT PAIR VERIFICATION** |
| Main bytes | **PENDING** |
| Main SHA-256 | **PENDING** |
| mmproj filename | **PENDING EXACT PAIR VERIFICATION** |
| mmproj bytes | **PENDING** |
| mmproj SHA-256 | **PENDING** |
| Import method | planned GenieX `HubSource.LOCALFS` |
| `ModelPaths.model_path` | **PENDING** |
| `ModelPaths.mmproj_path` | **PENDING** |
| resolved `runtime_id` | **PENDING** |

## B5 — one-image NPU proof

**Status:** NOT STARTED.

| Acceptance item | Result |
| --- | --- |
| Requested compute unit | planned explicit `npu` |
| One image selected | **PENDING** |
| One short text prompt | **PENDING** |
| Sensible multimodal response | **PENDING** |
| Actual backend evidence | **PENDING** |
| NPU verdict | **PENDING** |

## B6 — repeat / lifecycle

| Test | Result |
| --- | --- |
| Second serial inference | **PENDING** |
| Stop active stream | **PENDING** |
| Recovery after stop | **PENDING** |
| `stopStream()` → `destroy()` unload | **PENDING** |
| Reload | **PENDING** |
| Force-close / relaunch | **PENDING** |
| Background / foreground | **PENDING** |
| Activity recreation / rotation | **PENDING** |

## B7 — controlled fallback

Fallback is not exercised unless needed to classify an NPU failure.

| Compute unit | Result |
| --- | --- |
| GPU | **NOT EXERCISED** |
| CPU | **NOT EXERCISED** |

## B8 — light profiling

Profiling is forbidden until B5/B6 reliability gates pass.

| Metric | Result |
| --- | --- |
| Harness APK size | **PENDING** |
| Source model bundle bytes | **PENDING** |
| GenieX managed cache bytes | **PENDING** |
| Temporary staging bytes | **PENDING** |
| Cold load time | **PENDING** |
| Warm/reload time | **PENDING** |
| Time to first output/token | **PENDING** |
| Total generation time | **PENDING** |
| Throughput | **PENDING** |
| Memory before load | **PENDING** |
| Loaded / peak memory | **PENDING** |
| Memory after destroy | **PENDING** |

## Overall LAB-2B verdict

**IN PROGRESS — B0/B1 PHYSICAL REFERENCE APP GATE PENDING.**

LAB-2C has **not** started.
