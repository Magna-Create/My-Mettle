package dev.kian.mymettle.ui

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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.workout.BasePrescription
import dev.kian.mymettle.workout.ExerciseImportance
import dev.kian.mymettle.workout.ModeExercise
import dev.kian.mymettle.workout.PlannedExercise
import dev.kian.mymettle.workout.TrainingMode
import dev.kian.mymettle.workout.WorkoutModePolicy
import dev.kian.mymettle.workout.evaluateLoadExpression
import kotlinx.coroutines.delay
import kotlin.math.max

private data class DemoExercise(
    val id: String,
    val name: String,
    val plannedLoad: Double,
    val cue: String,
    val importance: ExerciseImportance,
    val legacyA: BasePrescription,
    val legacyB: BasePrescription,
    val legacyC: BasePrescription,
)

private class DemoSetState(defaultLoad: Double) {
    var load by mutableStateOf(formatLoad(defaultLoad))
    var reps by mutableStateOf("")
    var logged by mutableStateOf(false)
}

private class DemoExerciseState(defaultLoad: Double) {
    val sets = mutableStateListOf<DemoSetState>()
    var completed by mutableStateOf(false)

    init {
        sets += DemoSetState(defaultLoad)
    }

    fun ensureSetCount(count: Int, defaultLoad: Double) {
        while (sets.size < count) sets += DemoSetState(defaultLoad)
    }
}

private val demoExercises = listOf(
    DemoExercise(
        id = "seated-cable-deadlift",
        name = "Seated Cable Deadlift",
        plannedLoad = 50.0,
        cue = "Drive the hips forwards, then fold back under control.",
        importance = ExerciseImportance.PRINCIPAL,
        legacyA = BasePrescription(true, 3, 6, 8, 180),
        legacyB = BasePrescription(true, 2, 6, 8, 180),
        legacyC = BasePrescription(true, 1, 6, 8, 180),
    ),
    DemoExercise(
        id = "incline-curl",
        name = "Incline Bicep Curl",
        plannedLoad = 12.0,
        cue = "Keep the upper arm behind the torso; do not chase the shoulder forwards.",
        importance = ExerciseImportance.CORE,
        legacyA = BasePrescription(true, 3, 8, 12, 120),
        legacyB = BasePrescription(true, 2, 8, 12, 120),
        legacyC = BasePrescription(true, 1, 8, 12, 120),
    ),
    DemoExercise(
        id = "pin-dip",
        name = "Pin Dip",
        plannedLoad = 25.0,
        cue = "Own the bottom position before driving through the handles.",
        importance = ExerciseImportance.CORE,
        legacyA = BasePrescription(true, 3, 6, 10, 150),
        legacyB = BasePrescription(true, 2, 6, 10, 150),
        legacyC = BasePrescription(true, 1, 6, 10, 150),
    ),
    DemoExercise(
        id = "leg-extension",
        name = "Leg Extension",
        plannedLoad = 45.0,
        cue = "Keep the hip still and finish the knee extension deliberately.",
        importance = ExerciseImportance.CORE,
        legacyA = BasePrescription(true, 3, 8, 12, 120),
        legacyB = BasePrescription(true, 2, 8, 12, 120),
        legacyC = BasePrescription(true, 1, 8, 12, 120),
    ),
    DemoExercise(
        id = "lateral-raise",
        name = "Cable Lateral Raise",
        plannedLoad = 7.5,
        cue = "Lead with the elbow and stop before the upper trap takes over.",
        importance = ExerciseImportance.ACCESSORY,
        legacyA = BasePrescription(true, 3, 10, 15, 90),
        legacyB = BasePrescription(true, 2, 10, 15, 90),
        legacyC = BasePrescription(true, 1, 10, 15, 90),
    ),
    DemoExercise(
        id = "calf-raise",
        name = "Standing Calf Raise",
        plannedLoad = 55.0,
        cue = "Pause in the stretched position; do not bounce out of the bottom.",
        importance = ExerciseImportance.ACCESSORY,
        legacyA = BasePrescription(true, 3, 8, 12, 90),
        legacyB = BasePrescription(true, 2, 8, 12, 90),
        legacyC = BasePrescription(true, 1, 8, 12, 90),
    ),
)

