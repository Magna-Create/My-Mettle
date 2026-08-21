package dev.kian.mymettle.ui

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WorkoutReferenceWidth = 453f
private val WorkoutInk = Color(0xFF11140F)
private val WorkoutSurfaceLow = Color(0xFF191D17)
private val WorkoutCard = Color(0xA81A3A37)
private val WorkoutCardQuiet = Color(0x9923423F)
private val WorkoutPaper = Color(0xFFE1E4DA)
private val WorkoutPaperMuted = Color(0xFFC3C8BB)
private val WorkoutCyan = Color(0xFFBBEBED)
private val WorkoutCyanStrong = Color(0xFFA0CFD0)
private val WorkoutDarkCyan = Color(0xFF002021)
private val WorkoutGreen = Color(0xFFC3EFAD)
private val WorkoutGreenDark = Color(0xFF1E3B19)
private val WorkoutDelete = Color(0xFFFFB4AB)
private val WorkoutCardShape = RoundedCornerShape(25.dp)

private data class WorkoutMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FigmaWorkoutSession(
    state: N2WorkoutUiState,
    drafts: MutableMap<String, TrainSetDraft>,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, SetRecordEntity) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
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
    val focusManager = LocalFocusManager.current
    val focused = workout.exercises.firstOrNull { it.entity.id == state.focusedExerciseId }
        ?: workout.exercises.firstOrNull { it.entity.status != "completed" }
        ?: workout.exercises.first()
    val listState = rememberLazyListState()
    val viewportHazeState = rememberHazeState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth = minOf(maxWidth, WorkoutReferenceWidth.dp)
        val metrics = WorkoutMetrics((viewportWidth.value / WorkoutReferenceWidth).coerceAtMost(1f))

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .background(WorkoutInk),
        ) {
            Box(Modifier.fillMaxSize()) {
                WorkoutBackdrop(
                    modifier = Modifier.fillMaxSize().hazeSource(viewportHazeState),
                    onTap = { focusManager.clearFocus(force = true) },
                )

                when {
                    state.swapTarget != null -> WorkoutSubstitutionContent(
                        current = state.swapTarget,
                        options = state.swapOptions,
                        loading = state.loadingSwapOptions,
                        onDismiss = onDismissSwap,
                        onSelect = onSelectSwap,
                        metrics = metrics,
                    )

                    state.workoutSurface == WorkoutSurface.QUICK_SELECT -> WorkoutQuickSelectContent(
                        workout = workout,
                        onSelect = onShowSets,
                        metrics = metrics,
                    )

                    else -> WorkoutExerciseContent(
                        workout = workout,
                        focusedId = focused.entity.id,
                        setupExerciseId = focused.entity.id.takeIf { state.workoutSurface == WorkoutSurface.SETUP },
                        drafts = drafts,
                        loading = state.loading,
                        listState = listState,
                        onFocusExercise = onShowSets,
                        onShowSetup = onShowSetup,
                        onAddSetupPhoto = onAddSetupPhoto,
                        onOpenCalculator = onOpenCalculator,
                        onSaveDraft = onSaveDraft,
                        onLogSet = onLogSet,
                        onSwap = onSwapExercise,
                        onToggleExercise = onToggleExercise,
                        onRateExercise = onRateExercise,
                        metrics = metrics,
                    )
                }
            }

            CompositionLocalProvider(LocalMettleHazeState provides viewportHazeState) {
                WorkoutViewportScrims(metrics)
                WorkoutHeader(
                    workout = workout,
                    metrics = metrics,
                    onOpenSettings = onOpenSettings,
                    onOpenAccount = onOpenAccount,
                )

                if (state.workoutSurface == WorkoutSurface.FINISH) {
                    FinishWorkoutGestureOverlay(
                        destructive = false,
                        onDismiss = onDismissSheet,
                        onComplete = onCompleteSession,
                        onCompleteWithoutReview = onCompleteWithoutReview,
                        onDelete = onShowDelete,
                    )
                }
                if (state.workoutSurface == WorkoutSurface.DELETE_CONFIRM) {
                    FinishWorkoutGestureOverlay(
                        destructive = true,
                        onDismiss = onDismissSheet,
                        onComplete = onCompleteSession,
                        onCompleteWithoutReview = onCompleteWithoutReview,
                        onDelete = onDiscardSession,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutBackdrop(modifier: Modifier = Modifier, onTap: () -> Unit) {
    Canvas(
        modifier.clickable(onClick = onTap),
    ) {
        drawRect(WorkoutInk)
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
private fun WorkoutExerciseContent(
    workout: ActiveWorkout,
    focusedId: String,
    setupExerciseId: String?,
    drafts: MutableMap<String, TrainSetDraft>,
    loading: Boolean,
    listState: LazyListState,
    onFocusExercise: (String?) -> Unit,
    onShowSetup: () -> Unit,
    onAddSetupPhoto: (ActiveWorkoutExercise) -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, SetRecordEntity) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onSwap: (ActiveWorkoutExercise) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    metrics: WorkoutMetrics,
) {
    val visible = workout.exercises.filter {
        it.entity.prescriptionIncluded || it.entity.status == "completed" || it.sets.any { set -> set.completedAt != null }
    }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(focusedId, setupExerciseId, visible.map { it.entity.id }) {
        val index = visible.indexOfFirst { it.entity.id == focusedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.dp(17),
            end = metrics.dp(18),
            top = metrics.dp(129),
            bottom = metrics.dp(145),
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        items(visible, key = { it.entity.id }) { exercise ->
            WorkoutExerciseCard(
                exercise = exercise,
                focused = exercise.entity.id == focusedId,
                showSetup = exercise.entity.id == setupExerciseId,
                drafts = drafts,
                enabled = workout.session.status == "active" && !loading,
                onFocus = {
                    focusManager.clearFocus(force = true)
                    onFocusExercise(exercise.entity.id)
                },
                onSetup = {
                    focusManager.clearFocus(force = true)
                    onFocusExercise(exercise.entity.id)
                    onShowSetup()
                },
                onAddSetupPhoto = { onAddSetupPhoto(exercise) },
                onOpenCalculator = { set -> onOpenCalculator(exercise, set) },
                onSaveDraft = { set, draft -> onSaveDraft(exercise, set, draft) },
                onLogSet = { set, draft -> onLogSet(exercise, set, draft) },
                onSwap = { onSwap(exercise) },
                onToggleExercise = { onToggleExercise(exercise) },
                onRateExercise = { onRateExercise(exercise) },
                metrics = metrics,
            )
        }
    }
}

@Composable
private fun WorkoutHeader(
    workout: ActiveWorkout,
    metrics: WorkoutMetrics,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(metrics.dp(70))
            .padding(horizontal = metrics.dp(20)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.width(metrics.dp(125))) {
            Text("My Mettle", color = WorkoutCyan, fontSize = metrics.sp(24), lineHeight = metrics.sp(27))
            Text("Workout Session", color = WorkoutPaperMuted, fontSize = metrics.sp(12), lineHeight = metrics.sp(16))
        }
        WorkoutWaveProgress(workout, Modifier.weight(1f).height(metrics.dp(49)), metrics)
        Spacer(Modifier.width(metrics.dp(18)))
        MettleControlGlassSurface(
            modifier = Modifier.width(metrics.dp(81)).height(metrics.dp(49)),
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
                    iconSize = DpSize(metrics.dp(17), metrics.dp(16)),
                )
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                    iconSize = DpSize(metrics.dp(16), metrics.dp(16)),
                )
            }
        }
    }
}

@Composable
private fun WorkoutWaveProgress(workout: ActiveWorkout, modifier: Modifier, metrics: WorkoutMetrics) {
    val visible = workout.exercises.filter { it.entity.prescriptionIncluded }
    val completed = visible.count { it.entity.status == "completed" }
    val progress = if (visible.isEmpty()) 0f else completed.toFloat() / visible.size
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = .84f, stiffness = 280f),
        label = "workout-progress",
    )
    MettleControlGlassSurface(
        modifier = modifier,
        shape = CircleShape,
        tint = Color.White.copy(alpha = .018f),
        shadowElevation = metrics.dp(3),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = metrics.dp(10)),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(metrics.dp(31))) {
                val centreY = size.height / 2f
                val endX = size.width * animatedProgress.coerceIn(0f, 1f)
                val trackStroke = metrics.dp(2.1).toPx()

                drawLine(
                    color = WorkoutPaper.copy(alpha = .52f),
                    start = Offset(endX, centreY),
                    end = Offset(size.width, centreY),
                    strokeWidth = trackStroke,
                )

                if (endX > 0f) {
                    val amplitude = metrics.dp(2.5).toPx()
                    val wavelength = metrics.dp(18).toPx().coerceAtLeast(1f)
                    val step = metrics.dp(1.5).toPx().coerceAtLeast(1f)
                    val wave = Path().apply {
                        moveTo(0f, centreY)
                        var x = step
                        while (x < endX) {
                            lineTo(x, centreY + sin((x / wavelength) * Math.PI * 2.0).toFloat() * amplitude)
                            x += step
                        }
                        lineTo(endX, centreY)
                    }

                    drawPath(
                        path = wave,
                        color = WorkoutCyan.copy(alpha = .07f),
                        style = Stroke(width = metrics.dp(13).toPx()),
                    )
                    drawPath(
                        path = wave,
                        color = WorkoutCyan.copy(alpha = .18f),
                        style = Stroke(width = metrics.dp(6).toPx()),
                    )
                    drawPath(
                        path = wave,
                        color = WorkoutCyan,
                        style = Stroke(width = trackStroke),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(WorkoutCyan.copy(alpha = .22f), Color.Transparent),
                            center = Offset(endX, centreY),
                            radius = metrics.dp(19).toPx(),
                        ),
                        radius = metrics.dp(19).toPx(),
                        center = Offset(endX, centreY),
                    )
                    drawLine(
                        color = WorkoutPaper,
                        start = Offset(endX, centreY - metrics.dp(9).toPx()),
                        end = Offset(endX, centreY + metrics.dp(9).toPx()),
                        strokeWidth = metrics.dp(2.2).toPx(),
                    )
                }
            }
        }
    }
}

