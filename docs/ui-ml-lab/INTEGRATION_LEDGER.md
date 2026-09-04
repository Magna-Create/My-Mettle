# UI/ML Lab integration ledger

> **Status:** active governance ledger for intentionally loose Lab seams.
>
> Every entry keeps a stable ID. Update the existing entry when its implementation/owner changes; do not create a new ID merely because a phase advanced.

## Entry format

| Field | Meaning |
| --- | --- |
| ID | Stable reference used by future prompts/reviews. |
| Feature / Surface | User-facing or infrastructure seam. |
| Lab implementation | What the Lab is allowed to implement now/later. |
| Current data source | Actual source of state at the current Lab phase. |
| Future canonical owner | System expected to own authoritative behaviour/data. |
| Expected future input/contract | Information needed to connect the seam correctly. |
| Current intentional limitation | What is deliberately disconnected or simplified. |
| Connection gate | Phase/event that permits canonical integration. |
| Do-not-do notes | Shortcuts that would corrupt ownership or semantics. |
| Status | `PLANNED`, `MOCKED`, `EXPERIMENTAL`, `CONNECTED`, `DEFERRED` or `CLOSED`. |

## LAB-INT-001 — Suggested load

- **Feature / Surface:** workout exercise-card suggested load.
- **Lab implementation:** explicit `SuggestionProvider` boundary; static/mock fixture initially.
- **Current data source:** none in LAB-1; provider is planned, not implemented.
- **Future canonical owner:** N-BIO V8 session/programme resolver.
- **Expected future input/contract:** versioned prescription/suggestion output including usable load target/range, provenance/uncertainty presentation inputs and equipment feasibility where relevant.
- **Current intentional limitation:** Lab UI may later show fixture state before V8 exists.
- **Connection gate:** V8 integration after its research/product gate and an agreed provider contract.
- **Do-not-do notes:** do not calculate suggested load in Compose; do not persist a suggestion as user-entered performance.
- **Status:** `PLANNED`.

## LAB-INT-002 — Suggested reps

- **Feature / Surface:** workout exercise-card suggested repetitions.
- **Lab implementation:** same explicit suggestion-provider class as load; fixture-backed initially.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** N-BIO V8 session/programme resolver.
- **Expected future input/contract:** feasible rep target/range coupled to load/set prescription and current programme/session intent.
- **Current intentional limitation:** disconnected from biological inference until V8 contract exists.
- **Connection gate:** V8 integration gate.
- **Do-not-do notes:** no UI-side progression heuristic or “last week + 1 rep” pseudo-prescription.
- **Status:** `PLANNED`.

## LAB-INT-003 — Suggestion wording / strength

- **Feature / Surface:** wording such as `Aim for`, `Try`, `You could try`.
- **Lab implementation:** presentation policy boundary; fixtures may demonstrate states after LAB-3 design approval.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** later V8 uncertainty/usability presentation policy.
- **Expected future input/contract:** semantic support/uncertainty class or other validated presentation input, not an exposed raw posterior-width shortcut.
- **Current intentional limitation:** wording hierarchy is not behaviourally mapped yet.
- **Connection gate:** research/design of V8 uncertainty presentation plus LAB-3 UX confirmation.
- **Do-not-do notes:** **do not map posterior width directly to UX wording prematurely**; do not present numeric confidence as normal-user authority.
- **Status:** `PLANNED`.

## LAB-INT-004 — Previous comparable performance placeholder

- **Feature / Surface:** faded actual-input placeholder showing prior comparable performance.
- **Lab implementation:** simple/mock comparable-history provider as appropriate during LAB-3.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** canonical workout/history evidence semantics coordinated with N-BIO comparison rules.
- **Expected future input/contract:** `ExecutionProfileVersion`, side, equipment binding/calibration and comparable-evidence semantics; actual historical value remains distinct from current-session entry.
- **Current intentional limitation:** a fixture may stand in for comparable history before all canonical comparability inputs are connected.
- **Connection gate:** LAB-3 provider seam first; canonical connection after relevant equipment/history contracts exist.
- **Do-not-do notes:** “previous” means last **comparable** performance, not literally last week/session; never store placeholder text/value as current entered data.
- **Status:** `PLANNED`.

## LAB-INT-005 — Exercise equipment chip

- **Feature / Surface:** exercise-card equipment state/chip.
- **Lab implementation:** mock/local Lab equipment source later, after LAB-3 design approval.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** shared equipment contract with N-BIO-7F-facing equipment semantics.
- **Expected future input/contract:** equipment model/instance/calibration/binding identity and user-facing label with provenance/selection semantics.
- **Current intentional limitation:** chip can communicate visible state before canonical persistence exists.
- **Connection gate:** LAB-5 shared equipment contract plus relevant N-BIO-7F alignment.
- **Do-not-do notes:** visible state does not imply the chip is tappable; do not make equipment an untracked string attached to an exercise.
- **Status:** `PLANNED`.

## LAB-INT-006 — Exercise Switch → Equipment

- **Feature / Surface:** `Switch` interaction entering equipment choice/path.
- **Lab implementation:** interaction shell only after LAB-3 design confirmation; fixture options are acceptable.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** shared equipment instance/binding selection semantics.
- **Expected future input/contract:** session-only binding versus persistent/default equipment choice and available equipment instances.
- **Current intentional limitation:** final binding persistence is absent before LAB-5.
- **Connection gate:** LAB-3 for UI; LAB-5 for canonical persistence.
- **Do-not-do notes:** do not infer a persistent equipment preference from a session-only switch.
- **Status:** `PLANNED`.

