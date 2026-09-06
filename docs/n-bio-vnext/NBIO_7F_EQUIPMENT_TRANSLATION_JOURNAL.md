# N-BIO-7F Equipment Translation Journal

Rough continuation log. Keep this source-led and compact; this is not closure documentation.

## Session 1 — 2026-09-06 15:17 BST

HEAD IN:
58aca1ee2da021aa4cb1141b1326869520a74114

OBJECTIVE:
Complete the required source/schema/backup audit and determine whether Room16 is genuinely required before authoring equipment APIs or schema.

SOURCE FINDINGS:
- Remote branch `agent/n-bio-vnext-inference` exactly matched the expected initial HEAD. No newer legitimate work required reconciliation.
- `ExecutionProfileVersion` currently owns only a small embedded `EquipmentProfile(identity, type)`. Room15 persists those as nullable `execution_profile_version.equipmentIdentity/equipmentType` fields.
- Current execution-profile authoring writes those equipment fields into immutable profile-version rows. This cannot honestly own mutable preferred/default equipment or distinct physical-equipment history without conflating equipment identity with execution-profile semantics.
- `session_exercise` snapshots an execution-profile version but has no actual-equipment binding. `set_observation` references set/profile semantics but has no independent equipment binding/override. Therefore historical actual use cannot be resolved independently from the profile/default today.
- No canonical equipment instance/specification/configuration owner exists in Room15. No existing table can preserve stable anonymous identity plus later OEM enrichment/provenance without misusing execution-profile rows or ContextModule memory.
- `EntryBasis.TOTAL/PER_HAND/PER_SIDE` is already a clean aggregation axis and must remain unchanged. There is no orthogonal complete/inclusive-vs-added-only load-value semantic today.
- `ResistanceSemantics.DEVICE_ORDINAL` and `PerformanceMetric.MACHINE_LEVEL` are already explicitly ordinal; `ResistanceResolver` fails rather than converting ordinal resistance to kg. Preserve this behaviour.
- Generic Native backup enumerates application tables dynamically and requires exact Room schema equality. New canonical Room tables will therefore be covered automatically once registered, but migration/round-trip tests are still required.
- Legacy import snapshots carry the existing execution-profile rows only; they do not contain trustworthy canonical equipment identity, actual historical equipment binding, or complete-vs-added semantics. Room15→16 migration must therefore leave those new semantics unknown/unbound rather than deriving them from legacy profile labels.

CHANGES:
- Created this 7F journal.
- No product/runtime behaviour or schema changed in this session.

TESTS:
- Source audit only; no code changes requiring build/tests.
- Verified branch HEAD and inspected current domain models, Room15 entities/database/migration registration, execution-profile authoring, session/observation ownership, legacy import snapshot, and generic Native backup implementation.

DECISIONS:
- Room16 is required. Room15 has no honest canonical owner for stable equipment identity/versioned facts, preference-vs-actual-use separation, or historical load-value semantics.
- Room16 must be additive and must not backfill guessed equipment identities/bindings/load conventions from `execution_profile_version.equipmentIdentity/equipmentType`.
- Existing embedded execution-profile equipment fields are historical semantic input and cannot become the new canonical equipment owner by reinterpretation.
- Backup code itself does not need a special table whitelist change because it is schema-driven; acceptance must still prove new-table round-trip/FK behaviour.

OPEN QUESTIONS:
- Exact minimal Room16 table/foreign-key topology and whether set-level actual-equipment override is necessary immediately or can be represented by observation-level binding without duplicating occurrence history. Resolve from source/contract before migration code.
- Existing global `.fallbackToDestructiveMigration(true)` predates 7F. The 15→16 path must be explicit; decide during migration implementation whether the broader fallback can be safely narrowed without breaking supported old-development upgrade paths.

HEAD OUT:
Source remains 58aca1ee2da021aa4cb1141b1326869520a74114; this journal is committed as the session checkpoint on top of that source state.

NEXT:
Implement the minimal canonical equipment + load-semantics Room16 substrate, including explicit additive 15→16 migration and migration/domain tests, without yet starting transfer modelling.