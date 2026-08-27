package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.context.RawNoteHash
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
class NativeFullBackupRepositoryTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var repository: NativeFullBackupRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        repository = NativeFullBackupRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun fullBackupRestoresTypedCurrentSchemaRowsAtomically() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        val expectedSchema = sqlite.query("PRAGMA user_version").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
        sqlite.execSQL(
            "INSERT INTO health_integration_state " +
                "(id, provider, permissionState, lastSyncedAt, lastError) " +
                "VALUES ('primary', 'fixture', 'allowed', NULL, 'original')",
        )

        val backup = repository.exportJson()
        sqlite.execSQL("UPDATE health_integration_state SET lastError = 'mutated' WHERE id = 'primary'")

        val result = repository.restoreJson(backup)

        assertEquals(expectedSchema, result.schemaVersion)
        assertTrue(result.tableCount > 0)
        assertTrue(result.rowCount >= 1)
        val restored = sqlite.query(
            "SELECT provider, permissionState, lastSyncedAt, lastError FROM health_integration_state WHERE id = 'primary'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getString(0),
                cursor.getString(1),
                if (cursor.isNull(2)) null else cursor.getString(2),
                cursor.getString(3),
            )
        }
        assertEquals(listOf("fixture", "allowed", null, "original"), restored)
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    @Test
    fun room14BackupRoundTripsRawNoteInterpretationProvenanceAndTypedAnnotation() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        val note = "I slept about 4 hours and feel wrecked today"
        val sourceHash = RawNoteHash.sha256(note)
        sqlite.execSQL(
            "INSERT INTO session " +
                "(id, cycleId, daySymbol, mode, routineVersionId, status, startedAt, completedAt, editedAt, discardedAt, excludedFromInsights, bodyweightSnapshotKg, healthExportState, healthClientRecordId) " +
                "VALUES ('session-context', 'cycle-fixture', 'ψ', 'B', 'routine-fixture', 'completed', ?, ?, NULL, NULL, 0, 70.0, NULL, NULL)",
            arrayOf(T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO session_review " +
                "(sessionId, exerciseOrder, organisation, pacing, delayImpact, note, recordedAt, updatedAt) " +
                "VALUES ('session-context', NULL, NULL, NULL, NULL, ?, ?, ?)",
            arrayOf(note, T0, T0),
        )
        sqlite.execSQL(
            "INSERT INTO note_interpretation_run " +
                "(id, sessionReviewSessionId, exerciseReflectionSessionExerciseId, sourceScope, sourceTextHash, sourceUpdatedAt, interpreterKind, interpreterImplementationVersion, tagSchemaVersion, promptVersion, structuredOutputSchemaVersion, promptApiLibraryVersion, promptApiStatus, structuredOutputAvailable, systemInstructionAvailable, actualBaseModelName, createdAt, executionOutcome, fallbackReason) " +
                "VALUES ('run-context', 'session-context', NULL, 'session_review', ?, ?, 'rules', 'rules-context-v1', 1, NULL, NULL, '1.0.0-beta4', 'downloadable', 0, 0, NULL, ?, 'fallback_success', 'Prompt API downloadable; Rules fallback')",
            arrayOf(sourceHash, T0, T1),
        )
        sqlite.execSQL(
            "INSERT INTO context_annotation " +
                "(interpretationRunId, ordinal, tagId, tagSchemaVersion, valueType, booleanValue, numberValue, numberUnit, categoryValue, textActionValue, assertionSemantics, temporalApplicability, approximate, sourceSpanStart, sourceSpanEnd, sourceSpanText) " +
                "VALUES ('run-context', 0, 'SLEEP_DURATION_REPORTED', 1, 'number', NULL, 4.0, 'h', NULL, NULL, 'asserted', 'current', 1, 8, 21, 'about 4 hours')",
        )
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })

        val backup = repository.exportJson()
        val tableNames = JSONObject(backup).getJSONArray("tables").let { tables ->
            buildSet {
                for (index in 0 until tables.length()) add(tables.getJSONObject(index).getString("name"))
            }
        }
        assertTrue("session_review" in tableNames)
        assertTrue("note_interpretation_run" in tableNames)
        assertTrue("context_annotation" in tableNames)

        sqlite.execSQL("UPDATE session_review SET note = 'mutated raw note' WHERE sessionId = 'session-context'")
        sqlite.execSQL("DELETE FROM note_interpretation_run WHERE id = 'run-context'")
        assertEquals(0, rowCount("context_annotation"))

        val result = repository.restoreJson(backup)

        assertEquals(14, result.schemaVersion)
        val restoredNote = sqlite.query(
            "SELECT note FROM session_review WHERE sessionId = 'session-context'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals(note, restoredNote)
        val restoredRun = sqlite.query(
            "SELECT sourceTextHash, interpreterKind, interpreterImplementationVersion, tagSchemaVersion, " +
                "promptApiLibraryVersion, promptApiStatus, structuredOutputAvailable, systemInstructionAvailable, executionOutcome, fallbackReason " +
                "FROM note_interpretation_run WHERE id = 'run-context'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getString(2),
                cursor.getInt(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getInt(6),
                cursor.getInt(7),
                cursor.getString(8),
                cursor.getString(9),
            )
        }
        assertEquals(
            listOf(
                sourceHash,
                "rules",
                "rules-context-v1",
                1,
                "1.0.0-beta4",
                "downloadable",
                0,
                0,
                "fallback_success",
                "Prompt API downloadable; Rules fallback",
            ),
            restoredRun,
        )
        val restoredAnnotation = sqlite.query(
            "SELECT tagId, valueType, numberValue, numberUnit, assertionSemantics, temporalApplicability, approximate, sourceSpanStart, sourceSpanEnd, sourceSpanText " +
                "FROM context_annotation WHERE interpretationRunId = 'run-context' AND ordinal = 0",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            listOf(
                cursor.getString(0),
                cursor.getString(1),
                cursor.getDouble(2),
                cursor.getString(3),
                cursor.getString(4),
                cursor.getString(5),
                cursor.getInt(6),
                cursor.getInt(7),
                cursor.getInt(8),
                cursor.getString(9),
            )
        }
        assertEquals(
            listOf(
                "SLEEP_DURATION_REPORTED",
                "number",
                4.0,
                "h",
                "asserted",
                "current",
                1,
                8,
                21,
                "about 4 hours",
            ),
            restoredAnnotation,
        )
        assertTrue(sqlite.query("PRAGMA foreign_key_check").use { !it.moveToFirst() })
    }

    @Test
    fun room13BackupIsRejectedBeforeRoom14DataChanges() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO health_integration_state " +
                "(id, provider, permissionState, lastSyncedAt, lastError) " +
                "VALUES ('primary', 'fixture', 'allowed', NULL, 'keep-me')",
        )
        val backup = JSONObject(repository.exportJson())
            .put("databaseSchemaVersion", 13)
            .toString()

        val failure = runCatching { repository.restoreJson(backup) }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure!!.message.orEmpty().contains("Backup schema 13 does not match current Native schema 14"))
        assertTrue(failure.message.orEmpty().contains("Translate the backup to the current Native format"))

        val current = sqlite.query(
            "SELECT lastError FROM health_integration_state WHERE id = 'primary'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals("keep-me", current)
    }

    private fun rowCount(table: String): Int = database.openHelper.writableDatabase
        .query("SELECT COUNT(*) FROM \"$table\"")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    companion object {
        private const val T0 = "2026-08-27T10:00:00Z"
        private const val T1 = "2026-08-27T10:01:00Z"
    }
}
