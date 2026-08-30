package dev.kian.mymettle.developer

import android.content.Context
import androidx.room.Room
import dev.kian.mymettle.data.backup.NativeFullBackupRepository
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.inference.DynamicCapabilityShadowRepository

/** Counts only the candidate-derived rows that 7B.3/4 is responsible for preserving. */
data class NBio7BBackupCandidateCounts(
    val shadowRuns: Long,
    val capabilityStates: Long,
    val capabilityParameterStates: Long,
) {
    val hasCandidateRows: Boolean get() = shadowRuns > 0L || capabilityStates > 0L || capabilityParameterStates > 0L
}

data class NBio7BBackupRoundTripResult(
    val schemaVersion: Int,
    val tableCount: Int,
    val rowCount: Int,
    val sourceFingerprint: NBio7BRawEvidenceFingerprint,
    val restoredFingerprint: NBio7BRawEvidenceFingerprint,
    val sourcePrescriptionFingerprint: NBio7BPrescriptionStateFingerprint,
    val restoredPrescriptionFingerprint: NBio7BPrescriptionStateFingerprint,
    val sourceCandidateCounts: NBio7BBackupCandidateCounts,
    val restoredCandidateCounts: NBio7BBackupCandidateCounts,
    val foreignKeysClean: Boolean,
) {
    val rawEvidenceMatches: Boolean get() = sourceFingerprint == restoredFingerprint
    val prescriptionStateMatches: Boolean get() = sourcePrescriptionFingerprint == restoredPrescriptionFingerprint
    val candidateRowsMatch: Boolean get() = sourceCandidateCounts == restoredCandidateCounts
    val candidateRowsPresent: Boolean get() = sourceCandidateCounts.hasCandidateRows
    val passed: Boolean get() = rawEvidenceMatches && prescriptionStateMatches && candidateRowsMatch && foreignKeysClean
}

/**
 * Exports the installed Native database, restores that JSON into an isolated in-memory Room14
 * database, and compares only non-sensitive fingerprints/counts. The installed database is never
 * restored, cleared, or otherwise mutated by this verifier.
 *
 * The internal round-trip deliberately uses compact JSON. Human-facing Native backups retain their
 * pretty-printed default; compact mode only avoids a large transient indentation/StringBuilder
 * footprint during this memory-constrained acceptance check.
 */
class NBio7BBackupRoundTripVerifier(
    context: Context,
    private val sourceDatabase: MyMettleDatabase,
) {
    private val appContext = context.applicationContext

    suspend fun verify(): NBio7BBackupRoundTripResult {
        val sourceFingerprint = NBio7BRawEvidenceFingerprinter.capture(sourceDatabase)
        val sourcePrescriptionFingerprint = NBio7BPrescriptionStateFingerprinter.capture(sourceDatabase)
        val sourceCandidateCounts = candidateCounts(sourceDatabase)
        val backupJson = NativeFullBackupRepository(sourceDatabase).exportJson(pretty = false)

        val isolated = Room.inMemoryDatabaseBuilder(appContext, MyMettleDatabase::class.java).build()
        return try {
            // Force Room to create/validate the exact current schema before restore.
            isolated.openHelper.writableDatabase
            val restored = NativeFullBackupRepository(isolated).restoreJson(backupJson)
            val restoredFingerprint = NBio7BRawEvidenceFingerprinter.capture(isolated)
            val restoredPrescriptionFingerprint = NBio7BPrescriptionStateFingerprinter.capture(isolated)
            val restoredCandidateCounts = candidateCounts(isolated)
            val foreignKeysClean = isolated.openHelper.readableDatabase
                .query("PRAGMA foreign_key_check")
                .use { !it.moveToFirst() }

            NBio7BBackupRoundTripResult(
                schemaVersion = restored.schemaVersion,
                tableCount = restored.tableCount,
                rowCount = restored.rowCount,
                sourceFingerprint = sourceFingerprint,
                restoredFingerprint = restoredFingerprint,
                sourcePrescriptionFingerprint = sourcePrescriptionFingerprint,
                restoredPrescriptionFingerprint = restoredPrescriptionFingerprint,
                sourceCandidateCounts = sourceCandidateCounts,
                restoredCandidateCounts = restoredCandidateCounts,
                foreignKeysClean = foreignKeysClean,
            )
        } finally {
            isolated.close()
        }
    }

    private fun candidateCounts(database: MyMettleDatabase): NBio7BBackupCandidateCounts {
        val sqlite = database.openHelper.readableDatabase
        val modelVersion = DynamicCapabilityShadowRepository.SHADOW_RUN_MODEL_VERSION
        val capabilityFamily = DynamicCapabilityShadowRepository.CAPABILITY_FAMILY
        val shadowRuns = count(
            sqlite.query(
                "SELECT COUNT(*) FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
                arrayOf(modelVersion),
            ),
        )
        val capabilityStates = count(
            sqlite.query(
                """
                SELECT COUNT(*)
                FROM capability_state AS cs
                INNER JOIN inference_run AS ir ON ir.id = cs.inferenceRunId
                WHERE ir.executionMode = 'shadow'
                  AND ir.modelVersion = ?
                  AND cs.capabilityFamily = ?
                """.trimIndent(),
                arrayOf(modelVersion, capabilityFamily),
            ),
        )
        val capabilityParameterStates = count(
            sqlite.query(
                """
                SELECT COUNT(*)
                FROM capability_parameter_state AS cps
                INNER JOIN inference_run AS ir ON ir.id = cps.inferenceRunId
                WHERE ir.executionMode = 'shadow'
                  AND ir.modelVersion = ?
                  AND cps.capabilityFamily = ?
                """.trimIndent(),
                arrayOf(modelVersion, capabilityFamily),
            ),
        )
        return NBio7BBackupCandidateCounts(
            shadowRuns = shadowRuns,
            capabilityStates = capabilityStates,
            capabilityParameterStates = capabilityParameterStates,
        )
    }

    private fun count(cursor: android.database.Cursor): Long = cursor.use {
        check(it.moveToFirst()) { "COUNT(*) returned no row." }
        it.getLong(0)
    }
}
