package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer

/**
 * Shared interactive-glass material derived from the global hotbar.
 *
 * Optics are intentionally common across controls. Geometry, outer lift and inset/selected shadows
 * remain component-owned so a small chip, a header capsule and a primary action do not acquire the
 * same visual weight merely because they are made from the same material.
 */
@Composable
internal fun MettleControlGlassSurface(
    modifier: Modifier,
    shape: RoundedCornerShape = CircleShape,
    tint: Color = Color.White.copy(alpha = 0.028f),
    enabled: Boolean = true,
    selected: Boolean? = null,
    shadowElevation: Dp = 4.dp,
    innerShadowRadius: Dp = 0.dp,
    innerShadowOffsetY: Dp = 0.dp,
    innerShadowAlpha: Float = 0f,
    borderWidth: Dp = 0.7.dp,
    borderColor: Color = Color.White.copy(alpha = 0.22f),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    MettleGlassSurface(
        modifier = modifier,
        shape = shape,
        tint = tint,
        baseColor = Color.Transparent,
        // These are the current global hotbar optics. Keep this primitive as the material source
        // of truth rather than re-tuning individual buttons by eye.
        blurRadius = 8.5.dp,
        refractionDisplacement = 9.dp,
        refractionStrength = 0.70f,
        shadowElevation = shadowElevation,
        borderWidth = borderWidth,
        borderColor = borderColor,
        innerShadowRadius = innerShadowRadius,
        innerShadowOffsetY = innerShadowOffsetY,
        innerShadowAlpha = innerShadowAlpha,
        enabled = enabled,
        selected = selected,
        onClick = onClick,
    ) {
        content()
    }
}

/** Material FilterChip-shaped API so older screens can migrate without changing their state model. */
@Composable
internal fun MettleGlassChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = if (selected) {
        MettleOnPrimaryContainer.copy(alpha = 0.11f)
    } else {
        Color.White.copy(alpha = 0.028f)
    }
    val contentColor = Color.White.copy(alpha = if (enabled) 0.94f else 0.42f)

    MettleControlGlassSurface(
        modifier = modifier
            .heightIn(min = 44.dp)
            .widthIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        tint = tint,
        enabled = enabled,
        selected = selected,
        shadowElevation = if (selected) 4.dp else 2.5.dp,
        innerShadowRadius = if (selected) 3.dp else 0.dp,
        innerShadowOffsetY = if (selected) 1.dp else 0.dp,
        innerShadowAlpha = if (selected) 0.14f else 0f,
        borderColor = Color.White.copy(alpha = if (selected) 0.28f else 0.18f),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                ) {
                    label()
                }
            }
        }
    }
}

/** Material AssistChip-shaped API for compact disclosure controls. */
@Composable
internal fun MettleGlassAssistChip(
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MettleControlGlassSurface(
        modifier = modifier.heightIn(min = 40.dp),
        shape = RoundedCornerShape(14.dp),
        tint = Color.White.copy(alpha = 0.028f),
        enabled = enabled,
        shadowElevation = 2.5.dp,
        borderColor = Color.White.copy(alpha = 0.18f),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White.copy(alpha = if (enabled) 0.90f else 0.40f)) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) { label() }
            }
        }
    }
}

/** Material Button/OutlinedButton/FilledTonalButton-shaped API for page-level actions. */
@Composable
internal fun MettleGlassActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = true,
    shadowElevation: Dp = 4.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val tint = if (accent) {
        MettleOnPrimaryContainer.copy(alpha = if (enabled) 0.10f else 0.035f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.035f else 0.018f)
    }
    val contentColor = Color.White.copy(alpha = if (enabled) 0.96f else 0.42f)

    MettleControlGlassSurface(
        modifier = modifier.defaultMinSize(minHeight = 48.dp),
        shape = CircleShape,
        tint = tint,
        enabled = enabled,
        shadowElevation = if (enabled) shadowElevation else 1.dp,
        borderColor = Color.White.copy(alpha = if (enabled) 0.24f else 0.10f),
        onClick = onClick,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }
    }
}

/**
 * Legacy Figma controls still pass their old coloured-glass tint values. This keeps their semantic
 * hue/selected strength while translating the paint-heavy alpha into the shared optical material.
 */
internal fun Color.asMettleControlGlassTint(): Color = copy(alpha = alpha * 0.25f)
