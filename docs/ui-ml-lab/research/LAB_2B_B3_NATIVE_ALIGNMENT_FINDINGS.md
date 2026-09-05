# LAB-2B B3 native alignment findings

> **Status:** B3 REVISE. The first 16 KB audit exposed one validator bug and one genuine upstream native-alignment constraint. B4 remains blocked.
>
> This note records evidence only. It does not authorise a GenieX version change, My Mettle integration, model download/import, or LAB-2C.

## Physical environment reported

The first B3 audit was run from Termux on the target Samsung Galaxy S25 Ultra.

| Field | Reported value |
| --- | --- |
| Android release | `16` |
| Android SDK | `36` |
| Build ID | `BP4A.251205.006` |
| Build fingerprint | `samsung/pa3qxeea/pa3q:16/BP4A.251205.006/S938BXXSBCZG3_OXMBCZG3:user/release-keys` |
| Runtime page size | **PENDING** — Termux did not have `getconf` on its own PATH |

The missing `getconf` command is not interpreted as a page-size result.

## Artefact under test

The audit inspected the standalone LAB-2B harness built against the frozen reference dependency:

```text
com.qualcomm.qti:geniex-android:0.3.5
```

The APK path reported by the tool was:

```text
experiments/lab2b-vlm-harness/app/build/outputs/apk/debug/app-debug.apk
```

The app preserves Qualcomm's reference `jniLibs.useLegacyPackaging = true`, so native libraries are compressed in the APK.

## Validator defect discovered

The first version of `tools/inspect_native_16k.sh` printed failures for both:

```text
align 2**14
align 2**12
```

That was wrong. Android's documented minimum is `2**14`; `2**14` is valid and only lower values such as `2**13` / `2**12` fail the ELF criterion.

The bug was in Bash extraction of the exponent:

```text
${token##*2**}
```

The `*` characters were interpreted as shell glob syntax, so valid tokens were not parsed as the numeric exponent `14` and were rejected.

The validator was fixed in commit:

```text
a794e7e22db8b1fda2f840eeb59b9de39ea79993
```

The corrected implementation captures the exponent with a Bash regex and fails only when the parsed exponent is `< 14`. It also reports ELF machine identity and emits a compact unaligned-file summary.

## Correct interpretation of the first APK output

The large majority of visible GenieX host/runtime libraries reported `align 2**14` and therefore meet the static ELF threshold. Examples include:

- `libgeniex.so`;
- `libgeniex_core.so`;
- `libgeniex_vlm.so`;
- `libgeniex_plugin_llama_cpp.so`;
- `libgeniex_plugin_qairt.so`;
- `libggml.so`;
- `libggml-base.so`;
- `libggml-cpu.so`;
- `libggml-hexagon.so`;
- `libggml-opencl.so`;
- `libllama.so`;
- `libllama-common.so`;
- `libmtmd.so`;
- `libnpu_jni.so`;
- `libomp.so`.

Those `2**14` entries were false positives from the original checker and are not treated as failures.

### Genuine `2**12` entries visible in the APK

The user-supplied APK audit contains genuine 4 KB LOAD alignment for at least the following files:

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

Each of these showed one or more LOAD segments with:

```text
align 2**12
```

This is a genuine failure against Android's native 16 KB ELF-alignment criterion.

The corrected rerun is still required because it will provide a compact complete list and ELF machine identity. The list above is evidence from the visible first APK output, not a claim about files that may have appeared outside the supplied excerpt.

## APK ZIP alignment

The first run's official `zipalign -P 16` check succeeded. The output showed the native libraries as compressed, matching the frozen Qualcomm reference setting:

```text
jniLibs.useLegacyPackaging = true
```

This means APK ZIP packaging is not the current failure.

Android documents compressed native libraries as a packaging workaround when uncompressed shared-library ZIP alignment is a problem. That workaround does **not** rebuild a precompiled `.so` whose ELF LOAD segments are only 4 KB aligned. ELF alignment and APK ZIP alignment are separate requirements.

## Upstream corroboration

Qualcomm GenieX issue `#886`, **[Android] Support 16KB page size alignment for Android 15 compliance**, independently reports the same class of problem in bundled Qualcomm QNN / GGML-HTP dependencies.

A later reporter explicitly listed 4 KB alignment in files including:

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

A Qualcomm maintainer subsequently said their initial SDK-side fix had not fully resolved the issue and that another fix would be provided. The issue was ultimately closed as stale on 2026-07-22 rather than with a demonstrated 16 KB-clean artefact.

Source:

```text
https://github.com/qualcomm/GenieX/issues/886
```

The LAB-2B physical audit is stronger evidence for `geniex-android:0.3.5` than the historical issue itself; the issue is retained as corroborating failure archaeology.

## Current Android criterion

Current Android guidance states that every shared library in the relevant 64-bit APK ABI should have ELF LOAD alignment of at least:

```text
2**14
```

`2**13`, `2**12`, and lower are not considered 16 KB ELF aligned. Android also requires checking APK ZIP alignment separately with `zipalign -P 16`.

Current guidance also notes Android 16's 16 KB compatibility mode can allow some 4 KB-aligned apps to run on a 16 KB kernel, but that mode is not equivalent to native 16 KB compliance and is not accepted here as proof of a production-clean runtime.

Reference:

```text
https://developer.android.com/guide/practices/page-sizes
```

## Primary-route significance

This finding does **not** show that all GenieX Android native code is 4 KB aligned. In the supplied output, the general GenieX / llama.cpp host libraries are predominantly 16 KB aligned.

The genuine failures cluster around HTP/QNN/DSP-related payloads. Critically, the frozen primary route requests:

```text
GenieX llama_cpp
→ compute_unit = npu
→ Snapdragon HTP
```

and the APK contains `libggml-htp-v81.so` at `2**12`.

Therefore the 16 KB finding is directly relevant to the intended NPU path and cannot be dismissed as an unused QAIRT-only dependency.

## Newer GenieX candidate — research only

As of this B3 review, Qualcomm's latest public GitHub release is `v0.3.19` (2026-08-07).

Its Android source uses:

```text
compileSdk = 35
ndkVersion = 29.0.14206865
jniLibs.useLegacyPackaging = true
```

The newer NDK is encouraging for libraries rebuilt by GenieX itself, but the source still copies vendor/HTP prebuilts into the AAR. No release note or resolved Qualcomm issue has yet been found that proves the exact HTP binaries are 16 KB clean.

Therefore `0.3.19` is only a **static comparison candidate**. The harness remains pinned to `0.3.5` until a controlled comparison demonstrates a material benefit and the route change is explicitly reviewed.

## B3 decision

```text
B3 = REVISE
```

Reasons:

1. the original checker must be rerun after its parser fix so the evidence set is clean;
2. the frozen `0.3.5` APK contains genuine `2**12` HTP/QNN/GGML-HTP libraries;
3. the target device's actual runtime page size is still unrecorded;
4. a newer GenieX AAR may or may not fix the upstream prebuilts and must be tested, not assumed.

### Gate

B4 exact model preparation remains **BLOCKED**.

LAB-2C remains **NOT STARTED**.