@Composable
private fun BoxScope.WorkoutViewportScrims(metrics: WorkoutMetrics) {
    // These are deliberately not glass. A continuous fog curve plus subtle dithering approximates
    // the progressive obscuration in the mock-up without refraction, edge highlights or a second
    // live Haze render pass over the scrolling workout.
    ProgressiveFogScrim(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(132))
            .align(Alignment.TopCenter),
        edgeAtTop = true,
        edgeAlpha = .76f,
    )

    ProgressiveFogScrim(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(142))
            .align(Alignment.BottomCenter),
        edgeAtTop = false,
        edgeAlpha = .58f,
    )
}

@Composable
private fun ProgressiveFogScrim(
    modifier: Modifier,
    edgeAtTop: Boolean,
    edgeAlpha: Float,
) {
    Box(
        modifier.drawWithCache {
            val colours = if (edgeAtTop) {
                listOf(
                    WorkoutInk.copy(alpha = edgeAlpha),
                    WorkoutInk.copy(alpha = edgeAlpha * .72f),
                    WorkoutInk.copy(alpha = edgeAlpha * .34f),
                    Color.Transparent,
                )
            } else {
                listOf(
                    Color.Transparent,
                    WorkoutInk.copy(alpha = edgeAlpha * .25f),
                    WorkoutInk.copy(alpha = edgeAlpha * .66f),
                    WorkoutInk.copy(alpha = edgeAlpha),
                )
            }
            val fog = Brush.verticalGradient(
                0f to colours[0],
                .34f to colours[1],
                .70f to colours[2],
                1f to colours[3],
            )
            val ditherStep = 7.dp.toPx().coerceAtLeast(1f)
            val lightPoints = ArrayList<Offset>()
            val darkPoints = ArrayList<Offset>()
            var y = ditherStep * .5f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) ditherStep * .35f else ditherStep * .8f
                while (x < size.width) {
                    val point = Offset(x, y)
                    if ((((x.toInt() * 31) xor (y.toInt() * 17)) and 1) == 0) {
                        lightPoints += point
                    } else {
                        darkPoints += point
                    }
                    x += ditherStep
                }
                y += ditherStep
                row += 1
            }
            val strokeWidth = 1.1.dp.toPx()
            onDrawBehind {
                drawRect(fog)
                drawPoints(lightPoints, PointMode.Points, Color.White.copy(alpha = .005f), strokeWidth)
                drawPoints(darkPoints, PointMode.Points, Color.Black.copy(alpha = .007f), strokeWidth)
            }
        },
    )
}

