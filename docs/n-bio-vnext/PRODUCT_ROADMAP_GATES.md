# N-BIO vNext — Product, Research & Collaboration Gates

> **Status:** authoritative additive supplement to [`PLAN.md`](./PLAN.md) for product-facing decisions, research gates, equipment intelligence, uncertainty communication, N-BIO-8 design, N-BIO-9 recomputation UX and Native database-safety behaviour.
>
> `PLAN.md` remains the overarching architecture/phase contract and `CORE_MODEL_DETAIL.md` remains the normative mathematical supplement. This document adds requirements that must be consulted before the relevant later phases are implemented. Where a conflict exists, stop and resolve it explicitly rather than silently choosing one interpretation.
>
> **Human collaboration rule:** when a gate below says `COLLABORATION REQUIRED`, the implementation agent must stop before behaviour-driving implementation, explicitly remind Kian what was previously agreed, summarise the unresolved decisions, and ask whether he wants to research, prototype or design the flow in Figma before proceeding. Do not silently choose a UX/product policy merely because code can be written.

---

# 1. Why this supplement exists

N-BIO-6 and N-BIO-7 are primarily evidence/inference foundations. From late N-BIO-7 onward, however, several decisions become inseparable from user experience, user intent, equipment context and product policy.

The failure mode to avoid is:

```text
backend model becomes capable of X
→ agent assumes a product behaviour for X
→ UX is implemented without user collaboration/research
→ the assumption becomes difficult to unwind later
```

The required pattern is:

```text
backend capability
→ explicit product/research gate
→ user intent + UX/design decisions
→ behaviour-driving implementation
→ validation
```

Future agents must treat these gates as part of the plan, not optional reminders.

---

# 2. General collaboration protocol

## 2.1 When to stop and involve Kian

`COLLABORATION REQUIRED` applies whenever work would decide any of the following without an already-approved contract:

- what a user sees when model uncertainty is high;
- whether the app changes a workout automatically or only suggests changes;
- what may change mid-workout;
- how programme priorities/goals are expressed;
- how exercises are added/removed/reallocated at programme level;
- how equipment identity/calibration is selected or corrected;
- how a new machine is treated for one session versus future sessions;
- how model upgrades/recomputation are surfaced;
- how destructive/incompatible database states are communicated;
- any new multi-step user workflow where an unreviewed backend assumption would materially constrain later UX.

At these points the agent must explicitly say, in substance:

> We previously agreed this behaviour needs research/design rather than silent implementation. Before I build it, do you want to research the decision, sketch the UX flow, or design the relevant screen/state in Figma?

The wording need not be identical, but the gate must be explicit.

## 2.2 Figma/design probe

When a phase requires a new interaction flow or materially changes an existing workout/programme flow, the agent should proactively probe whether Kian wants to:

- design the state/flow in Figma first;
- provide an existing Figma node;
- define interaction rules in conversation before implementation;
- deliberately accept a temporary developer-only UI while backend work proceeds.

Do not assume that a backend-complete state implies the normal-user UX should immediately be implemented.

## 2.3 Research probe

When a decision is scientifically/product underdetermined, remind Kian if prior discussion identified a research pass.

In particular:

- **before N-BIO-8 behaviour-driving implementation, a dedicated deep research/design pass is required**;
- equipment-mechanics/translation assumptions should be researched before they become numerical equivalence rules;
- adaptive mid-workout behaviour requires both training-science scrutiny and UX/product scrutiny;
- current Android/ML Kit/Health Connect platform facts must be rechecked at implementation time rather than copied from old prompts.

Negative research results are valid. Do not force complexity merely because the architecture can support it.

---

# 3. Uncertain predictions are not automatically useless predictions

The existing system invariant remains:

> Never fabricate precision or silently present a weak estimate as authoritative truth.

However, **high uncertainty does not imply that every useful estimate must become `null`.**

A later translation/prescription presentation layer should distinguish at least conceptually between:

