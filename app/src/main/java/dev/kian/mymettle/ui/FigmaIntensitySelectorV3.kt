package dev.kian.mymettle.ui

import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.kian.mymettle.ui.theme.MettleBackground
import dev.kian.mymettle.ui.theme.MettleOnSurface
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant
import dev.kian.mymettle.workout.TrainingMode
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val IntensityV3ReferenceWidth = 453f
private val IntensityV3Tertiary = Color(0xFFA0CFD0)
private val IntensityV3OnTertiaryContainer = Color(0xFFBBEBED)
private val IntensityV3IdleAmbient = Color(0xFF6B8F90)
private val IntensityV3WarmSpark = Color(0xFFFF806F)

private data class IntensityV3Metrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

private data class IntensityV3ModeVisual(
    val mode: TrainingMode,
    val title: String,
    val description: String,
    val centreX: Float,
    val centreY: Float,
    val glowScale: Float,
    val ambientColour: Color,
)

private val IntensityV3ModeVisuals = listOf(
    IntensityV3ModeVisual(
        mode = TrainingMode.C,
        title = "Nice & Chill",
        description = "Fewer sets, with the same movement coverage.",
        centreX = 153f,
        centreY = 651f,
        glowScale = 0.30f,
        ambientColour = Color(0xFF557B79),
    ),
    IntensityV3ModeVisual(
        mode = TrainingMode.B,
        title = "Busy Day",
        description = "More than Nice & Chill, without committing to the full session.",
        centreX = 298f,
        centreY = 651f,
        glowScale = 0.40f,
        ambientColour = Color(0xFF567864),
    ),
    IntensityV3ModeVisual(
        mode = TrainingMode.D,
        title = "Can’t Be Arsed",
        description = "You may not feel like a full-blown workout today, and that’s OK. Let’s go with something calm.",
        centreX = 88f,
        centreY = 780f,
        glowScale = 0.24f,
        ambientColour = Color(0xFF606F70),
    ),
    IntensityV3ModeVisual(
        mode = TrainingMode.A,
        title = "All In",
        description = "The complete programmed session.",
        centreX = 363f,
        centreY = 780f,
        glowScale = 0.54f,
        ambientColour = Color(0xFF526F8A),
    ),
)

/**
 * Full-window base source for the intensity destination. The animated selector artwork registers
 * itself separately so Haze samples the exact live Canvas while retaining a source in wide gutters.
 */
@Composable
internal fun IntensityHazeBase(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MettleBackground),
    )
}

