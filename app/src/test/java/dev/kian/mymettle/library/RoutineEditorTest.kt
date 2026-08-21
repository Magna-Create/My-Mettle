package dev.kian.mymettle.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RoutineEditorTest {
    @Test
    fun `move within day normalises every position`() {
        val draft = RoutineEditDraft(
            baseVersionId = "routine-4",
            days = listOf(
                RoutineBoardDay("ψ", listOf(slot("a", 0), slot("b", 1), slot("c", 2))),
                RoutineBoardDay("φ", emptyList()),
            ),
        )

        val moved = draft.moveWithinDay("c", 0)

        assertEquals(listOf("c", "a", "b"), moved.days.first().slots.map { it.id })
        assertEquals(listOf(0, 1, 2), moved.days.first().slots.map { it.position })
        assertEquals("ψ", moved.days.first().slots.first().daySymbol)
    }

    @Test
    fun `no-op move preserves draft instance`() {
        val draft = RoutineEditDraft("routine-4", listOf(RoutineBoardDay("ψ", listOf(slot("a", 0)))))
        assertSame(draft, draft.moveWithinDay("a", 0))
    }

    private fun slot(id: String, position: Int) = RoutineBoardSlot(
        id = id,
        exerciseId = "exercise-$id",
        exerciseName = id.uppercase(),
        daySymbol = "ψ",
        position = position,
        importance = "core",
        preferredSets = 3,
        repMin = 8,
        repMax = 12,
        restSeconds = 90,
        lockedToDay = false,
    )
}
