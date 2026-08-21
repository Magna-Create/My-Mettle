# Native Library and routine editing plan

## Goal

Bring the useful Library, routine creation and reordering behaviour from My Mettle Lite Legacy into My Mettle Native without copying the web implementation or weakening Native's N-Bio model.

The current Native Library search, exercise detail, setup notes and setup-photo storage remain the foundation. Import Routine is explicitly out of scope until its data contract and conflict UX are redesigned.

## Behaviour map

| Lite Legacy behaviour | Native implementation |
| --- | --- |
| Four routine lanes (`ψ`, `φ`, `π`, `&`) | Material 3 routine board backed by the active immutable `RoutineVersionEntity` |
| Tap a slot to manage it | Native exercise/slot editor with local-surface Haze 2 controls |
| Drag handle reorders within or between days | Long-press drag handle, insertion marker, edge auto-scroll and drop haptics in Compose |
| Overflow fallback | Move up/down, move to day, duplicate and remove actions remain available without dragging |
| Undo stack (20 edits) | In-memory draft command stack capped at 20, with Snackbar undo |
| Local recovered draft | `SavedStateHandle` plus a persisted Room draft record; offer Restore/Discard after interruption |
| Save creates a new routine version | One Room transaction clones the active version, applies the draft and activates the new version |
| Add-exercise wizard | Native five-step wizard: Movement, Place, Tracking & dose, N-Bio, Review |
| Manage exercise memory | Edit cues, mistakes, setup, video, equipment settings, setup photos and approved substitutions |
| Archive and restore | Archive removes current slots but preserves exercise and session history; restore returns it to the catalogue |
| Legacy target-muscle text | Structured execution-profile recruitment allocations using Native muscle segments, role, weighting and confidence |
| Legacy A/B/C prescription copy | Native programme-mode constraints and prescriptions, validated against N-Bio coverage |

## Visual system

- Use Daily Update's green and near-black Material 3 palette and atmospheric depth.
- Keep data-heavy cards as calm local surfaces. Interactive buttons, handles, chips and menus use the shared Haze 2 material.
- Each glass control samples the surface directly behind it through a scoped Haze source. It must not reveal the app background through an opaque card.
- Preserve 48 dp minimum touch targets, centred labels, dynamic type bounds and accessible non-drag alternatives.
- Use the existing app header and floating hotbar. Library is selected in the centre hotbar and routine editing is a dedicated full-screen destination.

## Delivery phases

### 1. Repository and draft contract

- Add a `RoutineEditorRepository` around routine versions, days, slots, programme targets and mode constraints.
- Introduce a stable `RoutineEditDraft` domain model independent of Compose state.
- Implement move, insert, duplicate, remove and validation as pure functions with unit tests.
- Commit a new immutable routine version in one Room transaction. Existing and completed sessions keep their original version references.
- Persist interrupted drafts and make Restore/Discard explicit.

### 2. Library overview

- Replace the basic list with the active routine board while retaining search and exercise detail.
- Show day symbol, role, tracking mode and useful prescription summary without exposing storage terminology.
- Add Edit routine, Add exercise, version summary and collapsed Archived exercises controls.
- Keep setup-photo capture and the current Native media storage pipeline.

### 3. Routine reorder screen

- Present the four days as vertically stacked lanes on compact phones; use a wider adaptive board on larger windows.
- Start drag only from the handle after a long press. Render a lifted ghost and a clear insertion marker.
- Support cross-day moves, empty-lane drops and edge auto-scroll.
- Haptics: lift, valid insertion change, day change, place and rejected drop.
- Preserve Legacy's 20-step undo and the complete overflow fallback for accessibility and precision.
- Confirm before discarding a dirty draft; Done validates and creates the new version.

### 4. Exercise creation

Build a five-step, resumable wizard:

1. **Movement:** name and tracking metric.
2. **Place:** routine day, priority/role and entry basis.
3. **Tracking & dose:** unit, load resolution, starting values and mode prescriptions.
4. **N-Bio:** execution profile, equipment and structured recruitment allocations. Show coverage/confidence validation before allowing completion.
5. **Review:** human-readable summary and the routine-version change that will be created.

After creation, offer setup notes, cues and camera capture through the existing Native setup-media flow.

### 5. Exercise and slot management

- Separate exercise-wide data from routine-slot data in the UI.
- Exercise: name, tracking, execution profiles, recruitment, memory, cues, mistakes, setup, video, equipment settings, setup photos and substitutions.
- Slot: day, order, importance, planned load and per-mode prescription/constraint data.
- Keep **Remove from routine** distinct from **Archive exercise**. Both require confirmation and neither rewrites historical sessions.

### 6. Archive, restore and version history

- Add searchable archived exercises with Restore.
- Show routine version number, creation time and a concise change summary.
- Initially keep rollback read-only; a later rollback action should create another immutable version rather than reactivate an old row in place.

## Performance and correctness gates

- Keep drag coordinates and transient hover state out of the database and main app ViewModel.
- Use stable slot keys and immutable collections so only the affected lanes recompose.
- Cache exercise/profile display models and load full memory/media only for an opened editor.
- Unit-test every draft operation, N-Bio validation, version cloning and archive distinction.
- Add Compose tests for drag fallback actions, draft recovery, creation validation and back/discard handling.
- Device-test long lists, edge auto-scroll, dynamic type, TalkBack and process recreation before replacing the current Library screen.

## Explicit exclusions

- Import Routine.
- Blind conversion of Legacy free-text target muscles.
- Blind copying of Legacy A/B/C prescriptions where Native programme constraints or recruitment coverage would be invalid.
- Editing an existing routine version or historical session in place.

## Suggested implementation sequence

Ship phases 1–3 together behind a Library feature flag, then phase 4, then phases 5–6. Promote the new Library only after existing workout generation produces the same session from an unchanged routine and the editor produces a new, validated version from a changed routine.
