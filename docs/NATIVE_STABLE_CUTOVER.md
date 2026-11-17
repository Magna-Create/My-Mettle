# Native Stable Cutover: daily-driver-first plan

> **Status: agreed direction; implementation NOT STARTED.** 16 November 2026.
>
> This plan prioritises a reliable daily-use My Mettle Native before N-BIO-8/9 development or a large graphics rewrite. It does not authorise an immediate data migration, release, or change of product authority.
>
> Related: [migration contract](./MIGRATION.md), [backup contract](./NATIVE_BACKUP_FORMAT.md), [Stable–Lab bridge](./STABLE_LAB_EVIDENCE_BRIDGE.md), [evidence collection](./n-bio-vnext/PROSPECTIVE_EVIDENCE_AND_EXPERIMENTS.md), [product gates](./n-bio-vnext/PRODUCT_ROADMAP_GATES.md).

## Purpose and release boundary

Make Native usable for everyday strength-training sessions **now**, so it can collect trustworthy, semantically rich evidence while N-BIO development continues separately.

- **Stable / My Mettle:** authoritative workout history, normal daily sessions, history, routines, backup, RIR/effort capture, equipment selection and optional timed context capture.
- **Lab / My Mettle Dev:** separate install and database; receives an on-demand, read-only view of canonical Stable evidence. Experiments can recompute and reset **Lab-owned** state without affecting Stable.
- **Pause for this milestone:** V8/V9 optimisation, advanced conditioning, new biological claims, a full shader/render-engine replacement, and speculative modelling work not needed for reliable capture.
- **Do not downgrade the scientific boundary:** N-BIO-5/6/7 foundations may remain installed and run diagnostics. Experimental V7 estimates are not automatically promoted to normal prescriptions; existing product authority remains until deliberately validated and changed.

This is one application codebase with separate release identities, configurations, and branch workflow, not a second independent product.

## Branches, builds and data

Existing Android identifiers:

- Release application: dev.kian.mymettle (My Mettle).
- Debug application: dev.kian.mymettle.dev (My Mettle Dev).

A future **stable** branch/release line should be cut from a verified checkpoint containing accepted work from the active N-BIO development branch. Do not cut it blindly from an older main branch; confirm the actual merge/head history first.

**Enforce database safety by build/release configuration, not Git branch alone.** Stable must reject any destructive Room fallback, developer database reset, or silently incompatible/downgraded database. Lab may retain deliberate destructive reset of **its own** local database. Stable and Lab must not share a database file or Android application identity. Ensure release validation fails if destructive fallback is accidentally enabled for Stable.

Stable updates still need tested, explicit Room migrations and compatibility checks. Assume future schema changes will often be additive, but never use that assumption instead of migration tests.

## Minimum daily-use product

| Area | Stable milestone |
| --- | --- |
| Workout loop | Programme/day/mode, start/resume, set editing, rest timer, exercise completion, session completion, history; preserve existing behavioural parity |
| Execution variations | **Required before daily use for relevant exercises:** lightweight per-set configurator for stage/form (e.g. dragon flag bent, half-bent, bent-up/straight-down, straight); preserve actual execution, mixed/unknown state and historical versioning. See [execution variants](./SET_EXECUTION_VARIANTS.md). |
| RIR / effort | Optional observation per completed set; distinguish estimated reps-in-reserve, technical failure, actual unsuccessful rep and early stopping; unknown remains unknown |
| Equipment | Manual machine/implement identity, additional equipment, permanent preference versus session-only override, relevant setup/calibration facts, and historical equipment binding |
| Context | Timestamped optional episodes (e.g. holiday, unwell, training break) including explicit resolution, retrospectively recorded status and overlap |
| Rendering | Simplify exercise/workout glass surfaces for responsiveness. Retain header/hotbar effects conditionally if measured performance is acceptable |
| N-BIO | Keep current dependable product behaviour; collect richer evidence for V7/V8; do not invent unvalidated coaching authority |
| Diagnostics | Crash/error visibility, export/restore and basic latency/energy measurement appropriate for daily use |

