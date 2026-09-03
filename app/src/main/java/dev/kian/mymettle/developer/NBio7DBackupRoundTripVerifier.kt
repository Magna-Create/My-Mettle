package dev.kian.mymettle.developer

import android.content.Context
import android.database.Cursor
import androidx.room.Room
import dev.kian.mymettle.data.backup.NativeFullBackupRepository
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.inference.NBio7DShadowRepository

data class NBio7DBackupCandidateCounts(
    val shadowRuns: Long,
    val capabilityStates: Long,
    val capabilityParameterStates: Long,
    val setDemandEstimates: Long,
    val muscleSetDoses: Long,
    val muscleSessionDoses: Long,
    val adaptiveMuscleStates: Long,
    val skillStates: Long,
) {
    val hasCandidateRows: Boolean get() = shadowRuns > 0L &&
        (setDemandEstimates > 0L || muscleSetDoses > 0L || muscleSessionDoses > 0L)
    val sevenEStateEmpty: Boolean get() = adaptiveMuscleStates == 0L && skillStates == 0L
}

data class NBio7DBackupRoundTripResult(
    val schemaVersion: Int,
    val tableCount: Int,
    val rowCount: Int,
    val sourceFingerprint: NBio7BRawEvidenceFingerprint,
    val restoredFingerprint: NBio7BRawEvidenceFingerprint,
    val sourcePrescriptionFingerprint: NBio7BPrescriptionStateFingerprint,
    val restoredPrescriptionFingerprint: NBio7BPrescriptionStateFingerprint,
    val sourceCandidateCounts: NBio7DBackupCandidateCounts,
    val restoredCandidateCounts: NBio7DBackupCandidateCounts,
    val foreignKeysClean: Boolean,
) {
    val rawEvidenceMatches: Boolean get() = sourceFingerprint == restoredFingerprint
    val prescriptionStateMatches: Boolean get() = sourcePrescriptionFingerprint == restoredPrescriptionFingerprint
    val candidateRowsMatch: Boolean get() = sourceCandidateCounts == restoredCandidateCounts
    val candidateRowsPresent: Boolean get() = sourceCandidateCounts.hasCandidateRows
    val sevenEStateEmpty: Boolean get() = sourceCandidateCounts.sevenEStateEmpty && restoredCandidateCounts.sevenEStateEmpty
    val passed: Boolean get() = schemaVersion == 15 &&
        rawEvidenceMatches && prescriptionStateMatches && candidateRowsMatch &&
        candidateRowsPresent && sevenEStateEmpty && foreignKeysClean
}

/** Isolated Native full-backup verification scoped only to N-BIO-7D SHADOW state. */
class NBio7DBackupRoundTripVerifier(
    context: Context,
    private val sourceDatabase: MyMettleDatabase,
) {
    private val appContext = context.applicationContext

    suspend fun verify(): NBio7DBackupRoundTripResult {
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
            NBio7DBackupRoundTripResult(
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

    private fun candidateCounts(database: MyMettleDatabase): NBio7DBackupCandidateCounts {
        val sqlite = database.openHelper.readableDatabase
        val modelVersion = NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION
        fun derivedCount(table: String): Long = count(
            sqlite.query(
                "SELECT COUNT(*) FROM \"$table\" AS d INNER JOIN inference_run AS ir ON ir.id = d.inferenceRunId " +
                    "WHERE ir.executionMode = 'shadow' AND ir.modelVersion = ?",
                arrayOf(modelVersion),
            ),
        )
        val shadowRuns = count(
            sqlite.query(
                "SELECT COUNT(*) FROM inference_run WHERE executionMode = 'shadow' AND modelVersion = ?",
                arrayOf(modelVersion),
            ),
        )
        return NBio7DBackupCandidateCounts(
            shadowRuns = shadowRuns,
            capabilityStates = derivedCount("capability_state"),
            capabilityParameterStates = derivedCount("capability_parameter_state"),
            setDemandEstimates = derivedCount("set_demand_estimate"),
            muscleSetDoses = derivedCount("muscle_set_dose"),
            muscleSessionDoses = derivedCount("muscle_session_dose"),
            adaptiveMuscleStates = derivedCount("adaptive_muscle_state"),
            skillStates = derivedCount("skill_state"),
        )
    }

    private fun count(cursor: Cursor): Long = cursor.use {
        check(it.moveToFirst())
        it.getLong(0)
    }
}
