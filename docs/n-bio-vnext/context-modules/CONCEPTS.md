# Context Module concepts and boundaries

## The six parts

| Part | What it means | Owner |
|---|---|---|
| Feature definition | The feature's identity, value shape, scope, timing, missingness, allowed reads, and allowed targets | Build-integrated feature registry |
| Feature evidence | A typed observation or missingness state at a known time and scope | Canonical evidence/adaptation path |
| Module | Code that reads approved data and updates one learner | Module implementation |
| Module memory | Versioned derived state kept by that module | Module; persisted by host |
| `ContextSignalV1` | A candidate effect and uncertainty envelope | Module publishes; host validates |
| N-BIO | Validation, arbitration, temporal inference, and authority decisions | Core/host |

These parts stay separate. Changing a learner does not rewrite old evidence. Adding evidence does not give it an automatic effect. A module cannot write Core state.

## Why each module owns memory

Different features need different models. The illness module tracks episodes, persistence, and a location association. The time-pressure module compares residual variance between explicit-present and explicit-false sessions. A universal coefficient row would lose those differences.

Module memory is derived data. The host can delete it and rebuild it by replaying canonical evidence in the same order. A codec gives the state format its own version. If the host cannot read old state safely, it rejects that state instead of guessing.

## Chronology

Module execution has three useful stages.

### Pre-session

The outcome does not exist yet. The module may use only evidence already available at the prediction horizon. A realised residual is forbidden in a pre-session `ContextReadViewV1`.

### Post-session

The outcome now exists. If the module declared and received `REALISED_POST_SESSION_RESIDUAL`, it may update its memory for later predictions.

### Replay

The host repeats the same chronological order from stored evidence. Module instances update serially for one user/replay, and providers are ordered by stable module ID.

A module must not learn from a session result and then claim that learned state was available before that same session.

## Host and module lifecycle

The host owns coroutine lifetime, dispatcher choice, ordering, cancellation, persistence, and the final transaction. `evaluate` is synchronous and must not perform I/O. A module should not create threads, coroutine scopes, or background work.

An ordinary module exception becomes a bounded module failure. The host keeps the previous state, emits no signal from that module, and continues with peers. `CancellationException` remains cancellation and is rethrown.

## Association is not causation

A module may learn that a feature helps predict a performance change. That does not show that the feature caused the change.

Use wording such as:

> Illness has been associated with lower predicted performance for this user.

Do not claim that illness caused an exact performance loss. The current modules are predictive association learners, not causal models.

## Do not collapse uncertainty into confidence

The system keeps several distinct ideas:

| Concept | Question it answers |
|---|---|
| Extraction confidence | How certain was the producer that it found the tag? |
| Evidence support | How many rows, sessions, or episodes support the learner? |
| Posterior variance | How uncertain is the module's estimated relationship? |
| Empirical calibration | Does the model predict fresh human outcomes well? |
| Product authority | May the result change normal workout behaviour? |

`extractorConfidence` is provenance. It does not become a smaller `ContextSignalV1.variance` or stronger biological evidence.

## Missing does not mean false

`ContextEvidenceMissingness` keeps these states separate:

| Value | Meaning |
|---|---|
| `PRESENT` | The feature was observed and the evidence carries a value. |
| `KNOWN_FALSE` | The source explicitly established that the feature is false. |
| `NOT_REPORTED` | The source did not mention the feature. |
| `NOT_MEASURED` | The feature was not measured. |
| `NOT_APPLICABLE` | The feature does not apply to this evidence. |
| `UNKNOWN` | The source cannot establish a usable value. |

If a note does not mention illness, illness is not automatically false. An explicit statement that the user is no longer ill may become `KNOWN_FALSE` when the feature and source contract support that meaning.

Episode modules may keep derived persistence through `NOT_REPORTED`, `NOT_MEASURED`, or `UNKNOWN`. They do not copy a raw positive tag onto later sessions.

## Three status axes

The docs keep status questions separate.

| Axis | Values used here | What it describes |
|---|---|---|
| API stability | STABLE, EXPERIMENTAL, DEPRECATED, INTERNAL | Compatibility promise |
| Capability | IMPLEMENTED, PROTOCOL-ONLY, RESERVED, LATER PHASE, UNSUPPORTED | What the current software can do |
| Scientific | NOT YET EVALUATED, STRUCTURALLY VALIDATED, CALIBRATION PENDING, EMPIRICALLY SUPPORTED, REJECTED | Evidence for the model or claim |

The author-facing v1 SPI is **EXPERIMENTAL**. The 7E structure is **STRUCTURALLY VALIDATED**. Implemented 7E context effects remain **CALIBRATION PENDING** under PD-003. Targets without an effect model are **NOT YET EVALUATED**. All 7E outputs remain SHADOW candidates, and `BENCHMARK_V0` remains normal product authority.

## Security boundary

Modules are build-integrated, reviewed Kotlin code. The SPI limits what the host passes to a module, but it does not place that code in a separate security sandbox. Arbitrary downloaded code, dynamic DEX loading, and a plug-in marketplace are not supported.

See the [authoring guide](./AUTHORING.md) for the workflow and the [reference](./REFERENCE.md) for exact types and fields.
