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
import dev.kian.mymettle.domain.inference.InferenceModelComponent
import dev.kian.mymettle.domain.inference.ModelConfigDefinition
import dev.kian.mymettle.domain.inference.NBio7DConfig
import dev.kian.mymettle.domain.inference.NBio7DModelConfigs
import dev.kian.mymettle.domain.inference.NBio7DModelIdentity
import dev.kian.mymettle.domain.inference.TemporalStateConfigV1
import dev.kian.mymettle.domain.performance.Laterality
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
    val upstreamProvenance: NBio7EUpstreamProvenanceV1,
)

data class NBio7EUpstreamProvenanceV1(
    val capabilityModelIdentity: String,
    val capabilitySolverIdentity: String,
    val capabilityEvidencePolicyIdentity: String,
    val capabilityEvaluationProtocol: String,
    val doseSourceMode: String,
    val doseModelIdentities: Map<String, String>,
    val doseConfigIds: Map<String, String>,
    val doseEligibleSessions: Int,
    val pd001Status: String = "OPEN",
    val pd002Status: String = "OPEN",
) {
    init {
        require(capabilityModelIdentity.isNotBlank())
        require(capabilitySolverIdentity.isNotBlank())
        require(capabilityEvidencePolicyIdentity.isNotBlank())
        require(capabilityEvaluationProtocol.isNotBlank())
        require(doseSourceMode == DOSE_SOURCE_MODE)
        require(doseModelIdentities == expectedDoseModelIdentities)
        require(doseConfigIds == expectedDoseConfigIds)
        require(doseEligibleSessions >= 0)
        require(pd001Status == "OPEN" && pd002Status == "OPEN")
    }

    companion object {
        const val DOSE_SOURCE_MODE = "CAUSAL_IN_MEMORY_7D_REPLAY"
        val expectedDoseModelIdentities: Map<String, String> = mapOf(
            "set_demand" to NBio7DModelIdentity.DEMAND,
            "exposure" to NBio7DModelIdentity.EXPOSURE,
            "effective_dose" to NBio7DModelIdentity.EFFECTIVE_DOSE,
            "session_dose" to NBio7DModelIdentity.SESSION_DOSE,
        ).toSortedMap()
        val expectedDoseConfigIds: Map<String, String> = NBio7DModelConfigs.definitions(NBio7DConfig())
            .associate { it.component.storageValue to it.id.value }
            .toSortedMap()
    }
}

/** Immutable identity persisted in the legacy-named temporalModelConfigId Room15 column. */
object NBio7EExecutionConfigV2 {
    const val SEMANTIC_VERSION = "n-bio-7e-state-context-execution-v2"
    private val CREATED_AT: Instant = Instant.parse("2026-09-04T00:00:00Z")

    fun definition(
        temporal: TemporalStateConfigV1,
        upstream: NBio7EUpstreamProvenanceV1,
    ): ModelConfigDefinition = ModelConfigDefinition.create(
        component = InferenceModelComponent.SYSTEMIC_CONTEXT,
        modelFamily = "neutral_temporal_state_with_versioned_upstream_inputs",
        modelName = "n_bio_7e_state_context_execution",
        semanticVersion = SEMANTIC_VERSION,
        configSchemaVersion = 2,
        parameters = mapOf(
            "temporalConfig" to temporal.canonicalPayload,
            "capabilityModelIdentity" to upstream.capabilityModelIdentity,
            "capabilitySolverIdentity" to upstream.capabilitySolverIdentity,
            "capabilityEvidencePolicyIdentity" to upstream.capabilityEvidencePolicyIdentity,
            "capabilityEvaluationProtocol" to upstream.capabilityEvaluationProtocol,
            "doseSourceMode" to upstream.doseSourceMode,
            "doseModelIdentities" to upstream.doseModelIdentities.entries.joinToString(";") { "${it.key}=${it.value}" },
            "doseConfigIds" to upstream.doseConfigIds.entries.joinToString(";") { "${it.key}=${it.value}" },
            "pd001Status" to upstream.pd001Status,
            "pd002Status" to upstream.pd002Status,
            "authority" to "SHADOW_CANDIDATE_ONLY",
        ),
        createdAt = CREATED_AT,
    )
}

/**
 * Privacy-bounded acceptance reader. Raw note text is read only long enough to verify interpretation
 * currentness by SHA-256; neither modules nor the acceptance export receive it.
 */
