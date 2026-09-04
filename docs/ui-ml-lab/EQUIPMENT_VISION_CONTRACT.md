# Equipment vision contract

> **Status:** LAB-0 design contract only. No OCR, segmentation, VLM runtime, camera workflow or equipment persistence is implemented here.

## Processing boundary

Future equipment understanding must preserve the stages that produced a fact:

```text
IMAGE
  ↓
OCR / visual OBSERVATIONS
  ↓
semantic INTERPRETATION
  ↓
typed candidate facts
  ↓
deterministic DERIVATION
  ↓
user VALIDATION
  ↓
CONFIRMED / CORRECTED canonical equipment data
```

Do not collapse these stages into “AI result”. Provenance and correction depend on knowing whether a value was observed, interpreted, derived or explicitly approved.

## Evidence classes

### OBSERVED

Direct image/OCR evidence, for example:

- text `STARTING RESISTANCE 7.5 KG` read from a placard;
- visible stack labels;
- a photographed manufacturer/model name;
- plate denominations visible in an image.

An OCR observation may be wrong. Preserve the raw/normalised observation distinctly from later meaning.

### INTERPRETED

Semantic meaning assigned to observations, for example:

- deciding that `7.5 kg` is the machine's starting resistance rather than one selectable stack increment;
- associating a placard field with left/right loading semantics;
- identifying a loading-system family from observed machine features.

Interpretation is a typed candidate, not canonical truth.

### DERIVED

Deterministic results computed from confirmed/candidate inputs where practical, for example:

- deriving the selectable-load sequence from a known starting load and increment pattern;
- checking whether a reported sequence is internally consistent;
- expanding fixed increments into a feasible-load set.

Prefer deterministic Kotlin/domain calculations over asking a VLM to repeatedly perform arithmetic or enumerable transformations.

### CONFIRMED / CORRECTED

The user-reviewed result that is eligible to become canonical equipment data under the shared equipment contract.

A correction outranks machine interpretation. Preserve enough provenance to know what the machine suggested and what the user approved/corrected when that history is useful.

## Canonical-truth rules

- AI interpretation is never silently canonical truth.
- User correction outranks machine interpretation.
- Deterministic calculations stay outside the VLM where practical.
- `unknown` is a valid output. Do not force a candidate merely to complete a form.
- Raw OCR and semantic interpretation remain distinguishable.
- Experimental benchmark/debug artefacts do not automatically belong in canonical Room storage.
- LAB-6 validates representative real gym images before polished Semi-auto depends on the pipeline.
- Historical equipment meaning must not be retroactively rewritten merely because a later interpretation/model improves.

## Future loading/resistance branches

The pipeline and later UX must be able to branch conceptually by loading/resistance system rather than pretending all equipment is a weight stack:

- selectorised stack;
- plate-loaded;
- free-weight implement;
- fixed increments;
- cable/pulley;
- assisted/counterweighted;
- other.

These labels are **not UX-final terminology**. LAB-6/LAB-7 may refine the taxonomy after real-image and workflow evidence.

## Candidate fact shape

Exact types are deferred, but a useful future candidate fact should be able to carry equivalents of:

```text
field / semantic target
value or unknown
unit where applicable
observation source(s)
interpretation provenance
quality/ambiguity state
user confirmation/correction state
```

Do not use a universal scalar “confidence” as a substitute for provenance, ambiguity and review state.

## Relationship to canonical equipment domain

The vision pipeline is an input path into the future shared equipment domain; it is not the owner of that domain.

Canonical persistence is deferred to LAB-5 and should support concepts equivalent to:

- `EquipmentModel`;
- `EquipmentInstance`;
- `EquipmentCalibrationVersion`;
- session/default equipment binding semantics;
- optional gym/location association;
- provenance and calibrated/uncalibrated state.

Exact names follow the future cross-branch source/schema gate.

## LAB-6 validation rule

LAB-6 should test the pipeline against real gym images covering the loading/resistance branches relevant to My Mettle. Measure failure modes as well as successes, especially:

- unreadable/partial placards;
- ambiguous units;
- multiple numbers with different meanings;
- manufacturer marketing text versus mechanical specification;
- occlusion/glare/perspective;
- missing starting resistance;
- unconventional increments or per-side semantics;
- equipment that cannot be identified reliably.

A polished Semi-auto flow in LAB-7 must not depend on a pipeline that has only been demonstrated on curated screenshots or hand-selected easy images.

## LAB-0 exclusions

LAB-0 adds no:

- OCR dependency;
- image-segmentation dependency;
- VLM runtime;
- equipment entity/table/migration;
- benchmark table;
- camera/equipment capture flow;
- networking or super-library upload path.
