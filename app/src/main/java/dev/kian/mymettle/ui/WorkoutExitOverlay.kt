package dev.kian.mymettle.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val ExitReferenceWidth = 453f
private const val ExitReferenceHeight = 983f

private val ExitInk = Color(0xFF11140F)
private val ExitField = Color(0xFF5E7A7A)
private val ExitDangerField = Color(0xFFCC9089)
private val ExitCyan = Color(0xFFBBEBED)
private val ExitTertiary = Color(0xFFA0CFD0)
private val ExitPaper = Color(0xFFE1E4DA)
private val ExitMuted = Color(0xFFC3C8BB)
private val ExitDelete = Color(0xFFFFB4AB)

private data class ExitMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

private enum class ExitChoice {
    RETURN,
    COMPLETE_AND_RATE,
    COMPLETE,
    DELETE,
}

private data class ExitTarget(
    val choice: ExitChoice,
    val x: Float,
    val y: Float,
    val size: Float,
    val icon: ImageVector,
)

/**
 * Figma-grounded exit-workout radial menu.
 *
 * The first drag is deliberately owned by the global hotbar. The shared gesture state lets this
 * overlay draw the same workout button at the same physical centre while that original pointer
 * stream continues, so the button appears to lift out of the dock rather than teleporting to a
 * second control. If the user releases without selecting anything, the lifted handle settles home
 * and can then be dragged normally without another long press.
 */
