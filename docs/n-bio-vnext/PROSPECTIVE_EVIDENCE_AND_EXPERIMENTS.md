# N-BIO: evidence capture, ongoing probes and replaceable models

> **Status: agreed direction / implementation proposal, NOT an empirical promotion.** 16 November 2026.
>
> This is a scoped companion to [PLAN](./PLAN.md), [POSTPONED_DEVELOPMENT](./POSTPONED_DEVELOPMENT.md), [PRODUCT_ROADMAP_GATES](./PRODUCT_ROADMAP_GATES.md) and [INFERENCE_BACKEND_AUDIT](./INFERENCE_BACKEND_AUDIT.md). Existing immutable mathematics, model identity, authority and causal-evaluation contracts continue to apply.

## Development principle

Ship a usable, instrumented **data-capture product** before attempting to settle every biological assumption. Treat uncertain algorithms as replaceable candidates, not as reasons to postpone the entire application. Preserve original observations and record every model's versioned predictions before outcomes occur.

This does **not** mean uncertainty automatically authorises a strong clinical/biological claim, high-impact autonomous workout changes or corruptible history. Distinguish a reversible tentative suggestion from a claimed measurement or an irreversible action.

## Shared evaluation loop for PD-001 through PD-004

1. Capture canonical evidence with identity, units, execution/equipment semantics, timestamps and correction provenance.
2. Freeze the candidate and a simple benchmark's **pre-outcome** predictive distribution and model/config/solver version.
3. Observe the user's actual outcome and any effort/equipment/context metadata without overwriting the earlier prediction.
4. Score eligible held-out observations chronologically; report calibration, bias, error, coverage, failure and runtime/energy. Count independent episodes, not duplicate rows, where appropriate.
5. Compare against simpler baselines and counterfactual/sensitivity variants when defensible.
6. Persist *negative* results. A candidate that worsens performance does not become authoritative by virtue of being complex.
7. Update algorithms as **new immutable model/config candidates** and replay derived state from preserved facts. Keep prior evaluations available.
8. Separate **predictive improvement** from proof of physiology or a causal improvement in training results. Recommendations influence the actions observed; observational correlations alone cannot prove coaching benefit.

A small optional **probe** may collect otherwise unobservable information, e.g. a standardised effort check or a comparable next-session performance attempt. Probes must be reasonable for current circumstances, never force a max effort, and distinguish performance demonstrated from maximum capability.

## Per-investigation questions

| Record | Plain-English test | Promising additional evidence | What a win would mean |
| --- | --- | --- | --- |
| PD-001 capability (7C) | Can we predict achievable holds, duration or contraction counts? | Repeated comparable tasks; optional RIR/effort; repeat trials | Better calibrated performance predictions; not proof of a unique physiological capacity |
| PD-002 demand/dose (7D) | Does our estimate of how demanding a set was improve subsequent predictions? | RIR/failure reports, consistent execution, later performance and training exposures | Predictive usefulness of a dose proxy; **not** direct measurement of hypertrophy/fatigue |
| PD-003 temporal/context (7E) | Does elapsed time and reported context improve predictions after changes or breaks? | Explicit start/resolution of episodes; repeated independent episodes; longitudinal data | Better future-performance predictions; not automatically a biological label or causal penalty |
| PD-004 equipment transfer (7F) | Does known equipment context improve predictions across machines/profiles? | Per-set machine binding, model/instance/facts/angle/entry basis; A/B evidence | Better cross-profile transfer within tested relationships; not universal mechanical equivalence |

The existing PD entries remain OPEN until their independent evidence-backed closure conditions are met. Data collection and provisional product usefulness do not silently close them.

## Optional set-level effort capture

**Proposal, not implemented:** a lightweight effort input in the workout flow.

- Record **estimated reps in reserve (RIR)** as user-reported, with a clear unknown/not-entered state and an optional "5+" bucket. Do not equate missing to zero.
- Record **failure outcome separately**: stopped voluntarily; estimated no further good rep; attempted next rep and failed; technique limit; stopped because of discomfort/other reason. The final design can simplify these choices but should preserve semantic distinctions.
- RIR 0 does **not** prove an attempted failed rep; an uncompleted rep is different evidence from a judgement of no remaining reps.
- Never backfill measured RIR from a Lite exercise description that merely specified a *target* RIR.
- Minimise friction: optional at set completion, potentially prioritise final working sets or sparse prompts; no forced survey every set.
- Preserve the context of the report, revision/correction identity, laterality and equipment/profile version; avoid assuming self-reported effort is objective truth.
- Use alongside other evidence; do not assume RIR fully determines SetDemand or physiological dose.

