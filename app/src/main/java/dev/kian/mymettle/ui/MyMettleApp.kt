package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MyMettleApp() {
    Box(modifier = Modifier.fillMaxSize()) {
        RoomWorkoutScreen()
        NativeRestTimerOverlay()
        SessionOutcomeOverlay()
    }
}
