package dev.kian.mymettle.workout

import kotlin.math.roundToInt

enum class CelebrationLevel {
    QUIET,
    SOLID,
    STRONG,
    FULL,
    EXCEEDED,
}

data class SessionAchievement(
    val score: Int,
    val level: CelebrationLevel,
    val targetExercises: Int,
    val achievedExercises: Int,
    val targetSets: Int,
    val loggedTargetSets: Int,
    val repBeats: Int,
    val extraLoggedSets: Int,
) {
    val targetComplete: Boolean = targetSets > 0 && loggedTargetSets >= targetSets
}

/**
 * Scores what happened against the prescription snapshot stored on this specific session.
 *
 * It never asks the current WorkoutModePolicy what A/B/C/D means. That keeps historical scores
 * stable even when mode definitions are tuned later and lets a fully completed D session earn the
 * same 100-point completion as a fully completed A session.
 */
object SessionAchievementScorer {
    fun score(workout: ActiveWorkout): SessionAchievement {
        val targets = workout.exercises.filter { it.entity.prescriptionIncluded }
        val targetSets = targets.sumOf { it.prescription.sets }
        val loggedTargetSets = targets.sumOf { exercise ->
            exercise.sets.count { set ->
                set.setIndex < exercise.prescription.sets && set.completedAt != null
            }
        }

        val achievedExercises = targets.count { exercise ->
            val prescribedSetCount = exercise.prescription.sets
            val allSetsLogged = prescribedSetCount > 0 && exercise.sets.count { set ->
                set.setIndex < prescribedSetCount && set.completedAt != null
            } >= prescribedSetCount
            exercise.entity.status == "completed" || allSetsLogged
        }

        val repBeats = targets.sumOf { exercise ->
            val repMaximum = exercise.prescription.repRange?.last
            exercise.sets.count { set ->
                set.completedAt != null &&
                    set.setIndex < exercise.prescription.sets &&
                    repMaximum != null &&
                    set.reps != null &&
                    set.reps > repMaximum
            }
        }

        val extraLoggedSets = workout.exercises.sumOf { exercise ->
            exercise.sets.count { set ->
                set.completedAt != null && (
                    !exercise.entity.prescriptionIncluded ||
                        set.kind == "additional" ||
                        set.setIndex >= exercise.prescription.sets
                    )
            }
        }

        val setRatio = if (targetSets == 0) 0.0 else (loggedTargetSets.toDouble() / targetSets).coerceIn(0.0, 1.0)
        val exerciseRatio = if (targets.isEmpty()) 0.0 else (achievedExercises.toDouble() / targets.size).coerceIn(0.0, 1.0)
        val baseScore = ((setRatio * 0.85) + (exerciseRatio * 0.15)) * 100.0
        val bonus = (repBeats * 2 + extraLoggedSets * 3).coerceAtMost(20)

        // Missing target work cannot be erased by doing lots of bonus work elsewhere. Bonuses only
        // push beyond 100 after the selected mode's own target is actually complete.
        val complete = targetSets > 0 && loggedTargetSets >= targetSets && achievedExercises >= targets.size
        val score = if (complete) {
            (100 + bonus).coerceAtMost(120)
        } else {
            (baseScore.roundToInt() + (bonus / 4)).coerceIn(0, 99)
        }

        return SessionAchievement(
            score = score,
            level = levelFor(score),
            targetExercises = targets.size,
            achievedExercises = achievedExercises,
            targetSets = targetSets,
            loggedTargetSets = loggedTargetSets,
            repBeats = repBeats,
            extraLoggedSets = extraLoggedSets,
        )
    }

    private fun levelFor(score: Int): CelebrationLevel = when {
        score < 50 -> CelebrationLevel.QUIET
        score < 80 -> CelebrationLevel.SOLID
        score < 100 -> CelebrationLevel.STRONG
        score < 110 -> CelebrationLevel.FULL
        else -> CelebrationLevel.EXCEEDED
    }
}
