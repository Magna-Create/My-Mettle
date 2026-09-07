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

## Session 5 — 2026-09-06 17:50 BST

HEAD IN:
9fc88e2ffb8426b6274a73f3589cc70b9ff83b1e

OBJECTIVE:
Audit the accepted 7B.X/7C capability posterior and predictive surfaces and implement the smallest typed 7F-C source-capability boundary needed by later transfer candidates, without fitting M0 or authoring numeric cross-profile transfer.

SOURCE FINDINGS:
- The accepted dynamic upstream for forward development is 7B.X Candidate-v2 with Adaptive Sparse, represented by `DynamicTrendFrontierFit`, not the older Candidate-v1 fit shape. It carries explicit mathematical-model identity, solver diagnostics/identity, causal evidence support and a joint weighted posterior-node representation.
- The 7C `NonDynamicCapabilityFit` surface has the same critical separation: family-specific semantics, explicit mathematical and solver identities, causal support, family-specific query coordinates and joint weighted posterior nodes.
- `PosteriorEstimate` summaries preserve useful marginal quantiles/variance and provenance but are not sufficient by themselves for 7F because accepted upstream inference retains material joint dependence in posterior nodes.
- Dynamic capability family identity remains `dynamic_resistance` even where the underlying execution-profile metric family is `BODYWEIGHT_RESISTANCE`; the boundary therefore preserves both capability-family and profile metric-family semantics instead of conflating them.
- 7C query domains are genuinely family-specific: loaded hold uses duration with resistance output, duration-only has no separate input coordinate, and repeated contraction uses cycle count with resistance output. A generic universal load vector would erase source semantics.
- Canonical equipment context cannot be reconstructed from current preference at this boundary. It must be supplied explicitly as resolved, genuinely not-applicable, or unresolved.
- Multi-source dependence is not established by 7B.X/7C. The boundary must therefore preserve within-source joint posterior dependence while explicitly refusing any assumption that different source capabilities are independent.

CHANGES:
- Added `CapabilityTransferBoundary.kt` as a source-only 7F-C transport contract.
- Added stable transfer-family identity for dynamic resistance, loaded hold, duration only and repeated contraction while retaining the originating profile's metric family, resistance semantics/model version, `EntryBasis`, laterality mode and exact profile/version identity.
- Added explicit equipment-context states: resolved single context with local interpretation version and contributing observation/fact ids, genuine not-applicable, and unresolved. Unresolved context remains visible rather than borrowing current preference.
- Added a strict causal cutoff carrying both inference `asOf` and `evidenceThrough`, with future evidence rejected.
- Added family-specific observed/query-domain types rather than a generic physical coordinate.
- Added sealed posterior payloads that retain the accepted upstream `DynamicTrendFrontierPosteriorNode` or `NonDynamicPosteriorNode` weighted joint representation. No mean/variance reduction or generic tensor was introduced.
- Added explicit upstream model-config, manifest/run provenance, mathematical-model identity, solver identity, posterior representation and evidence-policy identity.
- Added dependence metadata that records joint within-source nodes and `NOT_ESTABLISHED_DO_NOT_ASSUME_INDEPENDENT` across sources.
- Added source-led factories for accepted `DynamicTrendFrontierFit` and `NonDynamicCapabilityFit`; resolved equipment context must account for every selected source observation exactly.
- Added unit tests proving exact node/provenance preservation, family-specific domain preservation, explicit unresolved equipment status, source observation coverage, family mismatch rejection and future-cutoff rejection.

TESTS:
- Exact source checkpoint `321ef02a28d2a78dd591c5c96a3b2ecf0e820186` completed successfully in Android CI run 34046030542.
- Reference-asset, exercise-authoring, context-boundary, Context Module documentation, whitespace and Gradle-clean gates passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the new 7F-C boundary tests.
- `:app:assembleDebugAndroidTest` passed.
- Android lint passed.
- Room16 exported-schema verification passed; debug APK and Room schema artifacts uploaded successfully.

