package dev.kian.mymettle.ui

/**
 * Raw exercise review capture.
 *
 * The note is intentionally free-form. It is persisted unchanged in the existing reflection
 * record, while the retired structured ratings remain `unrated`. Any Gemini Nano/ML Kit
 * interpretation happens downstream and must never replace this user-authored source text.
 */
@androidx.compose.runtime.Composable
fun ExerciseReflectionOverlay(viewModel: N2WorkoutViewModel) {
    val state = viewModel.uiState
    val target = state.reflectionTarget ?: return

    ReviewNotesDialog(
        dialogKey = "exercise:${target.entity.id}:${state.reflection?.updatedAt.orEmpty()}",
        initialText = state.reflection?.note.orEmpty(),
        placeholder = "How did it feel? Anything about setup, comfort, technique, asymmetry or what you want to remember next time?",
        saving = state.savingReflection,
        onDismiss = viewModel::dismissReflection,
        onSave = viewModel::saveExerciseReview,
    )
}
