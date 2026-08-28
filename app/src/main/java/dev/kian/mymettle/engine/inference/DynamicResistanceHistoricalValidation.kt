package dev.kian.mymettle.engine.inference

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.domain.inference.DynamicCapabilityCandidateVerdict
import dev.kian.mymettle.domain.inference.DynamicCapabilityValidationSummary
import dev.kian.mymettle.domain.inference.DynamicHeldOutEvaluation
import dev.kian.mymettle.domain.inference.DynamicResistanceEvidencePolicy
import dev.kian.mymettle.domain.inference.DynamicResistanceProfileSemantics
import dev.kian.mymettle.domain.inference.DynamicResistanceV1Contract
import dev.kian.mymettle.domain.inference.DynamicResistanceV2Contract
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
 * Validation-only source-availability policy for migrated history.
 *
 * SQLite/Native audit timestamps are never backdated. The raw-history adapter may map the
 * `recordedAt` carried by a HistoricalCompletedSetEvidenceRevision to the time at which the fact was
 * knowable in its source system. Ordinary Native observations keep their factual recordedAt. Only
 * explicitly provenance-marked Lite Legacy imports may use source-session finalisation time.
 *
 * A source session edited after completion remains unavailable at the original completion cutoff,
 * preventing a later edit from leaking backwards into that session's held-out evaluation.
 */
object DynamicHistoricalAvailabilityV2 {
    const val POLICY_ID = "n-bio-7b4-historical-source-availability-v2"

    fun resolve(
        observationSource: String,
        nativeRecordedAt: Instant,
        sessionCompletedAt: Instant,
        sessionEditedAt: Instant?,
    ): Instant = if (observationSource == DynamicResistanceV2Contract.LEGACY_UNSIDED_SOURCE) {
        maxOf(sessionCompletedAt, sessionEditedAt ?: sessionCompletedAt)
    } else {
        nativeRecordedAt
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
 * Revision currentness is resolved across the entire supersession graph before profile/side
 * filtering. Therefore a later correction that changes side or profile semantics can retire the
 * old observation once it is knowable, but cannot leak backwards before recordedAt.
 */
class DynamicResistanceHistoricalEvaluator(
    private val evaluator: DynamicResistanceRetrospectiveEvaluator = DynamicResistanceRetrospectiveEvaluator(),
    private val evidencePolicy: DynamicResistanceEvidencePolicy = DynamicResistanceV1Contract.evidencePolicy,
) {
    fun evaluate(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
    ): DynamicHistoricalValidationResult {
        val candidateSessionIds = revisions
            .filter { it.evidence.matches(profile, side) }
            .map { requireNotNull(it.evidence.sessionId) }
            .toSet()
        val sessions = revisions
            .filter { requireNotNull(it.evidence.sessionId) in candidateSessionIds }
            .groupBy { requireNotNull(it.evidence.sessionId) }
            .entries
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
                revisions.filter { requireNotNull(it.evidence.sessionId) in priorSessionIds },
                cutoff,
            ).filter { it.matches(profile, side) }
            val heldOutRaw = HistoricalObservationRevisionSelector.currentAsOf(
                revisions.filter { requireNotNull(it.evidence.sessionId) == sessionId },
                cutoff,
            ).filter { it.matches(profile, side) }
            val trainingProjection = DynamicResistanceEvidenceProjector.project(
                profile = profile,
                side = side,
                evidence = trainingRaw,
                policy = evidencePolicy,
            )
            val heldOutProjection = DynamicResistanceEvidenceProjector.project(
                profile = profile,
                side = side,
                evidence = heldOutRaw,
                policy = evidencePolicy,
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

    private fun CompletedSetEvidence.matches(
        profile: DynamicResistanceProfileSemantics,
        side: Laterality,
    ): Boolean = executionProfileVersionId == profile.executionProfileVersionId && laterality == side
}
