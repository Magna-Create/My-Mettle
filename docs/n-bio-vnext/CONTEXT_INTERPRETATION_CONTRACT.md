# N-BIO-7A.5 Context Interpretation Contract

## Status

N-BIO-7A.5 establishes an interpretation boundary only. It assigns **zero mathematical effect** to workout-context annotations.

The governing flow is:

```text
RAW USER/SOURCE MATERIAL
→ VERSIONED STRUCTURED INTERPRETATION
→ EXPLICIT MODEL INPUT BOUNDARY
→ VALIDATED N-BIO MODEL
```

Never:

```text
LLM OUTPUT → HIDDEN BIOLOGICAL MODIFIER
```

## Canonical note ownership

The current shared `ReviewNotesDialog` writes two live review-note sources:

- whole-session review → `SessionReviewEntity.note`, owned by `sessionId`;
- exercise review → `ExerciseReflectionEntity.note`, owned by `sessionExerciseId`.

Both are editable after their first save. Their existing owner keys are stable, so 7A.5 does **not** introduce a second generic raw-note table.

`SessionExerciseEntity.note` and `SetRecordEntity.note` remain compatibility/raw fields but are not current `ReviewNotesDialog` authoring surfaces. They are not automatically interpreted by 7A.5.

Raw text remains canonical. `note_interpretation_run` and `context_annotation` are derived state only.

## Source revision/currentness

An interpretation run stores SHA-256 of the exact canonical note text plus the owner's `updatedAt` snapshot. Current selection requires the stored text hash to match the text currently owned by the raw entity and the current tag-schema version.

Consequences:

- the same text may be interpreted repeatedly by newer interpreter/prompt/schema versions;
- old runs are never mutated and remain auditable;
- editing the raw text changes the source hash, so prior annotations stop resolving as current;
- deleting derived interpretation state does not edit/delete raw notes.

## Ontology

Tag schema v1 is a bounded registry. Interpreters may emit only registered IDs.

Values are typed as:

- `BOOLEAN`;
- `NUMBER` with canonical unit where applicable;
- `CATEGORY`;
- `TEXT_ACTION`.

Definitions also state valid note scopes, categories/units, human semantics and inference eligibility.

Eligibility is structural, not a confidence score:

- `UX_ONLY` — product memory only;
- `CONTEXT_ONLY` — retained context but not exposed as an N-BIO covariate;
- `CANDIDATE_COVARIATE` — may be presented to a future explicitly configured candidate model;
- `PROHIBITED_FROM_INFERENCE` — never eligible for N-BIO consumption.

`NEXT_SESSION_ACTION` and other UX memory cannot enter `ContextEvidenceView` simply because they share persistence with biological-context tags.

## Assertion and temporal semantics

Annotations distinguish:

- asserted vs negated vs uncertain wording;
- current vs historical vs next-session vs unspecified applicability;
- exact vs approximate numeric reports.

For example, `I wasn't ill` must not become `ILLNESS_REPORTED=true`, and `I was ill last week but I'm fine today` must not become current-session illness.

Interpreter/model certainty, if recorded at all in future, is interpreter metadata. It is not N-BIO posterior uncertainty.

## Audit spans

Where an annotation is extracted, it may retain exact UTF-16 start/end offsets and the exact supporting source substring. This supports local auditability only. Hidden reasoning/chain-of-thought is never requested or stored.

Developer diagnostics omit raw note text and omit exact source-span text. Text-action values are redacted there.

## Interpreter boundary

`NoteInterpreter` has three implementation kinds:

- `ML_KIT_NANO`;
- `RULES`;
- `NO_OP`.

The save flow is:

```text
ReviewNotesDialog Save
→ persist canonical note
→ update/dismiss normal UI
→ optional foreground ContextInterpretationCoordinator
→ Nano only when strict runtime capability is available
→ otherwise Rules
→ NoOp if Rules itself fails
→ persist derived run/annotations
```

Interpretation failure never rolls back a saved note.

The Rules interpreter is deliberately conservative. Unknown wording can validly produce zero annotations.

## ML Kit Prompt API policy

The Nano adapter uses the maintained ML Kit GenAI Prompt API. It probes runtime feature status rather than inferring capability from the phone model.

Nano runs only when Prompt API is `AVAILABLE` **and** Structured Output is available. It uses schema-constrained typed output, deterministic/conservative generation settings and Thinking Mode disabled.

`DOWNLOADABLE`, `DOWNLOADING` and `UNAVAILABLE` do not block Save and do not trigger an implicit model download. If Prompt API exists but Structured Output is unavailable, 7A.5 uses Rules rather than accepting free-form generated JSON.

Runtime base-model identity is captured when the API exposes it. The device model name is not used as a substitute for runtime model identity.

## Privacy boundary

Nano may receive only:

- the single raw user-authored note being interpreted;
- note scope;
- exercise name when useful for exercise-note interpretation;
- bounded ontology definitions/output schema.

Nano does **not** receive Health Connect records, HR/HRV traces, structured sleep records, body measurements, workout history, exercise history, N-BIO posterior state, unrelated notes or hidden profile data.

A user-written sentence such as `Only slept four hours` is language material and may yield a user-reported context tag. A structured sleep record from Health Connect is not language material and is never routed through Nano.

## N-BIO input boundary

`ContextEvidenceView` is the only 7A.5 boundary intended for later N-BIO consumption. Each item retains tag/schema identity, typed value, scope, temporal/assertion semantics, source hash and interpretation provenance.

It contains **no penalty, multiplier, capability adjustment, observation-noise adjustment, stimulus adjustment or other biological equation**.

Future models must explicitly declare consumed tag IDs/schema versions in behaviour-driving model configuration. Performance-only and context-aware candidates must then compete under the existing chronological held-out evaluation rules before context can affect user-facing behaviour.

Execution-semantic warning tags (`TECHNIQUE_CHANGE_REPORTED`, `ROM_CHANGE_REPORTED`, `GRIP_CHANGE_REPORTED`, `EQUIPMENT_DIFFERENCE_REPORTED`, `SETUP_CHANGE_REPORTED`) signal only a possible reported change. They never mutate an historical `ExecutionProfileVersion`.

## Persistence and backup

7A.5 uses Room 14. New derived tables participate in the existing Native full-backup contract because that contract snapshots every application table dynamically.

A Room 13 Native backup therefore does not silently restore into Room 14; exact-schema restore rejects it and requires translation to the current Native format.

Lite migration does not invent annotations. The shipped Lite bootstrap UI/importer remains out of scope and is not reintroduced by 7A.5.

## Phase boundary

- **7A** — posterior/model-configuration/inference provenance foundation.
- **7A.5** — bounded note interpretation and external exercise-authoring contracts.
- **7B** — dynamic-resistance capability modelling, after this contract passes its acceptance gates.
- **N-BIO-9** — later product/integration work such as broader context UX, explicit reannotation controls, rollout/download management, notification/dashboard consumers, Health Connect/HR experiments and Analysis Export. It no longer owns creation of the note-annotation architecture itself.
