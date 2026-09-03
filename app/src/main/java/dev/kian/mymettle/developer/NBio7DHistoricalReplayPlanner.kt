package dev.kian.mymettle.developer

import dev.kian.mymettle.domain.inference.CompletedSetEvidence
import dev.kian.mymettle.engine.inference.HistoricalCompletedSetEvidenceRevision
import java.time.Instant

/** Auditable, solver-free causal replay plan for real Room14 history. */
data class NBio7DReplaySetPlan(
    val target: CompletedSetEvidence,
    val context: NBio7DObservationContext,
    val preSessionTrainingEvidence: List<CompletedSetEvidence>,
) {
    val capabilityStreamKey: String = listOf(
        target.metricFamily.storageValue,
        target.executionProfileVersionId.value,
        target.laterality.storageValue,
    ).joinToString("|")
}

data class NBio7DReplaySessionPlan(
    val session: NBio7DHistoricalSession,
    val replayKnowledgeAt: Instant,
    val sets: List<NBio7DReplaySetPlan>,
) {
    val targetWorkingSetCount: Int get() = sets.size
    val muscleExposureCount: Int get() = sets.sumOf { it.context.exposures.size }
    val streamCount: Int get() = sets.map { it.capabilityStreamKey }.distinct().size
}

data class NBio7DHistoricalReplayPlan(
    val sessions: List<NBio7DReplaySessionPlan>,
    val skippedTargetObservations: Map<String, String>,
) {
    val targetWorkingSetCount: Int get() = sessions.sumOf { it.targetWorkingSetCount }
    val muscleExposureCount: Int get() = sessions.sumOf { it.muscleExposureCount }
    val skippedTargetReasonCounts: Map<String, Int> get() = skippedTargetObservations.values
        .groupingBy { it.substringBefore(':') }
        .eachCount()
        .toSortedMap()
}

object NBio7DHistoricalReplayPlanner {
    fun plan(
        dynamicHistory: NBio7BRawHistory,
        nonDynamicHistory: NBio7CRawHistory,
        inputs: NBio7DHistoricalInputs,
        replayKnowledgeAt: Instant,
    ): NBio7DHistoricalReplayPlan {
        val revisions = (dynamicHistory.revisions + nonDynamicHistory.revisions)
            .distinctBy { it.recordKey }
        val skipped = linkedMapOf<String, String>()
        val sessions = inputs.sessions.values
            .filter { !it.startedAt.isAfter(replayKnowledgeAt) }
            .sortedWith(compareBy<NBio7DHistoricalSession> { it.startedAt }.thenBy { it.sessionId })
            .mapNotNull { session ->
                val sessionRevisions = revisions.filter { it.evidence.sessionId == session.sessionId }
                if (sessionRevisions.isEmpty()) return@mapNotNull null
                val slice = NBio7DCausalHistory.slice(
                    revisions = revisions,
                    targetSessionId = session.sessionId,
                    targetStartedAt = session.startedAt,
                    replayKnowledgeAt = replayKnowledgeAt,
                )
                val sets = slice.target
                    .sortedWith(compareBy<CompletedSetEvidence> { it.completedAt }.thenBy { it.observationId })
                    .mapNotNull { target ->
                        // 7D working-set eligibility is semantic, not a storage-label allow-list:
                        // once a canonical performed observation exists, every non-warmup set is
                        // eligible. This deliberately includes prescribed and additional sets and
                        // remains compatible with imported historical kind labels.
                        if (target.warmUp) {
                            skipped[target.observationId] = "warm_up"
                            return@mapNotNull null
                        }
                        val context = inputs.observations[target.observationId]
                        if (context == null) {
                            skipped[target.observationId] = "missing_historical_context"
                            return@mapNotNull null
                        }
                        require(context.sessionId == session.sessionId)
                        require(context.executionProfileVersionId == target.executionProfileVersionId.value)
                        require(context.side == target.laterality.storageValue)
                        val training = slice.training.filter { candidate ->
                            !candidate.warmUp &&
                                candidate.metricFamily == target.metricFamily &&
                                candidate.executionProfileVersionId == target.executionProfileVersionId &&
                                candidate.laterality == target.laterality
                        }.sortedWith(compareBy<CompletedSetEvidence> { it.completedAt }.thenBy { it.observationId })
                        NBio7DReplaySetPlan(
                            target = target,
                            context = context,
                            preSessionTrainingEvidence = training,
                        )
                    }
                if (sets.isEmpty()) null else NBio7DReplaySessionPlan(
                    session = session,
                    replayKnowledgeAt = replayKnowledgeAt,
                    sets = sets,
                )
            }
        return NBio7DHistoricalReplayPlan(
            sessions = sessions,
            skippedTargetObservations = skipped.toSortedMap(),
        )
    }

    private val HistoricalCompletedSetEvidenceRevision.recordKey: String
        get() = listOf(
            evidence.observationId,
            recordedAt.toString(),
            supersedesObservationId ?: "root",
        ).joinToString("|")
}
