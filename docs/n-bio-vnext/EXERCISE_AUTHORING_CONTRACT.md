# External Exercise Authoring Contract

Status: N-BIO-7A.5 contract, exchange format v1.

This document defines the semantic boundary for an exercise proposal authored outside My Mettle by a human, an AI system, or both. It does **not** define a database dump and it does **not** grant biological authority to an external model.

The future product path is:

`external semantic proposal → structural validation → semantic validation → human preview/approval → ExecutionProfileAuthoringRepository`

Schema validity means only that the document has the expected shape. It never means that recruitment coefficients, mechanics, or performance semantics are biologically correct.

Machine-readable assets:

- `exercise-import.schema.json` — structural exchange contract, format version 1.
- `exercise-import-example.json` — canonical cable-lateral-raise example.

## 1. Canonical concepts

### Exercise identity

An **Exercise** is the stable conceptual movement identity: for example, “Cable Lateral Raise”. The exchange file supplies a human-readable `stableConceptKey`; it is a semantic hint for future matching, **not** a Room primary key. My Mettle assigns persistence identity.

### Execution profile

An **Execution Profile** is an equipment/execution variant of an exercise. Examples might distinguish a standing single-arm cable lateral raise from a bilateral machine lateral raise.

### Execution profile version

An **Execution Profile Version** is immutable historical semantics. A materially meaningful change in performance schema, resistance semantics, equipment calibration, ROM, technique, grip/support constraints, mechanics, or recruitment assumptions creates a successor immutable version. Historical observations continue to reference the version under which they were recorded; old rows are never rewritten to make new semantics appear historical.

The exchange field `semanticVersionIntent` communicates whether the proposal describes a new immutable profile or a proposed successor. It does not carry internal profile/version IDs.

## 2. Performance and capability semantics

`metricFamily` uses the current Native domain vocabulary:

- `dynamic_resistance`
- `bodyweight_resistance`
- `loaded_hold`
- `duration_only`
- `repeated_contraction`
- `power_duration`
- `speed_duration`
- `device_ordinal`

A performance schema declares exactly which measurement dimensions make sense for the execution. Available metrics include external load, assistance, repetitions, duration, distance, speed, pace, incline grade, machine level, power, cadence, steps, floors, and elevation gain.

Each metric has a canonical physical dimension and compatible units. Important examples:

- `external_load`, `assistance` → mass (`kg` canonical; `lb` is a compatible display/entry unit).
- `repetitions` → count (`rep`).
- `duration` → time (`s`; `min` compatible).
- `distance`, `elevation_gain` → distance (`m`; `km`/`mi` compatible where relevant).
- `speed` → speed (`m/s`; `km/h`/`mph` compatible).
- `pace` → pace (`s/m`; common pace units are compatible).
- `incline_grade` → grade (`fraction`; `percent` compatible).
- `machine_level` → **ordinal** (`machine_level`), not kilograms.
- `power` → power (`W`).

Minimum, maximum, increment and allowed values are profile-local resolution/calibration semantics. An uncalibrated ordinal machine setting must be represented as `machine_level`; it must not be relabelled as kilograms because a stack happens to show numbered levels.

## 3. Resistance semantics

`resistance.semantics` uses exactly the current Native concepts:

- `external`
- `assistance`
- `bodyweight`
- `bodyweight_plus_external`
- `none`
- `device_ordinal`

The coefficients describe the current deterministic resistance representation and must be coherent with the selected semantics. Examples:

- external resistance: external-load coefficient is positive; bodyweight and assistance coefficients are zero.
- assistance: assistance coefficient is positive and an assistance metric is represented.
- bodyweight: bodyweight coefficient is positive and no fictitious external kilograms are required.
- bodyweight + external: bodyweight and external contributions may both be positive.
- device ordinal: the performance schema uses ordinal machine level rather than a fabricated physical load.

These are measurement semantics. They are not N-BIO-7B capability equations.

## 4. Entry basis and laterality

Entry basis is one of:

- `total`
- `per_hand`
- `per_side`

Laterality mode is one of:

- `bilateral_only`
- `unilateral`
- `alternating_allowed`
- `not_applicable`
- `unknown`

`implementCount` is the number of implements represented by the entry basis where meaningful. Semantic validation checks obvious incompatibilities instead of silently guessing.

## 5. Execution mechanics

Where supported by the current domain, the proposal may describe:

