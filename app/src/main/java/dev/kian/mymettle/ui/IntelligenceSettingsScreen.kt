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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Settings home for optional intelligence features, independent of developer diagnostics.
 *
 * This screen deliberately does not expose a functional cloud switch or a fake
 * "connected" state while the Cloud Vision connector is still in the OCR Lab.
 * When implemented, credentials must be entered at runtime and never shipped
 * via source, BuildConfig, resources, assets, backups, or APK constants.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntelligenceSettingsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Intelligence", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 150.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                IntelligenceSettingsSection(
                    title = "On-device recognition",
                    description = "Weight-stack and placard OCR can run locally. The single-photo scan and live camera guides are being validated before integration.",
                    status = "In development",
                )
            }
            item {
                IntelligenceSettingsSection(
                    title = "Google Cloud Vision",
                    description = "Optional online logo and web identification. The experimental recogniser will be tested before cloud access is enabled here. Your own Google Cloud credentials will be configured on this device, not included in the repository or app build.",
                    status = "Not connected",
                )
            }
            item {
                IntelligenceSettingsSection(
                    title = "N-BIO",
                    description = "This area will also hold future user-facing biological intelligence preferences. Experimental inference and developer diagnostics remain separate until product behaviour is approved.",
                    status = "Future settings",
                )
            }
            item {
                Text(
                    "Cloud image analysis will require an explicit opt-in. Training and ordinary workout logging must continue to work offline.",
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IntelligenceSettingsSection(
    title: String,
    description: String,
    status: String,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                status,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
