package dev.kian.mymettle.engine.targeting

import dev.kian.mymettle.domain.exercise.ExecutionProfileId
import dev.kian.mymettle.domain.exercise.ExerciseId
import dev.kian.mymettle.domain.training.ResolvedTrainingTarget
import dev.kian.mymettle.domain.training.SessionConstraints
import dev.kian.mymettle.domain.training.TrainingTarget
import dev.kian.mymettle.domain.training.TrainingTargetId

interface TargetResolver {
    val modelVersion: String

    fun resolve(
        targets: List<TrainingTarget>,
        constraints: SessionConstraints,
    ): List<ResolvedTrainingTarget>
}

/**
 * Applies only explicit target-priority constraints. It does not manufacture a recovery or
 * under-development equation from N-BIO-4's deliberately neutral/null state fields.
 */
class ConstraintTargetResolver : TargetResolver {
    override val modelVersion: String = MODEL_VERSION

    override fun resolve(
        targets: List<TrainingTarget>,
        constraints: SessionConstraints,
    ): List<ResolvedTrainingTarget> {
        val eligibleIds = targets
            .filter { it.priority >= constraints.targetPriorityFloor }
            .mapTo(mutableSetOf()) { it.id }

        // A highly restrictive imported constraint must still be capable of producing a session.
        // Keep the highest-priority target when the programme has targets but none clear the floor.
        if (eligibleIds.isEmpty() && targets.isNotEmpty() && constraints.workingSetBudget > 0) {
            eligibleIds += targets.sortedWith(
                compareByDescending<TrainingTarget> { it.priority }.thenBy { it.id.value },
            ).first().id
        }

        return targets.map { target ->
            ResolvedTrainingTarget(
                target = target,
                included = target.id in eligibleIds,
                resolvedPriority = target.priority,
                resolutionModelVersion = modelVersion,
            )
        }
    }

    companion object {
        const val MODEL_VERSION = "n-bio-5-constraint-target-resolution-v0"
    }
}

data class ExerciseSelectionCandidate(
    val preferenceId: String,
    val exerciseId: ExerciseId,
    val executionProfileId: ExecutionProfileId,
    val ordinal: Int,
    val preferencePriority: Double,
    val preferredSetCap: Int,
    val repRange: IntRange,
    val restSeconds: Int,
    /** Confidence-weighted recruitment coverage by independent target identity. */
    val targetCoverage: Map<TrainingTargetId, Double>,
) {
    init {
        require(preferenceId.isNotBlank()) { "Exercise preference id cannot be blank." }
        require(ordinal >= 0) { "Exercise-preference ordinal cannot be negative." }
        require(preferencePriority in 0.0..1.0) { "Exercise-preference priority must be between 0 and 1." }
        require(preferredSetCap > 0) { "Exercise-preference set cap must be positive." }
        require(repRange.first > 0 && repRange.last >= repRange.first) { "Rep range must be positive and ordered." }
        require(restSeconds >= 0) { "Rest time cannot be negative." }
        require(targetCoverage.values.all { it >= 0.0 }) { "Target coverage cannot be negative." }
    }
}

data class SelectedExercise(
    val candidate: ExerciseSelectionCandidate,
    val targetIds: List<TrainingTargetId>,
    val sets: Int,
    val estimatedDurationSeconds: Int,
    val reason: String,
)

interface ExerciseSelector {
    val modelVersion: String

    fun select(
        targets: List<ResolvedTrainingTarget>,
        candidates: List<ExerciseSelectionCandidate>,
        constraints: SessionConstraints,
    ): List<SelectedExercise>
}

/**
 * Greedy, deterministic v0 session resolver.
 *
 * It spends one whole-session budget on the candidates that cover the most unresolved target
 * priority. Reduced modes therefore remove whole movements before collapsing every movement to
 * one set. If an imported programme has no resolvable recruitment at all, pinned preferences are
 * retained as an explicit compatibility fallback rather than being mistaken for target evidence.
 */
class BudgetedTargetExerciseSelector : ExerciseSelector {
    override val modelVersion: String = MODEL_VERSION

