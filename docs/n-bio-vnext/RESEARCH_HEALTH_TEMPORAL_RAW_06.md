│ Samsung Health · imported        │
│                                  │
│ [ View details ] [ Replace ]     │
└──────────────────────────────────┘

Incline: 4% · manually entered
```

The source badge should distinguish the interoperable transport and original origin conceptually. “Imported via Health Connect · Samsung Health” is more precise than implying Health Connect itself measured the workout.

`Unlink source` should remove/revoke the association, **not silently erase the Native snapshot**. “Delete from My Mettle” should be a separate explicit action.

### What constitutes the cardio “set”?

A 20-minute continuous treadmill workout is one **exercise bout/observation**, not naturally eight resistance-style sets merely because eight intensity intervals are detected.

If `SetRecord` means generically “one performed bout”, then:

```text
SessionExercise
    └── SetRecord
            └── PerformanceObservation
                    ├── speed trace
                    ├── HR trace
                    ├── distance intervals
                    └── grade annotation
```

is acceptable.

If `SetRecord` semantically contains resistance assumptions such as repetitions, inter-set behaviour or discrete contraction sets, N-BIO should generalise it now to something equivalent to `ExerciseBoutRecord` rather than distort cardio.

Automatically detected intervals should initially be:

```text
DerivedObservationSegment
    parentObservationId
    start/end
    algorithmVersion
```

not new immutable raw `SetRecord`s. Re-running an improved interval detector should not rewrite history.

Only intervals intentionally entered/prescribed as distinct workout bouts should become canonical child bouts.

### Predictive-model validation programme

The most important resistance-HR experiment is longitudinal and within-person.

**Primary trial**

```text
Input available before set 3:
    exercise/profile
    sets 1–2 external performance
    load
    reps
    set durations
    rest durations
    session elapsed time

Baseline A:
    performance history only

Baseline B:
    A + timing/rest

Candidate C:
    B + pre-set HR
        + HR rise
        + HR recovery
        + pre-next-set HR
        + observed-minus-expected HR residual

Target:
    set-3 reps / load / duration / velocity / completion
