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
private val IntensityToolbarTint = Color(0xFFBBEBED)

private data class IntensityToolbarItem(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

/**
 * Intensity keeps the current four-destination hotbar geometry, but its material is deliberately
 * clear Haze glass rather than a coloured translucent pill. The selector artwork should remain
 * plainly visible through the bar; colour comes from the refracted backdrop, not from paint.
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
                // Haze tint is not equivalent to a normal alpha fill. 30% here was effectively
                // painting an opaque mint slab over the refraction. Keep tint almost neutral and
                // let the selector lighting itself colour the glass.
                tint = IntensityToolbarTint.copy(alpha = 0.045f),
                blurRadius = scaled(14),
                refractionDisplacement = scaled(7),
                refractionStrength = 0.56f,
                shadowElevation = scaled(3),
                borderWidth = scaled(0.7),
                borderColor = Color.White.copy(alpha = 0.18f),
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
                                tint = Color.White.copy(alpha = 0.86f),
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
