package dev.kian.mymettle.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_setup_media ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE exercise_setup_media ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
    }
}
