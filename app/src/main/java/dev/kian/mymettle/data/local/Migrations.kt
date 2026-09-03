package dev.kian.mymettle.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE exercise_setup_media ADD COLUMN width INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE exercise_setup_media ADD COLUMN height INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * Lite Legacy keeps a stable logical slot id as an immutable routine evolves.
 * Native storage therefore keys a slot occurrence by (routineVersionId, id), not id alone.
 *
 * Existing v2 native databases could only contain one row for each logical slot id, so the
 * migration can recover the missing prescription version key by joining through routine_slot.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `routine_slot_new` (
                `id` TEXT NOT NULL,
                `routineVersionId` TEXT NOT NULL,
                `daySymbol` TEXT NOT NULL,
                `exerciseId` TEXT NOT NULL,
                `position` INTEGER NOT NULL,
                `importance` TEXT NOT NULL,
                `plannedLoad` REAL NOT NULL,
                `lockedToDay` INTEGER NOT NULL,
                PRIMARY KEY(`routineVersionId`, `id`),
                FOREIGN KEY(`routineVersionId`) REFERENCES `routine_version`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`exerciseId`) REFERENCES `exercise`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `routine_slot_new`
                (`id`, `routineVersionId`, `daySymbol`, `exerciseId`, `position`, `importance`, `plannedLoad`, `lockedToDay`)
            SELECT
                `id`, `routineVersionId`, `daySymbol`, `exerciseId`, `position`, `importance`, `plannedLoad`, `lockedToDay`
            FROM `routine_slot`
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `mode_prescription_new` (
                `slotId` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `included` INTEGER NOT NULL,
                `sets` INTEGER NOT NULL,
                `repMin` INTEGER NOT NULL,
                `repMax` INTEGER NOT NULL,
                `restSeconds` INTEGER NOT NULL,
                `deferToAnd` INTEGER NOT NULL,
                `routineVersionId` TEXT NOT NULL,
                PRIMARY KEY(`routineVersionId`, `slotId`, `mode`),
                FOREIGN KEY(`routineVersionId`, `slotId`) REFERENCES `routine_slot_new`(`routineVersionId`, `id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `mode_prescription_new`
                (`slotId`, `mode`, `included`, `sets`, `repMin`, `repMax`, `restSeconds`, `deferToAnd`, `routineVersionId`)
            SELECT
                mp.`slotId`, mp.`mode`, mp.`included`, mp.`sets`, mp.`repMin`, mp.`repMax`, mp.`restSeconds`, mp.`deferToAnd`, rs.`routineVersionId`
            FROM `mode_prescription` AS mp
            INNER JOIN `routine_slot` AS rs ON rs.`id` = mp.`slotId`
            """.trimIndent(),
        )

        db.execSQL("DROP TABLE `mode_prescription`")
        db.execSQL("DROP TABLE `routine_slot`")
        db.execSQL("ALTER TABLE `routine_slot_new` RENAME TO `routine_slot`")
        db.execSQL("ALTER TABLE `mode_prescription_new` RENAME TO `mode_prescription`")

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_slot_routineVersionId` ON `routine_slot` (`routineVersionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_routine_slot_exerciseId` ON `routine_slot` (`exerciseId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_routine_slot_routineVersionId_daySymbol_position` ON `routine_slot` (`routineVersionId`, `daySymbol`, `position`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_mode_prescription_routineVersionId_slotId` ON `mode_prescription` (`routineVersionId`, `slotId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `session_review` (
                `sessionId` TEXT NOT NULL,
                `exerciseOrder` INTEGER,
                `organisation` INTEGER,
                `pacing` INTEGER,
                `delayImpact` INTEGER,
                `note` TEXT,
                `recordedAt` TEXT NOT NULL,
                `updatedAt` TEXT NOT NULL,
                PRIMARY KEY(`sessionId`),
                FOREIGN KEY(`sessionId`) REFERENCES `session`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }
}

/** Additive 7E-only derived storage. No canonical workout/context or earlier N-BIO row is rewritten. */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7e_run` (
                `id` TEXT NOT NULL,
                `userProfileId` TEXT NOT NULL,
                `sourceInferenceRunId` TEXT NOT NULL,
                `temporalModelConfigId` TEXT NOT NULL,
                `contextProtocolVersion` INTEGER NOT NULL,
                `signalSchemaVersion` INTEGER NOT NULL,
                `solverIdentity` TEXT NOT NULL,
                `executionMode` TEXT NOT NULL,
                `pd001Status` TEXT NOT NULL,
                `pd002Status` TEXT NOT NULL,
                `pd003Status` TEXT NOT NULL,
                `calculatedAt` TEXT NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`userProfileId`) REFERENCES `user_profile`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`sourceInferenceRunId`) REFERENCES `inference_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_run_userProfileId` ON `n_bio_7e_run` (`userProfileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_run_sourceInferenceRunId` ON `n_bio_7e_run` (`sourceInferenceRunId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_run_calculatedAt` ON `n_bio_7e_run` (`calculatedAt`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7e_temporal_state` (
                `runId` TEXT NOT NULL,
                `candidateLayer` TEXT NOT NULL,
                `scopeKind` TEXT NOT NULL,
                `scopeId` TEXT NOT NULL,
                `stateSchemaVersion` INTEGER NOT NULL,
                `persistentMean` REAL NOT NULL,
                `transientMean` REAL NOT NULL,
                `doseCoefficientMean` REAL NOT NULL,
                `covariancePp` REAL NOT NULL,
                `covariancePt` REAL NOT NULL,
                `covariancePd` REAL NOT NULL,
                `covarianceTt` REAL NOT NULL,
                `covarianceTd` REAL NOT NULL,
                `covarianceDd` REAL NOT NULL,
                `horizon` TEXT NOT NULL,
                `observationCount` INTEGER NOT NULL,
                `independentSessionCount` INTEGER NOT NULL,
                PRIMARY KEY(`runId`, `candidateLayer`, `scopeKind`, `scopeId`),
                FOREIGN KEY(`runId`) REFERENCES `n_bio_7e_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_temporal_state_runId` ON `n_bio_7e_temporal_state` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_temporal_state_scopeKind_scopeId` ON `n_bio_7e_temporal_state` (`scopeKind`, `scopeId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7e_context_module_state` (
                `runId` TEXT NOT NULL,
                `moduleId` TEXT NOT NULL,
                `moduleModelVersion` TEXT NOT NULL,
                `moduleConfigId` TEXT NOT NULL,
                `stateSchemaVersion` INTEGER NOT NULL,
                `encodedState` TEXT NOT NULL,
                `evidenceThrough` TEXT,
                `updatedAt` TEXT NOT NULL,
                PRIMARY KEY(`runId`, `moduleId`),
                FOREIGN KEY(`runId`) REFERENCES `n_bio_7e_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_module_state_runId` ON `n_bio_7e_context_module_state` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_module_state_moduleId` ON `n_bio_7e_context_module_state` (`moduleId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7e_context_signal` (
                `runId` TEXT NOT NULL,
                `signalId` TEXT NOT NULL,
                `signalSchemaVersion` INTEGER NOT NULL,
                `sourceModuleId` TEXT NOT NULL,
                `moduleModelVersion` TEXT NOT NULL,
                `moduleConfigId` TEXT NOT NULL,
                `sourceFeatureId` TEXT NOT NULL,
                `sourceFeatureSchemaVersion` INTEGER NOT NULL,
                `target` TEXT NOT NULL,
                `scopeKind` TEXT NOT NULL,
                `scopeId` TEXT NOT NULL,
                `effectiveFrom` TEXT NOT NULL,
                `effectiveUntil` TEXT,
                `effectRepresentation` TEXT NOT NULL,
                `locationMean` REAL,
                `variance` REAL,
                `evidenceRowCount` INTEGER NOT NULL,
                `independentSessionCount` INTEGER NOT NULL,
                `independentEpisodeCount` INTEGER NOT NULL,
                `evidenceMaturity` TEXT NOT NULL,
                `correlationGroupId` TEXT NOT NULL,
                `episodeId` TEXT,
                `encodedSourceEvidenceIds` TEXT NOT NULL,
                `encodedUpstreamModelIdentities` TEXT NOT NULL,
                `publishedAt` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `failureCode` TEXT,
                PRIMARY KEY(`runId`, `signalId`),
                FOREIGN KEY(`runId`) REFERENCES `n_bio_7e_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_signal_runId` ON `n_bio_7e_context_signal` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_signal_sourceModuleId` ON `n_bio_7e_context_signal` (`sourceModuleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_signal_target_scopeKind_scopeId` ON `n_bio_7e_context_signal` (`target`, `scopeKind`, `scopeId`)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7e_context_module_status` (
                `runId` TEXT NOT NULL,
                `moduleId` TEXT NOT NULL,
                `phase` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `failureCode` TEXT,
                `failureSummary` TEXT,
                `recordedAt` TEXT NOT NULL,
                PRIMARY KEY(`runId`, `moduleId`, `phase`),
                FOREIGN KEY(`runId`) REFERENCES `n_bio_7e_run`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_module_status_runId` ON `n_bio_7e_context_module_status` (`runId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7e_context_module_status_moduleId` ON `n_bio_7e_context_module_status` (`moduleId`)")
    }
}
