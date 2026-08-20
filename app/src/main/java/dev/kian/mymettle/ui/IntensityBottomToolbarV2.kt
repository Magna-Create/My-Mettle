package dev.kian.mymettle.ui

import androidx.compose.runtime.Composable

/** Intensity uses the same five-control geometry over its animated Haze source. */
@Composable
internal fun IntensityBottomToolbarV2(
    onOpenHome: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    MettleBottomToolbarV2(
        selectedIndex = -1,
        onOpenHome = onOpenHome,
        onOpenWorkout = onOpenWorkout,
        onOpenHistory = onOpenHistory,
        onOpenLibrary = onOpenLibrary,
        transparentMaterial = true,
    )
}