DECISIONS:
- 7F-C consumes the accepted 7B.X Candidate-v2 / 7C native joint posterior shapes rather than rebuilding capability from raw working sets or reducing upstream uncertainty to mean/variance.
- The boundary is deliberately source-only. It does not contain destination relationship parameters, source-selection policy, semantic exchangeability claims, numeric transfer or no-transfer gating.
- `CapabilityEquipmentContext.NotApplicable` is distinct from `Unresolved`; later semantic admissibility must still verify that not-applicable is legitimate for the specific source/destination relationship.
- Resolved equipment context must cover the exact selected upstream observations, preventing a partial current-equipment label from masquerading as the context for a mixed historical capability posterior.
- Cross-source independence is explicitly not established. Multiple source envelopes cannot be precision-combined as independent evidence merely because each one is individually valid.
- No M0 mathematics, numeric cross-profile relation or real-history transfer fit was started.

OPEN QUESTIONS:
- The deferred 7F-E canonical correction/invalidation slice is still structurally unfinished: session actual-use bindings, observation overrides and observation load semantics currently reject conflicting replacement rather than preserving an auditable correction chain.
- Dependency-scoped invalidation and explicit Room16 equipment backup/replay proof remain required before model fitting work advances.
- M0 mathematics must still be frozen in `NBIO_7F_M0_MODEL_SPEC.md` before any real-history M0 fit, but mission implementation order places the unfinished correction/persistence/backup/replay proof before that preregistration step.

HEAD OUT:
Source checkpoint `321ef02a28d2a78dd591c5c96a3b2ecf0e820186` is green in Android CI run 34046030542; this journal commit follows on the same branch.

NEXT:
Return to the deferred canonical-safety step before M0: implement auditable correction/supersession semantics for historical actual-equipment/load-semantics records, dependency-scoped invalidation for affected derived equipment/translation consumers, and explicit Room16 backup/replay proof. Do not create or fit M0 yet.

## Session 6 — 2026-09-06 — RECONSTRUCTED FROM COMMITTED SOURCE/CI

HEAD IN:
321ef02a28d2a78dd591c5c96a3b2ecf0e820186

OBJECTIVE:
Close the deferred 7F-E canonical correction/invalidation/migration/backup/replay gate, then freeze the exact M0 mathematics before any real-history M0 fitting.

SOURCE FINDINGS:
- Room16 base equipment assertions can remain immutable if corrections are represented as append-only epistemic overlays rather than destructive replacement.
- Correction invalidation needs stable canonical dependency roots naming the corrected concept, not one correction row, so every successor correction invalidates the same derived dependency without deleting raw performance evidence.
- Native full backup is schema-driven and can preserve Room17 canonical/correction tables when restore uses the exact current schema and foreign keys remain valid.
- The accepted Candidate-v2 Adaptive Sparse joint posterior is sufficient to define M0 as a nested directed source covariate without introducing a universal equipment/load coordinate.

CHANGES:
- Advanced the equipment-correction substrate to Room17 with additive correction ledgers for session actual equipment, observation equipment override and observation load semantics.
- Added append-only correction domain/entity/DAO surfaces and repository orchestration with contiguous versions, stale-previous-value rejection, non-decreasing correction time and explicit retraction to unknown where authorised.
- Added stable `EquipmentCanonicalDependencyId` roots and `EquipmentInvalidationImpact` so affected derived equipment/translation consumers can be invalidated without touching unrelated raw evidence.
- Historical resolution now applies the auditable correction chain over immutable base assertions while preserving observation-override → session-actual precedence and never consulting current preference.
- Added Room16→17 migration proof and Native equipment backup/restore/replay instrumentation coverage. The migration proof was made serializer-independent by comparing migrated table shape with the current Room schema rather than requiring a checked-in intermediate Room16 serializer snapshot.
- Added/updated CI to execute the targeted Room17 correction + Native backup proof on an Android emulator.
- Created `docs/n-bio-vnext/NBIO_7F_M0_MODEL_SPEC.md` and froze the exact directed M0 candidate before any real-history fitting.

TESTS:
- Exact source checkpoint `bf1e904f78275847b11f685b371cc921dae2332a` completed successfully in Android CI run 34052849605, including normal Android build/test/lint/schema gates and the dedicated emulator Room17 correction + Native backup/replay proof.
- Frozen-spec checkpoint `1e3d0f7822ae3f59c547740fb8e9b7978fdf2c1b` completed successfully in exact-head Android CI run 34054584318.
- No real-history M0 fit was run before or during preregistration.