## LAB-INT-007 — Add Machine

- **Feature / Surface:** Add Machine workflow.
- **Lab implementation:** Semi-auto/Auto workflows only in their explicit later phases.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** shared equipment domain contract.
- **Expected future input/contract:** `EquipmentModel`, `EquipmentInstance`, `EquipmentCalibrationVersion`, location/gym association where useful, session-only/default semantics and provenance.
- **Current intentional limitation:** no equipment persistence or capture workflow exists in LAB-1.
- **Connection gate:** LAB-5 data contract → LAB-6 validation → LAB-7 Semi-auto → LAB-8 Auto.
- **Do-not-do notes:** do not let OCR/VLM output silently become canonical; do not collapse “use today” and regular/default semantics.
- **Status:** `PLANNED`.

## LAB-INT-008 — Equipment-aware load translation

- **Feature / Surface:** translation of performance/suggestions across equipment contexts.
- **Lab implementation:** no biological/mechanical translation implementation in the Lab UI.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** N-BIO-7F.
- **Expected future input/contract:** equipment instance/model/calibration features, execution profile, direct-history strength, raw entered load and versioned modelling coordinate with uncertainty.
- **Current intentional limitation:** Lab may display equipment context without claiming universal resistance equivalence.
- **Connection gate:** N-BIO-7F equipment semantics plus LAB-5 shared contract.
- **Do-not-do notes:** do not convert machine metadata into universal resistance with UI/Kotlin heuristics absent validated N-BIO mechanics.
- **Status:** `PLANNED`.

## LAB-INT-009 — Adaptive workout changes

- **Feature / Surface:** mid-workout changes to load/reps/sets/order/swap/remaining session.
- **Lab implementation:** UI fixtures may demonstrate approved states after LAB-3; no adaptive decision engine in the Lab.
- **Current data source:** none in LAB-1.
- **Future canonical owner:** N-BIO V8.
- **Expected future input/contract:** programme intent, current session evidence, remaining target need, equipment feasibility, uncertainty and explicit user-control policy.
- **Current intentional limitation:** no behaviour-driving adaptation exists.
- **Connection gate:** mandatory N-BIO V8 research/product/UX gate and later integration.
- **Do-not-do notes:** context tags are not optimiser commands; do not silently implement high-impact changes.
- **Status:** `PLANNED`.

## LAB-INT-010 — AI prompt backend

- **Feature / Surface:** typed prompt/image reasoning used by future Lab features.
- **Lab implementation:** LAB-1 implements the Lab-owned provider identity/capability/lifecycle/resolver shell, a read-only ML Kit system-provider probe, coalesced Lab-only startup refresh, developer diagnostics, and a no-op local lifecycle. It deliberately does not define a generic free-form generation API.
- **Current data source:** system state comes from the pinned ML Kit Prompt API capability/readiness probe; local state is `NoOpLocalModelLifecycle` and reports no installed local provider.
- **Future canonical owner:** system Android/ML Kit Prompt API when the **specific task** is ready and capability-sufficient; a proven local compatibility provider otherwise. LAB-1 does not make a permanent local-runtime choice.
- **Expected future input/contract:** each future task declares `PromptTaskRequirements` and uses typed task-specific request/result contracts. Equipment extraction owns its later task requirements; the generic provider resolver must not hardcode equipment assumptions.
- **Current intentional limitation:** the system adapter probes readiness/capabilities but LAB-1 does not expose generic task execution. `IMAGE_INPUT`/`MULTI_IMAGE` remain unknown until verifiable for the actual task. No local runtime/model exists.
- **Connection gate:** LAB-2A research → LAB-2B standalone runtime proof → LAB-2C local-provider integration. Later equipment work declares/validates its task capability set at its own gate.
- **Do-not-do notes:** do not expose two competing normal-user AI experiences; do not bake model assets into the APK; do not infer capability from a model/marketing name; do not treat `UNKNOWN` as supported; do not pull local runtime work into LAB-1.
- **Status:** `EXPERIMENTAL`.

### Existing N-BIO Nano boundary

`NanoNoteInterpreter` and `ContextInterpretationCoordinator` remain independent production/N-BIO source truth. LAB-1 learns from their typed Structured Output and Prompt API status handling but does not route them through `LAB-INT-010`, change their fallback semantics, or alter context evidence. Any future consolidation requires an explicit integration decision after the provider shell has proven useful; it is not implied by LAB-1.

## LAB-INT-011 — Super-library contribution

- **Feature / Surface:** contribution of equipment knowledge to a future shared library/server.
- **Lab implementation:** contract/scaffold only when explicitly required; uploader remains disabled/deferred.
- **Current data source:** local app state only; no server.
- **Future canonical owner:** future shared service with explicit storage, consent, identity and retention contract.
- **Expected future input/contract:** contribution schema, consent/provenance, endpoint/configuration and one explicit capability/config flag.
- **Current intentional limitation:** server infrastructure does not exist in this programme.
- **Connection gate:** explicit future server/privacy/infrastructure gate.
- **Do-not-do notes:** disabled means zero uploads, zero background jobs and no feature-only network dependency; no placeholder endpoints or speculative cloud client.
- **Status:** `DEFERRED`.

## Ledger maintenance rule

When a seam becomes canonical, update its `Lab implementation`, `Current data source`, `Connection gate` and `Status`, and record the connecting commit/SHA in the relevant phase closure. Keep the original stable ID so historical prompts and design decisions remain traceable.
