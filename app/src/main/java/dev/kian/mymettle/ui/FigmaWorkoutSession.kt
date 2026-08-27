package dev.kian.mymettle.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import dev.kian.mymettle.workout.PerformanceSetRecord
import kotlin.math.sin

private const val WorkoutTransientReferenceWidth = 453f
private val WorkoutTransientInk = Color(0xFF11140F)
private val WorkoutTransientCard = Color(0xA81A3A37)
private val WorkoutTransientPaper = Color(0xFFE1E4DA)
private val WorkoutTransientMuted = Color(0xFFC3C8BB)
private val WorkoutTransientCyan = Color(0xFFBBEBED)
private val WorkoutTransientGreen = Color(0xFFC3EFAD)
private val WorkoutTransientGreenDark = Color(0xFF1E3B19)
private val WorkoutTransientCardShape = RoundedCornerShape(25.dp)

private data class WorkoutTransientMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

/**
 * Compatibility renderer for the two alpha23 transient workout surfaces still used by the
 * N-BIO-6 workout renderer. SETS, SETUP and finish behaviour live exclusively in
 * FigmaWorkoutSessionV2; keeping those duplicate implementations here previously left two almost
 * complete workout UIs in the production source set.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun FigmaWorkoutSession(
    state: N2WorkoutUiState,
    drafts: MutableMap<String, TrainSetDraft>,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, PerformanceSetRecord, PerformanceMetric) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, PerformanceSetRecord, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, PerformanceSetRecord, TrainSetDraft) -> Unit,
    onSwapExercise: (ActiveWorkoutExercise) -> Unit,
    onSelectSwap: (ExerciseSwapOption) -> Unit,
    onDismissSwap: () -> Unit,
    onShowSets: (String?) -> Unit,
    onShowSetup: () -> Unit,
    onAddSetupPhoto: (ActiveWorkoutExercise) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    onDismissSheet: () -> Unit,
    onShowDelete: () -> Unit,
    onCompleteSession: () -> Unit,
    onCompleteWithoutReview: () -> Unit,
    onDiscardSession: () -> Unit,
) {
    val workout = requireNotNull(state.workout)
    val viewportHazeState = rememberHazeState()
    val headerHazeState = rememberHazeState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth = minOf(maxWidth, WorkoutTransientReferenceWidth.dp)
        val metrics = WorkoutTransientMetrics(
            (viewportWidth.value / WorkoutTransientReferenceWidth).coerceAtMost(1f),
        )

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .background(WorkoutTransientInk),
        ) {
            Box(Modifier.fillMaxSize().hazeSource(headerHazeState)) {
                WorkoutTransientBackdrop(
                    modifier = Modifier.fillMaxSize().hazeSource(viewportHazeState),
                )
                CompositionLocalProvider(LocalMettleHazeState provides viewportHazeState) {
                    when {
                        state.swapTarget != null -> WorkoutTransientSubstitutionContent(
                            current = state.swapTarget,
                            options = state.swapOptions,
                            loading = state.loadingSwapOptions,
                            onDismiss = onDismissSwap,
                            onSelect = onSelectSwap,
                            metrics = metrics,
                        )

                        state.workoutSurface == WorkoutSurface.QUICK_SELECT -> WorkoutTransientQuickSelectContent(
                            workout = workout,
                            onSelect = onShowSets,
                            metrics = metrics,
                        )

                        else -> error("Transient workout renderer invoked outside Quick Select/substitution.")
                    }
                }
                WorkoutTransientViewportScrims(metrics)
            }

            CompositionLocalProvider(LocalMettleHazeState provides headerHazeState) {
                WorkoutTransientHeader(
                    workout = workout,
                    metrics = metrics,
                    onOpenSettings = onOpenSettings,
                    onOpenAccount = onOpenAccount,
                )
            }
        }
    }
}

@Composable
private fun WorkoutTransientBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(WorkoutTransientInk)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFF5E7A7A), Color(0xFF384745), Color.Transparent),
                center = Offset(size.width * .5f, size.height * 1.08f),
                radius = size.height * .86f,
            ),
            alpha = .86f,
        )
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = .42f), Color.Transparent, Color.Transparent),
                endY = size.height * .30f,
            ),
        )
    }
}

@Composable
private fun WorkoutTransientHeader(
    workout: ActiveWorkout,
    metrics: WorkoutTransientMetrics,
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
        Column(modifier = Modifier.width(metrics.dp(125))) {
            Text(
                "My Mettle",
                color = WorkoutTransientCyan,
                fontSize = metrics.sp(24.2),
                lineHeight = metrics.sp(31),
            )
            Text(
                "Workout Session",
                color = WorkoutTransientMuted,
                fontSize = metrics.sp(13.2),
                lineHeight = metrics.sp(18),
            )
        }
        WorkoutTransientWaveProgress(
            workout = workout,
            modifier = Modifier.weight(1f).height(metrics.dp(52)),
            metrics = metrics,
        )
        Spacer(Modifier.width(metrics.dp(18)))
        MettleControlGlassSurface(
            modifier = Modifier.width(metrics.dp(81)).height(metrics.dp(52)),
            shape = CircleShape,
            tint = Color.White.copy(alpha = .025f),
            shadowElevation = metrics.dp(3),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Workout settings",
                    onClick = onOpenSettings,
                    iconSize = DpSize(metrics.dp(16.3916), metrics.dp(16.3916)),
                )
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                    iconSize = DpSize(metrics.dp(16.3916), metrics.dp(16.3916)),
                )
            }
        }
    }
}

@Composable
private fun WorkoutTransientWaveProgress(
    workout: ActiveWorkout,
    modifier: Modifier,
    metrics: WorkoutTransientMetrics,
) {
    val visible = workout.exercises.filter { it.entity.prescriptionIncluded }
    val completed = visible.count { it.entity.status == "completed" }
    val progress = if (visible.isEmpty()) 0f else completed.toFloat() / visible.size
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = .84f, stiffness = 280f),
        label = "workout-transient-progress",
    )

    MettleControlGlassSurface(
        modifier = modifier,
        shape = CircleShape,
        tint = Color.White.copy(alpha = .018f),
        shadowElevation = metrics.dp(3),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = metrics.dp(10), vertical = metrics.dp(11))) {
            val y = size.height / 2f
            val endX = size.width * animatedProgress.coerceIn(0f, 1f)
            drawLine(
                WorkoutTransientPaper.copy(alpha = .52f),
                Offset(endX, y),
                Offset(size.width, y),
                metrics.dp(2.1).toPx(),
            )
            if (endX > 0f) {
                val amplitude = metrics.dp(2.5).toPx()
                val wavelength = metrics.dp(18).toPx().coerceAtLeast(1f)
                val step = metrics.dp(1.5).toPx().coerceAtLeast(1f)
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = step
                    while (x < endX) {
                        lineTo(x, y + sin((x / wavelength) * Math.PI * 2.0).toFloat() * amplitude)
                        x += step
                    }
                    lineTo(endX, y)
                }
                drawPath(path, WorkoutTransientCyan, style = Stroke(metrics.dp(2.1).toPx()))
                drawLine(
                    WorkoutTransientPaper,
                    Offset(endX, y - metrics.dp(9).toPx()),
                    Offset(endX, y + metrics.dp(9).toPx()),
                    metrics.dp(2.2).toPx(),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WorkoutTransientViewportScrims(metrics: WorkoutTransientMetrics) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(132))
            .align(Alignment.TopCenter)
            .background(
                Brush.verticalGradient(
                    0f to WorkoutTransientInk.copy(alpha = .93f),
                    .65f to WorkoutTransientInk.copy(alpha = .70f),
                    1f to Color.Transparent,
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(142))
            .align(Alignment.BottomCenter)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .55f to WorkoutTransientInk.copy(alpha = .48f),
                    1f to WorkoutTransientInk.copy(alpha = .88f),
                ),
            ),
    )
}

@Composable
private fun WorkoutTransientQuickSelectContent(
    workout: ActiveWorkout,
    onSelect: (String?) -> Unit,
    metrics: WorkoutTransientMetrics,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.dp(17),
            end = metrics.dp(18),
            top = metrics.dp(129),
            bottom = metrics.dp(145),
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        items(
            items = workout.exercises,
            key = { it.entity.id },
            contentType = { "quick-select-exercise" },
        ) { exercise ->
            val done = exercise.entity.status == "completed"
            val cardHazeState = rememberHazeState()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(114))
                    .hazeSource(cardHazeState)
                    .clickable { onSelect(exercise.entity.id) },
                shape = WorkoutTransientCardShape,
                color = if (done) WorkoutTransientGreenDark.copy(alpha = .90f) else WorkoutTransientCard,
                border = BorderStroke(metrics.dp(.6), Color.White.copy(alpha = .10f)),
                shadowElevation = metrics.dp(2),
            ) {
                CompositionLocalProvider(LocalMettleHazeState provides cardHazeState) {
                    Row(
                        Modifier.padding(start = metrics.dp(16), end = metrics.dp(18)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            exercise.entity.exerciseNameSnapshot,
                            modifier = Modifier.weight(1f),
                            color = WorkoutTransientPaper,
                            fontSize = metrics.sp(22),
                            lineHeight = metrics.sp(28),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Box(contentAlignment = Alignment.TopEnd) {
                            MettleControlGlassSurface(
                                modifier = Modifier.size(metrics.dp(80)),
                                shape = CircleShape,
                                tint = if (done) {
                                    WorkoutTransientGreen.copy(alpha = .11f)
                                } else {
                                    WorkoutTransientCyan.copy(alpha = .07f)
                                },
                                borderColor = if (done) {
                                    WorkoutTransientGreen.copy(alpha = .48f)
                                } else {
                                    WorkoutTransientCyan.copy(alpha = .38f)
                                },
                                shadowElevation = metrics.dp(4),
                                onClick = { onSelect(exercise.entity.id) },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (done) MettleIcons.Check else MettleIcons.ArrowForward,
                                        contentDescription = if (done) "Open completed exercise" else "Open exercise",
                                        tint = if (done) WorkoutTransientGreen else WorkoutTransientPaper,
                                        modifier = Modifier.size(metrics.dp(30)),
                                    )
                                }
                            }
                            Text(
                                "${exercise.entity.position + 1}",
                                color = WorkoutTransientMuted,
                                fontSize = metrics.sp(11),
                                modifier = Modifier.padding(end = metrics.dp(1), top = metrics.dp(1)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutTransientSubstitutionContent(
    current: ActiveWorkoutExercise,
    options: List<ExerciseSwapOption>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ExerciseSwapOption) -> Unit,
    metrics: WorkoutTransientMetrics,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(options, query) {
        options.filter {
            query.isBlank() ||
                it.exerciseName.contains(query, ignoreCase = true) ||
                it.executionProfileName.contains(query, ignoreCase = true)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.dp(17),
            end = metrics.dp(18),
            top = metrics.dp(120),
            bottom = metrics.dp(145),
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        item(contentType = "search") {
            MettleExerciseSearchField(
                value = query,
                onValueChange = { query = it },
                height = metrics.dp(64),
                foreground = WorkoutTransientPaper,
                muted = WorkoutTransientMuted,
                accent = WorkoutTransientCyan,
            )
        }
        if (loading) {
            item(contentType = "loading") {
                Box(
                    Modifier.fillMaxWidth().padding(metrics.dp(30)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = WorkoutTransientCyan)
                }
            }
        } else {
            items(
                items = filtered,
                key = { it.executionProfileId },
                contentType = { "substitution-option" },
            ) { option ->
                val cardHazeState = rememberHazeState()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.dp(114))
                        .hazeSource(cardHazeState)
                        .clickable { onSelect(option) },
                    shape = WorkoutTransientCardShape,
                    color = WorkoutTransientCard,
                    border = BorderStroke(metrics.dp(.6), Color.White.copy(alpha = .10f)),
                ) {
                    CompositionLocalProvider(LocalMettleHazeState provides cardHazeState) {
                        Row(
                            Modifier.padding(start = metrics.dp(16), end = metrics.dp(18)),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    option.exerciseName,
                                    color = WorkoutTransientPaper,
                                    fontSize = metrics.sp(22),
                                    lineHeight = metrics.sp(28),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    option.executionProfileName,
                                    color = WorkoutTransientMuted,
                                    fontSize = metrics.sp(13),
                                    maxLines = 1,
                                )
                            }
                            MettleControlGlassSurface(
                                modifier = Modifier.size(metrics.dp(80)),
                                shape = CircleShape,
                                tint = WorkoutTransientCyan.copy(alpha = .07f),
                                borderColor = WorkoutTransientCyan.copy(alpha = .38f),
                                shadowElevation = metrics.dp(4),
                                onClick = { onSelect(option) },
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = MettleIcons.SwapHoriz,
                                        contentDescription = "Use ${option.exerciseName}",
                                        tint = WorkoutTransientPaper,
                                        modifier = Modifier.size(metrics.dp(30)),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item(contentType = "empty") {
                    Text(
                        "No target-compatible replacements match that search.",
                        color = WorkoutTransientMuted,
                        modifier = Modifier.padding(metrics.dp(20)),
                    )
                }
            }
        }
        item(contentType = "return") {
            WorkoutTransientCardButton(
                text = "Return to ${current.entity.exerciseNameSnapshot}",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                metrics = metrics,
            )
        }
    }
}

@Composable
private fun WorkoutTransientCardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    metrics: WorkoutTransientMetrics,
) {
    MettleGlassActionButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = metrics.dp(48)),
        shadowElevation = metrics.dp(2.6),
        accent = false,
        containerTint = WorkoutTransientCyan.copy(alpha = .055f),
        outlineColor = WorkoutTransientCyan.copy(alpha = .24f),
        foregroundColor = WorkoutTransientPaper,
        contentPadding = PaddingValues(horizontal = metrics.dp(13), vertical = metrics.dp(9)),
    ) {
        Text(
            text = text,
            fontSize = metrics.sp(14.5),
            lineHeight = metrics.sp(20),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
