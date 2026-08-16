package dev.kian.mymettle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass

/**
 * HazeState is owned at app level. Individual destinations register the live artwork that should
 * be sampled into that state, while glass surfaces consume it through this composition local.
 */
internal val LocalMettleHazeState = staticCompositionLocalOf<HazeState?> { null }

@OptIn(ExperimentalHazeApi::class)
@Composable
internal fun MettleGlassSurface(
    modifier: Modifier,
    shape: RoundedCornerShape,
    tint: Color,
    blurRadius: Dp,
    refractionDisplacement: Dp,
    refractionStrength: Float,
    shadowElevation: Dp,
    baseColor: Color = Color.Transparent,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    enabled: Boolean = true,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val hazeState = LocalMettleHazeState.current
    val interactionSource = remember { MutableInteractionSource() }
    val glassStyle = remember(
        shape,
        tint,
        baseColor,
        blurRadius,
        refractionDisplacement,
        refractionStrength,
    ) {
        GlassStyle {
            backgroundColor(baseColor)
            tint(tint)
            shape(shape)
            optics(
                refractionStrength = refractionStrength,
                refractionHeightFraction = 0.14f,
                refractionDisplacement = refractionDisplacement,
                depth = 0.22f,
                blurRadius = blurRadius,
            )
            specularIntensity(0.34f)
            specularExponent(28f)
            fresnelExponent(3.4f)
            ambientResponse(0.42f)
            edgeSoftness(1.dp)
            lightPosition(Alignment.TopStart)
            surfaceProfile(SurfaceProfile.Squircle)
            chromaticAberrationStrength(0.035f)
            contrast(0.03f)
            whitePoint(0.015f)
            chromaMultiplier(0.96f)
            pressed {
                lightingIntensity(0.72f)
                refractionMultiplier(1.04f)
                whitePointDelta(0.025f)
                scale(0.985f)
            }
        }
    }

    val materialModifier = if (hazeState != null) {
        Modifier.hazeGlass(
            input = HazeInput.Sources(hazeState),
            style = glassStyle,
            performanceMode = HazePerformanceMode.Default,
            expandLayerBounds = true,
            interactionSource = if (onClick != null) interactionSource else null,
            interactionTransformTarget = GlassTransformTarget.MaterialAndContent,
            interactionTransformPivot = GlassTransformPivot.Center,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
        )
    } else {
        // Preview and test fallback; runtime always supplies a Haze backdrop.
        Modifier.background(tint, shape)
    }

    // Compose's elevation shadow reads weakly through a highly transparent Haze material. Draw a
    // soft physical shadow behind the material instead: enough separation to establish lift while
    // leaving the sampled backdrop visible through the glass itself.
    val liftShadowModifier = if (shadowElevation > 0.dp) {
        Modifier.dropShadow(
            shape = shape,
            shadow = Shadow(
                radius = (shadowElevation.value * 2.2f).dp,
                spread = 0.dp,
                color = Color.Black.copy(alpha = 0.24f),
                offset = DpOffset(0.dp, (shadowElevation.value * 0.8f).dp),
            ),
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(liftShadowModifier)
            .then(materialModifier)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                },
            )
            .semantics {
                if (selected != null) this.selected = selected
            }
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}
