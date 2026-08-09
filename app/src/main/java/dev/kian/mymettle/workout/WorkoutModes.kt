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
    B("B", "Focused day", "Near-full training with a small set reduction."),
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
 * B = derived between Legacy A and Legacy B
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
        WorkoutModeDefinition(TrainingMode.B, "Derived between Legacy A and Legacy B"),
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
        val definition = definitions.first { it.mode == mode }
        val resolved = exercises
            .sortedBy { it.ordinal }
            .mapNotNull { exercise ->
                val prescription = resolvePrescription(exercise, mode)
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

    private fun <T> resolvePrescription(
        exercise: ModeExercise<T>,
        mode: TrainingMode,
    ): BasePrescription = when (mode) {
        TrainingMode.A -> exercise.legacyA
        TrainingMode.C -> exercise.legacyB
        TrainingMode.D -> exercise.legacyC
        TrainingMode.B -> focusedPrescription(exercise.legacyA, exercise.legacyB)
    }

    private fun focusedPrescription(
        full: BasePrescription,
        busy: BasePrescription,
    ): BasePrescription {
        if (!full.included) return full

        val targetSets = maxOf(
            if (busy.included) busy.sets else 0,
            (full.sets - 1).coerceAtLeast(1),
        )
        return full.copy(
            included = true,
            sets = targetSets.coerceAtMost(full.sets.coerceAtLeast(1)),
        )
    }

    private fun importanceRank(value: ExerciseImportance): Int = when (value) {
        ExerciseImportance.PRINCIPAL -> 0
        ExerciseImportance.CORE -> 1
        ExerciseImportance.ACCESSORY -> 2
    }
}
