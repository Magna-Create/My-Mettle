package dev.kian.mymettle.ui

import dev.kian.mymettle.domain.exercise.EntryBasis
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.workout.ActiveWorkoutExercise

/**
 * Compatibility bridge for translated Lite data. Legacy PER_SIDE was the explicit statement that
 * the entered performance belongs to one side. Older N-BIO-6 imports left lateralityMode UNKNOWN,
 * so resolve that signal at the workout boundary without rewriting historical observations.
 * The resulting mode is also used for set completion and rest-timer semantics, not just rendering.
 */
internal fun ActiveWorkoutExercise.resolvedWorkoutLateralityMode(): LateralityMode =
    if (lateralityMode == LateralityMode.UNKNOWN && entryBasis == EntryBasis.PER_SIDE) {
        LateralityMode.UNILATERAL
    } else {
        lateralityMode
    }
