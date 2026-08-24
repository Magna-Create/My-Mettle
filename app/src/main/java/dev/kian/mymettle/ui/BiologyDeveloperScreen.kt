package dev.kian.mymettle.ui

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.developer.BiologyTaskPhase
import dev.kian.mymettle.developer.NBio6VerificationCheck
import dev.kian.mymettle.workout.NativeWorkoutPlan
import dev.kian.mymettle.workout.TrainingMode
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiologyDeveloperScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel: BiologyDeveloperViewModel = viewModel(
        factory = remember(context) { BiologyDeveloperViewModelFactory(context) },
    )
    val state = viewModel.uiState
    var confirmReset by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(export.first) }
                        ?: error("Android could not open the selected diagnostic file.")
                }
            }.onSuccess {
                if (export.second) viewModel.markNBio6Exported() else viewModel.markExported()
            }
                .onFailure(viewModel::reportError)
        }
    }
    val liteBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val name = selectedFileName(context, uri)
                    val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                        ?: error("Android could not open the selected Lite backup.")
                    name to json
                }
            }.onSuccess { (name, json) -> viewModel.verifyLiteBackup(name, json) }
                .onFailure(viewModel::reportError)
        }
    }

    LaunchedEffect(state.resetComplete) {
        if (state.resetComplete) (context as? Activity)?.recreate()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Biological developer tools", fontWeight = FontWeight.SemiBold)
                        Text("N‑BIO‑6 observability and closure", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = { TextButton(onClick = viewModel::refresh) { Text("Refresh") } },
            )
        },
    ) { innerPadding ->
        val snapshot = state.snapshot
        if (state.loading && snapshot == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else if (snapshot != null) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    DebugCard("Runtime foundation") {
                        val reference = snapshot.reference
                        DebugLine("Room", "v${reference.schemaVersion}")
                        DebugLine("Reference status", if (
                            reference.muscleCount == 142 && reference.segmentCount == 164 && reference.priorCount == 66
                        ) "Valid" else "Unexpected counts")
                        DebugLine("Muscles", reference.muscleCount.toString())
                        DebugLine("Segments", reference.segmentCount.toString())
                        DebugLine("Selected/policy priors", reference.priorCount.toString())
                        DebugLine("Reference profile", reference.referenceProfile?.let { "${it.id} · v${it.version}" } ?: "Missing")
                        DebugLine("Current routine", snapshot.routineVersionId ?: "None imported")
                    }
                }

                item {
                    DebugCard("Lifecycle and diagnostics") {
                        Button(
                            onClick = viewModel::recompute,
                            enabled = state.task.phase != BiologyTaskPhase.RUNNING && snapshot.routineVersionId != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Recompute biological state") }
                        OutlinedButton(
                            onClick = {
                                runCatching { viewModel.diagnosticJson() }
                                    .onSuccess { json ->
                                        pendingExport = json to false
                                        exportLauncher.launch("my-mettle-n-bio-${Instant.now().epochSecond}.json")
                                    }
                                    .onFailure(viewModel::reportError)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Export diagnostic JSON") }
                        OutlinedButton(
                            onClick = { confirmReset = true },
                            enabled = state.task.phase != BiologyTaskPhase.RUNNING,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Reset Native development database") }
                        if (state.task.phase != BiologyTaskPhase.IDLE) {
                            HorizontalDivider()
                            DebugLine("Task", state.task.label ?: state.task.phase.name)
                            state.task.detail?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (state.task.phase != BiologyTaskPhase.RUNNING) {
                                TextButton(onClick = viewModel::dismissTaskResult) { Text("Dismiss task result") }
                            }
                        }
                    }
                }

                item {
                    DebugCard("N‑BIO‑6 device acceptance") {
                        Text(
                            "Runs production Room, profile authoring, workout, history and conservative inference paths in isolated databases. Your Native history is not modified.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::runNBio6DeviceVerification,
                            enabled = !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.nBio6VerificationRunning) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                                Text("Running on-device checks…")
                            } else {
                                Text("Run automated Room and flow checks")
                            }
                        }
                        state.nBio6DeviceReport?.let { report ->
                            HorizontalDivider()
                            DebugLine("Automated result", if (report.passed) "PASS" else "FAIL")
                            DebugLine("Scalar checks", "${report.checks.count { it.passed }}/${report.checks.size} passed")
                            report.checks.forEach { VerificationCheckResult(it) }
                            HorizontalDivider()
                            Text("Temporal evidence", fontWeight = FontWeight.SemiBold)
                            DebugLine(
                                "Temporal checks",
                                "${report.temporalChecks.count { it.passed }}/${report.temporalChecks.size} passed",
                            )
                            report.temporalChecks.forEach { VerificationCheckResult(it) }
                        }
                        HorizontalDivider()
                        Text(
                            "Select an actual Lite schema-6 backup to translate, persist and inspect in another isolated Room database. Photo bytes are validated without writing files; app settings are untouched.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { liteBackupLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            enabled = !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.nBio6LiteVerificationRunning) "Validating Lite backup…" else "Validate a real Lite backup")
                        }
                        state.nBio6LiteReport?.let { report ->
                            DebugLine("Lite backup", if (report.passed) "PASS" else "FAIL")
                            DebugLine("File", report.fileName)
                            Text(report.detail, color = if (report.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            if (report.passed) {
                                DebugLine("Exercises", report.exercises.toString())
                                DebugLine("Sessions", report.sessions.toString())
                                DebugLine("Sets / observations", "${report.sets} / ${report.observations}")
                                DebugLine("Metric values", report.metricValues.toString())
                                DebugLine("Photos validated", report.setupPhotosValidated.toString())
                                report.sampleEvidence.take(5).forEach { sample ->
                                    Text(sample, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching { viewModel.nBio6ClosureJson() }
                                    .onSuccess { json ->
                                        pendingExport = json to true
                                        exportLauncher.launch("my-mettle-n-bio-6-closure-${Instant.now().epochSecond}.json")
                                    }
                                    .onFailure(viewModel::reportError)
                            },
                            enabled = state.nBio6DeviceReport != null || state.nBio6LiteReport != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Export N-BIO-6 closure report") }
                    }
                }

                if (snapshot.days.isNotEmpty()) {
                    item {
                        DebugCard("Programme resolver preview") {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                snapshot.days.forEach { day ->
                                    FilterChip(
                                        selected = state.selectedDay == day.day,
                                        onClick = { viewModel.selectDay(day.day) },
                                        label = { Text(day.day) },
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TrainingMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = state.selectedMode == mode,
                                        onClick = { viewModel.selectMode(mode) },
                                        label = { Text("${mode.code} · ${mode.label}") },
                                    )
                                }
                            }
                            val day = snapshot.days.firstOrNull { it.day == state.selectedDay }
                            val plan = day?.plans?.get(state.selectedMode)
                            if (plan == null) Text("No plan resolves for this day/mode.") else PlanDebug(plan)
                        }
                    }
                }

                item {
                    val inference = snapshot.inference
                    DebugCard("Latest inference run") {
                        if (inference == null) {
                            Text("No inference run exists. Complete a session, then use Recompute biological state.")
                        } else {
                            DebugLine("Run", inference.run.id.value)
                            DebugLine("Calculated", inference.run.calculatedAt.toString())
                            DebugLine("Evidence sets", inference.run.evidenceSetCount.toString())
                            DebugLine("Stimulus estimates", inference.stimulusEstimates.size.toString())
                            DebugLine("Muscle states", inference.muscleStates.size.toString())
                            DebugLine("Performance anchors", inference.exerciseTranslationStates.size.toString())
                            HorizontalDivider()
                            Text("Performance anchors", style = MaterialTheme.typography.titleSmall)
                            inference.exerciseTranslationStates.take(40).forEach { state ->
                                val label = snapshot.executionProfileLabels[state.executionProfileVersionId.value]
                                    ?: state.executionProfileVersionId.value
                                Text(label, fontWeight = FontWeight.Medium)
                                Text(
                                    state.anchors.joinToString(" · ") { anchor ->
                                        "${anchor.metric.storageValue}: ${formatDebug(anchor.estimate.value)} ${anchor.canonicalUnit} · set ${anchor.sourceSetRecordId.takeLast(8)}"
                                    }.ifEmpty { "no metric anchors" } + " · ${state.sampleCount} samples",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            HorizontalDivider()
                            Text("Muscle states with evidence", style = MaterialTheme.typography.titleSmall)
                            val evidencedStates = inference.muscleStates.filter { it.evidenceCount > 0 }
                            if (evidencedStates.isEmpty()) Text("No recruited muscle evidence in this run.")
                            evidencedStates.take(40).forEach { muscle ->
                                DebugLine(
                                    muscle.segmentId.value,
                                    "${muscle.evidenceCount} estimates · development ${formatDebug(muscle.developmentIndex.value)}",
                                )
                            }
                            HorizontalDivider()
                            Text("Stimulus estimates (first 40)", style = MaterialTheme.typography.titleSmall)
                            inference.stimulusEstimates.take(40).forEach { estimate ->
                                Text(
                                    "${estimate.segmentId.value} · ${formatDebug(estimate.estimatedStimulus)} · " +
                                        "confidence ${formatDebug(estimate.confidence)} · set ${estimate.setRecordId.takeLast(8)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset Native development database?") },
            text = { Text("This deletes the disposable Native database, including imported workouts and inference runs. Lite is unaffected.") },
            confirmButton = {
                Button(onClick = { confirmReset = false; viewModel.resetDatabase() }) { Text("Reset database") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("Developer action failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            title = { Text("Done") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text("OK") } },
        )
    }
}

@Composable
private fun DebugCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PlanDebug(plan: NativeWorkoutPlan) {
    val included = plan.targetResolutions.count { it.included }
    DebugLine("Targets", "$included included · ${plan.targetResolutions.size - included} excluded")
    DebugLine("Constraint", "${plan.constraints.exerciseBudget} exercises · ${plan.constraints.workingSetBudget} sets")
    DebugLine("Minimum dose", "${plan.constraints.minimumSetsPerExercise} sets per movement")
    DebugLine("Priority floor", formatDebug(plan.constraints.targetPriorityFloor))
    plan.constraints.timeBudgetSeconds?.let { DebugLine("Time budget", "${it / 60} min") }
    HorizontalDivider()
    Text("Target resolution", style = MaterialTheme.typography.titleSmall)
    plan.targetResolutions.forEach { target ->
        AssistChip(
            onClick = {},
            label = { Text("${if (target.included) "IN" else "OUT"} · ${target.target.segmentId.value} · ${formatDebug(target.resolvedPriority)}") },
        )
    }
    HorizontalDivider()
    Text("Selected prescriptions", style = MaterialTheme.typography.titleSmall)
    plan.exercises.forEach { exercise ->
        Text("${exercise.name} · ${exercise.prescription.sets} sets", fontWeight = FontWeight.Medium)
        val targets = exercise.prescription.setPrescriptions.firstOrNull()?.metricTargets.orEmpty()
        Text(
            targets.joinToString(" · ") { target ->
                val range = listOfNotNull(target.lowerCanonical, target.upperCanonical).joinToString("–", transform = ::formatDebug)
                    .ifEmpty { "open" }
                "$range ${target.displayUnit.storageValue} ${target.metric.storageValue} (${target.evidence?.source ?: "preference/open"})"
            }.ifEmpty { "Open metric prescription" } + " · ${exercise.movementReason}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    HorizontalDivider()
    Text("All candidates", style = MaterialTheme.typography.titleSmall)
    plan.candidateDecisions.forEach { candidate ->
        Text(
            "${if (candidate.selected) "SELECTED" else "REJECTED"} · ${candidate.exerciseName}",
            fontWeight = FontWeight.Medium,
        )
        Text(
            "${candidate.decisionReason} · coverage ${candidate.targetCoverage.keys.joinToString().ifEmpty { "unresolved" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatDebug(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.3f".format(value).trimEnd('0').trimEnd('.')

@Composable
private fun VerificationCheckResult(check: NBio6VerificationCheck) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            "${if (check.passed) "PASS" else "FAIL"} · ${check.title}",
            fontWeight = FontWeight.Medium,
            color = if (check.passed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Text(
            "${check.detail} · ${check.durationMillis} ms",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun selectedFileName(context: android.content.Context, uri: Uri): String {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return uri.lastPathSegment ?: "selected-lite-backup.json"
}
