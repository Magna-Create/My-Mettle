# LAB-2B physical acceptance

**Cancellation/rejection lifecycle:** Stop or a failed turn unloads the runtime after the current native operation returns. Tap Load before another turn; model files remain installed. MNN clears multimodal embeddings in prefill, while `reset()` alone does not clear pending embeddings from a turn rejected/cancelled before prefill. Disposal prevents historical-image reuse on that path. Completed turns retain the loaded engine and reset conversation state.


## Current replacement-runtime acceptance — pending

Test the new MNN 3.6.1 CPU/GPU + bundled OCR APK using the harness README. Build/static validation is separate from actual phone acceptance. Do not mark LAB-2B COMPLETE from a build.

Record phone Android/build, PAGE_SIZE, model revision, requested/configured/effective text and vision backend, system preset/custom hash and actual system mode, pipeline, original/OCR/prepared hashes and dimensions, output, cold load/TTFT/generation timings and PSS.

- Launch; verify all three model choices, CPU default, GPU experimental, no NPU selector.
- Download Qwen3.5-2B; verify progress/failure/retry, hash activation, enough-storage rejection. Force-close during transfer, relaunch and recover. Once INSTALLED, force-close and relaunch; load without downloading again.
- Run built-in red-square and HELLO/1234 controls on CPU with the English grounded prompt, then ordinary photograph and equipment placard. Open the exact prepared input. Report only observed responses.
- Run bundled OCR immediately after APK install (no Play Services recognizer download); inspect text, blocks/lines, geometry, timing, source hash. Repeat image to confirm cache hit, change image to confirm invalidation; Clear OCR and automatic regeneration.
- Compare VISION ONLY, VISION + OCR and OCR ONLY. Confirm OCR ONLY has no final-model image. OCR is supplementary candidate evidence, not truth.
- Test NONE/ENGLISH GROUNDED/JSON TEST, custom UTF-8 txt/md, 64 KiB rejection, Clear. Inspect assembled prompt/system role. Reject over-budget inference explicitly.
- Stop during generation; ensure second Send/switch cannot race. Unload/reload, rotate/recreate, background/resume. Finish/stop generation, switch to a second installed model, then back; no deleted or duplicate weights. Observe memory after unload.
- Repeat control images with experimental GPU. Text requests OpenCL; vision stays CPU. The runtime cannot attest per-op GPU execution, and this is visible. On load error explicitly choose CPU. If visual correctness worsens, persist the failed-correctness annotation and use CPU.
- Test Gemma E2B and Qwen3-VL using their pinned assets. No model-specific physical PASS is currently recorded. Test on a 16 KB environment separately from the known S25's 4096-byte runtime.

### Historical Qualcomm control findings — do not transfer to MNN

Kian reported Qwen3.5-2B on the exact HELLO/1234 control: NPU-selected grossly misinterpreted blocks/logo; GPU-selected reported a blank image; CPU-selected recognized HELLO and nearly read 1234 as 1924. Standard GenieX AAR Hexagon objects also failed the mandatory 16 KB LOAD gate. This is why the accelerated Qualcomm route is closed. No corresponding conclusion about MNN has been made.

## Historical acceptance record (superseded route)


> **Status:** LAB-2B IN PROGRESS — B1 PASS by user physical attestation; B2 harness source prepared; B3 **REVISE** after corrected native/page-size audit.
>
> **Rule:** a requested accelerator is never recorded as a proven accelerator without physical runtime evidence. Build success is not inference success. A 4 KB current device runtime does not waive native 16 KB portability acceptance.

## Target device

| Field | Evidence |
| --- | --- |
| Device | Samsung Galaxy S25 Ultra |
| SoC | Snapdragon 8 Elite / SM8750 class |
| Android release | `16` — physically reported at B3 |
| Android SDK | `36` — physically reported at B3 |
| Android build ID | `BP4A.251205.006` — physically reported at B3 |
| Build fingerprint | `samsung/pa3qxeea/pa3q:16/BP4A.251205.006/S938BXXSBCZG3_OXMBCZG3:user/release-keys` |
| Device page size | `4096` bytes / 4 KB — physically reported at B3 |

The page-size value was obtained physically on the target phone. It is not inferred from device model or Android version.

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

The repository-operation sandbox could not perform the Android build itself because it lacks an Android SDK/network. Kian executed the unchanged reference gate externally and reported **“Gate passed.”** That attestation remains the B0/B1 result.

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

### Corrected physical audit

