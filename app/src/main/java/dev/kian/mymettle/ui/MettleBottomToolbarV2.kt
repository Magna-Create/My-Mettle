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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

private const val ToolbarReferenceWidth = 453f

private data class ToolbarMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
}

private data class ToolbarDestination(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

/**
 * Global floating hotbar.
 *
 * The glass body and icon layer are intentionally siblings rather than parent/child. This lets the
 * optical body sit a touch higher while preserving the established icon/hit-target position.
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
        ToolbarDestination("Daily Update", MettleIcons.Cycle, 23f, 23f, onOpenHome),
        ToolbarDestination("Workout", MettleIcons.SportsMartialArts, 20f, 23f, onOpenWorkout),
        ToolbarDestination("Progress", MettleIcons.AddChart, 20f, 20f, onOpenHistory),
        ToolbarDestination("Exercise library", MettleIcons.CardsStack, 23f, 20f, onOpenLibrary),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        val viewportWidth = minOf(maxWidth, ToolbarReferenceWidth.dp)
        val metrics = ToolbarMetrics(
            scale = (viewportWidth.value / ToolbarReferenceWidth).coerceAtMost(1f),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = metrics.dp(6)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(metrics.dp(220))
                    .height(metrics.dp(64)),
                contentAlignment = Alignment.Center,
            ) {
                // Shift the material, not the controls. The device capture showed the icons in the
                // right physical place but the optical body sitting a few pixels too low around them.
                MettleGlassSurface(
                    modifier = Modifier
                        .offset(y = metrics.dp(-3))
                        .fillMaxSize()
                        .dropShadow(
                            shape = CircleShape,
                            shadow = Shadow(
                                radius = metrics.dp(14),
                                spread = metrics.dp(0.5),
                                color = Color.Black.copy(alpha = 0.24f),
                                offset = DpOffset(0.dp, metrics.dp(3)),
                            ),
                        ),
                    shape = CircleShape,
                    tint = Color.White.copy(alpha = 0.035f),
                    blurRadius = metrics.dp(5.5),
                    refractionDisplacement = metrics.dp(4.8),
                    refractionStrength = 0.38f,
                    shadowElevation = 0.dp,
                    // A slightly brighter but thinner edge reads as a sharp glass catch-light
                    // without turning the perimeter into a painted outline.
                    borderWidth = metrics.dp(0.55),
                    borderColor = Color.White.copy(alpha = 0.24f),
                ) {
                    // Material-only layer. Controls are deliberately rendered as a sibling below.
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.dp(8)),
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(4)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        Box(
                            modifier = Modifier
                                .width(metrics.dp(48))
                                .fillMaxHeight()
                                .clickable(role = Role.Button, onClick = destination.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.contentDescription,
                                tint = Color.White.copy(alpha = if (isSelected) 1f else 0.8f),
                                modifier = Modifier.size(
                                    DpSize(
                                        metrics.dp(destination.width),
                                        metrics.dp(destination.height),
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
