package dev.kian.mymettle.developer

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import dev.kian.mymettle.data.backup.NativeFullBackupRepository
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.performance.MetricFamily
import dev.kian.mymettle.inference.NonDynamicCapabilityShadowRepository

data class NBio7CBackupCandidateCounts(
    val shadowRuns: Long,
    val capabilityStatesByFamily: Map<String, Long>,
    val capabilityParameterStatesByFamily: Map<String, Long>,
) {
    val capabilityStates: Long get() = capabilityStatesByFamily.values.sum()
    val capabilityParameterStates: Long get() = capabilityParameterStatesByFamily.values.sum()
    val hasCandidateRows: Boolean get() = shadowRuns > 0L || capabilityStates > 0L || capabilityParameterStates > 0L
}

data class NBio7CBackupRoundTripResult(
    val schemaVersion: Int,
    val tableCount: Int,
    val rowCount: Int,
    val sourceFingerprint: NBio7BRawEvidenceFingerprint,
    val restoredFingerprint: NBio7BRawEvidenceFingerprint,
    val sourcePrescriptionFingerprint: NBio7BPrescriptionStateFingerprint,
    val restoredPrescriptionFingerprint: NBio7BPrescriptionStateFingerprint,
    val sourceCandidateCounts: NBio7CBackupCandidateCounts,
    val restoredCandidateCounts: NBio7CBackupCandidateCounts,
    val foreignKeysClean: Boolean,
) {
    val rawEvidenceMatches: Boolean get() = sourceFingerprint == restoredFingerprint
    val prescriptionStateMatches: Boolean get() = sourcePrescriptionFingerprint == restoredPrescriptionFingerprint
    val candidateRowsMatch: Boolean get() = sourceCandidateCounts == restoredCandidateCounts
    val candidateRowsPresent: Boolean get() = sourceCandidateCounts.hasCandidateRows
    val passed: Boolean get() = schemaVersion == 14 && rawEvidenceMatches && prescriptionStateMatches && candidateRowsMatch && foreignKeysClean
}

/** Isolated Native full-backup verification scoped to the disposable N-BIO-7C SHADOW rows. */
class NBio7CBackupRoundTripVerifier(
    context: Context,
    private val sourceDatabase: MyMettleDatabase,
) {
    private val appContext = context.applicationContext

    suspend fun verify(): NBio7CBackupRoundTripResult {
        val sourceFingerprint = NBio7BRawEvidenceFingerprinter.capture(sourceDatabase)
        val sourcePrescriptionFingerprint = NBio7BPrescriptionStateFingerprinter.capture(sourceDatabase)
        val sourceCandidateCounts = candidateCounts(sourceDatabase)
        val backupJson = NativeFullBackupRepository(sourceDatabase).exportJson(pretty = false)
        val isolated = Room.inMemoryDatabaseBuilder(appContext, MyMettleDatabase::class.java).build()
        return try {
            isolated.openHelper.writableDatabase
            val restored = NativeFullBackupRepository(isolated).restoreJson(backupJson)
            val restoredFingerprint = NBio7BRawEvidenceFingerprinter.capture(isolated)
            val restoredPrescriptionFingerprint = NBio7BPrescriptionStateFingerprinter.capture(isolated)
            val restoredCandidateCounts = candidateCounts(isolated)
            val foreignKeysClean = isolated.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { !it.moveToFirst() }
            NBio7CBackupRoundTripResult(
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

    private fun candidateCounts(database: MyMettleDatabase): NBio7CBackupCandidateCounts {
        val sqlite = database.openHelper.readableDatabase
        val families = listOf(
            MetricFamily.LOADED_HOLD.storageValue,
            MetricFamily.DURATION_ONLY.storageValue,
            MetricFamily.REPEATED_CONTRACTION.storageValue,
        )
        val modelVersion = NonDynamicCapabilityShadowRepository.SHADOW_RUN_MODEL_VERSION
        val shadowRuns = count(
            sqlite.query(
                "SELECT COUNT(*) FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
                arrayOf(modelVersion),
            ),
        )
        val states = families.associateWith { family ->
            count(
                sqlite.query(
                    """
                    SELECT COUNT(*)
                    FROM capability_state AS cs
                    INNER JOIN inference_run AS ir ON ir.id = cs.inferenceRunId
                    WHERE ir.executionMode = 'shadow' AND ir.modelVersion = ? AND cs.capabilityFamily = ?
                    """.trimIndent(),
                    arrayOf(modelVersion, family),
                ),
            )
        }
        val parameters = families.associateWith { family ->
            count(
                sqlite.query(
                    """
                    SELECT COUNT(*)
                    FROM capability_parameter_state AS cps
                    INNER JOIN inference_run AS ir ON ir.id = cps.inferenceRunId
                    WHERE ir.executionMode = 'shadow' AND ir.modelVersion = ? AND cps.capabilityFamily = ?
                    """.trimIndent(),
                    arrayOf(modelVersion, family),
                ),
            )
        }
        return NBio7CBackupCandidateCounts(shadowRuns, states, parameters)
    }

    private fun count(cursor: Cursor): Long = cursor.use {
        check(it.moveToFirst())
        it.getLong(0)
    }
}