@Composable
private fun WorkoutExerciseCard(
    exercise: ActiveWorkoutExercise,
    focused: Boolean,
    showSetup: Boolean,
    drafts: MutableMap<String, TrainSetDraft>,
    enabled: Boolean,
    onFocus: () -> Unit,
    onSetup: () -> Unit,
    onAddSetupPhoto: () -> Unit,
    onOpenCalculator: (SetRecordEntity) -> Unit,
    onSaveDraft: (SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (SetRecordEntity, TrainSetDraft) -> Unit,
    onSwap: () -> Unit,
    onToggleExercise: () -> Unit,
    onRateExercise: () -> Unit,
    metrics: WorkoutMetrics,
) {
    val entity = exercise.entity
    val completed = entity.status == "completed"
    val sets = exercise.sets
        .filter { it.setIndex < entity.prescribedSets || it.completedAt != null }
        .sortedBy { it.setIndex }
    val logged = sets.count { it.completedAt != null }
    val cardHazeState = rememberHazeState()

    Surface(
        modifier = Modifier.fillMaxWidth().hazeSource(cardHazeState),
        shape = WorkoutCardShape,
        color = if (completed) WorkoutGreenDark.copy(alpha = .72f) else WorkoutCard,
        border = null,
        shadowElevation = if (focused) metrics.dp(4) else metrics.dp(2),
    ) {
        CompositionLocalProvider(LocalMettleHazeState provides cardHazeState) {
            Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(114))
                    .clickable(onClick = onFocus)
                    .padding(start = metrics.dp(16), end = metrics.dp(18)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        entity.exerciseNameSnapshot,
                        color = WorkoutPaper,
                        fontSize = metrics.sp(26),
                        lineHeight = metrics.sp(32),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(metrics.dp(12)))
                MettleControlGlassSurface(
                    modifier = Modifier.size(metrics.dp(80)),
                    shape = CircleShape,
                    tint = if (completed) WorkoutGreen.copy(alpha = .10f) else WorkoutCyan.copy(alpha = .055f),
                    shadowElevation = metrics.dp(4),
                    borderColor = if (completed) WorkoutGreen.copy(alpha = .50f) else WorkoutCyan.copy(alpha = .38f),
                    onClick = if (completed) null else onSetup,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (completed) MettleIcons.Check else MettleIcons.SportsMartialArts,
                            contentDescription = if (completed) "Exercise completed" else "Open exercise setup",
                            tint = if (completed) WorkoutGreen else WorkoutCyan,
                            modifier = Modifier.size(metrics.dp(if (completed) 32 else 29)),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = metrics.dp(16)),
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(8)),
            ) {
                if (completed) {
                    WorkoutChip("$logged/${entity.prescribedSets} Sets Complete", metrics, success = true)
                    entity.prescribedLoad?.let { WorkoutChip("${trimNumber(it)} load progression", metrics) }
                } else {
                    WorkoutChip(entity.importanceSnapshot.replaceFirstChar { it.uppercase() }, metrics)
                    WorkoutChip("${entity.repMin}–${entity.repMax} reps", metrics)
                    WorkoutChip("${entity.restSeconds}s rest", metrics)
                    entity.prescribedLoad?.let { WorkoutChip("${trimNumber(it)} load progression", metrics) }
                }
            }
            Spacer(Modifier.height(metrics.dp(14)))
            Box(Modifier.fillMaxWidth().height(metrics.dp(1)).background(Color.White.copy(alpha = .10f)))

            when {
                completed -> CompletedExerciseActions(onRateExercise, onToggleExercise, metrics)
                showSetup -> WorkoutSetupBody(
                    exercise = exercise,
                    onAddPhoto = onAddSetupPhoto,
                    onReturn = onFocus,
                    metrics = metrics,
                )
                else -> {
                    Column(Modifier.padding(top = metrics.dp(15))) {
                        sets.forEachIndexed { index, set ->
                            val draft = drafts.getOrPut(set.id) { TrainSetDraft(set) }
                            val setShape = workoutSetShape(index, sets.lastIndex, metrics)
                            WorkoutSetRow(
                                displayIndex = index,
                                exercise = exercise,
                                set = set,
                                draft = draft,
                                enabled = enabled,
                                isCurrent = focused && set.completedAt == null && sets.take(index).all { it.completedAt != null },
                                shape = setShape,
                                onOpenCalculator = { onOpenCalculator(set) },
                                onSaveDraft = { onSaveDraft(set, draft) },
                                onLogSet = { onLogSet(set, draft) },
                                metrics = metrics,
                            )
                            if (index != sets.lastIndex) Spacer(Modifier.height(metrics.dp(1.5)))
                        }
                    }
                    WorkoutExerciseActions(
                        onSwap = onSwap,
                        onRate = onRateExercise,
                        onComplete = onToggleExercise,
                        allSetsComplete = sets.isNotEmpty() && sets.all { it.completedAt != null },
                        metrics = metrics,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun WorkoutSetRow(
    displayIndex: Int,
    exercise: ActiveWorkoutExercise,
    set: SetRecordEntity,
    draft: TrainSetDraft,
    enabled: Boolean,
    isCurrent: Boolean,
    shape: RoundedCornerShape,
    onOpenCalculator: () -> Unit,
    onSaveDraft: () -> Unit,
    onLogSet: () -> Unit,
    metrics: WorkoutMetrics,
) {
    val focusManager = LocalFocusManager.current
    val profile = exercise.entity.executionProfileNameSnapshot.lowercase()
    val unilateral = "unilateral" in profile || "single" in profile
    val label = if (unilateral) "${displayIndex / 2 + 1} · ${if (displayIndex % 2 == 0) "R" else "L"}" else "${displayIndex + 1}"
    val metric = exercise.entity.trackingMetricSnapshot
    val fieldOne = when (metric) {
        "duration" -> draft.durationSeconds
        "distance" -> draft.distanceMetres
        else -> draft.load
    }
    val fieldOneLabel = when (metric) {
        "duration" -> "Seconds"
        "distance" -> "Metres"
        else -> set.unit.ifBlank { "Load" }
    }
    val needsReps = metric == "load_reps" || metric == "reps"
    val ready = when (metric) {
        "duration" -> draft.durationSeconds.toIntOrNull()?.let { it > 0 } == true
        "distance" -> draft.distanceMetres.toDoubleOrNull()?.let { it > 0 } == true
        "reps" -> draft.reps.toIntOrNull()?.let { it > 0 } == true
        else -> draft.reps.toIntOrNull()?.let { it > 0 } == true &&
            (exercise.entity.loadRelationshipSnapshot == "bodyweight" || draft.load.toDoubleOrNull() != null)
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(132))
            .clip(shape),
    ) {
        if (isCurrent) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(metrics.dp(16)),
            ) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            WorkoutCyanStrong.copy(alpha = .20f),
                            WorkoutCyan.copy(alpha = .07f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * .12f, size.height * .52f),
                        radius = size.width * .34f,
                    ),
                )
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            border = BorderStroke(metrics.dp(.3), Color.White.copy(alpha = .065f)),
            shape = shape,
        ) {
            Row {
            Box(
                modifier = Modifier
                    .width(metrics.dp(94))
                    .fillMaxHeight()
                    .background(
                        when {
                            set.completedAt != null -> WorkoutGreen.copy(alpha = .13f)
                            isCurrent -> WorkoutCyan.copy(alpha = .065f)
                            else -> Color.White.copy(alpha = .035f)
                        },
                    )
                    .clickable(enabled = enabled && ready) {
                        focusManager.clearFocus(force = true)
                        onLogSet()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (set.completedAt != null) WorkoutGreen else WorkoutPaper,
                    fontSize = metrics.sp(24),
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(metrics.dp(9)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(8)),
            ) {
                if (metric != "reps") {
                    WorkoutMetricField(
                        value = fieldOne,
                        label = fieldOneLabel,
                        enabled = enabled,
                        decimal = metric != "duration",
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onValueChange = { value ->
                            when (metric) {
                                "duration" -> draft.durationSeconds = value.filter(Char::isDigit).take(5)
                                "distance" -> draft.distanceMetres = workoutDecimalInput(value)
                                else -> draft.load = workoutDecimalInput(value)
                            }
                        },
                        onDone = onSaveDraft,
                        onCalculator = onOpenCalculator.takeIf { metric == "load_reps" },
                    )
                }
                if (needsReps) {
                    WorkoutMetricField(
                        value = draft.reps,
                        label = "Reps",
                        enabled = enabled,
                        decimal = false,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onValueChange = {
                            draft.reps = it.filter(Char::isDigit).take(3)
                        },
                        onDone = {
                            focusManager.clearFocus(force = true)
                            if (ready) onLogSet() else onSaveDraft()
                        },
                        onFocusLost = {
                            if (ready) onLogSet() else onSaveDraft()
                        },
                    )
                }
            }
        }
        }
    }
}

