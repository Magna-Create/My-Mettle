package dev.kian.mymettle.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.R
import dev.kian.mymettle.ui.theme.MettleBackground
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer
import dev.kian.mymettle.ui.theme.MettleOnSurface
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant
import dev.kian.mymettle.ui.theme.MettleOutlineVariant
import dev.kian.mymettle.ui.theme.MettlePrimary
import dev.kian.mymettle.ui.theme.MettlePrimaryContainer
import dev.kian.mymettle.ui.theme.MettleSurfaceContainerLow

private const val DailyUpdateReferenceWidth = 453f

private val NotoEmoji = FontFamily(
    Font(R.font.noto_emoji_variable, FontWeight.Medium),
)

private val DayOptions = listOf(
    ProgrammeDay(storageSymbol = "ψ", displaySymbol = "ψ", spokenName = "Psi"),
    ProgrammeDay(storageSymbol = "φ", displaySymbol = "ϕ", spokenName = "Phi"),
    ProgrammeDay(storageSymbol = "π", displaySymbol = "π", spokenName = "Pi"),
    ProgrammeDay(storageSymbol = "&", displaySymbol = "&", spokenName = "&"),
)

private data class ProgrammeDay(
    val storageSymbol: String,
    val displaySymbol: String,
    val spokenName: String,
)

private data class NutritionGuidance(
    val emoji: String,
    val title: String,
    val timing: String,
    val before: String? = null,
    val after: String? = null,
    val combined: String? = null,
    val unit: String,
)

private val NutritionItems = listOf(
    NutritionGuidance(
        emoji = "🧀",
        title = "Protein",
        timing = "1-2 Hours before training.",
        before = "65",
        after = "65",
        unit = "G",
    ),
    NutritionGuidance(
        emoji = "🥪",
        title = "Carbohydrates",
        timing = "2-4 Hours before training.",
        before = "65",
        after = "65",
        unit = "G",
    ),
    NutritionGuidance(
        emoji = "💧",
        title = "Water",
        timing = "Throughout Workout",
        combined = "400-500",
        unit = "mL",
    ),
)

private data class DesignMetrics(val scale: Float) {
    fun dp(value: Number): Dp = (value.toFloat() * scale).dp
    fun sp(value: Number): TextUnit = (value.toFloat() * scale).sp
}

@Composable
fun MettleGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to Color(0xFF687A5B),
                            0.25f to Color(0xFF526148),
                            0.5f to Color(0xFF3C4735),
                            0.75f to Color(0xFF272E22),
                            1f to MettleBackground,
                        ),
                        center = Offset(size.width / 2f, size.height),
                        radius = size.height,
                    ),
                )
            },
    ) {
        content()
    }
}

@Composable
fun HomeScreen(
    viewModel: N2WorkoutViewModel,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state = viewModel.uiState

    when {
        state.loading && state.workout == null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MettlePrimary)
            }
        }

        !state.hasProgramme -> {
            EmptyDailyUpdate(
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
                onBringOverLite = onOpenWorkout,
            )
        }

        else -> {
            DailyUpdate(
                state = state,
                onDaySelected = viewModel::selectDay,
                onBeginSession = {
                    if (state.workout == null) viewModel.startSession()
                    onOpenWorkout()
                },
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )
        }
    }
}

@Composable
private fun DailyUpdateViewport(
    content: @Composable (DesignMetrics) -> Unit,
) {
    val widthClass = LocalMettleWindowWidthClass.current
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // Size classes choose the layout family. The compact family scales the 453-unit
        // artboard continuously; wider families retain and centre that benchmark for now.
        val viewportWidth = when (widthClass) {
            MettleWindowWidthClass.Compact -> minOf(maxWidth, DailyUpdateReferenceWidth.dp)
            MettleWindowWidthClass.Medium,
            MettleWindowWidthClass.Expanded,
            -> minOf(maxWidth, DailyUpdateReferenceWidth.dp)
        }
        val metrics = DesignMetrics(
            scale = (viewportWidth.value / DailyUpdateReferenceWidth).coerceAtMost(1f),
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
private fun DailyUpdate(
    state: N2WorkoutUiState,
    onDaySelected: (String) -> Unit,
    onBeginSession: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val day = DayOptions.firstOrNull { it.storageSymbol == state.selectedDay } ?: DayOptions[2]
    val plan = state.plans[state.selectedMode]
    val exerciseCount = state.workout
        ?.exercises
        ?.count { it.entity.prescriptionIncluded }
        ?: plan?.exercises?.size
        ?: 0
    val canBegin = state.workout != null || (!state.loading && plan?.exercises?.isNotEmpty() == true)

    DailyUpdateViewport { metrics ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            DailyUpdateAppBar(
                metrics = metrics,
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )

            Spacer(Modifier.height(metrics.dp(10)))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = metrics.dp(21), end = metrics.dp(18)),
            ) {
                HeroGreeting(day = day, metrics = metrics)
                Spacer(Modifier.height(metrics.dp(16)))

                InsightChips(exerciseCount = exerciseCount, metrics = metrics)
                Spacer(Modifier.height(metrics.dp(16)))

                NutritionHeading(metrics)
                Spacer(Modifier.height(metrics.dp(16)))

                NutritionItems.forEachIndexed { index, guidance ->
                    NutritionCard(guidance, metrics)
                    if (index < NutritionItems.lastIndex) {
                        Spacer(Modifier.height(metrics.dp(16)))
                    }
                }

                Spacer(Modifier.height(metrics.dp(33)))

                SessionActions(
                    selectedDay = state.selectedDay,
                    enabled = state.workout == null && !state.loading,
                    beginEnabled = canBegin,
                    beginLabel = if (state.workout == null) "Begin Session" else "Resume Session",
                    onDaySelected = onDaySelected,
                    onBeginSession = onBeginSession,
                    metrics = metrics,
                )

                Spacer(Modifier.height(metrics.dp(28)))
            }
        }
    }
}

