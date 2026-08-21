package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkoutExercise
import dev.kian.mymettle.workout.evaluateLoadExpression

internal class TrainSetDraft(set: SetRecordEntity) {
    var load by mutableStateOf(set.load?.let(::formatDecimal).orEmpty())
    var reps by mutableStateOf(set.reps?.toString().orEmpty())
    var durationSeconds by mutableStateOf(set.durationSeconds?.toString().orEmpty())
    var distanceMetres by mutableStateOf(set.distanceMetres?.let(::formatDecimal).orEmpty())
}

private data class LoadCalculatorTarget(
    val exercise: ActiveWorkoutExercise,
    val set: SetRecordEntity,
)

/** Active-session renderer only. Session choice belongs exclusively to the intensity selector. */
@Composable
fun TrainScreen(
    viewModel: N2WorkoutViewModel,
    onOpenSettings: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
) {
    val state = viewModel.uiState
    if (state.workout == null) return

    val drafts = remember { mutableStateMapOf<String, TrainSetDraft>() }
    var calculatorTarget by remember { mutableStateOf<LoadCalculatorTarget?>(null) }
    FigmaWorkoutSession(
        state = state,
        drafts = drafts,
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
        onOpenCalculator = { exercise, set -> calculatorTarget = LoadCalculatorTarget(exercise, set) },
        onSaveDraft = { exercise, set, draft ->
            persistDraft(viewModel, exercise, set, draft, logged = set.completedAt != null)
        },
        onLogSet = { exercise, set, draft ->
            persistDraft(viewModel, exercise, set, draft, logged = true)
        },
        onSwapExercise = viewModel::requestExerciseSwap,
        onSelectSwap = viewModel::swapExercise,
        onDismissSwap = viewModel::dismissExerciseSwap,
        onShowSets = viewModel::showWorkoutSets,
        onShowSetup = viewModel::showExerciseSetup,
        onToggleExercise = viewModel::toggleExercise,
        onRateExercise = viewModel::rateExercise,
        onDismissSheet = viewModel::dismissWorkoutSheet,
        onShowDelete = viewModel::showDeleteConfirmation,
        onCompleteSession = viewModel::completeSession,
        onDiscardSession = viewModel::discardActiveSession,
    )

    calculatorTarget?.let { target ->
        val draft = drafts.getOrPut(target.set.id) { TrainSetDraft(target.set) }
        LoadCalculatorDialog(
            initialValue = draft.load,
            onDismiss = { calculatorTarget = null },
            onUseValue = { value ->
                draft.load = formatDecimal(value)
                persistDraft(viewModel, target.exercise, target.set, draft, logged = target.set.completedAt != null)
                calculatorTarget = null
            },
        )
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("My Mettle couldn’t do that") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
}

@Composable
private fun LoadCalculatorDialog(
    initialValue: String,
    onDismiss: () -> Unit,
    onUseValue: (Double) -> Unit,
) {
    var expression by remember(initialValue) { mutableStateOf(initialValue) }
    val evaluated = remember(expression) { runCatching { evaluateLoadExpression(expression) }.getOrNull() }
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf("0", ".", "(", "+"),
        listOf(")", "⌫", "C", "="),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Load calculator") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MettleControlGlassSurface(
                    modifier = Modifier.fillMaxWidth().height(74.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = .05f),
                    borderColor = MaterialTheme.colorScheme.primary.copy(alpha = .34f),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(expression.ifBlank { "0" }, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            evaluated?.let(::formatDecimal) ?: "—",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            MettleGlassActionButton(
                                onClick = {
                                    expression = when (key) {
                                        "C" -> ""
                                        "⌫" -> expression.dropLast(1)
                                        "=" -> evaluated?.let(::formatDecimal) ?: expression
                                        "×" -> expression + "*"
                                        "÷" -> expression + "/"
                                        "−" -> expression + "-"
                                        else -> expression + key
                                    }
                                },
                                modifier = Modifier.weight(1f).height(48.dp),
                                contentPadding = PaddingValues(0.dp),
                                accent = key == "=",
                            ) { Text(key) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            MettleGlassActionButton(onClick = { evaluated?.let(onUseValue) }, enabled = evaluated != null) {
                Text("Use value")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun persistDraft(
    viewModel: N2WorkoutViewModel,
    exercise: ActiveWorkoutExercise,
    set: SetRecordEntity,
    draft: TrainSetDraft,
    logged: Boolean,
) {
    if (exercise.entity.status == "completed") return
    viewModel.saveSet(
        exercise = exercise,
        setId = set.id,
        load = draft.load.toDoubleOrNull(),
        reps = draft.reps.toIntOrNull(),
        durationSeconds = draft.durationSeconds.toIntOrNull(),
        distanceMetres = draft.distanceMetres.toDoubleOrNull(),
        logged = logged,
    )
}

private fun formatDecimal(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString().trimEnd('0').trimEnd('.')
