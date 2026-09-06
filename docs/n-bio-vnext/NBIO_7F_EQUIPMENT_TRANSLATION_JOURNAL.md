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

## Session 3 — 2026-09-06 16:54 BST

HEAD IN:
8435112905aebfdcbf5869fd19662cb05b323f0c

OBJECTIVE:
Implement and test canonical equipment write/resolution behaviour: preference supersession, session actual-use binding, observation override precedence, fact-version lifecycle, and immutable historical separation without starting local physics or transfer modelling.

SOURCE FINDINGS:
- The 7F contract requires historical equipment resolution from occurrence-time evidence; current preferred/default equipment must never be used to reconstruct historical actual use.
- Session-exercise binding is the normal actual-use owner. Observation-level override is exceptional and takes precedence only for an observation that differs within the occurrence.
- Preference history and equipment-fact history can use append-plus-`supersededAt` lifecycle semantics without rewriting their historical payload values.
- The contract does not define archived equipment as invalid for historical corrections or later fact publication. A draft restriction based on `archivedAt` would therefore have invented product behaviour and was removed during review.

CHANGES:
- Added typed domain models for preferred-equipment bindings, session actual-use bindings, observation overrides, resolved binding provenance, and resolution source.
- Extended `EquipmentDao` with lifecycle supersession methods, current-preference retrieval that can detect invalid multiplicity, and an observation→set→session-exercise lookup for historical resolution.
- Added `EquipmentContextRepository` as the canonical transactional write/read boundary for equipment context.
- Preference changes are versioned separately from actual use; selecting a new preference supersedes only the preceding preference lifecycle marker.
- Equipment fact successors require contiguous versions and non-decreasing effective time; old fact values remain untouched while only their lifecycle marker is superseded.
- Session actual-use binding, observation override and observation load semantics are insert-only in this slice, with exact retries idempotent and conflicting rewrites rejected pending the explicit correction/audit slice.
- Historical resolution is strict: observation override → recorded session-exercise actual binding → unknown. It never falls back to current preference.
- Added repository unit tests for preference/actual separation, override precedence, unknown historical resolution, fact lifecycle, and insert-only historical evidence semantics.
- Initial repository tests compiled but JUnit rejected the class because expression-bodied `runBlocking` tests inferred a non-`Unit` return type from terminal `assertIs`; fixed by making the test bodies explicitly return `Unit`.

TESTS:
- First source CI run 34042848520 on `6f55b3a8ba1e9a91433515974faa85e0d17644bf`: production Kotlin compilation and debug APK assembly succeeded, but `EquipmentContextRepositoryTest` failed class initialisation due the test-signature issue above. This was a test-shape failure, not a production compilation failure.
- Fix commit `2062d423fe967d77cef1a943df700a621d8aab8e` was then tested in exact-head Android CI run 34043265691.
- On `2062d423...`, reference assets, exercise-authoring schema, context boundary, Context Module docs, whitespace and Gradle clean all passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the new equipment lifecycle/resolution tests.
- `:app:assembleDebugAndroidTest` passed.
- Android lint passed.
- Room16 exported-schema verification passed and CI uploaded debug APK plus Room schema artifacts.

DECISIONS:
- Historical equipment resolution never consults preferred equipment. Missing recorded actual-use remains unknown.
- Preferred/default selection and actual historical use remain separate write paths and separate state.
- Observation override has explicit precedence over session-exercise actual-use binding, but it does not mutate that session binding.
- Fact and preference successors preserve historical payloads and alter only lifecycle markers transactionally.
- Actual-use/load-semantics rows are deliberately non-overwritable in this slice. This prevents silent history rewriting until the contract-required correction/invalidation/audit path is implemented in 7F-E.
- No equipment kind, OEM identity, ratio, starting resistance, friction or calibration default is inferred by this repository.

OPEN QUESTIONS:
- Correction/invalidation semantics for session actual-use bindings, observation overrides and observation load semantics still require the dedicated persistence/correction slice; current writes fail closed on conflicting replacements.
- Local deterministic interpretation now needs time-valid equipment fact selection. The next slice should define the smallest exact as-of fact reader needed for deterministic interpretation rather than treating the newest fact as historically valid.

HEAD OUT:
Source checkpoint `2062d423fe967d77cef1a943df700a621d8aab8e` is green in Android CI run 34043265691; this journal commit follows on the same branch.

NEXT:
Implement the first local deterministic equipment-interpretation slice: resolve historical equipment plus observation load semantics and time-valid equipment facts, prove an exact bar/implement-mass + added-load case, fail closed on unknown complete-vs-added meaning or missing/ambiguous mechanics, and preserve the raw entered performance evidence. Do not start cross-profile transfer modelling yet.

## Session 4 — 2026-09-06 17:18 BST

HEAD IN:
21e6f55a422736f425ec15daa88091c1ca703d18

