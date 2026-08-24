package dev.kian.mymettle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.kian.mymettle.ui.theme.MettleBackground
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant

private const val MettleHeaderReferenceWidth = 453f

/**
 * Shared fixed-header viewport for ordinary destinations. Content and fog are the header's Haze
 * source; the header itself is deliberately outside that source so the glass cannot self-sample.
 */
@Composable
internal fun MettleHeaderScreen(
    destination: String,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundBrush: Brush? = null,
    content: @Composable BoxScope.(topContentPadding: Dp) -> Unit,
) {
    val headerHazeState = rememberHazeState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportWidth = minOf(maxWidth, MettleHeaderReferenceWidth.dp)
        val scale = (viewportWidth.value / MettleHeaderReferenceWidth).coerceAtMost(1f)
        val headerFadeHeight = (118f * scale).dp

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .hazeSource(headerHazeState)
                .then(
                    if (backgroundBrush != null) Modifier.background(backgroundBrush)
                    else Modifier.background(MettleBackground),
                ),
        ) {
            content(headerFadeHeight)
            MettleAppHeaderFog(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerFadeHeight)
                    .align(Alignment.TopCenter),
            )
        }

        CompositionLocalProvider(LocalMettleHazeState provides headerHazeState) {
            MettleAppHeader(
                destination = destination,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )
        }
    }
}

/** A non-refractive veil which lets scrolling content disappear progressively behind a header. */
@Composable
internal fun MettleAppHeaderFog(
    modifier: Modifier = Modifier,
    baseColor: Color = Color(0xFF10150F),
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to baseColor.copy(alpha = .98f),
                .42f to baseColor.copy(alpha = .86f),
                .76f to baseColor.copy(alpha = .38f),
                1f to Color.Transparent,
            ),
        ),
    )
}

/**
 * Fixed destination header for Native screens outside the Workout viewport.
 *
 * The geometry intentionally mirrors WorkoutHeader: status-bar safe, 70 dp tall, left-aligned
 * two-line identity and one locally sampled action capsule. It does not inherit Material's
 * TopAppBar content insets, which were making these destinations sit differently from Workout.
 */
@Composable
internal fun MettleAppHeader(
    destination: String,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewportWidth = minOf(maxWidth, MettleHeaderReferenceWidth.dp)
        val scale = (viewportWidth.value / MettleHeaderReferenceWidth).coerceAtMost(1f)
        fun unitDp(value: Number): Dp = (value.toFloat() * scale).dp

        Row(
            modifier = Modifier
                .width(viewportWidth)
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .height(unitDp(70.369))
                .padding(start = unitDp(21), end = unitDp(18)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "My Mettle",
                    color = MettleOnPrimaryContainer,
                    fontSize = (24.2f * scale).sp,
                    lineHeight = (31f * scale).sp,
                )
                Text(
                    destination,
                    color = MettleOnSurfaceVariant,
                    fontSize = (13.2f * scale).sp,
                    lineHeight = (18f * scale).sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            MettleControlGlassSurface(
                modifier = Modifier.width(unitDp(96)).height(unitDp(52)),
                tint = MettleOnPrimaryContainer.copy(alpha = .075f),
                borderColor = Color.White.copy(alpha = .10f),
                shadowElevation = unitDp(3.087),
                preserveEdgeDefinition = true,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MettleGlassIconTouchTarget(
                        modifier = Modifier.size(width = unitDp(48), height = unitDp(52)),
                        imageVector = MettleIcons.Settings,
                        contentDescription = "Settings",
                        onClick = onOpenSettings,
                        iconSize = DpSize(unitDp(16.3916), unitDp(16.3916)),
                        pressedHaloSize = unitDp(36),
                    )
                    MettleGlassIconTouchTarget(
                        modifier = Modifier.size(width = unitDp(48), height = unitDp(52)),
                        imageVector = MettleIcons.AccountCircle,
                        contentDescription = "Account",
                        onClick = onOpenAccount,
                        iconSize = DpSize(unitDp(16.3916), unitDp(16.3916)),
                        pressedHaloSize = unitDp(36),
                    )
                }
            }
        }
    }
}
