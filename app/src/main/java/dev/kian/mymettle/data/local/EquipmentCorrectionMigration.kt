package dev.kian.mymettle.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive Room16 -> Room17 correction ledger for canonical 7F historical assertions.
 *
 * Room16 base assertions are left untouched. Corrections append previous -> corrected epistemic
 * claims and may retract a wrong assertion back to unknown with a nullable corrected value.
 */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_exercise_equipment_binding_correction` (
                `id` TEXT NOT NULL,
                `sessionExerciseId` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `previousEquipmentId` TEXT,
                `correctedEquipmentId` TEXT,
                `source` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `correctedAt` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`sessionExerciseId`) REFERENCES `session_exercise`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`previousEquipmentId`) REFERENCES `equipment_instance`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`correctedEquipmentId`) REFERENCES `equipment_instance`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_equipment_binding_correction_sessionExerciseId` ON `session_exercise_equipment_binding_correction` (`sessionExerciseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_equipment_binding_correction_previousEquipmentId` ON `session_exercise_equipment_binding_correction` (`previousEquipmentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_session_exercise_equipment_binding_correction_correctedEquipmentId` ON `session_exercise_equipment_binding_correction` (`correctedEquipmentId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_session_exercise_equipment_binding_correction_sessionExerciseId_version` ON `session_exercise_equipment_binding_correction` (`sessionExerciseId`, `version`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `set_observation_equipment_override_correction` (
                `id` TEXT NOT NULL,
                `observationId` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `previousEquipmentId` TEXT,
                `correctedEquipmentId` TEXT,
                `source` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `correctedAt` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`observationId`) REFERENCES `set_observation`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`previousEquipmentId`) REFERENCES `equipment_instance`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`correctedEquipmentId`) REFERENCES `equipment_instance`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_observation_equipment_override_correction_observationId` ON `set_observation_equipment_override_correction` (`observationId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_observation_equipment_override_correction_previousEquipmentId` ON `set_observation_equipment_override_correction` (`previousEquipmentId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_observation_equipment_override_correction_correctedEquipmentId` ON `set_observation_equipment_override_correction` (`correctedEquipmentId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_set_observation_equipment_override_correction_observationId_version` ON `set_observation_equipment_override_correction` (`observationId`, `version`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `set_observation_load_semantics_correction` (
                `id` TEXT NOT NULL,
                `observationId` TEXT NOT NULL,
                `version` INTEGER NOT NULL,
                `previousExternalLoadAccounting` TEXT,
                `correctedExternalLoadAccounting` TEXT,
                `source` TEXT NOT NULL,
                `reason` TEXT NOT NULL,
                `correctedAt` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`observationId`) REFERENCES `set_observation`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_set_observation_load_semantics_correction_observationId` ON `set_observation_load_semantics_correction` (`observationId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_set_observation_load_semantics_correction_observationId_version` ON `set_observation_load_semantics_correction` (`observationId`, `version`)")
    }
}