## Context episodes and temporal modelling

**Proposal, not implemented:** accessible UI outside a workout for "on holiday", "unwell", "taking a training break" and other appropriately scoped user-reported states. Allow overlap, start/end time, explicit resolution, estimated/unknown dates and corrections. Episode-level observations remain distinct from every workout affected by an episode.

Retrospective context is useful for exploratory modelling; store **observed/effective time** and **authored/recorded time** separately. Do not leak later-entered evidence into historical pre-outcome evaluation.

The current 7E model already uses elapsed time, with a **fixed three-day transient half-life**, and its ContextModules maintain their own learned state/mathematics. ContextModules can publish validated signals under the central protocol, but they do not directly rewrite the Core temporal transition equations. Investigate both: (a) learned context/time associations as additional modules and (b) explicitly versioned challenger transitions that learn different elapsed-time behaviour. Do not pile up fixed illness/holiday penalties as though they were calibrated physiology.

Illness and time-pressure example modules already exist; richer real user input and optional module candidates are future work. Training absence can indicate several different processes; a later model must not assume that holiday, illness and detraining are identical.

## Equipment input before transfer accuracy is established

Add a practical **manual** equipment-entry workflow early. Support model/brand when known, individual machine identity, loading basis, stack/plate increments, base/sled resistance if stated, optional geometry, grip/settings, default versus one-session machine, and per-session/set corrections.

A future optional phone-assisted rail-angle reading should use **gravity/accelerometer/sensor fusion** to estimate static inclination, with guidance for the correct physical surface, broad standard angle categories and manual correction. The phone resting on a backrest does not necessarily measure the sled travel direction.

Persist the measurement as **uncertain equipment metadata** with source and calibration version. Angle, brand or displayed kilograms alone are not a universal physical load conversion; 7F should learn/evaluate where those features improve bounded translation.

## Model inspection and experiment interchange

Plan a versioned **N-BIO Model Snapshot** suitable for advanced use and development:

- Immutable mathematical model/config/solver identity and compatible schema/protocol versions.
- Explicit priors, hyperparameters, learned statistical parameters/posterior state, ContextModule state, and transfer relationships where applicable.
- Data/evidence lineage, training cut-off, evaluation results, supported/unsupported domains and numerical precision.
- Optional canonical evidence *references* or user-approved export, separate from model-only snapshots.

Most N-BIO models have **statistical parameters and posterior beliefs**, not necessarily neural-network weights. Use accurate labels. Export can be inspectable; advanced import/edit must create a **new Lab candidate**, validate ranges/compatibility and never overwrite stable observations or active production beliefs silently. Unknown model revisions fail safely.

The full Native backup, Stable–Lab evidence bridge and model snapshot remain separate contracts.

## Compute and performance: optimise both architecture and hardware when earned

Current numerical inference reference is **Kotlin/JVM/ART**. The repository's Android backend audit specifies solver/sequential reuse first, then measured native/GPU experiments if bottlenecks remain.

- Finish the *functional* Kotlin end-to-end adaptation/evaluation path as the replayable reference.
- Avoid unnecessary full-history fits for one new set/session; use incremental computation and dependency invalidation.
- Instrument kernel time, end-to-end latency, peak memory, accuracy, battery/thermal performance and repeat runs on a real device.
- For a measured arithmetic hotspot, compare Kotlin with C++/NDK compiler-vectorised ARM NEON and, if useful, a **Rust/NEON** challenger. SIMD support alone does not guarantee speed or reduced energy; JNI/FFI overhead and math fidelity matter.
- Consider Vulkan compute only for a proven large, regular GPU-suitable workload with net win after dispatch and transfer overhead.
- Preserve a deterministic, scientifically comparable reference and a way to reject slower or less faithful candidates.
- Do not port the entire N-BIO engine to a native language as a prerequisite for Stable data capture.

## Scope/authority guardrails

All currently quarantined 7C/7D/7E/7F results remain appropriately labelled. An optional tentative recommendation may be useful without claiming certainty, but user control and product-authority changes require the explicit V8 collaboration/research gate. No mandatory exhaustive lab work blocks a *data-safe recorder*, and no dataset size alone closes an empirical investigation.