@Composable
private fun DailyUpdateAppBar(
    metrics: DesignMetrics,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(70))
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

        MettleGlassSurface(
            modifier = Modifier
                .width(metrics.dp(81))
                .height(metrics.dp(49.388)),
            shape = CircleShape,
            tint = MettleOnPrimaryContainer.copy(alpha = 0.30f),
            blurRadius = metrics.dp(3.087),
            refractionDisplacement = metrics.dp(2.4),
            refractionStrength = 0.20f,
            shadowElevation = metrics.dp(3.087),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(metrics.dp(6)),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderIconButton(
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    metrics = metrics,
                )
                HeaderIconButton(
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
private fun HeaderIconButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    metrics: DesignMetrics,
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
private fun HeroGreeting(day: ProgrammeDay, metrics: DesignMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(114)),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(top = metrics.dp(14))) {
            Text(
                text = "Hey Kian, ",
                color = MettleOnSurface,
                fontSize = metrics.sp(45),
                lineHeight = metrics.sp(52),
            )
            Spacer(Modifier.height(metrics.dp(2)))
            Text(
                text = "Ready for ${day.spokenName}?",
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
                text = day.displaySymbol,
                color = MettleOnPrimaryContainer,
                fontSize = metrics.sp(27.6),
                lineHeight = metrics.sp(46),
            )
        }
    }
}

