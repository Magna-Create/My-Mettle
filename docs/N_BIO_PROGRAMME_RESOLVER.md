# N-BIO-5 — target-driven programme and mode resolver

## Scope

N-BIO-5 makes target intent, rather than a fixed exercise/mode matrix, the source of a new workout.
Room is now version 8 and retains the destructive-development policy while Lite Legacy remains the
live application.

The runtime path is now:

```text
programme targets + pinned exercise preferences + current inference snapshot
                              ↓
                whole-session mode constraints
                              ↓
             target resolution + exercise selection
                              ↓
        prescriptions + immutable session constraint snapshot
```

## Programme and session constraints

`programme_mode_constraint` stores one configuration per routine version, day and Native mode:

- whole-session working-set budget;
- whole-exercise budget;
- minimum sets per selected exercise;
- target-priority floor;
- optional time budget;
- source and resolver-model provenance.

`session_constraint` snapshots the configuration that produced a historical session. Session target
rows now also snapshot inclusion, resolved priority and target-resolution model version.

The per-slot `mode_prescription` table is removed. `RoutineSlotEntity` remains temporarily, but it is
only a pinned candidate/preference: programme targets pre-exist it, and the resolver may omit it.
Its rep range, rest and full-day set cap are compatibility preferences used after selection rather
than four fixed workout recipes.

## Lite Legacy projection

Legacy A/B/C rows remain transient importer input. `LegacyProgrammeConstraintProjector` converts
them once into Native A/B/C/D whole-session budgets.

Busy and minimum modes impose a two-set minimum when calculating their exercise budget. A Legacy
busy prescription containing one set on every movement therefore becomes fewer selected movements
with useful dose, avoiding repeated equipment changes for isolated single sets.

The original per-slot rows are not persisted as programme truth.

## Replaceable v0 resolution

`TargetResolver` and `ExerciseSelector` are independent engine boundaries.

The first target resolver applies only the explicit priority floor. It does not invent a recovery
or under-development equation from N-BIO-4's deliberately null/neutral state fields.

The first selector is deterministic and budgeted. It:

1. ranks pinned candidates by confidence-weighted recruitment coverage of included target priority;
2. prefers marginal target coverage before adding another expression of an already covered target;
3. selects whole movements within exercise, set and optional time budgets;
4. gives each selected movement the configured minimum dose before distributing spare sets;
5. records an explicit `unresolved_preference_fallback` for an imported pinned preference that has
   no target-resolvable recruitment; the mode priority floor still decides whether it is eligible.

Persisted N-BIO-4 same-profile load anchors now feed prescription generation. Raw latest load is a
compatibility fallback when no inference run exists. No automatic load progression is claimed.

## Deliberate limits

- Imported target dose remains nullable; working sets are the v0 whole-session dose budget.
- Time budgets are supported by the engine/schema but Legacy projection leaves them null.
- Recovery and development state do not alter target priority until defensible models exist.
- Exercise selection currently considers the default execution profile for each pinned candidate;
  cross-exercise discovery beyond programme preferences remains later work.
- `RoutineSlotEntity` can be renamed/replaced when the programme-editing UX moves fully onto target
  intent and optional exercise preferences.
