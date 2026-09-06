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
class NBio7FRoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MyMettleDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun room15To16IsAdditiveAndDoesNotGuessEquipmentSemantics() {
        helper.createDatabase(DATABASE_NAME, 15).use { room15 ->
            room15.execSQL(
                "INSERT INTO user_profile(id, displayName, units, dietaryPreference, cycleStartDay, createdAt, updatedAt) " +
                    "VALUES ('user', 'Fixture', 'metric', 'omnivore', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')",
            )
        }

        helper.runMigrationsAndValidate(DATABASE_NAME, 16, true, MIGRATION_15_16).use { room16 ->
            val userCount = room16.query("SELECT COUNT(*) FROM user_profile").use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            assertEquals(1, userCount)

            val newTables = listOf(
                "equipment_instance",
                "equipment_fact_version",
                "preferred_equipment_binding",
                "session_exercise_equipment_binding",
                "set_observation_equipment_override",
                "set_observation_load_semantics",
            )
            newTables.forEach { table ->
                val exists = room16.query(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(table),
                ).use { it.moveToFirst() }
                assertTrue(exists, "Missing additive table $table")

                val count = room16.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                    cursor.moveToFirst()
                    cursor.getInt(0)
                }
                assertEquals(0, count, "Room15 migration must not invent rows in $table")
            }
            assertFalse(room16.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        }
    }

    companion object { private const val DATABASE_NAME = "n-bio-7f-migration" }
}
