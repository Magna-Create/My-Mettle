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
class NBio7FRoom18DerivedMigrationTest {
    @Test
    fun room17To18AddsOnlyEmptyDerivedM0CacheTablesAndPreservesExistingRows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(DATABASE_NAME)

        val room17Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DATABASE_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(17) {
                        override fun onCreate(db: SupportSQLiteDatabase) = createRoom17Fixture(db)

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                            error("Fixture must be created directly at Room17.")
                        }
                    },
                )
                .build(),
        )
        val migrated = room17Helper.writableDatabase
        val currentDatabase = Room.inMemoryDatabaseBuilder(context, MyMettleDatabase::class.java).build()
        val current = currentDatabase.openHelper.writableDatabase

        try {
            MIGRATION_17_18.migrate(migrated)

            assertEquals(17, MIGRATION_17_18.startVersion)
            assertEquals(18, MIGRATION_17_18.endVersion)
            assertEquals(
                "preserve-me",
                migrated.query("SELECT payload FROM canonical_sentinel WHERE id='sentinel'").use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    cursor.getString(0)
                },
            )

            DERIVED_TABLES.forEach { table ->
                assertEquals(
                    tableShape(current, table),
                    tableShape(migrated, table),
                    "MIGRATION_17_18 must create the same $table structure Room18 expects.",
                )
                assertEquals(0, rowCount(migrated, table), "Migration must not invent derived M0 state in $table")
            }
            assertFalse(migrated.query("PRAGMA foreign_key_check").use { it.moveToFirst() })
        } finally {
            currentDatabase.close()
            room17Helper.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun createRoom17Fixture(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL(
            "CREATE TABLE canonical_sentinel (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)",
        )
        db.execSQL("INSERT INTO canonical_sentinel(id, payload) VALUES ('sentinel', 'preserve-me')")
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
        private const val DATABASE_NAME = "n-bio-7f-room18-derived-migration"
        private val DERIVED_TABLES = listOf(
            "n_bio_7f_m0_derived_state",
            "n_bio_7f_m0_derived_dependency",
        )
    }
}
