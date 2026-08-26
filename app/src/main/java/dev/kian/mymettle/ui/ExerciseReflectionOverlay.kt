package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val ExerciseReviewMaxCharacters = 1200
private val ReviewSurface = Color(0xFF272B25)
private val ReviewField = Color(0xFF32362F)
private val ReviewPaper = Color(0xFFE1E4DA)
private val ReviewMuted = Color(0xFFC3C8BB)
private val ReviewSave = Color(0xFFC3EFAD)
private val ReviewDiscard = Color(0xFFFFB4AB)

/**
 * Raw exercise review capture.
 *
 * The note is intentionally free-form. It is persisted unchanged in the existing reflection
 * record, while the retired structured ratings remain `unrated`. Any Gemini Nano/ML Kit
 * interpretation happens downstream and must never replace this user-authored source text.
 */
@Composable
fun ExerciseReflectionOverlay(viewModel: N2WorkoutViewModel) {
    val state = viewModel.uiState
    val target = state.reflectionTarget ?: return
    var text by remember(target.entity.id, state.reflection?.updatedAt) {
        mutableStateOf(TextFieldValue(state.reflection?.note.orEmpty()))
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.savingReflection) viewModel.dismissReflection()
        },
        modifier = Modifier.fillMaxWidth(.78f),
        shape = RoundedCornerShape(28.dp),
        containerColor = ReviewSurface,
        tonalElevation = 0.dp,
        title = {
            Text(
                "Review / Notes",
                color = ReviewPaper,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Normal,
            )
        },
        text = {
            Column {
                TextField(
                    value = text,
                    onValueChange = { next ->
                        if (next.text.length <= ExerciseReviewMaxCharacters) text = next
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 250.dp),
                    label = { Text("Edit") },
                    placeholder = {
                        Text(
                            "How did it feel? Anything about setup, comfort, technique, asymmetry or what you want to remember next time?",
                        )
                    },
                    supportingText = {
                        Text(
                            "This will be reviewed by Gemini Nano  ·  ${ExerciseReviewMaxCharacters - text.text.length} characters remaining",
                            color = ReviewMuted,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                        )
                    },
                    minLines = 7,
                    maxLines = 12,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ReviewField,
                        unfocusedContainerColor = ReviewField,
                        disabledContainerColor = ReviewField,
                        focusedTextColor = ReviewPaper,
                        unfocusedTextColor = ReviewPaper,
                        focusedLabelColor = ReviewMuted,
                        unfocusedLabelColor = ReviewMuted,
                        cursorColor = Color(0xFFA0CFD0),
                        focusedIndicatorColor = ReviewPaper,
                        unfocusedIndicatorColor = ReviewMuted.copy(alpha = .55f),
                    ),
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = viewModel::dismissReflection,
                enabled = !state.savingReflection,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text("Discard", color = ReviewDiscard, fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { viewModel.saveExerciseReview(text.text) },
                enabled = !state.savingReflection,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    if (state.savingReflection) "Saving…" else "Save",
                    color = ReviewSave,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}
