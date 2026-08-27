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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal const val ReviewNotesMaxCharacters = 1200

private val ReviewSurface = Color(0xFF272B25)
private val ReviewField = Color(0xFF32362F)
private val ReviewPaper = Color(0xFFE1E4DA)
private val ReviewMuted = Color(0xFFC3C8BB)
private val ReviewSave = Color(0xFFC3EFAD)
private val ReviewDiscard = Color(0xFFFFB4AB)

/** Shared free-form review capture used for both exercise and whole-session notes. */
@Composable
internal fun ReviewNotesDialog(
    dialogKey: String,
    initialText: String,
    placeholder: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    title: String = "Review / Notes",
    supportingMessage: String = "This will be reviewed by Gemini Nano",
    maxCharacters: Int = ReviewNotesMaxCharacters,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var text by remember(dialogKey, initialText) {
        mutableStateOf(TextFieldValue(initialText))
    }

    LaunchedEffect(dialogKey) {
        delay(90)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        onDismissRequest = {
            if (!saving) onDismiss()
        },
        modifier = Modifier.fillMaxWidth(.88f),
        shape = RoundedCornerShape(28.dp),
        containerColor = ReviewSurface,
        tonalElevation = 0.dp,
        title = {
            Text(
                title,
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
                        if (next.text.length <= maxCharacters) text = next
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 250.dp)
                        .focusRequester(focusRequester),
                    label = { Text("Edit") },
                    placeholder = { Text(placeholder) },
                    supportingText = {
                        Text(
                            "$supportingMessage  ·  ${maxCharacters - text.text.length} characters remaining",
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
                onClick = onDismiss,
                enabled = !saving,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text("Discard", color = ReviewDiscard, fontWeight = FontWeight.Medium)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.text) },
                enabled = !saving,
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                Text(
                    if (saving) "Saving…" else "Save",
                    color = ReviewSave,
                    fontWeight = FontWeight.Medium,
                )
            }
        },
    )
}