private val modeSourceExercises = demoExercises.mapIndexed { index, exercise ->
    ModeExercise(
        id = exercise.id,
        ordinal = index,
        importance = exercise.importance,
        legacyA = exercise.legacyA,
        legacyB = exercise.legacyB,
        legacyC = exercise.legacyC,
        payload = exercise,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun N2WorkoutScreen() {
    var selectedDay by remember { mutableStateOf("φ") }
    var selectedMode by remember { mutableStateOf(TrainingMode.B) }
    var sessionActive by remember { mutableStateOf(false) }
    var sessionComplete by remember { mutableStateOf(false) }
    val exerciseStates = remember { mutableStateMapOf<String, DemoExerciseState>() }
    val focusManager = LocalFocusManager.current

    var calculatorTarget by remember { mutableStateOf<Pair<DemoSetState, String>?>(null) }

    var timerVisible by remember { mutableStateOf(false) }
    var timerMinimised by remember { mutableStateOf(false) }
    var timerRunning by remember { mutableStateOf(false) }
    var timerExerciseName by remember { mutableStateOf("") }
    var timerTotalSeconds by remember { mutableIntStateOf(0) }
    var timerRemainingSeconds by remember { mutableIntStateOf(0) }
    var confirmEndTimer by remember { mutableStateOf(false) }

    fun currentPlan(mode: TrainingMode = selectedMode): List<PlannedExercise<DemoExercise>> =
        WorkoutModePolicy.plan(modeSourceExercises, mode)

    fun reconcileMode(mode: TrainingMode) {
        selectedMode = mode
        currentPlan(mode).forEach { planned ->
            val state = exerciseStates.getOrPut(planned.id) { DemoExerciseState(planned.payload.plannedLoad) }
            state.ensureSetCount(planned.prescription.sets, planned.payload.plannedLoad)
        }
    }

    fun startSession() {
        reconcileMode(selectedMode)
        sessionComplete = false
        sessionActive = true
    }

    fun startRest(exerciseName: String, seconds: Int) {
        focusManager.clearFocus(force = true)
        timerExerciseName = exerciseName
        timerTotalSeconds = seconds
        timerRemainingSeconds = seconds
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

    val activePlan = currentPlan()
    val completedExercises = activePlan.count { exerciseStates[it.id]?.completed == true }
    val exerciseProgress = if (activePlan.isEmpty()) 0f else completedExercises.toFloat() / activePlan.size

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("My Mettle", fontWeight = FontWeight.SemiBold)
                        Text(
                            if (sessionActive) "$selectedDay · ${selectedMode.label}" else "Native workout · N2",
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
        if (!sessionActive) {
            ProgrammeScreen(
                modifier = Modifier.padding(innerPadding),
                selectedDay = selectedDay,
                onDaySelected = { selectedDay = it },
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
                onStart = ::startSession,
            )
        } else {
            WorkoutScreen(
                modifier = Modifier.padding(innerPadding),
                day = selectedDay,
                mode = selectedMode,
                plan = activePlan,
                exerciseStates = exerciseStates,
                progress = exerciseProgress,
                sessionComplete = sessionComplete,
                onModeSelected = { reconcileMode(it) },
                onOpenCalculator = { setState -> calculatorTarget = setState to setState.load },
                onLogSet = { planned, set ->
                    set.logged = true
                    startRest(planned.payload.name, planned.prescription.restSeconds)
                },
                onToggleExercise = { planned ->
                    exerciseStates[planned.id]?.let { it.completed = !it.completed }
                },
                onCompleteSession = {
                    focusManager.clearFocus(force = true)
                    timerVisible = false
                    timerRunning = false
                    sessionComplete = true
                },
                onLeaveSession = {
                    sessionActive = false
                    sessionComplete = false
                    timerVisible = false
                    timerRunning = false
                },
            )
        }
    }

    calculatorTarget?.let { (set, initialExpression) ->
        CalculatorDialog(
            initialExpression = initialExpression,
            onDismiss = { calculatorTarget = null },
            onUseValue = { value ->
                set.load = formatLoad(value)
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
            text = { Text("This avoids the accidental one-tap cancellation from the Legacy timer.") },
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
private fun ProgrammeScreen(
    modifier: Modifier,
    selectedDay: String,
    onDaySelected: (String) -> Unit,
    selectedMode: TrainingMode,
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
            Spacer(Modifier.height(8.dp))
            Text(
                "A–D are resolved from one adjustable policy. Changing the policy later will not rewrite completed workout snapshots.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        item {
            Text("Programme day", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ψ", "φ", "π", "&").forEach { day ->
                    FilterChip(
                        selected = selectedDay == day,
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
                    val plan = WorkoutModePolicy.plan(modeSourceExercises, mode)
                    val setCount = plan.sumOf { it.prescription.sets }
                    ElevatedCard(
                        onClick = { onModeSelected(mode) },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (selectedMode == mode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                modifier = Modifier.size(48.dp),
                                shape = CircleShape,
                                color = if (selectedMode == mode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(mode.code, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(mode.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Text("${plan.size} exercises · $setCount working sets", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        item {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text("Begin $selectedDay · ${selectedMode.label}", modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun WorkoutScreen(
    modifier: Modifier,
    day: String,
    mode: TrainingMode,
    plan: List<PlannedExercise<DemoExercise>>,
    exerciseStates: Map<String, DemoExerciseState>,
    progress: Float,
    sessionComplete: Boolean,
    onModeSelected: (TrainingMode) -> Unit,
    onOpenCalculator: (DemoSetState) -> Unit,
    onLogSet: (PlannedExercise<DemoExercise>, DemoSetState) -> Unit,
    onToggleExercise: (PlannedExercise<DemoExercise>) -> Unit,
    onCompleteSession: () -> Unit,
    onLeaveSession: () -> Unit,
) {
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
                        selected = mode == candidate,
                        onClick = { onModeSelected(candidate) },
                        label = { Text("${candidate.code} · ${candidate.label}") },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "You can move mode mid-session. Entered sets stay attached to their exercise; an easier mode simply stops showing movements it no longer requires.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        items(plan, key = { it.id }) { planned ->
            val state = exerciseStates.getValue(planned.id)
            ExerciseWorkoutCard(
                planned = planned,
                state = state,
                onOpenCalculator = onOpenCalculator,
                onLogSet = { set -> onLogSet(planned, set) },
                onToggleComplete = { onToggleExercise(planned) },
            )
        }

        item {
            if (!sessionComplete) {
                Button(onClick = onCompleteSession, modifier = Modifier.fillMaxWidth()) {
                    Text("Complete session", modifier = Modifier.padding(vertical = 6.dp))
                }
            } else {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(22.dp)) {
                        Text("Session captured", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("$day · ${mode.label}. Celebration scoring will become mode-relative when this slice is wired to Room history.")
                        Spacer(Modifier.height(16.dp))
                        FilledTonalButton(onClick = onLeaveSession, modifier = Modifier.fillMaxWidth()) { Text("Back to programme") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseWorkoutCard(
    planned: PlannedExercise<DemoExercise>,
    state: DemoExerciseState,
    onOpenCalculator: (DemoSetState) -> Unit,
    onLogSet: (DemoSetState) -> Unit,
    onToggleComplete: () -> Unit,
) {
    val exercise = planned.payload
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (state.completed) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(planned.importance.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                    Text(exercise.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                }
                AssistChip(onClick = onToggleComplete, label = { Text(if (state.completed) "Completed" else "Active") })
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${planned.prescription.sets} × ${planned.prescription.repMin}–${planned.prescription.repMax} reps · ${planned.prescription.restSeconds}s rest",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(exercise.cue, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            repeat(planned.prescription.sets) { index ->
                val set = state.sets[index]
                SetEntryRow(
                    index = index,
                    set = set,
                    onOpenCalculator = { onOpenCalculator(set) },
                    onLogSet = { onLogSet(set) },
                )
                if (index < planned.prescription.sets - 1) Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = onToggleComplete, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.completed) "Reopen exercise" else "Complete exercise")
            }
        }
    }
}

@Composable
private fun SetEntryRow(
    index: Int,
    set: DemoSetState,
    onOpenCalculator: () -> Unit,
    onLogSet: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    Surface(
        color = if (set.logged) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Set ${index + 1}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onOpenCalculator, modifier = Modifier.weight(1f)) {
                    Text("${set.load} kg")
                }
                OutlinedTextField(
                    value = set.reps,
                    onValueChange = { value -> set.reps = value.filter(Char::isDigit).take(3); set.logged = false },
                    modifier = Modifier.weight(1f),
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                )
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = {
                    focusManager.clearFocus(force = true)
                    onLogSet()
                },
                enabled = set.load.toDoubleOrNull() != null && set.reps.toIntOrNull() != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (set.logged) "Logged · restart rest" else "Log set")
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
                            resolved?.let { "= ${formatLoad(it)} kg" } ?: "—",
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

private fun formatLoad(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
