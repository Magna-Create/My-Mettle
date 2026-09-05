# UI/ML Lab programme plan

> **Status:** phase and gate contract for `agent/ui-ml-lab`.
>
> Do not advance past a STOP condition by assumption. Refresh source truth at each future gate.

## LAB-0 — Parallel Build Isolation, Governance & Integration Structure

**Status:** COMPLETE.

**Purpose:** create a separately installable Lab variant and the governance needed for a long-lived parallel branch.

**Entry condition:** branch from a coherent live `agent/n-bio-vnext-inference` checkpoint and record its exact SHA.

**Major deliverables:** separate Android application identity/label; compile-time Lab identity; sandbox/component-collision audit; governance docs; seeded integration ledger; CI coverage for normal and Lab builds.

**STOP condition:** satisfied. LAB-0 closed without beginning LAB-1 automatically.

**Must not pull forward:** AI runtime implementation, OCR/vision dependencies, equipment persistence, camera/equipment UX, workout/library redesign, or N-BIO behaviour changes.

## LAB-1 — AI Provider / Model Lifecycle Shell

**Status:** COMPLETE.

**Purpose:** define a typed, replaceable prompt-provider boundary and lifecycle/capability shell without committing the product to a specific local model/runtime.

**Entry condition:** satisfied. LAB-0 was complete/green. Pre-flight observed `agent/n-bio-vnext-inference` at `3652b8974f60baa4df389f458dd10f5591541f9c`; its three post-`SYNC-000` commits changed only Context Module documentation/examples/tests and a small CI documentation verification, so no N-BIO sync was required for LAB-1.

**Implemented deliverables:**

- Lab-owned `SYSTEM` / `LOCAL` provider identity;
- tri-state task capability model where `UNKNOWN` never satisfies a requirement;
- deterministic `AUTO` / `SYSTEM` / `LOCAL` provider resolver;
- distinct system `READY`, `SETUP_REQUIRED`, `SETUP_IN_PROGRESS`, `UNAVAILABLE` and `UNKNOWN` semantics mapped from the pinned ML Kit Prompt API;
- read-only ML Kit system probe using `checkStatus()`, Structured Output/system-prompt feature checks and optional base-model identity without calling `download()` or generation APIs;
- future local-model lifecycle/metadata contract plus a no-op `NOT_INSTALLED` implementation;
- task-specific system-over-local transition and local-retirement states;
- coalesced, non-blocking Lab-only process-start refresh behind one `BuildConfig.UI_ML_LAB` activation boundary;
- Lab developer diagnostics and ephemeral provider overrides;
- deterministic fake-driven resolver/lifecycle/concurrency tests;
- no persistence of transient provider state or developer override;
- no change to `NanoNoteInterpreter`, Context Interpretation fallback semantics, N-BIO behaviour or Room.

LAB-1 deliberately does **not** introduce a generic free-form generation API. Future typed tasks should define task-specific request/result contracts rather than normalising all AI work to arbitrary text/JSON.

**STOP condition:** satisfied when the implementation, diagnostics, tests, documentation and normal/Lab verification chain are green. LAB-1 stops here; it does not begin local-runtime research or integration.

**Must not pull forward:** Qwen/Qualcomm/native-runtime integration, serious VLM benchmarking, equipment scanning, canonical equipment persistence, final AI product UX.

## LAB-2A — Proven Android VLM Implementation Research Gate

**Status:** COMPLETE — HUMAN ACCEPTED. LAB-2B is authorised.

**Purpose:** learn from implementations that have actually succeeded before My Mettle attempts serious local multimodal integration. This is implementation archaeology and deployment-playbook authoring, not merely a hardware/API capability survey.

**Entry condition:** satisfied. LAB-1 is complete. At LAB-2A pre-flight, `agent/ui-ml-lab` was `5e3fa1752945ea7baba73f7477fbc43db5d489e4` and live `agent/n-bio-vnext-inference` was `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de`; no N-BIO sync was required. Physical LAB-1 probing on the target Samsung Galaxy S25 Ultra / Snapdragon 8 Elite found the pinned ML Kit GenAI Prompt API `1.0.0-beta4` system provider `UNAVAILABLE` on two successive process launches, so a local fallback is currently required for local AI on that device. Unknown/unverified system capability fields remain unknown rather than being reinterpreted as hardware non-support.

