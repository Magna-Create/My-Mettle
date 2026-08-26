package dev.kian.mymettle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Material-symbol glyphs used by the Figma exit-workout interaction. */
internal object WorkoutExitIcons {
    val Cancel: ImageVector by lazy {
        exitIcon(
            "exit_cancel",
            "M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM17 15.59L15.59 17L12 13.41L8.41 17L7 15.59L10.59 12L7 8.41L8.41 7L12 10.59L15.59 7L17 8.41L13.41 12L17 15.59Z",
        )
    }

    val Verified: ImageVector by lazy {
        exitIcon(
            "exit_verified",
            "M23 12L20.56 9.21L20.9 5.52L17.29 4.7L15.4 1.5L12 2.96L8.6 1.5L6.71 4.69L3.1 5.5L3.44 9.2L1 12L3.44 14.79L3.1 18.49L6.71 19.3L8.6 22.5L12 21.03L15.4 22.49L17.29 19.3L20.9 18.48L20.56 14.79L23 12ZM10.09 16.72L6.29 12.91L7.77 11.43L10.09 13.76L15.94 7.89L17.42 9.37L10.09 16.72Z",
        )
    }

    val CheckCircle: ImageVector by lazy {
        exitIcon(
            "exit_check_circle",
            "M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12C22 6.48 17.52 2 12 2ZM10 17L5 12L6.41 10.59L10 14.17L17.59 6.58L19 8L10 17Z",
        )
    }

    val DeleteForever: ImageVector by lazy {
        exitIcon(
            "exit_delete_forever",
            "M6 19C6 20.1 6.9 21 8 21H16C17.1 21 18 20.1 18 19V7H6V19ZM8.46 10.41L9.87 9L12 11.13L14.13 9L15.54 10.41L13.41 12.54L15.54 14.67L14.13 16.08L12 13.95L9.87 16.08L8.46 14.67L10.59 12.54L8.46 10.41ZM15.5 4L14.5 3H9.5L8.5 4H5V6H19V4H15.5Z",
        )
    }
}

private fun exitIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(pathData).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()
