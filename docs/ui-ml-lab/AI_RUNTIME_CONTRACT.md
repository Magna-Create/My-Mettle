# AI runtime contract

> **Status:** implemented LAB-1 provider, capability and lifecycle contract. No local model/runtime is integrated. LAB-2A remains the next research gate.

## Product rule

My Mettle has one AI-assisted product experience. Runtime selection is infrastructure:

```text
SYSTEM AI WHEN SUFFICIENT
LOCAL AI ONLY AS A COMPATIBILITY FALLBACK
ONE PRODUCT EXPERIENCE
```

Normal product code must not decide between concrete runtimes itself. It declares the capabilities required by a typed task and consumes Lab-owned resolution state. Provider-specific Android classes remain below the adapter boundary.

LAB-1 deliberately does not define a universal `String -> String` execution API. Future tasks that need structured data should expose typed task-specific requests/results rather than making arbitrary free-form JSON parsing the architecture.

The existing N-BIO `NanoNoteInterpreter` remains separate production source truth. LAB-1 does not route, wrap or alter its note interpretation or fallback behaviour.

## Provider identity

`PromptProviderId` currently contains:

- `SYSTEM` — Android system Prompt API / Gemini Nano path exposed through the pinned ML Kit Prompt API;
- `LOCAL` — future separately downloaded app-local compatibility provider.

These IDs describe provider ownership, not marketing model names. A reported base-model name is diagnostic metadata only and is never parsed to select a provider.

## Capability model

`PromptCapability` currently contains:

- `TEXT`;
- `IMAGE_INPUT`;
- `STRUCTURED_OUTPUT`;
- `SYSTEM_INSTRUCTIONS`;
- `MULTI_IMAGE`.

Each capability is tri-state through `PromptCapabilitySupport`:

- `SUPPORTED`;
- `UNSUPPORTED`;
- `UNKNOWN`.

Missing/unverified capability information maps to `UNKNOWN`. Only `SUPPORTED` satisfies a `PromptTaskRequirements` requirement. Therefore unknown support can never become optimistic success.

Tasks declare a non-empty required capability set. Provider selection is then based on that set rather than on whether a generic AI/model API happens to exist.

LAB-1 includes one diagnostics fixture requirement set: `TEXT + STRUCTURED_OUTPUT + SYSTEM_INSTRUCTIONS`. It is not an equipment-vision contract. Later equipment work must declare its own requirements at the appropriate phase.

## System provider probe

`MlKitSystemPromptProviderProbe` is a read-only adapter around the already-pinned `com.google.mlkit:genai-prompt:1.0.0-beta4` dependency.

It uses the current APIs already established by the N-BIO Nano implementation:

- `Generation.getClient()`;
- `GenerativeModel.checkStatus()`;
- `FeatureStatus.UNAVAILABLE`;
- `FeatureStatus.DOWNLOADABLE`;
- `FeatureStatus.DOWNLOADING`;
- `FeatureStatus.AVAILABLE`;
- `GenerativeModel.isStructuredOutputFeatureAvailable()`;
- `GenerativeModel.isSystemPromptAvailable()`;
- `GenerativeModel.getBaseModelName()`;
- `GenerativeModel.close()`.

The probe never calls `GenerativeModel.download()` and never calls generation APIs. It submits no user content and cannot initiate a local-model download.

### Platform-to-domain readiness mapping

| ML Kit state | Lab-owned availability | Meaning |
| --- | --- | --- |
| `AVAILABLE` | `READY` | system provider can be evaluated against task capabilities now |
| `DOWNLOADABLE` | `SETUP_REQUIRED` | system path is supported but official system-managed setup/download is still required |
| `DOWNLOADING` | `SETUP_IN_PROGRESS` | official system model setup/download is already in progress |
| `UNAVAILABLE` | `UNAVAILABLE` | system provider cannot currently service the task path |
| unexpected/failed probe | `UNKNOWN` | do not assume support |

`DOWNLOADABLE` is deliberately not treated as permanent incompatibility and does not automatically redirect the user toward a large app-local fallback. LAB-1 reports the system setup requirement but does not trigger that setup on process start.

