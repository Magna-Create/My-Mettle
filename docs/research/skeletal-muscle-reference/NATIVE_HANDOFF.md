# My Mettle Native — biological model handoff v0.1

## Status

**Research is sufficiently defined to begin the Native data/domain rewrite.**

**Implementation update:** N-BIO-1 and N-BIO-2 are now combined in the Room v5 biological foundation. N-BIO-3 is the next structural patch.

This does not mean every human muscle has a precise adult-male PCSA. It means the model now knows:

- what the canonical anatomical entities are;
- which subdivisions are independently addressable;
- what evidence is available;
- what may be derived;
- what must remain latent or null;
- how incomplete reference physiology is allowed to behave;
- which existing Native structures conflict with the intended model.

Further physiology research can improve priors without requiring another ontology redesign.

---

# 1. Existing Native structures to preserve conceptually

The current app already has several useful foundations.

### Raw session history

Keep the principle that completed workouts contain immutable historical snapshots.

`SessionEntity`, `SessionExerciseEntity`, `SetRecordEntity`, reflections and reviews remain conceptually valid.

A historical session should preserve what My Mettle prescribed and what the user actually did at that time, even if the recommendation engine changes later.

### Routine versioning

The existing immutable `RoutineVersionEntity` idea remains valuable.

What changes is what a programme version fundamentally contains: targets should become first-class rather than treating a fixed exercise/load list as the programme itself.

### Exercise media and memory

Setup photos, cues, notes, machine settings and similar exercise memory are orthogonal to the biological model and can survive.

### Timer / Android service architecture

Unrelated to this rewrite.

---

# 2. Existing structures to remove or redefine

## `ExerciseEntity.progressionStep`

Remove as a progression concept.

An exercise does not own progression.

If the field is currently being used to represent a physical load increment, that concept should move to an execution/equipment constraint such as:

```text
LoadResolution
- minimumLoad
- maximumLoad?
- increment
- allowedValues?
```

## `ExerciseTargetMuscleEntity`

Remove as canonical muscle targeting.

It currently attaches an ordered free-text muscle list directly to an exercise. This conflates:

- what the exercise recruits;
- what the current programme intends to target.

Those become separate models.

## `ExerciseMuscleLoadEntity`

Replace.

Current shape:

```text
exerciseId
muscle: String
proportion
role
confidence
source
```

New recruitment must address stable `muscleSegmentId` values and belong to an **execution profile**, not necessarily the generic exercise.

## `RoutineSlotEntity.plannedLoad`

Remove from programme truth.

A programme/routine should not assert that an exercise intrinsically uses a particular load.

A load recommendation is produced for a particular session from the user's current state.

## `SessionExerciseEntity.plannedLoad`

Keep conceptually, but rename semantics towards `prescribedLoad`.

This is exactly where a historical recommendation belongs:

> on this session, under this model version, My Mettle prescribed this load.

## `ExperimentEntity`

The existing baseline/proposed-load experiment is tied to exercise-owned progression.

Do not migrate it into the new model unchanged. If experimentation returns later, model it around hypotheses/inference or exercise translation rather than a fixed old/new exercise load comparison.

## Legacy migration machinery

Do not optimise the new biological schema around compatibility with old Legacy/early-Native data.

The app is not yet the workout source of truth. My Mettle Lite Legacy remains the temporary live application and its eventual import can be written once against the final schema.

During this development stage, destructive Room migration is acceptable and preferable to preserving obsolete concepts.

---

# 3. Introduce a real domain layer

Room entities must stop defining the product ontology.

Suggested packages:

```text
dev.kian.mymettle.domain
├── anatomy
├── physiology
├── exercise
├── training
└── performance

dev.kian.mymettle.engine
├── targeting
├── prescription
├── stimulus
├── inference
└── translation
```

UI/repositories consume domain models. Room remains persistence.

---

# 4. Canonical anatomy domain

```kotlin
data class Muscle(
    val id: MuscleId,
    val name: String,
    val region: BodyRegion,
    val unitKind: AnatomicalUnitKind,
    val lateralityModel: LateralityModel,
    val segments: List<MuscleSegment>,
)

data class MuscleSegment(
    val id: MuscleSegmentId,
    val muscleId: MuscleId,
    val name: String,
    val type: SegmentType,
    val anatomicalStatus: AnatomicalStatus,
    val statePolicy: SegmentStatePolicy,
)
```