class NBio7EHistoricalEvidenceReader(
    private val database: MyMettleDatabase,
) {
    fun read(): NBio7EHistoricalEvidenceV1 {
        val dynamic = NBio7BRawHistoryReader(database).read()
        val capabilitySolver = NBioCorrectedCandidateV2Bundle.sparseSolver()
        val capabilityBakeoffs = dynamic.profiles.values
            .sortedBy { it.semantics.executionProfileVersionId.value }
            .flatMap { descriptor ->
                val version = descriptor.semantics.executionProfileVersionId.value
                dynamic.revisions.asSequence()
                    .filter { it.evidence.executionProfileVersionId.value == version }
                    .map { it.evidence.laterality }
                    .distinct()
                    .sortedBy(Laterality::storageValue)
                    .map { side ->
                        NBioCorrectedCandidateV2Bundle.evaluateHistorical(
                            solvers = listOf(capabilitySolver),
                            profile = descriptor.semantics,
                            side = side,
                            revisions = dynamic.revisions,
                        )
                    }
                    .toList()
            }
        val evaluationProtocols = capabilityBakeoffs.map { it.protocolVersion }.distinct()
        require(evaluationProtocols.size <= 1) { "7E capability inputs must share one evaluation protocol." }
        val heldOut = capabilityBakeoffs.flatMap { bakeoff ->
            require(bakeoff.candidates.size == 1)
            val candidate = bakeoff.candidates.single()
            require(candidate.solverIdentity == capabilitySolver.solverIdentity)
            candidate.observations
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
                // Same-session profile observations share context and are not independent draws.
                // Keep a conservative fully-dependent variance rather than shrinking by row count.
                capabilityPredictiveVariance = predictiveVariances.average(),
                profileObservationCount = observations.size,
            )
        }.sortedWith(compareBy<NBio7ESessionResidualV1> { it.at }.thenBy { it.sessionId })

        val context = currentSessionContext()
        val dose = replaySessionDose(dynamic)
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
            upstreamProvenance = NBio7EUpstreamProvenanceV1(
                capabilityModelIdentity = NBioCorrectedCandidateV2Bundle.mathematicalModelIdentity.identity,
                capabilitySolverIdentity = capabilitySolver.solverIdentity.identity,
                capabilityEvidencePolicyIdentity = NBioCorrectedCandidateV2Bundle.evidencePolicy.identity,
                capabilityEvaluationProtocol = evaluationProtocols.singleOrNull()
                    ?: "NOT_EVALUATED_NO_DYNAMIC_PROFILE_SIDE",
                doseSourceMode = NBio7EUpstreamProvenanceV1.DOSE_SOURCE_MODE,
                doseModelIdentities = NBio7EUpstreamProvenanceV1.expectedDoseModelIdentities,
                doseConfigIds = NBio7EUpstreamProvenanceV1.expectedDoseConfigIds,
                doseEligibleSessions = dose.first,
            ),
        )
    }

    fun currentContextEvidenceFingerprintInputs(): List<String> = currentSessionContext().map { row ->
        listOf(
            row.sessionId,
            row.evidence.evidenceId,
            row.evidence.featureKey.canonical,
            row.evidence.missingness.name,
            row.evidence.sourceRevisionId,
        ).joinToString("|")
    }.sorted()

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

    private fun replaySessionDose(dynamic: NBio7BRawHistory): Pair<Int, Map<String, DatedSessionDose>> {
        val nonDynamic = NBio7CRawHistoryReader(database).read()
        val inputs = NBio7DHistoricalInputReader(database).read()
        val replayKnowledgeAt = listOfNotNull(
            dynamic.revisions.maxOfOrNull { it.recordedAt },
            nonDynamic.revisions.maxOfOrNull { it.recordedAt },
            inputs.sessions.values.maxOfOrNull { it.completedAt },
        ).maxOrNull() ?: Instant.EPOCH
        val plan = NBio7DHistoricalReplayPlanner.plan(dynamic, nonDynamic, inputs, replayKnowledgeAt)
        val execution = NBio7DHistoricalReplayExecutor(NBio7DConfig()).execute(plan, dynamic, nonDynamic)
        val doses = execution.sessions.mapNotNull { session ->
            val resolvedMuscleMedians = session.result.muscleResults.mapNotNull { it.dose.rawSummary?.estimateMedian }
            if (resolvedMuscleMedians.isEmpty()) return@mapNotNull null
            val completedAt = inputs.sessions.getValue(session.sessionId).completedAt
            session.sessionId to DatedSessionDose(completedAt, resolvedMuscleMedians.sum())
        }.toMap()
        return plan.sessions.size to doses
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