```text
STRONG / DIRECTLY SUPPORTED
→ may be suitable for normal pre-fill / normal prescription presentation

USEFUL BUT UNCERTAIN
→ present as a suggestion/range with language that communicates tentativeness

ROUGH STARTING POINT
→ "you could try …" / exploratory suggestion semantics

NO MEANINGFUL BASIS
→ genuinely blank / no useful estimate yet
```

The exact thresholds and wording are product policy and must be versioned/validated where behaviour-driving.

Do not reduce uncertainty communication to a meaningless numeric confidence badge such as `Confidence: 0.41` in normal UX.

Prefer semantic communication such as:

- `Suggested: 60–65 kg`
- `Around 60 kg should be a reasonable starting point`
- `You could try ~55 kg`

The model may retain full posterior/provenance internally while the UI chooses human-readable phrasing.

## 3.1 Future input-box behaviour

A likely later UX distinction is:

- well-supported prescription → normal value may be pre-filled;
- weaker suggestion → suggested value/range appears in the suggestion/help region of the input rather than masquerading as the user's prescribed/entered value.

`COLLABORATION REQUIRED`: before implementing this presentation behaviour, remind Kian to review/design the input-box states in Figma or explicitly approve the interaction contract.

---

# 4. Equipment intelligence is a modelling input, not only a camera feature

## 4.1 Required late-N-BIO-7 backend hook

Before/during N-BIO-7F cross-profile translation, the backend must be able to represent the equipment actually responsible for evidence and prediction uncertainty, even if camera scanning/setup UX is not yet implemented.

Conceptual entities/boundaries should support equivalents of:

```text
EquipmentModel
EquipmentInstance
EquipmentCalibrationVersion
SessionEquipmentBinding
```

Exact names may differ after source audit.

### EquipmentModel

May describe known manufacturer/model/family/mechanism semantics, for example:

- manufacturer;
- product/model family;
- machine/external-resistance mechanism;
- display-load semantics;
- known calibration metadata;
- relevant pulley/leverage/resistance-curve information where genuinely known.

### EquipmentInstance

Represents the actual machine/implement context a user regularly encounters, potentially associated with a gym/location without making location mandatory.

It may retain:

- confirmed/candidate equipment-model identity;
- user-facing label;
- local equipment-specific configuration;
- preferred/default relationship to an execution profile;
- provenance.

### EquipmentCalibrationVersion

Immutable/versioned interpretation of the equipment's relevant measurable semantics, potentially including:

- displayed-load semantics;
- base/starting resistance or moving mass when stated;
- stack/plate increment rules;
- permitted/available loads;
- pulley/mechanical ratio where genuinely known;
- rail angle or geometry where legitimately observed/stated and useful;
- bilateral/per-side loading rules;
- calibrated vs uncalibrated status;
- provenance/source image/OCR/manual confirmation;
- uncertainty/quality state.

Do not invent universal mechanical equivalence from incomplete machine metadata.

### SessionEquipmentBinding

Historical evidence must be able to identify which equipment/calibration was actually used for that session/exercise.

A later gym equipment replacement must not silently rewrite the meaning of historical performance.

## 4.2 Equipment identity versus ExecutionProfile

Changing machine/equipment does **not automatically** require a new `ExecutionProfileVersion`.

However, if the equipment change materially changes execution semantics — e.g. mechanism, ROM geometry, unilateral/bilateral behaviour, resistance curve or another capability-defining feature — a distinct execution profile/version may be required.

That decision must remain explicit and auditable.

## 4.3 Raw load, equipment configuration and modelling coordinate are different

Preserve distinct concepts:

```text
USER-ENTERED LOAD
what the user selected/loaded

EQUIPMENT CONFIGURATION
what the machine/implement means mechanically or operationally

N-BIO RESISTANCE/CHALLENGE COORDINATE
what the versioned model uses internally
```

Example:

```text
added plates = 150 kg
machine states base sled mass = 45 kg
moving mass may therefore be 195 kg
```

Do not automatically claim that `195 kg moving mass == 195 kg universal resistance`.

Incline angle, pulley/leverage, cams, friction and other mechanics may matter. Use only defensible calibrated information and preserve uncertainty.