DECISIONS:
- 7F-E's structural storage/correction/replay gate is complete. Canonical equipment corrections are auditable overlays; raw performance and Room16 base assertions are not rewritten.
- M0 is a directed extension of the destination Candidate-v2 frontier, not an equipment converter. `beta = 0` is defined to reproduce N0 exactly.
- Source uncertainty is propagated from the accepted joint source posterior through a deterministic joint-node coreset; the coreset budget is numerical approximation, not an evidence-count threshold.
- Unpaired destination sessions remain N0-only rather than being discarded. Same-session source evidence is excluded by the frozen causal cutoff.
- Explicit versioned directed relationship admissibility is mandatory; display-name/muscle/equipment-family similarity cannot self-authorise transfer.
- The frozen M0 config identity is `modelcfg_sha256_f4fa3fb165873df5407da1daefcb9bce3656caa9586ecefe3b35a0ca42c79961`. Material behavioural changes require a new candidate/config identity; negative outcomes survive.

HEAD OUT:
`1e3d0f7822ae3f59c547740fb8e9b7978fdf2c1b`

NEXT:
Implement the explicit N0 destination-only champion surface using the already selected Candidate-v2 + Adaptive Sparse stack. Do not write M0 inference until N0 is independently typed and tested.

## Session 7 — 2026-09-06 21:49 BST

HEAD IN:
1e3d0f7822ae3f59c547740fb8e9b7978fdf2c1b

OBJECTIVE:
Implement and prove the explicit N0 destination-only champion surface required by the frozen 7F model spec, without starting M0 numeric transfer.

SOURCE FINDINGS:
- N0 does not justify a second mathematical candidate or duplicate frontier fitter. It should bind the 7F baseline role to the already selected Candidate-v2 mathematics and Adaptive Sparse solver.
- The accepted `DynamicTrendFrontierFit` already preserves the complete joint destination posterior, mathematical identity, solver identity, evidence support and provenance needed by later M0 comparison.
- Making the N0 fit API accept only a destination projection is a stronger structural guarantee than accepting source inputs and promising to ignore them.

CHANGES:
- Added `NBio7FN0V1` with immutable destination-only role identity and the exact selected Candidate-v2/Adaptive Sparse configuration from the frozen M0 spec.
- Added `DynamicTransferN0Fit`, which wraps the complete accepted destination `DynamicTrendFrontierFit` and rejects mismatched mathematical or solver identity rather than copying/reducing posterior fields.
- Added `DynamicTransferN0Champion`, which fits the frozen Candidate-v1 base and selected Candidate-v2 Adaptive Sparse destination stack using destination evidence only, then exposes the normal accepted next-independent-session N0 projection.
- Added N0 unit tests proving exact role/model/solver/config identity, destination observation/session retention, next-session projection and rejection of a Conditional-Laplace fit masquerading as N0.
- Initial CI exposed one test-only compile error because `PosteriorSummary` names its median field `p50`, not `median`; corrected without changing production N0 behaviour.

TESTS:
- Initial source checkpoint `c0fbc33cc70f4f6da19e9767ac9112398ada25ea`, Android CI run 34058456662: production Kotlin/debug APK compilation reached the new unit-test compile, which failed only on the incorrect test field name `median`; the dedicated emulator storage/backup proof still completed successfully.
- Fix checkpoint `3c0dc4fc3f230b380560945b564aca39094686f4`, exact-head Android CI run 34058790643: all gates passed.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the new N0 tests.
- `:app:assembleDebugAndroidTest` passed.
- Android lint and Room17 exported-schema verification passed.
- Dedicated Room17 correction + Native backup/replay emulator proof passed again.

DECISIONS:
- N0 is an explicit evaluation role around the accepted Candidate-v2 + Adaptive Sparse stack, not a new model family/config and not `InferenceModelComponent.TRANSLATION`.
- N0 structurally cannot consume source capability because its fit boundary has no source parameter.
- N0 preserves the complete destination joint posterior for M0 rather than reducing it to summary moments.
- N0 retains the accepted upstream next-session extrapolation behaviour. M0's frozen no-extrapolation/source-domain rules remain separate and must not leak backwards into N0.
- Normal product authority remains `BENCHMARK_V0`; N0 is SHADOW/developer-only.

HEAD OUT:
`3c0dc4fc3f230b380560945b564aca39094686f4`

NEXT:
Implement the first frozen M0 structural/synthetic kernel: immutable M0 config identity, directed admissibility/source-anchor preparation, deterministic joint source coreset and beta quadrature, with `beta = 0` reproducing N0. Keep it synthetic-only and do not fit real history yet.