@Composable
internal fun WorkoutExitOverlay(
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onDismiss: () -> Unit,
    onCompleteAndRate: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    val sharedGesture = LocalWorkoutExitGestureState.current
        ?: remember { WorkoutExitGestureState() }
    val view = LocalView.current
    val density = LocalDensity.current
    val exitHazeState = rememberHazeState()
    var overlayOriginRoot by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { coordinates ->
                overlayOriginRoot = coordinates.localToRoot(Offset.Zero)
            },
    ) {
        val scale = (maxWidth.value / ExitReferenceWidth).coerceAtMost(1f)
        val metrics = ExitMetrics(scale)
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val originRoot = overlayOriginRoot

        val targets = remember {
            listOf(
                ExitTarget(ExitChoice.DELETE, 114f, 653f, 80f, WorkoutExitIcons.DeleteForever),
                ExitTarget(ExitChoice.RETURN, 355f, 713f, 120f, WorkoutExitIcons.Cancel),
                ExitTarget(ExitChoice.COMPLETE_AND_RATE, 234f, 773f, 120f, WorkoutExitIcons.Verified),
                ExitTarget(ExitChoice.COMPLETE, 174f, 893f, 120f, WorkoutExitIcons.CheckCircle),
            )
        }

        fun targetCentre(target: ExitTarget): Offset = with(density) {
            Offset(metrics.dp(target.x).toPx(), metrics.dp(target.y).toPx())
        }

        fun resolveTarget(handle: Offset, thresholdScale: Float = .72f): ExitChoice? =
            targets.minByOrNull { target ->
                val centre = targetCentre(target)
                hypot(handle.x - centre.x, handle.y - centre.y)
            }?.takeIf { target ->
                val centre = targetCentre(target)
                val threshold = with(density) { metrics.dp(target.size).toPx() } * thresholdScale
                hypot(handle.x - centre.x, handle.y - centre.y) < threshold
            }?.choice

        val fallbackDock = with(density) {
            Offset(metrics.dp(391).toPx(), metrics.dp(929).toPx())
        }
        val dockLocal = sharedGesture.dockCentreRoot
            ?.let { it - originRoot }
            ?: fallbackDock
        val stateHandleLocal = sharedGesture.handleCentreRoot
            ?.let { it - originRoot }
            ?: dockLocal

        var settling by remember { mutableStateOf(false) }
        var settleStart by remember { mutableStateOf<Offset?>(null) }
        val settleProgress by animateFloatAsState(
            targetValue = if (settling) 1f else 0f,
            animationSpec = spring(dampingRatio = .72f, stiffness = 440f),
            label = "exit-handle-settle",
        )
        val handleLocal = settleStart?.takeIf { settling }?.let { start ->
            start + (dockLocal - start) * settleProgress
        } ?: stateHandleLocal
        val active = if (settling) null else resolveTarget(handleLocal)
        val dangerBlend by animateFloatAsState(
            targetValue = if (active == ExitChoice.DELETE) 1f else 0f,
            animationSpec = spring(dampingRatio = .88f, stiffness = 360f),
            label = "exit-danger-field",
        )

        var previousHapticChoice by remember { mutableStateOf<ExitChoice?>(null) }
        LaunchedEffect(active) {
            if (active != null && active != previousHapticChoice) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            previousHapticChoice = active
        }

        fun settleHome() {
            settleStart = stateHandleLocal
            settling = true
        }

        fun execute(choice: ExitChoice) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            sharedGesture.reset()
            settling = false
            settleStart = null
            when (choice) {
                ExitChoice.RETURN -> onDismiss()
                ExitChoice.COMPLETE_AND_RATE -> onCompleteAndRate()
                ExitChoice.COMPLETE -> onComplete()
                ExitChoice.DELETE -> onDelete()
            }
        }

        LaunchedEffect(sharedGesture.releaseGeneration) {
            val release = sharedGesture.consumeRelease() ?: return@LaunchedEffect
            val releaseLocal = release.positionRoot - originRoot
            val rawProjection = release.velocity * .11f
            val maximumProjection = with(density) { 180.dp.toPx() }
            val projectionLength = hypot(rawProjection.x, rawProjection.y)
            val projection = if (projectionLength > maximumProjection && projectionLength > 0f) {
                rawProjection * (maximumProjection / projectionLength)
            } else {
                rawProjection
            }
            val confirmed = resolveTarget(releaseLocal, .72f)
                ?: resolveTarget(releaseLocal + projection, .88f)
            if (confirmed != null) execute(confirmed) else settleHome()
        }

        LaunchedEffect(settling) {
            if (settling) {
                delay(330)
                sharedGesture.snapHome()
                settleStart = null
                settling = false
            }
        }

        val fieldColour = blendColour(ExitField, ExitDangerField, dangerBlend)
        Box(Modifier.fillMaxSize().hazeSource(exitHazeState)) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(ExitInk)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(fieldColour, fieldColour.copy(alpha = .68f), Color.Transparent),
                        center = Offset(size.width * .50f, size.height * 1.06f),
                        radius = size.height * .86f,
                    ),
                    alpha = .92f,
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = .38f),
                        .32f to Color.Transparent,
                        1f to Color.Transparent,
                    ),
                )
            }
        }

        CompositionLocalProvider(LocalMettleHazeState provides exitHazeState) {
            ExitHeader(
                metrics = metrics,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            val (title, subtitle) = when (active) {
                ExitChoice.RETURN -> "Return" to "Return to Workout"
                ExitChoice.COMPLETE_AND_RATE -> "Exit" to "Complete & Rate"
                ExitChoice.COMPLETE -> "Exit" to "Complete Workout"
                ExitChoice.DELETE -> "Delete" to "Exit & Delete Workout?"
                null -> "Exit" to "Drag the handle"
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = metrics.dp(190)),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = title,
                    color = if (active == ExitChoice.DELETE) ExitDelete else ExitCyan,
                    fontSize = metrics.sp(57),
                    lineHeight = metrics.sp(64),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(metrics.dp(1)))
                Text(
                    text = subtitle,
                    color = ExitPaper,
                    fontSize = metrics.sp(28),
                    lineHeight = metrics.sp(36),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
            }

            targets.forEach { target ->
                ExitTargetSurface(
                    target = target,
                    metrics = metrics,
                    dangerBlend = dangerBlend,
                )
            }

            ExitDockGhost(
                centre = dockLocal,
                metrics = metrics,
            )

            val handleHitSize = metrics.dp(92)
            val handleVisualSize = metrics.dp(64)
            Box(
                modifier = Modifier
                    .offset {
                        val half = with(density) { handleHitSize.toPx() / 2f }
                        IntOffset(
                            (handleLocal.x - half).roundToInt(),
                            (handleLocal.y - half).roundToInt(),
                        )
                    }
                    .size(handleHitSize)
                    .pointerInput(settling) {
                        if (settling) return@pointerInput
                        var velocityTracker = VelocityTracker()
                        detectDragGestures(
                            onDragStart = {
                                velocityTracker = VelocityTracker()
                                sharedGesture.begin()
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                sharedGesture.dragBy(amount)
                                sharedGesture.handleCentreRoot?.let { handle ->
                                    velocityTracker.addPosition(change.uptimeMillis, handle)
                                }
                            },
                            onDragEnd = {
                                val velocity = velocityTracker.calculateVelocity()
                                sharedGesture.release(Offset(velocity.x, velocity.y))
                            },
                            onDragCancel = sharedGesture::cancelGesture,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                ExitDragHandle(
                    modifier = Modifier.size(handleVisualSize),
                    active = active != null,
                )
            }
        }
    }
}

