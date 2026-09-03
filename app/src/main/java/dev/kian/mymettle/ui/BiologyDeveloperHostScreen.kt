package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stable developer-tools host that exposes the single consolidated N-BIO-7D acceptance surface
 * without rewriting or duplicating the existing N-BIO-6/7B/7C diagnostics screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiologyDeveloperHostScreen(onBack: () -> Unit) {
    var showing7D by rememberSaveable { mutableStateOf(false) }

    if (!showing7D) {
        Box(modifier = Modifier.fillMaxSize()) {
            BiologyDeveloperScreen(onBack = onBack)
            Button(
                onClick = { showing7D = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                    .fillMaxWidth(),
            ) {
                Text("Open N-BIO-7D Demand & Dose Acceptance")
            }
        }
        return
    }

    val context = LocalContext.current
    val viewModel: BiologyDeveloperViewModel = viewModel(
        factory = remember(context) { BiologyDeveloperViewModelFactory(context) },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("N-BIO-7D Demand & Dose") },
                navigationIcon = {
                    TextButton(onClick = { showing7D = false }) { Text("All tools") }
                },
                actions = {
                    TextButton(onClick = onBack) { Text("Close") }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                NBio7DAcceptanceDeveloperCard(
                    viewModel = viewModel,
                    state = viewModel.uiState,
                )
            }
        }
    }
}
