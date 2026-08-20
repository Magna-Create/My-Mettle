package dev.kian.mymettle.ui

import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
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
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import java.io.File
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val WorkoutReferenceWidth = 453f
private val WorkoutInk = Color(0xFF11140F)
private val WorkoutSurfaceLow = Color(0xFF191D17)
private val WorkoutCard = Color(0xE61A3A37)
private val WorkoutCardQuiet = Color(0xCC23423F)
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
    onSaveDraft: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onSwapExercise: (ActiveWorkoutExercise) -> Unit,
    onSelectSwap: (ExerciseSwapOption) -> Unit,
    onDismissSwap: () -> Unit,
    onShowSets: (String?) -> Unit,
    onShowSetup: () -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    onDismissSheet: () -> Unit,
    onShowDelete: () -> Unit,
    onCompleteSession: () -> Unit,
    onDiscardSession: () -> Unit,
) {
    val workout = requireNotNull(state.workout)
    val focusManager = LocalFocusManager.current
    val focused = workout.exercises.firstOrNull { it.entity.id == state.focusedExerciseId }
        ?: workout.exercises.firstOrNull { it.entity.status != "completed" }
        ?: workout.exercises.first()
    val listState = rememberLazyListState()

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
            WorkoutBackdrop(onTap = { focusManager.clearFocus(force = true) })

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
                    onSaveDraft = onSaveDraft,
                    onLogSet = onLogSet,
                    onSwap = onSwapExercise,
                    onToggleExercise = onToggleExercise,
                    onRateExercise = onRateExercise,
                    metrics = metrics,
                )
            }

            WorkoutViewportScrims(metrics)
            WorkoutHeader(
                workout = workout,
                metrics = metrics,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            if (state.workoutSurface == WorkoutSurface.FINISH) {
                FinishWorkoutGestureSheet(
                    destructive = false,
                    onDismiss = onDismissSheet,
                    onComplete = onCompleteSession,
                    onDelete = onShowDelete,
                )
            }
            if (state.workoutSurface == WorkoutSurface.DELETE_CONFIRM) {
                FinishWorkoutGestureSheet(
                    destructive = true,
                    onDismiss = onDismissSheet,
                    onComplete = onCompleteSession,
                    onDelete = onDiscardSession,
                )
            }
        }
    }
}