```

Use chronologically held-out sessions rather than randomly splitting individual sets from the same workout, because otherwise neighbouring sets leak session state into training and validation.

Secondary targets should include:

```text
later-session underperformance
spontaneously extended rest
load reduction
exercise abandonment
performance relative to current capability estimate
```

and stratify by:

```text
heavy compound work
moderate-load hypertrophy work
isolation work
supersets/circuits
isometric holds
```

No HR feature should become prescription-driving until it demonstrates repeatable incremental value over the performance+timing baseline.

### Validation vectors

| Case | Expected raw storage and evidence | Derived interpretation | N-BIO behaviour |
|---|---|---|---|
| **Steady 20-min treadmill run with speed trace** | One bout/observation; `speed(t)` TRACE with provenance; duration/possibly distance summary/interval | Time-weighted speed, sustained speed, distance, coverage | Running capability evidence; HR contextual if present; can inform future capability |
| **Interval treadmill workout with same average speed** | Same scalar average but full speed trace retained | Detect versioned interval segments; intensity distribution differs | Must **not** be capability-equivalent to steady case |
| **Treadmill + manual grade** | Imported speed trace plus `INCLINE_GRADE=...`, SUMMARY/USER_REPORTED | Grade-aware contextual summaries | Valid mixed-resolution observation |
| **Imported treadmill speed with gaps** | Preserve samples and gaps; no interpolation | Coverage/gap flags | Use available trace with reduced confidence |
| **Cycling power + cadence + HR** | Power/cadence primary performance traces; HR physiological trace | Work, power-duration features, cadence distribution, HR residuals | Strong cycling capability evidence; HR conditioning/context |
| **Cycling HR but no power** | HR trace + duration/speed if available | HR internal-load summaries | Weaker external capability evidence; do not infer watts from HR |
| **StairMaster cadence/floors, no level telemetry** | Cadence TRACE if supplied; floors INTERVAL; duration | cadence/floor summaries | Device/profile stair capacity; no invented machine level |
| **Same StairMaster, manually “level 8”** | Add MACHINE_LEVEL SUMMARY/USER_REPORTED | Local device-setting context | Compare longitudinally only within compatible machine profile |
| **Rowing power/pace/stroke-rate** | Traces with device provenance | work, sustained power/pace, stroke-rate distribution | Strong rowing-specific capability |
| **Manual duration + approximate speed** | Duration SUMMARY/USER_REPORTED; speed SUMMARY/USER_ESTIMATE | Coarse workout summary | Valid but lower-information capability evidence |
| **HC workout imported twice** | Unique external-session claim finds existing snapshot | None duplicated | Idempotent; offer existing import |
| **HC source updated** | New immutable external artifact/revision; supersedes old | Recompute summaries from new revision | Old evidence remains auditable; newest active revision used |
| **HC source deleted** | Original snapshot retained; `DELETED_AT_SOURCE` | Existing summaries still reproducible | No capability deletion merely because source vanished |
| **Two health apps overlap** | Keep origin-separated source records | Flag possible duplicate/conflict | User chooses source; never arbitrary merge |
| **Watch HR sensor spikes** | Store untouched HR samples | QC outlier/sustained-peak features | Raw peak not trusted; no physiological inference from spike |
| **Same cardio workload, progressively lower HR** | External trace + HR traces retained each session | Matched-work HR residual trend | Compatible with conditioning/efficiency, not proof of cause; experimental conditioning input |
| **Heat/stress causes high HR, performance unchanged** | Normal external evidence + high HR response | Positive HR residual | Systemic-context anomaly; **do not downgrade muscle capability automatically** |
| **Cardio after resistance vs fresh** | Preserve session order/timestamps and external traces | Compare residual/performance with prior-load context | Prior resistance is a candidate confounder/fatigue input |
| **Interval detection** | Raw trace untouched | Segments generated with detector version | Derived/recomputable |
| **Same averages, different traces** | Both traces retained | Different variability/intensity/sustained-work summaries | Models can distinguish sessions |
| **Hack squat, same load/reps, different HR recovery** | Set bounds + session HR trace | HRR/residual differs | Completed performance stays canonical; systemic-cost/readiness hypothesis only |
| **Elevated pre-set HR precedes rep drop** | Raw set performance + HR/context | Candidate positive predictive observation | Adds evidence only after repeated held-out validation |
| **High HR does not precede drop** | Preserve equally | Counterexample enters training set | Prevents deterministic high-HR=fatigue rule |
| **Lower-body compound vs upper-body isolation** | Same session HR linked by exact bounds | Exercise-specific expected-response distributions | Systemic-cost learning possible; no local-activation inference |
| **Same workload, different rest** | Rest duration + set bounds + HR | Separate elapsed-rest and HR recovery effects | Test incremental HR benefit beyond rest |
| **HR changes over months, workload stable** | Longitudinal raw HR + matching profile/performance | Residual trend | Conditioning/skill/context candidate, not muscle-development proof |
| **One poor strength session + abnormal HR** | Preserve both external decline and HR residual | Multimodal anomaly | May update short-term systemic uncertainty; avoid chronic regression from one session |
| **Abnormal HR from environmental stress, no muscle regression** | HR anomaly with preserved normal external performance | Conflicting evidence | Systemic/context uncertainty; local capability remains anchored by performance |
| **Same target-muscle work, exercises differ in HR cost** | Separate profile-specific HR traces | Learn different systemic-response distributions | Candidate personalised `SystemicCost`; experimental |
| **Missing/noisy HR around set boundaries** | Preserve missing/noisy data and boundary timestamps | Coverage/QC invalidates selected HR features | Resistance performance remains valid; HR feature omitted rather than imputed |

The paired positive/negative cases are essential. Cases 22 and 23, for example, prevent a model from discovering a one-direction heuristic merely because the training data contains only examples where elevated HR preceded performance loss.

### Expected-HR residual model

A sensible first model is hierarchical/personal rather than population-only:

```text
HRFeature ~
    user
  + exerciseProfile
  + load
  + reps
  + setDuration
  + restDuration
  + setOrdinal
  + sessionElapsedTime
  + recentWork
```

Then:

```text
HRResidual =
    observed feature
  - expected feature