OBJECTIVE:
Implement the first local deterministic equipment-interpretation slice: resolve historical equipment, observation load semantics and time-valid equipment facts; prove exact local mass arithmetic; fail closed on ambiguous or missing meaning; preserve raw performance evidence; stop before cross-profile transfer modelling.

SOURCE FINDINGS:
- The 7F contract permits deterministic arithmetic only when its local meaning is exact. The derived result must remain a local configured-load coordinate, not universal resistance.
- Equipment fact history cannot be resolved with a newest-row shortcut. A historical observation needs the fact version valid at its event time.
- Existing `PerformanceMetricValue` already preserves entered quantity alongside deterministic canonical quantity. The interpreter can therefore derive a local coordinate without rewriting either historical field.
- `EntryBasis` is an independent historical aggregation semantic. Inclusive `PER_HAND` and `PER_SIDE` values can remain in their existing coordinate, while added-only arithmetic that would require inventing implement aggregation must fail closed.

CHANGES:
- Added `LocalEquipmentInterpreter` and a typed `HistoricalEquipmentInterpretationSnapshot`.
- Added an explicit versioned local interpretation result carrying raw entered external load, canonical entered kg, resolved equipment binding, observation load-accounting semantics, applied fact versions, and the derived local external-load coordinate.
- The first interpreter version handles only exact external-mass bookkeeping: inclusive load passes through its canonical local coordinate; added-only `TOTAL` load may add one unambiguous time-valid `IMPLEMENT_MASS` fact.
- Unknown equipment, missing load-accounting semantics, missing external load, missing implement mass, multiple simultaneous implement-mass facts, or non-total added-only aggregation all return typed unavailable results rather than guessed values.
- Mechanical-ratio facts are deliberately ignored by this v1 arithmetic; pulley, lever, Smith counterbalance, rail geometry, friction and starting-resistance algebra are not inferred or defaulted.
- Extended `EquipmentContextRepository` with one transactional historical interpretation snapshot reader.
- Historical equipment resolution was factored into a transaction-internal helper so snapshot reads do not nest repository transactions.
- Time-valid equipment facts are selected with parsed `Instant` interval semantics: `effectiveAt <= asOf < supersededAt`, with an open upper bound for current facts.
- Stored fact rows are decoded back to typed domain facts with value-kind/unit/provenance validation; inconsistent persisted semantics fail closed.
- Added deterministic interpreter tests plus a repository test proving a 20 kg fact is selected before its successor, the 15 kg successor is selected exactly at its effective boundary, and the newest fact is not leaked backwards.

TESTS:
- Exact source checkpoint `e4647c9336097e1c163fa1ade080b7dad729bfba` completed successfully in Android CI run 34044523324.
- Reference-asset, exercise-authoring, context-boundary, Context Module documentation, whitespace and Gradle-clean gates passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the new local interpreter and historical as-of fact tests.
- `:app:assembleDebugAndroidTest` passed.
- Android lint passed.
- Room16 exported-schema verification passed; debug APK and Room schema artifacts uploaded successfully.

DECISIONS:
- `localExternalLoadCoordinateKg` is explicitly local equipment/entry interpretation, not `L_true`, user strength, effective resistance or a cross-profile conversion coordinate.
- Exact inclusive external mass does not require machine-physics inference. Exact added-only mass requires explicit implement mass and currently requires `EntryBasis.TOTAL`; ambiguous per-hand/per-side implement aggregation remains unknown.
- Overlapping same-type facts are not arbitrarily reduced by the repository. Multiple time-valid implement-mass facts remain visible and cause deterministic interpretation to fail closed.
- `PER_HAND` and `PER_SIDE` are preserved rather than totalised by the interpreter.
- No pulley ratio, starting resistance, friction, lever arm, rail angle, counterbalance or OEM-family default has entered normal or shadow arithmetic in this slice.
- Cross-profile transfer modelling remains untouched.

OPEN QUESTIONS:
- The contract permits genuinely documented device-local relationships when their exact scope and label semantics are known. Their algebra and admissibility must be separately specified before extending this interpreter; presence of a ratio fact alone is insufficient.
- Correction/invalidation for canonical actual-use/load-semantics rows remains deferred to the 7F-E persistence/replay slice.
- The next architectural layer is 7F-C: a minimal typed capability-transfer boundary. Before numeric transfer candidates are fitted, it must preserve capability family, causal as-of/cutoff, posterior representation fidelity, supported domain, upstream model identity and equipment/local-interpretation provenance.

HEAD OUT:
Source checkpoint `e4647c9336097e1c163fa1ade080b7dad729bfba` is green in Android CI run 34044523324; this journal commit follows on the same branch.

NEXT:
Begin 7F-C by auditing the existing 7B/7C capability posterior/predictive surfaces and defining the smallest typed source-capability boundary needed by transfer candidates. Do not fit M0 or author numeric cross-profile transfer yet.
