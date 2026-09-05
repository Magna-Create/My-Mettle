# LAB-2B B3 GenieX release comparison

> **Status:** NPU route rejected for LAB-2B production readiness. B3 remains REVISE while a non-NPU continuation is reviewed. B4 remains blocked.
>
> This document records release-comparison evidence only. It does not authorise LAB-2C.

## Why this comparison exists

The frozen LAB-2B starting route uses Qualcomm GenieX Android AAR `0.3.5`, because that is the exact dependency pinned by the accepted `geniex_chat_android` reference app revision.

The corrected B3 audit proved that `0.3.5` contains thirteen Qualcomm Hexagon shared objects whose ELF LOAD alignment is only `2**12` (4 KB), while APK ZIP alignment passes. The target Samsung Galaxy S25 Ultra currently reports a runtime page size of `4096` bytes, so the reference runtime can still initialise there, but that does not satisfy LAB-2B's production 16 KB portability gate.

The LAB-2B playbook explicitly states that an AAR which is not 16 KB-compatible may still pass target-device runtime proof, but the overall result must be `REVISE ROUTE / WAIT FOR FIX`, not production-ready.

## `v0.3.19` static comparison

Kian physically ran:

```text
./tools/compare_geniex_release_16k.sh 0.3.19
```

The downloaded release AAR matched Qualcomm's published digest:

```text
version: 0.3.19
SHA-256: 9bc409ff67ede99c1dcd7d9f732c13eb5e40eb71785795638ac539b32c26b3d8
SHA256_MATCH=PASS
SO_COUNT=51
```

The static check still found eleven Qualcomm Hexagon objects at `2**12`:

```text
libCalculator_skel.so
libQnnHtpV79.so
libQnnHtpV79Skel.so
libQnnHtpV81.so
libQnnHtpV81Skel.so
libQnnNetRunDirectV79Skel.so
libQnnNetRunDirectV81Skel.so
libggml-htp-v73.so
libggml-htp-v75.so
libggml-htp-v79.so
libggml-htp-v81.so
```

Result:

```text
GENIEX_RELEASE_16K=FAIL_ELF_ALIGNMENT
```

Therefore `0.3.19` is not a valid one-variable upgrade that clears B3.

## Correction: `0.3.19` is no longer the latest stable release

During the post-comparison source refresh on 2026-09-05, Qualcomm's live GitHub release feed showed a newer stable release:

```text
v0.6.1
published: 2026-09-03T22:41:53Z
```

Its standard Android AAR release asset is:

```text
geniex-android-aar-v0.6.1.aar
bytes: 81,890,454
SHA-256: 2dff6eac964556ba5b002fb935abc9bc22b42abaffe11368ed987d92b3c7619f
```

Qualcomm also publishes a separate CPU-only Android AAR:

```text
geniex-android-aar-cpu-v0.6.1.aar
bytes: 7,330,109
SHA-256: a454d2442997cd9146a34d5717ff88eb486b441d0e241f0e1302e1fdcea9b39a
```

Qualcomm's `v0.6.1` build presets show that the normal Snapdragon Android build enables `GENIEX_PLUGIN_LLAMA_CPP`, `GGML_HEXAGON`, `GGML_OPENCL` and `GENIEX_PLUGIN_QAIRT`, while the CPU-only build keeps `GENIEX_PLUGIN_LLAMA_CPP` but disables QAIRT, Hexagon and OpenCL and sets `GENIEX_CPU_ONLY=ON`.

## `v0.6.1` static comparison

Kian physically ran:

```text
./tools/compare_geniex_release_16k.sh 0.6.1
```

The downloaded standard Android AAR matched Qualcomm's published digest:

```text
version: 0.6.1
SHA-256: 2dff6eac964556ba5b002fb935abc9bc22b42abaffe11368ed987d92b3c7619f
SHA256_MATCH=PASS
SO_COUNT=51
```

The current stable standard AAR still contains the same eleven relevant Qualcomm Hexagon objects at `2**12`:

```text
libCalculator_skel.so
libQnnHtpV79.so
libQnnHtpV79Skel.so
libQnnHtpV81.so
libQnnHtpV81Skel.so
libQnnNetRunDirectV79Skel.so
libQnnNetRunDirectV81Skel.so
libggml-htp-v73.so
libggml-htp-v75.so
libggml-htp-v79.so
libggml-htp-v81.so
```

Result:

```text
GENIEX_RELEASE_16K=FAIL_ELF_ALIGNMENT
```

This is the final NPU-release comparison for LAB-2B. The accepted reference AAR, the later `0.3.19` release, and the current stable `0.6.1` standard Android AAR all fail the required native 16 KB gate for the HTP/Hexagon payload class.

## Decision

The GenieX `llama_cpp` NPU/HTP route is **rejected for LAB-2B production readiness as currently shipped**. This is not a claim that NPU execution cannot work on the target S25 Ultra: the phone currently runs 4 KB pages and the Qualcomm reference runtime initialises there. It is a deployment-compatibility rejection because the shipped Android AAR cannot satisfy the programme's 16 KB native requirement.

Do not spend further LAB-2B time cycling standard GenieX NPU releases unless Qualcomm publishes a materially new Android artefact or explicit upstream fix that changes this evidence.

This result does not reject local VLM inference or GenieX `llama_cpp` as a whole. A non-NPU continuation may be reviewed as a controlled route revision. The lowest-risk current candidate is Qualcomm's `v0.6.1` CPU-only Android AAR because its published build configuration removes QAIRT, Hexagon and OpenCL while retaining `llama_cpp`; it must still pass its own static and physical acceptance before B4 can resume.

The standard AAR is not promoted to GPU or CPU production use merely by changing `compute_unit`, because the failing Hexagon payloads remain packaged in that artefact.

## Gate

```text
NPU route = REJECTED FOR LAB-2B PRODUCTION READINESS
B3 = REVISE ROUTE
B4 = BLOCKED PENDING NON-NPU ROUTE REVIEW / STATIC ACCEPTANCE
LAB-2C = NOT STARTED
```