@Composable
private fun WorkoutBackdrop(onTap: () -> Unit) {
    val hazeState = LocalMettleHazeState.current
    Canvas(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onTap)
            .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier),
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
    MettleControlGlassSurface(
        modifier = modifier,
        shape = CircleShape,
        tint = Color.White.copy(alpha = .018f),
        shadowElevation = metrics.dp(3),
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = metrics.dp(5), vertical = metrics.dp(15))) {
            val y = size.height / 2f
            val markerX = (size.width * (.34f + progress * .58f)).coerceIn(size.width * .34f, size.width * .92f)
            val waveEnd = size.width * .33f
            val path = Path().apply {
                moveTo(0f, y)
                cubicTo(size.width * .06f, y - size.height * .45f, size.width * .10f, y + size.height * .45f, size.width * .16f, y)
                cubicTo(size.width * .22f, y - size.height * .40f, size.width * .27f, y + size.height * .38f, waveEnd, y)
            }
            drawPath(path, WorkoutCyan, style = Stroke(metrics.dp(3).toPx(), cap = StrokeCap.Round))
            drawLine(WorkoutPaper, Offset(waveEnd + metrics.dp(7).toPx(), y), Offset(size.width, y), metrics.dp(2).toPx(), StrokeCap.Round)
            drawLine(
                WorkoutPaper,
                Offset(markerX, y - metrics.dp(8).toPx()),
                Offset(markerX, y + metrics.dp(8).toPx()),
                metrics.dp(3).toPx(),
                StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun BoxScope.WorkoutViewportScrims(metrics: WorkoutMetrics) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(132))
            .align(Alignment.TopCenter)
            .blur(metrics.dp(10))
            .background(
                Brush.verticalGradient(
                    0f to WorkoutInk,
                    .74f to WorkoutInk.copy(alpha = .92f),
                    1f to Color.Transparent,
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(150))
            .align(Alignment.BottomCenter)
            .blur(metrics.dp(12))
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .46f to WorkoutInk.copy(alpha = .82f),
                    1f to WorkoutInk,
                ),
            ),
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = WorkoutCardShape,
        color = if (completed) WorkoutGreenDark.copy(alpha = .90f) else WorkoutCard,
        border = BorderStroke(metrics.dp(.7), Color.White.copy(alpha = if (focused) .22f else .08f)),
        shadowElevation = if (focused) metrics.dp(6) else metrics.dp(2),
    ) {
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
                        fontSize = metrics.sp(28),
                        lineHeight = metrics.sp(36),
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                MettleControlGlassSurface(
                    modifier = Modifier.size(metrics.dp(80)),
                    shape = CircleShape,
                    tint = if (completed) WorkoutGreen.copy(alpha = .08f) else WorkoutCyan.copy(alpha = .035f),
                    shadowElevation = metrics.dp(4),
                    borderColor = Color.White.copy(alpha = .35f),
                    onClick = if (completed) null else onSetup,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = MettleIcons.SportsMartialArts,
                            contentDescription = if (completed) null else "Open exercise setup",
                            tint = if (completed) WorkoutGreen else WorkoutCyan,
                            modifier = Modifier.size(metrics.dp(29)),
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
                WorkoutChip(entity.importanceSnapshot.replaceFirstChar { it.uppercase() }, metrics)
                WorkoutChip("${entity.repMin}–${entity.repMax} reps", metrics)
                WorkoutChip("${entity.restSeconds}s rest", metrics)
                entity.prescribedLoad?.let { WorkoutChip("${trimNumber(it)} load progression", metrics) }
                if (completed) WorkoutChip("$logged/${entity.prescribedSets} Sets Complete", metrics, success = true)
            }
            Spacer(Modifier.height(metrics.dp(14)))
            Box(Modifier.fillMaxWidth().height(metrics.dp(1)).background(Color.White.copy(alpha = .10f)))

            when {
                completed -> CompletedExerciseActions(onRateExercise, onToggleExercise, metrics)
                showSetup -> WorkoutSetupBody(exercise, onReturn = onFocus, metrics = metrics)
                else -> {
                    Column(Modifier.padding(top = metrics.dp(15))) {
                        sets.forEachIndexed { index, set ->
                            val draft = drafts.getOrPut(set.id) { TrainSetDraft(set) }
                            WorkoutSetRow(
                                displayIndex = index,
                                exercise = exercise,
                                set = set,
                                draft = draft,
                                enabled = enabled,
                                isCurrent = focused && set.completedAt == null && sets.take(index).all { it.completedAt != null },
                                onSaveDraft = { onSaveDraft(set, draft) },
                                onLogSet = { onLogSet(set, draft) },
                                metrics = metrics,
                            )
                            if (index != sets.lastIndex) Spacer(Modifier.height(metrics.dp(3)))
                        }
                    }
                    WorkoutExerciseActions(
                        onSwap = onSwap,
                        onRate = onRateExercise,
                        onComplete = onToggleExercise,
                        metrics = metrics,
                    )
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

    Surface(
        modifier = Modifier.fillMaxWidth().height(metrics.dp(132)),
        color = Color.Transparent,
        border = BorderStroke(metrics.dp(.65), Color.White.copy(alpha = .23f)),
        shape = RoundedCornerShape(metrics.dp(25)),
    ) {
        Row {
            Box(
                modifier = Modifier
                    .width(metrics.dp(85))
                    .fillMaxHeight()
                    .background(
                        when {
                            set.completedAt != null -> WorkoutGreen.copy(alpha = .13f)
                            isCurrent -> WorkoutCyan.copy(alpha = .10f)
                            else -> Color.White.copy(alpha = .045f)
                        },
                    )
                    .clickable(enabled = enabled && ready) {
                        focusManager.clearFocus(force = true)
                        onLogSet()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(metrics.dp(78))
                            .blur(metrics.dp(18))
                            .background(WorkoutCyan.copy(alpha = .22f), CircleShape),
                    )
                }
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
) {
    var hadFocus by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        color = WorkoutDarkCyan.copy(alpha = .94f),
        border = BorderStroke(.5.dp, Color.White.copy(alpha = .08f)),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 5.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = WorkoutCyan.copy(alpha = .78f), fontSize = 10.sp, lineHeight = 11.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                modifier = Modifier.onFocusChanged { focus ->
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
                    if (value.isEmpty()) Text("00", color = WorkoutPaper.copy(alpha = .38f), fontSize = 16.sp) else inner()
                },
            )
        }
    }
}

@Composable
private fun WorkoutChip(text: String, metrics: WorkoutMetrics, success: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(metrics.dp(6)),
        color = Color.Transparent,
        border = BorderStroke(metrics.dp(.7), if (success) WorkoutGreen.copy(alpha = .55f) else WorkoutPaperMuted.copy(alpha = .42f)),
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
    metrics: WorkoutMetrics,
) {
    Column(Modifier.padding(horizontal = metrics.dp(15), vertical = metrics.dp(16))) {
        WorkoutCardButton("Substitute this exercise", onSwap, Modifier.fillMaxWidth(), metrics)
        Spacer(Modifier.height(metrics.dp(12)))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            WorkoutCardButton("Rate exercise", onRate, Modifier.weight(1f), metrics)
            WorkoutCardButton("Complete exercise", onComplete, Modifier.weight(1f), metrics)
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
    MettleControlGlassSurface(
        modifier = modifier.heightIn(min = metrics.dp(48)),
        shape = CircleShape,
        tint = WorkoutCyan.copy(alpha = .022f),
        borderColor = WorkoutCyan.copy(alpha = .58f),
        shadowElevation = metrics.dp(4),
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = metrics.dp(10)), contentAlignment = Alignment.Center) {
            Text(text, color = WorkoutPaper, fontSize = metrics.sp(14), fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun WorkoutSetupBody(exercise: ActiveWorkoutExercise, onReturn: () -> Unit, metrics: WorkoutMetrics) {
    Column(Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(12))) {
        val notes = exercise.details.setupNotes.ifBlank { exercise.entity.movementReason }
        Row(verticalAlignment = Alignment.Top) {
            Text(
                notes.ifBlank { "Set the equipment to a stable, comfortable position and follow your setup cues." },
                modifier = Modifier.weight(1f),
                color = WorkoutPaper,
                fontSize = metrics.sp(14),
                lineHeight = metrics.sp(20),
            )
            Spacer(Modifier.width(metrics.dp(10)))
            MettleControlGlassSurface(
                modifier = Modifier.size(metrics.dp(58)),
                shape = CircleShape,
                tint = WorkoutCyan.copy(alpha = .025f),
                borderColor = WorkoutCyan.copy(alpha = .52f),
                shadowElevation = metrics.dp(3),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("✎", color = WorkoutCyan, fontSize = metrics.sp(23))
                }
            }
        }
        exercise.details.cues.forEach { cue ->
            Spacer(Modifier.height(metrics.dp(5)))
            Text("• $cue", color = WorkoutPaperMuted, fontSize = metrics.sp(13), lineHeight = metrics.sp(18))
        }
        Spacer(Modifier.height(metrics.dp(18)))
        SetupMediaStrip(exercise.details.setupMediaPaths, metrics)
        Spacer(Modifier.height(metrics.dp(20)))
        WorkoutCardButton("Return to sets", onReturn, Modifier.fillMaxWidth(), metrics)
    }
}

@Composable
private fun SetupMediaStrip(paths: List<String>, metrics: WorkoutMetrics) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        Surface(
            modifier = Modifier.width(metrics.dp(56)).height(metrics.dp(196)),
            shape = RoundedCornerShape(metrics.dp(25)),
            color = Color.White.copy(alpha = .06f),
            border = BorderStroke(metrics.dp(.7), Color.White.copy(alpha = .22f)),
        ) { Box(contentAlignment = Alignment.Center) { Text("+", color = WorkoutPaper, fontSize = metrics.sp(24)) } }
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
            Surface(
                modifier = Modifier.fillMaxWidth().height(metrics.dp(114)).clickable { onSelect(exercise.entity.id) },
                shape = WorkoutCardShape,
                color = if (done) WorkoutGreenDark.copy(alpha = .90f) else WorkoutCard,
                border = BorderStroke(metrics.dp(.6), Color.White.copy(alpha = .10f)),
                shadowElevation = metrics.dp(2),
            ) {
                Row(Modifier.padding(start = metrics.dp(16), end = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            exercise.entity.exerciseNameSnapshot,
                            color = WorkoutPaper,
                            fontSize = metrics.sp(28),
                            lineHeight = metrics.sp(36),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(contentAlignment = Alignment.TopEnd) {
                        MettleControlGlassSurface(
                            modifier = Modifier.size(metrics.dp(80)),
                            shape = CircleShape,
                            tint = if (done) WorkoutGreen.copy(alpha = .07f) else WorkoutCyan.copy(alpha = .03f),
                            borderColor = Color.White.copy(alpha = .34f),
                            shadowElevation = metrics.dp(4),
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("→", color = if (done) WorkoutGreen else WorkoutPaper, fontSize = metrics.sp(34))
                            }
                        }
                        Text(
                            "${exercise.entity.position + 1}",
                            color = WorkoutPaperMuted,
                            fontSize = metrics.sp(11),
                            modifier = Modifier.padding(end = metrics.dp(2)),
                        )
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
            MettleControlGlassSurface(
                modifier = Modifier.fillMaxWidth().height(metrics.dp(64)),
                shape = CircleShape,
                tint = Color.White.copy(alpha = .025f),
                borderColor = Color.White.copy(alpha = .10f),
                shadowElevation = metrics.dp(3),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = metrics.dp(20)), verticalAlignment = Alignment.CenterVertically) {
                    Text("☰", color = WorkoutPaperMuted, fontSize = metrics.sp(20))
                    Spacer(Modifier.width(metrics.dp(14)))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = WorkoutPaper, fontSize = metrics.sp(17)),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (query.isEmpty()) Text("Search for specific exercises", color = WorkoutPaperMuted, fontSize = metrics.sp(17)) else inner()
                        },
                    )
                    Text("⌕", color = WorkoutPaperMuted, fontSize = metrics.sp(24))
                }
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(metrics.dp(30)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WorkoutCyan) } }
        } else {
            items(filtered, key = { it.executionProfileId }) { option ->
                Surface(
                    modifier = Modifier.fillMaxWidth().height(metrics.dp(114)).clickable { onSelect(option) },
                    shape = WorkoutCardShape,
                    color = WorkoutCard,
                    border = BorderStroke(metrics.dp(.6), Color.White.copy(alpha = .10f)),
                ) {
                    Row(Modifier.padding(start = metrics.dp(16), end = metrics.dp(18)), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(option.exerciseName, color = WorkoutPaper, fontSize = metrics.sp(26), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(option.executionProfileName, color = WorkoutPaperMuted, fontSize = metrics.sp(13), maxLines = 1)
                        }
                        MettleControlGlassSurface(
                            modifier = Modifier.size(metrics.dp(80)),
                            shape = CircleShape,
                            tint = WorkoutCyan.copy(alpha = .03f),
                            borderColor = Color.White.copy(alpha = .34f),
                            shadowElevation = metrics.dp(4),
                        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("⇆", color = WorkoutPaper, fontSize = metrics.sp(29)) } }
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

private enum class FinishChoice { KEEP_TRAINING, COMPLETE, DELETE }

private data class FinishTarget(
    val choice: FinishChoice,
    val x: Float,
    val y: Float,
    val size: Float,
    val symbol: String,
    val colour: Color,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun FinishWorkoutGestureSheet(
    destructive: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WorkoutSurfaceLow,
        contentColor = WorkoutPaper,
        dragHandle = null,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(620.dp)) {
            val density = LocalDensity.current
            val view = LocalView.current
            val widthScale = (maxWidth.value / WorkoutReferenceWidth).coerceAtMost(1f)
            val targets = remember(destructive) {
                if (destructive) {
                    listOf(
                        FinishTarget(FinishChoice.KEEP_TRAINING, 335f, 215f, 120f, "×", WorkoutCyan),
                        FinishTarget(FinishChoice.DELETE, 205f, 355f, 120f, "⌫", WorkoutDelete),
                    )
                } else {
                    listOf(
                        FinishTarget(FinishChoice.DELETE, 114f, 285f, 80f, "⌫", WorkoutDelete),
                        FinishTarget(FinishChoice.KEEP_TRAINING, 355f, 345f, 120f, "×", WorkoutCyan),
                        FinishTarget(FinishChoice.COMPLETE, 234f, 405f, 120f, "✓", WorkoutGreen),
                    )
                }
            }
            var dragging by remember { mutableStateOf(false) }
            var rawOffset by remember { mutableStateOf(Offset.Zero) }
            var dragOrigin by remember { mutableStateOf(Offset.Zero) }
            var active by remember { mutableStateOf<FinishChoice?>(null) }
            var settling by remember { mutableStateOf(false) }
            val settle by animateFloatAsState(
                targetValue = if (settling) 1f else 0f,
                animationSpec = spring(dampingRatio = .72f, stiffness = 440f),
                label = "finish-handle-return",
            )
            val shownOffset = if (settling) rawOffset * (1f - settle) else rawOffset
            val homeDp = Offset(383f * widthScale, 555f)
            val homePx = with(density) { Offset(homeDp.x.dp.toPx(), homeDp.y.dp.toPx()) }

            LaunchedEffect(settling) {
                if (settling) {
                    kotlinx.coroutines.delay(330)
                    rawOffset = Offset.Zero
                    settling = false
                }
            }

            Box(Modifier.align(Alignment.TopCenter).padding(top = 16.dp).size(width = 32.dp, height = 4.dp).background(WorkoutPaperMuted.copy(alpha = .7f), CircleShape))
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 70.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(if (destructive) "Delete" else "Finished?", color = if (destructive) WorkoutDelete else WorkoutPaper, fontSize = 48.sp, fontWeight = FontWeight.Medium)
                Text(if (destructive) "Exit & Delete Workout?" else "Drag the handle", color = WorkoutPaper, fontSize = 25.sp, fontWeight = FontWeight.Medium)
            }

            targets.forEach { target ->
                val selected = active == target.choice
                Surface(
                    modifier = Modifier
                        .offset(x = (target.x * widthScale - target.size * widthScale / 2f).dp, y = (target.y - target.size / 2f).dp)
                        .size((target.size * widthScale).dp),
                    shape = CircleShape,
                    color = target.colour.copy(alpha = if (selected) .45f else .16f),
                    border = BorderStroke(if (selected) 3.dp else 1.dp, target.colour.copy(alpha = if (selected) .95f else .28f)),
                    shadowElevation = if (selected) 12.dp else 1.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(target.symbol, color = target.colour, fontSize = if (target.size > 90) 54.sp else 34.sp)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (homePx.x - 32.dp.toPx()).roundToInt(),
                            (homePx.y - 32.dp.toPx()).roundToInt(),
                        )
                    }
                    .size(64.dp)
                    .pointerInteropFilter { event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                dragging = true
                                settling = false
                                rawOffset = Offset.Zero
                                dragOrigin = Offset(event.x, event.y)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                rawOffset = Offset(event.x - dragOrigin.x, event.y - dragOrigin.y)
                                val handle = homePx + rawOffset
                                val next = targets.minByOrNull { target ->
                                    val targetPx = with(density) {
                                        Offset((target.x * widthScale).dp.toPx(), target.y.dp.toPx())
                                    }
                                    hypot(handle.x - targetPx.x, handle.y - targetPx.y)
                                }?.takeIf { target ->
                                    val targetPx = with(density) {
                                        Offset((target.x * widthScale).dp.toPx(), target.y.dp.toPx())
                                    }
                                    hypot(handle.x - targetPx.x, handle.y - targetPx.y) < with(density) { (target.size * .72f).dp.toPx() }
                                }?.choice
                                if (next != active) {
                                    active = next
                                    if (next != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                val confirmed = active
                                dragging = false
                                active = null
                                when (confirmed) {
                                    FinishChoice.KEEP_TRAINING -> onDismiss()
                                    FinishChoice.COMPLETE -> onComplete()
                                    FinishChoice.DELETE -> onDelete()
                                    null -> settling = true
                                }
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                dragging = false
                                active = null
                                settling = true
                                true
                            }
                            else -> true
                        }
                    },
            ) {
                MettleControlGlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = CircleShape,
                    tint = WorkoutCyan.copy(alpha = if (dragging) .08f else .035f),
                    borderColor = WorkoutPaper.copy(alpha = .52f),
                    shadowElevation = 7.dp,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(MettleIcons.SportsMartialArts, null, tint = WorkoutPaper, modifier = Modifier.size(24.dp))
                    }
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
                ) {
                    MettleControlGlassSurface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        tint = WorkoutCyan.copy(alpha = if (active != null) .10f else .045f),
                        borderColor = WorkoutPaper.copy(alpha = .62f),
                        shadowElevation = 9.dp,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(MettleIcons.SportsMartialArts, null, tint = WorkoutPaper, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
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
