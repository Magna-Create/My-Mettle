# My Mettle: guide for people

> **Purpose:** explain what the project does and why decisions were made, without forcing every planning conversation to interpret implementation contracts.
>
> These documents are **explanatory translations**, not independent engineering requirements. The linked technical docs and code remain authoritative. Update the translation whenever a material technical decision changes.

## Start here

1. [N-BIO in plain English](./NBIO_GUIDE.md): what each phase does; PD-001–004 explained; current limitations; ordinary-language glossary.
2. [Native Stable Cutover](../NATIVE_STABLE_CUTOVER.md): why the priority is to use Native daily, what must be safe first and what is postponed.
3. [Stable–Lab connection](../STABLE_LAB_EVIDENCE_BRIDGE.md): how Lab can read current Stable workout evidence without sharing a database.
4. [Evidence collection and experiments](../n-bio-vnext/PROSPECTIVE_EVIDENCE_AND_EXPERIMENTS.md): optional RIR, context, equipment metadata, predictions, model snapshots and benchmarking.
5. [Graphics/component direction](../GRAPHICS_AND_COMPONENT_LIBRARY.md): shared materials, simplified Stable UI and later custom shaders/3D.

**Engineering source of truth:** [N-BIO plan](../n-bio-vnext/PLAN.md), [postponed investigations](../n-bio-vnext/POSTPONED_DEVELOPMENT.md), [product design/research gates](../n-bio-vnext/PRODUCT_ROADMAP_GATES.md), [Native migration](../MIGRATION.md), [Native backups](../NATIVE_BACKUP_FORMAT.md).

## Collaboration convention for chats, planning and implementation agents

When explaining a proposed feature to the user:

1. **Start with the user-visible outcome:** what actually changes and why it is useful.
2. **State what already works and what is not built**, then distinguish evidence missing from engineering work missing.
3. **Explain the practical trade-off and reversible experiment**, including when a simple solution is sufficient.
4. Introduce mathematical or invented project terms only when necessary; translate them in ordinary language once, then link to the authoritative definition.
5. Do not treat a failed/inconclusive calibration study as a reason the data-capture app must remain unusable.
6. Keep data loss, privacy, unreliable exercise/health claims and unapproved high-impact adaptation as hard safety boundaries.
7. If a recommendation depends on user behaviour, equipment or UX, return to collaborative design instead of silently interpreting backend enum values as product policy.
8. **Avoid making new jargon for an existing concept.** Prefer "historical correction" to an unexplained multi-word internal abstraction when addressing the user.

When writing technical files, prefer stable contracts, precise names, evidence and concise references. Human guides should explain concepts without duplicating thousands of lines of technical requirements.

## Simple status vocabulary

| Everyday status | Meaning |
| --- | --- |
| **Working** | Implemented and tested for its stated use, not necessarily validated for every scientific claim |
| **Experimental** | A versioned candidate that may run in Lab/shadow without product authority |
| **Needs more data** | Architecture may be complete, but sufficient real-world calibration/evidence is missing |
| **Not built** | Planned, designed or proposed; no code claim |
| **Deferred** | Intentionally not on the current release critical path |
| **Ready for daily use** | Separate acceptance: safe migration, preserved history, backups, and reliable workout interaction |

Words like **closed** in an engineering checkpoint refer to the stated structural/CI acceptance only. They do not automatically mean the model is biologically true, predictive, personalised or authorised to coach.

## Current strategic decision (16 November 2026)

**Build a safe Native daily driver first**, collect higher-quality evidence during ordinary use, then continue algorithm research in a separately installed Lab app.

- No Stable release branch, full private migration, RIR UI, Lab bridge or Native graphics replacement has been completed by this documentation update.
- Existing N-BIO-5/6/7 groundwork remains valuable; V8 coaching and V9 integration are later phases.
- Historical Lite → Native migration will be **manually reviewed in a separate conversation**, not silently executed by an automatic generic translator.
- Canonical workout history is valuable, permanent user data. Experimental derived model state is replaceable.
- Stable and Lab should remain physically separated in app identity/data; the Lab may **read** evidence through a controlled, local interface but must not change Stable records.

## Maintenance rule

For every substantial change affecting these guides, update the matching technical source and link to it. If a technical source disagrees with this explanation, **do not invent a reconciliation**: report the mismatch, resolve it explicitly and update the guide. Never commit the user's private workouts, health information, uploaded backups, API keys or device data into this public repository.