## 4.4 V7F translation requirement

N-BIO-7F translation must be able to condition on equipment features/calibration where available and propagate uncertainty when they are absent/weak.

Relevant feature families may include:

- exact same equipment instance;
- equipment model/family;
- loading mechanism;
- calibrated versus uncalibrated output;
- display-load semantics;
- base/starting resistance;
- pulley/leverage metadata;
- resistance curve/mechanics;
- load increment/resolution;
- entry basis;
- unilateral/bilateral mechanism;
- calibration provenance/quality.

Direct history on the exact destination equipment remains strongest.

Unknown equipment does not automatically mean `null`; the model may produce a broader tentative estimate if there is still a meaningful evidence basis.

## 4.5 V7F collaboration reminder

`COLLABORATION REQUIRED` before finalising N-BIO-7F equipment semantics:

- remind Kian that equipment-instance/calibration hooks were deliberately requested before V8 UX exists;
- review whether current schema can represent session-only machine overrides and persistent preferred-machine changes;
- do not implement a full camera/setup UI during 7F unless explicitly requested;
- flag any translation equation that would convert machine metadata into universal resistance without research/validation.

---

# 5. Future equipment setup/scanning UX

The backend should support later UX approximately capable of:

```text
current programme thinks:
Technogym <model/family>

user enters setup/equipment mode
→ "Use a different machine today"
→ "Set a new regular machine"
→ "Add another machine"
```

Semantics:

- **different machine today** → session-only binding; future default unchanged;
- **new regular machine** → future preferred binding changes; old history unchanged;
- **additional machine** → user may maintain several regular equipment instances/gyms.

A normal workout card may eventually expose a horizontally scrollable tag/chip showing the current equipment assumption, e.g. `Technogym …`, so an invisible modelling assumption becomes inspectable.

`COLLABORATION REQUIRED`: this exact UI is not approved merely because the backend supports it. Prompt Kian to design/review setup-mode and equipment-tag behaviour in Figma before implementation.

## 5.1 ML-assisted capture

Future Android/ML Kit work may combine:

- OCR/text recognition for placards, stack labels and plate markings;
- barcode/QR scanning where manufacturers provide useful identifiers;
- object detection for plates/equipment regions;
- custom image classifiers/object detectors for gym-specific equipment taxonomy;
- user confirmation before canonical identity/calibration is accepted.

Preferred pattern:

```text
raw image
→ derived OCR/classifier candidates
→ structured candidate metadata
→ user confirms/edits
→ immutable canonical equipment/calibration version
```

Do not let vision/LLM output silently become historical equipment truth.

## 5.2 Feasible-load set

Equipment may expose a discrete set of physically selectable loads/configurations rather than a continuous numeric range.

Examples:

- weight stack increments;
- dumbbell rack values;
- available plate denominations/quantities;
- bilateral plate-loading requirements;
- micro-plate availability;
- local machine ordinal levels.

Preserve this as equipment/configuration information so N-BIO-8 can choose feasible prescriptions.

---

# 6. N-BIO-8 requires a research + product-design gate before implementation

## 6.1 Do not march directly from N-BIO-7H into an optimiser

Before behaviour-driving N-BIO-8 implementation:

`COLLABORATION REQUIRED`

Explicitly remind Kian that we agreed N-BIO-8 needs:

1. a new deep research pass;
2. programme-intent/product-policy design;
3. UX-flow design/prototyping, likely including Figma;
4. explicit user-control rules for adaptive behaviour;
5. scrutiny of uncertainty communication and mid-workout changes.

Do not implement N-BIO-8's resolver merely from the old high-level equations in `PLAN.md`.

## 6.2 Recommended N-BIO-8 internal sequence

Treat the following as a planning shape, not final implementation without research:

```text
8A  Programme intent, training-science & UX research/design
8B  User programme-intent / priority model
8C  Session resolver + feasible prescription model
8D  In-workout adaptive resolver
8E  Programme editing / regeneration / equipment UX integration
8F  Retrospective/prospective validation + closure
```

Exact subdivisions may change after research.

---

