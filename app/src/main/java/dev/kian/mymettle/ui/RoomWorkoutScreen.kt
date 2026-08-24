package dev.kian.mymettle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.domain.performance.PerformanceMetric
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.PerformanceSetRecord
import dev.kian.mymettle.workout.TrainingMode
import dev.kian.mymettle.workout.evaluateLoadExpression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

private class RoomSetDraft(set: PerformanceSetRecord) {
    var load by mutableStateOf(set.load?.let(::formatLoad).orEmpty())
    var reps by mutableStateOf(set.reps?.toString().orEmpty())
}

private data class CalculatorTarget(
    val exercise: ActiveWorkoutExercise,
    val set: PerformanceSetRecord,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomWorkoutScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: N2WorkoutViewModel = viewModel(
        factory = remember(context) { N2WorkoutViewModelFactory(context) },
    )
    val state = viewModel.uiState
    val focusManager = LocalFocusManager.current
    val setDrafts = remember { mutableStateMapOf<String, RoomSetDraft>() }

    var calculatorTarget by remember { mutableStateOf<CalculatorTarget?>(null) }

    var timerVisible by remember { mutableStateOf(false) }
    var timerMinimised by remember { mutableStateOf(false) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerExerciseName by remember { mutableStateOf("") }
    var timerTotalSeconds by remember { mutableIntStateOf(0) }
    var timerRemainingSeconds by remember { mutableIntStateOf(0) }
    var confirmEndTimer by remember { mutableStateOf(false) }

    fun startRest(exerciseName: String, seconds: Int) {
        focusManager.clearFocus(force = true)
        timerExerciseName = exerciseName
        timerTotalSeconds = seconds.coerceAtLeast(1)
        timerRemainingSeconds = seconds.coerceAtLeast(1)
        timerVisible = true
        timerMinimised = false
        timerRunning = true
        confirmEndTimer = false
    }

    LaunchedEffect(timerRunning, timerVisible) {
        while (timerRunning && timerVisible && timerRemainingSeconds > 0) {
            delay(1_000)
            timerRemainingSeconds = max(0, timerRemainingSeconds - 1)
        }
        if (timerVisible && timerRemainingSeconds == 0) timerRunning = false
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Android could not open that backup file.")
                    }
                }.onSuccess(viewModel::importBackup)
            }
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("My Mettle", fontWeight = FontWeight.SemiBold)
                        val workout = state.workout
                        Text(
                            when {
                                workout != null -> "${workout.session.daySymbol} · ${state.selectedMode.label}"
                                state.hasProgramme -> "Native workout"
                                else -> "Native migration"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    if (timerVisible && timerMinimised) {
                        AssistChip(
                            onClick = { timerMinimised = false },
                            label = { Text(if (timerRemainingSeconds == 0) "Ready" else formatTime(timerRemainingSeconds)) },
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading && state.workout == null -> LoadingScreen(Modifier.padding(innerPadding))
            !state.hasProgramme -> ImportScreen(
                modifier = Modifier.padding(innerPadding),
                importing = state.importing,
                importSummary = state.importSummary,
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            )
            state.workout == null -> ProgrammeRoomScreen(
                modifier = Modifier.padding(innerPadding),
                state = state,
                onDaySelected = viewModel::selectDay,
                onModeSelected = viewModel::selectMode,
                onStart = viewModel::startSession,
            )
            else -> ActiveRoomWorkoutScreen(
                modifier = Modifier.padding(innerPadding),
                workout = state.workout,
                selectedMode = state.selectedMode,
                loading = state.loading,
                sessionCompleted = state.sessionCompleted,
                drafts = setDrafts,
                onModeSelected = viewModel::selectMode,
                onOpenCalculator = { exercise, set -> calculatorTarget = CalculatorTarget(exercise, set) },
                onPersistDraft = { exercise, set, draft ->
                    viewModel.saveSet(
                        exercise = exercise,
                        setId = set.id,
                        load = draft.load.toDoubleOrNull(),
                        reps = draft.reps.toIntOrNull(),
                        logged = set.completedAt != null,
                    )
                },
                onLogSet = { exercise, set, draft ->
                    viewModel.saveSet(
                        exercise = exercise,
                        setId = set.id,
                        load = draft.load.toDoubleOrNull(),
                        reps = draft.reps.toIntOrNull(),
                        logged = true,
                        onSaved = { startRest(exercise.entity.exerciseNameSnapshot, exercise.entity.restSeconds) },
                    )
                },
                onToggleExercise = viewModel::toggleExercise,
                onCompleteSession = {
                    timerVisible = false
                    timerRunning = false
                    viewModel.completeSession()
                },
                onLeaveCompleted = viewModel::leaveCompletedSession,
            )
        }
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("My Mettle couldn't do that") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }

    calculatorTarget?.let { target ->
        val draft = setDrafts.getOrPut(target.set.id) { RoomSetDraft(target.set) }
        CalculatorDialog(
            initialExpression = draft.load,
            onDismiss = { calculatorTarget = null },
            onUseValue = { value ->
                draft.load = formatLoad(value)
                viewModel.saveSet(
                    exercise = target.exercise,
                    setId = target.set.id,
                    load = value,
                    reps = draft.reps.toIntOrNull(),
                    logged = target.set.completedAt != null,
                )
                calculatorTarget = null
            },
        )
    }

    if (timerVisible && !timerMinimised) {
        RestTimerSheet(
            exerciseName = timerExerciseName,
            remainingSeconds = timerRemainingSeconds,
            running = timerRunning,
            completed = timerRemainingSeconds == 0,
            onAdjust = { delta ->
                timerRemainingSeconds = max(0, timerRemainingSeconds + delta)
                timerTotalSeconds = max(1, timerTotalSeconds + delta)
                if (timerRemainingSeconds == 0) timerRunning = false
            },
            onTogglePause = { if (timerRemainingSeconds > 0) timerRunning = !timerRunning },
            onMinimise = { timerMinimised = true },
            onEnd = { confirmEndTimer = true },
            onDismissReady = {
                timerVisible = false
                timerRunning = false
            },
        )
    }

    if (confirmEndTimer) {
        AlertDialog(
            onDismissRequest = { confirmEndTimer = false },
            title = { Text("End rest timer?") },
            text = { Text("Your logged set stays saved. Only the current rest countdown will end.") },
            confirmButton = {
                Button(onClick = {
                    timerVisible = false
                    timerRunning = false
                    confirmEndTimer = false
                }) { Text("End timer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEndTimer = false }) { Text("Keep resting") }
            },
        )
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImportScreen(
    modifier: Modifier,
    importing: Boolean,
    importSummary: String?,
    onImport: () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ElevatedCard(modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Bring over My Mettle Lite", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                Text(
                    "The native database is empty. Choose the JSON backup exported from Lite; import is deliberately one-time so an existing native record cannot be overwritten by accident.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (importSummary != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(importSummary, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(18.dp))
                Button(onClick = onImport, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Importing…")
                    } else {
                        Text("Choose Lite JSON backup")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgrammeRoomScreen(
    modifier: Modifier,
    state: N2WorkoutUiState,
    onDaySelected: (String) -> Unit,
    onModeSelected: (TrainingMode) -> Unit,
    onStart: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Text("Choose the version of today that fits.", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            Text("Programme day", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ψ", "φ", "π", "&").forEach { day ->
                    FilterChip(
                        selected = state.selectedDay == day,
                        onClick = { onDaySelected(day) },
                        label = { Text(day) },
                    )
                }
            }
        }
        item {
            Text("Session mode", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TrainingMode.entries.forEach { mode ->
                    val plan = state.plans[mode]
                    ModeCard(
                        mode = mode,
                        plan = plan,
                        selected = state.selectedMode == mode,
                        onClick = { onModeSelected(mode) },
                    )
                }
            }
        }
        item {
            val selectedPlan = state.plans[state.selectedMode]
            Button(
                onClick = onStart,
                enabled = selectedPlan?.exercises?.isNotEmpty() == true && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Begin ${state.selectedDay} · ${state.selectedMode.label}", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: TrainingMode,
    plan: NativeWorkoutPlan?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(mode.code, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(mode.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    plan?.let { "${it.exercises.size} exercises · ${it.workingSetCount} working sets" } ?: "—",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActiveRoomWorkoutScreen(
    modifier: Modifier,
    workout: ActiveWorkout,
    selectedMode: TrainingMode,
    loading: Boolean,
    sessionCompleted: Boolean,
    drafts: MutableMap<String, RoomSetDraft>,
    onModeSelected: (TrainingMode) -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, PerformanceSetRecord) -> Unit,
    onPersistDraft: (ActiveWorkoutExercise, PerformanceSetRecord, RoomSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, PerformanceSetRecord, RoomSetDraft) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onCompleteSession: () -> Unit,
    onLeaveCompleted: () -> Unit,
) {
    val visible = workout.exercises.filter { exercise ->
        exercise.entity.prescriptionIncluded ||
            exercise.entity.status == "completed" ||
            exercise.sets.any { it.completedAt != null }
    }
    val target = visible.filter { it.entity.prescriptionIncluded }
    val completedTarget = target.count { it.entity.status == "completed" }
    val progress = if (target.isEmpty()) 0f else completedTarget.toFloat() / target.size

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrainingMode.entries.forEach { candidate ->
                    FilterChip(
                        selected = selectedMode == candidate,
                        enabled = workout.session.status == "active" && !loading,
                        onClick = { onModeSelected(candidate) },
                        label = { Text("${candidate.code} · ${candidate.label}") },
                    )
                }
            }
            if (loading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        items(visible, key = { it.entity.id }) { exercise ->
            RoomExerciseCard(
                exercise = exercise,
                drafts = drafts,
                sessionActive = workout.session.status == "active",
                onOpenCalculator = { set -> onOpenCalculator(exercise, set) },
                onPersistDraft = { set, draft -> onPersistDraft(exercise, set, draft) },
                onLogSet = { set, draft -> onLogSet(exercise, set, draft) },
                onToggleComplete = { onToggleExercise(exercise) },
            )
        }

        item {
            if (!sessionCompleted && workout.session.status == "active") {
                Button(onClick = onCompleteSession, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Complete session", modifier = Modifier.padding(vertical = 6.dp))
                }
            } else {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                        Text("Session captured", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("${workout.session.daySymbol} · ${selectedMode.label} · $completedTarget/${target.size} target exercises completed")
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onLeaveCompleted, modifier = Modifier.fillMaxWidth()) {
                            Text("Back to programme")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomExerciseCard(
    exercise: ActiveWorkoutExercise,
    drafts: MutableMap<String, RoomSetDraft>,
    sessionActive: Boolean,
    onOpenCalculator: (PerformanceSetRecord) -> Unit,
    onPersistDraft: (PerformanceSetRecord, RoomSetDraft) -> Unit,
    onLogSet: (PerformanceSetRecord, RoomSetDraft) -> Unit,
    onToggleComplete: () -> Unit,
) {
    val entity = exercise.entity
    val prescribed = exercise.sets
        .filter { it.setIndex < exercise.prescription.sets || it.completedAt != null }
        .sortedBy { it.setIndex }
    val previous = exercise.previousCompletedSets.firstOrNull()
    val completed = entity.status == "completed"

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (completed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entity.importanceSnapshot.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                    Text(entity.exerciseNameSnapshot, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                AssistChip(
                    onClick = onToggleComplete,
                    enabled = sessionActive,
                    label = { Text(if (completed) "Completed" else if (entity.prescriptionIncluded) "Active" else "Extra") },
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${exercise.prescription.sets} set${if (exercise.prescription.sets == 1) "" else "s"}${exercise.prescription.repRange?.let { " × ${it.first}–${it.last} reps" }.orEmpty()} · ${entity.restSeconds}s rest",
                style = MaterialTheme.typography.titleSmall,
            )
            previous?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Previous: ${it.load?.let(::formatLoad) ?: "—"} ${it.unit} × ${it.reps ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            prescribed.forEachIndexed { index, set ->
                val draft = drafts.getOrPut(set.id) { RoomSetDraft(set) }
                RoomSetEntryRow(
                    displayIndex = index + 1,
                    exercise = exercise,
                    set = set,
                    draft = draft,
                    enabled = sessionActive,
                    onOpenCalculator = { onOpenCalculator(set) },
                    onPersistDraft = { onPersistDraft(set, draft) },
                    onLogSet = { onLogSet(set, draft) },
                )
                if (index < prescribed.lastIndex) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onToggleComplete, enabled = sessionActive, modifier = Modifier.fillMaxWidth()) {
                Text(if (completed) "Reopen exercise" else "Complete exercise")
            }
        }
    }
}

@Composable
private fun RoomSetEntryRow(
    displayIndex: Int,
    exercise: ActiveWorkoutExercise,
    set: PerformanceSetRecord,
    draft: RoomSetDraft,
    enabled: Boolean,
    onOpenCalculator: () -> Unit,
    onPersistDraft: () -> Unit,
    onLogSet: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val metrics = exercise.schema.metrics.mapTo(hashSetOf()) { it.metric }
    val needsExternalLoad = PerformanceMetric.EXTERNAL_LOAD in metrics || PerformanceMetric.ASSISTANCE in metrics
    val supportsReps = PerformanceMetric.REPETITIONS in metrics
    val ready = (!needsExternalLoad || draft.load.toDoubleOrNull() != null) && (!supportsReps || draft.reps.toIntOrNull() != null)

    Surface(
        color = if (set.completedAt != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Set $displayIndex${if (set.kind == "additional") " · additional" else ""}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (needsExternalLoad) {
                    OutlinedButton(onClick = onOpenCalculator, enabled = enabled, modifier = Modifier.weight(1f)) {
                        Text("${draft.load.ifEmpty { "—" }} ${set.unit}")
                    }
                }
                if (supportsReps) {
                    OutlinedTextField(
                        value = draft.reps,
                        onValueChange = { value ->
                            draft.reps = value.filter(Char::isDigit).take(3)
                            onPersistDraft()
                        },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        label = { Text("Reps") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        keyboardActions = KeyboardActions(onDone = {
                            focusManager.clearFocus()
                            onPersistDraft()
                        }),
                    )
                }
            }
            if (!supportsReps) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "${exercise.schema.family.storageValue.replace('_', ' ')} entry is preserved in the generic performance model.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    onLogSet()
                },
                enabled = enabled && ready,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (set.completedAt != null) "Logged · restart rest" else "Log set")
            }
        }
    }
}

@Composable
private fun CalculatorDialog(
    initialExpression: String,
    onDismiss: () -> Unit,
    onUseValue: (Double) -> Unit,
) {
    var expression by remember(initialExpression) { mutableStateOf(initialExpression) }
    val resolved = runCatching { evaluateLoadExpression(expression) }.getOrNull()
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "+", "×2"),
        listOf("(", ")", "⌫", "C"),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load calculator") },
        text = {
            Column {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = MaterialTheme.shapes.large) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.End) {
                        Text(expression.ifEmpty { "0" }, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            resolved?.let { "= ${formatLoad(it)}" } ?: "—",
                            color = if (resolved == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                rows.forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    expression = when (key) {
                                        "C" -> ""
                                        "⌫" -> expression.dropLast(1)
                                        "×2" -> if (expression.isEmpty()) "2" else "$expression×2"
                                        else -> expression + key
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp),
                            ) { Text(key) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { resolved?.let(onUseValue) }, enabled = resolved != null) { Text("Use value") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestTimerSheet(
    exerciseName: String,
    remainingSeconds: Int,
    running: Boolean,
    completed: Boolean,
    onAdjust: (Int) -> Unit,
    onTogglePause: () -> Unit,
    onMinimise: () -> Unit,
    onEnd: () -> Unit,
    onDismissReady: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onMinimise) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(if (completed) "Ready" else "Rest", style = MaterialTheme.typography.labelLarge)
            Text(exerciseName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(14.dp))
            Text(formatTime(remainingSeconds), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            if (!completed) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = { onAdjust(-15) }, modifier = Modifier.weight(1f)) { Text("−15") }
                    Button(onClick = onTogglePause, modifier = Modifier.weight(1.2f)) { Text(if (running) "Pause" else "Resume") }
                    FilledTonalButton(onClick = { onAdjust(15) }, modifier = Modifier.weight(1f)) { Text("+15") }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = onMinimise, modifier = Modifier.fillMaxWidth()) { Text("Minimise") }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onEnd) { Text("End timer") }
            } else {
                Button(onClick = onDismissReady, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

private fun formatLoad(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
