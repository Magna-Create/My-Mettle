package dev.kian.mymettle.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.data.local.entity.SessionReviewEntity
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.history.HistoryExercise
import dev.kian.mymettle.history.HistorySession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val context = LocalContext.current
    val viewModel: HistoryViewModel = viewModel(
        factory = remember(context) { HistoryViewModelFactory(context) },
    )
    val state = viewModel.uiState
    var selected by remember { mutableStateOf<HistorySession?>(null) }

    Scaffold(
        topBar = {
            MettleAppHeader(
                destination = "Session History",
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.sessions, key = { it.session.id }) { session ->
                    HistorySessionCard(session = session, onClick = { selected = session })
                }
            }
        }
    }

    selected?.let { session ->
        HistoryDetailSheet(
            session = session,
            saving = state.saving,
            onDismiss = { selected = null },
            onSaveSet = { exercise, set, load, reps, duration, distance ->
                viewModel.updateSet(
                    sessionId = session.session.id,
                    sessionExerciseId = exercise.exercise.id,
                    setId = set.id,
                    load = load,
                    reps = reps,
                    durationSeconds = duration,
                    distanceMetres = distance,
                    onSaved = { selected = it },
                )
            },
            onSaveReview = { order, organisation, pacing, delay, note ->
                viewModel.saveSessionReview(
                    sessionId = session.session.id,
                    exerciseOrder = order,
                    organisation = organisation,
                    pacing = pacing,
                    delayImpact = delay,
                    note = note,
                    onSaved = { selected = it },
                )
            },
            onDiscard = {
                viewModel.discardSession(session.session.id) { selected = null }
            },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("History couldn't update") },
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
                MettleGlassAssistChip(onClick = onClick, label = { Text(session.achievement.score.toString()) })
            }
            val loggedSets = session.exercises.sumOf { exercise -> exercise.sets.count { it.completedAt != null } }
            Text(
                "${session.exercises.count { it.exercise.prescriptionIncluded }} target exercises · $loggedSets logged sets",
                style = MaterialTheme.typography.labelLarge,
            )
            session.session.editedAt?.let {
                Text("Edited", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(
    session: HistorySession,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSaveSet: (HistoryExercise, SetRecordEntity, Double?, Int?, Int?, Double?) -> Unit,
    onSaveReview: (Int?, Int?, Int?, Int?, String?) -> Unit,
    onDiscard: () -> Unit,
) {
    var editing by remember(session.session.id) { mutableStateOf(false) }
    var confirmDiscard by remember(session.session.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "${session.session.daySymbol} · Mode ${session.session.mode}",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Achievement ${session.achievement.score} · ${session.achievement.loggedTargetSets}/${session.achievement.targetSets} target sets",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editing = !editing }, enabled = !saving) {
                        Text(if (editing) "Done" else "Edit")
                    }
                }
            }

            items(session.exercises, key = { it.exercise.id }) { exercise ->
                HistoryExerciseCard(
                    exercise = exercise,
                    editing = editing,
                    saving = saving,
                    onSaveSet = { set, load, reps, duration, distance ->
                        onSaveSet(exercise, set, load, reps, duration, distance)
                    },
                )
            }

            item {
                if (editing) {
                    HistoryReviewEditor(
                        review = session.review,
                        saving = saving,
                        onSave = onSaveReview,
                    )
                } else {
                    session.review?.let { review -> HistoryReviewCard(review) }
                }
            }

            if (editing) {
                item {
                    HorizontalDivider()
                    TextButton(
                        onClick = { confirmDiscard = true },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Discard session", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard this session?") },
            text = { Text("It will disappear from History and be excluded from insights. The stored record is kept as discarded rather than being physically deleted.") },
            confirmButton = {
                MettleGlassActionButton(
                    onClick = {
                        confirmDiscard = false
                        onDiscard()
                    },
                    enabled = !saving,
                ) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun HistoryExerciseCard(
    exercise: HistoryExercise,
    editing: Boolean,
    saving: Boolean,
    onSaveSet: (SetRecordEntity, Double?, Int?, Int?, Double?) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(exercise.exercise.exerciseNameSnapshot, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(exercise.exercise.status, style = MaterialTheme.typography.labelMedium)
            }
            exercise.sets.filter { it.completedAt != null }.forEachIndexed { index, set ->
                if (editing) {
                    HistorySetEditor(
                        displayIndex = index + 1,
                        exercise = exercise,
                        set = set,
                        saving = saving,
                        onSave = onSaveSet,
                    )
                } else {
                    Text(
                        "${index + 1}. ${historySetSummary(set)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

@Composable
private fun HistorySetEditor(
    displayIndex: Int,
    exercise: HistoryExercise,
    set: SetRecordEntity,
    saving: Boolean,
    onSave: (SetRecordEntity, Double?, Int?, Int?, Double?) -> Unit,
) {
    val metric = exercise.exercise.trackingMetricSnapshot
    val needsLoad = metric == "load_reps" && exercise.exercise.loadRelationshipSnapshot != "bodyweight"
    val needsReps = metric == "load_reps" || metric == "reps"
    val needsDuration = metric == "duration"
    val needsDistance = metric == "distance"

    var load by remember(set.id, set.load) { mutableStateOf(set.load?.let(::formatHistoryLoad).orEmpty()) }
    var reps by remember(set.id, set.reps) { mutableStateOf(set.reps?.toString().orEmpty()) }
    var duration by remember(set.id, set.durationSeconds) { mutableStateOf(set.durationSeconds?.toString().orEmpty()) }
    var distance by remember(set.id, set.distanceMetres) { mutableStateOf(set.distanceMetres?.let(::formatHistoryLoad).orEmpty()) }

    val valid = when {
        needsLoad && load.toDoubleOrNull() == null -> false
        needsReps && reps.toIntOrNull() == null -> false
        needsDuration && duration.toIntOrNull()?.let { it > 0 } != true -> false
        needsDistance && distance.toDoubleOrNull()?.let { it > 0.0 } != true -> false
        else -> true
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Set $displayIndex", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (needsLoad) {
                OutlinedTextField(
                    value = load,
                    onValueChange = { load = decimalHistoryInput(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text(set.unit) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            if (needsReps) {
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (needsDuration) {
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Seconds") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (needsDistance) {
                OutlinedTextField(
                    value = distance,
                    onValueChange = { distance = decimalHistoryInput(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Metres") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        MettleGlassActionButton(
            onClick = {
                onSave(
                    set,
                    load.toDoubleOrNull(),
                    reps.toIntOrNull(),
                    duration.toIntOrNull(),
                    distance.toDoubleOrNull(),
                )
            },
            enabled = valid && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (saving) "Saving…" else "Save set")
        }
    }
}

@Composable
private fun HistoryReviewCard(review: SessionReviewEntity) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("Session review", style = MaterialTheme.typography.titleMedium)
            Text("Order ${review.exerciseOrder ?: "—"}/5 · Organisation ${review.organisation ?: "—"}/5")
            Text("Pacing ${review.pacing ?: "—"}/5 · Delay impact ${review.delayImpact ?: "—"}/5")
            review.note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun HistoryReviewEditor(
    review: SessionReviewEntity?,
    saving: Boolean,
    onSave: (Int?, Int?, Int?, Int?, String?) -> Unit,
) {
    var order by remember(review?.updatedAt) { mutableIntStateOf(review?.exerciseOrder ?: 0) }
    var organisation by remember(review?.updatedAt) { mutableIntStateOf(review?.organisation ?: 0) }
    var pacing by remember(review?.updatedAt) { mutableIntStateOf(review?.pacing ?: 0) }
    var delay by remember(review?.updatedAt) { mutableIntStateOf(review?.delayImpact ?: 0) }
    var note by remember(review?.updatedAt) { mutableStateOf(review?.note.orEmpty()) }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Session review", style = MaterialTheme.typography.titleMedium)
            HistoryRatingRow("Exercise order", order) { order = it }
            HistoryRatingRow("Organisation", organisation) { organisation = it }
            HistoryRatingRow("Pacing", pacing) { pacing = it }
            HistoryRatingRow("Delay impact", delay) { delay = it }
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(600) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Session note") },
                minLines = 2,
                maxLines = 5,
            )
            MettleGlassActionButton(
                onClick = {
                    onSave(
                        order.takeIf { it > 0 },
                        organisation.takeIf { it > 0 },
                        pacing.takeIf { it > 0 },
                        delay.takeIf { it > 0 },
                        note,
                    )
                },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Saving…" else "Save review")
            }
        }
    }
}

@Composable
private fun HistoryRatingRow(title: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            (1..5).forEach { rating ->
                MettleGlassChoiceChip(
                    selected = value == rating,
                    onClick = { onChange(if (value == rating) 0 else rating) },
                    label = { Text(rating.toString()) },
                )
            }
        }
    }
}

private fun historySetSummary(set: SetRecordEntity): String = buildString {
    if (set.load != null) append("${formatHistoryLoad(set.load)} ${set.unit}")
    if (set.load != null && set.reps != null) append(" × ")
    if (set.reps != null) append("${set.reps} reps")
    if (set.durationSeconds != null) append("${set.durationSeconds}s")
    if (set.distanceMetres != null) append("${formatHistoryLoad(set.distanceMetres)} m")
    if (isEmpty()) append("—")
}

private fun decimalHistoryInput(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '.' }.take(8)
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered else filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).replace(".", "")
}

private fun formatTimestamp(value: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(value))
}.getOrElse { value }

private fun formatHistoryLoad(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
