package dev.kian.mymettle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.developer.BiologyTaskPhase
import java.time.Instant

/**
 * Developer-only visualisation output. No inference is promoted to normal workout authority.
 * The SAF document is created before the expensive replay and written incrementally.
 */
@Composable
fun NBioPosteriorPortfolioDeveloperCard(
    viewModel: BiologyDeveloperViewModel,
    state: BiologyDeveloperUiState,
) {
    var expanded by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) viewModel.exportPosteriorPortfolio(uri)
    }
    val selected = state.posteriorOptions.firstOrNull { it.key == state.posteriorSelectedKey }
    val busy = state.posteriorExportRunning || state.posteriorOptionsLoading ||
        state.nBio7FRunning || state.nBio7ERunning || state.nBio7DRunning ||
        state.nBio7CCapabilityRunning || state.nBio7BAcceptanceRunning ||
        state.adaptiveInferenceRunning || state.nBio6VerificationRunning ||
        state.nBio6LiteVerificationRunning || state.task.phase == BiologyTaskPhase.RUNNING

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "N-BIO · Portfolio Posterior Visualisation",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Export actual Adaptive Sparse N0 posterior nodes at up to six chronological historical cutoffs. " +
                    "Each cutoff replays only evidence available as of that session; no artificial sets or weights are generated. " +
                    "The JSON preserves each node's five model parameters and original probability mass for Blender.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.posteriorOptionsLoading) {
                Text("Reading eligible exercises…", style = MaterialTheme.typography.bodySmall)
            } else if (state.posteriorOptions.isEmpty()) {
                Text(
                    "No suitable dynamic-resistance exercise evidence found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("Select execution profile and side", fontWeight = FontWeight.Medium)
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { expanded = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            selected?.let { "${it.label} · ${it.side.storageValue}" } ?: "Choose exercise",
                            maxLines = 2,
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        state.posteriorOptions.forEachIndexed { index, option ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            (if (index == 0) "Recommended · " else "") +
                                                option.label + " · " + option.side.storageValue,
                                        )
                                        Text(
                                            "${option.independentSessions} sessions · " +
                                                "${option.eligibleObservations} observations · " +
                                                "reps ${option.minRepetitions ?: "?"}–${option.maxRepetitions ?: "?"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectPosteriorProfile(option.key)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                selected?.let {
                    Text(
                        "Eligible support: ${it.independentSessions} independent sessions / " +
                            "${it.eligibleObservations} observations. " +
                            "Recommended = most independent sessions, then repetition coverage and sample count.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        exportLauncher.launch("my-mettle-posterior-${Instant.now().epochSecond}.json")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && selected != null,
                ) {
                    if (state.posteriorExportRunning) {
                        CircularProgressIndicator(Modifier.padding(end = 8.dp))
                        Text("Exporting historical posteriors…")
                    } else {
                        Text("Generate & save posterior JSON")
                    }
                }
            }
            state.posteriorExportProgress?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Research / visualisation only. The exported capability frontier is NOT a working-load prescription. " +
                    "Original training evidence and BENCHMARK_V0 remain unchanged.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
