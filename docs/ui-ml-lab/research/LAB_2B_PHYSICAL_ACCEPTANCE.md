# LAB-2B physical acceptance

> **Status:** LAB-2B IN PROGRESS — B1 PASS by user physical attestation; B2 harness source prepared; B3 static/native/page-size gate pending.
>
> **Rule:** a requested accelerator is never recorded as a proven accelerator without physical runtime evidence. Build success is not inference success.

## Target device

| Field | Evidence |
| --- | --- |
| Device | Samsung Galaxy S25 Ultra |
| SoC | Snapdragon 8 Elite / SM8750 class |
| Android build | gate passed; exact value not transcribed in chat |
| Device page size | **PENDING TRANSCRIPTION / B3** |
| Official page-size command | `adb shell getconf PAGE_SIZE` |
| Direct-on-device Termux equivalent | `getconf PAGE_SIZE` |

Android's current 16 KB guidance uses `adb shell getconf PAGE_SIZE`; `16384` denotes a 16 KB environment. The exact S25 Ultra value remains unrecorded until supplied; it is not inferred from device model.

## B0 — Qualcomm reference-app reproduction

| Field | Evidence |
| --- | --- |
| Repository | `qualcomm/ai-hub-apps` |
| Accepted commit | `db3f9772d4e423dee2df517335009c703845dba8` |
| App | `geniex_chat_android` |
| Application ID | `com.geniex.demo` |
| GenieX AAR | `com.qualcomm.qti:geniex-android:0.3.5` |
| GenieX 0.3.5 source snapshot used for API audit | `da7f27d7f1c6b052153eaa9d59e8aa872c6265a6` |
| AGP | `8.13.0` |
| Kotlin | `2.2.0` |
| Java | 17; Qualcomm tooling pins `17.0.16-ms` |
| Gradle | `9.1.0` |
| compileSdk / targetSdk | `34 / 34` |
| minSdk | `31` |
| NDK | `27.3.13750724` |
| JNI packaging | `jniLibs.useLegacyPackaging = true` |
| ABI relevant to target | `arm64-v8a` |
| Unchanged dependency resolution/build | **PASS — user attested B0/B1 gate passed** |
| APK path used | reference debug APK; exact physical path/hash not transcribed |
| APK size | **NOT TRANSCRIBED** |

The repository-operation sandbox could not perform the Android build itself because it lacks an Android SDK/network. Kian executed the unchanged reference gate externally and reported **“Gate passed.”** That attestation is recorded as the B0/B1 result; unavailable raw outputs are left unknown rather than invented.

## B1 — physical reference-app gate

**Overall: PASS — user-attested on the target S25 Ultra.**

The prior gate instructions required the following sequence; Kian reported that the gate passed before authorising continuation:

| Acceptance item | Result | Evidence / notes |
| --- | --- | --- |
| APK installs | **PASS** | user gate attestation |
| App launches | **PASS** | user gate attestation |
| No immediate `UnsatisfiedLinkError` / `dlopen` / ABI crash | **PASS** | user gate attestation |
| GenieX SDK initialisation path reached | **PASS** | reference app initialises SDK on launch; gate attested |
| ModelManager JNI path reached without model download | **PASS** | prior gate required Load-before-download path; gate attested |
| Reference UI remains usable | **PASS** | user gate attestation |
| Force-close / relaunch remains usable | **PASS** | part of prior physical gate; gate attested |
| Android build/fingerprint | **NOT TRANSCRIBED** | do not invent |
| Device page size | **NOT TRANSCRIBED** | required again at B3 |

No multi-gigabyte model download was required for B1.

## B2 — standalone harness

**Status: SOURCE PREPARED; BUILD/STATIC ACCEPTANCE PENDING B3.**

