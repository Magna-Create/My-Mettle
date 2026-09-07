package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.equipment.EquipmentCanonicalDependencyId
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CapabilityEquipmentContext
import dev.kian.mymettle.domain.inference.CapabilitySourceProfileSemantics
import dev.kian.mymettle.domain.inference.CapabilityTransferSource
import dev.kian.mymettle.domain.inference.CapabilityTransferSourceFactory
import dev.kian.mymettle.domain.inference.DynamicMetricEvidenceAudit
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidence
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidenceProjection
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
import dev.kian.mymettle.domain.inference.DynamicTrendFrontierFit
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.inference.DirectedDynamicTransferRelationshipDescriptor
import dev.kian.mymettle.engine.inference.DynamicTransferM0DestinationContext
import dev.kian.mymettle.engine.inference.DynamicTransferM0HistoricalPairing
import dev.kian.mymettle.engine.inference.DynamicTransferM0LoadAccountingContext
import dev.kian.mymettle.engine.inference.DynamicTransferM0ObservedDestinationPoint
import dev.kian.mymettle.engine.inference.DynamicTransferM0PosteriorReplay
import dev.kian.mymettle.engine.inference.DynamicTransferM0PosteriorReplayInput
import dev.kian.mymettle.engine.inference.DynamicTransferM0PredictionReplayInput
import dev.kian.mymettle.engine.inference.DynamicTransferM0ReplayDependencyScope
import dev.kian.mymettle.engine.inference.DynamicTransferM0SourceSelectionPolicies
import dev.kian.mymettle.engine.inference.DynamicTransferM0TrainingSession
import dev.kian.mymettle.engine.inference.DynamicTransferN0Champion
import dev.kian.mymettle.engine.inference.DynamicTransferN0Fit
import dev.kian.mymettle.inference.DynamicTransferM0DerivedRepository
import dev.kian.mymettle.inference.DynamicTransferM0DerivedSnapshotCodec
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7FM0DerivedBackupReplayTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var backupRepository: NativeFullBackupRepository
    private lateinit var derivedRepository: DynamicTransferM0DerivedRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        backupRepository = NativeFullBackupRepository(database)
        derivedRepository = DynamicTransferM0DerivedRepository(database)
        seedUserProfile()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun room18BackupDeleteDerivedRestoreReplaysExactM0FitAndPrediction() = runBlocking {
        val fixture = fixture()
        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val prediction = DynamicTransferM0PosteriorReplay.predict(
            fit = fit,
            input = DynamicTransferM0PredictionReplayInput(
                predictionCutoff = BASE.plusSeconds(8 * DAY_SECONDS),
                source = fixture.currentSource,
                sourceLoadAccounting = stableLoad(fixture.currentSource.selectedObservationIds.toSet()),
                relationship = fit.relationship,
                queryRepetitions = 8.0,
                sourceReplayDependencyScope = dependencies(fixture.currentSource.selectedObservationIds),
            ),
        )
        val policy = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
            policyId = "derived-backup-independent",
            version = 1,
            frozenAt = BASE.plusSeconds(DAY_SECONDS / 2),
        )
        val fitSnapshot = DynamicTransferM0DerivedSnapshotCodec.snapshotFit(fit, policy, CREATED_AT)
        val predictionSnapshot = DynamicTransferM0DerivedSnapshotCodec.snapshotPrediction(prediction, policy, CREATED_AT)

        derivedRepository.persist(fitSnapshot)
        derivedRepository.persist(predictionSnapshot)
        DynamicTransferM0DerivedSnapshotCodec.verifyReplay(derivedRepository.load(fitSnapshot.id), fit, policy)
        DynamicTransferM0DerivedSnapshotCodec.verifyReplay(
            derivedRepository.load(predictionSnapshot.id),
            prediction,
            policy,
        )
        val canonicalBefore = userProfileRow()

        val backup = backupRepository.exportJson(pretty = false)
        val tableNames = JSONObject(backup).getJSONArray("tables").let { tables ->
            buildSet {
                for (index in 0 until tables.length()) add(tables.getJSONObject(index).getString("name"))
            }
        }
        assertTrue("n_bio_7f_m0_derived_state" in tableNames)
        assertTrue("n_bio_7f_m0_derived_dependency" in tableNames)

        assertEquals(2, derivedRepository.deleteAllDerived())
        assertTrue(derivedRepository.stateIds().isEmpty())
        assertEquals(canonicalBefore, userProfileRow())

        val restored = backupRepository.restoreJson(backup)
        assertEquals(18, restored.schemaVersion)
        assertEquals(setOf(fitSnapshot.id, predictionSnapshot.id), derivedRepository.stateIds().toSet())
        DynamicTransferM0DerivedSnapshotCodec.verifyReplay(derivedRepository.load(fitSnapshot.id), fit, policy)
        DynamicTransferM0DerivedSnapshotCodec.verifyReplay(
            derivedRepository.load(predictionSnapshot.id),
            prediction,
            policy,
        )
        assertEquals(canonicalBefore, userProfileRow())
        assertTrue(database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    @Test
    fun unknownCodecPayloadSchemaAndModelIdentityFailClosedAfterRestore() = runBlocking {
        val fixture = fixture()
        val fit = DynamicTransferM0PosteriorReplay.fit(fixture.replayInput)
        val policy = DynamicTransferM0SourceSelectionPolicies.scoreIndependently(
            policyId = "derived-backup-fail-closed",
            version = 1,
            frozenAt = BASE.plusSeconds(DAY_SECONDS / 2),
        )
        val snapshot = DynamicTransferM0DerivedSnapshotCodec.snapshotFit(fit, policy, CREATED_AT)
        derivedRepository.persist(snapshot)
        val backup = backupRepository.exportJson(pretty = false)
        val sqlite = database.openHelper.writableDatabase

        backupRepository.restoreJson(backup)
        sqlite.execSQL(
            "UPDATE n_bio_7f_m0_derived_state SET codecId='unknown-codec' WHERE id=?",
            arrayOf(snapshot.id),
        )
        assertLoadFails(snapshot.id)

        backupRepository.restoreJson(backup)
        sqlite.execSQL(
            "UPDATE n_bio_7f_m0_derived_state SET payloadSchemaVersion=999 WHERE id=?",
            arrayOf(snapshot.id),
        )
        assertLoadFails(snapshot.id)

        backupRepository.restoreJson(backup)
        sqlite.execSQL(
            "UPDATE n_bio_7f_m0_derived_state SET modelConfigId='unknown-model-config' WHERE id=?",
            arrayOf(snapshot.id),
        )
        assertLoadFails(snapshot.id)
    }

    private suspend fun assertLoadFails(id: String) {
        val failure = runCatching { derivedRepository.load(id) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "Corrupt/unknown derived state must fail closed, not load.")
    }

    private fun fixture(): Fixture {
        val destinationEvidence = generated(
            profile = DESTINATION_PROFILE,
            prefix = "destination",
            sessionDays = listOf(2, 4, 6),
            baseFrontierKg = 78.0,
            trendPerSession = 0.03,
        )
        val rawDestinationN0 = champion.fit(
            destinationProjection = projection(DESTINATION_PROFILE, destinationEvidence),
            inferenceHorizon = BASE.plusSeconds(7 * DAY_SECONDS),
            configCreatedAt = CONFIG_CREATED_AT,
        )
        val destinationN0 = DynamicTransferN0Fit(trimPosterior(rawDestinationN0.destinationFit))
        val destination = DynamicTransferM0DestinationContext(
            n0 = destinationN0,
            profile = CapabilitySourceProfileSemantics.from(DESTINATION_PROFILE),
            equipmentContext = CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = DESTINATION_EQUIPMENT,
                interpretationVersion = "destination-local-v1",
                contributingObservationIds = destinationN0.destinationFit.selectedObservationIds.toSet(),
                equipmentFactVersionIds = setOf("destination-fact-v1"),
            ),
            loadAccounting = stableLoad(destinationN0.destinationFit.selectedObservationIds.toSet()),
        )

        val sourceEvidence = generated(
            profile = SOURCE_PROFILE,
            prefix = "source",
            sessionDays = listOf(1, 3),
            baseFrontierKg = 66.0,
            trendPerSession = 0.15,
        )
        val sourceOne = sourceBoundary(
            evidence = sourceEvidence.filter { it.sessionId == "source-session-0" },
            horizon = BASE.plusSeconds(4 * DAY_SECONDS - 1),
        )
        val sourceTwo = sourceBoundary(
            evidence = sourceEvidence,
            horizon = BASE.plusSeconds(6 * DAY_SECONDS - 1),
        )
        val relationship = relationship(sourceOne, destination)
        assertEquals(relationship, relationship(sourceTwo, destination))

        val destinationBySession = destinationEvidence.groupBy { it.sessionId }
        val sessions = listOf(
            trainingSession(
                evidence = destinationBySession.getValue("destination-session-0"),
                offset = -2,
                pairing = DynamicTransferM0HistoricalPairing.Unpaired("no admissible prior source snapshot"),
            ),
            trainingSession(
                evidence = destinationBySession.getValue("destination-session-1"),
                offset = -1,
                pairing = paired(sourceOne, relationship),
            ),
            trainingSession(
                evidence = destinationBySession.getValue("destination-session-2"),
                offset = 0,
                pairing = paired(sourceTwo, relationship),
            ),
        )
        return Fixture(
            replayInput = DynamicTransferM0PosteriorReplayInput(
                destination = destination,
                trainingSessions = sessions,
                destinationReplayDependencyScope = dependencies(destinationN0.destinationFit.selectedObservationIds),
            ),
            currentSource = sourceTwo,
        )
    }

    private fun paired(
        source: CapabilityTransferSource,
        relationship: DirectedDynamicTransferRelationshipDescriptor,
    ) = DynamicTransferM0HistoricalPairing.Paired(
        source = source,
        sourceLoadAccounting = stableLoad(source.selectedObservationIds.toSet()),
        relationship = relationship,
        replayDependencyScope = dependencies(source.selectedObservationIds),
    )

    private fun trainingSession(
        evidence: List<DynamicResistanceEvidence>,
        offset: Int,
        pairing: DynamicTransferM0HistoricalPairing,
    ): DynamicTransferM0TrainingSession {
        val ordered = evidence.sortedBy { it.completedAt }
        return DynamicTransferM0TrainingSession(
            sessionId = ordered.first().sessionId,
            firstObservationTime = ordered.first().completedAt,
            destinationSessionOffset = offset,
            observations = ordered.map {
                DynamicTransferM0ObservedDestinationPoint(
                    observationId = it.observationId,
                    repetitions = it.repetitions.toDouble(),
                    resistanceKg = it.resistance.value,
                )
            },
            pairing = pairing,
        )
    }

    private fun sourceBoundary(
        evidence: List<DynamicResistanceEvidence>,
        horizon: Instant,
    ): CapabilityTransferSource {
        val fit = trimPosterior(
            champion.fit(
                destinationProjection = projection(SOURCE_PROFILE, evidence),
                inferenceHorizon = horizon,
                configCreatedAt = CONFIG_CREATED_AT,
            ).destinationFit,
        )
        return CapabilityTransferSourceFactory.fromDynamicTrendFit(
            profile = SOURCE_PROFILE,
            fit = fit,
            equipmentContext = CapabilityEquipmentContext.ResolvedSingleContext(
                equipmentId = SOURCE_EQUIPMENT,
                interpretationVersion = "source-local-v1",
                contributingObservationIds = fit.selectedObservationIds.toSet(),
                equipmentFactVersionIds = setOf("source-fact-v1"),
            ),
        )
    }

    private fun trimPosterior(fit: DynamicTrendFrontierFit): DynamicTrendFrontierFit {
        val selected = fit.posteriorNodes.sortedByDescending { it.posteriorWeight }.take(3)
        val total = selected.sumOf { it.posteriorWeight }
        return fit.copy(
            posteriorNodes = selected.map { it.copy(posteriorWeight = it.posteriorWeight / total) },
            posteriorEffectiveNodeCount = minOf(3.0, fit.posteriorEffectiveNodeCount),
        )
    }

    private fun relationship(
        source: CapabilityTransferSource,
        destination: DynamicTransferM0DestinationContext,
    ): DirectedDynamicTransferRelationshipDescriptor {
        val sourceEquipment = source.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        val destinationEquipment = destination.equipmentContext as CapabilityEquipmentContext.ResolvedSingleContext
        return DirectedDynamicTransferRelationshipDescriptor(
            relationshipId = "derived-backup-source-to-destination",
            version = 1,
            policyIdentity = "fixture-explicit-directed-v1",
            sourceExecutionProfileId = source.profile.executionProfileId,
            sourceExecutionProfileVersionId = source.profile.executionProfileVersionId,
            destinationExecutionProfileId = destination.profile.executionProfileId,
            destinationExecutionProfileVersionId = destination.profile.executionProfileVersionId,
            side = Laterality.BILATERAL,
            sourceEquipmentId = sourceEquipment.equipmentId,
            sourceEquipmentInterpretationVersion = sourceEquipment.interpretationVersion,
            sourceEquipmentFactVersionIds = sourceEquipment.equipmentFactVersionIds,
            destinationEquipmentId = destinationEquipment.equipmentId,
            destinationEquipmentInterpretationVersion = destinationEquipment.interpretationVersion,
            destinationEquipmentFactVersionIds = destinationEquipment.equipmentFactVersionIds,
            sourceLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
            destinationLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
        )
    }

    private fun stableLoad(observationIds: Set<String>) = DynamicTransferM0LoadAccountingContext.StableKnown(
        accounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
        contributingObservationIds = observationIds,
    )

    private fun dependencies(observationIds: List<String>) = DynamicTransferM0ReplayDependencyScope(
        observationIds.map { EquipmentCanonicalDependencyId.observationLoadSemantics(it) }.toSet(),
    )

    private fun generated(
        profile: DynamicResistanceProfileSemantics,
        prefix: String,
        sessionDays: List<Int>,
        baseFrontierKg: Double,
        trendPerSession: Double,
    ): List<DynamicResistanceEvidence> = buildList {
        sessionDays.forEachIndexed { session, day ->
            listOf(6, 8, 12).forEachIndexed { ordinal, reps ->
                val logFrontierAtEight = ln(baseFrontierKg) + trendPerSession * session
                val resistance = exp(logFrontierAtEight - 0.17 * ln(reps / 8.0) - 0.03)
                add(set(profile, "$prefix-${session}_$ordinal", "$prefix-session-$session", reps, resistance, day, ordinal))
            }
        }
    }

    private fun projection(
        profile: DynamicResistanceProfileSemantics,
        evidence: List<DynamicResistanceEvidence>,
    ) = DynamicResistanceEvidenceProjection(
        profile = profile,
        side = Laterality.BILATERAL,
        evidence = evidence,
        exclusions = emptyList(),
        referenceRepetitions = null,
        policy = DynamicResistanceV2Contract.evidencePolicy,
    )

    private fun set(
        profile: DynamicResistanceProfileSemantics,
        id: String,
        sessionId: String,
        reps: Int,
        resistanceKg: Double,
        day: Int,
        ordinal: Int,
    ): DynamicResistanceEvidence {
        val load = Quantity(resistanceKg, UnitId.KILOGRAM)
        return DynamicResistanceEvidence(
            observationId = "obs-$id",
            setRecordId = "set-$id",
            sessionId = sessionId,
            executionProfileVersionId = profile.executionProfileVersionId,
            side = Laterality.BILATERAL,
            completedAt = BASE.plusSeconds(day.toLong() * DAY_SECONDS + ordinal),
            repetitions = reps,
            resistance = dev.kian.mymettle.domain.inference.ProfileLocalResistanceCoordinate(
                value = resistanceKg,
                unit = UnitId.KILOGRAM,
                resistanceSemantics = ResistanceSemantics.EXTERNAL,
                entryBasis = EntryBasis.TOTAL,
                resistanceModelVersion = "m0-derived-backup-resistance-v1",
                resolverVersion = DynamicResistanceV2Contract.evidencePolicy.resistanceCoordinateResolverVersion,
            ),
            metricEvidence = listOf(
                DynamicMetricEvidenceAudit(
                    metric = PerformanceMetric.EXTERNAL_LOAD,
                    entered = load,
                    canonical = load,
                    acquisitionMethod = "synthetic",
                    evidenceGranularity = "set",
                ),
            ),
            warmUp = false,
            setKind = "working",
            evidencePolicyIdentity = DynamicResistanceV2Contract.evidencePolicy.identity,
        )
    }

    private fun seedUserProfile() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO user_profile (id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                "VALUES ('user', 'Derived backup fixture', 'metric', 'unspecified', 1, ?, ?)",
            arrayOf(BASE.toString(), BASE.toString()),
        )
    }

    private fun userProfileRow(): List<String> = database.openHelper.writableDatabase.query(
        "SELECT id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt FROM user_profile WHERE id='user'",
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        listOf(
            cursor.getString(0),
            cursor.getString(1),
            cursor.getString(2),
            cursor.getString(3),
            cursor.getInt(4).toString(),
            cursor.getString(5),
            cursor.getString(6),
        )
    }

    private data class Fixture(
        val replayInput: DynamicTransferM0PosteriorReplayInput,
        val currentSource: CapabilityTransferSource,
    )

    companion object {
        private const val DAY_SECONDS = 86_400L
        private val BASE = Instant.parse("2026-01-01T00:00:00Z")
        private val CONFIG_CREATED_AT = Instant.parse("2026-09-06T00:00:00Z")
        private val CREATED_AT = Instant.parse("2026-09-07T00:00:00Z")
        private val SOURCE_EQUIPMENT = EquipmentId("m0-derived-backup-source-equipment")
        private val DESTINATION_EQUIPMENT = EquipmentId("m0-derived-backup-destination-equipment")
        private val champion = DynamicTransferN0Champion()
        private val SOURCE_PROFILE = profile("m0-derived-backup-source-profile", "m0-derived-backup-source-version")
        private val DESTINATION_PROFILE = profile(
            "m0-derived-backup-destination-profile",
            "m0-derived-backup-destination-version",
        )

        private fun profile(profileId: String, versionId: String) = DynamicResistanceProfileSemantics(
            executionProfileVersionId = ExecutionProfileVersionId(versionId),
            executionProfileId = ExecutionProfileId(profileId),
            metricFamily = MetricFamily.DYNAMIC_RESISTANCE,
            resistanceModel = ResistanceModel(
                modelVersion = "m0-derived-backup-resistance-v1",
                semantics = ResistanceSemantics.EXTERNAL,
                bodyweightCoefficient = 0.0,
                externalLoadCoefficient = 1.0,
                assistanceCoefficient = 0.0,
            ),
            entryBasis = EntryBasis.TOTAL,
            lateralityMode = LateralityMode.BILATERAL_ONLY,
        )
    }
}
