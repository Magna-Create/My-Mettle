# LAB-2B B3 native alignment findings

> **Status:** B3 REVISE. Corrected physical audit confirms the frozen GenieX `0.3.5` artefact is usable on the target phone's current 4 KB runtime but fails LAB-2B's required native 16 KB portability acceptance because 13 Qualcomm Hexagon payloads use `2**12` LOAD alignment. B4 remains blocked while a controlled newer-AAR static comparison is pending.
>
> This note records evidence only. It does not authorise a GenieX version change, My Mettle integration, model download/import, or LAB-2C.

## Physical environment

The corrected B3 audit was run from Termux on the target Samsung Galaxy S25 Ultra.

| Field | Reported value |
| --- | --- |
| Android release | `16` |
| Android SDK | `36` |
| Build ID | `BP4A.251205.006` |
| Build fingerprint | `samsung/pa3qxeea/pa3q:16/BP4A.251205.006/S938BXXSBCZG3_OXMBCZG3:user/release-keys` |
| Runtime page size | `4096` bytes / 4 KB |

The runtime page-size value was obtained physically from the device after `/system/bin/getconf` / Python fallback logic. It is not inferred from the device model.

## Artefacts under test

Frozen dependency:

```text
com.qualcomm.qti:geniex-android:0.3.5
```

AAR:

```text
/data/data/com.termux/files/home/.gradle/caches/modules-2/files-2.1/com.qualcomm.qti/geniex-android/0.3.5/a13f1a16d15c5e51bfd32a9408f9b4267d4ec526/geniex-android-0.3.5.aar
SHA-256 4a6ad5697bded1ce66ee3e691b2ce49fb2b7f5783db61b413b95b2222f1cb653
```

Harness APK:

```text
experiments/lab2b-vlm-harness/app/build/outputs/apk/debug/app-debug.apk
SHA-256 f845a43bd596ef657fa57178198a7be2387ec7a07019f46e56973d7295bcbcbc
```

The APK contains only:

```text
arm64-v8a
```

The app preserves Qualcomm's frozen reference setting:

```text
jniLibs.useLegacyPackaging = true
```

## Validator defect and correction

The first version of `tools/inspect_native_16k.sh` incorrectly reported valid `align 2**14` segments as failures because the Bash exponent extraction treated literal `*` characters as shell glob syntax.

The parser was corrected in commit:

```text
a794e7e22db8b1fda2f840eeb59b9de39ea79993
```

The corrected implementation parses the exponent with a regex and fails only when the parsed exponent is `< 14`. A later output cleanup reports the failing filename, ELF machine and minimum LOAD alignment without dumping every passing library.

The corrected physical rerun is authoritative for the `0.3.5` classification below.

## Corrected `0.3.5` result

The corrected audit reports exactly 13 failing files in the AAR and the same 13 files in the packaged APK. Every failing file is identified by `llvm-readelf` as:

```text
Machine: Qualcomm Hexagon
```

Each has minimum LOAD alignment:

```text
2**12
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

This is materially narrower than the original noisy output. The general GenieX / llama.cpp Android host libraries are not the problem: the corrected audit no longer rejects their valid `2**14` LOAD alignment.

### Primary-route significance

The failures are not irrelevant QAIRT-only baggage. The frozen primary route is:

```text
GenieX llama_cpp
→ compute_unit = npu
→ Snapdragon HTP
```

and the failing set includes:

```text
libggml-htp-v81.so
```

The HTP/GGML payload class is therefore directly relevant to the Snapdragon 8 Elite NPU route.

## APK ZIP alignment

The corrected physical audit reports:

```text
ZIP_ALIGNMENT=PASS
```

and final static verdict:

```text
LAB2B_NATIVE_16K=FAIL_ELF_ALIGNMENT
```

The native libraries are compressed in the APK, matching `jniLibs.useLegacyPackaging = true`. This means the APK ZIP packaging check passes, but it does not rebuild the 4 KB-aligned Qualcomm Hexagon ELF payloads.

## Device-runtime interpretation

The physical S25 Ultra currently reports:

```text
PAGE_SIZE=4096
```

Therefore this phone's current runtime does not itself require 16 KB ELF loading for the LAB-2B physical proof. That is consistent with the earlier Qualcomm reference app physically launching and initialising on this device.

However, LAB-2B explicitly made native 16 KB compatibility a hard acceptance item. The current 4 KB device runtime does not waive that portability requirement.

So the correct interpretation is:

```text
CURRENT TARGET DEVICE RUNTIME: 4 KB
GENIEX 0.3.5 CURRENT-DEVICE LOADABILITY: not contradicted by B3
GENIEX 0.3.5 NATIVE 16 KB PORTABILITY: FAIL
```

B3 therefore remains `REVISE`, not `PASS` and not a generic GenieX route rejection.

## Upstream corroboration

Qualcomm GenieX issue `#886`, **[Android] Support 16KB page size alignment for Android 15 compliance**, independently reports the same class of bundled QNN / GGML-HTP 4 KB-alignment problem.

The issue explicitly names several files also observed here, including:

```text
libCalculator_skel.so
libQnnHtpV79.so
libQnnHtpV79Skel.so
libQnnNetRunDirectV79Skel.so
libggml-htp-v73.so
libggml-htp-v75.so
libggml-htp-v79.so
libggml-htp-v81.so
```

A Qualcomm maintainer acknowledged that an initial SDK-side toolchain update had not fully fixed the bundled QNN/GGML-HTP set and said another fix was needed. The issue was later closed stale rather than with a demonstrated clean artefact.

Source:

```text
https://github.com/qualcomm/GenieX/issues/886
```

The physical `0.3.5` audit above is the stronger evidence for this LAB-2B pin; the issue is retained as corroborating failure archaeology.

## Current Android criterion

Current Android guidance distinguishes two checks:

1. native ELF LOAD alignment for relevant shared libraries, with `2**14` as the minimum 16 KB-compatible alignment;
2. APK ZIP alignment, checked separately with `zipalign -P 16`.

Compressed native packaging can satisfy the APK packaging side in compatibility scenarios, but it does not change a precompiled ELF whose LOAD alignment is only `2**12`.

Reference:

```text
https://developer.android.com/guide/practices/page-sizes
```

## Newer GenieX comparison — research only

Qualcomm's latest public GitHub release observed during B3 review is `v0.3.19` (2026-08-07).

Its Android source uses:

```text
compileSdk = 35
ndkVersion = 29.0.14206865
jniLibs.useLegacyPackaging = true
```

A newer NDK can improve libraries rebuilt by GenieX, but the Android packaging source still copies vendor/HTP prebuilts into the AAR. No release note has yet been accepted as proof that the exact failing Hexagon payloads were rebuilt cleanly.

`tools/compare_geniex_release_16k.sh` therefore performs a **research-only** static comparison of Qualcomm's published `v0.3.19` AAR. It does not modify the harness dependency. The script's executable bit was corrected after the first invocation returned `Permission denied`.

## B3 decision

```text
B3 = REVISE
```

Established facts:

1. corrected `0.3.5` AAR/APK audit is valid;
2. APK ABI is `arm64-v8a` only;
3. APK ZIP alignment passes;
4. exactly 13 Qualcomm Hexagon payloads fail native 16 KB ELF alignment at `2**12`;
5. the set includes `libggml-htp-v81.so`, directly relevant to the primary NPU/HTP lane;
6. the target S25 Ultra currently runs a 4 KB (`4096`) page-size environment;
7. the current device can therefore remain a valid physical runtime target, but `0.3.5` cannot satisfy LAB-2B's separate 16 KB portability acceptance;
8. `v0.3.19` static comparison remains pending and is not an authorised dependency change.

### Gate

B4 exact model preparation remains **BLOCKED** pending the controlled newer-AAR comparison and resulting route decision.

LAB-2C remains **NOT STARTED**.
