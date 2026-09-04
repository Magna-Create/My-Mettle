package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.ai.LabAiRuntime
import dev.kian.mymettle.ai.PromptCapability
import dev.kian.mymettle.ai.PromptCapabilitySupport
import dev.kian.mymettle.ai.PromptProviderPreference
import dev.kian.mymettle.ai.PromptProviderResolver
import dev.kian.mymettle.ai.PromptTaskRequirements
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLabDeveloperScreen(onBack: () -> Unit) {
    val runtime by LabAiRuntime.state.collectAsState()
    var preference by remember { mutableStateOf(PromptProviderPreference.AUTO) }
    val requirements = PromptTaskRequirements.DIAGNOSTIC_TEXT_STRUCTURED
    val resolution = PromptProviderResolver.resolve(
        requirements = requirements,
        preference = preference,
        system = runtime.systemProvider,
        localModel = runtime.localModel,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI runtime diagnostics", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("System provider", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        DiagnosticLine("Status", runtime.systemProvider.availability.name)
                        DiagnosticLine("Runtime", runtime.systemProvider.diagnostics.providerRuntime ?: "unknown")
                        DiagnosticLine("Runtime version", runtime.systemProvider.diagnostics.providerRuntimeVersion ?: "unknown")
                        DiagnosticLine("Base model", runtime.systemProvider.diagnostics.modelIdentity ?: "not reported")
                        DiagnosticLine("Last probe", formatEpoch(runtime.systemProvider.diagnostics.lastProbeEpochMillis))
                        runtime.systemProvider.failure?.let {
                            DiagnosticLine("Last probe error", listOfNotNull(it.kind.name, it.errorClass, it.errorCode).joinToString(" / "))
                        }
                        Text("Capabilities", style = MaterialTheme.typography.titleSmall)
                        PromptCapability.entries.forEach { capability ->
                            val support = runtime.systemProvider.capabilities.supportFor(capability)
                            DiagnosticLine(capability.name, support.displayName())
                        }
                        if (runtime.systemProvider.diagnostics.capabilityProbeErrors.isNotEmpty()) {
                            Text("Capability probe errors", style = MaterialTheme.typography.titleSmall)
                            runtime.systemProvider.diagnostics.capabilityProbeErrors.forEach { (capability, error) ->
                                DiagnosticLine(capability.name, error)
                            }
                        }
                        MettleGlassActionButton(
                            onClick = LabAiRuntime::refresh,
                            enabled = !runtime.refreshInProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (runtime.refreshInProgress) "Refreshing…" else "Refresh probe")
                        }
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Local provider", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        DiagnosticLine("Lifecycle", runtime.localModel.lifecycleState.name)
                        DiagnosticLine("Removal", runtime.localModel.removalState.name)
                        val metadata = runtime.localModel.metadata
                        DiagnosticLine("Model", metadata?.let { "${it.modelId}@${it.modelVersion}" } ?: "not installed")
                        DiagnosticLine("Runtime", metadata?.let { "${it.runtimeId}@${it.runtimeVersion}" } ?: "not integrated")
                        DiagnosticLine("Asset size", metadata?.assetSizeBytes?.toString() ?: "not applicable")
                        runtime.localModel.lastFailure?.let {
                            DiagnosticLine("Last lifecycle error", listOfNotNull(it.kind.name, it.errorClass, it.errorCode).joinToString(" / "))
                        }
                        Text(
                            "LAB-1 has no local model, downloader, URL or runtime. These lifecycle fields are the future contract only.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Resolution", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Developer-only provider override. Normal product behaviour remains AUTO.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PromptProviderPreference.entries.forEach { option ->
                                FilterChip(
                                    selected = preference == option,
                                    onClick = { preference = option },
                                    label = { Text(option.name) },
                                )
                            }
                        }
                        DiagnosticLine(
                            "Required capabilities",
                            requirements.required.sortedBy { it.name }.joinToString { it.name },
                        )
                        DiagnosticLine("Outcome", resolution.kind.name)
                        DiagnosticLine("Selected provider", resolution.selectedProvider?.name ?: "none")
                        DiagnosticLine("Setup provider", resolution.setupProvider?.name ?: "none")
                        DiagnosticLine("Reason", resolution.reason.name)
                        DiagnosticLine("System transition", resolution.systemTransition.name)
                        DiagnosticLine("Local retirement", resolution.localRetirement.name)
                        DiagnosticLine("Last runtime refresh", formatEpoch(runtime.lastRefreshEpochMillis))
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

private fun PromptCapabilitySupport.displayName(): String = when (this) {
    PromptCapabilitySupport.SUPPORTED -> "SUPPORTED"
    PromptCapabilitySupport.UNSUPPORTED -> "UNSUPPORTED"
    PromptCapabilitySupport.UNKNOWN -> "UNKNOWN / UNVERIFIED"
}

private fun formatEpoch(epochMillis: Long?): String = epochMillis?.let {
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(it))
} ?: "not yet probed"
