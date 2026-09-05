# LAB-2B B3 GenieX release comparison

> **Status:** B3 REVISE. B4 remains blocked while the current stable Android AAR is checked.
>
> This document records release-comparison evidence only. It does not authorise a runtime/model/framework change or LAB-2C.

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

Therefore `0.3.19` is **not** a valid one-variable upgrade that clears B3.

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

Qualcomm also publishes a separate CPU-only Android AAR, but that cannot satisfy the frozen NPU/HTP proof and is not substituted into the primary route.

The earlier statement that `0.3.19` was the latest public release is therefore obsolete and corrected here.

## Current decision

Do **not** reject GenieX solely from the `0.3.19` result because `v0.6.1` is a newer stable Android artefact and post-dates later SM8750/HTP work in Qualcomm's repository.

The only justified next B3 action is a static 16 KB check of the standard `v0.6.1` Android AAR. The harness dependency remains `0.3.5`; no version change is accepted until that comparison passes and a controlled revision is reviewed.

Required command after pulling the updated helper:

```text
./tools/compare_geniex_release_16k.sh 0.6.1
```

The helper verifies the published `v0.6.1` digest above before inspecting ELF LOAD alignment.

## Gate

```text
B3 = REVISE
B4 = BLOCKED
LAB-2C = NOT STARTED
```
