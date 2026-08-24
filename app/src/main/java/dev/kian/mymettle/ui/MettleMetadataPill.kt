package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

/** Shared geometry and optical behaviour for compact workout/session metadata. */
@Composable
internal fun MettleMetadataPill(
    label: String,
    modifier: Modifier = Modifier,
    height: Dp,
    cornerRadius: Dp,
    horizontalPadding: Dp,
    fill: Color,
    borderWidth: Dp,
    borderColor: Color,
    textColor: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    shadowBlurRadius: Dp,
    shadowOffsetY: Dp,
    shadowAlpha: Float,
    glassBlurRadius: Dp,
    refractionDisplacement: Dp,
    refractionStrength: Float,
) {
    val shape = RoundedCornerShape(cornerRadius)
    val lift = if (shadowBlurRadius > 0.dp && shadowAlpha > 0f) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = shadowBlurRadius,
                spread = 0.dp,
                color = Color.Black.copy(alpha = shadowAlpha),
                offset = DpOffset(0.dp, shadowOffsetY),
            ),
        )
    } else {
        Modifier
    }

    MettleGlassSurface(
        modifier = modifier.height(height).then(lift),
        shape = shape,
        tint = fill,
        blurRadius = glassBlurRadius,
        refractionDisplacement = refractionDisplacement,
        refractionStrength = refractionStrength,
        shadowElevation = 0.dp,
        borderWidth = borderWidth,
        borderColor = borderColor,
        grainStrength = .45f,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.height(height).padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = fontSize,
                lineHeight = lineHeight,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}