# 7. Programme Intent sits above muscle targets

The programme must ultimately optimise for what the user wants rather than an abstract global idea of training efficiency.

Conceptually separate:

```text
USER PROGRAMME INTENT
+ USER PREFERENCES
+ USER CONSTRAINTS
+ TRAINING-SCIENCE GUARDRAILS
        ↓
PROGRAMME TARGETS / PRIORITY STRUCTURE
        ↓
N-BIO-8 SESSION RESOLUTION
```

Examples of user intent may include:

- prioritise glute development;
- prioritise shoulder visual development;
- maintain rather than maximise a body area;
- pursue a particular performance goal;
- mixed aesthetic/performance/general-fitness goals.

A resolver must not reduce a user's explicitly prioritised target merely because another target appears more mechanically efficient in the abstract.

## 7.1 Dedicated user-priority UX

N-BIO-8 needs a real workflow/page for establishing and periodically revisiting programme intent/priorities.

Open questions requiring research/design include:

- body-area sliders versus ranked priorities versus natural-language goal capture;
- aesthetic/body-composition goals versus performance goals;
- how often to revisit goals;
- how to express `maintain`, `grow`, `high priority`, `not important` safely/intuitively;
- whether a body visual improves comprehension;
- how much direct control users have over muscle-group allocations versus higher-level goals;
- how default balance/safety guardrails interact with strong user priorities.

`COLLABORATION REQUIRED`: before building this page/model, prompt Kian for a dedicated design/research conversation and likely Figma work.

---

# 8. Programme editing has multiple semantic levels

Do not collapse all editing into one action.

Distinguish at least conceptually:

### Session-only edit

`Not today / swap this now / reorder today's work.`

Does not necessarily change future programme intent.

### Persistent execution/exercise preference

`Stop prescribing this exercise regularly / prefer another implementation for this role.`

Changes future resolver preference without redefining the high-level goal.

### Programme-intent edit

`Increase glute priority / reduce hamstring emphasis / prioritise pull-up strength.`

Changes what the programme is trying to accomplish.

### Programme overhaul/regeneration

May involve reallocation of muscle/performance priorities, exercise candidates, order and constraints.

The UX must make these meanings distinguishable enough that the model does not infer a permanent preference from a one-off session edit.

`COLLABORATION REQUIRED` before implementing the final editing/regeneration workflow.

---

# 9. N-BIO-8 in-workout adaptation requires explicit user-control policy

A future resolver may update recommendations after observing actual within-session evidence, including:

- completed performance versus initial prediction;
- actual rest/timing;
- set completion/failure semantics where trustworthy;
- per-exercise review/context tags through validated policies;
- remaining programme target need;
- equipment feasibility.

Possible adaptations include:

### Lower intrusiveness

- suggest load increase/decrease;
- adjust rep target;
- suggest rest change;
- mark a planned set optional.

### Medium intrusiveness

- add/remove a set;
- reorder upcoming exercises;
- suggest a swap.

### High intrusiveness

- remove an upcoming exercise/target;
- add new work;
- materially restructure the rest of the session;
- change persistent programme intent.

Do not silently implement high-impact changes.

## 9.1 User control

Research/design should decide whether adaptive behaviour uses modes such as:

```text
OFF
CONSERVATIVE / suggestions only
FULL / broader session adaptations may be proposed
```

or another clearer product model.

Major changes should likely require explicit confirmation even when adaptation is enabled.

This is not yet final policy.

`COLLABORATION REQUIRED`: remind Kian that the adaptive-workout interaction, toggles and allowed-change hierarchy were deliberately left for joint research/UX design before implementation.

## 9.2 Context tags do not automatically author programme changes

Example:

```text
GENERAL_FATIGUE_REPORTED
DISCOMFORT_REPORTED
```

must not directly mean:

```text
remove 2 sets
```

Any policy consuming context annotations in programme resolution must be explicit/versioned/validated and distinguish product-safety prompts from biological inference.

Actual performance plus context may justify a suggestion, but the annotation alone is not an optimiser command.

---

# 10. Equipment feasibility belongs in N-BIO-8 prescription resolution

