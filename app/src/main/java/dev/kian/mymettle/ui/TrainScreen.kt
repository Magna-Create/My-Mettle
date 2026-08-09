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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.kian.mymettle.data.local.entity.SessionExerciseEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkout
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.TrainingMode
import dev.kian.mymettle.workout.evaluateLoadExpression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private class TrainSetDraft(set: SetRecordEntity) {
    var load by mutableStateOf(set.load?.let(::formatDecimal).orEmpty())
    var reps by mutableStateOf(set.reps?.toString().orEmpty())
    var durationSeconds by mutableStateOf(set.durationSeconds?.toString().orEmpty())
    var distanceMetres by mutableStateOf(set.distanceMetres?.let(::formatDecimal).orEmpty())
}

private data class LoadCalculatorTarget(
    val exercise: ActiveWorkoutExercise,
    val set: SetRecordEntity,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainScreen(viewModel: N2WorkoutViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = viewModel.uiState
    val drafts = remember { mutableStateMapOf<String, TrainSetDraft>() }
    var calculatorTarget by remember { mutableStateOf<LoadCalculatorTarget?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Android could not open that backup file.")
                }
            }.onSuccess(viewModel::importBackup)
        }
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("My Mettle", fontWeight = FontWeight.SemiBold)
                        Text(
                            state.workout?.let { "${it.session.daySymbol} · ${state.selectedMode.label}" }
                                ?: if (state.hasProgramme) "Train" else "Native migration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading && state.workout == null -> LoadingTrainState(Modifier.padding(innerPadding))

            !state.hasProgramme -> ImportTrainState(
                modifier = Modifier.padding(innerPadding),
                importing = state.importing,
                importSummary = state.importSummary,
                onImport = { importLauncher.launch(arrayOf("application/json", "text/*")) },
            )

            state.workout == null -> ProgrammeTrainState(
                modifier = Modifier.padding(innerPadding),
                state = state,
                onDaySelected = viewModel::selectDay,
                onModeSelected = viewModel::selectMode,
                onStart = viewModel::startSession,
            )

            else -> ActiveTrainState(
                modifier = Modifier.padding(innerPadding),
                workout = state.workout,
                selectedMode = state.selectedMode,
                loading = state.loading,
                drafts = drafts,
                onModeSelected = viewModel::selectMode,
                onOpenCalculator = { exercise, set -> calculatorTarget = LoadCalculatorTarget(exercise, set) },
                onSaveDraft = { exercise, set, draft ->
                    persistDraft(viewModel, exercise, set, draft, logged = set.completedAt != null)
                },
                onLogSet = { exercise, set, draft ->
                    persistDraft(viewModel, exercise, set, draft, logged = true)
                },
                onToggleExercise = viewModel::toggleExercise,
                onCompleteSession = viewModel::completeSession,
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
        val draft = drafts.getOrPut(target.set.id) { TrainSetDraft(target.set) }
        LoadCalculatorDialog(
            initialExpression = draft.load,
            onDismiss = { calculatorTarget = null },
            onUseValue = { value ->
                draft.load = formatDecimal(value)
                persistDraft(viewModel, target.exercise, target.set, draft, logged = target.set.completedAt != null)
                calculatorTarget = null
            },
        )
    }
}

private fun persistDraft(
    viewModel: N2WorkoutViewModel,
    exercise: ActiveWorkoutExercise,
    set: SetRecordEntity,
    draft: TrainSetDraft,
    logged: Boolean,
) {
    viewModel.saveSet(
        exercise = exercise,
        setId = set.id,
        load = draft.load.toDoubleOrNull(),
        reps = draft.reps.toIntOrNull(),
        durationSeconds = draft.durationSeconds.toIntOrNull(),
        distanceMetres = draft.distanceMetres.toDoubleOrNull(),
        logged = logged,
    )
}

