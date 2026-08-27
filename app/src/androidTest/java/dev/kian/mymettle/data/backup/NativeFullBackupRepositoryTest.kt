package dev.kian.mymettle.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kian.mymettle.data.local.MyMettleDatabase
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeFullBackupRepositoryTest {
    private lateinit var database: MyMettleDatabase
    private lateinit var repository: NativeFullBackupRepository

    @BeforeTest
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        database.openHelper.writableDatabase
        repository = NativeFullBackupRepository(database)
    }

    @AfterTest
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
    fun schemaMismatchIsRejectedBeforeExistingDataChanges() = runBlocking {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO health_integration_state " +
                "(id, provider, permissionState, lastSyncedAt, lastError) " +
                "VALUES ('primary', 'fixture', 'allowed', NULL, 'keep-me')",
        )
        val backup = JSONObject(repository.exportJson())
            .put("databaseSchemaVersion", 999)
            .toString()

        assertFailsWith<IllegalArgumentException> {
            repository.restoreJson(backup)
        }

        val current = sqlite.query(
            "SELECT lastError FROM health_integration_state WHERE id = 'primary'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertEquals("keep-me", current)
    }
}