## Session 8 — 2026-09-06 — M0 STRUCTURAL CHECKPOINT

HEAD IN:
8d74035f5cb73f1d095a44b6350a163fcdeed555

OBJECTIVE:
Implement the first frozen M0 structural/synthetic kernel from the preregistered specification, prove the exact immutable identity and nested no-transfer algebra, and stop before any real-history fitting.

SOURCE FINDINGS:
- The existing 7F-C capability boundary already preserves the accepted Candidate-v2 joint source posterior and upstream identities needed by M0; no generic transfer tensor or source-summary reduction is required.
- The frozen M0 spec requires exact directed profile/version/side/equipment/load semantics, source-anchor no-extrapolation, deterministic joint source coreset K17, fixed GH7 beta quadrature, and `beta = 0` exact nesting inside N0.
- Canonical ModelConfig text is part of immutable candidate identity. JVM `Double.toString()` renders the smallest GH7 weights in scientific notation, which is numerically equivalent but does not reproduce the preregistered decimal payload.

CHANGES:
- Added `DynamicTransferM0Kernel.kt` with the frozen mathematical/solver/config identities, typed directed relationship descriptor, source/destination equipment and load-accounting admissibility, exact source-anchor construction, deterministic joint K17 coreset, training source-centre identifiability guard, fixed GH7 beta nodes/weights, and N0/M0 likelihood/prediction algebra.
- Added synthetic unit coverage for exact frozen identity, deterministic joint-node coreset retention, `beta = 0` N0 likelihood/prediction equivalence, source-centre variation guard, mixed/unknown semantics, unresolved equipment, reverse-edge rejection, source repetition extrapolation and bodyweight rejection.
- Initial source checkpoint `de5a59e2ef31cf47d98f398793f6c6545e3581c6` exposed only the canonical GH7 weight-string issue in CI; production compiled and 377/378 unit tests passed while the dedicated storage proof remained green.
- Fix checkpoint `517a3ee31539a77256d17c400fa9a2ff245f9285` serialises the GH7 weights using the exact frozen preregistered decimal literals while leaving the numeric quadrature values and model mathematics unchanged.

TESTS:
- Initial Android CI run 34062983794 on `de5a59e2...`: production/debug APK compilation succeeded; 377/378 unit tests passed; the single failure was the frozen ModelConfig identity guard caused by scientific-notation serialization; dedicated Room17 correction + Native backup/replay proof passed.
- Exact fix checkpoint `517a3ee31539a77256d17c400fa9a2ff245f9285` completed successfully in Android CI run 34065423268.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the frozen M0 config identity and `beta = 0` nesting tests.
- `:app:assembleDebugAndroidTest`, Android lint and Room17 exported-schema verification passed.
- Dedicated Room17 correction + Native backup/replay emulator proof passed again.
- No real-history M0 fitting occurred.

DECISIONS:
- The exact preregistered decimal GH7 payload is canonical identity text; runtime floating-point formatting is not allowed to rewrite it even when numerically equivalent.
- The M0 structural kernel remains a directed candidate extension around frozen N0, not an equipment converter or global capability scale.
- Source posterior tuples remain joint through coreset selection; no cross-source independence, precision combination or transitivity is introduced.
- M0 remains SHADOW/developer-only and real-history fitting stays locked until every required structural/synthetic test in the frozen spec is implemented and green.

HEAD OUT:
`517a3ee31539a77256d17c400fa9a2ff245f9285`

NEXT:
Complete the remaining preregistered M0 structural/synthetic proof surface before any real-history fit: destination-session atomic source freezing, future/same-session source exclusion, unpaired-destination zero M0 increment, destination M0 no-extrapolation while N0 remains available, explicit no-multi-source/no-transitivity enforcement, and correction-dependency invalidation/deterministic replay coverage.

## Session 9 — 2026-09-07 00:43 BST

HEAD IN:
3638613e3ff0144de1818b266f74555b10e1161d

OBJECTIVE:
Complete the remaining non-numerical preregistered M0 chronology, refusal-topology and correction-dependency proof surface before any real-history fit.

