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

    @Test
    fun `cross day move removes source and inserts at release index`() {
        val draft = RoutineEditDraft(
            "routine-4",
            listOf(
                RoutineBoardDay("ψ", listOf(slot("a", 0), slot("b", 1))),
                RoutineBoardDay("φ", listOf(slot("c", 0).copy(daySymbol = "φ"))),
            ),
        )

        val moved = draft.move("b", "φ", 0)

        assertEquals(listOf("a"), moved.days[0].slots.map { it.id })
        assertEquals(listOf("b", "c"), moved.days[1].slots.map { it.id })
        assertEquals(listOf("φ", "φ"), moved.days[1].slots.map { it.daySymbol })
        assertEquals(listOf(0, 1), moved.days[1].slots.map { it.position })
    }

    @Test
    fun `insert duplicate and remove preserve normalised lane positions`() {
        val draft = RoutineEditDraft(
            "routine-4",
            listOf(
                RoutineBoardDay("ψ", listOf(slot("a", 0))),
                RoutineBoardDay("φ", emptyList()),
            ),
        )

        val edited = draft
            .insert(slot("b", 99), "φ")
            .duplicate("b", "b-copy")
            .remove("b")

        assertEquals(listOf("b-copy"), edited.days[1].slots.map { it.id })
        assertEquals(0, edited.days[1].slots.single().position)
        assertEquals("φ", edited.days[1].slots.single().daySymbol)
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