**Research priority:** start with proven Kotlin/Android implementations of `Qwen3-VL-2B-Instruct`, especially Qualcomm-optimised variants on Snapdragon-class hardware. If direct examples are sparse, expand carefully to closely related Qualcomm/QNN-optimised Qwen/VLM implementations whose build/runtime lessons transfer. Prefer evidence in this order:

1. working maintained app/repository or reproducible sample;
2. confirmed GitHub issue/discussion solution with successful follow-up;
3. Qualcomm/Google/runtime-vendor sample and official integration guidance;
4. developer forum/community reports with concrete working configuration;
5. speculative blog/forum advice only as leads, not as proof.

**Major deliverables:**

- a catalogue of real successful Android/Kotlin implementations and the exact stacks they used where discoverable;
- a failure-archaeology catalogue showing mistakes people repeatedly make and confirmed fixes/workarounds;
- practical best practices for stable model packaging, loading, lifecycle, threading, image input and backend use;
- one recommended deployment route for the minimal harness in LAB-2B;
- an implementation playbook detailed enough that LAB-2B follows a known recipe rather than improvising from SDK documentation.

**Research outcome:**

- primary route: `Qwen3-VL-2B-Instruct` → GGUF `Q4_0` → Qualcomm GenieX Android AAR → GenieX `llama_cpp` runtime → explicit Snapdragon 8 Elite NPU proof;
- immediate fallback policy: keep the same GenieX runtime/model and change only compute unit to GPU, then CPU if required; do not introduce a second runtime/model during initial diagnosis;
- exact Qwen3-VL-2B QAIRT Android support remains **NOT YET ESTABLISHED** and is not inferred from other Qwen3-VL bundle sizes or plugin classes;
- the strongest Kotlin baseline is Qualcomm's `geniex_chat_android` reference app at release commit `db3f9772d4e423dee2df517335009c703845dba8`, which pins GenieX Android AAR `0.3.5`; later official AARs exist, so LAB-2B must turn this documented drift into one physically proven version pin rather than using "latest everything";
- old `NexaAI/*-NPU` / `com.nexa.demo` Android paths are explicitly obsolete according to Qualcomm maintainers;
- model weights remain outside the APK; LAB-2B should begin with a pre-downloaded local GGUF bundle imported through GenieX `HubSource.LOCALFS`, not production download UX;
- the research found no contradiction requiring changes to the generic LAB-1 provider/lifecycle contract.

**Research artefacts:**

- `docs/ui-ml-lab/research/LAB_2A_ANDROID_VLM_IMPLEMENTATION_RESEARCH.md`;
- `docs/ui-ml-lab/research/LAB_2A_SOURCE_LEDGER.md`;
- `docs/ui-ml-lab/research/LAB_2A_FAILURE_ARCHAEOLOGY.md`;
- `docs/ui-ml-lab/research/LAB_2B_IMPLEMENTATION_PLAYBOOK.md`.

The research must seek hard evidence about the implementation details that commonly decide whether these integrations work, including where relevant:

- exact model artefact/quantisation and target hardware;
- Android runtime/API actually used by successful implementations;
- Snapdragon 8 Elite / Qualcomm backend applicability;
- NPU/GPU/CPU backend selection and how successful projects verify the backend actually used;
- AGP, Kotlin, NDK, CMake, ABI and native-library compatibility;
- `.so` packaging/loading and dependency/version alignment;
- model packaging/download/storage and large-file handling;
- mmap/asset-compression/file-access pitfalls;
- image preprocessing and multimodal prompt path;
- context/image-token memory behaviour;
- lifecycle, singleton/session ownership, warm-up, cancellation and concurrency;
- app background/resume behaviour and model reload/unload stability;
- RAM/storage/thermal considerations;
- R8/ProGuard and Gradle packaging collisions where relevant;
- firmware/driver/Android-version dependencies;
- redistribution/licensing constraints;
- known-good samples, commits, issue threads and forum shortcuts that prevent rebuilding failed approaches from ground zero.

**Failure archaeology is mandatory.** Specifically look for recurring classes of failure such as ABI mismatch, `UnsatisfiedLinkError`, linker/native dependency errors, wrong SDK/runtime/model pairing, wrong SoC/backend artefacts, missing DSP/NPU libraries, silent CPU fallback, model-file access failures, repeated lifecycle leaks/crashes, and benchmark-only setups that do not translate into a stable Kotlin app.