private fun workoutSetShape(index: Int, lastIndex: Int, metrics: WorkoutMetrics): RoundedCornerShape {
    val outer = metrics.dp(25)
    val inner = metrics.dp(5)
    return when {
        lastIndex == 0 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomEnd = inner,
            bottomStart = inner,
        )
        index == lastIndex -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomEnd = outer,
            bottomStart = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun WorkoutMetricField(
    value: String,
    label: String,
    enabled: Boolean,
    decimal: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    onFocusLost: () -> Unit = onDone,
    onCalculator: (() -> Unit)? = null,
) {
    var hadFocus by remember { mutableStateOf(false) }
    MettleControlGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        tint = WorkoutCyan.copy(alpha = .018f),
        baseColor = WorkoutDarkCyan.copy(alpha = .72f),
        borderWidth = .3.dp,
        borderColor = WorkoutCyan.copy(alpha = .07f),
        shadowElevation = .5.dp,
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onFocusChanged { focus ->
                        if (focus.isFocused) hadFocus = true
                        if (!focus.isFocused && hadFocus) {
                            hadFocus = false
                            onFocusLost()
                        }
                    },
                singleLine = true,
                textStyle = TextStyle(color = WorkoutPaper, fontSize = 16.sp, lineHeight = 18.sp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    hadFocus = false
                    onDone()
                }),
                decorationBox = { inner ->
                    Column(
                        Modifier.fillMaxSize().padding(start = 13.dp, end = if (onCalculator == null) 13.dp else 4.dp, top = 5.dp, bottom = 5.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(label, color = WorkoutCyan.copy(alpha = .78f), fontSize = 10.sp, lineHeight = 11.sp)
                        if (value.isEmpty()) {
                            Text("00", color = WorkoutPaper.copy(alpha = .38f), fontSize = 16.sp, lineHeight = 18.sp)
                        } else {
                            inner()
                        }
                    }
                },
            )
            if (onCalculator != null) {
                IconButton(onClick = onCalculator, enabled = enabled, modifier = Modifier.size(46.dp)) {
                    Icon(
                        imageVector = MettleIcons.Calculate,
                        contentDescription = "Open load calculator",
                        tint = WorkoutCyan.copy(alpha = .88f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutChip(text: String, metrics: WorkoutMetrics, success: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(metrics.dp(6)),
        color = Color.Transparent,
        border = BorderStroke(
            metrics.dp(.45),
            if (success) WorkoutGreen.copy(alpha = .20f) else WorkoutPaperMuted.copy(alpha = .15f),
        ),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = metrics.dp(10), vertical = metrics.dp(5)),
            color = if (success) WorkoutGreen else WorkoutPaperMuted,
            fontSize = metrics.sp(12),
            lineHeight = metrics.sp(15),
        )
    }
}

@Composable
private fun WorkoutExerciseActions(
    onSwap: () -> Unit,
    onRate: () -> Unit,
    onComplete: () -> Unit,
    allSetsComplete: Boolean,
    metrics: WorkoutMetrics,
) {
    Column(Modifier.padding(horizontal = metrics.dp(15), vertical = metrics.dp(16))) {
        WorkoutCardButton("Substitute this exercise", onSwap, Modifier.fillMaxWidth(), metrics)
        Spacer(Modifier.height(metrics.dp(12)))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            WorkoutCardButton("Rate exercise", onRate, Modifier.weight(1f), metrics)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (allSetsComplete) {
                    Box(
                        Modifier
                            .fillMaxWidth(.82f)
                            .height(metrics.dp(18))
                            .align(Alignment.BottomCenter)
                            .offset(y = metrics.dp(7))
                            .blur(metrics.dp(12))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, WorkoutGreen.copy(alpha = .74f), Color.Transparent),
                                ),
                                CircleShape,
                            ),
                    )
                }
                WorkoutCardButton("Complete exercise", onComplete, Modifier.fillMaxWidth(), metrics)
            }
        }
    }
}

