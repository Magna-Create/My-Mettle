package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.equipment.EquipmentId
import dev.kian.mymettle.domain.equipment.EquipmentInstance
import dev.kian.mymettle.domain.equipment.ExternalLoadAccounting
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverride
import dev.kian.mymettle.domain.equipment.ObservationEquipmentOverrideCorrection
import dev.kian.mymettle.domain.equipment.ObservationLoadSemantics
import dev.kian.mymettle.domain.equipment.ObservationLoadSemanticsCorrection
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBinding
import dev.kian.mymettle.domain.equipment.SessionExerciseEquipmentBindingCorrection
import dev.kian.mymettle.equipment.EquipmentContextRepository
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7FEquipmentBackupRoundTripTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var backupRepository: NativeFullBackupRepository
    private lateinit var equipmentRepository: EquipmentContextRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        backupRepository = NativeFullBackupRepository(database)
        equipmentRepository = EquipmentContextRepository(database)
        seedCanonicalObservationFixture()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun room17BackupRoundTripsCanonicalEquipmentCorrectionsAndRawEvidence() = runBlocking {
        createEquipment("equipment:a")
        createEquipment("equipment:b")
        createEquipment("equipment:c")
        equipmentRepository.bindSessionActualEquipment(
            SessionExerciseEquipmentBinding(
                sessionExerciseId = "session-exercise",
                equipmentId = EquipmentId("equipment:a"),
                source = "recorded-session-choice",
                boundAt = T1,
            ),
        )
        equipmentRepository.bindObservationEquipmentOverride(
            ObservationEquipmentOverride(
                observationId = "observation",
                equipmentId = EquipmentId("equipment:c"),
                source = "recorded-set-choice",
                boundAt = T2,
            ),
        )
        equipmentRepository.recordObservationLoadSemantics(
            ObservationLoadSemantics(
                observationId = "observation",
                externalLoadAccounting = ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
                source = "recorded-load-meaning",
                recordedAt = T2,
            ),
        )
        equipmentRepository.correctSessionActualEquipment(
            SessionExerciseEquipmentBindingCorrection(
                id = "correction:session:1",
                sessionExerciseId = "session-exercise",
                version = 1,
                previousEquipmentId = EquipmentId("equipment:a"),
                correctedEquipmentId = EquipmentId("equipment:b"),
                source = "reviewed-history",
                reason = "wrong equipment recorded",
                correctedAt = T3,
            ),
        )
        equipmentRepository.correctObservationEquipmentOverride(
            ObservationEquipmentOverrideCorrection(
                id = "correction:override:1",
                observationId = "observation",
                version = 1,
                previousEquipmentId = EquipmentId("equipment:c"),
                correctedEquipmentId = null,
                source = "reviewed-history",
                reason = "set-specific override did not occur",
                correctedAt = T3,
            ),
        )
        equipmentRepository.correctObservationLoadSemantics(
            ObservationLoadSemanticsCorrection(
                id = "correction:load:1",
                observationId = "observation",
                version = 1,
                previousExternalLoadAccounting = ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
                correctedExternalLoadAccounting = ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
                source = "verified-entry-record",
                reason = "contemporaneous record confirms inclusive load",
                correctedAt = T3,
            ),
        )

        val rawBefore = rawExternalLoadRow()
        val resolvedBefore = equipmentRepository.resolveHistoricalEquipment("observation")
        val semanticsBefore = equipmentRepository.resolveHistoricalInterpretationSnapshot(
            observationId = "observation",
            asOf = Instant.parse(T2),
        ).loadSemantics
        assertEquals(EquipmentId("equipment:b"), resolvedBefore?.equipmentId)
        assertEquals(ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD, semanticsBefore?.externalLoadAccounting)

        val backup = backupRepository.exportJson(pretty = false)
        val tableNames = JSONObject(backup).getJSONArray("tables").let { tables ->
            buildSet {
                for (index in 0 until tables.length()) add(tables.getJSONObject(index).getString("name"))
            }
        }
        assertTrue("session_exercise_equipment_binding_correction" in tableNames)
        assertTrue("set_observation_equipment_override_correction" in tableNames)
        assertTrue("set_observation_load_semantics_correction" in tableNames)

        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("DELETE FROM session_exercise_equipment_binding_correction")
        sqlite.execSQL("DELETE FROM set_observation_equipment_override_correction")
        sqlite.execSQL("DELETE FROM set_observation_load_semantics_correction")

        // Without the correction ledger, immutable Room16 base assertions remain exactly as recorded.
        assertEquals(EquipmentId("equipment:c"), equipmentRepository.resolveHistoricalEquipment("observation")?.equipmentId)
        assertEquals(
            ExternalLoadAccounting.ADDED_EXTERNAL_LOAD_ONLY,
            equipmentRepository.resolveHistoricalInterpretationSnapshot("observation", Instant.parse(T2))
                .loadSemantics?.externalLoadAccounting,
        )
        assertEquals(rawBefore, rawExternalLoadRow())

        val restored = backupRepository.restoreJson(backup)

        assertEquals(17, restored.schemaVersion)
        assertEquals(1, rowCount("session_exercise_equipment_binding_correction"))
        assertEquals(1, rowCount("set_observation_equipment_override_correction"))
        assertEquals(1, rowCount("set_observation_load_semantics_correction"))
        assertEquals(EquipmentId("equipment:b"), equipmentRepository.resolveHistoricalEquipment("observation")?.equipmentId)
        assertEquals(
            ExternalLoadAccounting.INCLUSIVE_EXTERNAL_LOAD,
            equipmentRepository.resolveHistoricalInterpretationSnapshot("observation", Instant.parse(T2))
                .loadSemantics?.externalLoadAccounting,
        )
        assertEquals(rawBefore, rawExternalLoadRow())
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    private suspend fun createEquipment(id: String) {
        equipmentRepository.createEquipment(
            userProfileId = "user",
            equipment = EquipmentInstance(
                id = EquipmentId(id),
                localLabel = id,
                source = "fixture",
                createdAt = T0,
                archivedAt = null,
            ),
        )
    }

    private fun rawExternalLoadRow(): List<Any> = database.openHelper.writableDatabase.query(
        "SELECT enteredValue, enteredUnit, canonicalValue, canonicalUnit, acquisitionMethod, evidenceGranularity, semanticRole " +
            "FROM set_metric_value WHERE observationId='observation' AND metric='external_load'",
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        listOf(
            cursor.getDouble(0),
            cursor.getString(1),
            cursor.getDouble(2),
            cursor.getString(3),
            cursor.getString(4),
            cursor.getString(5),
            cursor.getString(6),
        )
    }

    private fun rowCount(table: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM \"$table\"")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun seedCanonicalObservationFixture() {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO user_profile (id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                "VALUES ('user', 'Fixture', 'metric', 'unspecified', 1, ?, ?)",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO exercise (id, name, archived, essentialCue, createdAt, updatedAt) " +
                "VALUES ('exercise', 'Fixture lift', 0, NULL, ?, ?)",
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
                "VALUES ('recruitment:v1', 'profile', 1, ?, ?, NULL, 'fixture', 'recruitment-v1')",
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
                NULL, 'recruitment:v1', ?, ?,
                NULL, 'fixture', 'profile-v1'
            )
            """.trimIndent(),
            arrayOf(T0, T0),
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
                'session-exercise', 'session', 0, 'exercise', 'slot', 'Fixture lift', 'normal',
                'profile', 'profile:v1', 'Fixture profile',
                'full', 1, 120, 'fixture', 0, 'completed', NULL, ?, ?, 'planned', NULL
            )
            """.trimIndent(),
            arrayOf(T1, T2),
        )
        sqlite.execSQL(
            "INSERT INTO set_record (id, sessionExerciseId, setIndex, note, warmUp, kind, createdAt) " +
                "VALUES ('set', 'session-exercise', 0, NULL, 0, 'working', ?)",
            arrayOf(T1),
        )
        sqlite.execSQL(
            "INSERT INTO set_observation (id, setRecordId, executionProfileVersionId, ordinal, side, completedAt, recordedAt, source, bodyMassContextKg, bodyMassContextSource, supersedesObservationId, startedAtEpochSecond, startedAtNano, endedAtEpochSecond, endedAtNano, timingQuality, sourceZoneOffsetMinutes) " +
                "VALUES ('observation', 'set', 'profile:v1', 1, 'bilateral', ?, ?, 'fixture', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'legacy_unknown', NULL)",
            arrayOf(T2, T2),
        )
        sqlite.execSQL(
            "INSERT INTO set_metric_value (observationId, metric, enteredValue, enteredUnit, canonicalValue, canonicalUnit, acquisitionMethod, evidenceGranularity, semanticRole) " +
                "VALUES ('observation', 'external_load', 60.0, 'kg', 60.0, 'kg', 'manual', 'summary', 'performance_output')",
        )
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    companion object {
        private const val T0 = "2026-01-01T00:00:00Z"
        private const val T1 = "2026-01-02T10:00:00Z"
        private const val T2 = "2026-01-02T10:05:00Z"
        private const val T3 = "2026-01-03T10:00:00Z"
    }
}
