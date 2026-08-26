package dev.kian.mymettle.ui

import androidx.compose.ui.geometry.Offset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutExitGestureStateTest {
    @Test
    fun `long press drag keeps one handle from dock through release`() {
        val state = WorkoutExitGestureState()
        val dock = Offset(390f, 780f)

        state.updateDockCentre(dock)
        state.begin()

        assertTrue(state.dragging)
        assertEquals(dock, state.handleCentreRoot)

        state.dragBy(Offset(-80f, -120f))
        val dragged = Offset(310f, 660f)
        assertEquals(dragged, state.handleCentreRoot)

        val velocity = Offset(-900f, -350f)
        state.release(velocity)

        assertFalse(state.dragging)
        val release = state.consumeRelease()
        assertEquals(dragged, release?.positionRoot)
        assertEquals(velocity, release?.velocity)
        assertNull(state.consumeRelease(), "A release may only be consumed once by the overlay")
    }

    @Test
    fun `dock updates do not steal an active drag`() {
        val state = WorkoutExitGestureState()
        state.updateDockCentre(Offset(390f, 780f))
        state.begin()
        state.dragBy(Offset(-50f, -100f))

        val activeHandle = state.handleCentreRoot
        state.updateDockCentre(Offset(392f, 782f))

        assertEquals(activeHandle, state.handleCentreRoot)
        assertEquals(Offset(392f, 782f), state.dockCentreRoot)
    }

    @Test
    fun `settle and reset return the handle to the current dock`() {
        val state = WorkoutExitGestureState()
        val dock = Offset(390f, 780f)
        state.updateDockCentre(dock)
        state.begin()
        state.dragBy(Offset(-100f, -200f))
        state.release(Offset.Zero)
        state.consumeRelease()

        state.snapHome()
        assertEquals(dock, state.handleCentreRoot)

        state.begin()
        state.dragBy(Offset(-20f, -40f))
        state.reset()

        assertFalse(state.dragging)
        assertEquals(dock, state.handleCentreRoot)
        assertNull(state.consumeRelease())
    }

    @Test
    fun `begin is inert until the dock has been measured`() {
        val state = WorkoutExitGestureState()

        state.begin()
        state.dragBy(Offset(10f, 10f))
        state.release(Offset(100f, 100f))

        assertFalse(state.dragging)
        assertNull(state.handleCentreRoot)
        assertNull(state.consumeRelease())
    }
}
