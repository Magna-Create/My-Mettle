package dev.kian.mymettle.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.ExerciseSwapOption
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val WorkoutInk = Color(0xFF11140F)
private val WorkoutPaper = Color(0xFFE1E4DA)
private val WorkoutPaperMuted = Color(0xFFC3C8BB)
private val WorkoutCyan = Color(0xFFBBEBED)
private val WorkoutCyanStrong = Color(0xFFA0CFD0)
private val WorkoutDarkCyan = Color(0xFF002021)
private val WorkoutGreen = Color(0xFFC3EFAD)
private val WorkoutGreenDark = Color(0xFF436833)
private val WorkoutDelete = Color(0xFFFFB4AB)
private val WorkoutCardShape = RoundedCornerShape(22.dp)

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
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    onDismissSheet: () -> Unit,
    onShowDelete: () -> Unit,
    onCompleteSession: () -> Unit,
    onDiscardSession: () -> Unit,
) {
    val workout = requireNotNull(state.workout)
    val focused = workout.exercises.firstOrNull { it.entity.id == state.focusedExerciseId }
        ?: workout.exercises.firstOrNull { it.entity.status != "completed" }
        ?: workout.exercises.first()

    Box(modifier = Modifier.fillMaxSize().background(WorkoutInk)) {
        when {
            state.swapTarget != null -> WorkoutSubstitutionScreen(
                current = state.swapTarget,
                options = state.swapOptions,
                loading = state.loadingSwapOptions,
                onDismiss = onDismissSwap,
                onSelect = onSelectSwap,
            )

            state.workoutSurface == WorkoutSurface.QUICK_SELECT -> WorkoutQuickSelectScreen(
                workout = workout,
                focusedId = focused.entity.id,
                onSelect = onShowSets,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            state.workoutSurface == WorkoutSurface.SETUP -> WorkoutSetupScreen(
                workout = workout,
                exercise = focused,
                onReturn = { onShowSets(focused.entity.id) },
                onSwap = { onSwapExercise(focused) },
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            else -> WorkoutSetsScreen(
                workout = workout,
                focusedId = focused.entity.id,
                drafts = drafts,
                loading = state.loading,
                onFocusExercise = onShowSets,
                onShowSetup = onShowSetup,
                onOpenCalculator = onOpenCalculator,
                onSaveDraft = onSaveDraft,
                onLogSet = onLogSet,
                onSwap = onSwapExercise,
                onToggleExercise = onToggleExercise,
                onRateExercise = onRateExercise,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )
        }

        if (state.workoutSurface == WorkoutSurface.FINISH) {
            FinishWorkoutSheet(
                destructive = false,
                onDismiss = onDismissSheet,
                onComplete = onCompleteSession,
                onDelete = onShowDelete,
            )
        }
        if (state.workoutSurface == WorkoutSurface.DELETE_CONFIRM) {
            FinishWorkoutSheet(
                destructive = true,
                onDismiss = onDismissSheet,
                onComplete = onCompleteSession,
                onDelete = onDiscardSession,
            )
        }
    }
}

@Composable
private fun WorkoutSetsScreen(
    workout: ActiveWorkout,
    focusedId: String,
    drafts: MutableMap<String, TrainSetDraft>,
    loading: Boolean,
    onFocusExercise: (String?) -> Unit,
    onShowSetup: () -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, SetRecordEntity) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onSwap: (ActiveWorkoutExercise) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onRateExercise: (ActiveWorkoutExercise) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val visible = workout.exercises.filter {
        it.entity.prescriptionIncluded || it.entity.status == "completed" || it.sets.any { set -> set.completedAt != null }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item {
            WorkoutHeader(
                workout = workout,
                focusedId = focusedId,
                onProgressPoint = onFocusExercise,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )
        }
        items(visible, key = { it.entity.id }) { exercise ->
            WorkoutExerciseCard(
                exercise = exercise,
                focused = exercise.entity.id == focusedId,
                drafts = drafts,
                enabled = workout.session.status == "active" && !loading,
                onFocus = { onFocusExercise(exercise.entity.id) },
                onSetup = { onFocusExercise(exercise.entity.id); onShowSetup() },
                onOpenCalculator = { set -> onOpenCalculator(exercise, set) },
                onSaveDraft = { set, draft -> onSaveDraft(exercise, set, draft) },
                onLogSet = { set, draft -> onLogSet(exercise, set, draft) },
                onSwap = { onSwap(exercise) },
                onToggleExercise = { onToggleExercise(exercise) },
                onRateExercise = { onRateExercise(exercise) },
            )
        }
    }
}

@Composable
private fun WorkoutHeader(
    workout: ActiveWorkout,
    focusedId: String,
    onProgressPoint: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.width(128.dp)) {
            Text("My Mettle", color = WorkoutPaper, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
            Text("Workout Session", color = WorkoutPaperMuted, fontSize = 13.sp)
        }
        WorkoutWaveProgress(
            workout = workout,
            focusedId = focusedId,
            onProgressPoint = onProgressPoint,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        MettleControlGlassSurface(
            modifier = Modifier.width(80.dp).height(42.dp),
            shape = CircleShape,
            tint = Color.White.copy(alpha = 0.02f),
        ) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Workout settings",
                    onClick = onOpenSettings,
                    iconSize = androidx.compose.ui.unit.DpSize(17.dp, 16.dp),
                )
                MettleGlassIconTouchTarget(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account",
                    onClick = onOpenAccount,
                    iconSize = androidx.compose.ui.unit.DpSize(16.dp, 16.dp),
                )
            }
        }
    }
}

