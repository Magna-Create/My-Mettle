package dev.kian.mymettle.ui

import androidx.compose.runtime.staticCompositionLocalOf

internal enum class MettleWindowWidthClass {
    Compact,
    Medium,
    Expanded,
}

internal val LocalMettleWindowWidthClass = staticCompositionLocalOf {
    MettleWindowWidthClass.Compact
}
