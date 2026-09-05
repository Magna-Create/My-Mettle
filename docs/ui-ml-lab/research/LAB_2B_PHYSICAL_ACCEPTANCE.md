# LAB-2B physical acceptance

> **Status:** LAB-2B IN PROGRESS — B1 PASS by user physical attestation; B2 harness source prepared; B3 **REVISE** after first native-alignment audit.
>
> **Rule:** a requested accelerator is never recorded as a proven accelerator without physical runtime evidence. Build success is not inference success. Compatibility mode is not native 16 KB compliance.

## Target device

| Field | Evidence |
| --- | --- |
| Device | Samsung Galaxy S25 Ultra |
| SoC | Snapdragon 8 Elite / SM8750 class |
| Android release | `16` — physically reported at B3 |
| Android SDK | `36` — physically reported at B3 |
| Android build ID | `BP4A.251205.006` — physically reported at B3 |
| Build fingerprint | `samsung/pa3qxeea/pa3q:16/BP4A.251205.006/S938BXXSBCZG3_OXMBCZG3:user/release-keys` |
| Device page size | **PENDING** — Termux's own PATH did not contain `getconf` |
| Official page-size command | `adb shell getconf PAGE_SIZE` |
| Direct-device preferred command | `/system/bin/getconf PAGE_SIZE` when present |
| Direct-device fallback | Python `os.sysconf("SC_PAGE_SIZE")`, or install Termux `getconf` |

Android's current 16 KB guidance uses `getconf PAGE_SIZE`; `16384` denotes a 16 KB runtime environment. The missing Termux command is not interpreted as a value.

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

The repository-operation sandbox could not perform the Android build itself because it lacks an Android SDK/network. Kian executed the unchanged reference gate externally and reported **“Gate passed.”** That attestation is recorded as the B0/B1 result; unavailable raw values are left unknown rather than invented.

## B1 — physical reference-app gate

**Overall: PASS — user-attested on the target S25 Ultra.**

| Acceptance item | Result | Evidence / notes |
| --- | --- | --- |
| APK installs | **PASS** | user gate attestation |
| App launches | **PASS** | user gate attestation |
| No immediate `UnsatisfiedLinkError` / `dlopen` / ABI crash | **PASS** | user gate attestation |
| GenieX SDK initialisation path reached | **PASS** | reference app initialises SDK on launch; gate attested |
| ModelManager JNI path reached without model download | **PASS** | prior gate required Load-before-download path; gate attested |
| Reference UI remains usable | **PASS** | user gate attestation |
| Force-close / relaunch remains usable | **PASS** | part of prior physical gate; gate attested |

No multi-gigabyte model download was required for B1.

## B2 — standalone harness

**Status: SOURCE PREPARED. Root My Mettle CI remains green.**

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

The root Android CI run for `d999359ad8bb96e64e67950250f2e98e0f176719` completed **SUCCESS**, confirming LAB-2B did not break the normal My Mettle verification chain.

## B3 — native / 16 KB validation

**Status: REVISE. B4 remains blocked.**

### First physical audit

The first audit executed against the built harness APK and exact cached `geniex-android:0.3.5` dependency.

Two separate findings resulted.

#### 1. Validator bug — fixed

The initial checker incorrectly reported valid `align 2**14` LOAD segments as failures. The Bash expression used to extract the exponent treated `*` as shell glob syntax instead of parsing the literal `2**14` token.

That defect is corrected in:

```text
a794e7e22db8b1fda2f840eeb59b9de39ea79993
```

The tool now extracts the exponent with a regex and rejects only exponents `< 14`.

A follow-up output cleanup in:

```text
d650e807dc95c19c62855aa13e2435ff7d1d2546
```

makes the default audit compact and reports ELF machine identity for genuine failures. Set `LAB2B_VERBOSE_NATIVE=1` only when full LOAD lines are needed.

#### 2. Genuine upstream 4 KB-aligned native payloads

