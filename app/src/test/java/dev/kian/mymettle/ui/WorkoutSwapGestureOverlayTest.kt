package dev.kian.mymettle.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSwapGestureOverlayTest {
    @Test fun exactFigmaTargetsResolveToDistinctActions() {
        val points = listOf(
            Offset(154f, 526f) to WorkoutSwapChoice.SWITCH_EQUIPMENT,
            Offset(298f, 526f) to WorkoutSwapChoice.ADD_EQUIPMENT,
            Offset(89f, 653f) to WorkoutSwapChoice.SUBSTITUTE_EXERCISE,
            Offset(363f, 653f) to WorkoutSwapChoice.CANCEL,
        )
        points.forEach { (point, expected) ->
            assertEquals(expected, resolveWorkoutSwapChoice(point, Offset.Zero, 453f, 983f))
        }
    }

    @Test fun viewportScalingRetainsAllFourTargets() {
        assertEquals(
            WorkoutSwapChoice.SWITCH_EQUIPMENT,
            resolveWorkoutSwapChoice(Offset(154f * 384f / 453f, 526f * 832f / 983f), Offset.Zero, 384f, 832f),
        )
    }

    @Test fun shortFlickFinishesInsideTarget() {
        assertEquals(
            WorkoutSwapChoice.SWITCH_EQUIPMENT,
            resolveWorkoutSwapChoice(Offset(154f, 602f), Offset(0f, -850f), 453f, 983f),
        )
    }

    @Test fun distantReleaseDoesNotTriggerAccidentalSwap() {
        assertNull(resolveWorkoutSwapChoice(Offset(226f, 300f), Offset.Zero, 453f, 983f))
    }

    @Test fun longPressStartsDirectlyAtThumbRatherThanOriginalDock() {
        val gesture = WorkoutSwapGestureState()
        gesture.begin("exercise-1", Offset(195f, 730f), Offset(168f, 453f))
        assertEquals(Offset(168f, 453f), gesture.handleRoot)
        assertTrue(gesture.dragging)
        gesture.dragBy(Offset(25f, -30f))
        assertEquals(Offset(193f, 423f), gesture.handleRoot)
    }

    @Test fun nonTargetReleaseResetsAndAllowsAnotherOverlayDrag() {
        val gesture = WorkoutSwapGestureState()
        gesture.begin("exercise-1", Offset(195f, 730f), Offset(168f, 453f))
        gesture.finish(Offset.Zero)
        assertFalse(gesture.dragging)
        assertEquals("exercise-1", gesture.release?.exerciseId)
        gesture.resetHandle()
        assertNull(gesture.release)
        assertEquals("exercise-1", gesture.exerciseId)
        gesture.beginOnOverlay(Offset(225f, 645f))
        assertTrue(gesture.dragging)
        assertEquals(Offset(225f, 645f), gesture.handleRoot)
        gesture.dragBy(Offset(-15f, -40f))
        assertEquals(Offset(210f, 605f), gesture.handleRoot)
    }

    @Test fun cancelWhileOpenDoesNotStrandSelector() {
        val gesture = WorkoutSwapGestureState()
        gesture.open("exercise-2", Offset(226f, 880f))
        gesture.beginOnOverlay(Offset(221f, 720f))
        gesture.cancel()
        assertEquals("exercise-2", gesture.exerciseId)
        assertFalse(gesture.dragging)
        assertNull(gesture.release)
        gesture.beginOnOverlay(Offset(210f, 702f))
        assertTrue(gesture.dragging)
        gesture.dismiss()
        assertNull(gesture.exerciseId)
        assertFalse(gesture.dragging)
    }

    @Test fun brokenSizeReturnsNoTarget() {
        assertNull(resolveWorkoutSwapChoice(Offset(154f, 526f), Offset.Zero, 0f, 983f))
    }
}
