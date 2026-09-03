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

/** Single developer-only physical gate for N-BIO-7E; it has no normal workout authority. */
@Composable
fun NBio7EAcceptanceDeveloperCard(viewModel: BiologyDeveloperViewModel, state: BiologyDeveloperUiState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.nBio7EAcceptanceJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected N-BIO-7E report file.")
                }
            }.onSuccess { viewModel.markNBio7EAcceptanceExported() }.onFailure(viewModel::reportError)
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("N-BIO 7E State & Context Acceptance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Developer-only causal replay of neutral persistent/transient state and build-integrated context modules. Compares capability, temporal, dose and context candidates; verifies Room15 persistence, replay, backup and privacy/product-authority invariants. PD-001, PD-002 and PD-003 remain open.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::runNBio7EAcceptance,
                enabled = !state.nBio7ERunning && !state.nBio7DRunning && !state.nBio7CCapabilityRunning &&
                    !state.nBio7BAcceptanceRunning && !state.adaptiveInferenceRunning &&
                    !state.nBio6VerificationRunning && !state.nBio6LiteVerificationRunning &&
                    state.task.phase != BiologyTaskPhase.RUNNING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.nBio7ERunning) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp))
                    Text("Running N-BIO-7E acceptance…")
                } else Text("Run N-BIO 7E State & Context Acceptance")
            }
            state.nBio7EProgress?.let { progress ->
                line("Progress", "${progress.completedGroups}/${progress.totalGroups}")
                Text(progress.label, style = MaterialTheme.typography.bodySmall)
            }
            state.nBio7EReport?.let { report ->
                HorizontalDivider()
                line("Structural", report.structuralVerdict.name)
                line("Empirical calibration", "PD-003 OPEN")
                line("Room", "v${report.roomSchemaVersion}")
                line("Synthetic", "${report.synthetic.temporalCases.count { it.passed }}/17 temporal · ${report.synthetic.contextModuleCases.count { it.passed }}/25 module")
                line("Real residual sessions", report.history.residualSessions.toString())
                line("Context evidence", report.history.contextTagCounts.values.sum().toString())
                line("Dose sessions", report.history.doseSessions.toString())
                line("Persist / replay", if (report.persistence.persistReloadEquivalent && report.persistence.fullReplayEquivalent) "PASS" else "FAIL")
                line("Native backup", if (report.nativeBackupRoundTrip) "PASS" else "FAIL")
                line("Raw/context/prescription", if (
                    report.rawFingerprintBefore == report.rawFingerprintAfter &&
                    report.contextFingerprintBefore == report.contextFingerprintAfter &&
                    report.prescriptionBefore == report.prescriptionAfter
                ) "UNCHANGED" else "CHANGED")
                line("BENCHMARK_V0", if (report.benchmarkRunIdBefore == report.benchmarkRunIdAfter) "UNCHANGED" else "CHANGED")
                Text("All results are SHADOW/candidate evidence. No coaching or workout behaviour is changed.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(
                    onClick = { export.launch("my-mettle-n-bio-7e-state-context-${Instant.now().epochSecond}.json") },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Export N-BIO-7E State & Context JSON") }
            }
        }
    }
}

@Composable
private fun line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}