N-BIO-7 may estimate a continuous latent capability/prediction, while real equipment allows only discrete selections.

Example:

```text
model's useful target region ≈ 63 kg @ 8 reps
machine permits 60 or 65 kg
```

N-BIO-8 should choose among feasible combinations rather than outputting impossible loads or blindly rounding.

It may trade between:

- load;
- repetitions;
- number of sets;
- set structure;
- rest/density where justified;

subject to programme intent and the evidence/model contract.

Progression therefore need not mean increasing load every session.

A coarse machine increment may make a progression such as:

```text
60 × 8
60 × 9
60 × 10
65 × 6
65 × 7
```

more sensible than forcing an immediate jump.

The exact resolver objective requires N-BIO-8 research/validation.

---

# 11. N-BIO-9 model/recomputation lifecycle UX

N-BIO-9 must treat derived intelligence as versioned/recomputable product state rather than invisible magic.

Different derived products should remain independently understandable/controlable, e.g.:

- note/context annotations;
- biological/inference state;
- trace summaries/caches;
- Health imports/mappings where applicable.

Do not hide everything behind one ambiguous `Recompute everything` action.

## 11.1 Nano/base-model upgrade detection

Because interpretation provenance stores runtime model identity/version/schema information, the app should be able to detect when historical notes were interpreted under an older materially different interpreter/model than the currently available runtime.

A future UX may say, in substance:

> A newer on-device language model is available. My Mettle can reanalyse your past workout notes. Your original notes will not be changed.

Actions may include:

- `Reanalyse now`
- `Later`

Do not automatically perform a potentially long full-history reannotation without appropriate product policy/user feedback.

## 11.2 Progress, pause/resume and foreground constraints

Large reannotation/recomputation may take meaningful time.

Design for:

- progress indication;
- checkpointed batches;
- safe interruption;
- pause/resume where platform constraints justify it;
- explicit statement that canonical raw data is unchanged.

Recheck current ML Kit foreground/background/runtime constraints at implementation time.

`COLLABORATION REQUIRED`: before final reannotation/recompute UX, prompt Kian to review/design the loading/progress/error states.

## 11.3 Biological model recomputation

When a behaviour-driving N-BIO model/config changes, full-history replay may be required.

The product should be able to distinguish:

```text
annotation reanalysis
biological-state recomputation
health-data rescan/import
full derived-data rebuild
```

Small/fast compatible updates may eventually be automatic; long/relevant rebuilds may warrant explicit progress/consent. Decide this through product design rather than silently.

---

# 12. Native Cutover requires a Database Compatibility Gate

The existing plan requirement remains:

- remove destructive production migration at cutover;
- later schema changes require explicit migrations/tests.

Add the following product-safety contract.

## 12.1 Pre-open compatibility assessment

Before an authoritative database is destructively touched, the app should be able to distinguish at least:

```text
CURRENT / DIRECTLY COMPATIBLE
SUPPORTED MIGRATION PATH EXISTS
DATABASE NEWER THAN APP UNDERSTANDS
UNSUPPORTED OLDER VERSION / MISSING MIGRATION PATH
SCHEMA/INTEGRITY PROBLEM
```

Do not respond to an unsupported state by recreating the database.

## 12.2 Supported normal migration

For an ordinary tested migration:

```text
schema N
→ known migration
→ schema N+1
```

avoid unnecessarily alarming the user.

If the operation is slow, a lightweight `Updating your training data…` state may be appropriate.

## 12.3 Pre-migration safety snapshot

For authoritative history, investigate/implement a safety strategy conceptually equivalent to:

```text
migration required
→ create/verify pre-migration safety snapshot where appropriate
→ execute tested migration
→ run integrity validation
→ open app
```

The exact snapshot mechanism must be designed/researched for Android/Room/file-safety constraints.

## 12.4 Post-migration validation

Relevant validation may include:

- expected Room/schema identity;
- `foreign_key_check`;
- critical required tables;
- representative row/count invariants where useful;
- backup metadata/integrity;
- domain-specific canonical invariants where lightweight enough.

