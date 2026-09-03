package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicCapabilityBackupRoundTripTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var repository: NativeFullBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        repository = NativeFullBackupRepository(database)
        seedForeignKeyValidCandidateFixture()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun nativeBackupRoundTripsCandidateRunCapabilityAndParameterStateDistinctly() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })

        val backup = repository.exportJson()
        val json = JSONObject(backup)
        assertEquals(15, json.getInt("databaseSchemaVersion"))
        val tableNames = json.getJSONArray("tables").let { tables ->
            buildSet {
                for (index in 0 until tables.length()) add(tables.getJSONObject(index).getString("name"))
            }
        }
        assertTrue("inference_run" in tableNames)
        assertTrue("model_config_definition" in tableNames)
        assertTrue("inference_model_manifest" in tableNames)
        assertTrue("capability_state" in tableNames)
        assertTrue("capability_parameter_state" in tableNames)

        sqlite.execSQL("DELETE FROM inference_run WHERE id = 'shadow-run'")
        assertEquals(0, rowCount("capability_state"))
        assertEquals(0, rowCount("capability_parameter_state"))
        assertEquals(1, rowCount("user_profile"))
        assertEquals(1, rowCount("execution_profile_version"))

        val restored = repository.restoreJson(backup)
        assertEquals(15, restored.schemaVersion)
        assertEquals(1, rowCount("capability_state"))
        assertEquals(1, rowCount("capability_parameter_state"))

        val capability = sqlite.query(
            "SELECT capabilityFamily, canonicalUnit, posterior_p05, posterior_p50, posterior_p95, " +
                "posterior_variance, posterior_observationCount, posterior_independentSessionCount, " +
                "posterior_firstEvidenceAt, posterior_lastEvidenceAt, posterior_evidenceFamily, modelConfigId " +
                "FROM capability_state WHERE inferenceRunId = 'shadow-run'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getString(0), cursor.getString(1), cursor.getDouble(2), cursor.getDouble(3),
                cursor.getDouble(4), cursor.getDouble(5), cursor.getInt(6), cursor.getInt(7),
                cursor.getString(8), cursor.getString(9), cursor.getString(10), cursor.getString(11),
            )
        }
        assertEquals(
            listOf(
                "dynamic_resistance", "kg", 70.0, 75.0, 82.0, 12.5, 9, 5,
                T0, T1, "dynamic_resistance", "candidate-config",
            ),
            capability,
        )

        val parameters = sqlite.query(
            "SELECT parameterSchemaVersion, encodedParameters, modelConfigId " +
                "FROM capability_parameter_state WHERE inferenceRunId = 'shadow-run'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(cursor.getInt(0), cursor.getString(1), cursor.getString(2))
        }
        assertEquals(listOf(1, "codec-fixture-v1", "candidate-config"), parameters)
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    private fun seedForeignKeyValidCandidateFixture() {
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
            "INSERT INTO exercise (id, name, archived, essentialCue, createdAt, updatedAt) " +
                "VALUES ('exercise', 'Fixture press', 0, NULL, ?, ?)",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO exercise_execution_profile (id, exerciseId, name, isDefault, archived) " +
                "VALUES ('profile', 'exercise', 'Fixture profile', 1, 0)",
        )
        sqlite.execSQL(
            "INSERT INTO performance_schema (id, version, metricFamily, createdAt, provenance) " +
                "VALUES ('schema', 1, 'dynamic_resistance', ?, 'fixture')",
            arrayOf(T0),
        )
        sqlite.execSQL(
            "INSERT INTO recruitment_profile_version " +
                "(id, executionProfileId, version, createdAt, effectiveAt, supersededAt, provenance, modelVersion) " +
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
                'profile:v1', 'profile', 1, 'dynamic_resistance', 'schema',
                NULL, 'barbell', 'external', 'resistance-v1',
                0.0, 1.0, 0.0, 'total',
                NULL, 'bilateral_only', NULL, NULL, NULL,
                NULL, NULL, NULL, NULL,
                NULL, 'recruitment', ?, ?,
                NULL, 'fixture', 'profile-v1'
            )
            """.trimIndent(),
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO model_config_definition " +
                "(id, component, modelFamily, modelName, semanticVersion, configSchemaVersion, canonicalConfigPayload, createdAt, effectiveAt) " +
                "VALUES ('candidate-config', 'dynamic_capability', 'stochastic_frontier', 'fixture', 'candidate-v1', 1, '{}', ?, ?)",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO inference_model_manifest (id, createdAt) VALUES ('manifest', ?)",
            arrayOf(T0),
        )
        sqlite.execSQL(
            "INSERT INTO inference_model_manifest_entry (manifestId, component, modelConfigId) " +
                "VALUES ('manifest', 'dynamic_capability', 'candidate-config')",
        )
        sqlite.execSQL(
            """
            INSERT INTO inference_run (
                id, userProfileId, modelVersion, referenceProfileId, referenceProfileVersion,
                referenceModelVersion, recruitmentModelVersion, stimulusModelVersion,
                muscleStateModelVersion, exerciseTranslationModelVersion, modelManifestId,
                executionMode, semanticsMode, calculatedAt, evidenceThrough, evidenceSetCount,
                evidenceObservationCount, effectiveIndependentSessionCount
            ) VALUES (
                'shadow-run', 'user', 'shadow-v1', 'reference', 1,
                'reference-v1', 'recruitment-v1', 'stimulus-v1',
                'muscle-v1', 'translation-v1', 'manifest',
                'shadow', 'historical_semantics', ?, ?, 9, 9, 5
            )
            """.trimIndent(),
            arrayOf(T1, T1),
        )
        sqlite.execSQL(
            """
            INSERT INTO capability_state (
                inferenceRunId, executionProfileVersionId, side, capabilityFamily, canonicalUnit,
                posterior_p05, posterior_p50, posterior_p95, posterior_variance,
                posterior_observationCount, posterior_independentSessionCount,
                posterior_firstEvidenceAt, posterior_lastEvidenceAt, posterior_evidenceFamily,
                modelConfigId, updatedAt
            ) VALUES (
                'shadow-run', 'profile:v1', 'bilateral', 'dynamic_resistance', 'kg',
                70.0, 75.0, 82.0, 12.5,
                9, 5, ?, ?, 'dynamic_resistance',
                'candidate-config', ?
            )
            """.trimIndent(),
            arrayOf(T0, T1, T1),
        )
        sqlite.execSQL(
            "INSERT INTO capability_parameter_state " +
                "(inferenceRunId, executionProfileVersionId, side, capabilityFamily, parameterSchemaVersion, encodedParameters, modelConfigId) " +
                "VALUES ('shadow-run', 'profile:v1', 'bilateral', 'dynamic_resistance', 1, 'codec-fixture-v1', 'candidate-config')",
        )
    }

    private fun rowCount(table: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM \"$table\"")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    companion object {
        private const val T0 = "2026-08-01T10:00:00Z"
        private const val T1 = "2026-08-20T10:00:00Z"
    }
}
