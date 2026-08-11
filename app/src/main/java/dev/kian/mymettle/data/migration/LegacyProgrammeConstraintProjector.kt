package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.ProgrammeModeConstraintEntity
import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import dev.kian.mymettle.engine.targeting.BudgetedTargetExerciseSelector

/**
 * Converts per-slot Legacy A/B/C recipes into whole-session budgets once at import.
 *
 * The original values remain migration input, not runtime programme truth. In particular, Busy
 * Day's exercise budget is capped by a two-set minimum so a Legacy one-set-everywhere plan becomes
 * fewer useful movements rather than repeated equipment changes for one set each.
 */
object LegacyProgrammeConstraintProjector {
    const val SOURCE = "legacy-mode-budget-projection-v1"

    fun project(
        routineSlots: List<RoutineSlotEntity>,
        prescriptions: List<LegacyModePrescription>,
    ): List<ProgrammeModeConstraintEntity> {
        val prescriptionsByDay = prescriptions.groupBy { it.routineVersionId to it.daySymbol }

        return routineSlots
            .groupBy { it.routineVersionId to it.daySymbol }
            .toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .flatMap { (dayKey, slots) ->
                val dayPrescriptions = prescriptionsByDay[dayKey].orEmpty()
                validateDay(slots, dayPrescriptions, dayKey)
                val full = summary(dayPrescriptions, "A")
                val busy = summary(dayPrescriptions, "B")
                val minimum = summary(dayPrescriptions, "C")
                val focusedSetBudget = (full.workingSets + busy.workingSets + 1) / 2

                listOf(
                    constraint(dayKey, "A", full.workingSets, full.exerciseCount, 1, 0.0),
                    constraint(
                        dayKey,
                        "B",
                        focusedSetBudget,
                        exerciseBudget(full.exerciseCount, focusedSetBudget, 2),
                        2,
                        0.4,
                    ),
                    constraint(
                        dayKey,
                        "C",
                        busy.workingSets,
                        exerciseBudget(busy.exerciseCount, busy.workingSets, 2),
                        2,
                        0.7,
                    ),
                    constraint(
                        dayKey,
                        "D",
                        minimum.workingSets,
                        exerciseBudget(minimum.exerciseCount, minimum.workingSets, 2, cap = 4),
                        2,
                        1.0,
                    ),
                )
            }
    }

    private fun validateDay(
        slots: List<RoutineSlotEntity>,
        prescriptions: List<LegacyModePrescription>,
        dayKey: Pair<String, String>,
    ) {
        val expected = slots.size * LEGACY_MODE_COUNT
        if (prescriptions.size != expected) {
            throw LegacyImportException(
                "Lite Legacy programme ${dayKey.first}/${dayKey.second} expected $expected mode prescriptions but received ${prescriptions.size}.",
            )
        }
        val slotsById = slots.associateBy { it.id }
        prescriptions.forEach { prescription ->
            if (prescription.slotId !in slotsById || prescription.mode !in LEGACY_MODES) {
                throw LegacyImportException(
                    "Lite Legacy programme ${dayKey.first}/${dayKey.second} contains an invalid slot/mode constraint source.",
                )
            }
        }
        slots.forEach { slot ->
            val slotModes = prescriptions.filter { it.slotId == slot.id }.map { it.mode }.toSet()
            if (slotModes != LEGACY_MODES) {
                throw LegacyImportException("Lite Legacy slot ${slot.id} does not contain exactly A, B and C prescriptions.")
            }
        }
    }

    private fun summary(
        prescriptions: List<LegacyModePrescription>,
        mode: String,
    ): LegacyModeSummary {
        val included = prescriptions.filter { it.mode == mode && it.included && it.sets > 0 }
        return LegacyModeSummary(
            workingSets = included.sumOf { it.sets },
            exerciseCount = included.map { it.slotId }.distinct().size,
        )
    }

    private fun exerciseBudget(
        availableExercises: Int,
        workingSets: Int,
        minimumSetsPerExercise: Int,
        cap: Int? = null,
    ): Int {
        if (availableExercises == 0 || workingSets == 0) return 0
        return minOf(
            availableExercises,
            maxOf(1, workingSets / minimumSetsPerExercise),
            cap ?: Int.MAX_VALUE,
        )
    }

    private fun constraint(
        dayKey: Pair<String, String>,
        mode: String,
        workingSetBudget: Int,
        exerciseBudget: Int,
        minimumSetsPerExercise: Int,
        targetPriorityFloor: Double,
    ): ProgrammeModeConstraintEntity = ProgrammeModeConstraintEntity(
        routineVersionId = dayKey.first,
        daySymbol = dayKey.second,
        mode = mode,
        workingSetBudget = workingSetBudget,
        exerciseBudget = exerciseBudget,
        minimumSetsPerExercise = minimumSetsPerExercise,
        targetPriorityFloor = targetPriorityFloor,
        timeBudgetSeconds = null,
        source = SOURCE,
        resolverModelVersion = BudgetedTargetExerciseSelector.MODEL_VERSION,
    )

    private data class LegacyModeSummary(
        val workingSets: Int,
        val exerciseCount: Int,
    )

    private val LEGACY_MODES = setOf("A", "B", "C")
    private const val LEGACY_MODE_COUNT = 3
}
