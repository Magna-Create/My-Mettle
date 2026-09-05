# Third-party notice

## Current runtime

MNN 3.6.1, copyright Alibaba Group Holding Limited, Apache License 2.0.
Source: https://github.com/alibaba/MNN/tree/3.6.1
Full upstream licence: `third_party/MNN_APACHE2.txt`.
The small `mnn_bridge.cpp` uses the public Llm API and adapts the Android reference application's incremental generation/status-restoration lifecycle. The inference engine itself is the verified upstream prebuilt; it is not forked or built from source here.

Bundled OCR: Google ML Kit Text Recognition v2 Latin `16.0.1`, distributed through Google Maven under its published terms. AndroidX, Kotlin/coroutines, and Material dependencies retain their published licences. Model repositories and their licences are recorded in `model-registry.json`; all three pinned cards declare Apache-2.0. Google also identifies Gemma 4 as Apache-2.0: https://ai.google.dev/gemma/docs/gemma_4_license.

## Historical Qualcomm work

Earlier revisions adapted the GGUF projector parser and centre-crop approach from `qualcomm/ai-hub-apps` revision `db3f9772d4e423dee2df517335009c703845dba8`, copyright 2025–2026 Qualcomm Technologies, Inc. and/or subsidiaries, BSD 3-Clause. The parser and crop route have been removed; the historical licence remains in `third_party/QUALCOMM_AI_HUB_APPS_BSD3.txt` for provenance. GenieX and Qualcomm runtime binaries are not dependencies of this build.

No third-party native binaries or model weights are committed. Build tooling retrieves and SHA-verifies the pinned MNN release; the installed harness downloads and SHA-verifies selected model assets.
