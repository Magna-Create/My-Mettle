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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseReflectionOverlay(viewModel: N2WorkoutViewModel) {
    val state = viewModel.uiState
    val target = state.reflectionTarget ?: return
    val existing = state.reflection

    var engagement by remember(target.entity.id, existing?.updatedAt) {
        mutableIntStateOf(existing?.targetMuscleEngagement?.toIntOrNull() ?: 0)
    }
    var execution by remember(target.entity.id, existing?.updatedAt) {
        mutableStateOf(existing?.execution?.takeUnless { it == "unrated" })
    }
    var enjoyment by remember(target.entity.id, existing?.updatedAt) {
        mutableIntStateOf(existing?.enjoyment?.toIntOrNull() ?: 0)
    }
    var comfort by remember(target.entity.id, existing?.updatedAt) {
        mutableStateOf(existing?.comfort?.takeUnless { it == "unrated" })
    }
    var note by remember(target.entity.id, existing?.updatedAt) { mutableStateOf(existing?.note.orEmpty()) }

    ModalBottomSheet(onDismissRequest = viewModel::dismissReflection) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text("How did that feel?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    target.entity.exerciseNameSnapshot,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            SevenPointRating(
                title = "Target muscle",
                value = engagement,
                lowLabel = "Barely",
                highLabel = "Very clear",
                onValueChange = { engagement = it },
            )

            ChoiceRating(
                title = "Form",
                choices = listOf("clean" to "Clean", "mixed" to "Mixed", "poor" to "Poor"),
                selected = execution,
                onSelected = { execution = if (execution == it) null else it },
            )

            SevenPointRating(
                title = "Vibe",
                value = enjoyment,
                lowLabel = "Hated it",
                highLabel = "Loved it",
                onValueChange = { enjoyment = it },
            )

            ChoiceRating(
                title = "Comfort",
                choices = listOf(
                    "good" to "Good",
                    "fine" to "Fine",
                    "uncomfortable" to "Uncomfortable",
                    "pain" to "Pain",
                ),
                selected = comfort,
                onSelected = { comfort = if (comfort == it) null else it },
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(500) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Optional note") },
                minLines = 2,
                maxLines = 4,
            )

            MettleGlassActionButton(
                onClick = {
                    viewModel.saveExerciseReflection(
                        targetMuscleEngagement = engagement.takeIf { it > 0 },
                        execution = execution,
                        enjoyment = enjoyment.takeIf { it > 0 },
                        comfort = comfort,
                        note = note,
                    )
                },
                enabled = !state.savingReflection,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.savingReflection) "Saving…" else "Save reflection")
            }

            TextButton(onClick = viewModel::dismissReflection, modifier = Modifier.fillMaxWidth()) {
                Text("Skip")
            }
        }
    }
}

@Composable
private fun SevenPointRating(
    title: String,
    value: Int,
    lowLabel: String,
    highLabel: String,
    onValueChange: (Int) -> Unit,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                when (value) {
                    0 -> "Not rated"
                    1 -> lowLabel
                    7 -> highLabel
                    else -> "$value / 7"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            (1..7).forEach { rating ->
                MettleGlassChoiceChip(
                    selected = value == rating,
                    onClick = { onValueChange(if (value == rating) 0 else rating) },
                    label = { Text(rating.toString()) },
                )
            }
        }
    }
}

@Composable
private fun ChoiceRating(
    title: String,
    choices: List<Pair<String, String>>,
    selected: String?,
    onSelected: (String) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { (value, label) ->
                MettleGlassChoiceChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}
