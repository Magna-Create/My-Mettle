package dev.kian.mymettle.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kian.mymettle.workout.CelebrationLevel
import dev.kian.mymettle.workout.SessionAchievement

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionOutcomeOverlay() {
    val context = LocalContext.current
    val viewModel: N2WorkoutViewModel = viewModel(
        factory = remember(context) { N2WorkoutViewModelFactory(context) },
    )
    val state = viewModel.uiState
    val workout = state.workout ?: return
    val achievement = state.achievement ?: return
    if (!state.sessionCompleted || workout.session.status != "completed") return

    val review = state.sessionReview
    var open by remember(workout.session.id) { mutableStateOf(true) }
    var exerciseOrder by remember(review?.updatedAt) { mutableIntStateOf(review?.exerciseOrder ?: 0) }
    var organisation by remember(review?.updatedAt) { mutableIntStateOf(review?.organisation ?: 0) }
    var pacing by remember(review?.updatedAt) { mutableIntStateOf(review?.pacing ?: 0) }
    var delayImpact by remember(review?.updatedAt) { mutableIntStateOf(review?.delayImpact ?: 0) }
    var note by remember(review?.updatedAt) { mutableStateOf(review?.note.orEmpty()) }

    if (!open) return

    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(achievementTitle(achievement), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${workout.session.daySymbol} · ${state.selectedMode.label}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AchievementCard(achievement)

            HorizontalDivider()

            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column {
                    Text("Whole-session review", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Optional. This is about how the session worked as a system, not how each exercise felt.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                RatingRow(
                    title = "Exercise order",
                    value = exerciseOrder,
                    labels = QUALITY_LABELS,
                    onValueChange = { exerciseOrder = it },
                )
                RatingRow(
                    title = "Organisation",
                    value = organisation,
                    labels = QUALITY_LABELS,
                    onValueChange = { organisation = it },
                )
                RatingRow(
                    title = "Pacing",
                    value = pacing,
                    labels = QUALITY_LABELS,
                    onValueChange = { pacing = it },
                )
                RatingRow(
                    title = "Delay impact",
                    value = delayImpact,
                    labels = DELAY_LABELS,
                    onValueChange = { delayImpact = it },
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(600) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Session note") },
                    minLines = 2,
                    maxLines = 5,
                )

                Button(
                    onClick = {
                        viewModel.saveSessionReview(
                            exerciseOrder = exerciseOrder.takeIf { it > 0 },
                            organisation = organisation.takeIf { it > 0 },
                            pacing = pacing.takeIf { it > 0 },
                            delayImpact = delayImpact.takeIf { it > 0 },
                            note = note,
                        )
                    },
                    enabled = !state.savingReview,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.savingReview) "Saving…" else if (review == null) "Save review" else "Update review")
                }

                TextButton(
                    onClick = viewModel::leaveCompletedSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back to programme")
                }
            }
        }
    }
}

@Composable
private fun AchievementCard(achievement: SessionAchievement) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(achievement.level.readableName(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${achievement.score}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                "${achievement.loggedTargetSets}/${achievement.targetSets} target sets · ${achievement.achievedExercises}/${achievement.targetExercises} target exercises",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (achievement.repBeats > 0 || achievement.extraLoggedSets > 0) {
                Text(
                    buildString {
                        if (achievement.repBeats > 0) append("${achievement.repBeats} rep target beat${if (achievement.repBeats == 1) "" else "s"}")
                        if (achievement.repBeats > 0 && achievement.extraLoggedSets > 0) append(" · ")
                        if (achievement.extraLoggedSets > 0) append("${achievement.extraLoggedSets} extra logged set${if (achievement.extraLoggedSets == 1) "" else "s"}")
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun RatingRow(
    title: String,
    value: Int,
    labels: List<String>,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                if (value == 0) "Not rated" else labels[value - 1],
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..5).forEach { rating ->
                FilterChip(
                    selected = value == rating,
                    onClick = { onValueChange(if (value == rating) 0 else rating) },
                    label = { Text(rating.toString()) },
                )
            }
        }
    }
}

private fun achievementTitle(value: SessionAchievement): String = when (value.level) {
    CelebrationLevel.QUIET -> "Session logged"
    CelebrationLevel.SOLID -> "Good work"
    CelebrationLevel.STRONG -> "Strong session"
    CelebrationLevel.FULL -> "Target met"
    CelebrationLevel.EXCEEDED -> "Target exceeded"
}

private fun CelebrationLevel.readableName(): String = when (this) {
    CelebrationLevel.QUIET -> "Partial target"
    CelebrationLevel.SOLID -> "Solid"
    CelebrationLevel.STRONG -> "Strong"
    CelebrationLevel.FULL -> "Full target"
    CelebrationLevel.EXCEEDED -> "Above target"
}

private val QUALITY_LABELS = listOf("Poor", "Off", "Fine", "Good", "Excellent")
private val DELAY_LABELS = listOf("None", "Minor", "Some", "Disruptive", "Major")