@Composable
private fun CompletedExerciseActions(onRate: () -> Unit, onUndo: () -> Unit, metrics: WorkoutMetrics) {
    Row(
        modifier = Modifier.padding(horizontal = metrics.dp(15), vertical = metrics.dp(20)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        WorkoutCardButton("Rate exercise", onRate, Modifier.weight(1f), metrics)
        WorkoutCardButton("Mark undone", onUndo, Modifier.weight(1f), metrics)
    }
}

@Composable
private fun WorkoutCardButton(text: String, onClick: () -> Unit, modifier: Modifier, metrics: WorkoutMetrics) {
    MettleGlassActionButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = metrics.dp(48)),
        shadowElevation = metrics.dp(4),
        accent = false,
        containerTint = WorkoutCyan.copy(alpha = .055f),
        outlineColor = WorkoutCyan.copy(alpha = .10f),
        foregroundColor = WorkoutPaper,
        contentPadding = PaddingValues(horizontal = metrics.dp(13), vertical = metrics.dp(9)),
    ) {
        Text(
            text,
            fontSize = metrics.sp(14.5),
            lineHeight = metrics.sp(20),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun WorkoutSetupBody(
    exercise: ActiveWorkoutExercise,
    onAddPhoto: () -> Unit,
    onReturn: () -> Unit,
    metrics: WorkoutMetrics,
) {
    Column(Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(16))) {
        val notes = exercise.details.setupNotes
            .ifBlank { exercise.entity.movementReason }
            .replace(Regex("(?i)^Target\\s+\\d+\\s*[–-]\\s*\\d+\\s+RIR\\.\\s*"), "")
            .trim()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Setup",
                modifier = Modifier.weight(1f),
                color = WorkoutPaperMuted,
                fontSize = metrics.sp(13),
                lineHeight = metrics.sp(18),
                fontWeight = FontWeight.Medium,
            )
            MettleControlGlassSurface(
                modifier = Modifier.size(metrics.dp(58)),
                shape = CircleShape,
                tint = WorkoutCyan.copy(alpha = .025f),
                borderColor = WorkoutCyan.copy(alpha = .38f),
                shadowElevation = metrics.dp(3),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        MettleIcons.Edit,
                        contentDescription = "Edit setup notes",
                        tint = WorkoutCyan,
                        modifier = Modifier.size(metrics.dp(22)),
                    )
                }
            }
        }
        Spacer(Modifier.height(metrics.dp(8)))
        Text(
            notes.ifBlank { "Set the equipment to a stable, comfortable position and follow your setup cues." },
            modifier = Modifier.fillMaxWidth(),
            color = WorkoutPaper,
            fontSize = metrics.sp(14.5),
            lineHeight = metrics.sp(20.5),
            fontWeight = FontWeight.Normal,
        )
        exercise.details.cues.forEach { cue ->
            Spacer(Modifier.height(metrics.dp(7)))
            Text(
                "• $cue",
                color = WorkoutPaperMuted,
                fontSize = metrics.sp(13.5),
                lineHeight = metrics.sp(19),
            )
        }
        Spacer(Modifier.height(metrics.dp(20)))
        SetupMediaStrip(exercise.details.setupMediaPaths, onAddPhoto, metrics)
        Spacer(Modifier.height(metrics.dp(20)))
        WorkoutCardButton("Return to sets", onReturn, Modifier.fillMaxWidth(), metrics)
    }
}

