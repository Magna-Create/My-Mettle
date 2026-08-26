package dev.kian.mymettle.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.kian.mymettle.domain.performance.Laterality
import dev.kian.mymettle.domain.performance.LateralityMode
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.domain.performance.QuantityDimension
import dev.kian.mymettle.domain.performance.SchemaMetric
import dev.kian.mymettle.domain.performance.TargetKind
import dev.kian.mymettle.domain.performance.UnitConverter
import dev.kian.mymettle.domain.performance.UnitId
import dev.kian.mymettle.library.WorkoutSetupDetails
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import dev.kian.mymettle.workout.PerformanceSetRecord
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

private const val WorkoutV2ReferenceWidth = 453f
private val WorkoutV2Ink = Color(0xFF11140F)
private val WorkoutV2Card = Color(0xA81A3A37)
private val WorkoutV2Paper = Color(0xFFE1E4DA)
private val WorkoutV2Muted = Color(0xFFC3C8BB)
private val WorkoutV2Cyan = Color(0xFFBBEBED)
private val WorkoutV2CyanStrong = Color(0xFFA0CFD0)
private val WorkoutV2DarkCyan = Color(0xFF002021)
private val WorkoutV2Green = Color(0xFFC3EFAD)
private val WorkoutV2GreenDark = Color(0xFF1E3B19)
private val WorkoutV2Delete = Color(0xFFFFB4AB)
private val WorkoutV2CardShape = RoundedCornerShape(25.dp)

private data class WorkoutV2Metrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

/**
 * N-BIO-6 aware workout renderer.
 *
 * QUICK_SELECT, substitution and the finish gesture remain delegated to the battle-tested alpha23
 * renderer. SETS and SETUP are replaced here because their layout now follows logical sets,
 * side-addressed observations and the redesigned setup editor.
 */
@Composable
internal fun FigmaWorkoutSessionV2(
    state: N2WorkoutUiState,
    drafts: MutableMap<String, TrainSetDraft>,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, PerformanceSetRecord, PerformanceMetric, Laterality?) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, PerformanceSetRecord, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, PerformanceSetRecord, TrainSetDraft) -> Unit,
    onSwapExercise: (ActiveWorkoutExercise) -> Unit,
    onSelectSwap: (ExerciseSwapOption) -> Unit,
    onDismissSwap: () -> Unit,
    onShowSets: (String?) -> Unit,
    onShowSetup: () -> Unit,
    onAddSetupPhoto: (ActiveWorkoutExercise) -> Unit,
    onDeleteSetupPhoto: (ActiveWorkoutExercise, String) -> Unit,
    onSaveSetupDetails: (ActiveWorkoutExercise, String, String, String) -> Unit,
    onOpenExerciseLink: (String) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    onDismissSheet: () -> Unit,
    onShowDelete: () -> Unit,
    onCompleteSession: () -> Unit,
    onCompleteWithoutReview: () -> Unit,
    onDiscardSession: () -> Unit,
) {
    val delegateToLegacy = state.swapTarget != null || state.workoutSurface == WorkoutSurface.QUICK_SELECT
    if (delegateToLegacy) {
        FigmaWorkoutSession(
            state = state,
            drafts = drafts,
            onOpenSettings = onOpenSettings,
            onOpenAccount = onOpenAccount,
            onOpenCalculator = { exercise, set, metric -> onOpenCalculator(exercise, set, metric, null) },
            onSaveDraft = onSaveDraft,
            onLogSet = onLogSet,
            onSwapExercise = onSwapExercise,
            onSelectSwap = onSelectSwap,
            onDismissSwap = onDismissSwap,
            onShowSets = onShowSets,
            onShowSetup = onShowSetup,
            onAddSetupPhoto = onAddSetupPhoto,
            onToggleExercise = onToggleExercise,
            onRateExercise = onRateExercise,
            onDismissSheet = onDismissSheet,
            onShowDelete = onShowDelete,
            onCompleteSession = onCompleteSession,
            onCompleteWithoutReview = onCompleteWithoutReview,
            onDiscardSession = onDiscardSession,
        )
        return
    }

    val workout = requireNotNull(state.workout)
    val focusManager = LocalFocusManager.current
    val focused = workout.exercises.firstOrNull { it.entity.id == state.focusedExerciseId }
        ?: workout.exercises.firstOrNull { it.entity.status != "completed" }
        ?: workout.exercises.first()
    val listState = rememberLazyListState()
    val viewportHazeState = rememberHazeState()
    val headerHazeState = rememberHazeState()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportWidth = minOf(maxWidth, WorkoutV2ReferenceWidth.dp)
        val metrics = WorkoutV2Metrics((viewportWidth.value / WorkoutV2ReferenceWidth).coerceAtMost(1f))
        Box(
            Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .background(WorkoutV2Ink),
        ) {
            // Header glass samples the fully composited workout below it. The header itself stays
            // outside this source, preventing the recursive/black glass artefact seen on device.
            Box(Modifier.fillMaxSize().hazeSource(headerHazeState)) {
                WorkoutV2Backdrop(
                    modifier = Modifier.fillMaxSize().hazeSource(viewportHazeState),
                    onTap = { focusManager.clearFocus(force = true) },
                )
                CompositionLocalProvider(LocalMettleHazeState provides viewportHazeState) {
                    WorkoutV2ExerciseContent(
                        workout = workout,
                        focusedId = focused.entity.id,
                        setupExerciseId = focused.entity.id.takeIf { state.workoutSurface == WorkoutSurface.SETUP },
                        setupDetails = state.setupDetails,
                        loadingSetupDetails = state.loadingSetupDetails,
                        savingSetupDetails = state.savingSetupDetails,
                        savingSetupPhoto = state.savingSetupPhoto,
                        drafts = drafts,
                        loading = state.loading,
                        listState = listState,
                        onFocusExercise = onShowSets,
                        onShowSetup = onShowSetup,
                        onAddSetupPhoto = onAddSetupPhoto,
                        onDeleteSetupPhoto = onDeleteSetupPhoto,
                        onSaveSetupDetails = onSaveSetupDetails,
                        onOpenExerciseLink = onOpenExerciseLink,
                        onOpenCalculator = onOpenCalculator,
                        onSaveDraft = onSaveDraft,
                        onLogSet = onLogSet,
                        onSwap = onSwapExercise,
                        onToggleExercise = onToggleExercise,
                        onRateExercise = onRateExercise,
                        metrics = metrics,
                    )
                }
                WorkoutV2ViewportScrims(metrics)
            }
            CompositionLocalProvider(LocalMettleHazeState provides headerHazeState) {
                WorkoutV2Header(workout, metrics, onOpenSettings, onOpenAccount)
            }
            if (state.workoutSurface in setOf(WorkoutSurface.FINISH, WorkoutSurface.DELETE_CONFIRM)) {
                WorkoutExitOverlay(
                    onOpenSettings = onOpenSettings,
                    onOpenAccount = onOpenAccount,
                    onDismiss = onDismissSheet,
                    onCompleteAndRate = onCompleteSession,
                    onComplete = onCompleteWithoutReview,
                    onDelete = onDiscardSession,
                )
            }
        }
    }
}

