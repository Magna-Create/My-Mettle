package dev.kian.mymettle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.developer.BiologyTaskPhase
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One consolidated developer surface for N-BIO-7D. No temporary sub-test buttons are exposed. */
@Composable
fun NBio7DAcceptanceDeveloperCard(
    viewModel: BiologyDeveloperViewModel,
    state: BiologyDeveloperUiState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.nBio7DAcceptanceJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected N-BIO-7D report file.")
                }
            }.onSuccess { viewModel.markNBio7DAcceptanceExported() }
                .onFailure(viewModel::reportError)
        }
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("N-BIO-7D Demand & Dose Acceptance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "One structural acceptance over SetDemand, exact historical muscle Exposure, posterior EffectiveDose and SessionDose. Includes causal real-history replay, Dense/Adaptive-Sparse downstream fidelity, delta/tau sensitivity, correction boundaries, Room14 persistence/delete/replay, Native backup integrity and BENCHMARK_V0 authority checks. PD-001 and PD-002 remain open.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Prerequisite: run ‘Recompute biological state’ in Lifecycle and diagnostics at least once so a BENCHMARK_V0 authority baseline exists before this SHADOW acceptance starts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::runNBio7DAcceptance,
                enabled = !state.nBio7DRunning && !state.nBio7CCapabilityRunning && !state.nBio7BAcceptanceRunning &&
                    !state.adaptiveInferenceRunning && !state.nBio6VerificationRunning &&
                    !state.nBio6LiteVerificationRunning && state.task.phase != BiologyTaskPhase.RUNNING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.nBio7DRunning) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                    Text("Running N-BIO-7D acceptance…")
                } else {
                    Text("Run N-BIO-7D Demand & Dose Acceptance")
                }
            }
            state.nBio7DProgress?.let { progress ->
                NBio7DCardLine(
                    "Progress",
                    if (progress.totalGroups > 0) "${progress.completedGroups}/${progress.totalGroups}" else "Preparing",
                )
                Text(progress.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.nBio7DReport?.let { report ->
                val core = report.core
                HorizontalDivider()
                NBio7DCardLine("Structural", report.structuralVerdict.storageValue)
                NBio7DCardLine("Empirical calibration", report.empiricalCalibrationStatus.storageValue)
                NBio7DCardLine("Overall", report.overallVerdict.storageValue)
                NBio7DCardLine("Room", "v${core.roomSchemaVersion}")
                NBio7DCardLine("14-case synthetic", if (core.synthetic.allPassed) "PASS" else "FAIL")
                NBio7DCardLine("Delta / tau sensitivity", if (report.sensitivity.passed) "PASS" else "FAIL")
                NBio7DCardLine("Correction boundaries", if (report.correctionBoundary.passed) "PASS" else "FAIL")
                NBio7DCardLine("Downstream solver fidelity", if (report.downstreamFidelity.passed) "PASS" else "FAIL")
                NBio7DCardLine("Real-history sets", core.history.evaluatedSets.toString())
                NBio7DCardLine("Historical exposures", core.history.historicalMuscleExposures.toString())
                NBio7DCardLine(
                    "EffectiveDose",
                    "${core.history.resolvedEffectiveDoses} resolved · ${core.history.unresolvedEffectiveDoses} unresolved",
                )
                NBio7DCardLine("Session dose outputs", core.history.muscleSessionDoses.toString())
                NBio7DCardLine("Persist / reload / delete", if (core.persistenceChecksPass) "PASS" else "FAIL")
                NBio7DCardLine("Representative full replay", if (core.representativeFullReplayEquivalent) "PASS" else "FAIL")
                NBio7DCardLine("Native backup", if (core.backupRoundTrip.passed) "PASS" else "FAIL")
                NBio7DCardLine("Raw evidence", if (core.rawEvidenceUnchanged) "UNCHANGED" else "CHANGED")
                NBio7DCardLine("Prescriptions", if (core.prescriptionStateUnchanged) "UNCHANGED" else "CHANGED")
                NBio7DCardLine("BENCHMARK_V0", report.benchmarkV0Status)
                NBio7DCardLine("7E state", "NOT STARTED")
                NBio7DCardLine("Runtime", "${core.totalElapsedMillis} ms + fidelity validation")
                Text(
                    "Structural PASS does not close PD-001 or PD-002 and does not grant normal workout/prescription authority.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        runCatching { viewModel.nBio7DAcceptanceJson() }
                            .onSuccess {
                                exportLauncher.launch("my-mettle-n-bio-7d-demand-dose-${Instant.now().epochSecond}.json")
                            }
                            .onFailure(viewModel::reportError)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export N-BIO-7D Demand & Dose JSON") }
            }
        }
    }
}

@Composable
private fun NBio7DCardLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}