Do not continue writing to a database after a migration failure as though nothing happened.

## 12.5 Blocking incompatible-state UX

For an unsupported/incompatible/corrupt state, preserve the database and show a blocking recovery flow in substance such as:

> This version of My Mettle cannot safely open/upgrade this training database. Your data has not been modified.

Possible actions:

- export/create backup when technically safe;
- show instructions;
- install/update the required app version;
- exit;
- developer/recovery options where explicitly gated.

`COLLABORATION REQUIRED`: design the exact recovery/warning UX with Kian before cutover implementation.

## 12.6 Downgrade protection

If the database was written by a newer app/schema than the installed app supports:

- do not write;
- do not destructively recreate;
- explain that a newer compatible app version is required.

This must be tested.

---

# 13. Required future phase reminders

When generating prompts for the following phases, explicitly carry these hooks forward.

## Before N-BIO-7F

Remind Kian / the implementation agent:

- equipment model/instance/calibration/session-binding backend hooks are required;
- equipment/calibration belongs in translation uncertainty/maths where defensible;
- uncertain translation may still produce a tentative suggestion rather than automatically null;
- do not build the final scanning/setup UI without collaboration;
- flag any need for equipment-mechanics research.

## Before N-BIO-8A / first N-BIO-8 implementation

STOP.

Remind Kian:

- we explicitly agreed to conduct another deep research pass before V8 implementation;
- programme intent/priorities require dedicated product/UX design;
- adaptive in-workout behaviour needs explicit user-control policy;
- programme editing/regeneration semantics need design;
- equipment setup/feasible-load UX likely needs Figma work;
- uncertainty/suggestion states should be designed rather than inferred from backend enums.

Ask whether to begin research, requirements design, or Figma flow work.

## Before N-BIO-9 recomputation/product-intelligence work

Remind Kian:

- Nano model changes should be detectable through provenance;
- reannotation requires explicit progress/error/pause/resume UX consideration;
- biological recomputation and note reanalysis are distinct controls;
- current Android/ML Kit constraints must be rechecked;
- ask whether he wants to design the recompute/update flows in Figma first.

## Before Native Cutover

STOP.

Remind Kian:

- production destructive migration must be removed;
- database compatibility/downgrade handling must exist;
- unsupported/incompatible databases must fail safe rather than reset;
- pre-migration backup/snapshot and integrity-validation policy needs final review;
- blocking migration/recovery UX requires explicit design/approval.

---

# 14. What this document does NOT authorise

This supplement does not itself authorise:

- fixed context penalties;
- automatic programme changes from Nano tags;
- universal machine-load conversion equations;
- arbitrary biomechanical corrections from incomplete equipment metadata;
- automatic acceptance of OCR/image-classifier equipment identity;
- automatic user-goal inference from body images;
- high-impact mid-workout changes without approved product policy;
- destructive database recovery;
- bypassing existing N-BIO validation/provenance requirements.

All existing scientific/model/data invariants remain in force.

---

# 15. Current roadmap interpretation

Use the following human-level map:

```text
N-BIO-6
BUILD THE MEMORY
canonical scalar + temporal evidence

N-BIO-7
BUILD THE BRAIN
capability → demand → dose → acute/slow state → translation → conditioning

late N-BIO-7
PREPARE EQUIPMENT-AWARE TRANSLATION
backend equipment identity/calibration/session-binding hooks

N-BIO-8A
RESEARCH + DESIGN GATE
programme intent + adaptive-workout science + product UX

N-BIO-8
BUILD THE COACH
programme intent → dynamic targets → feasible prescriptions → adaptive sessions

N-BIO-9
CONNECT + MAINTAIN INTELLIGENCE
context UX + recomputation + Health Connect + HR research + Analysis Export

NATIVE CUTOVER
MAKE HISTORY PERMANENT
explicit migrations + database compatibility/safety/recovery lifecycle
```

The purpose of the collaboration gates is not to slow implementation unnecessarily. It is to ensure the system pauses exactly where backend capability stops being enough to determine the correct product behaviour.