IDs are stable typed wrappers in the domain even if persisted as strings.

Every ordinary whole muscle resolves to one addressable whole-muscle segment unless an override replaces it with children.

For segmented muscles, the parent does not also acquire a competing independently progressing whole-muscle segment.

---

# 5. Reference physiology domain

Research evidence itself can remain in `docs/research` and offline tooling.

The runtime app only needs the **selected versioned reference profile** plus enough provenance to identify how a prior was produced.

Suggested domain objects:

```kotlin
data class ReferenceProfile(
    val id: ReferenceProfileId,
    val version: Int,
    val population: ReferencePopulation,
    val priors: Map<MuscleSegmentId, ReferencePhysiologyPrior>,
)

data class ReferencePhysiologyPrior(
    val segmentId: MuscleSegmentId,
    val volumeCm3: Estimate<Double>?,
    val optimalFibreLengthMm: Estimate<Double>?,
    val pennationDeg: Estimate<Double>?,
    val geometricPcsaCm2: Estimate<Double>?,
    val effectivePcsaCm2: Estimate<Double>?,
    val structuralCapacityIndex: Estimate<Double>?,
    val absoluteSharePolicy: AbsoluteSharePolicy,
)
```

`Estimate<T>` should carry model metadata, not user-facing warning copy:

```kotlin
data class Estimate<T>(
    val value: T,
    val uncertainty: Double?,
    val sourceKind: EstimateSourceKind,
    val modelVersion: String?,
)
```

Do not require every field to be non-null.

---

# 6. Parent-latent segment representation

Segments such as pectoralis-major parts must be possible even when their adult-male absolute volume shares are unknown.

Suggested policy:

```kotlin
sealed interface AbsoluteSharePolicy {
    data class Known(val fraction: Double) : AbsoluteSharePolicy
    data class StructuralPrior(val fraction: Double, val uncertainty: Double) : AbsoluteSharePolicy
    data object Latent : AbsoluteSharePolicy
}
```

For `Latent` segments:

- development is independently tracked;
- target intent can address the segment;
- recruitment can address the segment;
- the parent retains the absolute structural prior;
- the model does not invent a 1/N share merely to populate a database field.

This is central to making the research model implementable.

---

# 7. Exercise domain

Separate fundamental movement from execution.

```kotlin
data class Exercise(
    val id: ExerciseId,
    val name: String,
    val archived: Boolean,
    val trackingMetric: TrackingMetric,
    val memory: ExerciseMemory,
    val executionProfiles: List<ExecutionProfile>,
)

data class ExecutionProfile(
    val id: ExecutionProfileId,
    val exerciseId: ExerciseId,
    val name: String,
    val equipment: EquipmentProfile,
    val loadResolution: LoadResolution?,
    val recruitment: RecruitmentProfile,
)
```

A new profile does not need a manually authored progression scheme.

---

# 8. Recruitment domain

```kotlin
data class RecruitmentProfile(
    val allocations: List<RecruitmentAllocation>,
)

data class RecruitmentAllocation(
    val segmentId: MuscleSegmentId,
    val role: RecruitmentRole,
    val weighting: Double,
    val confidence: Double,
    val source: RecruitmentSource?,
)

enum class RecruitmentRole {
    PRIME,
    SYNERGIST,
    STABILISER,
}
```

The exact semantics/normalisation of `weighting` will be researched separately during the exercise-recruitment pass.

Do not force prime/synergist/stabiliser values to sum together unless the eventual recruitment model explicitly defines them that way.

---

# 9. Target domain

Targets exist independently of exercises.

```kotlin
data class TrainingTarget(
    val id: TrainingTargetId,
    val segmentId: MuscleSegmentId,
    val priority: Double,
    val desiredStimulus: Double?,
    val source: TargetSource,
)
```

A programme can contain targets before exercise selection.

A resolved session then records which exercise prescriptions were selected to satisfy them.

---

# 10. Programme versus resolved workout

Long-term representation should conceptually separate:

```text
PROGRAMME INTENT
muscle targets + priorities + constraints
        ↓
SESSION RESOLUTION
exercise selection + execution profile + dose + load
        ↓
HISTORICAL SESSION SNAPSHOT
what was actually prescribed
```

Existing routine exercise slots can temporarily survive as user preferences or pinned assignments while the UX is being redesigned, but they should not remain the only source of programme meaning.

Workout modes should eventually operate as **session constraints/budgets**, not fixed Legacy A/B/C set anchors.

For example, a reduced mode can lower total target dose and omit low-priority targets/exercises rather than visiting every machine for one set.

---

# 11. Prescription domain

```kotlin
data class ExercisePrescription(
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val targetIds: List<TrainingTargetId>,
    val sets: Int,
    val repRange: IntRange,
    val targetRir: Double?,
    val prescribedLoad: Double?,
    val restSeconds: Int,
    val generatedByModelVersion: String,
)
```

This object is resolved from body state + targets + constraints.

It is then snapshotted into the session.

---

# 12. Raw performance evidence

`SetRecordEntity` needs to become rich enough to support inference.

At minimum the next schema should anticipate:

```text
load
reps
duration / distance where applicable
RIR / effort estimate
warm-up / working-set type
completion time
notes
```

RIR is particularly important for translating a performed set into capability evidence. It should not be inferred solely from whether the rep target was met.

Raw performance stays immutable historical evidence even when inference models change.

---

# 13. User muscle state

Do not use one `headPoints` number for everything.

Suggested first domain state:

```kotlin
data class UserMuscleState(
    val segmentId: MuscleSegmentId,
    val side: BodySide,
    val developmentIndex: Estimate<Double>,
    val volumeScale: Estimate<Double>?,
    val structuralCapacityScale: Estimate<Double>?,
    val recentStimulus: Estimate<Double>?,
    val recovery: Estimate<Double>?,
    val updatedAt: Instant,
    val inferenceModelVersion: String,
)
```

The first implementation does **not** need to dynamically infer every field.

The schema should merely keep them separable.

---

# 14. Derived event/state storage

Raw history and derived interpretation need different persistence lifecycles.

Recommended entities/concepts:

```text
InferenceRun
- id
- modelVersion
- referenceProfileVersion
- recruitmentModelVersion
- calculatedAt
- evidenceThrough

MuscleStateSnapshot
- inferenceRunId
- segmentId
- side
- developmentIndex
- volumeScale?
- structuralCapacityScale?
- recentStimulus?
- recovery?
- uncertainty...

StimulusEstimate
- inferenceRunId
- sessionExerciseId / setId
- segmentId
- estimatedStimulus
- role
- confidence

ExerciseTranslationState
- executionProfileId
- user-specific translation parameters
- uncertainty
- sampleCount
- updatedAt
```

When an inference model changes, derived tables may be discarded/rebuilt from raw history.

---

# 15. Exercise translation

This is what prevents a new exercise from becoming a blank slate.

Conceptually:

```text
current muscle state
+ execution recruitment
+ mechanically/similarly related exercise history
+ user exercise-translation state
+ current recovery/fatigue
        ↓
prescribed performance target
```

After the set/session:

```text
actual performance
    ↓
update global muscle state
+ update exercise-specific translation
```

The implementation should expose this as an engine boundary rather than embedding it in a repository.

Suggested interfaces:

```kotlin
interface TargetResolver
interface ExerciseSelector
interface PrescriptionEngine
interface StimulusEstimator
interface MuscleStateUpdater
interface ExerciseTranslationModel
```

Initial implementations can be simple and replaced later.

---

# 16. Persistence proposal

The exact Room names can change, but the next schema should approximately contain:

### Canonical/reference

```text
muscle
muscle_segment
reference_profile
reference_physiology_prior
```

### Exercise

```text
exercise
exercise_execution_profile
recruitment_allocation
exercise_memory
exercise_setup_media
...
```

### Programme/targets

```text
programme_version / routine_version
programme_target
optional exercise_assignment / pinned exercise preference
mode/session constraint configuration
```

### Session raw evidence

```text
session
session_target
session_exercise
session_exercise_target
set_record
exercise_reflection
session_review
```

### Derived user model

```text
inference_run
muscle_state_snapshot
stimulus_estimate
exercise_translation_state
```

