package dev.kian.mymettle.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test fun brokenSizeReturnsNoTarget() {
        assertNull(resolveWorkoutSwapChoice(Offset(154f, 526f), Offset.Zero, 0f, 983f))
    }
}