When the provider is `READY`, `TEXT` is verified as `SUPPORTED`. Structured Output and system instructions are probed independently with their current feature-check methods. A feature-check exception maps that individual capability to `UNKNOWN` and is retained as non-sensitive diagnostic metadata.

The pinned Prompt API can accept image request parts, but LAB-1 found no equivalent per-device capability/readiness checker for image or multi-image input. `IMAGE_INPUT` and `MULTI_IMAGE` therefore remain `UNKNOWN` in the system snapshot rather than being guessed from API surface existence. A later task must verify what it actually requires.

`getBaseModelName()` is optional diagnostics. Failure to retrieve it does not affect readiness or provider selection.

## Provider readiness and failure state

`PromptProviderAvailability` supplies the common readiness vocabulary:

- `NOT_CHECKED`;
- `READY`;
- `SETUP_REQUIRED`;
- `SETUP_IN_PROGRESS`;
- `UNAVAILABLE`;
- `NOT_INSTALLED`;
- `INCOMPATIBLE`;
- `CORRUPT`;
- `REMOVING`;
- `FAILED`;
- `UNKNOWN`.

Provider failures are translated into Lab-owned `PromptProviderFailure` values with a deliberately small taxonomy:

- `UNSUPPORTED`;
- `CANCELLED`;
- `TEMPORARY_FAILURE`;
- `PERMANENT_FAILURE`;
- `UNKNOWN`.

Diagnostics may retain a non-sensitive error class/code. Runtime-specific exception objects do not escape into Compose/product logic.

No failure is converted into fake success.

## Local fallback lifecycle contract

LAB-1 defines `LocalModelLifecycle` with operations for:

- `probe()`;
- `install()`;
- `verify()`;
- `remove()`.

It also defines `LocalModelLifecycleState`:

- `NOT_INSTALLED`;
- `INSTALL_REQUIRED`;
- `INSTALLING`;
- `READY_VERIFIED`;
- `INCOMPATIBLE`;
- `CORRUPT`;
- `REMOVING`;
- `FAILED`.

`LocalModelMetadata` reserves the lifecycle information needed for a future safe implementation:

- model ID/version;
- runtime ID/version;
- asset size;
- SHA-256 integrity identity;
- declared capability snapshot;
- optional compatibility identity.

This is a contract only. `NoOpLocalModelLifecycle` reports `NOT_INSTALLED`; install/verify/remove reject with `UNSUPPORTED`. LAB-1 includes no model file, model URL, remote downloader, network client, native runtime, Qwen/Qualcomm metadata or model hosting.

Future local assets remain subject to the established rule: download separately from the APK into app-private storage, verify before `READY_VERIFIED`, and manage removal through the lifecycle owner rather than UI code.

## Preference and resolver semantics

`PromptProviderPreference` contains:

- `AUTO` — normal product default;
- `SYSTEM` — developer/testing system-only override;
- `LOCAL` — developer/testing local-only override.

The diagnostics override is ephemeral in LAB-1 and is not persisted. No normal-user provider picker exists.

### AUTO

Resolution order is:

1. if system is `READY` **and** every required task capability is `SUPPORTED`, select `SYSTEM`;
2. if system is `SETUP_REQUIRED` or `SETUP_IN_PROGRESS`, a `READY_VERIFIED` sufficient local provider may temporarily service the task while the system transition remains explicit;
3. if system cannot currently satisfy the task for another reason, use a `READY_VERIFIED` sufficient local provider when present;
4. otherwise return an explicit system-setup, local-install/setup, or unavailable outcome.

A system provider that is `READY` but has an `UNKNOWN` or `UNSUPPORTED` required capability is not sufficient. AUTO may use a sufficient local provider instead.

### SYSTEM

SYSTEM is system-only. It never silently falls back to local. A downloadable/downloading system model returns the corresponding system setup state; insufficient/unavailable/failed state returns explicit unavailability.

### LOCAL

LOCAL is local-only. It never silently substitutes system. An absent local model returns local installation required; installing returns setup in progress; incompatible/corrupt/failed state remains explicit.