@Composable
private fun SetupMediaStrip(paths: List<String>, onAddPhoto: () -> Unit, metrics: WorkoutMetrics) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        MettleControlGlassSurface(
            modifier = Modifier.width(metrics.dp(56)).height(metrics.dp(196)),
            shape = RoundedCornerShape(metrics.dp(25)),
            tint = WorkoutCyan.copy(alpha = .035f),
            borderColor = WorkoutCyan.copy(alpha = .32f),
            onClick = onAddPhoto,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("+", color = WorkoutPaper, fontSize = metrics.sp(24), textAlign = TextAlign.Center)
            }
        }
        paths.forEach { path -> SetupMediaImage(path, metrics) }
        if (paths.isEmpty()) {
            Surface(
                modifier = Modifier.width(metrics.dp(252)).height(metrics.dp(196)),
                shape = RoundedCornerShape(metrics.dp(25)),
                color = Color.White.copy(alpha = .04f),
            ) { Box(contentAlignment = Alignment.Center) { Text("Setup photos", color = WorkoutPaperMuted) } }
        }
    }
}

@Composable
private fun SetupMediaImage(relativePath: String, metrics: WorkoutMetrics) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, relativePath) {
        value = withContext(Dispatchers.IO) {
            val direct = File(relativePath)
            val candidate = if (direct.isAbsolute) direct else File(context.filesDir, relativePath)
            if (candidate.isFile) BitmapFactory.decodeFile(candidate.absolutePath) else null
        }
    }
    Surface(
        modifier = Modifier.width(metrics.dp(252)).height(metrics.dp(196)),
        shape = RoundedCornerShape(metrics.dp(25)),
        color = Color.White.copy(alpha = .05f),
    ) {
        if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Box(contentAlignment = Alignment.Center) { Text("Setup photo", color = WorkoutPaperMuted) }
    }
}

