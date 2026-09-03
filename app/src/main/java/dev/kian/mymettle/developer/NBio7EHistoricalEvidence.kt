package dev.kian.mymettle.developer

import android.database.Cursor
import dev.kian.mymettle.context.RawNoteHash
import dev.kian.mymettle.context.modules.ProductionContextFeaturesV7E
import dev.kian.mymettle.data.local.MyMettleDatabase
import dev.kian.mymettle.domain.context.ContextEvidenceMissingness
import dev.kian.mymettle.domain.context.ContextEvidenceSourceKind
import dev.kian.mymettle.domain.context.ContextFeatureEvidenceV7E
import dev.kian.mymettle.domain.context.ContextFeatureValueV7E
import dev.kian.mymettle.domain.context.ContextScope
import dev.kian.mymettle.domain.context.ContextScopeKind
import dev.kian.mymettle.domain.inference.DatedSessionDose
import dev.kian.mymettle.domain.inference.DynamicHeldOutStatus
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.inference.DynamicResistanceHistoricalEvaluator
import dev.kian.mymettle.inference.NBio7DShadowRepository
import java.time.Instant
import kotlin.math.ln
import kotlin.math.max

data class NBio7ESessionResidualV1(
    val sessionId: String,
    val at: Instant,
    val observedLogResidual: Double,
    val capabilityPredictiveVariance: Double,
    val profileObservationCount: Int,
)

data class NBio7EHistoricalEvidenceV1(
    val residuals: List<NBio7ESessionResidualV1>,
    val contextBySession: Map<String, List<ContextFeatureEvidenceV7E>>,
    val doseBySession: Map<String, DatedSessionDose>,
    val contextTagCounts: Map<String, Int>,
    val currentContextEvidenceFingerprintInputs: List<String>,
    val sourceDoseRunId: String?,
)

/**
 * Privacy-bounded acceptance reader. Raw note text is read only long enough to verify interpretation
 * currentness by SHA-256; neither modules nor the acceptance export receive it.
 */
