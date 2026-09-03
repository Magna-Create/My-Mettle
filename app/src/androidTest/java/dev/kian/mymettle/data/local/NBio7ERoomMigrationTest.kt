package dev.kian.mymettle.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7ERoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MyMettleDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun room14To15IsAdditiveAndForeignKeysRemainClean() {
        helper.createDatabase(DATABASE_NAME, 14).use { room14 ->
            room14.execSQL(
                "INSERT INTO user_profile(id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                    "VALUES ('user', 'Fixture', 'metric', 'omnivore', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 15, true, MIGRATION_14_15).use { room15 ->
            val userCount = room15.query("SELECT COUNT(*) FROM user_profile").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, userCount)
            listOf(
                "n_bio_7e_run",
                "n_bio_7e_temporal_state",
                "n_bio_7e_context_module_state",
                "n_bio_7e_context_signal",
                "n_bio_7e_context_module_status",
            ).forEach { table ->
                val exists = room15.query(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table),
                ).use { it.moveToFirst() }
                assertTrue(exists, "Missing additive table $table")
            }
            assertFalse(room15.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        }
    }

    companion object { private const val DATABASE_NAME = "n-bio-7e-migration" }
}
