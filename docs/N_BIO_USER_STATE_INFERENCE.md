# N-BIO-4 — user-state and inference scaffold

## Scope

N-BIO-4 makes biological interpretation a versioned, recomputable layer downstream of immutable
workout evidence. Room is now version 7 and retains the destructive-development policy: Native is
not yet the authoritative workout store, while Lite Legacy remains the migration source.

The new derived path is:

```text
completed session/set evidence + execution-profile recruitment
                            ↓
                     inference_run
                ↙             ↓              ↘
stimulus_estimate   muscle_state_snapshot   exercise_translation_state
```

An inference run records the reference profile and the recruitment, stimulus, muscle-state and
translation model versions used to produce its outputs. Historical runs are immutable snapshots.
Deleting/rebuilding them does not edit `session`, `session_exercise` or `set_record` evidence.

## Room v7 records

- `inference_run` owns calculation time, evidence horizon/count and complete model provenance.
- `stimulus_estimate` links one completed working set to a stable muscle-segment ID, role,
  recruitment weighting, dimensionless v0 estimate and confidence.
- `muscle_state_snapshot` keeps development, volume scale, structural-capacity scale, recent
  stimulus and recovery as separate estimates rather than one exercise-owned score.
- `exercise_translation_state` stores execution-profile-specific observed
  load/rep/duration/distance/RIR anchors, uncertainty and sample count.

User state is side-addressable. The v0 engine writes `bilateral` because present execution and set
evidence is not side-resolved; the schema can later hold independent left/right estimates without
changing anatomy IDs.

## Replaceable v0 engines

`StimulusEstimator`, `MuscleStateUpdater` and `ExerciseTranslationModel` are independent engine
boundaries.

The first implementations are deliberately conservative:

- A completed non-warm-up set creates recruitment-weighted set units. RIR affects evidence
  confidence only; it does not feed an invented effort/hypertrophy curve.
- Every independently tracked segment receives a neutral `developmentIndex = 1.0` with maximum
  uncertainty. Evidence count is recorded, while volume, structural capacity, recent stimulus and
  recovery remain null. All-time evidence is not mislabeled as recent stimulus.
- Translation stores the latest observed performance for the same execution profile. It does not
  claim cross-exercise transfer.

These outputs are useful scaffolding and provenance, not a research-grade biological model. Their
version IDs make later reinterpretation explicit.

## Runtime and lifecycle

`RoomInferenceRepository.recomputeFromRawHistory()` is an explicit maintenance/background
operation. It is not called by ordinary screen navigation or set entry. When this becomes a product
surface it must run through the visible background-task UI rather than silently replaying history.

`latestSnapshot()` reads persisted current output without recomputation.
`discardDerivedStateForRebuild()` deletes only derived runs and their cascaded outputs.

## Deliberate limits

- No hypertrophy, development, recovery or fatigue equation is claimed yet.
- No recency window is selected yet.
- No cross-exercise transfer coefficient exists yet.
- N-BIO-5 consumes the same-profile translation anchor for prescription load evidence. Neutral
  muscle state and null recovery remain deliberately non-operative.
- Incremental inference after ordinary set/session completion remains a later engine policy; the
  explicit v0 path is full-history replay only.

N-BIO-5 is implemented in [`N_BIO_PROGRAMME_RESOLVER.md`](N_BIO_PROGRAMME_RESOLVER.md): programme
intent now resolves through whole-session constraints, target coverage and persisted same-profile
performance state without coupling raw evidence to the current inference formula.
