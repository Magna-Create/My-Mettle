# LAB-2B continuous implementation checkpoints

## Checkpoint 1 — 2026-09-05 03:20 UTC

- Starting/live Lab HEAD: `3f25ea228dfca24bc898a7c312015d2df0f3ab2d`.
- Live N-BIO: `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de`; no sync.
- Route: MNN 3.6.1 official Android CPU/OpenCL/Vulkan release, one runtime for all three exact publisher exports.
- Proven: published archive SHA matches; every ARM64 release library has LOAD alignment >= 16 KB; exact model revisions/asset digests acquired; production/source and binary safety checks pass.
- Implemented: typed registry, DownloadManager + verified atomic installation, one process owner, stateless prompt assembly, prompt files, OCR/cache, separate observable image preparation, developer controls, small JNI adapter and pure tests.
- Build: first Gradle run failed before source compilation because SDK installation nested its platform/build-tools contents incorrectly. Corrected local SDK layout; next compile is running. This is an environment setup failure, not an MNN implementation failure.
- Review: no product runtime, N-BIO, Room, equipment or LAB-2C changes; no weights/native binaries tracked. Removed obsolete GGUF parser and unused NPU layout; history preserves them. Existing lifecycle/image concepts reused.
- Next: resolve actual compile/lint findings, build final APK, inspect exact native closure/alignment, validate lifecycle and deliver installation handoff. Continue automatically.

## Checkpoint 2 — 2026-09-05 03:38 UTC

- Previous published HEAD: `bd1a6f8e235390a40a1937d9efc57791ef669ed9` (same tree as local checkpoint `0ab694e`; GitHub connector authored the published commit).
- Route: MNN 3.6.1 CPU / experimental OpenCL text + CPU vision; no runtime reassessment trigger occurred.
- Proven: actual Kotlin/JNI/dependency build, 18 unit tests with zero failures, lint zero errors; first APK 36,304,327 bytes. All 10 packaged ARM64 native libraries have LOAD alignment >=16 KB and APK zipalign passed. Packaged libc++ matches the stripped publisher STL, not an accidental NDK replacement.
- Failed: initial CI isolation step lacked ripgrep. Added explicit installation and a fail-closed prerequisite check. Earlier local SDK layout failure was environment setup, not a runtime failure.
- Changes reviewed: persistent GPU observation, no-op spinner guards, restart image cleanup, token budget, pure download-state tests, source-matched Termux native bundle and current README/plan/acceptance records. Source and binary safety pass; no product/N-BIO/model-weight files changed.
- Build state: fresh release-candidate build/Termux prebuilt-path validation in progress; CI will repeat complete gates after this checkpoint.
- Next: inspect final native bundle/APK, verify prebuilt build path, publish final APK/checksums and detailed morning handoff. Continue automatically; physical acceptance remains pending.

## Checkpoint 3 — 2026-09-05 03:41 UTC

- Previous published HEAD: `37025f4b96a1c23b1bf8f07779f99e73791833bc`.
- Route remains MNN 3.6.1; no engine compilation or model-conversion failure occurred.
- Fresh prebuilt-path validation: **BUILD SUCCESSFUL**, all 55 tasks executed (no CMake tasks); 18 tests, zero failures. This resolves the concrete risk that Termux packaging depended on leftover native build outputs. Phone-side Termux execution remains untested.
- Source review found MNN `Omni::embedding()` clears pending image embeddings after prefill; `Llm::reset()` does not clear them if a turn aborts before prefill. The harness now disposes the engine after every stopped/failed turn and tells the user to Load again. Successful turns keep the engine with stateless reset. Added a lifecycle policy regression test; final build underway.
- Anonymous HTTP range probes succeeded (206) for each pinned model's main weight file, with matching total byte sizes. This proves public access only, not inference or complete model hash verification.
- Source-isolation/binary checks pass. Continue to final artifact build/audit, CI result and handoff; physical acceptance pending, LAB-2C not started.

## Final validation / resumed handoff — 2026-09-05

- Implementation HEAD: `34fcadf8e25387f4afb534811124fbd6fd456081`; live N-BIO remains `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de`.
- Final dedicated Actions run `33942676654`: SUCCESS, 19 declared tests with testDebugUnitTest passing, assembleDebug, lintDebug, both safety checks, every packaged ELF and zipalign 16 KB gate passing. Artifact `lab2b-vlm-harness-debug`, ID `9962381669`; main APK 36,326,209 bytes, SHA-256 `b670336d205837e71d9c0633ec2029f7a0922fb7978792e14b9a9a4edaddb1a2`.
- Checkpoint 2 run `33942395327` also succeeded. The first failed run was missing ripgrep, corrected in checkpoint 2. No runtime link/model conversion rebuild loop occurred.
- Account interruption cleared scratch deliverables before attachment. Restored source from GitHub; final build and complete artifact remain available in Actions. The connector retrieved an artifact reference, but downloading that reference into the resumed workspace returned HTTP 403. Deliver through the successful Actions artifact; do not substitute an older local APK/checksum.
- Repeated local diff/source/binary safety checks on the restored final implementation pass. Only documentation/Termux copy instructions and final evidence records changed after the built implementation.
- Status: LAB-2B IMPLEMENTATION READY / PHYSICAL ACCEPTANCE PENDING. LAB-2C NOT STARTED.