@Composable
fun IntensitySelectorScreenV3(
    viewModel: N2WorkoutViewModel,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state = viewModel.uiState

    LaunchedEffect(state.workout?.session?.id) {
        if (state.workout != null) onOpenWorkout()
    }

    IntensitySelectorPageV3(
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
private fun IntensitySelectorPageV3(
    enabled: Boolean,
    onModeConfirmed: (TrainingMode) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportWidth = minOf(maxWidth, IntensityV3ReferenceWidth.dp)
        val metrics = IntensityV3Metrics(
            scale = (viewportWidth.value / IntensityV3ReferenceWidth).coerceAtMost(1f),
        )
        val density = LocalDensity.current
        val view = LocalView.current
        val hazeState = LocalMettleHazeState.current

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
            label = "intensity-v3-handle-return",
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
                warmPulse.animateTo(1f, animationSpec = tween(110))
                ambientMode = mode
                warmPulse.animateTo(0f, animationSpec = tween(470))
            }
        }

        val ambientColour by animateColorAsState(
            targetValue = IntensityV3ModeVisuals.firstOrNull { it.mode == ambientMode }?.ambientColour
                ?: IntensityV3IdleAmbient,
            animationSpec = tween(durationMillis = 650),
            label = "intensity-v3-ambient-colour",
        )

        val releaseOffset = if (settling) rawOffset * (1f - settleProgress) else rawOffset
        val magneticOffset = if (dragging && activeMode != null) {
            val target = intensityV3ModeTargetVector(activeMode!!, metrics, density.density)
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
                .align(Alignment.TopCenter),
        ) {
            IntensityV3Backdrop(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeSource(hazeState)
                        } else {
                            Modifier
                        },
                    ),
                ambientColour = ambientColour,
                warmPulse = warmPulse.value,
                activeMode = activeMode,
                metrics = metrics,
            )

            IntensityV3AppBar(
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
                color = IntensityV3OnTertiaryContainer,
                fontSize = metrics.sp(57),
                lineHeight = metrics.sp(64),
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )

            IntensityV3ModeCopy(
                modifier = Modifier.offset(x = metrics.dp(47), y = metrics.dp(255)),
                metrics = metrics,
                activeMode = activeMode,
            )

            IntensityV3ModeVisuals.forEach { visual ->
                IntensityV3ModeZone(
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

            val thresholdPx = with(density) { metrics.dp(30).toPx() }
            val maxRadiusPx = with(density) { metrics.dp(166).toPx() }

            Box(
                modifier = Modifier
                    .offset(x = metrics.dp(190), y = metrics.dp(744))
                    .size(metrics.dp(72))
                    .semantics {
                        stateDescription = activeMode?.let(::intensityV3ModeTitle)
                            ?: "Drag to choose workout intensity"
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
                                rawOffset = intensityV3ClampOffset(
                                    Offset(event.x - dragOrigin.x, event.y - dragOrigin.y),
                                    maxRadiusPx,
                                )
                                val nextMode = intensityV3ResolveMode(
                                    offset = rawOffset,
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
                // The centre dock is a separate physical object. It never leaves home.
                IntensityV3HandleDock(metrics = metrics)

                // A second refractive lens is what follows the finger. It grows into the selected
                // field, matching the Figma interaction rather than dragging the dock itself.
                if (dragging || settling) {
                    IntensityV3MovingGlassLens(
                        modifier = Modifier.offset {
                            IntOffset(
                                magneticOffset.x.roundToInt(),
                                magneticOffset.y.roundToInt(),
                            )
                        },
                        metrics = metrics,
                        active = activeMode != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun IntensityV3Backdrop(
    modifier: Modifier = Modifier,
    ambientColour: Color,
    warmPulse: Float,
    activeMode: TrainingMode?,
    metrics: IntensityV3Metrics,
) {
    Canvas(modifier = modifier) {
        drawIntensityV3Background(
            ambientColour = ambientColour,
            warmPulse = warmPulse,
            activeMode = activeMode,
            metrics = metrics,
        )
    }
}

@Composable
private fun IntensityV3AppBar(
    modifier: Modifier,
    metrics: IntensityV3Metrics,
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
                color = IntensityV3OnTertiaryContainer,
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

        MettleControlGlassSurface(
            modifier = Modifier
                .offset(y = metrics.dp(-0.685))
                .width(metrics.dp(96))
                .height(metrics.dp(52)),
            shape = CircleShape,
            // Same hotbar-derived optic as the rest of the interactive glass family. Keep only
            // the page's cyan semantic bias and the capsule's own compact lift.
            tint = IntensityV3OnTertiaryContainer.copy(alpha = 0.07f),
            shadowElevation = metrics.dp(3),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntensityV3HeaderIcon(
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    metrics = metrics,
                )
                IntensityV3HeaderIcon(
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
private fun IntensityV3HeaderIcon(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    metrics: IntensityV3Metrics,
) {
    MettleGlassIconTouchTarget(
        modifier = Modifier
            .width(metrics.dp(48))
            .fillMaxHeight(),
        imageVector = imageVector,
        contentDescription = contentDescription,
        onClick = onClick,
        iconSize = DpSize(metrics.dp(16.39), metrics.dp(16.39)),
        contentAlpha = 0.82f,
        pressedHaloSize = metrics.dp(36),
    )
}

@Composable
private fun IntensityV3ModeCopy(
    modifier: Modifier,
    metrics: IntensityV3Metrics,
    activeMode: TrainingMode?,
) {
    val visual = IntensityV3ModeVisuals.firstOrNull { it.mode == activeMode }
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
        if (visual != null) {
            Text(
                modifier = Modifier.padding(top = metrics.dp(8)),
                text = visual.description,
                color = MettleOnSurface.copy(alpha = 0.92f),
                fontSize = metrics.sp(16),
                lineHeight = metrics.sp(24),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun IntensityV3ModeZone(
    modifier: Modifier,
    metrics: IntensityV3Metrics,
    visual: IntensityV3ModeVisual,
    dragging: Boolean,
    selected: Boolean,
    anotherSelected: Boolean,
) {
    val targetAlpha = when {
        selected -> 1f
        anotherSelected -> 0.34f
        dragging -> 0.72f
        else -> 0.52f
    }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(150),
        label = "intensity-v3-zone-alpha-${visual.mode.code}",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.09f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 420f),
        label = "intensity-v3-zone-scale-${visual.mode.code}",
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
                        dragging -> 6
                        else -> 2
                    },
                ),
                shape = CircleShape,
                clip = false,
                ambientColor = IntensityV3Tertiary.copy(alpha = if (selected) 0.34f else 0.12f),
                spotColor = IntensityV3Tertiary.copy(alpha = if (selected) 0.34f else 0.12f),
            )
            .drawBehind {
                val strokePx = metrics.dp(if (selected) 1.25 else 0.9).toPx()
                drawCircle(Color(0xFF849FA1).copy(alpha = if (selected) 0.25f else 0.20f))
                drawCircle(
                    color = IntensityV3OnTertiaryContainer.copy(alpha = if (selected) 0.48f else 0.32f),
                    style = Stroke(width = strokePx),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (selected) 0.24f else 0.13f),
                            IntensityV3Tertiary.copy(alpha = if (selected) 0.78f else 0.66f),
                            IntensityV3Tertiary.copy(alpha = 0.22f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * visual.glowScale,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            IntensityV3Tertiary.copy(alpha = if (selected) 0.16f else 0.09f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * (visual.glowScale + 0.18f).coerceAtMost(0.72f),
                    ),
                )
            },
    )
}

/** The fixed centre dock. It remains visible while the movable glass lens travels away. */
@Composable
private fun IntensityV3HandleDock(
    metrics: IntensityV3Metrics,
) {
    // Same home marker as the workout-exit surface: quiet fill, outer definition ring,
    // then one smaller inner ring. Deliberately no third concentric treatment.
    Canvas(modifier = Modifier.size(metrics.dp(48))) {
        drawCircle(Color.White.copy(alpha = .045f))
        drawCircle(
            MettleOnSurface.copy(alpha = .68f),
            style = Stroke(metrics.dp(.8).toPx()),
        )
        drawCircle(
            MettleOnSurface.copy(alpha = .34f),
            radius = size.minDimension * .22f,
            style = Stroke(metrics.dp(.65).toPx()),
        )
    }
}

/**
 * The actual draggable object: a nearly untinted Haze lens. It is intentionally separate from
 * the centre dock and expands to almost the diameter of a mode field once that field is engaged.
 * No blurred backing layer is used, avoiding the rectangular compositing artefact seen on device.
 */
@Composable
private fun IntensityV3MovingGlassLens(
    modifier: Modifier,
    metrics: IntensityV3Metrics,
    active: Boolean,
) {
    val lensSize by animateDpAsState(
        targetValue = metrics.dp(if (active) 112 else 50),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = 430f),
        label = "intensity-v3-lens-size",
    )

    MettleGlassSurface(
        modifier = modifier.size(lensSize),
        shape = CircleShape,
        tint = Color.White.copy(alpha = if (active) 0.026f else 0.014f),
        blurRadius = metrics.dp(if (active) 13 else 9),
        refractionDisplacement = metrics.dp(if (active) 9 else 6.5),
        refractionStrength = if (active) 0.64f else 0.52f,
        shadowElevation = metrics.dp(if (active) 5 else 2),
        borderWidth = metrics.dp(if (active) 0.42 else 0.30),
        borderColor = IntensityV3OnTertiaryContainer.copy(alpha = if (active) 0.14f else 0.08f),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val thin = metrics.dp(if (active) 0.95 else 0.78).toPx()

            // A soft off-axis specular wash; the surface remains transparent enough for the
            // refracted selector lighting to be the dominant visual information.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (active) 0.17f else 0.12f),
                        IntensityV3Tertiary.copy(alpha = if (active) 0.035f else 0.022f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.34f, size.height * 0.28f),
                    radius = size.minDimension * 0.58f,
                ),
            )

            val outerRing = size.minDimension * if (active) 0.37f else 0.31f
            val innerRing = size.minDimension * if (active) 0.28f else 0.19f

            if (active) {
                // The low-alpha broad ring gives the selected state the glass-donut volume seen
                // in the Figma prototype without introducing an opaque centre or backing tile.
                drawCircle(
                    color = IntensityV3Tertiary.copy(alpha = 0.075f),
                    radius = outerRing,
                    style = Stroke(width = metrics.dp(7).toPx()),
                )
            }
            drawCircle(
                color = Color.White.copy(alpha = if (active) 0.50f else 0.40f),
                radius = outerRing,
                style = Stroke(width = thin),
            )
            drawCircle(
                color = Color.White.copy(alpha = if (active) 0.34f else 0.28f),
                radius = innerRing,
                style = Stroke(width = thin * 0.86f),
            )
            drawCircle(
                color = IntensityV3Tertiary.copy(alpha = if (active) 0.028f else 0.018f),
                radius = innerRing * 0.82f,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIntensityV3Background(
    ambientColour: Color,
    warmPulse: Float,
    activeMode: TrainingMode?,
    metrics: IntensityV3Metrics,
) {
    drawRect(MettleBackground)
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to ambientColour,
                0.24f to lerp(ambientColour, MettleBackground, 0.16f),
                0.50f to lerp(ambientColour, MettleBackground, 0.42f),
                0.74f to lerp(ambientColour, MettleBackground, 0.68f),
                0.88f to lerp(ambientColour, MettleBackground, 0.84f),
                1f to MettleBackground,
            ),
            center = Offset(size.width / 2f, size.height * 1.045f),
            radius = size.height * 0.70f,
        ),
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                ambientColour.copy(alpha = 0.035f),
                ambientColour.copy(alpha = 0.14f),
            ),
            startY = size.height * 0.56f,
            endY = size.height,
        ),
    )

    val visual = IntensityV3ModeVisuals.firstOrNull { it.mode == activeMode }
    if (visual != null && warmPulse > 0f) {
        val centre = Offset(
            x = metrics.dp(visual.centreX).toPx(),
            y = metrics.dp(visual.centreY).toPx(),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    IntensityV3WarmSpark.copy(alpha = 0.27f * warmPulse),
                    IntensityV3WarmSpark.copy(alpha = 0.085f * warmPulse),
                    Color.Transparent,
                ),
                center = centre,
                radius = metrics.dp(150).toPx(),
            ),
            center = centre,
            radius = metrics.dp(150).toPx(),
        )
    }
}

private fun intensityV3ModeTitle(mode: TrainingMode): String =
    IntensityV3ModeVisuals.firstOrNull { it.mode == mode }?.title ?: mode.label

private fun intensityV3ModeTargetVector(
    mode: TrainingMode,
    metrics: IntensityV3Metrics,
    density: Float,
): Offset {
    val visual = IntensityV3ModeVisuals.first { it.mode == mode }
    val handleX = 226f
    val handleY = 780f
    return Offset(
        x = metrics.dp(visual.centreX - handleX).value * density,
        y = metrics.dp(visual.centreY - handleY).value * density,
    )
}

private fun intensityV3ResolveMode(
    offset: Offset,
    current: TrainingMode?,
    thresholdPx: Float,
    metrics: IntensityV3Metrics,
    density: Float,
): TrainingMode? {
    val magnitude = hypot(offset.x, offset.y)
    if (magnitude < thresholdPx) return null

    val scores = IntensityV3ModeVisuals.associate { visual ->
        val target = intensityV3ModeTargetVector(visual.mode, metrics, density)
        visual.mode to intensityV3DirectionalScore(offset, target)
    }
    val best = scores.maxByOrNull { it.value } ?: return null
    if (best.value < 0.60f) return null

    if (current != null && current != best.key) {
        val currentScore = scores[current] ?: -1f
        if (currentScore >= 0.43f && best.value < currentScore + 0.14f) return current
    }
    return best.key
}

private fun intensityV3DirectionalScore(value: Offset, target: Offset): Float {
    val valueLength = hypot(value.x, value.y).coerceAtLeast(0.001f)
    val targetLength = hypot(target.x, target.y).coerceAtLeast(0.001f)
    return ((value.x * target.x) + (value.y * target.y)) / (valueLength * targetLength)
}

private fun intensityV3ClampOffset(value: Offset, maxRadius: Float): Offset {
    val distance = hypot(value.x, value.y)
    if (distance <= maxRadius || distance == 0f) return value
    val scale = maxRadius / distance
    return value * scale
}
