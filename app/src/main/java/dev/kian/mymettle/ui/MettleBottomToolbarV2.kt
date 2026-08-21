package dev.kian.mymettle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

private const val ToolbarReferenceWidth = 453f

private data class ToolbarDestination(
    val label: String,
    val icon: ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

/** The split five-control hotbar from the Workout Session Figma frames. */
@Composable
internal fun MettleBottomToolbarV2(
    selectedIndex: Int,
    onOpenHome: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
    onQuickSelect: () -> Unit = onOpenWorkout,
    onLongPressWorkout: () -> Unit = onOpenWorkout,
    leadingIcon: ImageVector = MettleIcons.QuickSelect,
    leadingDescription: String = "Quick select",
    leadingProgress: Float? = null,
    showWorkoutControls: Boolean = true,
    transparentMaterial: Boolean = false,
) {
    val middle = listOf(
        ToolbarDestination("Daily Update", MettleIcons.Cycle, 23f, 23f, onOpenHome),
        ToolbarDestination("Progress", MettleIcons.AddChart, 20f, 20f, onOpenHistory),
        ToolbarDestination("Exercise library", MettleIcons.CardsStack, 23f, 20f, onOpenLibrary),
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
    ) {
        val scale = (minOf(maxWidth, ToolbarReferenceWidth.dp).value / ToolbarReferenceWidth)
            .coerceAtMost(1f)
        fun scaled(value: Number) = (value.toFloat() * scale).dp
        val tint = if (transparentMaterial) Color.Transparent else Color.White.copy(alpha = 0.028f)

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = scaled(39), vertical = scaled(6)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showWorkoutControls) {
                MettleControlGlassSurface(
                    modifier = Modifier.width(scaled(64)).height(scaled(64)),
                    shape = CircleShape,
                    tint = tint,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        leadingProgress?.let { progress ->
                            val stroke = scaled(4).toPx()
                            drawArc(
                                brush = Brush.sweepGradient(listOf(Color(0xFFBBEBED), Color(0xFFC3EFAD))),
                                startAngle = -90f,
                                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                                useCenter = false,
                                topLeft = androidx.compose.ui.geometry.Offset(stroke, stroke),
                                size = androidx.compose.ui.geometry.Size(size.width - stroke * 2f, size.height - stroke * 2f),
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    MettleGlassIconTouchTarget(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = leadingIcon,
                        contentDescription = leadingDescription,
                        onClick = onQuickSelect,
                        iconSize = DpSize(scaled(24), scaled(24)),
                        contentAlpha = 1f,
                    )
                }
            } else {
                Spacer(Modifier.width(scaled(64)).height(scaled(64)))
            }

            MettleControlGlassSurface(
                modifier = Modifier.width(scaled(168)).height(scaled(64)),
                shape = CircleShape,
                tint = tint,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = scaled(6)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    middle.forEachIndexed { index, destination ->
                        MettleGlassIconTouchTarget(
                            modifier = Modifier.width(scaled(48)).fillMaxHeight(),
                            imageVector = destination.icon,
                            contentDescription = destination.label,
                            onClick = destination.onClick,
                            iconSize = DpSize(scaled(destination.width), scaled(destination.height)),
                            contentAlpha = if (selectedIndex == index) 1f else 0.80f,
                            pressedHaloSize = scaled(38),
                        )
                    }
                }
            }

            if (showWorkoutControls) {
                MettleControlGlassSurface(
                    modifier = Modifier.width(scaled(64)).height(scaled(64)),
                    shape = CircleShape,
                    tint = tint,
                ) {
                    MettleGlassIconTouchTarget(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = MettleIcons.SportsMartialArts,
                        contentDescription = "Workout. Hold to finish.",
                        onClick = onOpenWorkout,
                        onLongClick = onLongPressWorkout,
                        iconSize = DpSize(scaled(20), scaled(23)),
                        contentAlpha = if (selectedIndex == 3) 1f else 0.84f,
                    )
                }
            } else {
                Spacer(Modifier.width(scaled(64)).height(scaled(64)))
            }
        }
    }
}
