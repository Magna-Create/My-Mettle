package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.ai.LabAiRuntime

private enum class DeveloperHostSection {
    HUB,
    BIOLOGY,
    AI_RUNTIME,
}

/** Compatibility route; normal builds retain the existing biological tools directly. */
@Composable
fun BiologyDeveloperHostScreen(onBack: () -> Unit) {
    if (!LabAiRuntime.isEnabled) {
        BiologyDeveloperScreen(onBack = onBack)
        return
    }

    var section by remember { mutableStateOf(DeveloperHostSection.HUB) }
    when (section) {
        DeveloperHostSection.HUB -> LabDeveloperHub(
            onBack = onBack,
            onOpenBiology = { section = DeveloperHostSection.BIOLOGY },
            onOpenAiRuntime = { section = DeveloperHostSection.AI_RUNTIME },
        )
        DeveloperHostSection.BIOLOGY -> BiologyDeveloperScreen(
            onBack = { section = DeveloperHostSection.HUB },
        )
        DeveloperHostSection.AI_RUNTIME -> AiLabDeveloperScreen(
            onBack = { section = DeveloperHostSection.HUB },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabDeveloperHub(
    onBack: () -> Unit,
    onOpenBiology: () -> Unit,
    onOpenAiRuntime: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Developer tools", fontWeight = FontWeight.SemiBold) },
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("AI runtime", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Inspect the read-only system Prompt API probe, local fallback lifecycle shell and provider resolution.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MettleGlassActionButton(onClick = onOpenAiRuntime, modifier = Modifier.fillMaxWidth()) {
                            Text("Open AI runtime diagnostics")
                        }
                    }
                }
            }
            item {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Biology", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Open the existing N-BIO acceptance, inference and provenance diagnostics unchanged.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MettleGlassActionButton(onClick = onOpenBiology, modifier = Modifier.fillMaxWidth()) {
                            Text("Open biological developer tools")
                        }
                    }
                }
            }
        }
    }
}
