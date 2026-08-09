package dev.kian.mymettle.workout

/**
 * Native workout modes deliberately sit above the imported Lite Legacy A/B/C prescriptions.
 *
 * Legacy history is never rewritten. For a new workout we interpret the three stored anchors
 * through this one policy object, so the meaning of A/B/C/D can be tuned later without scattering
 * mode logic through UI, persistence and session code.
 */
enum class TrainingMode(
    val code: String,
    val label: String,
    val description: String,
) {
    A("A", "Full day", "The complete programmed session."),
    B("B", "Focused day", "More than Busy Day, without committing to the full session."),
    C("C", "Busy day", "The old Busy Day prescription: fewer sets, same movement coverage."),
    D("D", "Can't be arsed", "Minimum viable training; lower-priority movements can disappear entirely."),
}

enum class ExerciseImportance {
    PRINCIPAL,
    CORE,
    ACCESSORY,
}

data class BasePrescription(
    val included: Boolean,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSeconds: Int,
)

data class ModeExercise<T>(
    val id: String,
    val ordinal: Int,
    val importance: ExerciseImportance,
    val legacyA: BasePrescription,
    val legacyB: BasePrescription,
    val legacyC: BasePrescription,
    val payload: T,
)

data class PlannedExercise<T>(
    val id: String,
    val ordinal: Int,
    val importance: ExerciseImportance,
    val prescription: BasePrescription,
    val payload: T,
)

data class WorkoutModeDefinition(
    val mode: TrainingMode,
    val legacyMeaning: String,
    val wholeExerciseCap: Int? = null,
    val permittedImportance: Set<ExerciseImportance> = ExerciseImportance.entries.toSet(),
)

/**
 * The only place that currently defines what the four workout modes do.
 *
 * Current mapping:
 * A = Legacy A
 * B = a session-level midpoint between Legacy A and Legacy B
 * C = Legacy B
 * D = Legacy C, then allowed to remove whole exercises
 *
 * When we add a user-facing mode editor, it should edit an equivalent configuration rather than
 * teaching the rest of the app about individual mode letters.
 */
object WorkoutModePolicy {
    private const val MINIMUM_MODE_EXERCISE_CAP = 4

    val definitions: List<WorkoutModeDefinition> = listOf(
        WorkoutModeDefinition(TrainingMode.A, "Legacy A / full prescription"),
        WorkoutModeDefinition(TrainingMode.B, "Session-level midpoint between Legacy A and Legacy B"),
        WorkoutModeDefinition(TrainingMode.C, "Legacy B / previous Busy Day"),
        WorkoutModeDefinition(
            mode = TrainingMode.D,
            legacyMeaning = "Legacy C / previous minimum prescription, with whole-exercise reduction",
            wholeExerciseCap = MINIMUM_MODE_EXERCISE_CAP,
            permittedImportance = setOf(ExerciseImportance.PRINCIPAL, ExerciseImportance.CORE),
        ),
    )

    fun <T> plan(
        exercises: List<ModeExercise<T>>,
        mode: TrainingMode,
    ): List<PlannedExercise<T>> {
        val ordered = exercises.sortedBy { it.ordinal }
        if (mode == TrainingMode.B) return focusedPlan(ordered)

        val definition = definitions.first { it.mode == mode }
        val resolved = ordered.mapNotNull { exercise ->
            val prescription = when (mode) {
                TrainingMode.A -> exercise.legacyA
                TrainingMode.C -> exercise.legacyB
                TrainingMode.D -> exercise.legacyC
                TrainingMode.B -> error("Focused mode is resolved at session level.")
            }
            if (!prescription.included || prescription.sets <= 0) return@mapNotNull null
            if (exercise.importance !in definition.permittedImportance) return@mapNotNull null
            PlannedExercise(
                id = exercise.id,
                ordinal = exercise.ordinal,
                importance = exercise.importance,
                prescription = prescription,
                payload = exercise.payload,
            )
        }

        val cap = definition.wholeExerciseCap ?: return resolved
        if (resolved.size <= cap) return resolved

        // D chooses the most important movements first, then restores programme order for training.
        val chosenIds = resolved
            .sortedWith(compareBy<PlannedExercise<T>>({ importanceRank(it.importance) }, { it.ordinal }))
            .take(cap)
            .mapTo(mutableSetOf()) { it.id }
        return resolved.filter { it.id in chosenIds }
    }

    /**
     * There is often no integer per-exercise value between Legacy A=3 sets and B=2 sets. Instead
     * of quietly making native B identical to C, resolve B across the whole session: start from the
     * old Busy Day workload and add roughly half of the sets that separate it from Full Day.
     * Extra sets go to principal/core movements first.
     */
    private fun <T> focusedPlan(exercises: List<ModeExercise<T>>): List<PlannedExercise<T>> {
        data class Working<T>(
            val source: ModeExercise<T>,
            var sets: Int,
            val fullSets: Int,
        )

        val working = exercises.mapNotNull { exercise ->
            val full = exercise.legacyA
            if (!full.included || full.sets <= 0) return@mapNotNull null
            val busySets = if (exercise.legacyB.included) exercise.legacyB.sets else 0
            Working(
                source = exercise,
                sets = busySets.coerceIn(0, full.sets),
                fullSets = full.sets,
            )
        }

        val busyTotal = working.sumOf { it.sets }
        val fullTotal = working.sumOf { it.fullSets }
        val gap = (fullTotal - busyTotal).coerceAtLeast(0)
        // Round upward so B is genuinely above C whenever there is any room between them.
        var setsToRestore = (gap + 1) / 2

        val upgradeOrder = working.sortedWith(
            compareBy<Working<T>>({ importanceRank(it.source.importance) }, { it.source.ordinal }),
        )
        while (setsToRestore > 0) {
            var changed = false
            for (entry in upgradeOrder) {
                if (setsToRestore <= 0) break
                if (entry.sets >= entry.fullSets) continue
                entry.sets += 1
                setsToRestore -= 1
                changed = true
            }
            if (!changed) break
        }

        return working
            .filter { it.sets > 0 }
            .sortedBy { it.source.ordinal }
            .map { entry ->
                PlannedExercise(
                    id = entry.source.id,
                    ordinal = entry.source.ordinal,
                    importance = entry.source.importance,
                    prescription = entry.source.legacyA.copy(included = true, sets = entry.sets),
                    payload = entry.source.payload,
                )
            }
    }

    private fun importanceRank(value: ExerciseImportance): Int = when (value) {
        ExerciseImportance.PRINCIPAL -> 0
        ExerciseImportance.CORE -> 1
        ExerciseImportance.ACCESSORY -> 2
    }
}
