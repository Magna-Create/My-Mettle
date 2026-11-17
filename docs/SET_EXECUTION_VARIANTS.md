# Per-set execution variants: multi-stage exercises

> **Status: newly agreed Stable cutover requirement; NOT IMPLEMENTED.** 17 November 2026.
>
> This is an evidence-integrity feature, not a request to build a comprehensive movement-analysis system. It must be available in Stable before relying on progression-sensitive exercises as model evidence.
>
> Related: [Native Stable cutover](./NATIVE_STABLE_CUTOVER.md), [N-BIO evidence experiments](./n-bio-vnext/PROSPECTIVE_EVIDENCE_AND_EXPERIMENTS.md), [product design gates](./n-bio-vnext/PRODUCT_ROADMAP_GATES.md), [Stable–Lab evidence bridge](./STABLE_LAB_EVIDENCE_BRIDGE.md).

## User problem

Some exercises change physical difficulty and performance meaning according to the **execution variation used for a given set**, even though the exercise name and apparatus stay the same. Example: a dragon flag may be performed with both knees bent, a partially extended lever, bent legs on the ascent and straight legs on the descent, or straight legs both ways.

Recording simply "dragon flag — 8 reps" hides an important change in the motion/lever and can corrupt trend comparisons or N-BIO model inputs. Equipment identity, external load, and RIR alone cannot correct this.

**The central requirement: make actual set execution explicit, quickly selectable and historically stable.**

## Minimal Stable UI

- For exercises with configured movement variants, show a compact **Variation** selector on or beside each set entry; exercises without relevant variants remain unchanged.
- Start with a few named, exercise-specific **presets**. For example, dragon flag: `fully_bent`, `half_bent`, `bent_up_straight_down`, `straight_leg`. These labels are illustrative; the exact definitions/IDs and the user's real execution must be reviewed.
- Default a new set to the *previous set's selected variant* for ease of logging, while keeping prescribed/expected variation distinct from **what was actually performed**. Do not silently auto-fill a completed observation when the actual variation has never been confirmed.
- Allow a user-defined custom variation with a short explanation, initially without requiring a physics/lever model.
- Allow a **mixed/changed-within-set** selection and optional short breakdown. Do **not** mislabel every rep in a set as one variation if the person switched partway through. If no breakdown is supplied, store "mixed — details unknown" and withhold precise per-variant attribution.
- Keep the interaction fast; do not demand per-repetition tagging, video recording, force measurement or a detailed biomechanical editor for first Stable.
- Support correcting a recorded variant later with provenance rather than rewriting what the original set initially stated.

### Illustrative set entry

```text
Dragon Flag — Set 2
Reps: 6      Effort: 1 RIR
Variation: [ Bent up / straight down  v ]
                Fully bent
                Half bent
              ✓ Bent up / straight down
                Straight leg
                Mixed / changed during set
                Custom…
```

The set can also store ordinary details such as RIR, failure and equipment independently.

## Domain and persistence semantics

The precise Room schema must be designed against the current implementation, not assumed by this document.

**Existing foundation (verified 17 November):** `PerformanceObservation` and `SetObservationEntity` already carry `executionProfileVersionId` per observation; the model supports superseding/correcting observations. This is useful but does **not** automatically encode all asymmetric or mixed-phase movements.

Proposed layered semantics:

1. **Exercise identity:** the general named movement (dragon flag).
2. **Execution variation/preset definition:** stable ID and version, user-facing title, optional structured parameters (e.g. lever position during ascent and descent, ROM class, assistance) and semantic comparability rules. Define only meaningful dimensions.
3. **Performed set evidence:** reference to the exact immutable preset/version or a clearly marked custom/mixed/unknown execution, with user-confirmed source and correction history.
4. **Evidence granularity:** when a set has several distinct variants, represent an optional ordered count/phase breakdown if known, with total count validation; otherwise preserve mixed/unknown without inventing a homogeneous set.
5. **Profile relationship:** a variation that changes capability-defining lever/ROM or movement semantics may require an appropriate existing/new `ExecutionProfileVersion`. Do not create a new full profile on every set, and do not treat cosmetic differences as separate capability domains. Map presets to profiles explicitly where useful.

An **asymmetric concentric/eccentric variation** (e.g. bent legs going up and straight legs lowering) is a *single defined variation* with two different phase parameters; it is not necessarily "mixed within a set". By contrast, changing from fully bent repetitions to straight-leg repetitions partway through the set is mixed execution and may require a count breakdown.

Unknown means **unknown**. A prescribed variation or an old exercise name/description is not proof of what was performed.

### N-BIO and validation implications

- Avoid mixing unconverted variant-specific performance into one strength/progression curve, predicting a straight-leg set directly from bent-knee history, or awarding progression solely because reps remained equal while leverage changed.
- If matching evidence is sparse, use a versioned tentative transfer model with wider uncertainty, a simple fallback, or no estimate; do not create universal difficulty multipliers from arbitrary stage numbers.
- Preserve raw reps/load/effort **and** variation identity so current V7 and future V8 models can compare like with like and learn whether cross-variant predictions work.
- The Stable–Lab evidence contract must include variant definition/version and actual per-set selection, and optional within-set breakdown/corrections. Lab cannot reinterpret an unknown stage as a known one.

## Lite → Native historical migration

Review old recorded stage/technique descriptions **manually** in the separate one-time migration workstream. Do not auto-assign a stage merely from the exercise title, suggested targets or a note that may describe technique intention rather than observed execution. If a genuine past set's variation cannot be reconstructed, mark unknown, while retaining original completed repetitions and original notes.

No personal training histories or derived user data should be committed to the public repository.

## Acceptance criteria

1. User can choose and save a variation for each relevant completed set without navigating away from the workout.
2. Set 1 and set 2 can use different variations within the same exercise/session; each survives app restart, backup/restore and historical viewing.
3. A defined bent-up/straight-down variation is represented as distinct from both fully bent and straight-leg variants.
4. An explicitly mixed set is not silently converted to a homogeneous stage; unknown/omitted details remain unknown.
5. Updating a preset's definition does not retrospectively change the meaning of old observations; later correction is explicit and auditable.
6. Current summary/progression views do not silently combine fundamentally incomparable variants into an unqualified trend.
7. Stable–Lab export includes enough variant identity/semantics for accurate read-only replay, including corrections.
8. Minimal UI remains usable without detailed motion tracking or new N-BIO inference algorithms.

## Deferred, not required for the first release

Rep-by-rep form tracking, automatic vision classification, biomechanics simulation, learned cross-stage equivalence, motor-sensor estimation and an exhaustive user-facing exercise taxonomy are later experiments.

**Design follow-up:** review the actual selector and preset semantics with the user before coding; this document establishes the data requirement and minimum behaviour, not an approved Figma layout.
