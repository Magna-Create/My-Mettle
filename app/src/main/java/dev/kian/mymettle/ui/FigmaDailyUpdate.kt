package dev.kian.mymettle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.R
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer
import dev.kian.mymettle.ui.theme.MettleOnSurface
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant
import dev.kian.mymettle.ui.theme.MettleOutlineVariant
import dev.kian.mymettle.ui.theme.MettlePrimary
import dev.kian.mymettle.ui.theme.MettlePrimaryContainer
import dev.kian.mymettle.ui.theme.MettleSurfaceContainerLow

private const val FigmaReferenceWidth = 453f

private data class FigmaMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

private val FigmaNotoEmoji = FontFamily(
    Font(R.font.noto_emoji_variable, FontWeight.Medium),
)

private data class FigmaProgrammeDay(
    val storageSymbol: String,
    val displaySymbol: String,
)

private val FigmaProgrammeDays = listOf(
    FigmaProgrammeDay("ψ", "ψ"),
    FigmaProgrammeDay("φ", "ϕ"),
    FigmaProgrammeDay("π", "π"),
    FigmaProgrammeDay("&", "&"),
)

private data class FigmaNutritionGuidance(
    val emoji: String,
    val title: String,
    val timing: String,
    val before: String? = null,
    val after: String? = null,
    val combined: String? = null,
    val unit: String,
)

private val FigmaNutritionItems = listOf(
    FigmaNutritionGuidance(
        emoji = "🧀",
        title = "Protein",
        timing = "1-2 Hours before training.",
        before = "65",
        after = "65",
        unit = "G",
    ),
    FigmaNutritionGuidance(
        emoji = "🥪",
        title = "Carbohydrates",
        timing = "2-4 Hours before training.",
        before = "65",
        after = "65",
        unit = "G",
    ),
    FigmaNutritionGuidance(
        emoji = "💧",
        title = "Water",
        timing = "Throughout Workout",
        combined = "400-500",
        unit = "mL",
    ),
)

@Composable
internal fun FigmaDailyUpdateScreen(
    selectedDay: String,
    dayDisplaySymbol: String,
    daySpokenName: String,
    exerciseCount: Int,
    daySelectionEnabled: Boolean,
    beginEnabled: Boolean,
    beginLabel: String,
    onDaySelected: (String) -> Unit,
    onBeginSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    FigmaDailyViewport { metrics ->
        Box(modifier = Modifier.fillMaxSize()) {
            FigmaDailyAppBar(
                modifier = Modifier.offset(y = metrics.dp(40)),
                metrics = metrics,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            FigmaHeroGreeting(
                modifier = Modifier.offset(x = metrics.dp(21), y = metrics.dp(120)),
                displaySymbol = dayDisplaySymbol,
                spokenName = daySpokenName,
                metrics = metrics,
            )

            FigmaInsightChips(
                modifier = Modifier.offset(x = metrics.dp(21), y = metrics.dp(250)),
                exerciseCount = exerciseCount,
                metrics = metrics,
            )

            FigmaNutritionHeading(
                modifier = Modifier.offset(x = metrics.dp(24), y = metrics.dp(298)),
                metrics = metrics,
            )

            FigmaNutritionItems.forEachIndexed { index, guidance ->
                FigmaNutritionCard(
                    modifier = Modifier.offset(
                        x = metrics.dp(21),
                        y = metrics.dp(347 + (index * 108)),
                    ),
                    guidance = guidance,
                    metrics = metrics,
                )
            }

            HorizontalDivider(
                modifier = Modifier
                    .offset(x = metrics.dp(21), y = metrics.dp(671.5))
                    .width(metrics.dp(414)),
                thickness = metrics.dp(1),
                color = MettleOutlineVariant,
            )

            FigmaSessionActions(
                modifier = Modifier.offset(y = metrics.dp(687.626)),
                selectedDay = selectedDay,
                enabled = daySelectionEnabled,
                beginEnabled = beginEnabled,
                beginLabel = beginLabel,
                onDaySelected = onDaySelected,
                onBeginSession = onBeginSession,
                metrics = metrics,
            )
        }
    }
}

@Composable
private fun FigmaDailyViewport(content: @Composable (FigmaMetrics) -> Unit) {
    val widthClass = LocalMettleWindowWidthClass.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val viewportWidth = when (widthClass) {
            MettleWindowWidthClass.Compact -> minOf(maxWidth, FigmaReferenceWidth.dp)
            MettleWindowWidthClass.Medium,
            MettleWindowWidthClass.Expanded,
            -> minOf(maxWidth, FigmaReferenceWidth.dp)
        }
        val metrics = FigmaMetrics(
            scale = (viewportWidth.value / FigmaReferenceWidth).coerceAtMost(1f),
        )

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .align(Alignment.TopCenter),
        ) {
            content(metrics)
        }
    }
}

