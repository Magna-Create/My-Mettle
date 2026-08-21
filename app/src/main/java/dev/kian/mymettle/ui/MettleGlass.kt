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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.innerShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.clipPath
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
import kotlin.math.sqrt
import kotlin.random.Random

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
    innerShadowRadius: Dp = 0.dp,
    innerShadowOffsetY: Dp = 0.dp,
    innerShadowAlpha: Float = 0f,
    grainStrength: Float = 1f,
    enabled: Boolean = true,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
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

    // Inset shading belongs to the component rather than the optical material. Selected toggles,
    // primary actions and dock-like controls can therefore keep their own pressure/depth behaviour
    // while sharing the exact same glass recipe.
    val insetShadowModifier = if (innerShadowRadius > 0.dp && innerShadowAlpha > 0f) {
        Modifier.innerShadow(
            shape = shape,
            shadow = Shadow(
                radius = innerShadowRadius,
                spread = 0.dp,
                color = Color.Black.copy(alpha = innerShadowAlpha),
                offset = DpOffset(0.dp, innerShadowOffsetY),
            ),
        )
    } else {
        Modifier
    }

    // A very faint, deterministic film-grain layer helps the eye separate the refracted material
    // from whatever sits behind it without tinting the glass. Points are generated once per size
    // and radially stretched near the perimeter, echoing the stronger optical bend at rounded
    // glass edges. The grain is explicitly masked to the component outline.
    val grainModifier = if (grainStrength > 0f) {
        Modifier.drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val maskPath = Path().apply {
                when (outline) {
                    is Outline.Rectangle -> addRect(outline.rect)
                    is Outline.Rounded -> addRoundRect(outline.roundRect)
                    is Outline.Generic -> addPath(outline.path)
                }
            }

            val area = size.width * size.height
            val cellPx = 5.2f * density
            val pointCount = (area / (cellPx * cellPx)).toInt().coerceIn(20, 260)
            val random = Random(
                0x4D371E +
                    (size.width.toInt() * 31) +
                    (size.height.toInt() * 17),
            )
            val centre = Offset(size.width / 2f, size.height / 2f)
            val halfWidth = centre.x.coerceAtLeast(1f)
            val halfHeight = centre.y.coerceAtLeast(1f)

            val lightCentre = ArrayList<Offset>(pointCount / 3)
            val darkCentre = ArrayList<Offset>(pointCount / 3)
            val lightEdge = ArrayList<Offset>(pointCount / 4)
            val darkEdge = ArrayList<Offset>(pointCount / 4)

            repeat(pointCount) {
                val sourceX = random.nextFloat() * size.width
                val sourceY = random.nextFloat() * size.height
                val nx = (sourceX - centre.x) / halfWidth
                val ny = (sourceY - centre.y) / halfHeight
                val radial = sqrt((nx * nx) + (ny * ny))
                val edge = ((radial - 0.56f) / 0.44f).coerceIn(0f, 1f)
                val warp = 1f + (0.052f * edge * edge)
                val point = Offset(
                    x = (centre.x + ((sourceX - centre.x) * warp)).coerceIn(0f, size.width),
                    y = (centre.y + ((sourceY - centre.y) * warp)).coerceIn(0f, size.height),
                )
                val bright = random.nextBoolean()
                if (edge > 0.58f) {
                    if (bright) lightEdge += point else darkEdge += point
                } else {
                    if (bright) lightCentre += point else darkCentre += point
                }
            }

            val strokeWidth = 0.34.dp.toPx().coerceAtLeast(0.8f)
            onDrawWithContent {
                drawContent()
                clipPath(maskPath) {
                    drawPoints(
                        points = lightCentre,
                        pointMode = PointMode.Points,
                        color = Color.White.copy(alpha = 0.010f * grainStrength),
                        strokeWidth = strokeWidth,
                    )
                    drawPoints(
                        points = darkCentre,
                        pointMode = PointMode.Points,
                        color = Color.Black.copy(alpha = 0.007f * grainStrength),
                        strokeWidth = strokeWidth,
                    )
                    drawPoints(
                        points = lightEdge,
                        pointMode = PointMode.Points,
                        color = Color.White.copy(alpha = 0.020f * grainStrength),
                        strokeWidth = strokeWidth,
                    )
                    drawPoints(
                        points = darkEdge,
                        pointMode = PointMode.Points,
                        color = Color.Black.copy(alpha = 0.013f * grainStrength),
                        strokeWidth = strokeWidth,
                    )
                }
            }
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(liftShadowModifier)
            .then(materialModifier)
            .then(insetShadowModifier)
            .then(
                if (borderWidth > 0.dp) {
                    Modifier.border(borderWidth, borderColor, shape)
                } else {
                    Modifier
                },
            )
            .then(grainModifier)
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
        contentAlignment = contentAlignment,
    ) {
        content()
    }
}
