# AI runtime contract

> **Status:** LAB-0 contract skeleton. It defines ownership and replacement principles only; it does not select or implement an AI runtime.

## Contract boundary

Future AI-assisted product code should depend on one typed prompt-provider boundary rather than directly depending on a concrete system or local model API.

Conceptually:

```text
product task
    ↓
typed request + required capabilities
    ↓
prompt-provider boundary
    ↓
system provider OR local compatibility provider
    ↓
typed result / explicit unavailable or failure state
```

The provider API and exact Android classes are deliberately not frozen during LAB-0.

## Provider principles

1. Prefer the system Android/ML Kit Prompt API when its **task-specific capabilities** are sufficient.
2. Treat a local model as a fallback compatibility bridge unless a later explicit product decision makes it the universal backend.
3. Do not expose two competing normal-user AI experiences merely because two providers happen to exist.
4. If system capability later becomes sufficient for a task, the intended normal product behaviour is to migrate that task to system AI and remove downloaded fallback assets after an appropriate, verified transition.
5. If later evidence supports making the local model the universal product backend, make that a separate explicit product/runtime decision. Do not let an experimental spike decide it accidentally.
6. Keep provider implementations replaceable behind the typed boundary.
7. Treat capability checks as task-specific. `AI exists` is not a sufficient capability test.
8. Represent unavailable, unsupported, cancelled and failed states explicitly enough that product code does not fabricate a result.

## Likely capability concepts

LAB-1/LAB-2A may refine or rename these concepts after current API research:

- `TEXT`
- `IMAGE_INPUT`
- `STRUCTURED_OUTPUT`
- `SYSTEM_INSTRUCTIONS`
- `MULTI_IMAGE`

These are capability concepts, **not** a claim that a frozen Android API exposes these exact enum names or semantics.

A task should declare the capabilities it genuinely needs. Provider selection should answer whether a provider can satisfy that task, not whether a device has any generative-AI feature.

## Local asset contract

If a local fallback is later required:

- model assets are downloaded separately rather than baked into the APK;
- files live in app-private storage;
- lifecycle code owns download/readiness/removal rather than UI code;
- a failed/partial download must not masquerade as model availability;
- moving from local fallback to a sufficient system provider includes a reviewed cleanup transition for no-longer-needed downloaded assets.

LAB-0 adds no model download code or model artefacts.

## Product/UI boundary

Compose/product surfaces request a typed task and consume a typed result. They should not:

- know that a provider is Qualcomm/Qwen/system/another runtime unless a developer surface explicitly needs diagnostics;
- parse provider-specific free-form text when a typed result contract is required;
- implement fallback inference rules themselves when a provider is unavailable;
- expose multiple AI entry points solely because more than one backend is installed.

Provider-specific diagnostics may exist for development without becoming normal product UX.

## Research gate

LAB-2A must stop and research one credible local multimodal route before serious Qualcomm/Qwen/local-VLM Android integration. A minimal standalone image+prompt path must work before that route enters My Mettle.

The research gate exists specifically to avoid repeating native-runtime integration characterised by:

- benchmark hell;
- repeated rebuild failures;
- opaque native errors;
- uncertain runtime compatibility;
- integration without a known-good minimal sample.

## LAB-0 non-decisions

LAB-0 does **not** decide:

- Qwen model/version/quantisation;
- Qualcomm/QNN use;
- LiteRT-LM use;
- ONNX Runtime use;
- MediaPipe LLM/VLM use;
- exact ML Kit Prompt APIs beyond the existing upstream dependency footprint;
- OCR/segmentation/runtime dependencies;
- local-versus-system long-term product preference;
- model download hosting or server architecture.

Those decisions require their explicit later phases and current evidence.