After removing the checker false positives conceptually, the user-supplied APK output still contains real `align 2**12` libraries.

The visible genuine set includes at least:

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

By contrast, the visible general GenieX / llama.cpp host libraries such as `libgeniex*`, `libgeniex_plugin_llama_cpp.so`, `libggml.so`, `libggml-cpu.so`, `libggml-opencl.so`, `libllama.so`, `libmtmd.so`, `libnpu_jni.so` and `libomp.so` reported `2**14`.

This narrows the real compatibility problem to the HTP/QNN/DSP-related payload class rather than the whole GenieX Android stack.

Critically, `libggml-htp-v81.so` belongs to the HTP lane relevant to the frozen primary route:

```text
GenieX llama_cpp
→ compute_unit = npu
→ Snapdragon HTP
```

The finding therefore cannot be dismissed as an unused QAIRT-only library.

### APK ZIP alignment

The first `zipalign -P 16` verification succeeded.

The output showed native `.so` entries as compressed, which matches Qualcomm's frozen reference setting:

```text
jniLibs.useLegacyPackaging = true
```

This means APK ZIP packaging is not the active B3 failure. Android documents compressed JNI libraries as a packaging workaround; it does not convert a prebuilt ELF with `2**12` LOAD alignment into a native 16 KB-compatible ELF.

### Qualcomm failure archaeology

Qualcomm GenieX issue `#886` independently reports the same class of 4 KB alignment in bundled QNN / GGML-HTP files, including several exact names observed in this B3 run. A Qualcomm maintainer acknowledged that the first SDK-side update had not fully fixed the problem and said another fix would be needed. The issue was later closed as stale rather than with a demonstrated 16 KB-clean artefact.

See:

```text
https://github.com/qualcomm/GenieX/issues/886
```

Detailed B3 evidence is retained in:

```text
docs/ui-ml-lab/research/LAB_2B_B3_NATIVE_ALIGNMENT_FINDINGS.md
```

### Current Android rule

Android's current guidance requires each relevant 64-bit shared library's LOAD alignment to be at least `2**14`; `2**13`, `2**12`, or lower is not native 16 KB ELF alignment. APK ZIP alignment is checked separately.

Android 16 also has a compatibility mode for some 4 KB-aligned apps on a 16 KB kernel. LAB-2B does **not** reinterpret compatibility mode as a clean production pass.

### Newer GenieX candidate

Qualcomm's latest public release observed during B3 review is `v0.3.19` (2026-08-07). Its Android source uses NDK `29.0.14206865`, compileSdk 35 and still uses legacy JNI packaging.

A newer NDK is promising for libraries actually rebuilt by GenieX, but its Android build still copies vendor/HTP prebuilts into the AAR. No release note or closed issue currently proves that the exact 4 KB HTP files are fixed. `0.3.19` is therefore a **static comparison candidate only**, not a selected harness dependency.

### B3 table

| Item | Result |
| --- | --- |
| Harness APK exists at expected build path | **PASS — physically audited** |
| Harness unit tests | **NOT TRANSCRIBED IN THIS B3 RESPONSE** |
| Harness lint | **NOT TRANSCRIBED IN THIS B3 RESPONSE** |
| APK ABI | `arm64-v8a` audit path exercised |
| Original ELF checker | **INVALID / BUGGY for `2**14`** |
| Correct interpretation of `2**14` | **PASS** |
| Genuine `< 2**14` ELF files | **FAIL — HTP/QNN/GGML-HTP subset contains `2**12`** |
| APK `zipalign -P 16` | **PASS** |
| Actual S25 runtime page size | **PENDING** |
| B3 verdict | **REVISE** |

## B4 — exact model bundle

**Status: NOT STARTED / BLOCKED BY B3.**

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

**IN PROGRESS — B1 PASS; B2 SOURCE PREPARED; B3 REVISE. B4 BLOCKED.**

LAB-2C has **not** started.