```

Use the residual itself as an **observation**, not immediately a diagnosis.

Test whether it predicts:

```text
next-set performance error
later-session degradation
unexpected rest extension
cardio performance under matched external load
```

This has a stronger scientific footing than asking the model to interpret raw BPM globally because exercise identity and individual baseline are explicitly conditioned out.

## Foundation-freeze requirements, hazards, evidence quality and sources

### N-BIO-6 change list

| Category | Requirement | Decision |
|---|---|---|
| **MUST ADD BEFORE FOUNDATION FREEZE** | Generic temporal evidence/`EvidenceTrace` abstraction | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Point-series representation | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Interval-series representation with value semantics | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Source-record/chunk boundaries | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Per-metric/per-trace evidence granularity | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Acquisition method separate from temporal resolution | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Generic external-record provenance | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Source origin/device/type/ID/version/last-modified information | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Source start/end interval | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Immutable import revision/supersession relationship | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Exact observation/set start and end timestamps | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Timing quality/uncertainty | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Session-scoped trace ownership | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Observation↔trace links so resistance sets can reference session HR without copying it | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Semantic distinction: performance vs physiological-response trace | **Yes** |
| **MUST ADD BEFORE FOUNDATION FREEZE** | Manual and imported evidence coexisting per observation | **Yes** |
| **MUST DESIGN FOR NOW** | Spatial route evidence | **Yes; implementation can follow later** |
| **MUST DESIGN FOR NOW** | Versioned trace encoding/chunk payload | **Yes** |
| **MUST DESIGN FOR NOW** | Derived summary provenance/versioning | **Yes** |
| **MUST DESIGN FOR NOW** | Trace QC/outlier/gap metadata | **Yes** |
| **MUST DESIGN FOR NOW** | Source-state metadata after disconnect/update/deletion | **Yes** |
| **SAFE TO ADD LATER** | Actual Health Connect DTO mapper | N-BIO-9/integration layer |
| **SAFE TO ADD LATER** | Health Connect changes-token synchronisation | Later |
| **SAFE TO ADD LATER** | Automatic interval detector | Later, derived |
| **SAFE TO ADD LATER** | UI downsample cache implementation | Later |
| **SAFE TO ADD LATER** | Critical-speed/critical-power inference | Later versioned models |
| **SAFE TO ADD LATER** | HR expected-response model | Later experimental N-BIO |
| **SAFE TO ADD LATER** | Exercise-specific `SystemicCost` latent | Later after validation |
| **SAFE TO ADD LATER** | Cardio local-muscle exposure model | Later experimental |
| **DO NOT STORE** | Destructive smoothed trace instead of source samples | Never as canonical |
| **DO NOT STORE** | Watch HR zones as though they were raw observations | Store HR; derive zones |
| **DO NOT STORE** | Automatically detected intervals as immutable truth | Derived only |
| **DO NOT MODEL YET** | HR→hypertrophy multiplier | Unsupported |
| **DO NOT MODEL YET** | HR→local-fatigue deterministic rule | Unsupported |
| **DO NOT MODEL YET** | Cardio→equivalent hypertrophy sets | Unsupported |
| **DO NOT MODEL YET** | Universal cross-machine cardio capability scalar | Architecturally/physiologically inappropriate |

### Explicit do-not-implement list

| Tempting shortcut | Assessment |
|---|---|
| **Average speed is sufficient for an interval workout** | **Reject.** It destroys intensity distribution and speed-duration evidence. |
| **Average HR is universal training load** | **Reject.** HR is internal cardiovascular response and heavily context-dependent. TRIMP can be a contextual model, not universal dose. citeturn23search0turn19search7 |
| **HR directly multiplies muscle stimulus** | **Reject.** No evidence establishes BPM as a local hypertrophic-dose multiplier. |
| **HR directly measures local muscle fatigue** | **Reject.** It can be systemic/contextual and possibly predictive, but is not a local contractile-state measurement. |
| **Calories are universal dose** | **Reject.** Wearable energy-expenditure estimates have historically shown poor validity, including during resistance exercise. citeturn18search2turn18search8 |
| **StairMaster level is cross-machine comparable** | **Reject.** Preserve as device/profile-local ordinal evidence. |
| **Cadence directly equals effort** | **Reject.** Effort depends on force/resistance/power as well as cadence. |
| **Watch HR zones are precise personal thresholds** | **Reject.** Treat zone boundaries as versioned interpretations. |
| **Age-predicted HRmax is exact HRmax** | **Reject.** Population equations should not become exact personal physiology. |
| **Cardio minutes → hypertrophy sets** | **Reject.** Distinct adaptations with uncertain overlap. citeturn23search11turn23search15 |
| **Import trace without source provenance** | **Reject.** Makes future reconciliation, dedupe and trust assessment impossible. |
| **Mix all Health Connect origins inside session timestamps** | **Reject.** Temporal overlap is not a foreign-key relationship. citeturn24view5 |
| **Delete Native history because HC source disappears** | **Reject as default policy.** Retain immutable snapshot and mark source state. |
| **Manual summary = full trace** | **Reject.** Both are valid evidence with different information content. |
| **Higher HR always means poorer recovery** | **Reject.** Heat, hydration, arousal and workload alter HR, while fatigue responses are not uniformly directional. citeturn21search0turn19search0 |
| **Lower HR always means improved conditioning** | **Reject.** Conditioning is one explanation, not the only one. citeturn19search1 |
| **Post-set HRR determines the rest timer** | **Reject until incremental prediction is validated.** |
| **Exercise HR identifies muscle activation** | **Reject.** Exercise/muscle-mass effects on cardiovascular demand do not provide local recruitment measurement. citeturn17search5turn17search4 |
| **Cardiovascular strain implies hypertrophy** | **Reject.** Different physiological domains. |
| **Clean sensor spikes by overwriting raw samples** | **Reject.** Quality-filter in derived layers. |
| **Interpolate gaps and treat interpolation as observed data** | **Reject.** Preserve missingness. |
| **Critical power/speed from ordinary non-maximal sessions without uncertainty** | **Reject.** These capability parameters require sufficiently informative performance efforts. citeturn14search0turn15search20 |
| **Route-derived elevation equals measured elevation automatically** | **Reject.** Route altitude accuracy/filtering matter. |
| **Samsung cadence will definitely be available because Samsung watches measure cadence** | **Reject.** Current Samsung HC documentation does not guarantee that export. citeturn22view6 |
| **Samsung workouts appear instantly** | **Reject.** Samsung documents synchronisation triggers/policies, not guaranteed latency. citeturn22view6turn12view1 |

