package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7DBackupRoundTripTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var repository: NativeFullBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        repository = NativeFullBackupRepository(database)
        seedFixture()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun fullBackupRoundTrips7DDemandExposureDoseAndCausalCapabilityWithoutStarting7E() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
        assertEquals(1, rowCount("set_observation"))
        assertEquals(1, rowCount("set_demand_estimate"))
        assertEquals(1, rowCount("muscle_set_dose"))
        assertEquals(1, rowCount("muscle_session_dose"))
        assert7EEmpty()

        val backup = repository.exportJson()
        sqlite.execSQL("DELETE FROM inference_run WHERE id = '7d-run'")

        // Derived 7D state cascades; canonical workout/performance/recruitment evidence survives.
        assertEquals(0, rowCount("capability_state"))
        assertEquals(0, rowCount("capability_parameter_state"))
        assertEquals(0, rowCount("set_demand_estimate"))
        assertEquals(0, rowCount("muscle_set_dose"))
        assertEquals(0, rowCount("muscle_session_dose"))
        assertEquals(1, rowCount("session"))
        assertEquals(1, rowCount("set_record"))
        assertEquals(1, rowCount("set_observation"))
        assertEquals(1, rowCount("recruitment_profile_version"))
        assert7EEmpty()

        val restored = repository.restoreJson(backup)
        assertEquals(14, restored.schemaVersion)
        assertEquals(1, rowCount("capability_state"))
        assertEquals(1, rowCount("capability_parameter_state"))
        assertEquals(1, rowCount("set_demand_estimate"))
        assertEquals(1, rowCount("muscle_set_dose"))
        assertEquals(1, rowCount("muscle_session_dose"))
        assert7EEmpty()

        val demand = sqlite.query(
            "SELECT posterior_p05, posterior_p50, posterior_p95, posterior_variance, posterior_evidenceFamily, modelConfigId " +
                "FROM set_demand_estimate WHERE inferenceRunId = '7d-run' AND setObservationId = 'observation'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getDouble(0), cursor.getDouble(1), cursor.getDouble(2), cursor.getDouble(3),
                cursor.getString(4), cursor.getString(5),
            )
        }
        assertEquals(listOf(-0.02, 0.04, 0.12, 0.003, "n_bio_7d_set_demand:dynamic_resistance:RESOLVED", "demand-config"), demand)

        val setDose = sqlite.query(
            "SELECT recruitmentProfileVersionId, recruitmentWeight, conservativeExposure, " +
                "effectiveDose_p05, effectiveDose_p50, effectiveDose_p95, effectiveDose_variance, " +
                "exposureModelConfigId, effectiveDoseModelConfigId " +
                "FROM muscle_set_dose WHERE inferenceRunId = '7d-run'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getString(0), cursor.getDouble(1), cursor.getDouble(2), cursor.getDouble(3),
                cursor.getDouble(4), cursor.getDouble(5), cursor.getDouble(6), cursor.getString(7), cursor.getString(8),
            )
        }
        assertEquals(listOf("recruitment:v1", 0.7, 0.7, 0.0, 0.7, 0.7, 0.1176, "exposure-config", "dose-config"), setDose)

        val sessionDose = sqlite.query(
            "SELECT posterior_p05, posterior_p50, posterior_p95, posterior_variance, posterior_evidenceFamily, sessionDoseModelConfigId " +
                "FROM muscle_session_dose WHERE inferenceRunId = '7d-run'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getDouble(0), cursor.getDouble(1), cursor.getDouble(2), cursor.getDouble(3),
                cursor.getString(4), cursor.getString(5),
            )
        }
        assertEquals(listOf(0.0, 0.7, 0.7, 0.1176, "n_bio_7d_session_dose:FULLY_RESOLVED:unresolved=0:cross_stream_independence=false", "session-dose-config"), sessionDose)

        val capabilityParameters = sqlite.query(
            "SELECT parameterSchemaVersion, encodedParameters, modelConfigId FROM capability_parameter_state " +
                "WHERE inferenceRunId = '7d-run'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(cursor.getInt(0), cursor.getString(1), cursor.getString(2))
        }
        assertEquals(listOf(1, "exact-pre-session-capability-codec-fixture", "capability-config"), capabilityParameters)
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    private fun assert7EEmpty() {
        assertEquals(0, rowCount("adaptive_muscle_state"))
        assertEquals(0, rowCount("skill_state"))
        assertEquals(0, rowCount("exercise_translation_prediction"))
        assertEquals(0, rowCount("exercise_translation_source"))
    }

    private fun seedFixture() {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO user_profile (id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                "VALUES ('user', 'Fixture', 'metric', 'unspecified', 1, ?, ?)", arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO reference_profile (id, version, populationSex, populationAgeSummary, populationDescription, datasetVersion, modelVersion) " +
                "VALUES ('reference', 1, 'mixed', 'fixture', 'fixture', 'fixture-v1', 'reference-v1')",
        )
        sqlite.execSQL(
            "INSERT INTO muscle (id, name, region, unitKind, lateralityModel, instancePattern, verificationStatus) " +
                "VALUES ('muscle', 'Fixture muscle', 'fixture', 'muscle', 'bilateral', NULL, 'verified')",
        )
        sqlite.execSQL(
            "INSERT INTO muscle_segment (id, muscleId, name, segmentType, anatomicalStatus, statePolicy, verificationStatus) " +
                "VALUES ('segment', 'muscle', 'Fixture segment', 'whole', 'verified', 'direct', 'verified')",
        )
        sqlite.execSQL(
            "INSERT INTO exercise (id, name, archived, essentialCue, createdAt, updatedAt) " +
                "VALUES ('exercise', 'Fixture press', 0, NULL, ?, ?)", arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO exercise_execution_profile (id, exerciseId, name, isDefault, archived) " +
                "VALUES ('profile', 'exercise', 'Fixture profile', 1, 0)",
        )
        sqlite.execSQL(
            "INSERT INTO performance_schema (id, version, metricFamily, createdAt, provenance) " +
                "VALUES ('schema', 1, 'dynamic_resistance', ?, 'fixture')", arrayOf(T0),
        )
        sqlite.execSQL(
            "INSERT INTO recruitment_profile_version " +
                "(id, executionProfileId, version, createdAt, effectiveAt, supersededAt, provenance, modelVersion) " +
                "VALUES ('recruitment:v1', 'profile', 1, ?, ?, NULL, 'fixture', 'recruitment-v1')", arrayOf(T0, T0),
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
                NULL, 'recruitment:v1', ?, ?,
                NULL, 'fixture', 'profile-v1'
            )
            """.trimIndent(), arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO session (id, cycleId, daySymbol, mode, routineVersionId, status, startedAt, completedAt, editedAt, discardedAt, excludedFromInsights, bodyweightSnapshotKg, healthExportState, healthClientRecordId) " +
                "VALUES ('session', 'cycle', 'psi', 'full', 'routine', 'completed', ?, ?, NULL, NULL, 0, NULL, NULL, NULL)",
            arrayOf(T1, T2),
        )
        sqlite.execSQL(
            """
            INSERT INTO session_exercise (
                id, sessionId, position, exerciseId, slotId, exerciseNameSnapshot, importanceSnapshot,
                executionProfileId, executionProfileVersionId, executionProfileNameSnapshot,
                prescriptionMode, prescriptionIncluded, restSeconds, generatedByModelVersion,
                deferToAnd, status, note, startedAt, completedAt, movementReason, substitutedFromExerciseId
            ) VALUES (
                'session-exercise', 'session', 0, 'exercise', 'slot', 'Fixture press', 'normal',
                'profile', 'profile:v1', 'Fixture profile',
                'full', 1, 120, 'fixture', 0, 'completed', NULL, ?, ?, 'planned', NULL
            )
            """.trimIndent(), arrayOf(T1, T2),
        )
        sqlite.execSQL(
            "INSERT INTO set_record (id, sessionExerciseId, setIndex, note, warmUp, kind, createdAt) " +
                "VALUES ('set', 'session-exercise', 0, NULL, 0, 'working', ?)", arrayOf(T1),
        )
        sqlite.execSQL(
            "INSERT INTO set_observation (id, setRecordId, executionProfileVersionId, ordinal, side, completedAt, recordedAt, source, bodyMassContextKg, bodyMassContextSource, supersedesObservationId, startedAtEpochSecond, startedAtNano, endedAtEpochSecond, endedAtNano, timingQuality, sourceZoneOffsetMinutes) " +
                "VALUES ('observation', 'set', 'profile:v1', 1, 'bilateral', ?, ?, 'fixture', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'legacy_unknown', NULL)",
            arrayOf(T2, T2),
        )

        val configs = listOf(
            Triple("capability-config", "dynamic_capability", "capability-v1"),
            Triple("demand-config", "set_demand", "demand-v1"),
            Triple("exposure-config", "exposure", "exposure-v1"),
            Triple("dose-config", "effective_dose", "dose-v1"),
            Triple("session-dose-config", "session_dose", "session-dose-v1"),
        )
        configs.forEach { (id, component, version) ->
            sqlite.execSQL(
                "INSERT INTO model_config_definition " +
                    "(id, component, modelFamily, modelName, semanticVersion, configSchemaVersion, canonicalConfigPayload, createdAt, effectiveAt) " +
                    "VALUES (?, ?, 'fixture', 'fixture', ?, 1, '{}', ?, ?)",
                arrayOf(id, component, version, T0, T0),
            )
        }
        sqlite.execSQL("INSERT INTO inference_model_manifest (id, createdAt) VALUES ('manifest', ?)", arrayOf(T0))
        configs.forEach { (id, component, _) ->
            sqlite.execSQL(
                "INSERT INTO inference_model_manifest_entry (manifestId, component, modelConfigId) VALUES ('manifest', ?, ?)",
                arrayOf(component, id),
            )
        }
        sqlite.execSQL(
            """
            INSERT INTO inference_run (
                id, userProfileId, modelVersion, referenceProfileId, referenceProfileVersion,
                referenceModelVersion, recruitmentModelVersion, stimulusModelVersion,
                muscleStateModelVersion, exerciseTranslationModelVersion, modelManifestId,
                executionMode, semanticsMode, calculatedAt, evidenceThrough, evidenceSetCount,
                evidenceObservationCount, effectiveIndependentSessionCount
            ) VALUES (
                '7d-run', 'user', 'n-bio-7d-demand-dose-shadow-v1', 'reference', 1,
                'reference-v1', 'recruitment-v1', 'dose-v1',
                'n-bio-7d-no-adaptive-muscle-state', 'n-bio-7d-no-translation', 'manifest',
                'shadow', 'historical_semantics', ?, ?, 1, 1, 1
            )
            """.trimIndent(), arrayOf(T2, T2),
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
                '7d-run', 'profile:v1', 'bilateral', 'dynamic_resistance', 'kg',
                70.0, 75.0, 82.0, 12.5, 9, 5, ?, ?, 'dynamic_resistance', 'capability-config', ?
            )
            """.trimIndent(), arrayOf(T0, T1, T2),
        )
        sqlite.execSQL(
            "INSERT INTO capability_parameter_state " +
                "(inferenceRunId, executionProfileVersionId, side, capabilityFamily, parameterSchemaVersion, encodedParameters, modelConfigId) " +
                "VALUES ('7d-run', 'profile:v1', 'bilateral', 'dynamic_resistance', 1, 'exact-pre-session-capability-codec-fixture', 'capability-config')",
        )
        sqlite.execSQL(
            """
            INSERT INTO set_demand_estimate (
                inferenceRunId, setObservationId, executionProfileVersionId, side,
                posterior_p05, posterior_p50, posterior_p95, posterior_variance,
                posterior_observationCount, posterior_independentSessionCount,
                posterior_firstEvidenceAt, posterior_lastEvidenceAt, posterior_evidenceFamily, modelConfigId
            ) VALUES (
                '7d-run', 'observation', 'profile:v1', 'bilateral',
                -0.02, 0.04, 0.12, 0.003,
                9, 5, ?, ?, 'n_bio_7d_set_demand:dynamic_resistance:RESOLVED', 'demand-config'
            )
            """.trimIndent(), arrayOf(T0, T1),
        )
        sqlite.execSQL(
            """
            INSERT INTO muscle_set_dose (
                inferenceRunId, setObservationId, executionProfileVersionId, recruitmentProfileVersionId,
                muscleSegmentId, side, recruitmentWeight, conservativeExposure,
                effectiveDose_p05, effectiveDose_p50, effectiveDose_p95, effectiveDose_variance,
                effectiveDose_observationCount, effectiveDose_independentSessionCount,
                effectiveDose_firstEvidenceAt, effectiveDose_lastEvidenceAt, effectiveDose_evidenceFamily,
                exposureModelConfigId, effectiveDoseModelConfigId
            ) VALUES (
                '7d-run', 'observation', 'profile:v1', 'recruitment:v1',
                'segment', 'bilateral', 0.7, 0.7,
                0.0, 0.7, 0.7, 0.1176,
                9, 5, ?, ?, 'n_bio_7d_effective_dose:RESOLVED',
                'exposure-config', 'dose-config'
            )
            """.trimIndent(), arrayOf(T0, T1),
        )
        sqlite.execSQL(
            """
            INSERT INTO muscle_session_dose (
                inferenceRunId, sessionId, muscleSegmentId, side,
                posterior_p05, posterior_p50, posterior_p95, posterior_variance,
                posterior_observationCount, posterior_independentSessionCount,
                posterior_firstEvidenceAt, posterior_lastEvidenceAt, posterior_evidenceFamily,
                sessionDoseModelConfigId
            ) VALUES (
                '7d-run', 'session', 'segment', 'bilateral',
                0.0, 0.7, 0.7, 0.1176,
                1, 1, ?, ?, 'n_bio_7d_session_dose:FULLY_RESOLVED:unresolved=0:cross_stream_independence=false',
                'session-dose-config'
            )
            """.trimIndent(), arrayOf(T2, T2),
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
        private const val T2 = "2026-08-20T11:00:00Z"
    }
}
