package dev.kian.mymettle.data.backup

import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.kian.mymettle.data.local.MyMettleDatabase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Portable, current-schema-only Native backup contract.
 *
 * The payload is deliberately a typed table dump rather than a Room database file: it remains
 * inspectable/translatable outside the app while preserving SQLite storage classes, including
 * temporal-evidence BLOBs. Restore requires an exact current Room schema match and replaces all
 * application tables atomically. It never understands My Mettle Lite formats.
 */
class NativeFullBackupRepository(
    private val database: MyMettleDatabase,
) {
    /**
     * Normal user exports remain pretty-printed. Internal verification can request compact JSON to
     * avoid a large additional whitespace/StringBuilder footprint while preserving the exact same
     * backup schema and values.
     */
    suspend fun exportJson(pretty: Boolean = true): String = withContext(Dispatchers.IO) {
        val sqlite = database.openHelper.writableDatabase
        val tables = applicationTables(sqlite)
        val root = JSONObject()
            .put("kind", BACKUP_KIND)
            .put("formatVersion", FORMAT_VERSION)
            .put("databaseSchemaVersion", schemaVersion(sqlite))
            .put("exportedAt", Instant.now().toString())
            .put(
                "tables",
                JSONArray().apply {
                    tables.forEach { table -> put(exportTable(sqlite, table)) }
                },
            )
        if (pretty) root.toString(2) else root.toString()
    }

    suspend fun restoreJson(json: String): NativeRestoreResult = withContext(Dispatchers.IO) {
        val sqlite = database.openHelper.writableDatabase
        val parsed = parseAndValidate(sqlite, json)
        val rowCount = parsed.tables.sumOf { it.rows.size }

        database.runInTransaction {
            val transactionalDb = database.openHelper.writableDatabase
            transactionalDb.execSQL("PRAGMA defer_foreign_keys = ON")

            val insertionOrder = insertionOrder(transactionalDb, parsed.tables.map { it.name }.toSet())
            insertionOrder.asReversed().forEach { table ->
                transactionalDb.execSQL("DELETE FROM ${quoteIdentifier(table)}")
            }
            if (hasTable(transactionalDb, "sqlite_sequence")) {
                transactionalDb.execSQL("DELETE FROM sqlite_sequence")
            }

            val byName = parsed.tables.associateBy { it.name }
            insertionOrder.forEach { tableName ->
                restoreTable(transactionalDb, requireNotNull(byName[tableName]))
            }

            val failures = foreignKeyFailures(transactionalDb)
            check(failures.isEmpty()) {
                "Backup would create foreign-key violations: ${failures.take(8).joinToString()}"
            }
        }

        NativeRestoreResult(
            schemaVersion = parsed.schemaVersion,
            tableCount = parsed.tables.size,
            rowCount = rowCount,
        )
    }

    private fun parseAndValidate(sqlite: SupportSQLiteDatabase, json: String): ParsedBackup {
        val root = runCatching { JSONObject(json) }
            .getOrElse { error("Selected file is not valid My Mettle Native backup JSON.") }

        require(root.optString("kind") == BACKUP_KIND) {
            "Selected file is not a My Mettle Native full backup."
        }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) {
            "Unsupported Native backup format version ${root.optInt("formatVersion", -1)}; expected $FORMAT_VERSION."
        }

        val currentSchema = schemaVersion(sqlite)
        val backupSchema = root.optInt("databaseSchemaVersion", -1)
        require(backupSchema == currentSchema) {
            "Backup schema $backupSchema does not match current Native schema $currentSchema. Translate the backup to the current Native format before restoring."
        }

        val expectedTables = applicationTables(sqlite).toSet()
        val tablesJson = root.optJSONArray("tables") ?: error("Native backup is missing tables.")
        val parsedTables = ArrayList<BackupTable>(tablesJson.length())
        val seenNames = mutableSetOf<String>()

        for (tableIndex in 0 until tablesJson.length()) {
            val tableJson = tablesJson.getJSONObject(tableIndex)
            val name = tableJson.getString("name")
            require(name in expectedTables) { "Backup contains unknown table '$name'." }
            require(seenNames.add(name)) { "Backup contains table '$name' more than once." }

            val expectedColumns = tableColumns(sqlite, name)
            val columnsJson = tableJson.getJSONArray("columns")
            val columns = List(columnsJson.length()) { index -> columnsJson.getString(index) }
            require(columns == expectedColumns) {
                "Backup columns for '$name' do not match the current Native schema."
            }

            val rowsJson = tableJson.getJSONArray("rows")
            val rows = ArrayList<List<BackupCell>>(rowsJson.length())
            for (rowIndex in 0 until rowsJson.length()) {
                val rowJson = rowsJson.getJSONArray(rowIndex)
                require(rowJson.length() == columns.size) {
                    "Backup row $rowIndex in '$name' has ${rowJson.length()} values; expected ${columns.size}."
                }
                rows += List(rowJson.length()) { cellIndex -> parseCell(rowJson.getJSONObject(cellIndex)) }
            }
            parsedTables += BackupTable(name, columns, rows)
        }

        require(seenNames == expectedTables) {
            val missing = (expectedTables - seenNames).sorted()
            "Native full backup is incomplete; missing tables: ${missing.joinToString()}."
        }

        return ParsedBackup(backupSchema, parsedTables)
    }

    private fun exportTable(sqlite: SupportSQLiteDatabase, table: String): JSONObject {
        val rows = JSONArray()
        val cursor = sqlite.query("SELECT * FROM ${quoteIdentifier(table)}")
        cursor.use {
            val columns = it.columnNames.toList()
            while (it.moveToNext()) {
                rows.put(
                    JSONArray().apply {
                        columns.indices.forEach { index -> put(exportCell(it, index)) }
                    },
                )
            }
            return JSONObject()
                .put("name", table)
                .put("columns", JSONArray(columns))
                .put("rows", rows)
        }
    }

    private fun exportCell(cursor: Cursor, index: Int): JSONObject = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> JSONObject().put("type", "null")
        Cursor.FIELD_TYPE_INTEGER -> JSONObject().put("type", "integer").put("value", cursor.getLong(index).toString())
        Cursor.FIELD_TYPE_FLOAT -> JSONObject().put("type", "real").put("value", cursor.getDouble(index).toString())
        Cursor.FIELD_TYPE_STRING -> JSONObject().put("type", "text").put("value", cursor.getString(index))
        Cursor.FIELD_TYPE_BLOB -> JSONObject()
            .put("type", "blob")
            .put("value", Base64.encodeToString(cursor.getBlob(index), Base64.NO_WRAP))
        else -> error("Unsupported SQLite storage type ${cursor.getType(index)}.")
    }

    private fun parseCell(json: JSONObject): BackupCell = when (val type = json.getString("type")) {
        "null" -> BackupCell.Null
        "integer" -> BackupCell.Integer(json.getString("value").toLong())
        "real" -> BackupCell.Real(json.getString("value").toDouble().also { require(it.isFinite()) })
        "text" -> BackupCell.Text(json.getString("value"))
        "blob" -> BackupCell.Blob(
            runCatching { Base64.decode(json.getString("value"), Base64.DEFAULT) }
                .getOrElse { error("Backup contains malformed base64 BLOB data.") },
        )
        else -> error("Unsupported backup cell type '$type'.")
    }

    private fun restoreTable(sqlite: SupportSQLiteDatabase, table: BackupTable) {
        if (table.rows.isEmpty()) return
        val identifiers = table.columns.joinToString(",") { quoteIdentifier(it) }
        val placeholders = List(table.columns.size) { "?" }.joinToString(",")
        val statement = sqlite.compileStatement(
            "INSERT INTO ${quoteIdentifier(table.name)} ($identifiers) VALUES ($placeholders)",
        )
        table.rows.forEach { row ->
            statement.clearBindings()
            row.forEachIndexed { index, cell ->
                val bindIndex = index + 1
                when (cell) {
                    BackupCell.Null -> statement.bindNull(bindIndex)
                    is BackupCell.Integer -> statement.bindLong(bindIndex, cell.value)
                    is BackupCell.Real -> statement.bindDouble(bindIndex, cell.value)
                    is BackupCell.Text -> statement.bindString(bindIndex, cell.value)
                    is BackupCell.Blob -> statement.bindBlob(bindIndex, cell.value)
                }
            }
            statement.executeInsert()
        }
    }

    private fun applicationTables(sqlite: SupportSQLiteDatabase): List<String> = sqlite.query(
        "SELECT name FROM sqlite_master " +
            "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
            "AND name != 'android_metadata' AND name != 'room_master_table' ORDER BY name",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun tableColumns(sqlite: SupportSQLiteDatabase, table: String): List<String> = sqlite.query(
        "PRAGMA table_info(${quoteIdentifier(table)})",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(cursor.getString(1))
        }
    }

    private fun insertionOrder(sqlite: SupportSQLiteDatabase, tables: Set<String>): List<String> {
        val parentsByTable = tables.associateWith { table ->
            sqlite.query("PRAGMA foreign_key_list(${quoteIdentifier(table)})").use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        val parent = cursor.getString(2)
                        if (parent in tables && parent != table) add(parent)
                    }
                }
            }
        }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        val ordered = mutableListOf<String>()

        fun visit(table: String) {
            if (table in visited) return
            if (!visiting.add(table)) return
            parentsByTable[table].orEmpty().sorted().forEach(::visit)
            visiting.remove(table)
            visited.add(table)
            ordered.add(table)
        }

        tables.sorted().forEach(::visit)
        return ordered
    }

    private fun foreignKeyFailures(sqlite: SupportSQLiteDatabase): List<String> = sqlite.query(
        "PRAGMA foreign_key_check",
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add("${cursor.getString(0)} row ${cursor.getLong(1)} -> ${cursor.getString(2)}")
            }
        }
    }

    private fun schemaVersion(sqlite: SupportSQLiteDatabase): Int = sqlite.query("PRAGMA user_version").use { cursor ->
        check(cursor.moveToFirst()) { "PRAGMA user_version returned no row." }
        cursor.getInt(0)
    }

    private fun hasTable(sqlite: SupportSQLiteDatabase, name: String): Boolean = sqlite.query(
        "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
        arrayOf(name),
    ).use { it.moveToFirst() }

    private fun quoteIdentifier(identifier: String): String =
        "\"${identifier.replace("\"", "\"\"")}\""

    private data class ParsedBackup(
        val schemaVersion: Int,
        val tables: List<BackupTable>,
    )

    private data class BackupTable(
        val name: String,
        val columns: List<String>,
        val rows: List<List<BackupCell>>,
    )

    private sealed interface BackupCell {
        data object Null : BackupCell
        data class Integer(val value: Long) : BackupCell
        data class Real(val value: Double) : BackupCell
        data class Text(val value: String) : BackupCell
        data class Blob(val value: ByteArray) : BackupCell
    }

    companion object {
        const val BACKUP_KIND = "my-mettle-native-full-backup"
        const val FORMAT_VERSION = 1
    }
}

data class NativeRestoreResult(
    val schemaVersion: Int,
    val tableCount: Int,
    val rowCount: Int,
)