    override fun select(
        targets: List<ResolvedTrainingTarget>,
        candidates: List<ExerciseSelectionCandidate>,
        constraints: SessionConstraints,
    ): List<SelectedExercise> {
        if (
            constraints.workingSetBudget == 0 ||
            constraints.exerciseBudget == 0 ||
            candidates.isEmpty()
        ) return emptyList()

        val includedTargets = targets.filter { it.included }.associateBy { it.target.id }
        val pool = candidates.filter { candidate ->
            val coversIncludedTarget = candidate.targetCoverage.keys.any { it in includedTargets }
            val unresolvedImportedPreference = candidate.targetCoverage.isEmpty() &&
                candidate.preferencePriority >= constraints.targetPriorityFloor
            coversIncludedTarget || unresolvedImportedPreference
        }
            .toMutableList()
        val minimumSets = constraints.minimumSetsPerExercise
        val setLimitedCount = maxOf(1, constraints.workingSetBudget / minimumSets)
        val selectionLimit = minOf(constraints.exerciseBudget, setLimitedCount, pool.size)
        val selected = mutableListOf<WorkingSelection>()
        val coveredTargetIds = mutableSetOf<TrainingTargetId>()
        var remainingSets = constraints.workingSetBudget
        var remainingTime = constraints.timeBudgetSeconds

        while (selected.size < selectionLimit && pool.isNotEmpty() && remainingSets > 0) {
            val ranked = pool.sortedWith(
                compareByDescending<ExerciseSelectionCandidate> { candidate ->
                    score(candidate, includedTargets, coveredTargetIds, onlyUncovered = true)
                }
                    .thenByDescending { candidate -> score(candidate, includedTargets, emptySet(), onlyUncovered = false) }
                    .thenByDescending { it.preferencePriority }
                    .thenBy { it.ordinal }
                    .thenBy { it.preferenceId },
            )
            val candidate = ranked.first()
            pool.remove(candidate)

            var initialSets = minOf(minimumSets, candidate.preferredSetCap, remainingSets)
            if (remainingTime != null) {
                while (initialSets > 0 && estimatedDuration(initialSets, candidate.restSeconds) > remainingTime) {
                    initialSets -= 1
                }
            }
            if (initialSets == 0) continue

            val targetIds = candidate.targetCoverage.keys
                .filter { it in includedTargets }
                .sortedBy { it.value }
            val duration = estimatedDuration(initialSets, candidate.restSeconds)
            selected += WorkingSelection(
                candidate = candidate,
                targetIds = targetIds,
                sets = initialSets,
                estimatedDurationSeconds = duration,
                score = score(candidate, includedTargets, emptySet(), onlyUncovered = false),
                reason = if (targetIds.isEmpty()) UNRESOLVED_PREFERENCE_FALLBACK else TARGET_COVERAGE,
            )
            coveredTargetIds += targetIds
            remainingSets -= initialSets
            remainingTime = remainingTime?.minus(duration)
        }

        val allocationOrder = selected.sortedWith(
            compareByDescending<WorkingSelection> { it.score }
                .thenByDescending { it.candidate.preferencePriority }
                .thenBy { it.candidate.ordinal },
        )
        while (remainingSets > 0) {
            var changed = false
            allocationOrder.forEach { selection ->
                if (remainingSets == 0 || selection.sets >= selection.candidate.preferredSetCap) return@forEach
                val incrementalTime = SET_EXECUTION_SECONDS + selection.candidate.restSeconds
                val timeRemaining = remainingTime
                if (timeRemaining != null && incrementalTime > timeRemaining) return@forEach
                selection.sets += 1
                selection.estimatedDurationSeconds += incrementalTime
                remainingSets -= 1
                remainingTime = remainingTime?.minus(incrementalTime)
                changed = true
            }
            if (!changed) break
        }

        return selected
            .sortedBy { it.candidate.ordinal }
            .map { selection ->
                SelectedExercise(
                    candidate = selection.candidate,
                    targetIds = selection.targetIds,
                    sets = selection.sets,
                    estimatedDurationSeconds = selection.estimatedDurationSeconds,
                    reason = selection.reason,
                )
            }
    }

    private fun score(
        candidate: ExerciseSelectionCandidate,
        targets: Map<TrainingTargetId, ResolvedTrainingTarget>,
        coveredTargetIds: Set<TrainingTargetId>,
        onlyUncovered: Boolean,
    ): Double = candidate.targetCoverage.entries.sumOf { (targetId, coverage) ->
        val target = targets[targetId] ?: return@sumOf 0.0
        if (onlyUncovered && targetId in coveredTargetIds) 0.0 else target.resolvedPriority * coverage
    }

    private fun estimatedDuration(sets: Int, restSeconds: Int): Int =
        sets * SET_EXECUTION_SECONDS + (sets - 1).coerceAtLeast(0) * restSeconds

    private data class WorkingSelection(
        val candidate: ExerciseSelectionCandidate,
        val targetIds: List<TrainingTargetId>,
        var sets: Int,
        var estimatedDurationSeconds: Int,
        val score: Double,
        val reason: String,
    )

    companion object {
        const val MODEL_VERSION = "n-bio-5-budgeted-target-selection-v0"
        const val TARGET_COVERAGE = "target_coverage"
        const val UNRESOLVED_PREFERENCE_FALLBACK = "unresolved_preference_fallback"
        private const val SET_EXECUTION_SECONDS = 45
    }
}