The minimal per-set variation selector is **in scope** for Stable because the movement's execution can fundamentally change the meaning of reps. The complete equipment camera scanner, angle-measuring workflow, advanced visualisation, model parameter editor and automated coaching are **later options**, not blockers for first daily use. Any user-facing equipment or effort interaction should be reviewed against the design/product gates before implementing.

## One-time Lite migration is a separate, manually reviewed task

The original Lite Legacy schema-v6 backup has historical semantics that cannot safely be inferred from free-text labels, proportions, equipment names or changed routine versions.

- Conduct **one curated historical migration in a separate conversation/workstream** with the user. Work through unclear exercises, equipment, historical semantic regime changes, routine versions, bodyweight and relevant context episodes manually.
- Reuse the repository's existing LegacyV6 reader/importer and reviewed recruitment translation *where appropriate*, but do not build or silently execute a new one-size-fits-all translator. Any transformation tool only implements explicitly reviewed mappings.
- Take a **fresh Lite export after the last Lite workout is completed**. Check whether the export contains an active session before committing to cutover; decide its fate explicitly, not by silently dropping it.
- Never invent RIR, effort/failure labels, machine angles, physiology, duration traces, or context dates absent from the source or user review.
- Keep the private Lite backup and all training/health history **out of this public repository**, including committed test fixtures derived directly from the user's data.
- Compare the original and imported counts, IDs, routine versions, exercise identities, canonical units, session statuses, completed-set values, reflections, notes, media and references. Preserve historical facts even where interpretation remains uncertain.
- Migration must be tested on a disposable Native installation and manually signed off before first Stable daily-driver session.

**Documentation correction:** Native currently has LegacyV6BackupReader and LegacyV6Importer code, but the normal backup UI restores only Native-format backups. The existence of the importer is not proof that present migration is production-ready.

## Data-safety acceptance gates

No daily-driver cutover until all of the following pass on an installed device:

1. Stable's destructive fallback and reset paths are disabled or inaccessible; incompatible/newer schema opens fail safely without destroying history.
2. Explicit migration tests cover the schema versions that a Stable upgrade could encounter, with integrity checks and a recoverable failure path.
3. Native full export → restore → representative workout/history replay succeeds. **Current implementation backs up Room tables, not external setup-photo bytes; media-inclusive recovery is outstanding** (see NATIVE_BACKUP_FORMAT.md).
4. Routine history, session states, per-set execution variations, set history, photos, notes, and timers survive the curated Lite migration and installed-device smoke tests.
5. Process death, interrupted sessions, app upgrade, background timer behaviour and duplicate submit actions are tested.
6. An initial period of real Stable workouts confirms usability before retiring Lite as the daily driver.
7. A minimal Stable → Lab evidence bridge can be validated independently, or explicitly deferred without blocking the **data-safe** Stable launch. The bridge is desirable early but must not delay basic reliable recording indefinitely.

Retain an independently restorable Lite backup until the real-world cutover is accepted.

## Practical sequencing

1. Freeze feature scope; identify an exact source commit; document current migration/data-safety gaps.
2. Resolve Stable database safety and media-complete backup/restore.
3. Implement the minimal per-set execution-variation configurator, RIR/effort and equipment logging paths; verify immutable evidence and corrections.
4. Reduce costly workout-screen glass and validate smooth interaction/thermal behaviour.
5. Run manually reviewed Lite migration in its own workstream and validate on-device.
6. Establish Stable daily use; then build/verify the pull-only Lab evidence bridge.
7. Resume V8/V9 research and N-BIO experiments against growing canonical evidence.

**Acceptance rule:** working, trustworthy data capture takes priority over perfect algorithms, while irreversible data loss, misleading records and unsupported medical claims are never acceptable shortcuts.

## Still to decide during implementation

- Exact set variation selector and preset semantics for progressive/multi-stage exercises (including distinct ascent and descent forms), with user sign-off; avoid compulsory per-rep tracking.
- Exact RIR/failure input design and optionality (Figma/user sign-off).
- Minimum manual equipment setup fields and session-vs-default selection.
- Release signing, rollback, app-update and backup location policy.
- Manual Lite annotation decisions, case by case, in the separate migration conversation.
- Whether the first Lab bridge uses an entire snapshot or paged/revision-based reads; keep the transport replaceable.

