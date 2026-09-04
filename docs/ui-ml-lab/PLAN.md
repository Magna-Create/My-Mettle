# UI/ML Lab programme plan

> **Status:** phase and gate contract for `agent/ui-ml-lab`.
>
> Do not advance past a STOP condition by assumption. Refresh source truth at each future gate.

## LAB-0 — Parallel Build Isolation, Governance & Integration Structure

**Purpose:** create a separately installable Lab variant and the governance needed for a long-lived parallel branch.

**Entry condition:** branch from a coherent live `agent/n-bio-vnext-inference` checkpoint and record its exact SHA.

**Major deliverables:** separate Android application identity/label; compile-time Lab identity; sandbox/component-collision audit; governance docs; seeded integration ledger; CI coverage for normal and Lab builds.

**STOP condition:** stop when both build lines are protected, Room/schema is unchanged, documentation is complete and the branch is pushed. Do not begin LAB-1 automatically.

**Must not pull forward:** AI runtime implementation, OCR/vision dependencies, equipment persistence, camera/equipment UX, workout/library redesign, or N-BIO behaviour changes.

## LAB-1 — AI Provider / Model Lifecycle Shell

**Purpose:** define a typed, replaceable prompt-provider boundary and lifecycle/capability shell without committing the product to a specific model/runtime.

**Entry condition:** LAB-0 is explicitly complete and its CI/build isolation remains green.

**Major deliverables:** typed provider interfaces; task-specific capability model; provider selection/lifecycle shell; explicit failure/unavailable states; test doubles/fixtures; boundaries for separately downloaded local assets.

**STOP condition:** stop when provider/lifecycle contracts can be exercised with fakes and no serious local-VLM integration has begun.

**Must not pull forward:** Qwen/Qualcomm/native-runtime integration, serious VLM benchmarking, equipment scanning, canonical equipment persistence, final AI product UX.

## LAB-2A — Local Multimodal AI Research Gate

**Purpose:** determine one credible Android local multimodal deployment route before My Mettle integration.

**Entry condition:** LAB-1 provider shell is stable enough to describe what the product actually needs.

**Major deliverables:** current research on candidate deployment routes; device/runtime compatibility evidence; one selected route; a known-good minimal standalone image+prompt sample; documented failure modes and fallback decision.

**Mandatory STOP for research:** do not begin serious Qualcomm/Qwen/local-VLM Android integration before this gate. The explicit goal is to avoid repeating previous native-runtime work characterised by benchmark hell, repeated rebuild failures, opaque native errors, uncertain runtime compatibility and integration without a known-good minimal sample.

The gate succeeds only after **one** credible deployment route proves a minimal standalone image+prompt path outside My Mettle.

**STOP condition:** stop after the route decision and standalone proof. Review the evidence before integrating it into My Mettle.

**Must not pull forward:** polished product integration, equipment vision workflow, canonical persistence, or multiple competing runtime stacks “just in case”.

## LAB-2B — Local VLM Runtime Spike

**Purpose:** integrate the LAB-2A-selected route behind the LAB-1 provider boundary as an experimental compatibility bridge.

**Entry condition:** LAB-2A has selected and independently proven one route.

**Major deliverables:** minimal Lab-only runtime spike; downloaded/private model-asset lifecycle as required; capability/failure telemetry suitable for development; provider-boundary tests; removal path documented.

**STOP condition:** stop when the spike can answer the selected test task reproducibly enough to evaluate. A spike passing does not make it the universal production backend.

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
