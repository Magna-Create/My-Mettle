package dev.kian.mymettle.ui

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.ui.theme.MettleBackground
import dev.kian.mymettle.ui.theme.MettleOnSurface
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant
import dev.kian.mymettle.workout.TrainingMode
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val IntensityReferenceWidth = 453f
private val IntensityTertiary = Color(0xFFA0CFD0)
private val IntensityOnTertiaryContainer = Color(0xFFBBEBED)
private val IntensityWarmSpark = Color(0xFFFF806F)

private data class IntensityMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

private data class ModeVisual(
    val mode: TrainingMode,
    val title: String,
    val description: String,
    val centreX: Float,
    val centreY: Float,
    val glowScale: Float,
    val ambientColour: Color,
)

// This mapping is deliberately page-local. It defines only the spatial language of the
// Quick Start intensity selector and does not change the workout-mode policy itself.
private val ModeVisuals = listOf(
    ModeVisual(
        mode = TrainingMode.B,
        title = "Focused Day",
        description = "More than Busy Day, without committing to the full session.",
        centreX = 153f,
        centreY = 651f,
        glowScale = 0.30f,
        ambientColour = Color(0xFF557B79),
    ),
    ModeVisual(
        mode = TrainingMode.A,
        title = "All In",
        description = "The complete programmed session.",
        centreX = 298f,
        centreY = 651f,
        glowScale = 0.40f,
        ambientColour = Color(0xFF567864),
    ),
    ModeVisual(
        mode = TrainingMode.D,
        title = "Can’t Be Arsed",
        description = "You may not feel like a full-blown workout today, and that’s OK. Let’s go with something calm.",
        centreX = 88f,
        centreY = 780f,
        glowScale = 0.24f,
        ambientColour = Color(0xFF606F70),
    ),
    ModeVisual(
        mode = TrainingMode.C,
        title = "Busy Day",
        description = "Fewer sets, with the same movement coverage.",
        centreX = 363f,
        centreY = 780f,
        glowScale = 0.54f,
        ambientColour = Color(0xFF526F8A),
    ),
)