## System-becomes-sufficient transition

A local fallback is compatibility infrastructure, not a permanent competing user mode.

In AUTO, once the system provider becomes both:

- `READY`; and
- sufficient for the requested task's full required capability set,

SYSTEM wins immediately on the next resolved snapshot even if a verified local fallback is still present.

If the local model is still installed, resolution exposes `SYSTEM_READY_LOCAL_PRESENT` plus `LocalRetirementState.REQUIRED`. This marks cleanup eligibility; it does not auto-delete files in LAB-1.

Retirement is never declared merely because generic system AI appeared. It only follows a task-specific, ready, sufficient system result.

Local removal remains a separate lifecycle operation. If future removal fails, AUTO may continue truthfully selecting the already-verified system provider while `LocalRetirementState.FAILED` and the lifecycle failure remain diagnosable for retry. `REMOVING` is likewise explicit. A completed removal clears the retirement requirement.

## Startup coordinator

`LabAiRuntime` is the single build activation boundary. It alone reads `BuildConfig.UI_ML_LAB`.

`MyMettleApplication.onCreate()` calls `LabAiRuntime.onProcessStart()` for every process start, but that function immediately returns in ordinary debug/release builds. Only the Lab build begins provider probing.

The Lab coordinator:

1. launches off the main thread on an application-owned `SupervisorJob + Dispatchers.Default` scope;
2. probes system and local lifecycle state concurrently;
3. translates exceptions into explicit snapshots;
4. resolves the default AUTO diagnostic task;
5. exposes the current state through a `StateFlow` for diagnostics/future UI.

App launch never waits for probe completion.

## Refresh concurrency policy

Refresh is **coalesced**.

If a refresh is already active, another startup/manual request receives the same active `Job`. No second probe begins. Once that job completes, a later refresh may start a new probe. This prevents duplicate simultaneous work and prevents an older refresh from overwriting a newer result.

Coroutine cancellation is rethrown after the coordinator records a cancelled diagnostic state. Platform exceptions are contained and do not crash application startup.

## Persistence

LAB-1 persists no provider probe snapshot and no provider-specific implementation object.

AUTO is recreated as the normal default each process. SYSTEM/LOCAL are ephemeral developer diagnostic overrides. Current platform readiness is transient and is refreshed from source truth rather than written into Room/DataStore.

No Room entity, DAO or migration is introduced.

## Developer diagnostics

The Lab build extends the existing developer-tools route with a small AI runtime diagnostics page. It shows:

- system readiness;
- verified/unsupported/unknown capabilities;
- Prompt API runtime/library version;
- optional base-model identity;
- last probe time/error;
- capability-probe errors;
- local lifecycle/removal state and metadata if one ever exists;
- ephemeral AUTO/SYSTEM/LOCAL resolution fixture;
- task requirements, selected/setup provider, reason, system transition and local retirement state;
- manual `Refresh probe`.

The page has no free-form prompt input, model download control, equipment scanning or biological/user content.

Normal builds preserve the existing biological developer route directly and do not expose this Lab page.

## Security and privacy

LAB-1 adds no network-backed inference, telemetry or new network permission. The system probe submits no prompts and logs no user text, workout content, review note, image or private file path.

Diagnostic state is limited to provider/capability/lifecycle flags, non-sensitive model/runtime identity, timestamps and error class/code.

## Deliberately unimplemented

LAB-1 does **not** implement or choose:

- Qwen or another local model;
- Qualcomm/QNN or another local runtime;
- local model download/hosting/networking;
- a generic execution/generation API;
- a system download/setup action;
- equipment OCR/vision;
- equipment task requirements;
- Add Machine;
- workout/library product UI;
- normal-user setup/transition popups;
- N-BIO `NanoNoteInterpreter` consolidation;
- automatic local-file retirement.

## Next gate

LAB-2A remains the next phase and is blocked behind its research-before-integration gate. It must produce the evidence-backed deployment playbook before a standalone local VLM proof is attempted in LAB-2B. Only a LAB-2B-proven route may enter My Mettle behind this shell in LAB-2C.
