package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import dev.kian.mymettle.engine.inference.HistoricalObservationRevisionSelector
import java.time.Instant

/**
 * Revision-aware historical slice used by N-BIO-7D replay.
 *
 * Capability training is frozen at the instant the target session starts. Only sessions that had
 * already completed before that instant may train the baseline, and only the revision head that was
 * knowable at that instant is visible. This prevents same-session evidence and later corrections
 * from leaking backwards into the pre-session capability posterior.
 *
 * The target observation itself is selected at replayKnowledgeAt. This deliberately allows a later
 * correction of the performed set to repair the target SetDemand while keeping its capability
 * baseline causal. A correction to an earlier session affects only target sessions that begin after
 * that correction became knowable.
 */
object NBio7DCausalHistory {
    data class Slice(
        val training: List<CompletedSetEvidence>,
        val target: List<CompletedSetEvidence>,
        val targetSessionId: String,
        val targetStartedAt: Instant,
        val replayKnowledgeAt: Instant,
    )

    fun slice(
        revisions: Collection<HistoricalCompletedSetEvidenceRevision>,
        targetSessionId: String,
        targetStartedAt: Instant,
        replayKnowledgeAt: Instant,
    ): Slice {
        require(targetSessionId.isNotBlank())
        require(!replayKnowledgeAt.isBefore(targetStartedAt)) {
            "Replay knowledge horizon cannot precede the target session start."
        }

        val targetRevisions = revisions.filter { it.evidence.sessionId == targetSessionId }
        require(targetRevisions.isNotEmpty()) { "Target session $targetSessionId has no evidence revisions." }
        require(targetRevisions.all { !it.evidence.completedAt.isBefore(targetStartedAt) }) {
            "Target set evidence cannot precede the target session start."
        }

        val priorSessionIds = revisions
            .filter { revision ->
                revision.evidence.sessionId != targetSessionId &&
                    revision.sessionCompletedAt.isBefore(targetStartedAt)
            }
            .mapNotNull { it.evidence.sessionId }
            .toSet()

        val training = HistoricalObservationRevisionSelector.currentAsOf(
            revisions = revisions.filter { it.evidence.sessionId in priorSessionIds },
            cutoff = targetStartedAt,
        )
        val target = HistoricalObservationRevisionSelector.currentAsOf(
            revisions = targetRevisions,
            cutoff = replayKnowledgeAt,
        )

        return Slice(
            training = training,
            target = target,
            targetSessionId = targetSessionId,
            targetStartedAt = targetStartedAt,
            replayKnowledgeAt = replayKnowledgeAt,
        )
    }
}