- movement pattern;
- joint actions;
- kinetic-chain context;
- contraction type;
- ROM class;
- technique class;
- resistance-curve class;
- grip/support constraints.

These fields describe the assumptions under which the execution/recruitment proposal applies. They are not a substitute for immutable profile versioning: a later material execution change must eventually be authored as a successor version rather than mutating historical semantics.

## 6. Recruitment: the critical definition

`RecruitmentAllocation.weighting` is an **independent muscle-local exposure coefficient in [0,1] under the stated execution/recruitment model**.

It is **not**:

- a percentage of external load;
- a percentage force contribution;
- EMG percentage;
- activation percentage;
- probability;
- percentage share of the whole exercise;
- a conserved allocation;
- hypertrophy percentage;
- EffectiveDose;
- confidence.

Allocations across muscles **do not need to sum to 1** and must never be normalised to 100%.

A valid example is:

- lateral/acromial deltoid: `0.90`
- supraspinatus: `0.55`
- upper/descending trapezius: `0.45`
- anterior/clavicular deltoid: `0.25`

The total is `2.15`. That is valid because each number is local to its muscle segment.

### Role, weighting and confidence are different

**ROLE** is the qualitative relationship (`prime`, `synergist`, `stabiliser`).

**WEIGHTING** is the muscle-local exposure coefficient.

**CONFIDENCE** is confidence/quality of the recruitment estimate.

Do not derive weighting from role. There is no hidden rule such as “stabiliser = 0.25”, and `prime` does not imply `1.0`.

## 7. Recruitment provenance

Every externally proposed allocation must carry provenance metadata. The exchange format supports:

- author/source type (`human`, `ai`, `mixed`, `unknown`);
- evidence status (`external_evidence`, `reasoning_based_proposal`, `source_unknown`);
- genuine source references where available;
- a concise biomechanical basis for the proposal;
- model/tool identity where known;
- allocation confidence;
- applicable ROM, technique and resistance-curve assumptions.

Do not fabricate academic citations. If no source exists, say `reasoning_based_proposal` or `source_unknown`; an honest uncertainty is preferable to a fake reference.

The `biomechanicalBasis` field is source provenance/rationale. My Mettle does not ask an AI for hidden chain-of-thought and does not persist hidden reasoning.

## 8. Canonical anatomy IDs

`segmentId` must be an existing canonical My Mettle muscle-segment ID from the current anatomy/reference data. Natural-language muscle names are display metadata only and cannot act as canonical keys.

The JSON Schema deliberately validates only the identifier shape. The **semantic validator resolves IDs against the authoritative anatomy data/runtime**, avoiding a manually duplicated schema enum that could drift.

## 9. This format is not a Room dump

External authors must not supply:

- Room primary keys or internal UUIDs;
- lifecycle row IDs;
- timestamps;
- inference IDs;
- database schema versions;
- superseded-row IDs.

My Mettle assigns persistence identity and timestamps after validation/approval. The exchange file describes semantics that can be translated into the existing `ExecutionProfileAuthoringRequest` boundary.

## 10. Validation layers

### Level 1 — structural validation

The document must conform to `exercise-import.schema.json`: expected fields, data types, enums, bounds, and no undeclared Room/internal fields.

### Level 2 — semantic validation

My Mettle must additionally verify facts the structural schema cannot prove, including:

- canonical muscle IDs exist;
- recruitment weighting and confidence are in `[0,1]`;
- metric/default-unit dimensions match;
- no metric is duplicated;
- performance schema is coherent with metric family;
- resistance semantics and metrics agree;
- entry basis/laterality/implement count are coherent;
- uncalibrated ordinal levels are not represented as kilograms;
- execution semantics are sufficiently described for the proposed profile;
- recruitment provenance is present and honest;
- the proposal can map into the current authoring domain without external database IDs.

A warning may be appropriate for low-quality/unknown evidence; missing mandatory provenance structure is a rejection in v1.

### Level 3 — human review

AI-authored biological and execution semantics must be previewed and approved before canonical creation. **Schema-valid never means automatically trusted.**

## 11. Future importer mapping

7A.5 intentionally does not implement a file picker or automatic database write. A future importer should translate a validated proposal into an `ExecutionProfileAuthoringRequest` and then call the existing `ExecutionProfileAuthoringRepository` creation/successor pathway.

The importer must not create a parallel authoring truth, auto-accept recruitment coefficients, or infer internal identifiers from the exchange document.
