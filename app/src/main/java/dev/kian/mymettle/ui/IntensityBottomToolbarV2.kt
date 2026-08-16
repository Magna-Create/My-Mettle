package dev.kian.mymettle.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

private const val IntensityToolbarReferenceWidth = 453f

private data class IntensityToolbarItem(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

/**
 * Intensity keeps the current four-destination hotbar geometry. This surface is deliberately
 * unpainted: Haze provides the material, the selector artwork provides the colour.
 */
@Composable
internal fun IntensityBottomToolbarV2(
    onOpenHome: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val destinations = listOf(
        IntensityToolbarItem("Daily Update", MettleIcons.Cycle, 23f, 23f, onOpenHome),
        IntensityToolbarItem("Workout", MettleIcons.SportsMartialArts, 20f, 23f, onOpenWorkout),
        IntensityToolbarItem("Progress", MettleIcons.AddChart, 20f, 20f, onOpenHistory),
        IntensityToolbarItem("Exercise library", MettleIcons.CardsStack, 23f, 20f, onOpenLibrary),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        val scale = (minOf(maxWidth, IntensityToolbarReferenceWidth.dp).value /
            IntensityToolbarReferenceWidth).coerceAtMost(1f)
        fun scaled(value: Number) = (value.toFloat() * scale).dp

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = scaled(6)),
            contentAlignment = Alignment.Center,
        ) {
            MettleGlassSurface(
                modifier = Modifier
                    .width(scaled(220))
                    .height(scaled(64)),
                shape = CircleShape,
                // No cyan paint and no dark base coat: the bar is the Haze optical material only.
                tint = Color.Transparent,
                baseColor = Color.Transparent,
                blurRadius = scaled(4.5),
                refractionDisplacement = scaled(9),
                refractionStrength = 0.78f,
                shadowElevation = scaled(3),
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
                    destinations.forEach { destination ->
                        Box(
                            modifier = Modifier
                                .width(scaled(48))
                                .fillMaxHeight()
                                .clickable(role = Role.Button, onClick = destination.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.contentDescription,
                                tint = Color.White.copy(alpha = 0.88f),
                                modifier = Modifier.size(
                                    DpSize(
                                        scaled(destination.width),
                                        scaled(destination.height),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
