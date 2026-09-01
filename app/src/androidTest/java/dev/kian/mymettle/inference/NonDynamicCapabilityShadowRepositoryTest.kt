package dev.kian.mymettle.inference

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExecutionProfileVersionId
import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.LoadedHoldCapabilityQuery
import dev.kian.mymettle.domain.inference.NonDynamicCapabilityV1
import dev.kian.mymettle.domain.inference.NonDynamicProfileSemantics
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.PerformanceMetricValue
import dev.kian.mymettle.domain.performance.Quantity
import dev.kian.mymettle.domain.performance.ResistanceModel
import dev.kian.mymettle.domain.performance.ResistanceSemantics
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.engine.performance.NonDynamicAdaptiveSparseSolver
import dev.kian.mymettle.engine.performance.NonDynamicCapabilityEvidenceProjector
import java.time.Instant
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NonDynamicCapabilityShadowRepositoryTest {
    private lateinit var database: MyMettleDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        seedMinimumForeignKeys()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistReloadDeleteAndFullReplayPreserve7CScientificState() = runBlocking {
        val profile = profile()
        val solver = NonDynamicAdaptiveSparseSolver(NonDynamicCapabilityV1.loadedHold)
        val evidence = List(5) { session -> observation(profile, session, 22.0 + session, 30.0) }
        val projection = NonDynamicCapabilityEvidenceProjector.project(profile, Laterality.UNKNOWN, evidence)
        val horizon = projection.evidence.maxOf { it.completedAt }
        val fit = solver.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
        val repository = NonDynamicCapabilityShadowRepository(
            database = database,
            solver = solver,
            clock = { T1_INSTANT },
            idFactory = { "shadow-7c-hold" },
        )

        val runId = repository.persist("user", fit)
        val loaded = repository.load(runId)
        assertTrue(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, loaded))
        val originalPrediction = solver.predict(fit, LoadedHoldCapabilityQuery(30.0)).summary!!
        val loadedPrediction = repository.predictReloaded(runId, LoadedHoldCapabilityQuery(30.0)).summary!!
        assertSummaryClose(originalPrediction, loadedPrediction)
        assertEquals(1, rowCount("capability_state"))
        assertEquals(1, rowCount("capability_parameter_state"))

        repository.discard(runId)
        assertEquals(0, rowCount("capability_state"))
        assertEquals(0, rowCount("capability_parameter_state"))
        assertFails { runBlocking { repository.load(runId) } }
        assertEquals(1, rowCount("user_profile"))
        assertEquals(1, rowCount("execution_profile_version"))

        val replay = solver.fit(projection, horizon, NonDynamicCapabilityShadowRepository.CANDIDATE_CONFIG_CREATED_AT)
        assertTrue(NonDynamicCapabilityParameterCodec.scientificallyEquivalent(fit, replay))
        assertSummaryClose(originalPrediction, solver.predict(replay, LoadedHoldCapabilityQuery(30.0)).summary!!)
    }

    private fun profile() = NonDynamicProfileSemantics(
        executionProfileVersionId = ExecutionProfileVersionId("profile:v1"),
        executionProfileId = ExecutionProfileId("profile"),
        metricFamily = MetricFamily.LOADED_HOLD,
        resistanceModel = ResistanceModel("resistance-v1", ResistanceSemantics.EXTERNAL, 0.0, 1.0, 0.0),
        entryBasis = EntryBasis.TOTAL,
        lateralityMode = LateralityMode.UNKNOWN,
    )

    private fun observation(profile: NonDynamicProfileSemantics, session: Int, load: Double, duration: Double) = CompletedSetEvidence(
        setRecordId = "set_$session",
        observationId = "obs_$session",
        sessionExerciseId = "se_$session",
        executionProfileVersionId = profile.executionProfileVersionId,
        metricFamily = profile.metricFamily,
        laterality = Laterality.UNKNOWN,
        completedAt = T0_INSTANT.plusSeconds(session * 86_400L),
        metricValues = listOf(
            PerformanceMetricValue(PerformanceMetric.EXTERNAL_LOAD, Quantity(load, UnitId.KILOGRAM)),
            PerformanceMetricValue(PerformanceMetric.DURATION, Quantity(duration, UnitId.SECOND)),
        ),
        bodyMassContextKg = null,
        warmUp = false,
        kind = "work",
        observationSource = "corrected_lite_import",
        sessionId = "session_$session",
    )

    private fun seedMinimumForeignKeys() {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO user_profile (id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                "VALUES ('user', 'Fixture', 'metric', 'unspecified', 1, ?, ?)",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO reference_profile (id, version, populationSex, populationAgeSummary, populationDescription, datasetVersion, modelVersion) " +
                "VALUES ('reference', 1, 'mixed', 'fixture', 'fixture', 'fixture-v1', 'reference-v1')",
        )
        sqlite.execSQL(
            "INSERT INTO exercise (id, name, archived, essentialCue, createdAt, updatedAt) VALUES ('exercise', 'Fixture hold', 0, NULL, ?, ?)",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO exercise_execution_profile (id, exerciseId, name, isDefault, archived) VALUES ('profile', 'exercise', 'Fixture hold', 1, 0)",
        )
        sqlite.execSQL(
            "INSERT INTO performance_schema (id, version, metricFamily, createdAt, provenance) VALUES ('schema', 1, 'loaded_hold', ?, 'fixture')",
            arrayOf(T0),
        )
        sqlite.execSQL(
            "INSERT INTO recruitment_profile_version (id, executionProfileId, version, createdAt, effectiveAt, supersededAt, provenance, modelVersion) " +
                "VALUES ('recruitment', 'profile', 1, ?, ?, NULL, 'fixture', 'recruitment-v1')",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            """
            INSERT INTO execution_profile_version (
                id, executionProfileId, version, metricFamily, performanceSchemaId,
                equipmentIdentity, equipmentType, resistanceSemantics, resistanceModelVersion,
                bodyweightCoefficient, externalLoadCoefficient, assistanceCoefficient, entryBasis,
                implementCount, lateralityMode, romClass, techniqueClass, resistanceCurveClass,
                movementPattern, jointActionsJson, kineticChain, contractionType,
                gripSupportConstraintsJson, recruitmentProfileVersionId, createdAt, effectiveAt,
                supersededAt, provenance, modelVersion
            ) VALUES (
                'profile:v1', 'profile', 1, 'loaded_hold', 'schema',
                NULL, 'dumbbell', 'external', 'resistance-v1',
                0.0, 1.0, 0.0, 'total',
                1, 'unknown', NULL, NULL, NULL,
                NULL, NULL, NULL, 'isometric',
                NULL, 'recruitment', ?, ?,
                NULL, 'fixture', 'profile-v1'
            )
            """.trimIndent(),
            arrayOf(T0, T0),
        )
    }

    private fun rowCount(table: String): Int = database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM \"$table\"")
        .use { cursor -> assertTrue(cursor.moveToFirst()); cursor.getInt(0) }

    private fun assertSummaryClose(
        left: dev.kian.mymettle.domain.inference.PosteriorSummary,
        right: dev.kian.mymettle.domain.inference.PosteriorSummary,
    ) {
        listOf(left.p05 to right.p05, left.p50 to right.p50, left.p95 to right.p95, left.posteriorVariance to right.posteriorVariance)
            .forEach { (a, b) -> assertTrue(abs(a - b) <= 1e-10 * maxOf(1.0, abs(a), abs(b))) }
    }

    companion object {
        private const val T0 = "2026-09-01T00:00:00Z"
        private const val T1 = "2026-09-02T00:00:00Z"
        private val T0_INSTANT: Instant = Instant.parse(T0)
        private val T1_INSTANT: Instant = Instant.parse(T1)
    }
}
