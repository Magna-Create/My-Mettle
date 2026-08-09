package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.history.HistoryExercise
import dev.kian.mymettle.history.HistorySession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = remember(context) { HistoryViewModelFactory(context) },
    )
    val state = viewModel.uiState
    var selected by remember { mutableStateOf<HistorySession?>(null) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("History", fontWeight = FontWeight.SemiBold)
                        Text(
                            "What actually happened",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.sessions.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Completed workouts will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.session.id }) { session ->
                    HistorySessionCard(session = session, onClick = { selected = session })
                }
            }
        }
    }

    selected?.let { session ->
        HistoryDetailSheet(session = session, onDismiss = { selected = null })
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("History couldn't load") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun HistorySessionCard(session: HistorySession, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${session.session.daySymbol} · Mode ${session.session.mode}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatTimestamp(session.session.completedAt ?: session.session.startedAt),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(onClick = onClick, label = { Text(session.achievement.score.toString()) })
            }
            val loggedSets = session.exercises.sumOf { exercise -> exercise.sets.count { it.completedAt != null } }
            Text(
                "${session.exercises.count { it.exercise.prescriptionIncluded }} target exercises · $loggedSets logged sets",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(session: HistorySession, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "${session.session.daySymbol} · Mode ${session.session.mode}",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    "Achievement ${session.achievement.score} · ${session.achievement.loggedTargetSets}/${session.achievement.targetSets} target sets",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(session.exercises, key = { it.exercise.id }) { exercise ->
                HistoryExerciseCard(exercise)
            }

            session.review?.let { review ->
                item {
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text("Session review", style = MaterialTheme.typography.titleMedium)
                            Text("Order ${review.exerciseOrder ?: "—"}/5 · Organisation ${review.organisation ?: "—"}/5")
                            Text("Pacing ${review.pacing ?: "—"}/5 · Delay impact ${review.delayImpact ?: "—"}/5")
                            review.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun HistoryExerciseCard(exercise: HistoryExercise) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exercise.exercise.exerciseNameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(exercise.exercise.status, style = MaterialTheme.typography.labelMedium)
            }
            exercise.sets.filter { it.completedAt != null }.forEachIndexed { index, set ->
                Text(
                    "${index + 1}. ${set.load?.let(::formatHistoryLoad)?.plus(" ${set.unit}") ?: ""}${if (set.load != null && set.reps != null) " × " else ""}${set.reps?.let { "$it reps" } ?: ""}${set.durationSeconds?.let { "${it}s" } ?: ""}${set.distanceMetres?.let { "${formatHistoryLoad(it)} m" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            exercise.reflection?.let { reflection ->
                HorizontalDivider()
                Text(
                    "Reflection · target ${reflection.targetMuscleEngagement} · ${reflection.execution} · vibe ${reflection.enjoyment} · ${reflection.comfort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                reflection.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private fun formatTimestamp(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrElse { value }

private fun formatHistoryLoad(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