@Composable
fun IntensitySelectorScreen(
    viewModel: N2WorkoutViewModel,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state = viewModel.uiState

    LaunchedEffect(state.workout?.session?.id) {
        if (state.workout != null) onOpenWorkout()
    }

    FigmaIntensitySelectorScreen(
        enabled = state.workout == null && !state.loading,
        onModeConfirmed = { mode ->
            if (state.workout == null && !state.loading) {
                viewModel.selectMode(mode)
                viewModel.startSession()
            }
        },
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
    )

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("My Mettle couldn’t do that") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissError) { Text("OK") }
            },
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FigmaIntensitySelectorScreen(
    enabled: Boolean,
    onModeConfirmed: (TrainingMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportWidth = minOf(maxWidth, IntensityReferenceWidth.dp)
        val metrics = IntensityMetrics(
            scale = (viewportWidth.value / IntensityReferenceWidth).coerceAtMost(1f),
        )
        val density = LocalDensity.current
        val view = LocalView.current

        var dragging by remember { mutableStateOf(false) }
        var settling by remember { mutableStateOf(false) }
        var rawOffset by remember { mutableStateOf(Offset.Zero) }
        var dragOrigin by remember { mutableStateOf(Offset.Zero) }
        var activeMode by remember { mutableStateOf<TrainingMode?>(null) }
        var ambientMode by remember { mutableStateOf<TrainingMode?>(null) }
        val warmPulse = remember { Animatable(0f) }

        val settleProgress by animateFloatAsState(
            targetValue = if (settling) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.72f, stiffness = 460f),
            label = "intensity-handle-return",
        )

        LaunchedEffect(settling) {
            if (settling) {
                kotlinx.coroutines.delay(320)
                rawOffset = Offset.Zero
                settling = false
            }
        }

        LaunchedEffect(activeMode) {
            val mode = activeMode
            if (mode == null) {
                ambientMode = null
                warmPulse.snapTo(0f)
            } else {
                warmPulse.snapTo(0f)
                warmPulse.animateTo(1f, animationSpec = tween(115))
                ambientMode = mode
                warmPulse.animateTo(0f, animationSpec = tween(420))
            }
        }

        val ambientColour by animateColorAsState(
            targetValue = ModeVisuals.firstOrNull { it.mode == ambientMode }?.ambientColour
                ?: Color(0xFF5E7A7A),
            animationSpec = tween(durationMillis = 620),
            label = "intensity-ambient-colour",
        )

        val releaseOffset = if (settling) rawOffset * (1f - settleProgress) else rawOffset
        val magneticOffset = if (dragging && activeMode != null) {
            val target = modeTargetVector(activeMode!!, metrics, density.density)
            val targetDistance = hypot(target.x, target.y).coerceAtLeast(1f)
            val distanceToTarget = hypot(target.x - releaseOffset.x, target.y - releaseOffset.y)
            val proximity = (1f - (distanceToTarget / targetDistance)).coerceIn(0f, 1f)
            val attraction = 0.08f + (0.18f * proximity)
            Offset(
                x = releaseOffset.x + ((target.x - releaseOffset.x) * attraction),
                y = releaseOffset.y + ((target.y - releaseOffset.y) * attraction),
            )
        } else {
            releaseOffset
        }

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .drawBehind {
                    drawIntensityBackground(
                        ambientColour = ambientColour,
                        warmPulse = warmPulse.value,
                        activeMode = activeMode,
                        metrics = metrics,
                    )
                },
        ) {
            IntensityAppBar(
                modifier = Modifier.offset(y = metrics.dp(40)),
                metrics = metrics,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            Text(
                modifier = Modifier
                    .offset(x = metrics.dp(29), y = metrics.dp(190))
                    .width(metrics.dp(394)),
                text = "Intensity",
                color = IntensityOnTertiaryContainer,
                fontSize = metrics.sp(57),
                lineHeight = metrics.sp(64),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            IntensityModeCopy(
                modifier = Modifier.offset(x = metrics.dp(47), y = metrics.dp(255)),
                metrics = metrics,
                activeMode = activeMode,
            )

            ModeVisuals.forEach { visual ->
                IntensityModeZone(
                    modifier = Modifier.offset(
                        x = metrics.dp(visual.centreX - 60f),
                        y = metrics.dp(visual.centreY - 60f),
                    ),
                    metrics = metrics,
                    visual = visual,
                    dragging = dragging,
                    selected = activeMode == visual.mode,
                    anotherSelected = activeMode != null && activeMode != visual.mode,
                )
            }

            val gestureTopLeftX = metrics.dp(190)
            val gestureTopLeftY = metrics.dp(744)
            val thresholdPx = with(density) { metrics.dp(30).toPx() }
            val maxRadiusPx = with(density) { metrics.dp(166).toPx() }

            Box(
                modifier = Modifier
                    .offset(x = gestureTopLeftX, y = gestureTopLeftY)
                    .size(metrics.dp(72))
                    .semantics {
                        stateDescription = activeMode?.let(::modeTitle) ?: "Drag to choose workout intensity"
                    }
                    .pointerInteropFilter { event ->
                        if (!enabled) return@pointerInteropFilter false

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                dragging = true
                                settling = false
                                rawOffset = Offset.Zero
                                activeMode = null
                                dragOrigin = Offset(event.x, event.y)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                true
                            }

                            MotionEvent.ACTION_MOVE -> {
                                val nextRaw = clampOffset(
                                    Offset(event.x - dragOrigin.x, event.y - dragOrigin.y),
                                    maxRadiusPx,
                                )
                                rawOffset = nextRaw
                                val nextMode = resolveMode(
                                    offset = nextRaw,
                                    current = activeMode,
                                    thresholdPx = thresholdPx,
                                    metrics = metrics,
                                    density = density.density,
                                )
                                if (nextMode != activeMode) {
                                    activeMode = nextMode
                                    if (nextMode != null) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                true
                            }

                            MotionEvent.ACTION_UP -> {
                                val distance = hypot(rawOffset.x, rawOffset.y)
                                val confirmed = activeMode
                                dragging = false
                                if (confirmed != null && distance >= thresholdPx) {
                                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                    onModeConfirmed(confirmed)
                                } else {
                                    activeMode = null
                                    settling = true
                                }
                                true
                            }

                            MotionEvent.ACTION_CANCEL -> {
                                dragging = false
                                activeMode = null
                                settling = true
                                true
                            }

                            else -> true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                IntensityHandle(
                    modifier = Modifier.offset {
                        IntOffset(
                            magneticOffset.x.roundToInt(),
                            magneticOffset.y.roundToInt(),
                        )
                    },
                    metrics = metrics,
                    active = activeMode != null,
                    dragging = dragging,
                )
            }
        }
    }
}

@Composable
private fun IntensityAppBar(
    modifier: Modifier,
    metrics: IntensityMetrics,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.dp(70.369))
            .padding(start = metrics.dp(21), end = metrics.dp(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My Mettle",
                color = IntensityOnTertiaryContainer,
                fontSize = metrics.sp(24.2),
                lineHeight = metrics.sp(31),
            )
            Text(
                text = "Quick Start | Workouts",
                color = MettleOnSurfaceVariant,
                fontSize = metrics.sp(13.2),
                lineHeight = metrics.sp(18),
                fontWeight = FontWeight.Medium,
            )
        }

        MettleGlassSurface(
            modifier = Modifier
                .offset(y = metrics.dp(-0.685))
                .width(metrics.dp(81))
                .height(metrics.dp(49.388)),
            shape = CircleShape,
            tint = IntensityOnTertiaryContainer.copy(alpha = 0.30f),
            blurRadius = metrics.dp(4),
            refractionDisplacement = metrics.dp(3),
            refractionStrength = 0.22f,
            shadowElevation = metrics.dp(3),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = metrics.dp(6.17)),
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(3.09)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntensityHeaderIcon(
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    metrics = metrics,
                )
                IntensityHeaderIcon(
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account and history",
                    onClick = onOpenAccount,
                    metrics = metrics,
                )
            }
        }
    }
}