| Field | Result |
| --- | --- |
| Harness path | `experiments/lab2b-vlm-harness/` |
| Package / application ID | `dev.kian.lab2b.vlm` |
| App label | `LAB-2B VLM Harness` |
| GenieX AAR | `0.3.5` |
| AGP / Kotlin / Gradle / Java | `8.13.0 / 2.2.0 / 9.1.0 / 17` |
| compileSdk / targetSdk / minSdk | `34 / 34 / 31` |
| ABI | `arm64-v8a` only |
| JNI packaging | legacy packaging, matching reference |
| INTERNET permission | absent |
| My Mettle dependency | none |
| Runtime owner | one process-scoped `HarnessRuntimeOwner` |
| Inference concurrency | one active request; concurrent generation rejected |
| Stop/unload | explicit `stopStream()` then `destroy()` |
| Model import | LOCALFS only; model files external to git |
| Image input | one system-picked image copied to app-private storage |
| Vision geometry | read from selected mmproj; no magic fallback |
| Requested backend | explicit `npu` |
| Backend proof | deliberately `UNPROVEN` until B5 logs |

## B3 — native / 16 KB validation

**Status: PENDING PHYSICAL/BUILD-ENVIRONMENT EXECUTION.**

The harness includes `tools/inspect_native_16k.sh`, which inventories the exact cached 0.3.5 AAR and built APK, verifies APK ABI isolation, checks each native library's ELF LOAD alignment using `llvm-objdump`, and executes Android's current APK command:

```text
zipalign -v -c -P 16 4 app-debug.apk
```

| Item | Result |
| --- | --- |
| Harness compile | **PENDING** |
| Harness unit tests | **PENDING** |
| Harness lint | **PENDING** |
| AAR native `.so` inventory | **PENDING** |
| APK native `.so` inventory | **PENDING** |
| APK ABI inventory | **PENDING** |
| ELF LOAD alignment (`>= 2**14`) | **PENDING** |
| APK `zipalign -P 16` | **PENDING** |
| Actual S25 page size | **PENDING** |

B4 must not begin until this gate is reviewed.

## B4 — exact model bundle

**Status: NOT STARTED.**

| Field | Result |
| --- | --- |
| Model | Qwen3-VL-2B-Instruct |
| Repository | `unsloth/Qwen3-VL-2B-Instruct-GGUF` |
| Quantisation | `Q4_0` |
| Main GGUF filename | **PENDING B4 EXACT PAIR VERIFICATION** |
| Main bytes / SHA-256 | **PENDING** |
| mmproj filename | **PENDING B4 EXACT PAIR VERIFICATION** |
| mmproj bytes / SHA-256 | **PENDING** |
| Import method | planned GenieX `HubSource.LOCALFS` |
| `ModelPaths` / runtime_id | **PENDING** |

## B5 — one-image NPU proof

**Status: NOT STARTED.**

| Acceptance item | Result |
| --- | --- |
| Requested compute unit | planned explicit `npu` |
| One image + one prompt | **PENDING** |
| Sensible multimodal response | **PENDING** |
| Actual backend evidence | **PENDING** |
| NPU verdict | **PENDING** |

## B6 — repeat / lifecycle

| Test | Result |
| --- | --- |
| Second serial inference | **PENDING** |
| Stop + recovery | **PENDING** |
| `stopStream()` → `destroy()` | **PENDING** |
| Reload | **PENDING** |
| Force-close / relaunch | **PENDING** |
| Background / foreground | **PENDING** |
| Activity recreation / rotation | **PENDING** |

## B7 — controlled fallback

| Compute unit | Result |
| --- | --- |
| GPU | **NOT EXERCISED** |
| CPU | **NOT EXERCISED** |

Fallback is not exercised unless needed to classify an NPU failure.

## B8 — light profiling

Profiling remains blocked until B5/B6 reliability passes.

| Metric | Result |
| --- | --- |
| Harness APK size | **PENDING** |
| Source bundle / managed cache / staging bytes | **PENDING** |
| Cold/warm load | **PENDING** |
| TTFT / total generation | **PENDING** |
| Throughput | **PENDING** |
| Memory before/loaded/after destroy | **PENDING** |

## Overall LAB-2B verdict

**IN PROGRESS — B1 PASS; B2 SOURCE PREPARED; B3 STATIC/NATIVE/PAGE-SIZE GATE PENDING.**

LAB-2C has **not** started.