@Composable
private fun WorkoutV2Backdrop(modifier: Modifier = Modifier, onTap: () -> Unit) {
    Canvas(modifier.clickable(onClick = onTap)) {
        drawRect(WorkoutV2Ink)
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
private fun WorkoutV2ExerciseContent(
    workout: ActiveWorkout,
    focusedId: String,
    setupExerciseId: String?,
    setupDetails: WorkoutSetupDetails?,
    loadingSetupDetails: Boolean,
    savingSetupDetails: Boolean,
    savingSetupPhoto: Boolean,
    drafts: MutableMap<String, TrainSetDraft>,
    loading: Boolean,
    listState: LazyListState,
    onFocusExercise: (String?) -> Unit,
    onShowSetup: () -> Unit,
    onAddSetupPhoto: (ActiveWorkoutExercise) -> Unit,
    onDeleteSetupPhoto: (ActiveWorkoutExercise, String) -> Unit,
    onSaveSetupDetails: (ActiveWorkoutExercise, String, String, String) -> Unit,
    onOpenExerciseLink: (String) -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, PerformanceSetRecord, PerformanceMetric, Laterality?) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, PerformanceSetRecord, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, PerformanceSetRecord, TrainSetDraft) -> Unit,
    onSwap: (ActiveWorkoutExercise) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val visible = workout.exercises.filter {
        it.entity.prescriptionIncluded || it.entity.status == "completed" || it.sets.any { set -> set.observations.isNotEmpty() }
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
            WorkoutV2ExerciseCard(
                exercise = exercise,
                focused = exercise.entity.id == focusedId,
                showSetup = exercise.entity.id == setupExerciseId,
                setupDetails = setupDetails.takeIf { exercise.entity.id == setupExerciseId },
                loadingSetupDetails = loadingSetupDetails && exercise.entity.id == setupExerciseId,
                savingSetupDetails = savingSetupDetails,
                savingSetupPhoto = savingSetupPhoto,
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
                onDeleteSetupPhoto = { relativePath -> onDeleteSetupPhoto(exercise, relativePath) },
                onSaveSetupDetails = { exerciseInstructions, setupInstructions, url ->
                    onSaveSetupDetails(exercise, exerciseInstructions, setupInstructions, url)
                },
                onOpenExerciseLink = onOpenExerciseLink,
                onOpenCalculator = { set, metric, side -> onOpenCalculator(exercise, set, metric, side) },
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
private fun WorkoutV2Header(
    workout: ActiveWorkout,
    metrics: WorkoutV2Metrics,
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
            Text("My Mettle", color = WorkoutV2Cyan, fontSize = metrics.sp(24.2), lineHeight = metrics.sp(31))
            Text("Workout Session", color = WorkoutV2Muted, fontSize = metrics.sp(13.2), lineHeight = metrics.sp(18))
        }
        WorkoutV2WaveProgress(workout, Modifier.weight(1f).height(metrics.dp(52)), metrics)
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
private fun WorkoutV2WaveProgress(workout: ActiveWorkout, modifier: Modifier, metrics: WorkoutV2Metrics) {
    val visible = workout.exercises.filter { it.entity.prescriptionIncluded }
    val completed = visible.count { it.entity.status == "completed" }
    val progress = if (visible.isEmpty()) 0f else completed.toFloat() / visible.size
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = .84f, stiffness = 280f),
        label = "workout-progress-v2",
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
            drawLine(WorkoutV2Paper.copy(alpha = .52f), Offset(endX, y), Offset(size.width, y), metrics.dp(2.1).toPx())
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
                drawPath(path, WorkoutV2Cyan, style = Stroke(metrics.dp(2.1).toPx()))
                drawLine(
                    WorkoutV2Paper,
                    Offset(endX, y - metrics.dp(9).toPx()),
                    Offset(endX, y + metrics.dp(9).toPx()),
                    metrics.dp(2.2).toPx(),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.WorkoutV2ViewportScrims(metrics: WorkoutV2Metrics) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.dp(132))
            .align(Alignment.TopCenter)
            .background(
                Brush.verticalGradient(
                    0f to WorkoutV2Ink.copy(alpha = .93f),
                    .65f to WorkoutV2Ink.copy(alpha = .70f),
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
                    .55f to WorkoutV2Ink.copy(alpha = .48f),
                    1f to WorkoutV2Ink.copy(alpha = .88f),
                ),
            ),
    )
}

@Composable
private fun WorkoutV2ExerciseCard(
    exercise: ActiveWorkoutExercise,
    focused: Boolean,
    showSetup: Boolean,
    setupDetails: WorkoutSetupDetails?,
    loadingSetupDetails: Boolean,
    savingSetupDetails: Boolean,
    savingSetupPhoto: Boolean,
    drafts: MutableMap<String, TrainSetDraft>,
    enabled: Boolean,
    onFocus: () -> Unit,
    onSetup: () -> Unit,
    onAddSetupPhoto: () -> Unit,
    onDeleteSetupPhoto: (String) -> Unit,
    onSaveSetupDetails: (String, String, String) -> Unit,
    onOpenExerciseLink: (String) -> Unit,
    onOpenCalculator: (PerformanceSetRecord, PerformanceMetric, Laterality?) -> Unit,
    onSaveDraft: (PerformanceSetRecord, TrainSetDraft) -> Unit,
    onLogSet: (PerformanceSetRecord, TrainSetDraft) -> Unit,
    onSwap: () -> Unit,
    onToggleExercise: () -> Unit,
    onRateExercise: () -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val entity = exercise.entity
    val completed = entity.status == "completed"
    val sets = exercise.sets
        .filter { it.setIndex < exercise.prescription.sets || it.observations.isNotEmpty() }
        .sortedBy { it.setIndex }
    val logged = sets.count { it.isCompleteFor(exercise.resolvedWorkoutLateralityMode()) }

    val cardHazeState = rememberHazeState()
    val cardColor = if (completed) WorkoutV2GreenDark.copy(alpha = .78f) else WorkoutV2Card
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .dropShadow(
                shape = WorkoutV2CardShape,
                shadow = Shadow(
                    radius = metrics.dp(if (focused) 7 else 4.5),
                    spread = 0.dp,
                    color = Color.Black.copy(alpha = if (focused) .30f else .22f),
                    offset = DpOffset(0.dp, metrics.dp(if (focused) 2.6 else 1.7)),
                ),
            )
            .mettleDirectionalBorder(
                width = metrics.dp(.7),
                color = Color.White.copy(alpha = if (focused) .18f else .11f),
                shape = WorkoutV2CardShape,
            )
            .clip(WorkoutV2CardShape),
    ) {
        // Restrict the Haze source to the clipped card paint. Sampling an elevated Surface was
        // producing the large rectangular ghost layer visible behind cards while scrolling.
        Box(
            Modifier
                .matchParentSize()
                .hazeSource(cardHazeState)
                .background(cardColor),
        )
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
                Text(
                    entity.exerciseNameSnapshot,
                    modifier = Modifier.weight(1f),
                    color = WorkoutV2Paper,
                    fontSize = metrics.sp(26),
                    lineHeight = metrics.sp(32),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(metrics.dp(12)))
                MettleControlGlassSurface(
                    modifier = Modifier.size(metrics.dp(80)),
                    shape = CircleShape,
                    tint = if (completed) WorkoutV2Green.copy(alpha = .10f) else WorkoutV2Cyan.copy(alpha = .055f),
                    baseColor = WorkoutV2DarkCyan.copy(alpha = .20f),
                    borderColor = if (completed) WorkoutV2Green.copy(alpha = .50f) else WorkoutV2Cyan.copy(alpha = .38f),
                    shadowElevation = metrics.dp(4),
                    onClick = if (completed) null else onSetup,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (completed) MettleIcons.Check else MettleIcons.SportsMartialArts,
                            contentDescription = if (completed) "Exercise completed" else "Open exercise setup",
                            tint = if (completed) WorkoutV2Green else WorkoutV2Cyan,
                            modifier = Modifier.size(metrics.dp(if (completed) 32 else 29)),
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = metrics.dp(16)),
                horizontalArrangement = Arrangement.spacedBy(metrics.dp(8)),
            ) {
                if (completed) {
                    WorkoutV2Chip("$logged/${exercise.prescription.sets} Sets Complete", metrics, success = true)
                } else {
                    WorkoutV2Chip(entity.importanceSnapshot.replaceFirstChar { it.uppercase() }, metrics)
                    workoutV2PrescriptionLabels(exercise).forEach { WorkoutV2Chip(it, metrics) }
                    WorkoutV2Chip("${entity.restSeconds}s rest", metrics)
                }
            }
            Spacer(Modifier.height(metrics.dp(14)))
            Box(Modifier.fillMaxWidth().height(metrics.dp(1)).background(Color.White.copy(alpha = .10f)))

            when {
                completed -> WorkoutV2CompletedActions(onRateExercise, onToggleExercise, metrics)
                showSetup -> WorkoutV2SetupBody(
                    exercise = exercise,
                    details = setupDetails,
                    loading = loadingSetupDetails,
                    saving = savingSetupDetails,
                    photoMutationInProgress = savingSetupPhoto,
                    onAddPhoto = onAddSetupPhoto,
                    onDeletePhoto = onDeleteSetupPhoto,
                    onSave = onSaveSetupDetails,
                    onOpenLink = onOpenExerciseLink,
                    onReturn = onFocus,
                    metrics = metrics,
                )
                else -> {
                    Column(
                        Modifier.padding(
                            start = metrics.dp(15),
                            end = metrics.dp(15),
                            top = metrics.dp(15),
                        ),
                        verticalArrangement = Arrangement.spacedBy(
                            metrics.dp(
                                if (exercise.resolvedWorkoutLateralityMode() == LateralityMode.UNILATERAL) 9 else 2.5,
                            ),
                        ),
                    ) {
                        sets.forEachIndexed { index, set ->
                            val isCurrentLogicalSet = focused &&
                                !set.isCompleteFor(exercise.resolvedWorkoutLateralityMode()) &&
                                sets.take(index).all { it.isCompleteFor(exercise.resolvedWorkoutLateralityMode()) }
                            WorkoutV2LogicalSetGroup(
                                displayIndex = index,
                                groupIndex = index,
                                groupLastIndex = sets.lastIndex,
                                exercise = exercise,
                                set = set,
                                drafts = drafts,
                                enabled = enabled,
                                isCurrent = isCurrentLogicalSet,
                                onOpenCalculator = { metric, side -> onOpenCalculator(set, metric, side) },
                                onSaveDraft = { draft -> onSaveDraft(set, draft) },
                                onLogSet = { draft -> onLogSet(set, draft) },
                                metrics = metrics,
                            )
                        }
                    }
                    WorkoutV2ExerciseActions(
                        onSwap = onSwap,
                        onRate = onRateExercise,
                        onComplete = onToggleExercise,
                        allSetsComplete = sets.isNotEmpty() && sets.all { it.isCompleteFor(exercise.resolvedWorkoutLateralityMode()) },
                        metrics = metrics,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun WorkoutV2LogicalSetGroup(
    displayIndex: Int,
    groupIndex: Int,
    groupLastIndex: Int,
    exercise: ActiveWorkoutExercise,
    set: PerformanceSetRecord,
    drafts: MutableMap<String, TrainSetDraft>,
    enabled: Boolean,
    isCurrent: Boolean,
    onOpenCalculator: (PerformanceMetric, Laterality?) -> Unit,
    onSaveDraft: (TrainSetDraft) -> Unit,
    onLogSet: (TrainSetDraft) -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val sides = when (exercise.resolvedWorkoutLateralityMode()) {
        LateralityMode.UNILATERAL -> listOf(Laterality.RIGHT, Laterality.LEFT)
        else -> listOf<Laterality?>(null)
    }
    Column(verticalArrangement = Arrangement.spacedBy(metrics.dp(2.5))) {
        sides.forEachIndexed { sideIndex, side ->
            val key = workoutDraftKey(set.id, side)
            val draft = drafts.getOrPut(key) { TrainSetDraft(set, exercise, side) }
            val sideCompleted = side?.let(set::hasObservation)
                ?: set.isCompleteFor(exercise.resolvedWorkoutLateralityMode())
            val shape = when {
                sides.size == 1 -> workoutV2GroupedSetShape(groupIndex, groupLastIndex, metrics)
                sideIndex == 0 -> RoundedCornerShape(
                    topStart = metrics.dp(25),
                    topEnd = metrics.dp(25),
                    bottomStart = metrics.dp(5),
                    bottomEnd = metrics.dp(5),
                )
                else -> RoundedCornerShape(
                    topStart = metrics.dp(5),
                    topEnd = metrics.dp(5),
                    bottomStart = metrics.dp(25),
                    bottomEnd = metrics.dp(25),
                )
            }
            WorkoutV2ObservationRow(
                displayIndex = displayIndex,
                exercise = exercise,
                set = set,
                draft = draft,
                side = side,
                completed = sideCompleted,
                enabled = enabled && !sideCompleted,
                isCurrent = isCurrent && !sideCompleted && sides.take(sideIndex).all { previous ->
                    previous == null || set.hasObservation(previous)
                },
                shape = shape,
                onOpenCalculator = { metric -> onOpenCalculator(metric, side) },
                onSaveDraft = { onSaveDraft(draft) },
                onLogSet = { onLogSet(draft) },
                metrics = metrics,
            )
        }
    }
}

private fun workoutV2GroupedSetShape(
    index: Int,
    lastIndex: Int,
    metrics: WorkoutV2Metrics,
): RoundedCornerShape {
    val outer = metrics.dp(25)
    val inner = metrics.dp(5)
    return when {
        lastIndex == 0 -> RoundedCornerShape(outer)
        index == 0 -> RoundedCornerShape(
            topStart = outer,
            topEnd = outer,
            bottomStart = inner,
            bottomEnd = inner,
        )
        index == lastIndex -> RoundedCornerShape(
            topStart = inner,
            topEnd = inner,
            bottomStart = outer,
            bottomEnd = outer,
        )
        else -> RoundedCornerShape(inner)
    }
}

@Composable
private fun WorkoutV2ObservationRow(
    displayIndex: Int,
    exercise: ActiveWorkoutExercise,
    set: PerformanceSetRecord,
    draft: TrainSetDraft,
    side: Laterality?,
    completed: Boolean,
    enabled: Boolean,
    isCurrent: Boolean,
    shape: RoundedCornerShape,
    onOpenCalculator: (PerformanceMetric) -> Unit,
    onSaveDraft: () -> Unit,
    onLogSet: () -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val focusManager = LocalFocusManager.current
    val definitions = exercise.schema.metrics
    val lateralityChoices = if (side == null && exercise.resolvedWorkoutLateralityMode() == LateralityMode.ALTERNATING_ALLOWED) {
        listOf(Laterality.LEFT, Laterality.RIGHT, Laterality.ALTERNATING)
    } else {
        emptyList()
    }
    val requiredReady = definitions.filter { it.required }.all { definition ->
        val value = draft.value(definition.metric).toDoubleOrNull() ?: return@all false
        when (definition.metric) {
            PerformanceMetric.REPETITIONS, PerformanceMetric.DURATION -> value > 0.0
            else -> true
        }
    }
    val lateralityReady = exercise.resolvedWorkoutLateralityMode() != LateralityMode.UNILATERAL ||
        draft.laterality in setOf(Laterality.LEFT, Laterality.RIGHT)
    val ready = requiredReady && lateralityReady
    val sideLabel = when (side ?: draft.laterality) {
        Laterality.LEFT -> "L"
        Laterality.RIGHT -> "R"
        Laterality.ALTERNATING -> "Alt"
        else -> null
    }
    val label = "${displayIndex + 1}${sideLabel?.let { " · $it" }.orEmpty()}"
    val standardTwoMetricLayout = definitions.size == 2 && lateralityChoices.isEmpty()
    val fieldHeight = metrics.dp(55)
    val rowHeight = if (standardTwoMetricLayout) {
        // Preserve the established workout interaction geometry: one set column at left and two
        // large full-width metric rows stacked vertically. N-BIO-6 changes data semantics, not UX.
        metrics.dp(132)
    } else {
        definitions.size * fieldHeight +
            (definitions.size - 1).coerceAtLeast(0) * metrics.dp(8) +
            if (lateralityChoices.isNotEmpty()) metrics.dp(52) else metrics.dp(18)
    }

    Box(
    Modifier
        .fillMaxWidth()
        .height(rowHeight),
) {
    // Keep the structural row clipped, but let the current-set light live outside that
    // clip. The old blurred child was clipped at its own rectangular bounds, creating
    // the hard top/bottom cut-off visible on device.
    Box(
        Modifier
            .matchParentSize()
            .clip(shape)
            .background(Color.White.copy(alpha = if (completed) .055f else .025f)),
    )
        if (isCurrent) {
            Box(
                Modifier
                .size(metrics.dp(104))
                .align(Alignment.CenterStart)
                .blur(
                    radius = metrics.dp(40),
                    edgeTreatment = BlurredEdgeTreatment.Unbounded,
                )
                .background(WorkoutV2Cyan.copy(alpha = .20f), CircleShape),
            )
        }
        Row(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .mettleDirectionalBorder(
                    width = metrics.dp(.55),
                    color = Color.White.copy(alpha = if (isCurrent) .17f else .12f),
                    shape = shape,
                ),
        ) {
            Box(
                modifier = Modifier
                    .width(metrics.dp(94))
                    .fillMaxHeight()
                    .background(
                        when {
                            completed -> WorkoutV2Green.copy(alpha = .13f)
                            isCurrent -> WorkoutV2Cyan.copy(alpha = .065f)
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
                    color = if (completed) WorkoutV2Green else WorkoutV2Paper,
                    fontSize = metrics.sp(24),
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(metrics.dp(9)),
                verticalArrangement = Arrangement.spacedBy(metrics.dp(8)),
            ) {
                if (lateralityChoices.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().height(metrics.dp(44)),
                        horizontalArrangement = Arrangement.spacedBy(metrics.dp(7)),
                    ) {
                        lateralityChoices.forEach { choice ->
                            MettleGlassActionButton(
                                onClick = {
                                    draft.laterality = choice
                                    onSaveDraft()
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                accent = draft.laterality == choice,
                                enabled = enabled,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(choice.shortWorkoutLabel(), fontSize = metrics.sp(12), fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                definitions.forEach { definition ->
                    WorkoutV2MetricField(
                        value = draft.value(definition.metric),
                        label = workoutV2MetricFieldLabel(definition, draft.unit(definition.metric)),
                        enabled = enabled,
                        decimal = definition.metric.dimension !in setOf(QuantityDimension.COUNT, QuantityDimension.ORDINAL),
                        modifier = if (standardTwoMetricLayout) {
                            Modifier.fillMaxWidth().weight(1f)
                        } else {
                            Modifier.fillMaxWidth().height(fieldHeight)
                        },
                        onValueChange = { value ->
                            draft.update(definition.metric, workoutV2MetricInput(definition.metric, value))
                        },
                        onDone = {
                            focusManager.clearFocus(force = true)
                            if (ready) onLogSet() else onSaveDraft()
                        },
                        onFocusLost = {
                            if (ready) onLogSet() else onSaveDraft()
                        },
                        onCalculator = if (definition.metric in setOf(PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.ASSISTANCE)) {
                            { onOpenCalculator(definition.metric) }
                        } else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutV2MetricField(
    value: String,
    label: String,
    enabled: Boolean,
    decimal: Boolean,
    modifier: Modifier,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    onFocusLost: () -> Unit,
    onCalculator: (() -> Unit)? = null,
) {
    var hadFocus by remember { mutableStateOf(false) }
    MettleControlGlassSurface(
        modifier = modifier,
        shape = RoundedCornerShape(11.dp),
        tint = WorkoutV2Cyan.copy(alpha = .018f),
        baseColor = WorkoutV2DarkCyan.copy(alpha = .22f),
        borderWidth = .55.dp,
        borderColor = WorkoutV2Cyan.copy(alpha = .18f),
        shadowElevation = .75.dp,
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
                textStyle = TextStyle(color = WorkoutV2Paper, fontSize = 16.sp, lineHeight = 18.sp),
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
                        Text(label, color = WorkoutV2Cyan.copy(alpha = .78f), fontSize = 10.sp, lineHeight = 11.sp)
                        if (value.isEmpty()) Text("00", color = WorkoutV2Paper.copy(alpha = .38f), fontSize = 16.sp) else inner()
                    }
                },
            )
            if (onCalculator != null) {
                IconButton(onClick = onCalculator, enabled = enabled, modifier = Modifier.size(46.dp)) {
                    Icon(MettleIcons.Calculate, "Open load calculator", tint = WorkoutV2Cyan, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private enum class WorkoutSetupDialog { EXERCISE_INSTRUCTIONS, SETUP_INSTRUCTIONS, LINK }

@Composable
private fun WorkoutV2SetupBody(
    exercise: ActiveWorkoutExercise,
    details: WorkoutSetupDetails?,
    loading: Boolean,
    saving: Boolean,
    photoMutationInProgress: Boolean,
    onAddPhoto: () -> Unit,
    onDeletePhoto: (String) -> Unit,
    onSave: (String, String, String) -> Unit,
    onOpenLink: (String) -> Unit,
    onReturn: () -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val fallbackSetup = exercise.details.setupNotes
        .ifBlank { exercise.entity.movementReason }
        .replace(Regex("(?i)^Target\\s+\\d+\\s*[–-]\\s*\\d+\\s+RIR\\.\\s*"), "")
        .trim()
    val fallbackExerciseInstructions = exercise.details.cues.joinToString(" ").ifBlank { exercise.entity.movementReason }
    var editMode by remember(exercise.entity.id) { mutableStateOf(false) }
    var dialog by remember(exercise.entity.id) { mutableStateOf<WorkoutSetupDialog?>(null) }
    var pendingDeletePhoto by remember(exercise.entity.id) { mutableStateOf<String?>(null) }
    var exerciseInstructions by remember(exercise.entity.id) { mutableStateOf(fallbackExerciseInstructions) }
    var setupInstructions by remember(exercise.entity.id) { mutableStateOf(fallbackSetup) }
    var link by remember(exercise.entity.id) { mutableStateOf("") }

    LaunchedEffect(details?.exerciseId, details?.exerciseInstructions, details?.setupInstructions, details?.videoReferenceUrl) {
        if (!editMode && details != null) {
            exerciseInstructions = details.exerciseInstructions.ifBlank { fallbackExerciseInstructions }
            setupInstructions = details.setupInstructions.ifBlank { fallbackSetup }
            link = details.videoReferenceUrl
        }
    }

    Column(Modifier.padding(bottom = metrics.dp(16))) {
        if (loading && details == null) {
            Box(Modifier.fillMaxWidth().height(metrics.dp(72)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WorkoutV2Cyan)
            }
        }

        WorkoutV2EditableTextBlock(
            text = exerciseInstructions.ifBlank { "Add exercise instructions" },
            editMode = editMode,
            onClick = { if (editMode) dialog = WorkoutSetupDialog.EXERCISE_INSTRUCTIONS },
            metrics = metrics,
        )

        details?.reviewReminder?.takeIf { it.isNotBlank() }?.let { reminder ->
            Text(
                reminder,
                modifier = Modifier.padding(horizontal = metrics.dp(16), vertical = metrics.dp(14)),
                color = WorkoutV2Cyan,
                fontSize = metrics.sp(14),
                lineHeight = metrics.sp(20),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.dp(72))
                .padding(horizontal = metrics.dp(16)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Setup Notes →",
                modifier = Modifier.weight(1f),
                color = WorkoutV2Paper,
                fontSize = metrics.sp(24),
                lineHeight = metrics.sp(32),
            )
            if (!editMode) {
                WorkoutV2RoundIconButton(
                    icon = WorkoutLinkIcons.Link,
                    description = "Open exercise link",
                    tint = WorkoutV2Cyan,
                    onClick = { if (link.isNotBlank()) onOpenLink(link) },
                    enabled = link.isNotBlank(),
                    metrics = metrics,
                )
                Spacer(Modifier.width(metrics.dp(10)))
                WorkoutV2RoundIconButton(
                    icon = MettleIcons.Edit,
                    description = "Edit setup notes",
                    tint = WorkoutV2Cyan,
                    onClick = { editMode = true },
                    metrics = metrics,
                )
            } else {
                WorkoutV2RoundIconButton(
                    icon = WorkoutLinkIcons.AddLink,
                    description = "Edit exercise link",
                    tint = WorkoutV2Cyan,
                    onClick = { dialog = WorkoutSetupDialog.LINK },
                    metrics = metrics,
                )
                Spacer(Modifier.width(metrics.dp(8)))
                WorkoutV2RoundIconButton(
                    icon = MettleIcons.Check,
                    description = "Save setup edits",
                    tint = WorkoutV2Green,
                    onClick = {
                        onSave(exerciseInstructions, setupInstructions, link)
                        editMode = false
                    },
                    enabled = !saving,
                    metrics = metrics,
                )
                Spacer(Modifier.width(metrics.dp(8)))
                WorkoutV2RoundIconButton(
                    icon = MettleIcons.Close,
                    description = "Discard setup edits",
                    tint = WorkoutV2Delete,
                    onClick = {
                        exerciseInstructions = details?.exerciseInstructions?.ifBlank { fallbackExerciseInstructions }
                            ?: fallbackExerciseInstructions
                        setupInstructions = details?.setupInstructions?.ifBlank { fallbackSetup } ?: fallbackSetup
                        link = details?.videoReferenceUrl.orEmpty()
                        editMode = false
                        dialog = null
                    },
                    metrics = metrics,
                )
            }
        }

        WorkoutV2EditableTextBlock(
            text = setupInstructions.ifBlank { "Add setup instructions" },
            editMode = editMode,
            onClick = { if (editMode) dialog = WorkoutSetupDialog.SETUP_INSTRUCTIONS },
            metrics = metrics,
        )

        Spacer(Modifier.height(metrics.dp(14)))
        WorkoutV2SetupMediaStrip(
            paths = exercise.details.setupMediaPaths,
            editMode = editMode,
            mutationInProgress = photoMutationInProgress,
            onAddPhoto = onAddPhoto,
            onRequestDelete = { pendingDeletePhoto = it },
            metrics = metrics,
        )
        Spacer(Modifier.height(metrics.dp(20)))
        if (!editMode) {
            Box(Modifier.padding(horizontal = metrics.dp(15))) {
                WorkoutV2CardButton("Return to sets", onReturn, Modifier.fillMaxWidth(), metrics)
            }
        }
    }

    when (dialog) {
        WorkoutSetupDialog.EXERCISE_INSTRUCTIONS -> WorkoutV2TextEditDialog(
            title = "Exercise Instructions",
            value = exerciseInstructions,
            maxCharacters = 1600,
            onDismiss = { dialog = null },
            onSave = {
                exerciseInstructions = it
                dialog = null
            },
        )
        WorkoutSetupDialog.SETUP_INSTRUCTIONS -> WorkoutV2TextEditDialog(
            title = "Setup Instructions",
            value = setupInstructions,
            maxCharacters = 1600,
            onDismiss = { dialog = null },
            onSave = {
                setupInstructions = it
                dialog = null
            },
        )
        WorkoutSetupDialog.LINK -> WorkoutV2LinkEditDialog(
            value = link,
            onDismiss = { dialog = null },
            onSave = {
                link = it
                dialog = null
            },
        )
        null -> Unit
    }

    pendingDeletePhoto?.let { relativePath ->
        AlertDialog(
            onDismissRequest = { if (!photoMutationInProgress) pendingDeletePhoto = null },
            shape = RoundedCornerShape(28.dp),
            containerColor = Color(0xFF272B25),
            tonalElevation = 0.dp,
            title = { Text("Delete setup photo?", color = WorkoutV2Paper, fontSize = 24.sp, lineHeight = 32.sp) },
            text = {
                Text(
                    "This photo will be permanently removed from this exercise.",
                    color = WorkoutV2Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeletePhoto = null },
                    enabled = !photoMutationInProgress,
                ) { Text("Cancel", color = WorkoutV2Paper) }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePhoto(relativePath)
                        pendingDeletePhoto = null
                    },
                    enabled = !photoMutationInProgress,
                ) { Text("Delete", color = WorkoutV2Delete) }
            },
        )
    }
}

@Composable
private fun WorkoutV2EditableTextBlock(
    text: String,
    editMode: Boolean,
    onClick: () -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = metrics.dp(15), vertical = metrics.dp(7))
        .then(if (editMode) Modifier.clickable(onClick = onClick) else Modifier)
    if (editMode) {
        Surface(
            modifier = modifier,
            color = Color(0x4D386667),
            shape = RoundedCornerShape(metrics.dp(8)),
            border = BorderStroke(metrics.dp(.7), WorkoutV2Cyan.copy(alpha = .34f)),
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = metrics.dp(10), vertical = metrics.dp(13)),
                color = WorkoutV2Cyan,
                fontSize = metrics.sp(14),
                lineHeight = metrics.sp(20),
            )
        }
    } else {
        Text(
            text,
            modifier = modifier.padding(horizontal = metrics.dp(1), vertical = metrics.dp(6)),
            color = WorkoutV2Paper,
            fontSize = metrics.sp(14),
            lineHeight = metrics.sp(20),
        )
    }
}

@Composable
private fun WorkoutV2RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit,
    metrics: WorkoutV2Metrics,
    enabled: Boolean = true,
) {
    MettleControlGlassSurface(
        modifier = Modifier.size(metrics.dp(58)),
        shape = CircleShape,
        tint = tint.copy(alpha = .035f),
        baseColor = WorkoutV2DarkCyan.copy(alpha = .22f),
        borderColor = tint.copy(alpha = .35f),
        shadowElevation = metrics.dp(3),
        onClick = if (enabled) onClick else null,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = tint.copy(alpha = if (enabled) 1f else .38f), modifier = Modifier.size(metrics.dp(24)))
        }
    }
}

@Composable
private fun WorkoutV2TextEditDialog(
    title: String,
    value: String,
    maxCharacters: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(title, value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF272B25),
        tonalElevation = 0.dp,
        title = { Text(title, color = WorkoutV2Paper, fontSize = 24.sp, lineHeight = 32.sp) },
        text = {
            TextField(
                value = draft,
                onValueChange = { if (it.length <= maxCharacters) draft = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp),
                label = { Text("Edit") },
                supportingText = { Text("Characters Remaining: ${maxCharacters - draft.length}") },
                minLines = 7,
                maxLines = 12,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF32362F),
                    unfocusedContainerColor = Color(0xFF32362F),
                    focusedTextColor = WorkoutV2Paper,
                    unfocusedTextColor = WorkoutV2Paper,
                    focusedLabelColor = WorkoutV2Muted,
                    unfocusedLabelColor = WorkoutV2Muted,
                    cursorColor = WorkoutV2CyanStrong,
                    focusedIndicatorColor = WorkoutV2Paper,
                ),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard Changes", color = WorkoutV2Delete) } },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("Save", color = WorkoutV2Green) } },
    )
}

@Composable
private fun WorkoutV2LinkEditDialog(
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(value) { mutableStateOf(value) }
    val valid = draft.isBlank() || draft.startsWith("http://", true) || draft.startsWith("https://", true)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = Color(0xFF272B25),
        tonalElevation = 0.dp,
        title = { Text("Edit Link", color = WorkoutV2Paper, fontSize = 24.sp, lineHeight = 32.sp) },
        text = {
            TextField(
                value = draft,
                onValueChange = { draft = it.take(2048) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Edit") },
                placeholder = { Text("Paste Link Here:") },
                singleLine = true,
                isError = !valid,
                supportingText = if (!valid) ({ Text("Use a http:// or https:// link") }) else null,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF32362F),
                    unfocusedContainerColor = Color(0xFF32362F),
                    focusedTextColor = WorkoutV2Paper,
                    unfocusedTextColor = WorkoutV2Paper,
                    focusedLabelColor = WorkoutV2Muted,
                    unfocusedLabelColor = WorkoutV2Muted,
                    cursorColor = WorkoutV2CyanStrong,
                    focusedIndicatorColor = WorkoutV2Paper,
                ),
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Discard", color = WorkoutV2Delete) } },
        confirmButton = {
            TextButton(onClick = { onSave(draft.trim()) }, enabled = valid) {
                Text("Save", color = if (valid) WorkoutV2Green else WorkoutV2Muted)
            }
        },
    )
}

@Composable
private fun WorkoutV2SetupMediaStrip(
    paths: List<String>,
    editMode: Boolean,
    mutationInProgress: Boolean,
    onAddPhoto: () -> Unit,
    onRequestDelete: (String) -> Unit,
    metrics: WorkoutV2Metrics,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = metrics.dp(16)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(10)),
    ) {
        MettleControlGlassSurface(
            modifier = Modifier.width(metrics.dp(56)).height(metrics.dp(196)),
            shape = RoundedCornerShape(metrics.dp(25)),
            tint = WorkoutV2Cyan.copy(alpha = .035f),
            baseColor = WorkoutV2DarkCyan.copy(alpha = .20f),
            enabled = !mutationInProgress,
            borderColor = WorkoutV2Cyan.copy(alpha = .32f),
            onClick = if (mutationInProgress) null else onAddPhoto,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "+",
                    color = WorkoutV2Paper.copy(alpha = if (mutationInProgress) .38f else 1f),
                    fontSize = metrics.sp(24),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        paths.forEach { relativePath ->
            WorkoutV2SetupMediaImage(
                relativePath = relativePath,
                editMode = editMode,
                mutationInProgress = mutationInProgress,
                onRequestDelete = { onRequestDelete(relativePath) },
                metrics = metrics,
            )
        }
        if (paths.isEmpty()) {
            Surface(
                modifier = Modifier.width(metrics.dp(252)).height(metrics.dp(196)),
                shape = RoundedCornerShape(metrics.dp(25)),
                color = Color.White.copy(alpha = .035f),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Setup photos", color = WorkoutV2Muted)
                }
            }
        }
    }
}

@Composable
private fun WorkoutV2SetupMediaImage(
    relativePath: String,
    editMode: Boolean,
    mutationInProgress: Boolean,
    onRequestDelete: () -> Unit,
    metrics: WorkoutV2Metrics,
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, relativePath) {
        value = withContext(Dispatchers.IO) {
            val direct = File(relativePath)
            val candidate = if (direct.isAbsolute) direct else File(context.filesDir, relativePath)
            if (candidate.isFile) BitmapFactory.decodeFile(candidate.absolutePath) else null
        }
    }
    Box(Modifier.width(metrics.dp(252)).height(metrics.dp(196))) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(metrics.dp(25)),
            color = Color.White.copy(alpha = .04f),
        ) {
            if (bitmap != null) {
                Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Setup photo", color = WorkoutV2Muted)
                }
            }
        }
        if (editMode) {
            MettleControlGlassSurface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(metrics.dp(8))
                    .size(metrics.dp(36)),
                shape = CircleShape,
                tint = WorkoutV2Delete.copy(alpha = .08f),
                baseColor = WorkoutV2Ink.copy(alpha = .48f),
                enabled = !mutationInProgress,
                borderWidth = metrics.dp(.55),
                borderColor = WorkoutV2Delete.copy(alpha = .62f),
                shadowElevation = metrics.dp(4),
                onClick = if (mutationInProgress) null else onRequestDelete,
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        MettleIcons.Close,
                        contentDescription = "Delete setup photo",
                        tint = WorkoutV2Delete.copy(alpha = if (mutationInProgress) .38f else 1f),
                        modifier = Modifier.size(metrics.dp(18)),
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutV2Chip(text: String, metrics: WorkoutV2Metrics, success: Boolean = false) {
    MettleMetadataPill(
        label = text,
        height = metrics.dp(32),
        cornerRadius = metrics.dp(8),
        horizontalPadding = metrics.dp(12),
        fill = if (success) WorkoutV2Green.copy(alpha = .028f) else WorkoutV2DarkCyan.copy(alpha = .18f),
        borderWidth = metrics.dp(1),
        borderColor = if (success) WorkoutV2Green.copy(alpha = .28f) else WorkoutV2Cyan.copy(alpha = .20f),
        textColor = if (success) WorkoutV2Green else WorkoutV2Muted,
        fontSize = metrics.sp(14),
        lineHeight = metrics.sp(20),
        shadowBlurRadius = metrics.dp(8),
        shadowOffsetY = metrics.dp(2),
        shadowAlpha = .06f,
        glassBlurRadius = metrics.dp(24),
        refractionDisplacement = metrics.dp(2.5),
        refractionStrength = .10f,
    )
}

@Composable
private fun WorkoutV2ExerciseActions(
    onSwap: () -> Unit,
    onRate: () -> Unit,
    onComplete: () -> Unit,
    allSetsComplete: Boolean,
    metrics: WorkoutV2Metrics,
) {
    Column(Modifier.padding(horizontal = metrics.dp(15), vertical = metrics.dp(16))) {
        WorkoutV2CardButton("Substitute this exercise", onSwap, Modifier.fillMaxWidth(), metrics)
        Spacer(Modifier.height(metrics.dp(12)))
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(12))) {
            WorkoutV2CardButton("Rate exercise", onRate, Modifier.weight(1f), metrics)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (allSetsComplete) {
                    Box(
                        Modifier
                            .fillMaxWidth(.82f)
                            .height(metrics.dp(18))
                            .align(Alignment.BottomCenter)
                            .blur(metrics.dp(12))
                            .background(
                                Brush.horizontalGradient(listOf(Color.Transparent, WorkoutV2Green.copy(alpha = .68f), Color.Transparent)),
                                CircleShape,
                            ),
                    )
                }
                WorkoutV2CardButton("Complete exercise", onComplete, Modifier.fillMaxWidth(), metrics)
            }
        }
    }
}

@Composable
private fun WorkoutV2CompletedActions(onRate: () -> Unit, onUndo: () -> Unit, metrics: WorkoutV2Metrics) {
    Row(
        modifier = Modifier.padding(horizontal = metrics.dp(15), vertical = metrics.dp(20)),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(12)),
    ) {
        WorkoutV2CardButton("Rate exercise", onRate, Modifier.weight(1f), metrics)
        WorkoutV2CardButton("Mark undone", onUndo, Modifier.weight(1f), metrics)
    }
}

@Composable
private fun WorkoutV2CardButton(text: String, onClick: () -> Unit, modifier: Modifier, metrics: WorkoutV2Metrics) {
    MettleGlassActionButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = metrics.dp(48)),
        shadowElevation = metrics.dp(2.6),
        accent = false,
        containerTint = WorkoutV2DarkCyan.copy(alpha = .22f),
        outlineColor = WorkoutV2Cyan.copy(alpha = .24f),
        foregroundColor = WorkoutV2Paper,
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

private fun workoutV2PrescriptionLabels(exercise: ActiveWorkoutExercise): List<String> {
    val targets = exercise.prescription.setPrescriptions.firstOrNull()?.metricTargets.orEmpty()
    if (targets.isEmpty()) return exercise.schema.metrics.filter { it.targetable }.map { "Open ${it.metric.workoutV2DisplayName()}" }
    return targets.map { target ->
        val value = when (target.kind) {
            TargetKind.EXACT -> target.lowerCanonical?.let { target.workoutV2DisplayValue(it) } ?: "Open"
            TargetKind.RANGE -> "${target.lowerCanonical?.let { target.workoutV2DisplayValue(it) } ?: "?"}–${target.upperCanonical?.let { target.workoutV2DisplayValue(it) } ?: "?"}"
            TargetKind.MINIMUM -> "≥${target.lowerCanonical?.let { target.workoutV2DisplayValue(it) } ?: "?"}"
            TargetKind.MAXIMUM -> "≤${target.upperCanonical?.let { target.workoutV2DisplayValue(it) } ?: "?"}"
            TargetKind.OPEN -> "Open"
        }
        "$value ${target.displayUnit.storageValue} ${target.metric.workoutV2DisplayName()}"
    }
}

private fun workoutV2MetricFieldLabel(definition: SchemaMetric, enteredUnit: UnitId): String = when (definition.metric) {
    PerformanceMetric.EXTERNAL_LOAD, PerformanceMetric.ASSISTANCE -> enteredUnit.storageValue
    PerformanceMetric.REPETITIONS -> "Reps"
    PerformanceMetric.DURATION -> if (enteredUnit == UnitId.SECOND) "Seconds" else enteredUnit.storageValue
    PerformanceMetric.DISTANCE -> enteredUnit.storageValue
    else -> "${definition.metric.workoutV2DisplayName()} · ${enteredUnit.storageValue}"
}

private fun dev.kian.mymettle.domain.performance.MetricTarget.workoutV2DisplayValue(canonical: Double): String =
    formatDecimal(UnitConverter.convert(dev.kian.mymettle.domain.performance.Quantity(canonical, canonicalUnit), displayUnit).value)

private fun PerformanceMetric.workoutV2DisplayName(): String = storageValue
    .replace('_', ' ')
    .replaceFirstChar { it.uppercase() }

private fun Laterality.shortWorkoutLabel(): String = when (this) {
    Laterality.LEFT -> "Left"
    Laterality.RIGHT -> "Right"
    Laterality.ALTERNATING -> "Alternating"
    Laterality.BILATERAL -> "Bilateral"
    Laterality.NOT_APPLICABLE -> "N/A"
    Laterality.UNKNOWN -> "Unknown"
}

private fun workoutV2MetricInput(metric: PerformanceMetric, value: String): String {
    if (metric.dimension in setOf(QuantityDimension.COUNT, QuantityDimension.ORDINAL)) {
        return value.filter(Char::isDigit).take(7)
    }
    val allowNegative = metric == PerformanceMetric.INCLINE_GRADE
    val sign = if (allowNegative && value.trimStart().startsWith('-')) "-" else ""
    val cleaned = value.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    val magnitude = if (firstDot < 0) cleaned.take(7) else {
        cleaned.take(firstDot + 1) + cleaned.drop(firstDot + 1).filter(Char::isDigit).take(2)
    }.take(8)
    return (sign + magnitude).take(9)
}
