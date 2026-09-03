package dev.kian.mymettle.inference

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.context.modules.EpisodeAssociationModuleV1
import dev.kian.mymettle.context.modules.ProductionContextFeaturesV7E
import dev.kian.mymettle.context.modules.ProductionContextModuleRegistryV7E
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.context.ContextEvidenceMaturity
import dev.kian.mymettle.domain.context.ContextModuleRegistryV7E
import dev.kian.mymettle.domain.context.ContextScope
import dev.kian.mymettle.domain.context.ContextSignalEffectRepresentation
import dev.kian.mymettle.domain.context.ContextSignalStatus
import dev.kian.mymettle.domain.context.ContextSignalTarget
import dev.kian.mymettle.domain.context.ContextSignalV1
import dev.kian.mymettle.domain.inference.NeutralTemporalStateFilterV1
import dev.kian.mymettle.domain.inference.TemporalCandidateLayer
import java.time.Instant
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7EShadowRepositoryTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var registry: ContextModuleRegistryV7E
    private lateinit var repository: NBio7EShadowRepository

    @BeforeTest
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyMettleDatabase::class.java,
        ).build()
        registry = ContextModuleRegistryV7E(ProductionContextModuleRegistryV7E.providers)
        repository = NBio7EShadowRepository(database.nBio7EDao(), registry)
        seedParents()
    }

    @AfterTest
    fun close() = database.close()

    @Test
    fun completeRunRoundTripsAndIndividualDerivedStoresCanBeDeleted() = runBlocking {
        val at = Instant.parse("2026-01-02T00:00:00Z")
        val signal = ContextSignalV1(
            signalId = "signal",
            sourceModuleId = EpisodeAssociationModuleV1.MODULE_ID,
            moduleModelVersion = "episode-normal-association-v1",
            moduleConfigId = "illness-episode-association-config-v1",
            sourceFeatureKey = ProductionContextFeaturesV7E.illness.key,
            target = ContextSignalTarget.SYSTEMIC_TRANSIENT_STATE,
            scope = ContextScope.SYSTEMIC,
            effectiveFrom = at,
            effectiveUntil = at.plusSeconds(86_400),
            effectRepresentation = ContextSignalEffectRepresentation.LOG_PERFORMANCE_LOCATION_SHIFT,
            locationMean = -0.02,
            variance = 0.04,
            evidenceRowCount = 1,
            independentSessionCount = 1,
            independentEpisodeCount = 1,
            evidenceMaturity = ContextEvidenceMaturity.PRIOR_DOMINATED,
            correlationGroupId = "systemic_wellbeing_episode",
            episodeId = "episode",
            sourceEvidenceIds = setOf("evidence:with:semicolon;safe"),
            upstreamModelIdentities = setOf("PD-001:OPEN", "PD-002:OPEN"),
            publishedAt = at,
            status = ContextSignalStatus.PRIOR_DOMINATED,
        )
        val moduleStates = registry.modules.associate { it.descriptor.moduleId to it.initialState() }
        val run = NBio7EShadowRunV1(
            id = "7e-run",
            userProfileId = "user",
            sourceInferenceRunId = "source-run",
            temporalModelConfigId = "temporal-config",
            calculatedAt = at,
            temporalStates = listOf(
                NBio7ETemporalStateRecordV1(
                    TemporalCandidateLayer.CONTEXT_TEMPORAL,
                    ContextScope.SYSTEMIC,
                    NeutralTemporalStateFilterV1().initial(at),
                ),
            ),
            moduleStates = moduleStates,
            moduleEvidenceThrough = moduleStates.keys.associateWith { at },
            signals = listOf(signal),
            failures = emptyList(),
        )

        repository.save(run)
        val loaded = assertNotNull(repository.load(run.id))
        assertEquals(run.temporalStates, loaded.temporalStates)
        assertEquals(run.moduleStates, loaded.moduleStates)
        assertEquals(run.signals, loaded.signals)
        assertTrue(database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })

        repository.deleteSignals(run.id)
        assertTrue(assertNotNull(repository.load(run.id)).signals.isEmpty())
        repository.deleteModuleMemory(run.id)
        assertTrue(assertNotNull(repository.load(run.id)).moduleStates.isEmpty())
        repository.deleteDerivedRun(run.id)
        assertNull(repository.load(run.id))
    }

    private fun seedParents() {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO user_profile(id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                "VALUES ('user', 'Fixture', 'metric', 'omnivore', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO reference_profile(id, version, populationSex, populationAgeSummary, populationDescription, datasetVersion, modelVersion) " +
                "VALUES ('reference', 1, 'mixed', 'adult', 'fixture', 'fixture', 'fixture')",
        )
        db.execSQL("INSERT INTO inference_model_manifest(id, createdAt) VALUES ('manifest', '2026-01-01T00:00:00Z')")
        db.execSQL(
            """
            INSERT INTO inference_run(
                id, userProfileId, modelVersion, referenceProfileId, referenceProfileVersion,
                referenceModelVersion, recruitmentModelVersion, stimulusModelVersion,
                muscleStateModelVersion, exerciseTranslationModelVersion, modelManifestId,
                executionMode, semanticsMode, calculatedAt, evidenceThrough, evidenceSetCount,
                evidenceObservationCount, effectiveIndependentSessionCount
            ) VALUES (
                'source-run', 'user', '7d', 'reference', 1, 'reference', 'recruitment', 'stimulus',
                'state', 'translation', 'manifest', 'shadow_candidate', 'typed_v7',
                '2026-01-01T00:00:00Z', NULL, 0, 0, 0
            )
            """.trimIndent(),
        )
    }
}
