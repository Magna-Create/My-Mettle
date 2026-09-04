# UI/ML Lab UX decisions

> **Status:** agreed product/interaction decisions recorded for later phases. LAB-0 does not implement these surfaces.
>
> Where this file marks an interaction as subject to later design confirmation, do not treat the description as a licence to silently finalise the behaviour.

## Workout exercise card

- The final Lab UI may be built before the corresponding N-BIO output exists.
- Suggested load/reps occupy a region separate from the user's actual input.
- A faded actual-input placeholder may show previous comparable performance.
- A placeholder is **never** stored as entered current-session data.
- The actual current-session value remains `null` until the user acts or an explicitly approved confirmation/prefill semantic says otherwise.
- “Previous performance” means the last **comparable** performance, not literally “last week” or the most recent session row.
- Comparability may depend on `ExecutionProfileVersion`, side, equipment binding/calibration and canonical comparable-evidence semantics.
- An equipment change may invalidate direct historical placeholder comparability.
- Wording such as `Aim for`, `Try`, `You could try` remains a future UX-strength/presentation policy rather than a hardcoded posterior-width mapping.
- If N-BIO does not yet own the suggestion, Lab UI must consume fixture/mock state through an explicit provider boundary rather than inventing pseudo-prescription logic in Compose.

## Switch control

The intended compact control shape is:

```text
Rate | Switch | Complete
```

- `Switch` may replace the old full-width “Substitute this exercise” action.
- Intended interaction supports:
  - **tap** → open/explore the switch surface;
  - **hold from rest** → transition while held;
  - **flick/release** → fast path from the held state.
- Exact gesture thresholds, visual transition, cancellation rules and accessibility alternatives remain subject to LAB-3 design confirmation.
- The equipment chip is primarily visible state. Its presence does not automatically imply tappable behaviour.
- `Add Machine` is expected to live inside the Equipment path rather than necessarily as a peer action.
- A session-only switch must not silently become a persistent exercise/equipment preference.

## Add Machine

- Capture the whole machine first.
- Background masking/relight/recognition work may continue while later capture steps proceed, provided ownership/lifecycle is explicit and the UI remains responsive.
- Use resistance/loading-system-specific workflows rather than one universal machine form.
- **Auto** and **Semi-auto** are distinct future modes.
- Implement and validate **Semi-auto before Auto**.
- Machine interpretation is editable/correctable before canonical save.
- Final save/use state should distinguish concepts such as:
  - use today/session only;
  - make regular/default;
  - keep additional available equipment;
  as the later UX requires.
- Exact wording and control layout remain a later design decision.

## Equipment state and historical meaning

- Equipment is a modelling/context input, not merely camera metadata.
- A visible equipment chip should make the current assumption inspectable without claiming that every chip interaction is finalised.
- Historical performance remains associated with the equipment/calibration actually used; a later preferred machine must not reinterpret old history.
- User-entered load, equipment configuration and the N-BIO modelling coordinate remain separate concepts.

## Libraries

- Current exercise/library/swap/equipment library experiences are not assumed final.
- LAB-4 requires a shared Library UX grammar/information architecture before implementation.
- Do not grow separate ad-hoc libraries merely because each feature can create one independently.
- Exercise, substitution and equipment discovery/selection should be reconciled through the LAB-4 design gate before a final information architecture is coded.

## Suggestion and adaptive-change authority

- Useful but uncertain predictions may later be presented as tentative suggestions rather than forced to `null`, but the exact thresholds/wording are product policy.
- Do not expose raw numeric confidence as the normal user-facing substitute for semantic uncertainty communication.
- Adaptive workout changes belong to N-BIO V8 plus explicit user-control/product policy. UI prototypes may display fixture states; they do not author adaptation rules.
- High-impact session/programme changes must not be silently introduced merely because backend code can calculate them.

## Human/design gates

Before LAB-3 behaviour-driving implementation, stop for Kian to provide/finalise the previously mocked workout/exercise UI flow.

Before LAB-4 implementation, stop for Kian to design/finalise the harmonised Library information architecture.

Where either phase introduces a materially new gesture, state hierarchy or multi-step flow not covered by an approved design, resolve it in design/conversation rather than burying the decision in implementation code.
