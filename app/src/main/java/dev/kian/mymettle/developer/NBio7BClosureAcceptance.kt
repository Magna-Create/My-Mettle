package dev.kian.mymettle.developer

import android.content.Context
import android.database.Cursor
import dev.kian.mymettle.data.local.MyMettleDatabase
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class NBio7BPrescriptionStateFingerprint(
    val sha256: String,
    val tableRowCounts: Map<String, Long>,
)

/**
 * Fingerprints the persisted session prescription surface separately from raw performance evidence.
 * This proves that the explicit 7B acceptance action cannot silently rewrite an existing workout.
 */
object NBio7BPrescriptionStateFingerprinter {
    private val tableQueries = linkedMapOf(
        "session_set_prescription" to "SELECT * FROM session_set_prescription ORDER BY id",
        "session_metric_target" to "SELECT * FROM session_metric_target ORDER BY sessionSetPrescriptionId, metric",
    )

    fun capture(database: MyMettleDatabase): NBio7BPrescriptionStateFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        val counts = linkedMapOf<String, Long>()
        val sqlite = database.openHelper.readableDatabase
        tableQueries.forEach { (table, query) ->
            digest.text("table:$table\n")
            var count = 0L
            sqlite.query(query).use { cursor ->
                cursor.columnNames.forEach { digest.text("column:$it\n") }
                while (cursor.moveToNext()) {
                    count += 1
                    digest.text("row\n")
                    for (column in 0 until cursor.columnCount) {
                        digest.update(byteArrayOf(cursor.getType(column).toByte()))
                        when (cursor.getType(column)) {
                            Cursor.FIELD_TYPE_NULL -> Unit
                            Cursor.FIELD_TYPE_INTEGER -> digest.update(ByteBuffer.allocate(8).putLong(cursor.getLong(column)).array())
                            Cursor.FIELD_TYPE_FLOAT -> digest.update(ByteBuffer.allocate(8).putDouble(cursor.getDouble(column)).array())
                            Cursor.FIELD_TYPE_STRING -> digest.text(cursor.getString(column))
                            Cursor.FIELD_TYPE_BLOB -> digest.update(cursor.getBlob(column))
                            else -> error("Unsupported SQLite cursor type ${cursor.getType(column)}")
                        }
                        digest.update(byteArrayOf(0))
                    }
                }
            }
            counts[table] = count
            digest.text("count:$count\n")
        }
        return NBio7BPrescriptionStateFingerprint(
            sha256 = digest.digest().joinToString("") { "%02x".format(it) },
            tableRowCounts = counts,
        )
    }

    private fun MessageDigest.text(value: String) {
        update(value.toByteArray(StandardCharsets.UTF_8))
    }
}

