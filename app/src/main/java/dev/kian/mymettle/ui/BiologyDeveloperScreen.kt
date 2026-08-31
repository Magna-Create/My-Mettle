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
    val nBio7BExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.nBio7BAcceptanceJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected N-BIO-7B report file.")
                }
            }.onSuccess { viewModel.markNBio7BAcceptanceExported() }
                .onFailure(viewModel::reportError)
        }
    }
    val adaptiveInferenceExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.adaptiveInferenceJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected adaptive-inference report file.")
                }
            }.onSuccess { viewModel.markAdaptiveInferenceExported() }
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
                        Text("N‑BIO observability, validation and acceptance", style = MaterialTheme.typography.labelMedium)
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
                    DebugCard("N-BIO-7A.5 context interpretation") {
                        val contextSnapshot = state.contextSnapshot
                        if (contextSnapshot == null) {
                            Text("Context diagnostics have not loaded yet.")
                        } else {
                            DebugLine("Tag schema", "v${contextSnapshot.tagSchemaVersion}")
                            DebugLine("Prompt API", contextSnapshot.capabilities.promptApiStatus.storageValue)
                            DebugLine(
                                "Structured Output",
                                contextSnapshot.capabilities.structuredOutputAvailable?.toString() ?: "Unknown",
                            )
                            DebugLine(
                                "System Instructions",
                                contextSnapshot.capabilities.systemInstructionAvailable?.toString() ?: "Unknown",
                            )
                            DebugLine("Base model", contextSnapshot.capabilities.baseModelName ?: "Unavailable / not exposed")
                            DebugLine("Selected interpreter", contextSnapshot.selectedInterpreter)
                            DebugLine("Recent interpretation runs", contextSnapshot.recentRuns.size.toString())
                            contextSnapshot.capabilities.probeFailure?.let { failure ->
                                DebugLine("Capability probe", "Fallback-safe · $failure")
                            }
                            contextSnapshot.recentRuns.take(8).forEach { run ->
                                HorizontalDivider()
                                DebugLine("Source", "${run.sourceScope} · ${run.sourceTextHash.take(12)}…")
                                DebugLine("Interpreter", "${run.interpreterKind} · ${run.interpreterImplementationVersion}")
                                DebugLine("Outcome", run.executionOutcome)
                                run.actualBaseModelName?.let { model -> DebugLine("Runtime model", model) }
                                run.fallbackReason?.let { reason -> DebugLine("Fallback", reason) }
                                DebugLine("Annotations", run.annotations.size.toString())
                                run.annotations.take(8).forEach { annotation ->
                                    Text(
                                        "${annotation.tagId} · ${annotation.valueType} · ${annotation.inferenceEligibility}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                "Raw note text is intentionally omitted from diagnostics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item {
                    DebugCard("Lifecycle and diagnostics") {
                        Button(
                            onClick = viewModel::recompute,
                            enabled = state.task.phase != BiologyTaskPhase.RUNNING && snapshot.routineVersionId != null &&
                                !state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning,
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
                            enabled = state.task.phase != BiologyTaskPhase.RUNNING && !state.nBio7BAcceptanceRunning &&
                                !state.adaptiveInferenceRunning,
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
                    DebugCard("N‑BIO‑7B dynamic-resistance acceptance") {
                        Text(
                            "Explicit foreground acceptance over the installed Room14 history. Runs chronological held-out validation, final shadow fits, persist/reload/delete/full replay checks and raw-evidence invariance. Candidate rows remain non-authoritative SHADOW state.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::runNBio7BAcceptance,
                            enabled = !state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning &&
                                !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&
                                state.task.phase != BiologyTaskPhase.RUNNING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.nBio7BAcceptanceRunning) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                                Text("Running real-history acceptance…")
                            } else {
                                Text("Run N-BIO-7B real-history acceptance")
                            }
                        }
                        state.nBio7BAcceptanceProgress?.let { progress ->
                            DebugLine(
                                "Progress",
                                if (progress.totalGroups > 0) "${progress.completedGroups}/${progress.totalGroups}" else "Preparing",
                            )
                            Text(progress.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.nBio7BAcceptanceReport?.let { report ->
                            HorizontalDivider()
                            DebugLine("Device result", if (report.passed) "PASS / ACCEPTANCE OUTPUT COMPLETE" else "REVIEW REQUIRED")
                            DebugLine("Model verdict", report.finalModelVerdict.storageValue)
                            DebugLine("Groups", report.groupsDiscovered.toString())
                            DebugLine("Chronological fits", report.totalChronologicalFits.toString())
                            DebugLine("Total runtime", "${report.totalElapsedMillis} ms")
                            DebugLine("Worst group", report.worstProfileElapsedMillis?.let { "$it ms" } ?: "n/a")
                            DebugLine("Context", report.contextConsumption)
                            DebugLine("Authority", report.productAuthorityStatus)
                            report.checks.forEach { check ->
                                Text(
                                    "${check.status.storageValue.uppercase()} · ${check.id}",
                                    fontWeight = FontWeight.Medium,
                                    color = when (check.status.storageValue) {
                                        "pass" -> MaterialTheme.colorScheme.primary
                                        "fail" -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                                Text(check.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            report.profiles.forEach { profile ->
                                HorizontalDivider()
                                Text("${profile.label} · ${profile.side}", fontWeight = FontWeight.SemiBold)
                                DebugLine("Profile version", profile.executionProfileVersionId)
                                DebugLine(
                                    "Evidence",
                                    "${profile.currentEligibleObservations} obs · ${profile.currentIndependentSessions} sessions · ${profile.currentExclusions} excluded",
                                )
                                DebugLine(
                                    "Rep / resistance domain",
                                    if (profile.repMin != null && profile.repMax != null) {
                                        "${profile.repMin}–${profile.repMax} reps · ${profile.resistanceMinKg?.let(::formatDebug)}–${profile.resistanceMaxKg?.let(::formatDebug)} kg"
                                    } else "unresolved",
                                )
                                DebugLine("Reference reps", profile.referenceRepetitions?.let(::formatDebug) ?: "unresolved")
                                profile.frontierAtReference?.let { frontier ->
                                    DebugLine("Frontier p05/p50/p95", "${formatDebug(frontier.p05)} / ${formatDebug(frontier.p50)} / ${formatDebug(frontier.p95)} kg")
                                }
                                profile.slope?.let { slope ->
                                    DebugLine(
                                        "Slope p05/p50/p95",
                                        "${formatDebug(slope.summary.p05)} / ${formatDebug(slope.summary.p50)} / ${formatDebug(slope.summary.p95)} · ${slope.identification.storageValue}",
                                    )
                                }
                                profile.slackScale?.let { slack ->
                                    DebugLine("Slack scale", "${formatDebug(slack.summary.p50)} · ${slack.identification.storageValue}")
                                }
                                profile.noiseScale?.let { noise ->
                                    DebugLine("Noise scale", "${formatDebug(noise.summary.p50)} · ${noise.identification.storageValue}")
                                }
                                DebugLine(
                                    "Held-out",
                                    "${profile.validation.evaluableCount}/${profile.validation.heldOutObservationCount} evaluable · ${profile.validation.insufficientEvidenceCount} insufficient · ${profile.validation.modelFailureCount} failed",
                                )
                                DebugLine(
                                    "Predictive coverage",
                                    profile.validation.candidatePredictiveCoverage?.let { formatDebug(it) } ?: "insufficient / n/a",
                                )
                                DebugLine(
                                    "PIT bins",
                                    with(profile.validation.candidatePitCalibration) { "$lowCount / $middleCount / $highCount (${sampleCount} total)" },
                                )
                                DebugLine(
                                    "Candidate / benchmark MAE",
                                    "${profile.validation.candidateDemonstrationMedianMaeKg?.let(::formatDebug) ?: "n/a"} / ${profile.validation.benchmarkLatestAnchorMaeKg?.let(::formatDebug) ?: "n/a"} kg",
                                )
                                DebugLine("Verdict", profile.candidateVerdict.storageValue)
                                DebugLine("Shadow run", profile.shadowRunId ?: "none")
                                DebugLine("Parameter codec", profile.parameterCodecVersion?.let { "v$it" } ?: "none")
                                DebugLine("Persist → reload", profile.persistReloadEquivalent?.let { if (it) "PASS" else "FAIL" } ?: "n/a")
                                DebugLine("Full replay", profile.fullReplayEquivalent?.let { if (it) "PASS" else "FAIL" } ?: "n/a")
                                DebugLine("Runtime", "${profile.elapsedMillis} ms")
                                if (profile.predictions.isNotEmpty()) {
                                    Text("Representative predictions", style = MaterialTheme.typography.titleSmall)
                                    profile.predictions.forEach { prediction ->
                                        Text(
                                            "${formatDebug(prediction.repetitions)} reps · frontier ${formatDebug(prediction.frontier.p50)} kg (${formatDebug(prediction.frontier.p05)}–${formatDebug(prediction.frontier.p95)}) · demonstration ${formatDebug(prediction.demonstrationP50Kg)} kg (${formatDebug(prediction.demonstrationP05Kg)}–${formatDebug(prediction.demonstrationP95Kg)})",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                profile.limitations.forEach { limitation ->
                                    Text("• $limitation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching { viewModel.nBio7BAcceptanceJson() }
                                        .onSuccess {
                                            nBio7BExportLauncher.launch("my-mettle-n-bio-7b-acceptance-${Instant.now().epochSecond}.json")
                                        }
                                        .onFailure(viewModel::reportError)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Export N-BIO-7B acceptance JSON") }
                        }
                    }
                }

                item {
                    DebugCard("N‑BIO Adaptive Inference acceptance") {
                        Text(
                            "Single N-BIO-7B.X physical acceptance over installed Room14 history. Full chronology compares same-mathematics adaptive sparse and conditional-Laplace Candidate-v2 solvers; the expensive dense tensor remains a bounded high-fidelity oracle on the richest current profile/side posteriors. Includes corrected median-MAE evaluation, shared replay, solver-substrate benchmarks and safety fingerprints. No product authority is changed.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = viewModel::runAdaptiveInferenceAcceptance,
                            enabled = !state.adaptiveInferenceRunning && !state.nBio7BAcceptanceRunning &&
                                !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&
                                state.task.phase != BiologyTaskPhase.RUNNING,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.adaptiveInferenceRunning) {
                                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                                Text("Running adaptive-inference acceptance…")
                            } else {
                                Text("Run N-BIO Adaptive Inference Acceptance")
                            }
                        }
                        state.adaptiveInferenceProgress?.let { progress ->
                            DebugLine(
                                "Progress",
                                if (progress.totalGroups > 0) "${progress.completedGroups}/${progress.totalGroups}" else "Preparing",
                            )
                            Text(progress.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        state.adaptiveInferenceReport?.let { report ->
                            HorizontalDivider()
                            DebugLine("Safety", if (report.safetyPassed) "PASS" else "FAIL / REVIEW REQUIRED")
                            DebugLine("Build", "${report.appIdentity.versionName} (${report.appIdentity.versionCode})")
                            DebugLine("Device", "${report.deviceIdentity.manufacturer} ${report.deviceIdentity.model} · SDK ${report.deviceIdentity.sdkInt}")
                            DebugLine("Room", "v${report.roomSchemaVersion}")
                            DebugLine("Groups", report.profiles.size.toString())
                            DebugLine("Total runtime", "${report.totalElapsedMillis} ms")
                            DebugLine("Sequential reuse", report.sequentialReuseAssessment.verdict.storageValue)
                            report.profiles.forEach { profile ->
                                HorizontalDivider()
                                Text("${profile.label} · ${profile.side}", fontWeight = FontWeight.SemiBold)
                                DebugLine("Profile version", profile.executionProfileVersionId)
                                DebugLine("Evidence", "${profile.eligibleObservationCount} obs · ${profile.independentSessionCount} sessions")
                                DebugLine("Chronological fits", profile.chronologicalFitCount.toString())
                                DebugLine(
                                    "Current runtime",
                                    "dense ${profile.currentFitElapsedMillisDense?.let { "$it ms" } ?: "n/a"} · sparse ${profile.currentFitElapsedMillisSparse?.let { "$it ms" } ?: "n/a"} · Laplace ${profile.currentFitElapsedMillisLaplace?.let { "$it ms" } ?: "n/a"}",
                                )
                                profile.denseVsSparsePosteriorFidelity?.let { fidelity ->
                                    DebugLine(
                                        "Dense ↔ sparse fidelity",
                                        "frontier ${formatDebug(fidelity.nextFrontierMedianRelativeError)} · trend ${formatDebug(fidelity.trendPositiveProbabilityAbsoluteError)} · W1 ${formatDebug(fidelity.maxStandardisedMarginalWasserstein1)}",
                                    )
                                }
                                profile.denseVsLaplacePosteriorFidelity?.let { fidelity ->
                                    DebugLine(
                                        "Dense ↔ Laplace fidelity",
                                        "frontier ${formatDebug(fidelity.nextFrontierMedianRelativeError)} · trend ${formatDebug(fidelity.trendPositiveProbabilityAbsoluteError)} · W1 ${formatDebug(fidelity.maxStandardisedMarginalWasserstein1)}",
                                    )
                                }
                                profile.bakeoff.candidates.forEach { candidate ->
                                    DebugLine(
                                        candidate.solverIdentity.solverFamily.storageValue,
                                        candidate.developmentComparisonAgainstV1.verdict.storageValue,
                                    )
                                }
                                DebugLine(
                                    "Persist/reload",
                                    "dense ${profile.densePersistReloadEquivalent ?: "n/a"} · sparse ${profile.sparsePersistReloadEquivalent ?: "n/a"} · Laplace ${profile.laplacePersistReloadEquivalent ?: "n/a"}",
                                )
                                DebugLine(
                                    "Full replay",
                                    "dense ${profile.denseReplayEquivalent ?: "n/a"} · sparse ${profile.sparseReplayEquivalent ?: "n/a"} · Laplace ${profile.laplaceReplayEquivalent ?: "n/a"}",
                                )
                                profile.limitations.forEach { limitation ->
                                    Text("• $limitation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    runCatching { viewModel.adaptiveInferenceJson() }
                                        .onSuccess {
                                            adaptiveInferenceExportLauncher.launch(
                                                "my-mettle-n-bio-adaptive-inference-${Instant.now().epochSecond}.json",
                                            )
                                        }
                                        .onFailure(viewModel::reportError)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Export adaptive-inference acceptance JSON") }
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
                            enabled = !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&
                                !state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning,
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
                            "Select an AI-reviewed Lite schema-6 translation. The original Lite database stays factual; a separate Native supplement must provide independent recruitment profiles for every current-routine exercise. Validation runs in an isolated Room database and does not touch app settings.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = { liteBackupLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                            enabled = !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&
                                !state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.nBio6LiteVerificationRunning) "Validating reviewed translation…" else "Validate a reviewed Lite translation")
                        }
                        state.nBio6LiteReport?.let { report ->
                            DebugLine("Lite translation", if (report.passed) "PASS" else "FAIL")
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
                            DebugLine("Execution mode", inference.run.executionMode.storageValue)
                            DebugLine("Semantics", inference.run.semanticsMode.storageValue)
                            DebugLine("Manifest", inference.run.modelManifestId.value)
                            DebugLine("Calculated", inference.run.calculatedAt.toString())
                            DebugLine("Evidence sets", inference.run.evidenceSetCount.toString())
                            DebugLine("Evidence observations", inference.run.evidenceObservationCount.toString())
                            DebugLine("Independent sessions", inference.run.effectiveIndependentSessionCount.toString())
                            DebugLine("Stimulus estimates", inference.stimulusEstimates.size.toString())
                            DebugLine("Muscle states", inference.muscleStates.size.toString())
                            DebugLine("Performance anchors", inference.exerciseTranslationStates.size.toString())
                            DebugLine("Candidate v7 posterior", "Shadow rows are inspected in N-BIO-7B acceptance above")
                            HorizontalDivider()
                            Text("Model/config manifest", style = MaterialTheme.typography.titleSmall)
                            inference.modelConfigs.sortedBy { it.component.storageValue }.forEach { config ->
                                Text(
                                    "${config.component.storageValue} · ${config.modelName} · ${config.semanticVersion}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
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