The final playbook should end with one concrete recommended route, conceptually documenting:

```text
Model artefact / quantisation
Runtime + exact relevant version
Target SoC/backend
Known-good Android/Kotlin reference
AGP/Kotlin/NDK/CMake requirements where applicable
Native artefacts / packaging rules
Model storage and load pattern
Image preprocessing/input path
Generation call shape
Lifecycle/threading pattern
Backend-verification method
Known traps + confirmed fixes
Fallback decision if the route fails
```

The completed research covers those required implementation dimensions in the report, source ledger, failure archaeology and playbook. Future LAB-2B failures should extend those artefacts rather than restarting the search from scratch.

**Mandatory STOP for research:** satisfied. No serious Qualcomm/Qwen/local-VLM Android integration was begun inside My Mettle during LAB-2A. The explicit goal remains to avoid repeating previous native-runtime work characterised by benchmark hell, repeated rebuild failures, opaque native errors, uncertain runtime compatibility and implementation without first understanding how successful projects avoided those traps.

**STOP condition:** satisfied for research and human review. The deployment playbook and primary route were explicitly accepted before LAB-2B began. LAB-2A did **not** modify My Mettle or prove the runtime itself; that proof belongs to LAB-2B.

**Must not pull forward:** My Mettle runtime integration, polished product integration, equipment vision workflow, canonical persistence, or multiple competing runtime stacks “just in case”.

## LAB-2B — Minimal Standalone VLM Harness

**Status:** IN PROGRESS — B1 PASS; B2 standalone harness prepared; B3 **REVISE** after corrected physical native/page-size validation. B4 is **BLOCKED** pending controlled newer-AAR comparison and route review.

**Purpose:** prove the LAB-2A-recommended route in the smallest possible Kotlin Android harness before My Mettle depends on it.

**Entry condition:** satisfied. LAB-2A was explicitly human-accepted. LAB-2B pre-flight observed `agent/ui-ml-lab` at `13fcff8e608e18e3ac4faa232d17c98da25750df` and live `agent/n-bio-vnext-inference` at `5727ea95cf692c8ea0145bdb4cc0ac5a4dc705de`; no N-BIO sync was required. B0 source inspection confirmed the accepted Qualcomm reference app pins GenieX AAR `0.3.5`, AGP `8.13.0`, Kotlin `2.2.0`, Java 17, NDK `27.3.13750724`, compile/target SDK 34, minSdk 31, legacy JNI packaging and Gradle `9.1.0`.

**Physical progress:** the unchanged Qualcomm `geniex_chat_android` reference app at `db3f9772d4e423dee2df517335009c703845dba8` passed the B1 physical launch/init gate on the target Samsung Galaxy S25 Ultra. The isolated harness now exists under `experiments/lab2b-vlm-harness/` and does not depend on My Mettle source.

**Current hard gate — B3 REVISE:** corrected physical native inspection of `geniex-android:0.3.5` and the harness APK established:

- target device Android 16 / API 36 / build `BP4A.251205.006`;
- actual target-device runtime page size `4096` bytes / 4 KB;
- APK ABI `arm64-v8a` only;
- APK `zipalign -P 16`: PASS;
- native ELF 16 KB acceptance: FAIL because exactly 13 `Machine: Qualcomm Hexagon` payloads have minimum LOAD alignment `2**12`;
- the failing set includes `libggml-htp-v81.so`, so the finding is directly relevant to the accepted llama.cpp NPU/HTP path rather than unused QAIRT-only baggage.

The current 4 KB device runtime means this finding does not contradict the already-passed reference-app launch/init gate, but LAB-2B explicitly made native 16 KB portability a hard acceptance item. Therefore B4 exact-model preparation must not begin while the selected `0.3.5` AAR remains unresolved against that requirement.

A controlled **research-only** static comparison against Qualcomm GenieX `v0.3.19` is permitted to determine whether Qualcomm rebuilt the affected Hexagon payloads. This comparison does not change the harness dependency. If a later AAR materially fixes the exact B3 failure, that is a candidate version revision for human review; do not silently substitute it.

**The harness must remain deliberately tiny.** It should prove the runtime, not the product. Conceptually:

```text
launch
→ load model
→ select/capture one image
→ submit image + text prompt
→ receive sensible response
→ close/reopen app
→ repeat reliably
```

No Room, N-BIO, OCR pipeline, equipment schema, equipment UX or My Mettle product integration belongs in this proof.

**Major deliverables:** reproducible image+prompt inference on the target device; verified model/backend path where possible; model load/reload behaviour; lifecycle/background/resume stability; cancellation/concurrency boundaries; storage/RAM/timing/thermal observations; documented deviations from the LAB-2A playbook; a clear PASS / REVISE ROUTE / REJECT ROUTE verdict.

If the recommended route fails, diagnose it against LAB-2A evidence. Do not convert the harness into an open-ended benchmark project or try many native stacks in parallel. Material route changes return to a short research/review decision before another harness attempt.

**STOP condition:** stop only when one route is stable enough on the target device to justify My Mettle integration, or when evidence says the chosen route should be rejected/researched again. Build success alone is not a physical runtime pass. B3's 16 KB failure must be resolved or explicitly reviewed before B4 resumes.

**Must not pull forward:** My Mettle provider integration, OCR/equipment semantics, canonical equipment persistence, polished UX or server contribution.

## LAB-2C — Local VLM Provider Integration

**Purpose:** integrate the LAB-2B-proven route behind the LAB-1 provider boundary as a replaceable local compatibility provider.

**Entry condition:** LAB-2B has a reproducible target-device PASS and its runtime/lifecycle constraints are documented.

**Major deliverables:** Lab-only local provider implementation; downloaded/app-private model-asset lifecycle as required; install/readiness/removal states; capability/failure diagnostics suitable for development; provider-boundary tests; clean removal path; confirmation that provider-specific details do not leak into product/UI contracts.

The local model remains a compatibility bridge unless a later explicit product decision changes that rule. A successful local model does not justify maintaining two competing normal-user AI experiences if the system provider later satisfies the task.

**STOP condition:** stop when the proven local runtime can satisfy the selected typed task through the LAB-1 provider boundary without pulling equipment-specific interpretation into the provider itself.

**Must not pull forward:** final equipment-recognition UX, server contribution, canonical equipment schema, or assumptions that local must remain preferred if a system provider later satisfies the task.

## LAB-3 — Workout / Exercise UI Overhaul

**Purpose:** implement the intended workout/exercise interaction model while keeping unavailable N-BIO outputs behind explicit mock/provider seams.

**Entry condition:** **STOP for Kian to provide/finalise the previously mocked workout/exercise UI flow before implementation.** Review the interaction states/gestures, preferably from the agreed Figma/source design.

**Major deliverables:** approved exercise-card/input/suggestion structure; compact Rate/Switch/Complete control; equipment-state presentation where approved; fixture/provider boundaries for disconnected state; accessibility/gesture behaviour defined and tested.

**STOP condition:** stop when the approved UI flow is implemented without pseudo-N-BIO logic and all unresolved backend seams are in the integration ledger.

**Must not pull forward:** invented suggestion algorithms, posterior-width→wording mapping, canonical equipment persistence, automatic adaptive-workout policy, or library IA work that belongs to LAB-4.

## LAB-4 — Unified Library UX / Information Architecture

**Purpose:** establish one coherent information architecture/interaction grammar across exercise, swap and future equipment/library surfaces.

**Entry condition:** **STOP for Kian to design/finalise the harmonised Library information architecture before implementation.** Existing library/swap screens are not presumed final.

**Major deliverables:** approved IA; shared search/filter/navigation grammar; exercise/equipment relationship model at the UI boundary; implementation of the approved Lab experience using non-canonical data where necessary.

**STOP condition:** stop when the harmonised library UX is implemented and unresolved persistence/ownership seams are recorded.

**Must not pull forward:** shared Room equipment entities/migrations, server super-library, or automatic equipment recognition.

## LAB-5 — Shared Equipment Data Contract + Cross-Branch Room Migration Gate

**Purpose:** introduce canonical equipment persistence once both development lines can agree one schema/domain contract.

**Entry condition:** **STOP for a cross-branch database/schema gate.** Refresh both live N-BIO and Lab heads before deciding any Room version or migration.

Required sequence:

1. stop Lab feature development;
2. refresh live N-BIO head;
3. refresh live Lab head;
4. determine the then-current Room version;
5. agree the shared equipment domain contract;
6. create the next legitimate Room migration;
7. keep that shared schema/domain change isolated from camera UI, Qwen/runtime code, Lab-only mocks and Figma UI work;
8. carry the **same** canonical database/domain contract across both development lines;
9. verify schema identity/compatibility;
10. resume Lab work only after the cross-branch database state is aligned.

Do **not** hardcode a future “Room15” assumption. The next version is whatever follows the live schema at this checkpoint.

**Major deliverables:** canonical equipment-domain contract; isolated migration; matching schema/domain state across both lines; compatibility verification.

**STOP condition:** stop until both branches are aligned on that contract and schema.

**Must not pull forward:** camera capture, OCR/VLM runtime implementation, Lab-only fixtures, polished Add Machine UI, or super-library networking.

## LAB-6 — Equipment Vision Lab

**Purpose:** validate the equipment-image understanding pipeline against real gym images before user-facing automation depends on it.

**Entry condition:** LAB-5 canonical contract is aligned and the selected vision/runtime route has enough evidence to run the experiment safely.

**Major deliverables:** real-image corpus/evaluation plan with appropriate consent; OCR observations; semantic candidate extraction; deterministic derivation; correction/unknown handling; benchmark/debug outputs kept separate from canonical Room unless explicitly promoted.

**STOP condition:** stop after the pipeline is evaluated on representative real images and limitations are understood. Semi-auto must not depend on an unvalidated pipeline.

**Must not pull forward:** silent canonicalisation of model output, automatic recognition UX, server upload, or deterministic equipment mechanics delegated to a VLM where Kotlin can own them.

## LAB-7 — Semi-Automatic Add Machine Workflow

**Purpose:** build a user-validated machine-capture workflow around the proven pipeline.

**Entry condition:** LAB-6 demonstrates a useful real-image path and UX states for the relevant resistance/loading systems are designed.

**Major deliverables:** whole-machine-first capture; background processing while later capture steps continue; loading-system-specific capture; editable candidate facts; explicit confirmation/correction; “use today” versus regular/default semantics where approved.

**STOP condition:** stop when Semi-auto is usable, correction-first and canonical save semantics are explicit.

**Must not pull forward:** fully automatic recognition, unreviewed persistent defaults, silent contribution to a shared server, or assumptions that one capture path fits every loading mechanism.

## LAB-8 — Automatic Equipment Recognition

**Purpose:** add a higher-automation path only after Semi-auto and the underlying vision contract are proven.

**Entry condition:** LAB-7 is stable, LAB-6 evidence supports safe automation, and recognition confidence/unknown/correction UX is explicitly designed.

**Major deliverables:** automatic candidate recognition; graceful unknown/fallback to Semi-auto; user validation before canonical truth; equipment binding/default integration through the shared contract.

**STOP condition:** stop when Auto adds measurable value without weakening correction, provenance or canonical-truth rules.

**Must not pull forward:** autonomous uploads, opaque canonical writes, or irreversible dependence on one experimental model/runtime.

## SUPER-LIBRARY — Deferred server integration

**Purpose:** reserve a contribution/consumption contract for a future shared equipment library without making current product work depend on infrastructure that does not exist.

**Entry condition:** real server/storage, privacy/consent, identity, retention and contribution contracts exist and are explicitly approved.

**Major deliverables:** contract/scaffold only until that condition is met. A future uploader/client must sit behind one explicit capability/config flag that defaults **DISABLED**.

Disabled means:

- zero uploads;
- zero background jobs;
- no server dependency;
- no network behaviour introduced solely for this feature.

**STOP condition:** no network implementation until real infrastructure and consent/storage policy exist.

**Must not pull forward:** `INTERNET` permission solely for this feature, HTTP clients, cloud SDKs, upload workers, placeholder server endpoints or speculative credentials/configuration.

## Cross-programme ownership reminders

- Suggested load/reps and adaptive workout changes ultimately belong to N-BIO V8/session-programme resolution, not UI heuristics.
- Equipment-aware load translation belongs to N-BIO-7F-facing semantics.
- Uncertainty wording is a later presentation policy. Do not mechanically map posterior width to `Aim for` / `Try` / `You could try` before that policy is researched and validated.
- Current Android/ML/platform facts must be rechecked at the phase where they drive implementation.