@Composable
private fun WorkoutQuickSelectContent(
    workout: ActiveWorkout,
    onSelect: (String?) -> Unit,
    metrics: WorkoutMetrics,
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
        items(workout.exercises, key = { it.entity.id }) { exercise ->
            val done = exercise.entity.status == "completed"
            val cardHazeState = rememberHazeState()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.dp(114))
                    .hazeSource(cardHazeState)
                    .clickable { onSelect(exercise.entity.id) },
                shape = WorkoutCardShape,
                color = if (done) WorkoutGreenDark.copy(alpha = .90f) else WorkoutCard,
                border = BorderStroke(metrics.dp(.6), Color.White.copy(alpha = .10f)),
                shadowElevation = metrics.dp(2),
            ) {
                CompositionLocalProvider(LocalMettleHazeState provides cardHazeState) {
                    Row(Modifier.padding(start = metrics.dp(16), end = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            exercise.entity.exerciseNameSnapshot,
                            color = WorkoutPaper,
                            fontSize = metrics.sp(22),
                            lineHeight = metrics.sp(28),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(contentAlignment = Alignment.TopEnd) {
                        MettleControlGlassSurface(
                            modifier = Modifier.size(metrics.dp(80)),
                            shape = CircleShape,
                            tint = if (done) WorkoutGreen.copy(alpha = .11f) else WorkoutCyan.copy(alpha = .07f),
                            borderColor = if (done) WorkoutGreen.copy(alpha = .48f) else WorkoutCyan.copy(alpha = .38f),
                            shadowElevation = metrics.dp(4),
                            onClick = { onSelect(exercise.entity.id) },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (done) MettleIcons.Check else MettleIcons.ArrowForward,
                                    contentDescription = if (done) "Open completed exercise" else "Open exercise",
                                    tint = if (done) WorkoutGreen else WorkoutPaper,
                                    modifier = Modifier.size(metrics.dp(30)),
                                )
                            }
                        }
                        Text(
                            "${exercise.entity.position + 1}",
                            color = WorkoutPaperMuted,
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
private fun WorkoutSubstitutionContent(
    current: ActiveWorkoutExercise,
    options: List<ExerciseSwapOption>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ExerciseSwapOption) -> Unit,
    metrics: WorkoutMetrics,
) {
    var query by remember { mutableStateOf("") }
    val filtered = options.filter {
        query.isBlank() || it.exerciseName.contains(query, ignoreCase = true) || it.executionProfileName.contains(query, ignoreCase = true)
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
        item {
            MettleExerciseSearchField(
                value = query,
                onValueChange = { query = it },
                height = metrics.dp(64),
                foreground = WorkoutPaper,
                muted = WorkoutPaperMuted,
                accent = WorkoutCyan,
            )
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(metrics.dp(30)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WorkoutCyan) } }
        } else {
            items(filtered, key = { it.executionProfileId }) { option ->
                val cardHazeState = rememberHazeState()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.dp(114))
                        .hazeSource(cardHazeState)
                        .clickable { onSelect(option) },
                    shape = WorkoutCardShape,
                    color = WorkoutCard,
                    border = BorderStroke(metrics.dp(.6), Color.White.copy(alpha = .10f)),
                ) {
                    CompositionLocalProvider(LocalMettleHazeState provides cardHazeState) {
                        Row(Modifier.padding(start = metrics.dp(16), end = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(option.exerciseName, color = WorkoutPaper, fontSize = metrics.sp(22), lineHeight = metrics.sp(28), fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(option.executionProfileName, color = WorkoutPaperMuted, fontSize = metrics.sp(13), maxLines = 1)
                        }
                        MettleControlGlassSurface(
                            modifier = Modifier.size(metrics.dp(80)),
                            shape = CircleShape,
                            tint = WorkoutCyan.copy(alpha = .07f),
                            borderColor = WorkoutCyan.copy(alpha = .38f),
                            shadowElevation = metrics.dp(4),
                            onClick = { onSelect(option) },
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = MettleIcons.SwapHoriz,
                                    contentDescription = "Use ${option.exerciseName}",
                                    tint = WorkoutPaper,
                                    modifier = Modifier.size(metrics.dp(30)),
                                )
                            }
                        }
                        }
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("No target-compatible replacements match that search.", color = WorkoutPaperMuted, modifier = Modifier.padding(metrics.dp(20))) }
            }
        }
        item {
            WorkoutCardButton(
                text = "Return to ${current.entity.exerciseNameSnapshot}",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                metrics = metrics,
            )
        }
    }
}

private enum class FinishChoice { KEEP_TRAINING, COMPLETE, COMPLETE_WITHOUT_REVIEW, DELETE }

private data class FinishTarget(
    val choice: FinishChoice,
    val xFraction: Float,
    val yFraction: Float,
    val size: Dp,
    val icon: ImageVector,
    val colour: Color,
)

