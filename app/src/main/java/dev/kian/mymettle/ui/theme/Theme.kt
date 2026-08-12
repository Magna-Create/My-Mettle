package dev.kian.mymettle.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val MettlePrimary = Color(0xFFA8D293)
val MettlePrimaryContainer = Color(0xFF2B4F1E)
val MettleOnPrimaryContainer = Color(0xFFC3EFAD)
val MettleOnSurface = Color(0xFFE1E4DA)
val MettleOnSurfaceVariant = Color(0xFFC3C8BB)
val MettleOutlineVariant = Color(0xFF43483F)
val MettleSurfaceContainerLow = Color(0xFF191D17)
val MettleBackground = Color(0xFF11140F)

private val MettleDarkColorScheme = darkColorScheme(
    primary = MettlePrimary,
    onPrimary = Color(0xFF143808),
    primaryContainer = MettlePrimaryContainer,
    onPrimaryContainer = MettleOnPrimaryContainer,
    secondary = MettlePrimary,
    onSecondary = Color(0xFF143808),
    secondaryContainer = Color(0xFF35462F),
    onSecondaryContainer = MettleOnSurface,
    background = MettleBackground,
    onBackground = MettleOnSurface,
    surface = MettleBackground,
    onSurface = MettleOnSurface,
    surfaceVariant = Color(0xFF3B4536),
    onSurfaceVariant = MettleOnSurfaceVariant,
    outline = Color(0xFF8D9387),
    outlineVariant = MettleOutlineVariant,
    surfaceContainerLowest = Color(0xFF0C0F0B),
    surfaceContainerLow = MettleSurfaceContainerLow,
    surfaceContainer = Color(0xFF1D221B),
    surfaceContainerHigh = Color(0xFF272E22),
    surfaceContainerHighest = Color(0xFF313A2B),
)

private val MettleLightColorScheme = lightColorScheme(
    primary = Color(0xFF416533),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC3EFAD),
    onPrimaryContainer = MettlePrimaryContainer,
)

private val RobotoFamily = FontFamily.SansSerif

private val MettleTypography = Typography(
    displayMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = RobotoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

@Composable
fun MyMettleTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MettleDarkColorScheme
        else -> MettleLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MettleTypography,
        content = content,
    )
}
