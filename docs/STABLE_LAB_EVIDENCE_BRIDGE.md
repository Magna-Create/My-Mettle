# Stable → Lab: read-only training evidence bridge

> **Status: proposed design, NOT IMPLEMENTED.** 16 November 2026.
>
> Design goal: My Mettle Stable owns authoritative training history. The separately installed My Mettle Lab can read it **on demand** for experimental N-BIO evaluation without requiring repeated manual export/import or sharing SQLite/Room databases.

Related: [Stable cutover](./NATIVE_STABLE_CUTOVER.md), [Native backups](./NATIVE_BACKUP_FORMAT.md), [N-BIO evaluation](./n-bio-vnext/PROSPECTIVE_EVIDENCE_AND_EXPERIMENTS.md).

## Non-negotiable boundaries

1. **One-way:** Stable → Lab. Lab cannot edit, delete, correct or replace Stable history through the bridge.
2. **Stable owns canonical facts:** observations, revisions, equipment assignments, context episodes, user-entered effort, source timestamps and provenance. Experimental posterior/model states belong to Lab, not to the sharing contract.
3. **No direct database sharing:** the bridge does not expose SQLite files, Room entities, internal table names or a shared application UID.
4. **Local and opt-in:** no cloud or background data transfer is required. Lab explicitly requests a read; Stable does not push data continuously.
5. **Revocable and permission-limited:** default private. An explicitly approved Lab installation gains limited read access to only the requested evidence. App/package name alone is not an adequate identity check.
6. **Versioned and replayable:** preserve stable IDs, revisions, chronology and deletion/correction semantics so Lab can rebuild derived results safely.

## Leading Android implementation

Investigate a small Android **ContentProvider** in Stable, consumed using Lab's ContentResolver. This is an established Android cross-application data-sharing mechanism, not a network connector.

- Restrict provider and URI exposure; use an explicit, revocable read grant/pairing mechanism suitable for separately signed release and debug builds. Do **not** assume matching signatures or make all training data accessible to arbitrary installed apps.
- Expose only narrow, queryable, stable evidence projections (or versioned read-only snapshot streams), not arbitrary SQL.
- Do not implement insert/update/delete operations; reject unknown query shapes and unauthorized callers.
- Use bounded/paginated reads and background-thread I/O where useful; avoid blocking workout UI.
- Remove or revoke access cleanly on unpairing, package replacement or permission change; handle provider unavailable/locked states.
- Consider Android package visibility, UID verification, URI-grant lifetime, restore and version compatibility during the detailed implementation review.

Android references: https://developer.android.com/guide/topics/providers/content-provider-creating and https://developer.android.com/guide/topics/manifest/provider-element .

## Logical evidence contract (draft)

The exposed model is an **interchange schema independent of Room**. At minimum:

- Contract version, source installation identity, export/query generation, capture time and bounded query cursor.
- Stable canonical identifiers, entity type, schema/semantic version, origin, creation/observation/effective/revision timestamps.
- Programme/routine/exercise/execution-profile identity needed to interpret each observation.
- Completed session and set observations with entered and canonical metric units, side/laterality, source attribution and corrections.
- User-reported effort: RIR estimate, failure type, omitted/unknown state and interpretation version.
- Equipment model/instance, versioned fact/calibration assertions and historical exercise/set bindings.
- Optional context evidence/episodes including start/end, explicit resolution, retrospective vs contemporaneous authoring and privacy/consent metadata.
- Revisions, invalidations and tombstones so a corrected/deleted Stable observation is not retained as current Lab truth.

Do not expose sensitive notes/health details beyond the user-approved scope by default. Data minimisation and opt-in field groups should be reviewed before implementation.

**Important:** an event's effective date is not necessarily its recording date. Retrospective illness/holiday annotations may support exploratory history analysis, but must not be treated as facts an older model knew at that earlier prediction time.

## Lab-side lifecycle

1. User opens Lab or starts a benchmark and explicitly requests current Stable evidence.
2. Lab negotiates supported contract version and requests a consistent snapshot or incremental changes.
3. Lab validates semantics, hashes/IDs where applicable, revisions and completeness, then imports into **Lab-owned canonical evidence staging**.
4. Lab recomputes its derived N-BIO state under named model/config/solver versions.
5. The experiment records its baseline, frozen prediction, outcome, quality/performance diagnostics and provenance.
6. A failure or incompatible schema is reported without writing anything back to Stable.

Initially, a complete snapshot can be the simplest and safest option while the dataset is small. Introduce incremental change cursors only when they materially reduce work and can correctly handle updates, corrections and deletions. A missed cursor should trigger a full resynchronisation, not silent divergence.

## Distinct from backups and model exports

- **Full Native backup**: user disaster recovery; all canonical tables **plus media once implemented**, exact restoration semantics.
- **Stable–Lab evidence bridge**: versioned read-only projections; not intended to restore Stable.
- **Model snapshot/experiment export**: immutable model identity, config, learned parameters/posteriors, diagnostics and optional evidence references; should be importable as a **new Lab candidate**, never silently replace production beliefs.

These are three separate contracts. Do not disguise a database dump as a long-term cross-version interchange API.

## Acceptance tests before deployment

- Lab queries allowed evidence after explicit pairing; unrelated apps cannot.
- Lab cannot modify Stable through provider calls, crafted URIs or oversized/malformed requests.
- Stable and Lab remain independent when Lab clears its database or is uninstalled.
- A correction or deletion in Stable propagates to the next Lab read; imported duplicate IDs are idempotent.
- Retrospective annotations preserve their original recording times.
- Interrupted, partial and unsupported-version reads fail visibly and can safely retry.
- Sensitive fields are absent unless explicitly enabled; access can be revoked.
- Benchmarks run against a consistent read generation and never contaminate historical training records.

## Design decisions pending

Exact URI/permission-grant protocol, entity envelope, batching, opt-in UX, allowed data categories, migration behaviour and snapshot consistency require device proof and user review. This document intentionally does **not** claim the connector already exists.