@Composable
private fun FigmaDailyAppBar(
    modifier: Modifier,
    metrics: FigmaMetrics,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(metrics.dp(70.369))
            .padding(start = metrics.dp(21), end = metrics.dp(18)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My Mettle",
                color = MettleOnPrimaryContainer,
                fontSize = metrics.sp(24.2),
                lineHeight = metrics.sp(31),
            )
            Text(
                text = "Daily Update",
                color = MettleOnSurfaceVariant,
                fontSize = metrics.sp(13.2),
                lineHeight = metrics.sp(18),
                fontWeight = FontWeight.Medium,
            )
        }

        FigmaTintedSurface(
            modifier = Modifier
                .width(metrics.dp(81))
                .height(metrics.dp(49.388)),
            shape = CircleShape,
            fill = MettleOnPrimaryContainer.copy(alpha = 0.30f),
            shadowElevation = metrics.dp(3.087),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(metrics.dp(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FigmaHeaderIconButton(
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    metrics = metrics,
                )
                FigmaHeaderIconButton(
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account and history",
                    onClick = onOpenAccount,
                    metrics = metrics,
                )
            }
        }
    }
}

@Composable
private fun FigmaHeaderIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    metrics: FigmaMetrics,
) {
    Box(
        modifier = Modifier
            .size(metrics.dp(34.5))
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(metrics.dp(16.4)),
        )
    }
}

@Composable
private fun FigmaHeroGreeting(
    modifier: Modifier,
    displaySymbol: String,
    spokenName: String,
    metrics: FigmaMetrics,
) {
    Row(
        modifier = modifier
            .width(metrics.dp(414))
            .height(metrics.dp(114)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(top = metrics.dp(14))) {
            Text(
                text = "Hey Kian,",
                color = MettleOnSurface,
                fontSize = metrics.sp(45),
                lineHeight = metrics.sp(52),
            )
            Spacer(Modifier.height(metrics.dp(2)))
            Text(
                text = "Ready for $spokenName?",
                color = MettleOnSurface,
                fontSize = metrics.sp(24),
                lineHeight = metrics.sp(32),
            )
        }

        Box(
            modifier = Modifier
                .padding(top = metrics.dp(17), end = metrics.dp(1))
                .size(metrics.dp(80))
                .clip(CircleShape)
                .background(MettlePrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displaySymbol,
                color = MettleOnPrimaryContainer,
                fontSize = metrics.sp(27.6),
                lineHeight = metrics.sp(46),
            )
        }
    }
}