---

# 17. Static reference seeding

The research folder is not itself runtime data.

Create a deliberately selected runtime seed from:

- canonical anatomy;
- segment policies;
- `healthy_adult_male_v0_1` morphology;
- selected architecture/model priors;
- equation/model version IDs.

Suggested source-controlled runtime form:

```text
app/src/main/assets/reference/
├── anatomy_v1.json
├── reference_profile_healthy_adult_male_v1.json
└── reference_manifest.json
```

Room can seed these records on database creation.

This gives both:

- inspectable/versioned source assets;
- relational query performance once loaded.

Do not ship the entire literature-evidence CSV corpus into the consumer runtime unless Labs later needs it.

---

# 18. Development-stage Room strategy

Current database is version 4 and still carries obsolete Legacy-oriented entities.

Because Native is not yet the live workout record, prefer a **destructive development migration** for the biological-model rewrite rather than spending effort maintaining backwards compatibility with pre-use schemas.

Practical first patch:

1. introduce the domain models;
2. create the replacement Room entities;
3. bump database version;
4. allow destructive migration during this development period;
5. seed canonical anatomy/reference assets;
6. adapt Library/Workout repositories to domain models;
7. only later remove destructive fallback when Native becomes an authoritative workout store.

The final Lite-Legacy import is a separate one-off translator written against the stable schema.

---

# 19. Current code coupling that should be broken

The current `LibraryExercise` directly exposes `ExerciseEntity`, `ExerciseMemoryEntity` and `ExerciseSetupMediaEntity`.

The new library repository should map Room rows into domain `Exercise` objects.

Likewise workout code should stop moving persistence entities through planning/UI layers.

This is the right moment to create that boundary because the data ontology is changing anyway.

---

# 20. Performance rules

The biological model should not make the workout UI computationally heavy.

- Canonical anatomy/reference priors are tiny and can be cached in memory.
- Current `UserMuscleState` is persisted/cached; UI should not replay full history to render a screen.
- Normal set completion performs only incremental inference/update work.
- Full-history recalculation occurs explicitly after model-version changes or maintenance tasks and can run as visible background work.
- Expensive analytical recomputation should never be silently triggered by ordinary navigation.
- Reference evidence research files do not belong in hot runtime queries.

---

# 21. What remains deliberately unresolved before the first Native patch

These are **not architecture blockers**:

- exact first-generation stimulus formula;
- exact development-index update equation;
- exact recovery model;
- exact recruitment-weight normalisation;
- exact exercise-transfer equation;
- exact target-dose units;
- precise quantitative segment shares for several tracked muscles;
- programme UX and whether target-first planning is exposed directly to the user.

The structures above are designed so all of those can iterate independently.

---

# 22. Recommended implementation sequence

### Patch N-BIO-1 — domain/reference foundation

- domain anatomy IDs/models/enums;
- runtime anatomy/reference assets;
- Room `muscle`, `muscle_segment`, `reference_profile`, `reference_physiology_prior`;
- seed and validation tests.

### Patch N-BIO-2 — exercise/recruitment rewrite

- `ExecutionProfile`;
- recruitment allocations by `muscleSegmentId`;
- remove free-text exercise target muscles;
- remove exercise-owned progression;
- preserve media/memory.

### Patch N-BIO-3 — target/prescription split

- first-class training/session targets;
- session snapshot of generated prescription;
- remove `RoutineSlot.plannedLoad` as programme truth;
- add RIR-ready performance fields.

### Patch N-BIO-4 — user-state/inference scaffold

- inference run;
- muscle-state snapshot;
- stimulus-estimate storage;
- exercise-translation state;
- simple replaceable v0 engine implementations.

### Patch N-BIO-5 — programme/mode resolver

- reinterpret routine/programme around target intent;
- treat workout modes as target/dose/time constraints;
- exercise selection becomes a resolution step rather than the programme's only identity.

At that point the UI can progressively move onto the new model without waiting for a perfect Labs algorithm.

---

# Handoff conclusion

The model is now sufficiently constrained to begin **N-BIO-1** without making further fundamental assumptions about muscle biology.

Additional research should improve priors and recruitment data in parallel. It should no longer hold the foundational Native rewrite hostage.
