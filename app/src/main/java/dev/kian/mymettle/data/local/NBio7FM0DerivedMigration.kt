package dev.kian.mymettle.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Additive Room17 -> Room18 derived-only cache for frozen N-BIO-7F M0. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7f_m0_derived_state` (
                `id` TEXT NOT NULL,
                `stateKind` TEXT NOT NULL,
                `destinationExecutionProfileVersionId` TEXT NOT NULL,
                `sourceExecutionProfileVersionId` TEXT NOT NULL,
                `side` TEXT NOT NULL,
                `relationshipId` TEXT NOT NULL,
                `relationshipVersion` INTEGER NOT NULL,
                `relationshipPolicyIdentity` TEXT NOT NULL,
                `relationshipFingerprint` TEXT NOT NULL,
                `modelConfigId` TEXT NOT NULL,
                `mathematicalModelIdentity` TEXT NOT NULL,
                `solverIdentity` TEXT NOT NULL,
                `sourceSelectionPolicyId` TEXT NOT NULL,
                `sourceSelectionPolicyVersion` INTEGER NOT NULL,
                `sourceSelectionPolicyMode` TEXT NOT NULL,
                `sourceSelectionPolicyIdentity` TEXT NOT NULL,
                `sourceSelectionPolicyFrozenAt` TEXT NOT NULL,
                `evidenceThrough` TEXT NOT NULL,
                `predictionCutoff` TEXT,
                `codecId` TEXT NOT NULL,
                `codecVersion` INTEGER NOT NULL,
                `payloadSchemaVersion` INTEGER NOT NULL,
                `payload` TEXT NOT NULL,
                `payloadSha256` TEXT NOT NULL,
                `createdAt` TEXT NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_state_stateKind` ON `n_bio_7f_m0_derived_state` (`stateKind`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_state_destinationExecutionProfileVersionId` ON `n_bio_7f_m0_derived_state` (`destinationExecutionProfileVersionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_state_sourceExecutionProfileVersionId` ON `n_bio_7f_m0_derived_state` (`sourceExecutionProfileVersionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_state_relationshipId` ON `n_bio_7f_m0_derived_state` (`relationshipId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_state_sourceSelectionPolicyIdentity` ON `n_bio_7f_m0_derived_state` (`sourceSelectionPolicyIdentity`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_state_evidenceThrough` ON `n_bio_7f_m0_derived_state` (`evidenceThrough`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `n_bio_7f_m0_derived_dependency` (
                `stateId` TEXT NOT NULL,
                `dependencyId` TEXT NOT NULL,
                PRIMARY KEY(`stateId`, `dependencyId`),
                FOREIGN KEY(`stateId`) REFERENCES `n_bio_7f_m0_derived_state`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_n_bio_7f_m0_derived_dependency_dependencyId` ON `n_bio_7f_m0_derived_dependency` (`dependencyId`)")
    }
}
