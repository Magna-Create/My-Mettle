package dev.kian.mymettle.ui

import androidx.compose.runtime.Composable

/**
 * Whole-session free-form review capture.
 *
 * The retired 1–5 session ratings are no longer collected in the UI. Their persistence fields are
 * left unset while the raw session note remains canonical for later versioned interpretation.
 */
@Composable
fun SessionOutcomeOverlay(viewModel: N2WorkoutViewModel) {
    val state = viewModel.uiState
    val workout = state.workout ?: return
    if (!state.sessionCompleted || workout.session.status != "completed") return

    val review = state.sessionReview

    ReviewNotesDialog(
        dialogKey = "session:${workout.session.id}:${review?.updatedAt.orEmpty()}",
        initialText = review?.note.orEmpty(),
        placeholder = "How did the workout feel overall? Anything about energy, sleep, illness, stress, pacing, exercise order, interruptions or what you want to remember next time?",
        saving = state.savingReview,
        onDismiss = viewModel::leaveCompletedSession,
        onSave = { note ->
            viewModel.saveSessionReview(
                exerciseOrder = null,
                organisation = null,
                pacing = null,
                delayImpact = null,
                note = note,
            )
            viewModel.leaveCompletedSession()
        },
    )
}