@Composable
private fun IntensityHeaderIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    metrics: IntensityMetrics,
) {
    MettleGlassSurface(
        modifier = Modifier.size(metrics.dp(32.78)),
        shape = CircleShape,
        tint = Color.Transparent,
        blurRadius = 0.dp,
        refractionDisplacement = 0.dp,
        refractionStrength = 0f,
        shadowElevation = 0.dp,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = 0.82f),
                modifier = Modifier.size(metrics.dp(16.39)),
            )
        }
    }
}

@Composable
private fun IntensityModeCopy(
    modifier: Modifier,
    metrics: IntensityMetrics,
    activeMode: TrainingMode?,
) {
    val visual = ModeVisuals.firstOrNull { it.mode == activeMode }
    Column(
        modifier = modifier.width(metrics.dp(358)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = visual?.title ?: "Drag the handle",
            color = MettleOnSurface,
            fontSize = metrics.sp(if (visual == null) 28 else 31),
            lineHeight = metrics.sp(if (visual == null) 36 else 39),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Text(
            modifier = Modifier.padding(top = metrics.dp(if (visual == null) 7 else 8)),
            text = visual?.description ?: "---",
            color = MettleOnSurface.copy(alpha = if (visual == null) 0.78f else 0.92f),
            fontSize = metrics.sp(16),
            lineHeight = metrics.sp(24),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun IntensityModeZone(
    modifier: Modifier,
    metrics: IntensityMetrics,
    visual: ModeVisual,
    dragging: Boolean,
    selected: Boolean,
    anotherSelected: Boolean,
) {
    val targetAlpha = when {
        selected -> 1f
        anotherSelected -> 0.36f
        dragging -> 0.62f
        else -> 0.34f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(150),
        label = "intensity-zone-alpha-${visual.mode.code}",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.09f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "intensity-zone-scale-${visual.mode.code}",
    )

    Box(
        modifier = modifier
            .size(metrics.dp(120))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .shadow(
                elevation = metrics.dp(
                    when {
                        selected -> 14
                        dragging -> 5
                        else -> 0
                    },
                ),
                shape = CircleShape,
                clip = false,
                ambientColor = IntensityTertiary.copy(alpha = if (selected) 0.34f else 0.12f),
                spotColor = IntensityTertiary.copy(alpha = if (selected) 0.34f else 0.12f),
            )
            .clip(CircleShape)
            .drawBehind {
                drawCircle(Color(0xFF82999A).copy(alpha = 0.16f))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IntensityTertiary.copy(alpha = if (selected) 0.74f else 0.54f),
                            IntensityTertiary.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * visual.glowScale,
                    ),
                )
                if (selected) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.10f),
                                IntensityTertiary.copy(alpha = 0.08f),
                                Color.Transparent,
                            ),
                            center = center,
                            radius = size.minDimension * 0.72f,
                        ),
                    )
                }
            },
    )
}

