package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

private const val ToolbarReferenceWidth = 453f

private data class ToolbarItem(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

/**
 * Global floating hotbar.
 *
 * Geometry intentionally mirrors IntensityBottomToolbarV2 exactly. The shared control-glass
 * primitive owns the material now so every other glass interactable can reuse this exact optic.
 */
@Composable
internal fun MettleBottomToolbarV2(
    selectedIndex: Int,
    onOpenHome: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val destinations = listOf(
        ToolbarItem("Daily Update", MettleIcons.Cycle, 23f, 23f, onOpenHome),
        ToolbarItem("Workout", MettleIcons.SportsMartialArts, 20f, 23f, onOpenWorkout),
        ToolbarItem("Progress", MettleIcons.AddChart, 20f, 20f, onOpenHistory),
        ToolbarItem("Exercise library", MettleIcons.CardsStack, 23f, 20f, onOpenLibrary),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        val scale = (minOf(maxWidth, ToolbarReferenceWidth.dp).value /
            ToolbarReferenceWidth).coerceAtMost(1f)
        fun scaled(value: Number) = (value.toFloat() * scale).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = scaled(6)),
            contentAlignment = Alignment.Center,
        ) {
            MettleControlGlassSurface(
                modifier = Modifier
                    .width(scaled(220))
                    .height(scaled(64)),
                shape = CircleShape,
                tint = Color.White.copy(alpha = 0.028f),
                shadowElevation = scaled(4.5),
                borderWidth = scaled(0.7),
                borderColor = Color.White.copy(alpha = 0.22f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = scaled(8)),
                    horizontalArrangement = Arrangement.spacedBy(scaled(4)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        MettleGlassIconTouchTarget(
                            modifier = Modifier
                                .width(scaled(48))
                                .fillMaxHeight(),
                            imageVector = destination.icon,
                            contentDescription = destination.contentDescription,
                            onClick = destination.onClick,
                            iconSize = DpSize(
                                scaled(destination.width),
                                scaled(destination.height),
                            ),
                            contentAlpha = if (isSelected) 1f else 0.84f,
                            pressedHaloSize = scaled(38),
                        )
                    }
                }
            }
        }
    }
}
