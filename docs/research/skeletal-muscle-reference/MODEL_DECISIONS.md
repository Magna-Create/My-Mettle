# Current Model Decisions for My Mettle Native

These are implementation-facing decisions derived from the two Deep Research reports and the My Mettle design discussion. They are deliberately separated from the raw research so later evidence can change a decision without rewriting history.

## 1. Progress belongs to the person, not the exercise

The foundational state is the user's underlying muscular state. Exercises are interventions and observations applied to that state.

A new exercise should therefore be able to receive a predicted session prescription from existing user state rather than starting from an empty progression history.

Exercise-specific calibration still exists, but it is a translation layer, not the ownership location of muscular progress.

## 2. Canonical anatomy

Conceptual domain objects:

```text
Region
  -> MuscleGroup (many-to-many classification)
      -> CanonicalMuscle
          -> MuscleSegment [0..n addressable segments]
```

`CanonicalMuscle` is the primary anatomical identity.

A segment records both:

- `segmentType`: `HEAD | PART | FIBRE_REGION | WHOLE_MUSCLE`
- `anatomicalStatus`: formal head / formal part / experimentally useful region / whole formal muscle / informal-only where retained as metadata

A parent muscle with independently addressable segments should **not** also maintain an independent competing whole-muscle development state. Parent state is derived from children.

## 3. Groups are not identities

"Hamstrings", "rotator cuff", "hip flexors", "chest" and similar group concepts remain relational classifications. A muscle can belong to multiple groups without duplication.

## 4. Laterality

Reference anatomy is normally shared left/right.

User muscle/segment state carries `side = LEFT | RIGHT` where bilateral independence is useful.

## 5. Repeated muscle series

The ontology must be able to represent repeated axial structures without materialising every vertebral/intercostal level as a trainable state in v1.

Provision for concepts such as `muscleSeriesId`, `instancePattern` and optional anatomical level should remain possible.

## 6. Evidence is separate from the selected reference

Do not store only:

```text
biceps_brachii.referenceVolume = 203.63
```

Instead retain study-level evidence and make the chosen reference a reproducible selection/derivation.

Conceptually:

```text
ReferencePhysiologyEvidence
  source
  entity
  population
  method
  context
  variable
  value + uncertainty
  valueSource
  availability

ReferenceSelection
  target reference population/version
  selected evidence / derivation
  equation/version
```

This allows the reference atlas to improve without destroying the underlying evidence record.

## 7. Evidence quality is multidimensional

Canonical evidence metadata should support at least:

- `measurementTier`
- `populationCompatibility`
- `entityCompatibility`
- `methodCompatibility`
- `valueSource`
- `availabilityStatus`
- `uncertainty`

A convenience quality score/tier may be derived later, but should not replace these fields.

## 8. Morphology and architecture variables

Keep conceptually distinct:

- `volumeCm3`
- directly measured mass only when useful
- `measuredFascicleLengthMm`
- `optimalFibreLengthMm`
- `sarcomereNormalisedFibreLengthMm`
- `pennationDeg` with context
- source-native PCSA definitions
- derived geometric/effective PCSA

Mass derived from volume × assumed density is a derived value, not new evidence.

## 9. Force-capacity representation

Do not implement an opaque `2DScalingFactor` as biological ground truth.

The provisional structural path is:

```text
volume + compatible architecture
        -> definition-tagged geometric PCSA
        -> optional/equation-tagged effective PCSA
        -> normalised referenceForceCapacityIndex
```

`V^(2/3)` may exist only as an explicitly low-confidence geometric-similarity fallback when architecture is absent.

Do not canonicalise exact Newton-valued muscle force in v1.

Do not canonicalise a universal specific-tension constant in v1.

## 10. Reference physiology is a prior, not user anatomy

Reference volume, fibre length, pennation and PCSA describe a reference population/model.

They do not get mutated when the user trains.

Conceptually:

```text
expected personal baseline
  = reference morphology
  × personal skeletal/morphological scale

current user state
  = expected baseline
  + acquired development / adaptation state
```

The exact mathematics remain open.

## 11. User muscle state

The schema should permit, even if v1 only actively models a subset:

```text
UserMuscleSegmentState
  userId
  segmentId
  side
  estimatedVolume
  estimatedPcsa
  developmentIndex
  recentStimulus
  recoveryState
  architectureEstimate?        // later
  modelUncertainty
  modelVersion
  updatedAt
```

Current fascicle length and pennation should be possible future dynamic variables rather than immutable identity fields.

## 12. Exercise identity is separate from execution

```text
Exercise
  -> ExecutionProfile
      -> RecruitmentProfile
          -> MuscleSegment allocation(s)
```

The same fundamental movement may have execution variants with different recruitment/mechanics without needing unrelated exercise identities.

This layer requires a separate dedicated research pass; the current skeletal-muscle research does not provide exercise recruitment coefficients.

## 13. Target intent is separate from recruitment

An exercise's recruitment profile describes what that execution is expected to load.

A workout target describes what the programme/user intends to stimulate.

Targets should therefore exist independently from exercises and be resolvable into exercise prescriptions.

Conceptually:

```text
User muscle state + goals + recovery
        -> target needs
        -> exercise/variant selection
        -> load/reps/sets prescription
        -> performed work
        -> stimulus inference
        -> updated user muscle state
```

## 14. Raw workout evidence remains authoritative

Sets, reps, load, reflection data and session context should remain durable raw evidence. Native v0
deliberately excludes RIR/subjective reserve ratings: their expected value does not justify the
noise they add to the evidence model.

Derived stimulus and muscle-state estimates must be versioned/recomputable. If the progression model changes later, historical sessions should be capable of being reinterpreted rather than permanently encoding an early formula.

## 15. Legacy continuity is not a design constraint right now

Native is still a fresh rewrite and is not yet the active workout system. My Mettle Lite Legacy remains the temporary workout source.

Therefore the Native schema may be changed aggressively where it improves the long-term model. A one-off manual/assisted translation from Lite Legacy into the final Native structure can be written when Native reaches a usable stage.

Do not preserve a weak Native structure merely to maintain compatibility with pre-use development data.

## 16. Current research-data rule

The production anatomy/reference dataset must be rebuilt deliberately from the surviving research and primary evidence. The vanished Deep Research spreadsheets are not an API or historical contract.

Incomplete physiology is valid. `null + provenance + availabilityStatus` is preferable to an invented value.

## 17. Immediate implementation implication

Before implementing the progression engine, Native should gain a proper domain layer that separates:

- anatomy/reference physiology;
- exercise/recruitment;
- training targets/prescriptions;
- user muscle state;
- raw performance evidence;
- derived inference/engine state;
- Room persistence entities.

The exact Room schema should be designed after the production canonical anatomy/evidence tables are reconstructed, not before.
