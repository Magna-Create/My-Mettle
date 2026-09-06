package dev.kian.mymettle.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NBio7FRoom17CorrectionMigrationTest {
    @Test
    fun room16To17MatchesCurrentCorrectionSchemaWithoutRewritingRoom16EquipmentRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)

        val room16Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(16) {
                        override fun onCreate(db: SupportSQLiteDatabase) = createRoom16Fixture(db)

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            error("Fixture must be created directly at Room16.")
                        }
                    },
                )
                .build(),
        )
        val migrated = room16Helper.writableDatabase
        val currentDatabase = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        val current = currentDatabase.openHelper.writableDatabase

        try {
            MIGRATION_16_17.migrate(migrated)

            assertEquals(16, MIGRATION_16_17.startVersion)
            assertEquals(17, MIGRATION_16_17.endVersion)
            val equipment = migrated.query(
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
            assertEquals(listOf("user", "Fixture bar", "fixture", T0, null), equipment)

            CORRECTION_TABLES.forEach { table ->
                assertEquals(
                    tableShape(current, table),
                    tableShape(migrated, table),
                    "MIGRATION_16_17 must create the same $table structure Room17 expects.",
                )
                assertEquals(0, rowCount(migrated, table), "Migration must not invent correction history in $table")
            }
            assertFalse(migrated.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        } finally {
            currentDatabase.close()
            room16Helper.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun createRoom16Fixture(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("CREATE TABLE user_profile (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("CREATE TABLE session_exercise (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("CREATE TABLE set_observation (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL(
            """
            CREATE TABLE equipment_instance (
                id TEXT NOT NULL,
                userProfileId TEXT NOT NULL,
                localLabel TEXT,
                source TEXT NOT NULL,
                createdAt TEXT NOT NULL,
                archivedAt TEXT,
                PRIMARY KEY(id),
                FOREIGN KEY(userProfileId) REFERENCES user_profile(id) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("INSERT INTO user_profile(id) VALUES ('user')")
        db.execSQL(
            "INSERT INTO equipment_instance(id, userProfileId, localLabel, source, createdAt, archivedAt) " +
                "VALUES ('equipment:a', 'user', 'Fixture bar', 'fixture', '$T0', NULL)",
        )
    }

    private fun rowCount(db: SupportSQLiteDatabase, table: String): Int = db
        .query("SELECT COUNT(*) FROM `$table`")
        .use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun tableShape(db: SupportSQLiteDatabase, table: String): TableShape = TableShape(
        columns = db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ColumnShape(
                            name = cursor.getString(1),
                            type = cursor.getString(2),
                            notNull = cursor.getInt(3) != 0,
                            defaultValue = if (cursor.isNull(4)) null else cursor.getString(4),
                            primaryKeyPosition = cursor.getInt(5),
                        ),
                    )
                }
            }
        },
        foreignKeys = db.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        ForeignKeyShape(
                            parentTable = cursor.getString(2),
                            childColumn = cursor.getString(3),
                            parentColumn = cursor.getString(4),
                            onUpdate = cursor.getString(5),
                            onDelete = cursor.getString(6),
                        ),
                    )
                }
            }.sortedBy { it.toString() }
        },
        explicitIndices = db.query("PRAGMA index_list(`$table`)").use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getString(3) != "c") continue
                    val indexName = cursor.getString(1)
                    val columns = db.query("PRAGMA index_info(`$indexName`)").use { indexCursor ->
                        buildList {
                            while (indexCursor.moveToNext()) add(indexCursor.getString(2))
                        }
                    }
                    add(IndexShape(unique = cursor.getInt(2) != 0, columns = columns))
                }
            }.sortedBy { it.toString() }
        },
    )

    private data class TableShape(
        val columns: List<ColumnShape>,
        val foreignKeys: List<ForeignKeyShape>,
        val explicitIndices: List<IndexShape>,
    )

    private data class ColumnShape(
        val name: String,
        val type: String,
        val notNull: Boolean,
        val defaultValue: String?,
        val primaryKeyPosition: Int,
    )

    private data class ForeignKeyShape(
        val parentTable: String,
        val childColumn: String,
        val parentColumn: String,
        val onUpdate: String,
        val onDelete: String,
    )

    private data class IndexShape(
        val unique: Boolean,
        val columns: List<String>,
    )

    companion object {
        private const val DATABASE_NAME = "n-bio-7f-room17-correction-migration"
        private const val T0 = "2026-01-01T00:00:00Z"
        private val CORRECTION_TABLES = listOf(
            "session_exercise_equipment_binding_correction",
            "set_observation_equipment_override_correction",
            "set_observation_load_semantics_correction",
        )
    }
}
