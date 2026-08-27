package dev.kian.mymettle.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.entity.InferenceRunEntity
import dev.kian.mymettle.data.local.entity.UserProfileEntity
import dev.kian.mymettle.data.reference.ReferenceSeedCallback
import dev.kian.mymettle.domain.inference.InferenceExecutionMode
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.InferenceSemanticsMode
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.ModelConfigId
import dev.kian.mymettle.domain.inference.REQUIRED_NBIO7_COMPONENTS
import dev.kian.mymettle.inference.RoomInferenceRepository
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class NBio7FoundationRoomTest {
    private lateinit var database: MyMettleDatabase

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java)
            .addCallback(ReferenceSeedCallback(context))
            .build()
        database.openHelper.writableDatabase
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun modelConfigManifestAndInferenceRunRoundTripWithoutPromotingShadow() = runBlocking {
        database.workoutDao().upsertProfile(UserProfileEntity("user_1", "Tester", "kg", "none", 1, NOW, NOW))
        val repository = RoomInferenceRepository(
            database = database,
            clock = { Instant.parse(NOW) },
            idFactory = { "run_benchmark" },
        )
        val first = repository.recomputeFromRawHistory()
        val dao = database.inferenceDao()

        assertEquals(InferenceExecutionMode.BENCHMARK_V0, first.run.executionMode)
        assertEquals(InferenceSemanticsMode.HISTORICAL_SEMANTICS, first.run.semanticsMode)
        assertEquals(0, first.run.evidenceObservationCount)
        assertEquals(0, first.run.effectiveIndependentSessionCount)
        val manifest = assertNotNull(first.modelManifest)
        assertEquals(REQUIRED_NBIO7_COMPONENTS, manifest.entries.keys)
        assertEquals(REQUIRED_NBIO7_COMPONENTS.size, first.modelConfigs.size)

        val reloaded = assertNotNull(repository.latestSnapshot())
        assertEquals(first.run, reloaded.run)
        assertEquals(manifest.id, reloaded.modelManifest?.id)
        assertEquals(first.modelConfigs.map { it.id }.toSet(), reloaded.modelConfigs.map { it.id }.toSet())

        val originalConfig = first.modelConfigs.first { it.component == InferenceModelComponent.EXPOSURE }
        val persisted = assertNotNull(dao.modelConfigDefinition(originalConfig.id.value))
        val roundTrip = ModelConfigDefinition.restore(
            id = ModelConfigId(persisted.id),
            component = InferenceModelComponent.fromStorage(persisted.component),
            modelFamily = persisted.modelFamily,
            modelName = persisted.modelName,
            semanticVersion = persisted.semanticVersion,
            configSchemaVersion = persisted.configSchemaVersion,
            canonicalConfigPayload = persisted.canonicalConfigPayload,
            createdAt = Instant.parse(persisted.createdAt),
            effectiveAt = persisted.effectiveAt?.let(Instant::parse),
        )
        assertEquals(originalConfig.id, roundTrip.id)
        assertEquals(originalConfig.canonicalConfigPayload, roundTrip.canonicalConfigPayload)
        assertFailsWith<Exception> {
            dao.insertModelConfigDefinition(persisted.copy(canonicalConfigPayload = persisted.canonicalConfigPayload + "\nmutated=true"))
        }

        val benchmarkEntity = assertNotNull(dao.inferenceRun("run_benchmark"))
        dao.insertInferenceRun(
            benchmarkEntity.copy(
                id = "run_shadow",
                executionMode = InferenceExecutionMode.SHADOW.storageValue,
                calculatedAt = "2026-08-27T01:00:00Z",
            ),
        )
        assertEquals(
            setOf(InferenceExecutionMode.BENCHMARK_V0.storageValue, InferenceExecutionMode.SHADOW.storageValue),
            dao.inferenceRuns("user_1").mapTo(hashSetOf()) { it.executionMode },
        )
        assertEquals("run_benchmark", repository.latestSnapshot()?.run?.id?.value)

        dao.deleteInferenceRun("run_shadow")
        assertEquals(listOf("run_benchmark"), dao.inferenceRuns("user_1").map { it.id })
        assertTrue(dao.inferenceModelManifestEntries(manifest.id.value).isNotEmpty())
    }

    private companion object {
        const val NOW = "2026-08-27T00:00:00Z"
    }
}
