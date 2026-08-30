# N-BIO-7B physical acceptance correction — 2026-08-28

Status: **N-BIO-7B.4 remains empirically open; N-BIO-7C has not started.**

## Physical report result

The corrected evidence-policy v2 physical run proved that the legacy UNKNOWN-laterality repair worked. Installed Room14 history produced current SHADOW frontier fits for 16 of 19 discovered execution-profile-version/side groups, with 141 eligible observations in total.

The integrity/safety boundary remained clean:

- canonical raw workout/performance evidence fingerprint unchanged;
- BENCHMARK_V0 authority unchanged;
- context consumption remained NONE;
- SHADOW persist/reload equivalence passed;
- full replay equivalence passed;
- fitted numerical outputs were finite and positive;
- foreign keys were clean;
- persisted prescription state was unchanged;
- Native full-backup round trip reproduced raw evidence, prescriptions and candidate SHADOW rows.

Fourteen exclusions were attributable to missing historical body mass on bodyweight-dependent profiles. Those exclusions remain factual; current body mass must not be projected backwards into historical sessions.

## Remaining zero-holdout diagnosis

Despite successful current fits, the physical report contained zero chronological fits and zero held-out observations for every profile. This was not evidence that the user lacked workout history.

Lite Legacy translation intentionally preserves two different temporal facts:

1. original set/session completion timestamps from the source app; and
2. Native ingestion/audit time, stored as `set_observation.recordedAt = backup exportedAt`.

The original historical evaluator treated the Native `recordedAt` column as the historical source-availability time. Because the imported observations were ingested on 2026-08-27 while the workouts occurred earlier in August, no imported observation appeared knowable at its own historical workout cutoff. Current fitting therefore worked while whole-session retrospective validation was starved.

## Source-availability correction

Validation now uses explicit policy:

`n-bio-7b4-historical-source-availability-v2`

The correction is deliberately narrow:

- the SQLite `set_observation.recordedAt` value is never modified or backdated;
- ordinary Native evidence continues to use its factual Native recorded time;
- only observations whose factual source is exactly `lite_legacy_v6_import` may map to source-session finalisation time in the in-memory historical validation adapter;
- source availability is `max(session.completedAt, session.editedAt ?: session.completedAt)`;
- the whole-session holdout cutoff remains `session.completedAt`;
- therefore a source session edited after completion is conservatively unavailable at that session cutoff and cannot leak the later edit backwards;
- append-only supersession/currentness logic remains unchanged.

This is a retrospective reconstruction correction only. It does not change the frozen 7B.2 Half-Normal/Student-t mathematics, evidence-policy v2, model priors, session weighting, 12-session current window, context policy, Room schema, raw evidence, prescriptions or product authority.

The held-out protocol identity is now:

`n-bio-7b34-whole-session-heldout-source-availability-v2`

so subsequent exports are distinguishable from the zero-holdout v1 report.

## Acceptance heap/runtime correction — 2026-08-30

The first two source-availability-v2 device attempts failed before export with Android heap allocation failures requesting roughly 151 MB contiguous allocations. This is an acceptance-engineering failure, not a stochastic-model verdict.

The preceding successful acceptance export already contained 32 N-BIO-7B SHADOW inference runs, 32 capability rows and 32 capability-parameter rows. Each acceptance invocation had retained another final SHADOW state per fitted profile. The full Native backup verifier then materialised all of those derived parameter payloads into an in-memory JSON object and a second pretty-printed JSON string. Repeated acceptance therefore allowed disposable derived state to grow until the verification serialization exceeded the app heap.

The bounded correction is deliberately non-mathematical:

- before a new explicit 7B acceptance action, prior inference runs matching `executionMode=shadow` and the dedicated N-BIO-7B shadow-run model version are deleted; FK cascade removes only their derived capability/parameter rows;
- BENCHMARK_V0 and canonical workout evidence are outside that deletion predicate;
- current parameter persistence moves to lossless deterministic DEFLATE codec schema v2 while retaining schema-v1 decode compatibility and all joint-posterior/per-observation-slack information;
- the internal Native backup round-trip uses compact JSON, while normal user-facing Native backup export remains pretty-printed by default;
- stochastic grid-node evaluation uses a bounded three-worker pool with deterministic indexed output and ordered posterior reduction;
- the hot quadrature likelihood reuses per-thread scratch storage instead of allocating a list for every observation/node evaluation;
- top posterior nodes for per-observation slack are sorted once per fit instead of once per observation;
- held-out demonstration predictive intervals are cached per fit/repetition because several held-out sets can share the same repetition count.

The parallelism level is intentionally conservative. Profiles and chronological sessions remain sequential so several complete posterior states are not resident simultaneously. This should use additional CPU cores without multiplying peak heap pressure. It changes execution time only; the 7B.2 model equation, priors, grid coordinates, likelihood, posterior ordering, evidence policy, validation protocol, context policy and product authority are unchanged.

The acceptance export reports how many stale N-BIO-7B SHADOW runs were pruned before the current run. Physical rerun remains required to demonstrate that peak memory is now bounded and to measure actual wall-time improvement.

## Gate

After implementation CI passes, physical installed-history acceptance must be run again. A valid next report should show the v2 validation protocol and, where prior source sessions exist for a profile, non-zero chronological/held-out evaluation. The resulting empirical metrics must be reviewed before N-BIO-7B can be closed or a new candidate requested.

Do not start N-BIO-7C, promote SHADOW output, or alter BENCHMARK_V0 authority from this correction.