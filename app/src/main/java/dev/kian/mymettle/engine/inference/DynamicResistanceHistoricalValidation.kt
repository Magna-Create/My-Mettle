package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.engine.performance.DynamicResistanceEvidenceProjector
import java.time.Instant

/**
 * One immutable raw observation revision plus the time at which that revision became knowable.
 * completedAt belongs to the performed set; recordedAt governs retrospective knowledge.
 */
data class HistoricalCompletedSetEvidenceRevision(
    val evidence: CompletedSetEvidence,
    val recordedAt: Instant,
    val sessionCompletedAt: Instant,
    val supersedesObservationId: String?,
) {
    init {
        require(evidence.sessionId != null) { "Historical capability validation requires a session owner." }
        require(!evidence.completedAt.isAfter(sessionCompletedAt)) {
            "A performed set cannot complete after its owning session completion."
        }
        require(supersedesObservationId == null || supersedesObservationId.isNotBlank())
    }
}

/**
 * Reconstructs the revision heads that were actually knowable at a historical cutoff. A correction
 * recorded after the cutoff cannot replace the observation that was current then.
 */
object HistoricalObservationRevisionSelector {
    fun currentAsOf(
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
        cutoff: Instant,
    ): List<CompletedSetEvidence> {
        val known = revisions.filter { !it.recordedAt.isAfter(cutoff) }
        val supersededIds = known.mapNotNull { it.supersedesObservationId }.toSet()
        return known
            .filterNot { it.evidence.observationId in supersededIds }
            .map { it.evidence }
            .sortedWith(
                compareBy<CompletedSetEvidence> { it.completedAt }
                    .thenBy { it.setRecordId }
                    .thenBy { it.observationId },
            )
    }
}

data class DynamicHistoricalValidationResult(
    val observations: List<DynamicHeldOutEvaluation>,
    val summary: DynamicCapabilityValidationSummary,
    val verdict: DynamicCapabilityCandidateVerdict,
    /** One candidate fit attempt per held-out session with non-empty earlier evidence. */
    val chronologicalFitCount: Int,
)

/**
 * HISTORICAL_SEMANTICS adapter over the shared 7B.3 evaluator.
 *
 * For held-out Sk, earlier sessions are reconstructed using only revisions recorded by Sk's
 * completion time. Sk itself is reconstructed at its own completion time, and the entire session is
 * passed to evaluateHeldOutSession as one longitudinal unit. No later correction can leak backwards.
 */
class DynamicResistanceHistoricalEvaluator(
    private val evaluator: DynamicResistanceRetrospectiveEvaluator = DynamicResistanceRetrospectiveEvaluator(),
) {
    fun evaluate(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
    ): DynamicHistoricalValidationResult {
        val relevant = revisions.filter {
            it.evidence.executionProfileVersionId == profile.executionProfileVersionId &&
                it.evidence.laterality == side
        }
        val sessions = relevant.groupBy { requireNotNull(it.evidence.sessionId) }.entries
            .sortedWith(
                compareBy<Map.Entry<String, List<HistoricalCompletedSetEvidenceRevision>>> { entry ->
                    entry.value.maxOf { it.sessionCompletedAt }
                }.thenBy { it.key },
            )
        val priorSessionIds = linkedSetOf<String>()
        val results = mutableListOf<DynamicHeldOutEvaluation>()
        var fitCount = 0
        sessions.forEach { (sessionId, sessionRevisions) ->
            val cutoff = sessionRevisions.maxOf { it.sessionCompletedAt }
            require(sessionRevisions.all { it.sessionCompletedAt == cutoff }) {
                "One session must have one stable completion horizon."
            }
            val trainingRaw = HistoricalObservationRevisionSelector.currentAsOf(
                relevant.filter { requireNotNull(it.evidence.sessionId) in priorSessionIds },
                cutoff,
            )
            val heldOutRaw = HistoricalObservationRevisionSelector.currentAsOf(sessionRevisions, cutoff)
            val trainingProjection = DynamicResistanceEvidenceProjector.project(
                profile = profile,
                side = side,
                evidence = trainingRaw,
                policy = DynamicResistanceV1Contract.evidencePolicy,
            )
            val heldOutProjection = DynamicResistanceEvidenceProjector.project(
                profile = profile,
                side = side,
                evidence = heldOutRaw,
                policy = DynamicResistanceV1Contract.evidencePolicy,
            )
            if (trainingProjection.evidence.isNotEmpty() && heldOutProjection.evidence.isNotEmpty()) fitCount += 1
            val heldOutResults = evaluator.evaluateHeldOutSession(trainingProjection, heldOutProjection)
            require(heldOutResults.all { it.sessionId == sessionId })
            results += heldOutResults
            priorSessionIds += sessionId
        }
        val summary = evaluator.summarize(results)
        return DynamicHistoricalValidationResult(
            observations = results,
            summary = summary,
            verdict = evaluator.verdict(summary),
            chronologicalFitCount = fitCount,
        )
    }
}
