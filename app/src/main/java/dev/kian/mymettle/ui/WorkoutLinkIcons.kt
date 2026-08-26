package dev.kian.mymettle.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Link glyphs matched to the Setup View / Setup Edit Mode Figma controls. */
internal object WorkoutLinkIcons {
    val Link: ImageVector by lazy {
        workoutLinkIcon(
            name = "workout_open_link",
            pathData = "M3.9 12C3.9 10.29 5.29 8.9 7 8.9H11V7H7C4.24 7 2 9.24 2 12C2 14.76 4.24 17 7 17H11V15.1H7C5.29 15.1 3.9 13.71 3.9 12ZM8 13H16V11H8V13ZM17 7H13V8.9H17C18.71 8.9 20.1 10.29 20.1 12C20.1 13.71 18.71 15.1 17 15.1H13V17H17C19.76 17 22 14.76 22 12C22 9.24 19.76 7 17 7Z",
        )
    }

    val AddLink: ImageVector by lazy {
        workoutLinkIcon(
            name = "workout_add_link",
            pathData = "M3.9 12C3.9 10.29 5.29 8.9 7 8.9H11V7H7C4.24 7 2 9.24 2 12C2 14.76 4.24 17 7 17H11V15.1H7C5.29 15.1 3.9 13.71 3.9 12ZM8 13H15V11H8V13ZM17 7H13V8.9H17C18.46 8.9 19.69 9.91 20.02 11.27C20.61 11.11 21.24 11.02 21.89 11.02H22C21.54 8.72 19.48 7 17 7ZM19 14V17H16V19H19V22H21V19H24V17H21V14H19Z",
        )
    }
}

private fun workoutLinkIcon(name: String, pathData: String): ImageVector = ImageVector.Builder(
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
