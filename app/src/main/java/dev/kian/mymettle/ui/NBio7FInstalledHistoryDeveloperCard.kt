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

/** Developer-only N-BIO-7F installed-history evaluation. It has no normal workout authority. */
@Composable
fun NBio7FInstalledHistoryDeveloperCard(
    viewModel: BiologyDeveloperViewModel,
    state: BiologyDeveloperUiState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = viewModel.nBio7FInstalledHistoryJson()
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(json) }
                        ?: error("Android could not open the selected N-BIO-7F report file.")
                }
            }.onSuccess { viewModel.markNBio7FInstalledHistoryExported() }
                .onFailure(viewModel::reportError)
        }
    }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "N-BIO 7F Installed-History Development Evaluation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Developer-only causal prequential replay of installed history. N0 is evaluated wherever prior destination evidence exists. M0 requires explicit versioned directed relationship descriptors; this build supplies none, so no relationship is inferred from names, muscles or equipment family. Normal product authority remains BENCHMARK_V0.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = viewModel::runNBio7FInstalledHistoryEvaluation,
                enabled = !state.nBio7FRunning && !state.nBio7ERunning && !state.nBio7DRunning &&
                    !state.nBio7CCapabilityRunning && !state.nBio7BAcceptanceRunning &&
                    !state.adaptiveInferenceRunning && !state.nBio6VerificationRunning &&
                    !state.nBio6LiteVerificationRunning && state.task.phase != BiologyTaskPhase.RUNNING,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.nBio7FRunning) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp))
                    Text("Running N-BIO-7F evaluation…")
                } else {
                    Text("Run N-BIO 7F installed-history evaluation")
                }
            }
            state.nBio7FProgress?.let { progress ->
                line7f(
                    "Progress",
                    if (progress.totalGroups > 0) {
                        "${progress.completedGroups}/${progress.totalGroups}"
                    } else {
                        "Preparing"
                    },
                )
                Text(
                    progress.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.nBio7FReport?.let { report ->
                HorizontalDivider()
                line7f("Integrity", if (report.integrityPassed) "PASS" else "FAIL")
                line7f("Room", "v${report.roomSchemaVersion}")
                line7f(
                    "N0 destination events",
                    "${report.n0AvailableEventCount}/${report.destinationEvents.size} available",
                )
                line7f("N0 scored observations", report.n0ScoredObservationCount.toString())
                line7f("Explicit M0 relationships", report.relationshipDescriptorCount.toString())
                line7f(
                    "M0 destination events",
                    "${report.m0AvailableDestinationEventCount}/${report.destinationEvents.size} available",
                )
                line7f("M0 scored event-edges", report.m0ScoredEventEdgeCount.toString())
                line7f(
                    "Capability-family revisions",
                    report.capabilityFamilyCoverage.entries.joinToString(" · ") { (family, count) ->
                        "$family $count"
                    }.ifBlank { "none" },
                )
                line7f(
                    "Causal future exclusions",
                    "${report.causallyExcludedFutureCorrectionCount} corrections · " +
                        "${report.causallyExcludedFutureFactCount} facts",
                )
                line7f(
                    "N1 / M1 / M2",
                    "${report.n1RealHistoryStatus} / ${report.m1RealHistoryStatus} / ${report.m2RealHistoryStatus}",
                )
                line7f("Runtime", "${report.runtimeMillis} ms")
                line7f("Peak observed heap", formatBytes7f(report.peakObservedHeapBytes))
                Text(
                    if (report.relationshipDescriptorCount == 0) {
                        "M0 is intentionally unavailable in this run because no explicit 7F relationship registry exists. This is a valid N0-only development result, not missing-data imputation."
                    } else {
                        "M0 results are SHADOW development evidence only. No candidate promotion or normal workout behaviour is changed."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = {
                        export.launch(
                            "my-mettle-n-bio-7f-installed-history-${Instant.now().epochSecond}.json",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Export N-BIO-7F installed-history JSON")
                }
            }
        }
    }
}

@Composable
private fun line7f(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), fontWeight = FontWeight.Medium)
    }
}

private fun formatBytes7f(bytes: Long): String {
    val mib = bytes.toDouble() / (1024.0 * 1024.0)
    return "%.1f MiB".format(mib)
}