@Composable
private fun FigmaInsightChips(
    modifier: Modifier,
    exerciseCount: Int,
    metrics: FigmaMetrics,
) {
    val chips = listOf(
        "$exerciseCount Exercises" to 97f,
        "5-min warm-up" to 121f,
        "2/3 Core Days" to 114f,
        "No.1 Improvement from π last week was: [EXERCISE NAME]" to 404f,
    )

    Box(
        modifier = modifier
            .width(metrics.dp(414))
            .height(metrics.dp(32))
            .clipToBounds(),
    ) {
        Row(
            modifier = Modifier
                .offset(x = metrics.dp(3))
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(metrics.dp(8)),
        ) {
            chips.forEach { (label, width) ->
                FigmaTintedSurface(
                    modifier = Modifier
                        .width(metrics.dp(width))
                        .height(metrics.dp(32)),
                    shape = RoundedCornerShape(metrics.dp(8)),
                    fill = Color.Black.copy(alpha = 0.01f),
                    shadowElevation = metrics.dp(4),
                    borderWidth = metrics.dp(1),
                    borderColor = MettleOutlineVariant,
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = MettleOnSurfaceVariant,
                            fontSize = metrics.sp(14),
                            lineHeight = metrics.sp(20),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FigmaNutritionHeading(modifier: Modifier, metrics: FigmaMetrics) {
    Column(
        modifier = modifier
            .width(metrics.dp(395))
            .height(metrics.dp(33)),
    ) {
        HorizontalDivider(
            thickness = metrics.dp(1),
            color = MettleOutlineVariant,
        )
        Spacer(Modifier.height(metrics.dp(7)))
        Text(
            text = "Pre and Post Workout Dietary Recommendations:",
            color = MettleOnSurfaceVariant,
            fontSize = metrics.sp(14),
            lineHeight = metrics.sp(20),
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun FigmaNutritionCard(
    modifier: Modifier,
    guidance: FigmaNutritionGuidance,
    metrics: FigmaMetrics,
) {
    val shape = RoundedCornerShape(metrics.dp(13.8))
    Row(
        modifier = modifier
            .width(metrics.dp(414))
            .height(metrics.dp(92))
            .shadow(
                elevation = metrics.dp(3.45),
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.15f),
                spotColor = Color.Black.copy(alpha = 0.15f),
            )
            .shadow(
                elevation = metrics.dp(2.3),
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.30f),
                spotColor = Color.Black.copy(alpha = 0.30f),
            )
            .clip(shape)
            .background(MettleSurfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = metrics.dp(18.4), top = metrics.dp(22.5)),
        ) {
            Row(
                modifier = Modifier.height(metrics.dp(28)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.width(metrics.dp(22)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = guidance.emoji,
                        color = MettleOnSurface,
                        fontFamily = FigmaNotoEmoji,
                        fontSize = metrics.sp(18.4),
                        lineHeight = metrics.sp(28),
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.width(metrics.dp(7.6)))
                Text(
                    text = guidance.title,
                    color = MettleOnSurface,
                    fontSize = metrics.sp(18.4),
                    lineHeight = metrics.sp(28),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
            Text(
                text = guidance.timing,
                color = MettleOnSurface.copy(alpha = 0.30f),
                fontSize = metrics.sp(13.8),
                lineHeight = metrics.sp(19),
                maxLines = 1,
            )
        }

        if (guidance.combined != null) {
            FigmaNutritionAmount(
                modifier = Modifier.width(metrics.dp(184)),
                amount = guidance.combined,
                unit = guidance.unit,
                label = null,
                background = Color(0xFF153809),
                metrics = metrics,
            )
        } else {
            FigmaNutritionAmount(
                modifier = Modifier.width(metrics.dp(92)),
                amount = guidance.before.orEmpty(),
                unit = guidance.unit,
                label = "Pre-workout",
                background = Color(0xFF153809),
                metrics = metrics,
            )
            FigmaNutritionAmount(
                modifier = Modifier.width(metrics.dp(92)),
                amount = guidance.after.orEmpty(),
                unit = guidance.unit,
                label = "Post-workout",
                background = MettlePrimaryContainer,
                metrics = metrics,
            )
        }
    }
}

@Composable
private fun FigmaNutritionAmount(
    modifier: Modifier,
    amount: String,
    unit: String,
    label: String?,
    background: Color,
    metrics: FigmaMetrics,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.height(metrics.dp(42)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = amount,
                color = MettlePrimary,
                fontSize = metrics.sp(32.2),
                lineHeight = metrics.sp(42),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = unit,
                color = MettlePrimary,
                fontSize = metrics.sp(if (unit == "mL") 14 else 16.1),
                lineHeight = metrics.sp(23),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (label == null) {
            Spacer(Modifier.height(metrics.dp(25)))
        } else {
            Box(
                modifier = Modifier.height(metrics.dp(25)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = MettlePrimary.copy(alpha = 0.45f),
                    fontSize = metrics.sp(11),
                    lineHeight = metrics.sp(16),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun FigmaSessionActions(
    modifier: Modifier,
    selectedDay: String,
    enabled: Boolean,
    beginEnabled: Boolean,
    beginLabel: String,
    onDaySelected: (String) -> Unit,
    onBeginSession: () -> Unit,
    metrics: FigmaMetrics,
) {
    Box(
        modifier = modifier
            .width(metrics.dp(453))
            .height(metrics.dp(72)),
    ) {
        if (beginEnabled) {
            // Figma filter12: ellipse (353, 724.607), 66 x 10.5, blur σ=11.85.
            Box(
                modifier = Modifier
                    .offset(x = metrics.dp(287), y = metrics.dp(26.481))
                    .width(metrics.dp(132))
                    .height(metrics.dp(21))
                    .blur(
                        radius = metrics.dp(11.85),
                        edgeTreatment = BlurredEdgeTreatment.Unbounded,
                    )
                    .background(MettleOnPrimaryContainer, CircleShape),
            )
        }

        val dayX = listOf(31f, 88.2035f, 146.407f, 203f)
        val dayWidth = listOf(49.2035f, 50.2035f, 48.2035f, 48.2035f)
        FigmaProgrammeDays.forEachIndexed { index, day ->
            FigmaDayButton(
                modifier = Modifier
                    .offset(x = metrics.dp(dayX[index]))
                    .width(metrics.dp(dayWidth[index]))
                    .height(metrics.dp(47.2522)),
                option = day,
                selected = selectedDay == day.storageSymbol,
                enabled = enabled,
                onClick = { onDaySelected(day.storageSymbol) },
                metrics = metrics,
            )
        }

        FigmaBeginSessionButton(
            modifier = Modifier
                .offset(x = metrics.dp(282))
                .width(metrics.dp(142.203))
                .height(metrics.dp(47.2522)),
            enabled = beginEnabled,
            label = beginLabel,
            onClick = onBeginSession,
            metrics = metrics,
        )
    }
}

@Composable
private fun FigmaBeginSessionButton(
    modifier: Modifier,
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    metrics: FigmaMetrics,
) {
    FigmaTintedSurface(
        modifier = modifier,
        shape = CircleShape,
        fill = MettleOnPrimaryContainer.copy(alpha = if (enabled) 0.45f else 0.18f),
        shadowElevation = if (enabled) metrics.dp(4) else 0.dp,
        borderWidth = metrics.dp(0.116),
        borderColor = MettleOutlineVariant,
        enabled = enabled,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White.copy(alpha = if (enabled) 1f else 0.45f),
                fontSize = metrics.sp(16.3),
                lineHeight = metrics.sp(24),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FigmaDayButton(
    modifier: Modifier,
    option: FigmaProgrammeDay,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    metrics: FigmaMetrics,
) {
    FigmaTintedSurface(
        modifier = modifier,
        shape = CircleShape,
        fill = MettleOnPrimaryContainer.copy(alpha = if (selected) 0.45f else 0.10f),
        shadowElevation = if (selected) metrics.dp(4) else 0.dp,
        borderWidth = metrics.dp(0.116),
        borderColor = MettleOutlineVariant,
        enabled = enabled,
        onClick = onClick,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = option.displaySymbol,
                color = if (selected) Color.White else MettleOnSurfaceVariant,
                fontSize = metrics.sp(16.3),
                lineHeight = metrics.sp(24),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun MettleBottomToolbar(
    selectedIndex: Int,
    onOpenHome: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val destinations = listOf(
        FigmaToolbarDestination("Daily Update", MettleIcons.Cycle, 23f, 23f, onOpenHome),
        FigmaToolbarDestination("Workout", MettleIcons.SportsMartialArts, 20f, 23f, onOpenWorkout),
        FigmaToolbarDestination("Progress", MettleIcons.AddChart, 20f, 20f, onOpenHistory),
        FigmaToolbarDestination("Exercise library", MettleIcons.CardsStack, 23f, 20f, onOpenLibrary),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        val viewportWidth = minOf(maxWidth, FigmaReferenceWidth.dp)
        val metrics = FigmaMetrics(
            scale = (viewportWidth.value / FigmaReferenceWidth).coerceAtMost(1f),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = metrics.dp(6)),
            contentAlignment = Alignment.Center,
        ) {
            FigmaTintedSurface(
                modifier = Modifier
                    .width(metrics.dp(220))
                    .height(metrics.dp(64)),
                shape = CircleShape,
                fill = MettleOnPrimaryContainer.copy(alpha = 0.30f),
                shadowElevation = metrics.dp(4),
                borderWidth = metrics.dp(0.116),
                borderColor = MettleOutlineVariant,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = metrics.dp(8)),
                    horizontalArrangement = Arrangement.spacedBy(metrics.dp(4)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        Box(
                            modifier = Modifier
                                .width(metrics.dp(48))
                                .fillMaxHeight()
                                .clickable(role = Role.Button, onClick = destination.onClick),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = destination.contentDescription,
                                tint = Color.White.copy(alpha = if (isSelected) 1f else 0.8f),
                                modifier = Modifier.size(
                                    DpSize(
                                        metrics.dp(destination.width),
                                        metrics.dp(destination.height),
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class FigmaToolbarDestination(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

@Composable
private fun FigmaTintedSurface(
    modifier: Modifier,
    shape: Shape,
    fill: Color,
    shadowElevation: Dp,
    borderWidth: Dp = 0.dp,
    borderColor: Color = Color.Transparent,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val surface = modifier
        .shadow(
            elevation = shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.30f),
            spotColor = Color.Black.copy(alpha = 0.30f),
        )
        .clip(shape)
        .background(fill)
        .then(
            if (borderWidth > 0.dp) {
                Modifier.border(borderWidth, borderColor, shape)
            } else {
                Modifier
            },
        )
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                )
            } else {
                Modifier
            },
        )

    Box(modifier = surface) {
        content()
    }
}