@Composable
private fun WorkoutWaveProgress(
    workout: ActiveWorkout,
    focusedId: String,
    onProgressPoint: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeIndex = workout.exercises.indexOfFirst { it.entity.id == focusedId }.coerceAtLeast(0)
    MettleControlGlassSurface(
        modifier = modifier.height(42.dp),
        shape = CircleShape,
        tint = Color.White.copy(alpha = 0.018f),
    ) {
        Box(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val y = size.height / 2f
                val path = Path().apply {
                    moveTo(0f, y)
                    cubicTo(size.width * .20f, y, size.width * .20f, y - 7f, size.width * .35f, y - 7f)
                    cubicTo(size.width * .50f, y - 7f, size.width * .50f, y + 7f, size.width * .65f, y + 7f)
                    cubicTo(size.width * .80f, y + 7f, size.width * .80f, y, size.width, y)
                }
                drawPath(path, WorkoutPaperMuted.copy(alpha = .75f), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
                val count = workout.exercises.size.coerceAtLeast(1)
                workout.exercises.forEachIndexed { index, exercise ->
                    val x = if (count == 1) size.width / 2f else size.width * index / (count - 1f)
                    drawCircle(
                        color = when {
                            exercise.entity.status == "completed" -> WorkoutGreen
                            index == activeIndex -> WorkoutCyan
                            else -> WorkoutPaperMuted
                        },
                        radius = if (index == activeIndex) 4.5.dp.toPx() else 3.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
            Row(Modifier.fillMaxSize()) {
                workout.exercises.forEach { exercise ->
                    Box(
                        Modifier.weight(1f).fillMaxSize().clickable { onProgressPoint(exercise.entity.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutExerciseCard(
    exercise: ActiveWorkoutExercise,
    focused: Boolean,
    drafts: MutableMap<String, TrainSetDraft>,
    enabled: Boolean,
    onFocus: () -> Unit,
    onSetup: () -> Unit,
    onOpenCalculator: (SetRecordEntity) -> Unit,
    onSaveDraft: (SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (SetRecordEntity, TrainSetDraft) -> Unit,
    onSwap: () -> Unit,
    onToggleExercise: () -> Unit,
    onRateExercise: () -> Unit,
) {
    val entity = exercise.entity
    val completed = entity.status == "completed"
    val sets = exercise.sets
        .filter { it.setIndex < entity.prescribedSets || it.completedAt != null }
        .sortedBy { it.setIndex }
    val logged = sets.count { it.completedAt != null }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onFocus),
        shape = WorkoutCardShape,
        color = if (completed) WorkoutGreen else WorkoutPaper,
        shadowElevation = if (focused) 5.dp else 1.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(modifier = Modifier.size(56.dp), shape = CircleShape, color = if (completed) WorkoutGreenDark else WorkoutCyanStrong) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            MettleIcons.SportsMartialArts,
                            contentDescription = null,
                            tint = if (completed) WorkoutGreen else WorkoutDarkCyan,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        entity.exerciseNameSnapshot,
                        color = WorkoutInk,
                        fontSize = 24.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entity.executionProfileNameSnapshot,
                        color = WorkoutInk.copy(alpha = .65f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(if (completed) "✓" else "↗", color = WorkoutDarkCyan, fontSize = 24.sp)
            }

            Spacer(Modifier.height(13.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                WorkoutChip(entity.importanceSnapshot.replaceFirstChar { it.uppercase() })
                WorkoutChip("${entity.repMin}–${entity.repMax} reps")
                WorkoutChip("${entity.restSeconds}s rest")
                entity.prescribedLoad?.let { WorkoutChip("${trimNumber(it)} load") }
                if (completed) WorkoutChip("$logged/${entity.prescribedSets} Sets Complete", success = true)
            }

            if (completed) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WorkoutCardButton("Rate exercise", onRateExercise, Modifier.weight(1f))
                    WorkoutCardButton("Mark undone", onToggleExercise, Modifier.weight(1f))
                }
            } else {
                Spacer(Modifier.height(15.dp))
                sets.forEachIndexed { index, set ->
                    val draft = drafts.getOrPut(set.id) { TrainSetDraft(set) }
                    WorkoutSetRow(
                        displayIndex = index,
                        exercise = exercise,
                        set = set,
                        draft = draft,
                        enabled = enabled,
                        isCurrent = focused && set.completedAt == null && sets.take(index).all { it.completedAt != null },
                        onOpenCalculator = { onOpenCalculator(set) },
                        onSaveDraft = { onSaveDraft(set, draft) },
                        onLogSet = { onLogSet(set, draft) },
                    )
                    if (index != sets.lastIndex) Spacer(Modifier.height(9.dp))
                }
                Spacer(Modifier.height(15.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WorkoutCardButton("Setup", onSetup, Modifier.weight(1f))
                    WorkoutCardButton("Substitute", onSwap, Modifier.weight(1f))
                    WorkoutCardButton("Complete", onToggleExercise, Modifier.weight(1f))
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
    onOpenCalculator: () -> Unit,
    onSaveDraft: () -> Unit,
    onLogSet: () -> Unit,
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
        "reps" -> draft.reps.toIntOrNull()?.let { it >= 0 } == true
        else -> draft.reps.toIntOrNull() != null &&
            (exercise.entity.loadRelationshipSnapshot == "bodyweight" || draft.load.toDoubleOrNull() != null)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(width = 50.dp, height = 52.dp).clickable(enabled = enabled && ready, onClick = {
                focusManager.clearFocus(force = true)
                onLogSet()
            }),
            shape = RoundedCornerShape(16.dp),
            color = when {
                set.completedAt != null -> WorkoutGreenDark
                isCurrent -> WorkoutCyanStrong
                else -> WorkoutPaperMuted.copy(alpha = .58f)
            },
            shadowElevation = if (isCurrent) 8.dp else 0.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(label, color = if (set.completedAt != null) WorkoutGreen else WorkoutInk, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(10.dp))
        if (metric != "reps") {
            WorkoutMetricField(
                value = fieldOne,
                label = fieldOneLabel,
                enabled = enabled,
                decimal = metric != "duration",
                modifier = Modifier.weight(1f),
                onClick = if (metric == "load_reps") onOpenCalculator else null,
                onValueChange = { value ->
                    when (metric) {
                        "duration" -> draft.durationSeconds = value.filter(Char::isDigit).take(5)
                        "distance" -> draft.distanceMetres = workoutDecimalInput(value)
                        else -> draft.load = workoutDecimalInput(value)
                    }
                    onSaveDraft()
                },
                onDone = onSaveDraft,
            )
            Spacer(Modifier.width(10.dp))
        }
        if (needsReps) {
            WorkoutMetricField(
                value = draft.reps,
                label = "Reps",
                enabled = enabled,
                decimal = false,
                modifier = Modifier.weight(1f),
                onValueChange = { draft.reps = it.filter(Char::isDigit).take(3); onSaveDraft() },
                onDone = {
                    focusManager.clearFocus(force = true)
                    if (ready) onLogSet() else onSaveDraft()
                },
            )
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
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.height(52.dp).then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(alpha = .52f),
        border = androidx.compose.foundation.BorderStroke(1.dp, WorkoutInk.copy(alpha = .14f)),
    ) {
        Column(Modifier.padding(horizontal = 13.dp, vertical = 6.dp), verticalArrangement = Arrangement.Center) {
            Text(label, color = WorkoutInk.copy(alpha = .58f), fontSize = 10.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled && onClick == null,
                singleLine = true,
                textStyle = TextStyle(color = WorkoutInk, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                decorationBox = { inner -> if (value.isEmpty()) Text("—", color = WorkoutInk.copy(alpha = .32f)) else inner() },
            )
        }
    }
}

@Composable
private fun WorkoutChip(text: String, success: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (success) WorkoutGreenDark else WorkoutCyanStrong.copy(alpha = .58f),
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            color = if (success) WorkoutGreen else WorkoutDarkCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun WorkoutCardButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 42.dp).clickable(onClick = onClick),
        shape = CircleShape,
        color = WorkoutDarkCyan,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = WorkoutCyan, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}

@Composable
private fun WorkoutSetupScreen(
    workout: ActiveWorkout,
    exercise: ActiveWorkoutExercise,
    onReturn: () -> Unit,
    onSwap: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        item { WorkoutHeader(workout, exercise.entity.id, {}, onOpenSettings, onOpenAccount) }
        item {
            Surface(shape = WorkoutCardShape, color = WorkoutPaper) {
                Column(Modifier.padding(18.dp)) {
                    Text(exercise.entity.exerciseNameSnapshot, color = WorkoutInk, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(9.dp))
                    Text("Setup", color = WorkoutInk.copy(alpha = .58f), fontSize = 13.sp)
                    Spacer(Modifier.height(13.dp))
                    val notes = exercise.details.setupNotes.ifBlank { exercise.entity.movementReason }
                    Text(notes.ifBlank { "Set the equipment to a stable, comfortable position and use the cues below." }, color = WorkoutInk, fontSize = 16.sp, lineHeight = 22.sp)
                    exercise.details.cues.forEach { cue ->
                        Spacer(Modifier.height(8.dp))
                        Text("• $cue", color = WorkoutInk.copy(alpha = .82f), fontSize = 14.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.height(18.dp))
                    SetupMediaStrip(exercise.details.setupMediaPaths)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WorkoutCardButton("Return to sets", onReturn, Modifier.weight(1f))
                        WorkoutCardButton("Substitute", onSwap, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupMediaStrip(paths: List<String>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        paths.forEach { path -> SetupMediaImage(path) }
        Surface(modifier = Modifier.width(122.dp).aspectRatio(1.2f), shape = RoundedCornerShape(18.dp), color = WorkoutPaperMuted.copy(alpha = .55f)) {
            Box(contentAlignment = Alignment.Center) { Text("＋\nAdd", color = WorkoutInk, textAlign = TextAlign.Center, fontSize = 15.sp) }
        }
    }
}

@Composable
private fun SetupMediaImage(relativePath: String) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, relativePath) {
        value = withContext(Dispatchers.IO) {
            val direct = File(relativePath)
            val candidate = if (direct.isAbsolute) direct else File(context.filesDir, relativePath)
            if (candidate.isFile) BitmapFactory.decodeFile(candidate.absolutePath) else null
        }
    }
    Surface(modifier = Modifier.width(160.dp).aspectRatio(1.35f), shape = RoundedCornerShape(18.dp), color = WorkoutPaperMuted.copy(alpha = .55f)) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Box(contentAlignment = Alignment.Center) { Text("Setup photo", color = WorkoutInk.copy(alpha = .55f)) }
        }
    }
}

@Composable
private fun WorkoutQuickSelectScreen(
    workout: ActiveWorkout,
    focusedId: String,
    onSelect: (String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WorkoutHeader(workout, focusedId, onSelect, onOpenSettings, onOpenAccount) }
        item { Text("Quick Select", color = WorkoutPaper, fontSize = 31.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
        items(workout.exercises, key = { it.entity.id }) { exercise ->
            val done = exercise.entity.status == "completed"
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(exercise.entity.id) },
                shape = WorkoutCardShape,
                color = if (done) WorkoutGreen else WorkoutPaper,
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = if (done) WorkoutGreenDark else WorkoutCyanStrong) {
                        Box(contentAlignment = Alignment.Center) { Text("${exercise.entity.position + 1}", color = if (done) WorkoutGreen else WorkoutDarkCyan, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.entity.exerciseNameSnapshot, color = WorkoutInk, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${exercise.entity.prescribedSets} sets · ${exercise.entity.repMin}–${exercise.entity.repMax} reps", color = WorkoutInk.copy(alpha = .58f), fontSize = 12.sp)
                    }
                    Text("↔", color = WorkoutDarkCyan, fontSize = 25.sp)
                }
            }
        }
    }
}

@Composable
private fun WorkoutSubstitutionScreen(
    current: ActiveWorkoutExercise,
    options: List<ExerciseSwapOption>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (ExerciseSwapOption) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = options.filter {
        query.isBlank() || it.exerciseName.contains(query, ignoreCase = true) || it.executionProfileName.contains(query, ignoreCase = true)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 48.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Substitute", color = WorkoutPaper, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                    Text(current.entity.exerciseNameSnapshot, color = WorkoutPaperMuted, fontSize = 14.sp)
                }
                TextButton(onClick = onDismiss) { Text("Close", color = WorkoutCyan) }
            }
        }
        item {
            Surface(shape = CircleShape, color = WorkoutPaper) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⌕", color = WorkoutInk, fontSize = 25.sp)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = WorkoutInk, fontSize = 17.sp),
                        singleLine = true,
                        decorationBox = { inner -> if (query.isEmpty()) Text("Search compatible exercises", color = WorkoutInk.copy(alpha = .48f)) else inner() },
                    )
                }
            }
        }
        if (loading) {
            item { Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = WorkoutCyan) } }
        } else {
            items(filtered, key = { it.executionProfileId }) { option ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(option) },
                    shape = WorkoutCardShape,
                    color = WorkoutPaper,
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = WorkoutCyanStrong) {
                            Box(contentAlignment = Alignment.Center) { Text("${options.indexOf(option) + 1}", color = WorkoutDarkCyan, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(option.exerciseName, color = WorkoutInk, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(option.executionProfileName, color = WorkoutInk.copy(alpha = .58f), fontSize = 12.sp, maxLines = 1)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                WorkoutChip("${option.prescription.sets} sets")
                                WorkoutChip("${option.prescription.repRange.first}–${option.prescription.repRange.last} reps")
                            }
                        }
                        Text("↔", color = WorkoutDarkCyan, fontSize = 25.sp)
                    }
                }
            }
            if (filtered.isEmpty()) {
                item { Text("No target-compatible replacements match that search.", color = WorkoutPaperMuted, modifier = Modifier.padding(20.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishWorkoutSheet(
    destructive: Boolean,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = WorkoutPaper,
        contentColor = WorkoutInk,
        dragHandle = {
            Surface(modifier = Modifier.padding(top = 11.dp).size(width = 42.dp, height = 5.dp), shape = CircleShape, color = WorkoutInk.copy(alpha = .25f)) {}
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (destructive) "Delete workout?" else "Finish workout?",
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = WorkoutInk,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (destructive) "This removes the active session from progress and insights." else "Complete the session, keep training, or delete it.",
                color = WorkoutInk.copy(alpha = .62f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FinishBubble("×", "Keep training", WorkoutCyanStrong, onDismiss)
                if (!destructive) FinishBubble("✓", "Complete", WorkoutGreen, onComplete)
                FinishBubble("⌫", if (destructive) "Delete now" else "Delete", WorkoutDelete, onDelete)
            }
        }
    }
}

@Composable
private fun FinishBubble(symbol: String, label: String, colour: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(modifier = Modifier.size(72.dp).clickable(onClick = onClick), shape = CircleShape, color = colour) {
            Box(contentAlignment = Alignment.Center) { Text(symbol, color = WorkoutInk, fontSize = 31.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = WorkoutInk, fontSize = 12.sp)
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