@Composable
private fun FinishWorkoutGestureOverlay(
    destructive: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onCompleteWithoutReview: () -> Unit,
    onDelete: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(WorkoutInk.copy(alpha = .98f)),
    ) {
        val density = LocalDensity.current
        val view = LocalView.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val targets = remember(destructive) {
            if (destructive) {
                listOf(
                    FinishTarget(FinishChoice.KEEP_TRAINING, .76f, .46f, 120.dp, MettleIcons.Close, WorkoutCyan),
                    FinishTarget(FinishChoice.DELETE, .48f, .69f, 120.dp, MettleIcons.Backspace, WorkoutDelete),
                )
            } else {
                listOf(
                    FinishTarget(FinishChoice.DELETE, .25f, .52f, 82.dp, MettleIcons.Backspace, WorkoutDelete),
                    FinishTarget(FinishChoice.KEEP_TRAINING, .78f, .62f, 120.dp, MettleIcons.Close, WorkoutCyan),
                    FinishTarget(FinishChoice.COMPLETE, .51f, .76f, 120.dp, MettleIcons.Check, WorkoutGreen),
                    FinishTarget(FinishChoice.COMPLETE_WITHOUT_REVIEW, .23f, .78f, 82.dp, MettleIcons.DoneAll, WorkoutGreen),
                )
            }
        }
        val homePx = Offset(widthPx * .84f, heightPx * .88f)
        var dragging by remember { mutableStateOf(false) }
        var rawOffset by remember { mutableStateOf(Offset.Zero) }
        var active by remember { mutableStateOf<FinishChoice?>(null) }
        var settling by remember { mutableStateOf(false) }
        val settle by animateFloatAsState(
            targetValue = if (settling) 1f else 0f,
            animationSpec = spring(dampingRatio = .72f, stiffness = 440f),
            label = "finish-handle-return",
        )
        val shownOffset = if (settling) rawOffset * (1f - settle) else rawOffset

        fun targetCentre(target: FinishTarget) = Offset(widthPx * target.xFraction, heightPx * target.yFraction)
        fun resolveTarget(handle: Offset, thresholdScale: Float = .72f): FinishChoice? =
            targets.minByOrNull { target ->
                val centre = targetCentre(target)
                hypot(handle.x - centre.x, handle.y - centre.y)
            }?.takeIf { target ->
                val centre = targetCentre(target)
                hypot(handle.x - centre.x, handle.y - centre.y) < with(density) { target.size.toPx() * thresholdScale }
            }?.choice

        LaunchedEffect(settling) {
            if (settling) {
                kotlinx.coroutines.delay(330)
                rawOffset = Offset.Zero
                settling = false
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = if (destructive) 122.dp else 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (destructive) "Delete" else "Finished?",
                color = if (destructive) WorkoutDelete else WorkoutPaper,
                fontSize = 48.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                if (destructive) "Exit & delete workout?" else "Drag the workout button",
                color = WorkoutPaper,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        targets.forEach { target ->
            val centre = targetCentre(target)
            val selected = active == target.choice
            MettleControlGlassSurface(
                modifier = Modifier
                    .offset {
                        val radius = with(density) { target.size.toPx() / 2f }
                        IntOffset((centre.x - radius).roundToInt(), (centre.y - radius).roundToInt())
                    }
                    .size(target.size),
                shape = CircleShape,
                tint = target.colour.copy(alpha = if (selected) .21f else .07f),
                borderWidth = if (selected) 2.dp else 1.dp,
                borderColor = target.colour.copy(alpha = if (selected) .92f else .38f),
                shadowElevation = if (selected) 14.dp else 5.dp,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        target.icon,
                        contentDescription = target.choice.name,
                        tint = target.colour,
                        modifier = Modifier.size(if (target.size > 100.dp) 48.dp else 34.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset((homePx.x - 46.dp.toPx()).roundToInt(), (homePx.y - 46.dp.toPx()).roundToInt()) }
                .size(92.dp)
                .pointerInput(destructive, widthPx, heightPx) {
                    var velocityTracker = VelocityTracker()
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker = VelocityTracker()
                            dragging = true
                            settling = false
                            rawOffset = Offset.Zero
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            rawOffset += amount
                            val handle = homePx + rawOffset
                            velocityTracker.addPosition(change.uptimeMillis, handle)
                            val next = resolveTarget(handle)
                            if (next != active) {
                                active = next
                                if (next != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            val rawProjection = Offset(velocity.x, velocity.y) * .11f
                            val maximumProjection = with(density) { 180.dp.toPx() }
                            val projectionLength = hypot(rawProjection.x, rawProjection.y)
                            val projection = if (projectionLength > maximumProjection) {
                                rawProjection * (maximumProjection / projectionLength)
                            } else {
                                rawProjection
                            }
                            val confirmed = active ?: resolveTarget(homePx + rawOffset + projection, thresholdScale = .88f)
                            dragging = false
                            active = null
                            if (confirmed != null) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            when (confirmed) {
                                FinishChoice.KEEP_TRAINING -> onDismiss()
                                FinishChoice.COMPLETE -> onComplete()
                                FinishChoice.COMPLETE_WITHOUT_REVIEW -> onCompleteWithoutReview()
                                FinishChoice.DELETE -> onDelete()
                                null -> settling = true
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            active = null
                            settling = true
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (!dragging && !settling) {
                FinishDragHandle(active = false)
            }
        }

        if (dragging || settling) {
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (homePx.x - 32.dp.toPx() + shownOffset.x).roundToInt(),
                            (homePx.y - 32.dp.toPx() + shownOffset.y).roundToInt(),
                        )
                    }
                    .size(64.dp),
            ) { FinishDragHandle(active = active != null) }
        }
    }
}

@Composable
private fun FinishDragHandle(active: Boolean) {
    MettleControlGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = CircleShape,
        tint = WorkoutCyan.copy(alpha = if (active) .12f else .045f),
        borderColor = WorkoutPaper.copy(alpha = if (active) .80f else .52f),
        shadowElevation = if (active) 11.dp else 7.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(MettleIcons.SportsMartialArts, null, tint = WorkoutPaper, modifier = Modifier.size(24.dp))
        }
    }
}

private fun trimNumber(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else "%.1f".format(value)

private fun workoutDecimalInput(value: String): String {
    val cleaned = value.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    return if (firstDot < 0) cleaned.take(7) else {
        cleaned.take(firstDot + 1) + cleaned.drop(firstDot + 1).filter(Char::isDigit).take(2)
    }.take(8)
}