@Composable
private fun ExitHeader(
    metrics: ExitMetrics,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(metrics.dp(70.369))
            .padding(start = metrics.dp(21), end = metrics.dp(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "My Mettle",
                color = ExitCyan,
                fontSize = metrics.sp(24.2),
                lineHeight = metrics.sp(31),
            )
            Text(
                "Quick Start | Workouts",
                color = ExitMuted,
                fontSize = metrics.sp(13.2),
                lineHeight = metrics.sp(18),
                fontWeight = FontWeight.Medium,
            )
        }
        MettleControlGlassSurface(
            modifier = Modifier.width(metrics.dp(81)).height(metrics.dp(49.388)),
            shape = CircleShape,
            tint = Color.White.copy(alpha = .025f),
            shadowElevation = metrics.dp(3),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    iconSize = DpSize(metrics.dp(16.4), metrics.dp(16.4)),
                )
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                    iconSize = DpSize(metrics.dp(16.4), metrics.dp(16.4)),
                )
            }
        }
    }
}

@Composable
private fun ExitTargetSurface(
    target: ExitTarget,
    metrics: ExitMetrics,
    dangerBlend: Float,
) {
    val size = metrics.dp(target.size)
    val baseFill = if (target.choice == ExitChoice.DELETE) Color(0xFF5B5B57) else Color(0xFF6E8584)
    val dangerFill = if (target.choice == ExitChoice.DELETE) Color(0xFF8C6F6A) else Color(0xFF8B7571)
    val fill = blendColour(baseFill, dangerFill, dangerBlend).copy(alpha = .78f)
    val baseIcon = if (target.choice == ExitChoice.DELETE) ExitDelete else ExitTertiary
    val iconTint = blendColour(baseIcon, ExitDelete, dangerBlend * if (target.choice == ExitChoice.DELETE) .15f else .72f)
    Surface(
        modifier = Modifier
            .offset(
                x = metrics.dp(target.x - target.size / 2f),
                y = metrics.dp(target.y - target.size / 2f),
            )
            .size(size),
        shape = CircleShape,
        color = fill,
        border = BorderStroke(metrics.dp(.55), Color.White.copy(alpha = .08f)),
        shadowElevation = metrics.dp(4),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = target.icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(metrics.dp(if (target.size < 100f) 38 else 72)),
            )
        }
    }
}

@Composable
private fun ExitDockGhost(
    centre: Offset,
    metrics: ExitMetrics,
) {
    val density = LocalDensity.current
    val sizeDp = metrics.dp(48)
    Canvas(
        modifier = Modifier
            .offset {
                val half = with(density) { sizeDp.toPx() / 2f }
                IntOffset((centre.x - half).roundToInt(), (centre.y - half).roundToInt())
            }
            .size(sizeDp),
    ) {
        drawCircle(Color.White.copy(alpha = .045f))
        drawCircle(
            ExitPaper.copy(alpha = .68f),
            style = Stroke(metrics.dp(.8).toPx()),
        )
        drawCircle(
            ExitPaper.copy(alpha = .34f),
            radius = size.minDimension * .22f,
            style = Stroke(metrics.dp(.65).toPx()),
        )
    }
}

@Composable
private fun ExitDragHandle(modifier: Modifier, active: Boolean) {
    MettleControlGlassSurface(
        modifier = modifier,
        shape = CircleShape,
        tint = ExitCyan.copy(alpha = if (active) .105f else .065f),
        baseColor = ExitField.copy(alpha = .18f),
        borderWidth = .7.dp,
        borderColor = ExitPaper.copy(alpha = if (active) .88f else .68f),
        shadowElevation = if (active) 12.dp else 8.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                MettleIcons.SportsMartialArts,
                contentDescription = "Exit workout handle",
                tint = ExitPaper,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun blendColour(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}