@Composable
private fun InsightChips(exerciseCount: Int, metrics: DesignMetrics) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(metrics.dp(8)),
    ) {
        listOf(
            "$exerciseCount Exercises",
            "5-min warm-up",
            "2/3 Core Days",
            "No.1 Improvement from π last week was: [EXERCISE NAME]",
        ).forEach { label ->
            MettleGlassSurface(
                modifier = Modifier.height(metrics.dp(32)),
                shape = RoundedCornerShape(metrics.dp(8)),
                tint = Color.Black.copy(alpha = 0.004f),
                blurRadius = metrics.dp(24),
                refractionDisplacement = metrics.dp(2.5),
                refractionStrength = 0.10f,
                shadowElevation = metrics.dp(2),
                borderWidth = metrics.dp(1),
                borderColor = MettleOutlineVariant,
            ) {
                Box(
                    modifier = Modifier
                        .height(metrics.dp(32))
                        .padding(horizontal = metrics.dp(12)),
                    contentAlignment = Alignment.Center,
                ) {
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

@Composable
private fun NutritionHeading(metrics: DesignMetrics) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(33)),
    ) {
        HorizontalDivider(color = MettleOutlineVariant)
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
private fun NutritionCard(guidance: NutritionGuidance, metrics: DesignMetrics) {
    val shape = RoundedCornerShape(metrics.dp(14))
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = NotoEmoji, fontWeight = FontWeight.Medium)) {
                        append(guidance.emoji)
                    }
                    append(" ${guidance.title}")
                },
                color = MettleOnSurface,
                fontSize = metrics.sp(18.4),
                lineHeight = metrics.sp(28),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = guidance.timing,
                color = MettleOnSurface.copy(alpha = 0.30f),
                fontSize = metrics.sp(13.8),
                lineHeight = metrics.sp(19),
                maxLines = 1,
            )
        }

        if (guidance.combined != null) {
            NutritionAmount(
                modifier = Modifier.width(metrics.dp(184)),
                amount = guidance.combined,
                unit = guidance.unit,
                label = null,
                background = Color(0xFF153809),
                metrics = metrics,
            )
        } else {
            NutritionAmount(
                modifier = Modifier.width(metrics.dp(92)),
                amount = guidance.before.orEmpty(),
                unit = guidance.unit,
                label = "Pre-workout",
                background = Color(0xFF153809),
                metrics = metrics,
            )
            NutritionAmount(
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
private fun NutritionAmount(
    modifier: Modifier,
    amount: String,
    unit: String,
    label: String?,
    background: Color,
    metrics: DesignMetrics,
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
private fun SessionActions(
    selectedDay: String,
    enabled: Boolean,
    beginEnabled: Boolean,
    beginLabel: String,
    onDaySelected: (String) -> Unit,
    onBeginSession: () -> Unit,
    metrics: DesignMetrics,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.dp(56))
            .padding(start = metrics.dp(10), end = metrics.dp(11)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.dp(9))) {
            DayOptions.forEach { day ->
                DayButton(
                    option = day,
                    selected = selectedDay == day.storageSymbol,
                    enabled = enabled,
                    onClick = { onDaySelected(day.storageSymbol) },
                    metrics = metrics,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        BeginSessionButton(
            enabled = beginEnabled,
            label = beginLabel,
            onClick = onBeginSession,
            metrics = metrics,
        )
    }
}

@Composable
private fun BeginSessionButton(
    enabled: Boolean,
    label: String,
    onClick: () -> Unit,
    metrics: DesignMetrics,
) {
    Box(
        modifier = Modifier
            .width(metrics.dp(142.2))
            .height(metrics.dp(56)),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (enabled) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(metrics.dp(132))
                    .height(metrics.dp(24))
                    .drawBehind {
                        drawOval(
                            brush = Brush.radialGradient(
                                colorStops = arrayOf(
                                    0f to MettleOnPrimaryContainer.copy(alpha = 0.55f),
                                    0.45f to MettleOnPrimaryContainer.copy(alpha = 0.24f),
                                    1f to Color.Transparent,
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = size.width / 2f,
                            ),
                        )
                    },
            )
        }

        MettleGlassSurface(
            modifier = Modifier
                .width(metrics.dp(142.2))
                .height(metrics.dp(47.25)),
            shape = CircleShape,
            tint = MettleOnPrimaryContainer.copy(alpha = if (enabled) 0.45f else 0.18f),
            blurRadius = metrics.dp(4),
            refractionDisplacement = metrics.dp(3),
            refractionStrength = 0.24f,
            shadowElevation = if (enabled) metrics.dp(4) else 0.dp,
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
}

@Composable
private fun DayButton(
    option: ProgrammeDay,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    metrics: DesignMetrics,
) {
    MettleGlassSurface(
        modifier = Modifier.size(metrics.dp(48)),
        shape = CircleShape,
        tint = MettleOnPrimaryContainer.copy(alpha = if (selected) 0.45f else 0.10f),
        blurRadius = metrics.dp(4),
        refractionDisplacement = metrics.dp(2.6),
        refractionStrength = if (selected) 0.22f else 0.14f,
        shadowElevation = if (selected) metrics.dp(4) else 0.dp,
        enabled = enabled,
        selected = selected,
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
        ToolbarDestination("Daily Update", MettleIcons.Cycle, 23f, 23f, onOpenHome),
        ToolbarDestination("Workout", MettleIcons.SportsMartialArts, 20f, 23f, onOpenWorkout),
        ToolbarDestination("Progress", MettleIcons.AddChart, 20f, 20f, onOpenHistory),
        ToolbarDestination("Exercise library", MettleIcons.CardsStack, 23f, 20f, onOpenLibrary),
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        val viewportWidth = minOf(maxWidth, DailyUpdateReferenceWidth.dp)
        val metrics = DesignMetrics(
            scale = (viewportWidth.value / DailyUpdateReferenceWidth).coerceAtMost(1f),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = metrics.dp(6)),
            contentAlignment = Alignment.Center,
        ) {
            MettleGlassSurface(
                modifier = Modifier
                    .width(metrics.dp(220))
                    .height(metrics.dp(64)),
                shape = CircleShape,
                tint = MettleOnPrimaryContainer.copy(alpha = 0.30f),
                blurRadius = metrics.dp(4),
                refractionDisplacement = metrics.dp(3.2),
                refractionStrength = 0.22f,
                shadowElevation = metrics.dp(4),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isSelected = selectedIndex == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
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

private data class ToolbarDestination(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val width: Float,
    val height: Float,
    val onClick: () -> Unit,
)

@Composable
private fun EmptyDailyUpdate(
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onBringOverLite: () -> Unit,
) {
    DailyUpdateViewport { metrics ->
        Column(modifier = Modifier.fillMaxSize()) {
            DailyUpdateAppBar(metrics, onOpenSettings, onOpenAccount)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(metrics.dp(24)),
                contentAlignment = Alignment.Center,
            ) {
                MettleGlassSurface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(metrics.dp(28)),
                    tint = MettleOnPrimaryContainer.copy(alpha = 0.18f),
                    blurRadius = metrics.dp(16),
                    refractionDisplacement = metrics.dp(5),
                    refractionStrength = 0.18f,
                    shadowElevation = metrics.dp(4),
                    onClick = onBringOverLite,
                ) {
                    Column(modifier = Modifier.padding(metrics.dp(24))) {
                        Text(
                            text = "Bring over My Mettle Lite",
                            color = MettleOnSurface,
                            fontSize = metrics.sp(24),
                            lineHeight = metrics.sp(32),
                        )
                        Spacer(Modifier.height(metrics.dp(8)))
                        Text(
                            text = "Import your existing programme before the first Daily Update can be prepared.",
                            color = MettleOnSurfaceVariant,
                            fontSize = metrics.sp(14),
                            lineHeight = metrics.sp(20),
                        )
                    }
                }
            }
        }
    }
}
