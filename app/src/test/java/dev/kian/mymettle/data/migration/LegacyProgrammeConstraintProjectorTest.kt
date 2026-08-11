package dev.kian.mymettle.data.migration

import dev.kian.mymettle.data.local.entity.RoutineSlotEntity
import kotlin.test.Test
import kotlin.test.assertEquals

class LegacyProgrammeConstraintProjectorTest {
    @Test
    fun `busy mode converts one set across every slot into fewer two-set movements`() {
        val slots = (0 until 6).map(::slot)
        val constraints = LegacyProgrammeConstraintProjector.project(
            routineSlots = slots,
            prescriptions = slots.flatMap { source ->
                listOf(
                    prescription(source, "A", sets = 3),
                    prescription(source, "B", sets = 1),
                    prescription(source, "C", sets = 1),
                )
            },
        ).associateBy { it.mode }

        assertEquals(18, constraints.getValue("A").workingSetBudget)
        assertEquals(6, constraints.getValue("A").exerciseBudget)
        assertEquals(6, constraints.getValue("C").workingSetBudget)
        assertEquals(3, constraints.getValue("C").exerciseBudget)
        assertEquals(2, constraints.getValue("C").minimumSetsPerExercise)
        assertEquals(0.7, constraints.getValue("C").targetPriorityFloor)
    }

    @Test
    fun `minimum mode caps whole movements and keeps its set budget`() {
        val slots = (0 until 10).map(::slot)
        val constraints = LegacyProgrammeConstraintProjector.project(
            routineSlots = slots,
            prescriptions = slots.flatMap { source ->
                listOf(
                    prescription(source, "A", sets = 3),
                    prescription(source, "B", sets = 2),
                    prescription(source, "C", sets = 1),
                )
            },
        ).associateBy { it.mode }

        assertEquals(10, constraints.getValue("D").workingSetBudget)
        assertEquals(4, constraints.getValue("D").exerciseBudget)
        assertEquals(1.0, constraints.getValue("D").targetPriorityFloor)
    }

    private fun slot(index: Int) = RoutineSlotEntity(
        id = "slot_$index",
        routineVersionId = "routine_v1",
        daySymbol = "psi",
        exerciseId = "exercise_$index",
        position = index,
        importance = if (index == 0) "principal" else "core",
        lockedToDay = false,
        preferredSets = 3,
        repMin = 8,
        repMax = 12,
        restSeconds = 120,
    )

    private fun prescription(
        slot: RoutineSlotEntity,
        mode: String,
        sets: Int,
    ) = LegacyModePrescription(
        routineVersionId = slot.routineVersionId,
        daySymbol = slot.daySymbol,
        slotId = slot.id,
        mode = mode,
        included = true,
        sets = sets,
        repMin = 8,
        repMax = 12,
        restSeconds = 120,
        deferToAnd = false,
    )
}
