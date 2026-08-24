package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared search composition used by Library and in-workout substitution. */
@Composable
internal fun MettleExerciseSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    placeholder: String = "Search for specific exercises",
    foreground: Color = MaterialTheme.colorScheme.onSurface,
    muted: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    MettleControlGlassSurface(
        modifier = modifier.fillMaxWidth().height(height),
        shape = CircleShape,
        tint = Color.White.copy(alpha = .025f),
        borderColor = Color.White.copy(alpha = .10f),
        shadowElevation = 3.dp,
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("☰", color = muted, fontSize = 20.sp)
            Spacer(Modifier.width(14.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = foreground, fontSize = 17.sp, lineHeight = 22.sp),
                singleLine = true,
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(placeholder, color = muted, fontSize = 17.sp, lineHeight = 22.sp)
                    } else {
                        inner()
                    }
                },
            )
            Text("⌕", color = if (value.isEmpty()) muted else accent, fontSize = 24.sp)
        }
    }
}
