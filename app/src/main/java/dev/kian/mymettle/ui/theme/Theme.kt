package dev.kian.mymettle.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MyMettleLightColors = lightColorScheme(
    primary = Color(0xFF25231C),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFFC95E43),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4EFE6),
    onBackground = Color(0xFF25231C),
    surface = Color(0xFFFAF7F0),
    onSurface = Color(0xFF25231C),
    surfaceVariant = Color(0xFFECE5D8),
    onSurfaceVariant = Color(0xFF676258),
    outline = Color(0xFFD6D0C5),
)

@Composable
fun MyMettleTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MyMettleLightColors,
        content = content,
    )
}
