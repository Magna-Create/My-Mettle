package dev.kian.mymettle.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small layout helper for count × scaled-dp measurements used by dynamic workout set groups. */
internal operator fun Int.times(other: Dp): Dp = (toFloat() * other.value).dp