SOURCE FINDINGS:
- The existing 7F-C source boundary already carries both `causalCutoff.evidenceThrough` and selected source session ids, so strict pre-destination source freezing can be enforced without inventing a new provenance channel.
- The frozen M0 specification treats each destination independent session as one atomic prequential event: destination N0 and any paired source snapshot must both be frozen before the session's first observation, and the source cannot contain the held-out destination session identity.
- An unpaired destination session is not discarded; it remains represented by N0 and contributes exactly zero M0 likelihood-ratio increment.
- Multiple direct sources are separate candidates and no transitive path is a candidate. This can be enforced structurally without changing M0 mathematics.
- Existing canonical correction impact ids are sufficient to express dependency-scoped derived-M0 invalidation while leaving raw performance immutable.

CHANGES:
- Added `DynamicTransferM0SessionPolicy` and typed held-out destination/source-pairing states around the frozen M0 kernel.
- Paired freezing now requires destination N0 evidence and source evidence to be strictly prior to the destination session's first observation; the held-out session id must be absent from both frozen destination N0 and source support.
- Missing destination-N0 evidence time fails closed rather than inventing chronology.
- Added first-class unpaired freezing with exact zero M0 likelihood-ratio increment.
- Added `DynamicTransferM0CandidateTopology.requireSingleDirectEdge`, so one candidate boundary cannot consume multi-source lists or transitive two-edge paths.
- Added `DynamicTransferM0ReplayDependencyScope` over stable canonical dependency roots for correction-scoped derived invalidation.
- Added synthetic tests for future-source refusal, same-session refusal, valid prior-source pairing, zero unpaired increment, no multi-source/no-transitivity, explicit `DEVICE_ORDINAL` and assistance rejection, destination M0 repetition no-extrapolation while N0 remains evaluable, and correction-scoped invalidation with unchanged raw evidence.

TESTS:
- Initial source checkpoint `62c0adcef2275d5736b596ce24236731b957c935`, Android CI run 34066981872, failed production compilation because `DynamicTrendFrontierFit.support.lastEvidenceAt` is nullable. Both normal and emulator jobs stopped on that same compile error before their proof suites could execute; there was no independent storage/backup regression.
- Fix checkpoint `e4fc2ef3ed403f31e4c326a6864c42eb8d71523a` handles a missing destination evidence timestamp as an explicit fail-closed chronology case.
- Exact-head Android CI run 34067220584 on `e4fc2ef3...` completed successfully.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including all new M0 session-policy/refusal tests.
- `:app:assembleDebugAndroidTest`, Android lint and Room17 exported-schema verification passed.
- Dedicated Room17 correction + Native backup/replay emulator proof passed again.
- No real-history M0 fitting occurred.

DECISIONS:
- No M0 mathematics, quadrature, coreset, candidate identity or frozen ModelConfig text changed in this slice.
- A destination N0 without a usable last-evidence timestamp is not chronologically admissible for held-out M0 scoring.
- Unpaired destination sessions are preserved as N0-only observations with exact M0 increment zero.
- One M0 candidate consumes one explicit directed edge only. Multi-source precision combination and transitive paths remain structurally unavailable.
- Canonical corrections can invalidate only derived M0 state whose retained dependency roots intersect the correction impact; raw evidence is not mutated.
- Preregistered structural proofs for future/same-session exclusion, unpaired zero increment, explicit ordinal/assistance refusal, M0 destination rep-domain refusal, no transitivity, no multi-source combination and minimal correction-scoped invalidation are now covered.

OPEN QUESTIONS:
- Frozen section-11 full M0 posterior construction, held-out scoring/prediction mixture and exact deterministic full posterior/prediction replay remain the main unfinished synthetic gate. This is section-16 proof 17 and must be green before real-history fitting is unlocked.
- An accidental throwaway remote branch `__invalid_do_not_create__` was created during connector probing at `3638613e...`. It has no source changes and does not affect the working branch, but the available connector exposes no branch-delete action.

HEAD OUT:
Source checkpoint `e4fc2ef3ed403f31e4c326a6864c42eb8d71523a` is fully green in Android CI run 34067220584; this journal commit follows on the same branch.

NEXT:
Implement the frozen section-11 M0 posterior construction and future-prediction mixture as a synthetic-only deterministic replay surface, retain complete replay provenance/dependency roots, and prove identical posterior/prediction on replay before any real-history fitting.

## Session 10 — 2026-09-07 — M0 POSTERIOR REPLAY CHECKPOINT

HEAD IN:
f1b8b6c1c3ec11515ceb513698742423d51f3959