@Composable
private fun LoadingTrainState(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ImportTrainState(
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
                    "Choose the JSON backup exported from Lite. Import is one-time while this native database is empty, so an existing native record cannot be overwritten by accident.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                importSummary?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = MaterialTheme.typography.labelLarge)
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
private fun ProgrammeTrainState(
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
        item { Text("Choose the version of today that fits.", style = MaterialTheme.typography.headlineMedium) }
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
                    ModePlanCard(
                        mode = mode,
                        plan = state.plans[mode],
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
private fun ModePlanCard(
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
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
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
private fun ActiveTrainState(
    modifier: Modifier,
    workout: ActiveWorkout,
    selectedMode: TrainingMode,
    loading: Boolean,
    drafts: MutableMap<String, TrainSetDraft>,
    onModeSelected: (TrainingMode) -> Unit,
    onOpenCalculator: (ActiveWorkoutExercise, SetRecordEntity) -> Unit,
    onSaveDraft: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (ActiveWorkoutExercise, SetRecordEntity, TrainSetDraft) -> Unit,
    onToggleExercise: (ActiveWorkoutExercise) -> Unit,
    onCompleteSession: () -> Unit,
) {
    val visible = workout.exercises.filter { exercise ->
        exercise.entity.prescriptionIncluded ||
            exercise.entity.status == "completed" ||
            exercise.sets.any { it.completedAt != null }
    }
    val targets = visible.filter { it.entity.prescriptionIncluded }
    val completedTargets = targets.count { it.entity.status == "completed" }
    val progress = if (targets.isEmpty()) 0f else completedTargets.toFloat() / targets.size

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
            TrainExerciseCard(
                exercise = exercise,
                drafts = drafts,
                sessionActive = workout.session.status == "active",
                onOpenCalculator = { set -> onOpenCalculator(exercise, set) },
                onSaveDraft = { set, draft -> onSaveDraft(exercise, set, draft) },
                onLogSet = { set, draft -> onLogSet(exercise, set, draft) },
                onToggleComplete = { onToggleExercise(exercise) },
            )
        }

        if (workout.session.status == "active") {
            item {
                Button(onClick = onCompleteSession, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Text("Complete session", modifier = Modifier.padding(vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TrainExerciseCard(
    exercise: ActiveWorkoutExercise,
    drafts: MutableMap<String, TrainSetDraft>,
    sessionActive: Boolean,
    onOpenCalculator: (SetRecordEntity) -> Unit,
    onSaveDraft: (SetRecordEntity, TrainSetDraft) -> Unit,
    onLogSet: (SetRecordEntity, TrainSetDraft) -> Unit,
    onToggleComplete: () -> Unit,
) {
    val entity = exercise.entity
    val sets = exercise.sets
        .filter { it.setIndex < entity.prescribedSets || it.completedAt != null }
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
            Text(prescriptionSummary(entity), style = MaterialTheme.typography.titleSmall)
            previous?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Previous: ${setSummary(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            sets.forEachIndexed { index, set ->
                val draft = drafts.getOrPut(set.id) { TrainSetDraft(set) }
                MetricSetRow(
                    displayIndex = index + 1,
                    entity = entity,
                    set = set,
                    draft = draft,
                    enabled = sessionActive,
                    onOpenCalculator = { onOpenCalculator(set) },
                    onSaveDraft = { onSaveDraft(set, draft) },
                    onLogSet = { onLogSet(set, draft) },
                )
                if (index < sets.lastIndex) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onToggleComplete, enabled = sessionActive, modifier = Modifier.fillMaxWidth()) {
                Text(if (completed) "Reopen exercise" else "Complete exercise")
            }
        }
    }
}

@Composable
private fun MetricSetRow(
    displayIndex: Int,
    entity: SessionExerciseEntity,
    set: SetRecordEntity,
    draft: TrainSetDraft,
    enabled: Boolean,
    onOpenCalculator: () -> Unit,
    onSaveDraft: () -> Unit,
    onLogSet: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val metric = entity.trackingMetricSnapshot
    val needsExternalLoad = metric == "load_reps" && entity.loadRelationshipSnapshot != "bodyweight"
    val needsReps = metric == "load_reps" || metric == "reps"
    val needsDuration = metric == "duration"
    val needsDistance = metric == "distance"
    val ready = when {
        needsExternalLoad && draft.load.toDoubleOrNull() == null -> false
        needsReps && draft.reps.toIntOrNull() == null -> false
        needsDuration && draft.durationSeconds.toIntOrNull()?.let { it > 0 } != true -> false
        needsDistance && draft.distanceMetres.toDoubleOrNull()?.let { it > 0.0 } != true -> false
        else -> true
    }

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
                if (needsReps) {
                    MetricNumberField(
                        value = draft.reps,
                        onValueChange = { draft.reps = it.filter(Char::isDigit).take(3); onSaveDraft() },
                        label = "Reps",
                        enabled = enabled,
                        decimal = false,
                        modifier = Modifier.weight(1f),
                        onDone = { focusManager.clearFocus(); onSaveDraft() },
                    )
                }
                if (needsDuration) {
                    MetricNumberField(
                        value = draft.durationSeconds,
                        onValueChange = { draft.durationSeconds = it.filter(Char::isDigit).take(5); onSaveDraft() },
                        label = "Seconds",
                        enabled = enabled,
                        decimal = false,
                        modifier = Modifier.weight(1f),
                        onDone = { focusManager.clearFocus(); onSaveDraft() },
                    )
                }
                if (needsDistance) {
                    MetricNumberField(
                        value = draft.distanceMetres,
                        onValueChange = { draft.distanceMetres = decimalInput(it, 7); onSaveDraft() },
                        label = "Metres",
                        enabled = enabled,
                        decimal = true,
                        modifier = Modifier.weight(1f),
                        onDone = { focusManager.clearFocus(); onSaveDraft() },
                    )
                }
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
private fun MetricNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    decimal: Boolean,
    modifier: Modifier,
    onDone: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

@Composable
private fun LoadCalculatorDialog(
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
                            resolved?.let { "= ${formatDecimal(it)}" } ?: "—",
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
        confirmButton = { Button(onClick = { resolved?.let(onUseValue) }, enabled = resolved != null) { Text("Use value") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun prescriptionSummary(entity: SessionExerciseEntity): String = when (entity.trackingMetricSnapshot) {
    "load_reps", "reps" -> "${entity.prescribedSets} × ${entity.repMin}–${entity.repMax} reps · ${entity.restSeconds}s rest"
    "duration" -> "${entity.prescribedSets} timed set${if (entity.prescribedSets == 1) "" else "s"} · ${entity.restSeconds}s rest"
    "distance" -> "${entity.prescribedSets} distance set${if (entity.prescribedSets == 1) "" else "s"} · ${entity.restSeconds}s rest"
    else -> "${entity.prescribedSets} set${if (entity.prescribedSets == 1) "" else "s"} · ${entity.restSeconds}s rest"
}

private fun setSummary(set: SetRecordEntity): String = buildString {
    if (set.load != null) append("${formatDecimal(set.load)} ${set.unit}")
    if (set.load != null && set.reps != null) append(" × ")
    if (set.reps != null) append("${set.reps} reps")
    if (set.durationSeconds != null) append("${set.durationSeconds}s")
    if (set.distanceMetres != null) append("${formatDecimal(set.distanceMetres)} m")
    if (isEmpty()) append("—")
}

private fun decimalInput(value: String, maxLength: Int): String {
    val filtered = value.filter { it.isDigit() || it == '.' }.take(maxLength)
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered else filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).replace(".", "")
}

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
