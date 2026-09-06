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

## Session 2 — 2026-09-06 15:56 BST

HEAD IN:
a9a1c2e786e3a2128fe3515138e2e62b4c4bb52f

OBJECTIVE:
Implement the minimal canonical equipment + load-semantics Room16 substrate and explicit additive Room15→16 migration, stopping before transfer modelling.

SOURCE FINDINGS:
- The Room16 topology can remain additive: no existing canonical row needs reinterpretation or mutation.
- `PLAN.md` explicitly states that `fallbackToDestructiveMigration(true)` remains acceptable during development and is removed only at Native cutover. Therefore 7F should add an explicit 15→16 migration but must not silently change the broader development reset policy.
- An observation-level override is the smallest honest way to support equipment changes within a session-exercise occurrence without duplicating all set history. Session-exercise binding remains the normal actual-use owner; observation override is exceptional.
- Complete/inclusive-vs-added-only external-load meaning is cleanly represented as observation-local canonical semantics separate from `EntryBasis`; null/absent row remains the legacy unknown state.

CHANGES:
- Added `domain/equipment/EquipmentModels.kt` with stable equipment identity, general equipment kinds, typed/versioned equipment facts, required provenance classes, and orthogonal `ExternalLoadAccounting` semantics.
- Added Room16 canonical entities for stable equipment instances, atomic fact versions, preferred equipment history, actual session-exercise equipment binding, observation-level equipment override, and observation-local load semantics.
- Added `EquipmentDao` with insert/read surfaces for the canonical substrate.
- Advanced `MyMettleDatabase` to Room16 and registered `EquipmentDao` plus all new entities.
- Added explicit additive `MIGRATION_15_16`; it creates only new tables/indexes and performs no legacy backfill.
- Registered `MIGRATION_15_16` in the database builder while retaining the source-authorised development fallback policy.
- Added `NBio7FRoomMigrationTest` asserting Room15 data survives, all new tables exist empty, no guessed canonical equipment/load rows are created, and FKs remain clean.
- Added domain tests covering orthogonal load accounting, scalar fact/unit validation, and no inferred cable mechanics from equipment kind.
- Updated Android CI to verify generated Room16 schema output while retaining the committed Room15 schema check.

TESTS:
- GitHub Actions Android CI run 34040716638 for source commit `0794fe30e7ffd0648c79b06f965755abbe1fb4e6` completed successfully.
- Reference-asset verification, exercise-authoring schema verification, context boundary verification, Context Module docs verification, whitespace check and Gradle clean all passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the new equipment domain tests.
- `:app:assembleDebugAndroidTest` passed, so the new Room15→16 migration test compiles into the instrumentation suite.
- Android lint passed.
- Generated Room16 schema verification passed; CI confirmed `16.json` was produced with database version 16.
- CI uploaded both the debug APK and Room schema artifacts successfully.
- Local clone/build was unavailable because the execution environment cannot resolve github.com; GitHub branch CI was therefore the compile/test oracle for this slice.

DECISIONS:
- Room16 canonical facts are atomic/versioned rather than one mutable machine record. This preserves distinct OEM specification, user configuration, deterministic derivation and measured instance calibration provenance without implying all fields share one epistemic source.
- Equipment identity remains independent of `ExecutionProfileVersion`; existing `execution_profile_version.equipmentIdentity/equipmentType` fields are untouched legacy/profile semantics, not migrated into new canonical equipment rows.
- Preferred equipment is versioned separately from actual historical use. A session-only/observation-only actual-use binding cannot become preference merely by persistence shape.
- `ExternalLoadAccounting` intentionally has no `UNKNOWN` enum value; absence of canonical semantics is the unknown state, preventing migration from inventing knowledge.
- Existing `DEVICE_ORDINAL` behaviour is untouched. No ratio, starting resistance, friction or unit conversion defaults were added.

OPEN QUESTIONS:
- Repository/service-level write orchestration still needs to enforce preference supersession, actual-use resolution precedence, and fact-version lifecycle; the current Room DAO is a persistence substrate only.
- Local deterministic equipment interpretation remains the subsequent 7F-B task after historical binding/preference behaviour is proven.

HEAD OUT:
Source checkpoint `0794fe30e7ffd0648c79b06f965755abbe1fb4e6` is green in Android CI run 34040716638; journal-only commits follow on the same branch.

NEXT:
Implement and test canonical equipment write/resolution behaviour: preference supersession, session actual-use binding, observation override precedence, fact-version lifecycle, and immutable historical separation without yet starting local physics or transfer modelling.