OBJECTIVE:
Implement the frozen section-11 M0 posterior construction and section-12 future-prediction mixture as a pure synthetic replay surface, retain sufficient immutable inputs/dependencies for exact replay, and complete preregistered proof 17 without fitting real history.

SOURCE FINDINGS:
- The frozen model explicitly defines M0 as likelihood-ratio reweighting of the complete destination N0 joint posterior crossed with the fixed GH7 beta prior; no second destination fitter is required or permitted.
- Unpaired destination sessions must remain structurally represented in the complete N0 history but contribute no M0 likelihood-ratio information.
- Historical source anchors must share the final destination reference-repetition coordinate before the fixed training-history source centre is computed.
- Future prediction crosses the fitted destination-node/beta-node posterior with exactly one current admissible source coreset. The prediction source cannot recenter the fitted relationship.
- Existing 7F-C source envelopes plus explicit relationship/equipment/load context and canonical correction dependency roots already contain the information needed for lossless in-memory deterministic replay; persistence of this derived state remains a later mission step.

CHANGES:
- Added `DynamicTransferM0PosteriorReplay.kt` with typed historical paired/unpaired destination-session replay inputs, exact selected-observation coverage checks and frozen Candidate-v2 session-coordinate validation.
- Implemented section-11 `Delta_jm` likelihood-ratio accumulation using equal total weight per paired destination session and log-sum-exp normalisation of `W_jm`.
- Re-prepared every historical source snapshot through the existing directed-edge kernel at the final destination `r_D`, then froze one training-only source centre across paired sessions.
- Added a full M0 posterior node carrying the original destination joint node, fixed beta node, likelihood-ratio increment and normalised M0 weight without reducing either posterior to marginal summaries.
- Added section-12 future predictive mixture components with exact `W_jm * w_source,k` weights, next-session frontier, destination slack/noise scales and current source original-node identity.
- Retained full fit/prediction replay inputs, relationship identity, source coresets and unioned canonical correction dependency scopes; added pure replay entry points that recompute posterior and prediction from those retained inputs.
- Added synthetic tests proving exact posterior replay, exact prediction replay, beta-zero N0 nesting, explicit unpaired-zero information, complete destination-history requirement, dependency retention and rejection of a future prediction source snapshot.
- No persistence, source-selection policy, real-history fit or normal-product behaviour was added.

TESTS:
- Exact source checkpoint `e9a3338292442d2fb2ac7bd97c0cf56dd33ede53` completed successfully in Android CI run 34069748802.
- `:app:testDebugUnitTest :app:assembleDebug` passed, including the new section-11/12 deterministic replay tests.
- `:app:assembleDebugAndroidTest` passed.
- Android lint and Room17 exported-schema verification passed.
- Dedicated Room17 correction + Native backup/replay emulator proof passed again.
- The new tests replayed the same full M0 posterior/prediction exactly from retained canonical inputs and frozen identities; section-16 proof 17 is therefore green synthetically.
- No real-history M0 fitting occurred.

DECISIONS:
- M0 posterior construction remains a pure nested extension of frozen N0; no mathematical, quadrature, coreset, prior or ModelConfig identity changed.
- Unpaired sessions are required in replay coverage but are deliberately absent from `Delta_jm` accumulation, preserving their exact zero likelihood-ratio contribution.
- The training source centre is computed only from admissible historical pairs and is never recomputed from the prediction source snapshot.
- Predictive components retain the destination slack/noise parameters rather than replacing the frozen observation model with point frontier predictions.
- Full deterministic replay is now proven in-memory. Derived candidate persistence/replay across Room/backup boundaries remains a separate later mission step and is not implied by this proof.
- The frozen specification's structural/synthetic prerequisite tests are now complete, but mission order still keeps real-history M0 evaluation deferred until source-selection policy, broader synthetic validation, persistence and prequential evaluation surfaces are built.

HEAD OUT:
Source checkpoint `e9a3338292442d2fb2ac7bd97c0cf56dd33ede53` is fully green in Android CI run 34069748802; this journal commit follows on the same branch.

NEXT:
Implement the explicit versioned M0 admissibility/source-selection policy for multiple potential direct sources, preserving one source per candidate, deterministic pre-outcome selection and no cross-source independence/transitivity. Then extend synthetic validation around that policy before any real-history evaluation.
