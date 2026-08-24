# N-BIO-6 temporal foundation implementation

This note records concrete Room 12 engineering choices. `PLAN.md` remains the authority.

## Canonical boundary

`PerformanceMetric` remains the typed scalar performance namespace. Temporal and physiological
streams use the separate typed `EvidenceMetricKey` registry plus an explicit semantic role, so
heart rate remains `PHYSIOLOGICAL_RESPONSE` rather than being mislabeled as mechanical output.
Acquisition method and temporal granularity are independent fields on both trace and scalar
evidence.

One `SetRecord` continues to mean one user/prescription-intended performed bout. Algorithmically
detected cardio intervals are future derived segments, not fabricated raw sets.

## Physical storage and scope

`evidence_trace` is the logical canonical series. `evidence_trace_chunk` stores deterministic
codec-v1 BLOBs aligned to source-record provenance boundaries. The codec preserves exact
epoch-second/nanosecond timestamps, irregular sampling, gaps, spikes, negative numeric values,
numeric/state intervals and route coordinates. It performs no interpolation, smoothing,
downsampling or outlier removal.

Four explicit link tables connect a trace to a session, session exercise, set record or performed
observation. These links use real foreign keys; there is no polymorphic `scopeType + scopeId`.
A session trace can therefore contextualise several observations without copying its payload.

## Revisions and layers

External records are immutable Native snapshots keyed by a stable logical source key and strictly
linear Native revision number. Each update supersedes the current revision; unique indexes prevent
duplicate revisions and forks. `DELETED_AT_SOURCE`, `PERMISSION_UNAVAILABLE` and
`SOURCE_DISCONNECTED` are distinct snapshots and never cause canonical trace deletion.

Raw traces/artifacts/chunks/links do not depend on derived summaries. Derived summary inputs point
to raw traces with `RESTRICT`; deleting summaries cannot cascade into raw evidence. UI graph cache
rows are explicitly disposable. The existing inference deletion path does not address temporal
tables, and the temporal repository exposes a separate derived/cache discard operation.

The older `health_observation` table is retained only for scalar Lite compatibility and N-BIO-9
reconciliation. It is not an alternative trace store. Future Health Connect ingestion must map
generic provenance and chunks into this temporal substrate rather than dual-write series there.

## Timing and compatibility

Performed observations can store exact optional start/end bounds plus `TimingQuality`. Native
manual completion defaults to `COMPLETION_ONLY`; it does not invent contraction start. Completion
no longer back-fills `SessionExercise.startedAt`. Lite history has no trace data and therefore
imports no traces; factual completion becomes the end bound and start remains null.

The existing load/reps/duration/distance UI methods and history properties remain compatibility
adapters over generic scalar observations. They do not write fixed set columns and are not the
canonical evidence model. Current conservative N-BIO inference continues to query only current
non-superseded scalar observations and deliberately ignores temporal/physiological traces.
