package dev.kian.mymettle.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset

internal data class WorkoutExitRelease(
    val positionRoot: Offset,
    val velocity: Offset,
    val generation: Int,
)

/**
 * UI-only bridge between the hotbar's long-press pointer stream and the workout exit overlay.
 *
 * Keeping the pointer owner alive means the workout button can lift directly out of the dock and
 * continue dragging without requiring a second press. No workout/domain state is stored here.
 */
@Stable
internal class WorkoutExitGestureState {
    var dockCentreRoot by mutableStateOf<Offset?>(null)
        private set

    var handleCentreRoot by mutableStateOf<Offset?>(null)
        private set

    var dragging by mutableStateOf(false)
        private set

    var releaseGeneration by mutableIntStateOf(0)
        private set

    private var releaseVelocity by mutableStateOf(Offset.Zero)
    private var consumedReleaseGeneration = 0

    fun updateDockCentre(centre: Offset) {
        val previousDock = dockCentreRoot
        dockCentreRoot = centre
        if (!dragging && (handleCentreRoot == null || handleCentreRoot == previousDock)) {
            handleCentreRoot = centre
        }
    }

    fun begin() {
        val home = dockCentreRoot ?: return
        handleCentreRoot = home
        dragging = true
        releaseVelocity = Offset.Zero
    }

    fun dragBy(delta: Offset) {
        val current = handleCentreRoot ?: dockCentreRoot ?: return
        handleCentreRoot = current + delta
    }

    fun release(velocity: Offset) {
        if (!dragging) return
        dragging = false
        releaseVelocity = velocity
        releaseGeneration += 1
    }

    fun cancelGesture() {
        if (!dragging) return
        dragging = false
        releaseVelocity = Offset.Zero
        releaseGeneration += 1
    }

    fun consumeRelease(): WorkoutExitRelease? {
        if (releaseGeneration <= consumedReleaseGeneration) return null
        consumedReleaseGeneration = releaseGeneration
        val position = handleCentreRoot ?: dockCentreRoot ?: return null
        return WorkoutExitRelease(position, releaseVelocity, releaseGeneration)
    }

    fun snapHome() {
        handleCentreRoot = dockCentreRoot
    }

    fun reset() {
        dragging = false
        handleCentreRoot = dockCentreRoot
        releaseVelocity = Offset.Zero
        consumedReleaseGeneration = releaseGeneration
    }
}

internal val LocalWorkoutExitGestureState = staticCompositionLocalOf<WorkoutExitGestureState?> { null }
