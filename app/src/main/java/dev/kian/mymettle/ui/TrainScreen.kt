package dev.kian.mymettle.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.kian.mymettle.data.local.entity.SetRecordEntity
import dev.kian.mymettle.workout.ActiveWorkoutExercise

internal class TrainSetDraft(set: SetRecordEntity) {
    var load by mutableStateOf(set.load?.let(::formatDecimal).orEmpty())
    var reps by mutableStateOf(set.reps?.toString().orEmpty())
    var durationSeconds by mutableStateOf(set.durationSeconds?.toString().orEmpty())
    var distanceMetres by mutableStateOf(set.distanceMetres?.let(::formatDecimal).orEmpty())
}

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
    FigmaWorkoutSession(
        state = state,
        drafts = drafts,
        onOpenSettings = onOpenSettings,
        onOpenAccount = onOpenAccount,
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

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("My Mettle couldn’t do that") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
        )
    }
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
