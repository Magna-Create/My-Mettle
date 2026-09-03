package dev.kian.mymettle.ui

import androidx.compose.runtime.Composable

/** Compatibility route; all developer acceptance cards now live in one scrollable tools screen. */
@Composable
fun BiologyDeveloperHostScreen(onBack: () -> Unit) {
    BiologyDeveloperScreen(onBack = onBack)
}