class NBio7EHistoricalEvidenceReader(
    private val database: MyMettleDatabase,
) {
    fun read(): NBio7EHistoricalEvidenceV1 {
        val dynamic = NBio7BRawHistoryReader(database).read()
        val evaluator = DynamicResistanceHistoricalEvaluator()
        val heldOut = dynamic.profiles.values.flatMap { descriptor ->
            val version = descriptor.semantics.executionProfileVersionId.value
            dynamic.revisions.asSequence()
                .filter { it.evidence.executionProfileVersionId.value == version }
                .map { it.evidence.laterality }
                .distinct()
                .sortedBy(Laterality::storageValue)
                .flatMap { side -> evaluator.evaluate(descriptor.semantics, side, dynamic.revisions).observations }
        }.filter { it.status == DynamicHeldOutStatus.EVALUABLE && it.candidatePredictive != null }

        val residuals = heldOut.groupBy { it.sessionId }.map { (sessionId, observations) ->
            val residualValues = observations.map { evaluation ->
                ln(evaluation.observedResistanceKg / requireNotNull(evaluation.candidatePredictive).p50ResistanceKg)
            }
            val predictiveVariances = observations.map { evaluation ->
                val predictive = requireNotNull(evaluation.candidatePredictive)
                val logSd = (ln(predictive.p95ResistanceKg) - ln(predictive.p05ResistanceKg)) / (2.0 * NORMAL_90_Z)
                max(MIN_VARIANCE, logSd * logSd)
            }
            NBio7ESessionResidualV1(
                sessionId = sessionId,
                at = observations.maxOf { it.heldOutAt },
                observedLogResidual = residualValues.average(),
                capabilityPredictiveVariance = predictiveVariances.sum() / (predictiveVariances.size * predictiveVariances.size),
                profileObservationCount = observations.size,
            )
        }.sortedWith(compareBy<NBio7ESessionResidualV1> { it.at }.thenBy { it.sessionId })

        val context = currentSessionContext()
        val dose = sessionDose()
        return NBio7EHistoricalEvidenceV1(
            residuals = residuals,
            contextBySession = context.groupBy { it.sessionId }.mapValues { (_, rows) -> rows.map { it.evidence } },
            doseBySession = dose.second,
            contextTagCounts = context.groupingBy { it.evidence.featureKey.featureId }.eachCount().toSortedMap(),
            currentContextEvidenceFingerprintInputs = context.map { row ->
                listOf(
                    row.sessionId,
                    row.evidence.evidenceId,
                    row.evidence.featureKey.canonical,
                    row.evidence.missingness.name,
                    row.evidence.sourceRevisionId,
                ).joinToString("|")
            }.sorted(),
            sourceDoseRunId = dose.first,
        )
    }

    private data class SessionContextRow(val sessionId: String, val evidence: ContextFeatureEvidenceV7E)

    private fun currentSessionContext(): List<SessionContextRow> {
        data class Candidate(
            val sessionId: String,
            val sessionCompletedAt: String,
            val note: String,
            val runId: String,
            val sourceTextHash: String,
            val sourceUpdatedAt: String,
            val runCreatedAt: String,
            val ordinal: Int,
            val tagId: String,
            val tagSchemaVersion: Int,
            val booleanValue: Boolean?,
            val assertion: String,
            val temporal: String,
            val extractorConfidence: Double?,
        )
        val candidates = mutableListOf<Candidate>()
        database.openHelper.readableDatabase.query(
            """
            SELECT nir.sessionReviewSessionId AS sessionId, s.completedAt AS sessionCompletedAt,
                   sr.note AS note, nir.id AS runId, nir.sourceTextHash AS sourceTextHash,
                   nir.sourceUpdatedAt AS sourceUpdatedAt, nir.createdAt AS runCreatedAt,
                   ca.ordinal AS ordinal, ca.tagId AS tagId, ca.tagSchemaVersion AS tagSchemaVersion,
                   ca.booleanValue AS booleanValue, ca.assertionSemantics AS assertionSemantics,
                   ca.temporalApplicability AS temporalApplicability,
                   CASE WHEN nir.interpreterKind = 'nano' THEN 0.5 ELSE NULL END AS extractorConfidence
            FROM note_interpretation_run nir
            INNER JOIN context_annotation ca ON ca.interpretationRunId = nir.id
            INNER JOIN session_review sr ON sr.sessionId = nir.sessionReviewSessionId
            INNER JOIN session s ON s.id = sr.sessionId
            WHERE nir.sessionReviewSessionId IS NOT NULL
              AND s.status = 'completed'
              AND s.excludedFromInsights = 0
              AND ca.tagId IN ('ILLNESS_REPORTED', 'TIME_PRESSURE_REPORTED')
            ORDER BY nir.createdAt, nir.id, ca.ordinal
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val note = cursor.string("note")
                candidates += Candidate(
                    sessionId = cursor.string("sessionId"),
                    sessionCompletedAt = cursor.string("sessionCompletedAt"),
                    note = note,
                    runId = cursor.string("runId"),
                    sourceTextHash = cursor.string("sourceTextHash"),
                    sourceUpdatedAt = cursor.string("sourceUpdatedAt"),
                    runCreatedAt = cursor.string("runCreatedAt"),
                    ordinal = cursor.int("ordinal"),
                    tagId = cursor.string("tagId"),
                    tagSchemaVersion = cursor.int("tagSchemaVersion"),
                    booleanValue = cursor.nullableInt("booleanValue")?.let { it != 0 },
                    assertion = cursor.string("assertionSemantics"),
                    temporal = cursor.string("temporalApplicability"),
                    extractorConfidence = cursor.nullableDouble("extractorConfidence"),
                )
            }
        }
        val currentRunBySession = candidates.filter { RawNoteHash.sha256(it.note) == it.sourceTextHash }
            .groupBy { it.sessionId }
            .mapValues { (_, rows) -> rows.maxWith(compareBy<Candidate> { it.runCreatedAt }.thenBy { it.runId }).runId }
        return candidates.filter { candidate ->
            candidate.runId == currentRunBySession[candidate.sessionId] &&
                candidate.tagSchemaVersion == 1
        }.mapNotNull { candidate ->
            val feature = when (candidate.tagId) {
                ProductionContextFeaturesV7E.illness.key.featureId -> ProductionContextFeaturesV7E.illness
                ProductionContextFeaturesV7E.timePressure.key.featureId -> ProductionContextFeaturesV7E.timePressure
                else -> return@mapNotNull null
            }
            val missingness = when {
                candidate.temporal != "current" -> ContextEvidenceMissingness.UNKNOWN
                candidate.assertion == "uncertain" -> ContextEvidenceMissingness.UNKNOWN
                candidate.assertion == "negated" -> ContextEvidenceMissingness.KNOWN_FALSE
                candidate.booleanValue == false -> ContextEvidenceMissingness.KNOWN_FALSE
                else -> ContextEvidenceMissingness.PRESENT
            }
            val observedAt = Instant.parse(candidate.runCreatedAt)
            SessionContextRow(
                sessionId = candidate.sessionId,
                evidence = ContextFeatureEvidenceV7E(
                    evidenceId = "${candidate.runId}:${candidate.ordinal}",
                    featureKey = feature.key,
                    value = if (missingness == ContextEvidenceMissingness.PRESENT) ContextFeatureValueV7E.BooleanValue(true) else null,
                    missingness = missingness,
                    scope = ContextScope(ContextScopeKind.SESSION, candidate.sessionId),
                    observedAt = observedAt,
                    effectiveFrom = observedAt,
                    sourceKind = ContextEvidenceSourceKind.LEGACY_NOTE_INTERPRETATION,
                    sourceRevisionId = "${candidate.sessionId}:${candidate.sourceTextHash}:${candidate.runId}",
                    extractorConfidence = candidate.extractorConfidence,
                ),
            )
        }
    }

    private fun sessionDose(): Pair<String?, Map<String, DatedSessionDose>> {
        val db = database.openHelper.readableDatabase
        val runId = db.query(
            "SELECT id FROM inference_run WHERE modelVersion = ? ORDER BY calculatedAt DESC, id DESC LIMIT 1",
            arrayOf(NBio7DShadowRepository.SHADOW_RUN_MODEL_VERSION),
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        if (runId == null) return null to emptyMap()
        val bySession = linkedMapOf<String, Pair<Instant, Double>>()
        db.query(
            """
            SELECT msd.sessionId AS sessionId, s.completedAt AS completedAt,
                   SUM(msd.posterior_p50) AS dose
            FROM muscle_session_dose msd
            INNER JOIN session s ON s.id = msd.sessionId
            WHERE msd.inferenceRunId = ? AND msd.posterior_p50 IS NOT NULL
            GROUP BY msd.sessionId, s.completedAt
            ORDER BY s.completedAt, msd.sessionId
            """.trimIndent(),
            arrayOf(runId),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val completedAt = cursor.string("completedAt")
                bySession[cursor.string("sessionId")] = Instant.parse(completedAt) to cursor.double("dose")
            }
        }
        return runId to bySession.mapValues { (_, value) -> DatedSessionDose(value.first, value.second) }
    }

    companion object {
        private const val NORMAL_90_Z = 1.6448536269514722
        private const val MIN_VARIANCE = 1e-6
    }
}

private fun Cursor.column(name: String): Int = getColumnIndexOrThrow(name)
private fun Cursor.string(name: String): String = getString(column(name))
private fun Cursor.int(name: String): Int = getInt(column(name))
private fun Cursor.double(name: String): Double = getDouble(column(name))
private fun Cursor.nullableInt(name: String): Int? = column(name).let { if (isNull(it)) null else getInt(it) }
private fun Cursor.nullableDouble(name: String): Double? = column(name).let { if (isNull(it)) null else getDouble(it) }