The corrected audit executed against the built harness APK and exact cached `geniex-android:0.3.5` dependency.

Artefact identities:

```text
AAR_SHA256=4a6ad5697bded1ce66ee3e691b2ce49fb2b7f5783db61b413b95b2222f1cb653
APK_SHA256=f845a43bd596ef657fa57178198a7be2387ec7a07019f46e56973d7295bcbcbc
APK_ABIS=arm64-v8a
```

The earlier audit parser bug was fixed in `a794e7e22db8b1fda2f840eeb59b9de39ea79993`; the corrected rerun no longer rejects valid `2**14` libraries.

The authoritative rerun identifies exactly 13 failing files in the AAR and the same 13 in the APK. Every failing file is:

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

This narrows the failure to Qualcomm Hexagon/HTP payloads rather than the general GenieX / llama.cpp Android host stack.

Critically, `libggml-htp-v81.so` belongs to the HTP lane relevant to the frozen primary route:

```text
GenieX llama_cpp
→ compute_unit = npu
→ Snapdragon HTP
```

The failure therefore cannot be dismissed as unused QAIRT-only baggage.

### APK ZIP alignment

The corrected audit reports:

```text
ZIP_ALIGNMENT=PASS
```

and final static result:

```text
LAB2B_NATIVE_16K=FAIL_ELF_ALIGNMENT
```

The APK keeps Qualcomm's `jniLibs.useLegacyPackaging = true`, so native libraries are compressed. ZIP packaging therefore passes, but it does not rebuild the 4 KB-aligned Hexagon ELF payloads.

### Device page-size interpretation

The target S25 Ultra physically reports:

```text
PAGE_SIZE=4096
```

So its current kernel/runtime is a 4 KB environment. The B3 finding therefore does **not** contradict the earlier B1 reference-app launch/init success on this phone.

However, the LAB-2B mission explicitly made 16 KB native compatibility a hard acceptance item. The correct current verdict is therefore:

```text
CURRENT TARGET DEVICE RUNTIME: 4 KB — PASS as observed device fact
GENIEX 0.3.5 APK ZIP 16 KB ALIGNMENT: PASS
GENIEX 0.3.5 NATIVE ELF 16 KB ALIGNMENT: FAIL
B3: REVISE
```

### Qualcomm failure archaeology

Qualcomm GenieX issue `#886` independently reports the same class of 4 KB alignment in bundled QNN / GGML-HTP files, including several exact names observed here. A Qualcomm maintainer acknowledged that an initial SDK-side update had not fully fixed the problem and said another fix would be needed. The issue was later closed stale rather than with a demonstrated 16 KB-clean artefact.

Detailed evidence is retained in:

```text
docs/ui-ml-lab/research/LAB_2B_B3_NATIVE_ALIGNMENT_FINDINGS.md
```

### Newer GenieX candidate

Qualcomm's latest public release observed during B3 review is `v0.3.19` (2026-08-07). Its Android source uses NDK `29.0.14206865`, compileSdk 35 and still uses legacy JNI packaging.

`0.3.19` remains a **static comparison candidate only**. It is not selected as the harness dependency. `tools/compare_geniex_release_16k.sh` performs the controlled comparison without modifying the project. Its executable bit was corrected after the first physical invocation returned `Permission denied`.

### B3 table

| Item | Result |
| --- | --- |
| Harness APK exists at expected build path | **PASS — physically audited** |
| Harness unit tests | **NOT TRANSCRIBED IN THIS B3 RESPONSE** |
| Harness lint | **NOT TRANSCRIBED IN THIS B3 RESPONSE** |
| APK ABI | **PASS — `arm64-v8a` only** |
| AAR SHA-256 | `4a6ad5697bded1ce66ee3e691b2ce49fb2b7f5783db61b413b95b2222f1cb653` |
| APK SHA-256 | `f845a43bd596ef657fa57178198a7be2387ec7a07019f46e56973d7295bcbcbc` |
| Correct interpretation of `2**14` | **PASS** |
| Genuine `< 2**14` ELF files | **FAIL — 13 Qualcomm Hexagon files at `2**12`** |
| APK `zipalign -P 16` | **PASS** |
| Actual S25 runtime page size | **4096 bytes / 4 KB** |
| Newer AAR comparison | **PENDING `v0.3.19` static audit** |
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

**IN PROGRESS — B1 PASS; B2 SOURCE PREPARED; B3 REVISE. B4 BLOCKED PENDING CONTROLLED NEWER-AAR COMPARISON.**

LAB-2C has **not** started.
