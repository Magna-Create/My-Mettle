package dev.kian.mymettle.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7FRoom17CorrectionMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MyMettleDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun room16To17AddsEmptyCorrectionLedgersWithoutRewritingRoom16EquipmentRows() {
        helper.createDatabase(DATABASE_NAME, 15).use { room15 ->
            room15.execSQL(
                "INSERT INTO user_profile(id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                    "VALUES ('user', 'Fixture', 'metric', 'unspecified', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
            )
            // Build an exact Room16 database using the already accepted additive migration, then
            // persist a canonical equipment row before exercising only the 16 -> 17 step.
            MIGRATION_15_16.migrate(room15)
            room15.execSQL("PRAGMA user_version = 16")
            room15.execSQL(
                "INSERT INTO equipment_instance(id, userProfileId, localLabel, source, createdAt, archivedAt) " +
                    "VALUES ('equipment:a', 'user', 'Fixture bar', 'fixture', '2026-01-01T00:00:00Z', NULL)",
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 17, true, MIGRATION_16_17).use { room17 ->
            val equipment = room17.query(
                "SELECT userProfileId, localLabel, source, createdAt, archivedAt FROM equipment_instance WHERE id='equipment:a'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                listOf(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getString(3),
                    if (cursor.isNull(4)) null else cursor.getString(4),
                )
            }
            assertEquals(listOf("user", "Fixture bar", "fixture", "2026-01-01T00:00:00Z", null), equipment)

            listOf(
                "session_exercise_equipment_binding_correction",
                "set_observation_equipment_override_correction",
                "set_observation_load_semantics_correction",
            ).forEach { table ->
                val exists = room17.query(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table),
                ).use { it.moveToFirst() }
                assertTrue(exists, "Missing additive Room17 correction table $table")
                val count = room17.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                assertEquals(0, count, "Migration must not invent correction history in $table")
            }
            assertFalse(room17.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        }
    }

    companion object { private const val DATABASE_NAME = "n-bio-7f-room17-correction-migration" }
}