@Composable
private fun IntensityHandle(
    modifier: Modifier,
    metrics: IntensityMetrics,
    active: Boolean,
    dragging: Boolean,
) {
    Box(
        modifier = modifier.size(metrics.dp(48)),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            Box(
                modifier = Modifier
                    .size(metrics.dp(54))
                    .blur(metrics.dp(12))
                    .drawBehind {
                        drawCircle(IntensityTertiary.copy(alpha = 0.24f))
                    },
            )
        }

        MettleGlassSurface(
            modifier = Modifier
                .size(metrics.dp(48))
                .shadow(
                    elevation = metrics.dp(if (active) 8 else if (dragging) 5 else 3),
                    shape = CircleShape,
                    clip = false,
                ),
            shape = CircleShape,
            tint = IntensityTertiary.copy(alpha = if (active) 0.20f else 0.12f),
            blurRadius = metrics.dp(6),
            refractionDisplacement = metrics.dp(4),
            refractionStrength = if (active) 0.32f else 0.24f,
            shadowElevation = 0.dp,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val outer = size.minDimension * 0.36f
                val inner = size.minDimension * 0.20f
                drawCircle(
                    color = Color.White.copy(alpha = 0.56f),
                    radius = outer,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.026f),
                )
                drawCircle(
                    color = IntensityTertiary.copy(alpha = 0.44f),
                    radius = outer * 0.76f,
                )
                drawCircle(
                    color = Color(0xFF304344).copy(alpha = 0.92f),
                    radius = inner,
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.30f),
                    radius = inner,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.018f),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntensityBackground(
    ambientColour: Color,
    warmPulse: Float,
    activeMode: TrainingMode?,
    metrics: IntensityMetrics,
) {
    drawRect(MettleBackground)
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to ambientColour,
                0.25f to lerp(ambientColour, MettleBackground, 0.22f),
                0.50f to lerp(ambientColour, MettleBackground, 0.48f),
                0.75f to lerp(ambientColour, MettleBackground, 0.72f),
                0.875f to lerp(ambientColour, MettleBackground, 0.86f),
                1f to MettleBackground,
            ),
            center = Offset(size.width / 2f, size.height * 1.02f),
            radius = size.height * 0.72f,
        ),
    )

    val visual = ModeVisuals.firstOrNull { it.mode == activeMode }
    if (visual != null && warmPulse > 0f) {
        val centre = Offset(
            x = metrics.dp(visual.centreX).toPx(),
            y = metrics.dp(visual.centreY).toPx(),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    IntensityWarmSpark.copy(alpha = 0.26f * warmPulse),
                    IntensityWarmSpark.copy(alpha = 0.08f * warmPulse),
                    Color.Transparent,
                ),
                center = centre,
                radius = metrics.dp(145).toPx(),
            ),
            center = centre,
            radius = metrics.dp(145).toPx(),
        )
    }
}

private fun modeTitle(mode: TrainingMode): String =
    ModeVisuals.firstOrNull { it.mode == mode }?.title ?: mode.label

private fun modeTargetVector(
    mode: TrainingMode,
    metrics: IntensityMetrics,
    density: Float,
): Offset {
    val visual = ModeVisuals.first { it.mode == mode }
    val handleX = 226f
    val handleY = 780f
    return Offset(
        x = metrics.dp(visual.centreX - handleX).value * density,
        y = metrics.dp(visual.centreY - handleY).value * density,
    )
}

private fun resolveMode(
    offset: Offset,
    current: TrainingMode?,
    thresholdPx: Float,
    metrics: IntensityMetrics,
    density: Float,
): TrainingMode? {
    val magnitude = hypot(offset.x, offset.y)
    if (magnitude < thresholdPx) return null

    val scores = ModeVisuals.associate { visual ->
        val target = modeTargetVector(visual.mode, metrics, density)
        visual.mode to directionalScore(offset, target)
    }
    val best = scores.maxByOrNull { it.value } ?: return null
    if (best.value < 0.60f) return null

    if (current != null && current != best.key) {
        val currentScore = scores[current] ?: -1f
        if (currentScore >= 0.43f && best.value < currentScore + 0.14f) return current
    }
    return best.key
}

private fun directionalScore(value: Offset, target: Offset): Float {
    val valueLength = hypot(value.x, value.y).coerceAtLeast(0.001f)
    val targetLength = hypot(target.x, target.y).coerceAtLeast(0.001f)
    return ((value.x * target.x) + (value.y * target.y)) / (valueLength * targetLength)
}

private fun clampOffset(value: Offset, maxRadius: Float): Offset {
    val distance = hypot(value.x, value.y)
    if (distance <= maxRadius || distance == 0f) return value
    val scale = maxRadius / distance
    return value * scale
}