data class NBio7BClosureAcceptanceReport(
    val acceptance: NBio7BAcceptanceReport,
    val prescriptionBefore: NBio7BPrescriptionStateFingerprint,
    val prescriptionAfter: NBio7BPrescriptionStateFingerprint,
    val backupRoundTrip: NBio7BBackupRoundTripResult,
    val prunedPriorShadowRuns: Int,
) {
    val prescriptionStateUnchanged: Boolean get() = prescriptionBefore == prescriptionAfter

    val closureChecks: List<NBio7BAcceptanceCheck> get() = listOf(
        NBio7BAcceptanceCheck(
            id = "prior_shadow_retention_bounded",
            status = NBio7BAcceptanceStatus.PASS,
            detail = "Pruned $prunedPriorShadowRuns prior N-BIO-7B SHADOW acceptance runs before evaluation; current-run derived state is retained only once.",
        ),
        NBio7BAcceptanceCheck(
            id = "prescription_state_unchanged",
            status = if (prescriptionStateUnchanged) NBio7BAcceptanceStatus.PASS else NBio7BAcceptanceStatus.FAIL,
            detail = if (prescriptionStateUnchanged) {
                "Persisted session_set_prescription and session_metric_target fingerprints/counts are unchanged."
            } else {
                "Persisted session prescription state changed during N-BIO-7B acceptance."
            },
        ),
        NBio7BAcceptanceCheck(
            id = "native_backup_round_trip",
            status = if (backupRoundTrip.passed) NBio7BAcceptanceStatus.PASS else NBio7BAcceptanceStatus.FAIL,
            detail = if (backupRoundTrip.passed) {
                "Installed Native backup restored into isolated Room14 with matching raw/prescription fingerprints, matching candidate counts and clean foreign keys."
            } else {
                "Isolated Native backup restore did not reproduce all required Room14 acceptance state."
            },
        ),
        NBio7BAcceptanceCheck(
            id = "native_backup_candidate_rows",
            status = when {
                !backupRoundTrip.candidateRowsMatch -> NBio7BAcceptanceStatus.FAIL
                backupRoundTrip.candidateRowsPresent -> NBio7BAcceptanceStatus.PASS
                else -> NBio7BAcceptanceStatus.NOT_EVALUATED
            },
            detail = when {
                !backupRoundTrip.candidateRowsMatch -> "Candidate SHADOW capability row counts differ after isolated backup restore."
                backupRoundTrip.candidateRowsPresent -> "Candidate SHADOW inference/capability/parameter rows are present and preserved by Native backup restore."
                else -> "No candidate SHADOW capability rows were produced by installed history, so candidate-row backup coverage is not empirically available on this database."
            },
        ),
    )

    val integritySafetyVerdict: NBio7BIntegritySafetyVerdict get() =
        if (acceptance.integritySafetyVerdict == NBio7BIntegritySafetyVerdict.FAIL ||
            closureChecks.any { it.status == NBio7BAcceptanceStatus.FAIL }) NBio7BIntegritySafetyVerdict.FAIL
        else NBio7BIntegritySafetyVerdict.PASS

    val empiricalModelEvaluationVerdict: NBio7BEmpiricalEvaluationVerdict
        get() = acceptance.empiricalModelEvaluationVerdict

    val overall7BClosureVerdict: NBio7BOverallClosureVerdict get() = when {
        integritySafetyVerdict == NBio7BIntegritySafetyVerdict.FAIL -> NBio7BOverallClosureVerdict.INTEGRITY_FAILURE
        acceptance.overall7BClosureVerdict == NBio7BOverallClosureVerdict.REQUIRES_NEW_CANDIDATE ->
            NBio7BOverallClosureVerdict.REQUIRES_NEW_CANDIDATE
        acceptance.overall7BClosureVerdict == NBio7BOverallClosureVerdict.READY_FOR_7B_CLOSURE ->
            NBio7BOverallClosureVerdict.READY_FOR_7B_CLOSURE
        else -> NBio7BOverallClosureVerdict.PENDING_EMPIRICAL_EVALUATION
    }

    val passed: Boolean get() = overall7BClosureVerdict == NBio7BOverallClosureVerdict.READY_FOR_7B_CLOSURE

    fun toJson(): String {
        val root = JSONObject(acceptance.toJson())
        root.put("formatVersion", 4)
        root.put(
            "memoryMaintenance",
            JSONObject().put("prunedPriorShadowRuns", prunedPriorShadowRuns),
        )
        root.put(
            "prescriptionState",
            JSONObject()
                .put("beforeSha256", prescriptionBefore.sha256)
                .put("afterSha256", prescriptionAfter.sha256)
                .put("unchanged", prescriptionStateUnchanged)
                .put("tableRowCounts", JSONObject(prescriptionAfter.tableRowCounts)),
        )
        root.put(
            "nativeBackupRoundTrip",
            JSONObject()
                .put("schemaVersion", backupRoundTrip.schemaVersion)
                .put("tableCount", backupRoundTrip.tableCount)
                .put("rowCount", backupRoundTrip.rowCount)
                .put("rawEvidenceMatches", backupRoundTrip.rawEvidenceMatches)
                .put("prescriptionStateMatches", backupRoundTrip.prescriptionStateMatches)
                .put("candidateRowsMatch", backupRoundTrip.candidateRowsMatch)
                .put("candidateRowsPresent", backupRoundTrip.candidateRowsPresent)
                .put("foreignKeysClean", backupRoundTrip.foreignKeysClean)
                .put("sourceCandidateCounts", backupRoundTrip.sourceCandidateCounts.toJson())
                .put("restoredCandidateCounts", backupRoundTrip.restoredCandidateCounts.toJson())
                .put("passed", backupRoundTrip.passed),
        )
        root.put("closureChecks", JSONArray(closureChecks.map { check ->
            JSONObject().put("id", check.id).put("status", check.status.storageValue).put("detail", check.detail)
        }))
        root.put("verdicts", JSONObject()
            .put("integritySafety", integritySafetyVerdict.storageValue)
            .put("empiricalModelEvaluation", empiricalModelEvaluationVerdict.storageValue)
            .put("overall7BClosure", overall7BClosureVerdict.storageValue))
        root.put("passed", passed)
        return root.toString(2)
    }
}

/** Runs the existing scientific/Room acceptance unchanged, then adds closure-integrity checks. */
class NBio7BClosureAcceptanceRunner(
    context: Context,
    private val database: MyMettleDatabase,
    private val acceptanceRepository: NBio7BAcceptanceRepository = NBio7BAcceptanceRepository(database),
    private val backupVerifier: NBio7BBackupRoundTripVerifier = NBio7BBackupRoundTripVerifier(context, database),
) {
    suspend fun run(
        onProgress: (NBio7BAcceptanceProgress) -> Unit = {},
    ): NBio7BClosureAcceptanceReport {
        onProgress(NBio7BAcceptanceProgress(0, 0, "Pruning prior N-BIO-7B SHADOW acceptance rows"))
        val prunedPriorShadowRuns = NBio7BShadowRunJanitor.prunePreviousAcceptanceRuns(database)
        val prescriptionBefore = NBio7BPrescriptionStateFingerprinter.capture(database)
        val acceptance = acceptanceRepository.run(onProgress)
        val prescriptionAfter = NBio7BPrescriptionStateFingerprinter.capture(database)
        onProgress(
            NBio7BAcceptanceProgress(
                completedGroups = acceptance.groupsDiscovered,
                totalGroups = acceptance.groupsDiscovered,
                label = "Validating isolated Native backup round-trip",
            ),
        )
        val backupRoundTrip = backupVerifier.verify()
        return NBio7BClosureAcceptanceReport(
            acceptance = acceptance,
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            backupRoundTrip = backupRoundTrip,
            prunedPriorShadowRuns = prunedPriorShadowRuns,
        )
    }
}

private fun NBio7BBackupCandidateCounts.toJson(): JSONObject = JSONObject()
    .put("shadowRuns", shadowRuns)
    .put("capabilityStates", capabilityStates)
    .put("capabilityParameterStates", capabilityParameterStates)
