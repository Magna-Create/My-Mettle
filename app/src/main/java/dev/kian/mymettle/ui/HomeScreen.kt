package dev.kian.mymettle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
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

@Composable
fun MettleGradientBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        DailyUpdateAppBar(
            onOpenSettings = onOpenSettings,
            onOpenAccount = onOpenAccount,
        )

        Spacer(Modifier.height(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 21.dp, end = 18.dp),
        ) {
            HeroGreeting(day = day)
            Spacer(Modifier.height(16.dp))

            InsightChips(exerciseCount = exerciseCount)
            Spacer(Modifier.height(16.dp))

            NutritionHeading()
            Spacer(Modifier.height(16.dp))

            NutritionItems.forEachIndexed { index, guidance ->
                NutritionCard(guidance)
                if (index < NutritionItems.lastIndex) Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(33.dp))

            SessionActions(
                selectedDay = state.selectedDay,
                enabled = state.workout == null && !state.loading,
                beginEnabled = canBegin,
                beginLabel = if (state.workout == null) "Begin Session" else "Resume Session",
                onDaySelected = onDaySelected,
                onBeginSession = onBeginSession,
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DailyUpdateAppBar(
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .padding(start = 21.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "My Mettle",
                color = MettleOnPrimaryContainer,
                fontSize = 24.2.sp,
                lineHeight = 31.sp,
            )
            Text(
                text = "Daily Update",
                color = MettleOnSurfaceVariant,
                fontSize = 13.2.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        GlassContainer(
            modifier = Modifier
                .width(81.dp)
                .height(49.dp),
            shape = CircleShape,
            fillAlpha = 0.30f,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderIconButton(
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                )
                HeaderIconButton(
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account and history",
                    onClick = onOpenAccount,
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
) {
    Box(
        modifier = Modifier
            .size(34.5.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(16.4.dp),
        )
    }
}

@Composable
private fun HeroGreeting(day: ProgrammeDay) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(114.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.padding(top = 14.dp)) {
            Text(
                text = "Hey Kian, ",
                style = MaterialTheme.typography.displayMedium,
                color = MettleOnSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Ready for ${day.spokenName}?",
                style = MaterialTheme.typography.headlineSmall,
                color = MettleOnSurface,
            )
        }

        Box(
            modifier = Modifier
                .padding(top = 17.dp, end = 1.dp)
                .size(80.dp)
                .clip(CircleShape)
                .background(MettlePrimaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.displaySymbol,
                color = MettleOnPrimaryContainer,
                fontSize = 27.6.sp,
                lineHeight = 46.sp,
            )
        }
    }
}

@Composable
private fun InsightChips(exerciseCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "$exerciseCount Exercises",
            "5-min warm-up",
            "2/3 Core Days",
            "No.1 Improvement from π last week was: [EXERCISE NAME]",
        ).forEach { label ->
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .border(1.dp, MettleOutlineVariant, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = MettleOnSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun NutritionHeading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(33.dp),
    ) {
        HorizontalDivider(color = MettleOutlineVariant)
        Spacer(Modifier.height(7.dp))
        Text(
            text = "Pre and Post Workout Dietary Recommendations:",
            color = MettleOnSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

@Composable
private fun NutritionCard(guidance: NutritionGuidance) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .shadow(
                elevation = 3.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.3f),
                spotColor = Color.Black.copy(alpha = 0.3f),
            )
            .clip(shape)
            .background(MettleSurfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .padding(start = 18.4.dp, top = 22.5.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = NotoEmoji, fontWeight = FontWeight.Medium)) {
                        append(guidance.emoji)
                    }
                    append(" ${guidance.title}")
                },
                color = MettleOnSurface,
                fontSize = 18.4.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = guidance.timing,
                color = MettleOnSurface.copy(alpha = 0.30f),
                fontSize = 13.8.sp,
                lineHeight = 19.sp,
                maxLines = 1,
            )
        }

        if (guidance.combined != null) {
            NutritionAmount(
                modifier = Modifier.width(184.dp),
                amount = guidance.combined,
                unit = guidance.unit,
                label = null,
                background = Color(0xFF153809),
            )
        } else {
            NutritionAmount(
                modifier = Modifier.width(92.dp),
                amount = guidance.before.orEmpty(),
                unit = guidance.unit,
                label = "Pre-workout",
                background = Color(0xFF153809),
            )
            NutritionAmount(
                modifier = Modifier.width(92.dp),
                amount = guidance.after.orEmpty(),
                unit = guidance.unit,
                label = "Post-workout",
                background = MettlePrimaryContainer,
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Row(
            modifier = Modifier.height(42.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = amount,
                color = MettlePrimary,
                fontSize = 32.2.sp,
                lineHeight = 42.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            Text(
                text = unit,
                color = MettlePrimary,
                fontSize = if (unit == "mL") 14.sp else 16.1.sp,
                lineHeight = 23.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        if (label == null) {
            Spacer(Modifier.height(25.dp))
        } else {
            Box(
                modifier = Modifier.height(25.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = MettlePrimary.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall,
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
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(start = 10.dp, end = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DayOptions.forEach { day ->
                DayButton(
                    option = day,
                    selected = selectedDay == day.storageSymbol,
                    enabled = enabled,
                    onClick = { onDaySelected(day.storageSymbol) },
                )
            }
        }

        Spacer(Modifier.weight(1f))

        GlassContainer(
            modifier = Modifier
                .width(142.dp)
                .height(48.dp)
                .then(
                    if (beginEnabled) {
                        Modifier.clickable(role = Role.Button, onClick = onBeginSession)
                    } else {
                        Modifier
                    },
                ),
            shape = CircleShape,
            fillAlpha = if (beginEnabled) 0.45f else 0.18f,
            shadowElevation = if (beginEnabled) 8.dp else 0.dp,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = beginLabel,
                    color = Color.White.copy(alpha = if (beginEnabled) 1f else 0.45f),
                    fontSize = 16.3.sp,
                    lineHeight = 24.sp,
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
) {
    GlassContainer(
        modifier = Modifier
            .size(48.dp)
            .semantics { this.selected = selected }
            .then(
                if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
            ),
        shape = CircleShape,
        fillAlpha = if (selected) 0.45f else 0.10f,
        shadowElevation = if (selected) 4.dp else 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = option.displaySymbol,
                color = if (selected) Color.White else MettleOnSurfaceVariant,
                fontSize = 16.3.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun GlassContainer(
    modifier: Modifier,
    shape: Shape,
    fillAlpha: Float,
    shadowElevation: androidx.compose.ui.unit.Dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = shadowElevation,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.16f),
            )
            .clip(shape)
            .background(MettleOnPrimaryContainer.copy(alpha = fillAlpha))
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape),
    ) {
        content()
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
        ToolbarDestination("Daily Update", MettleIcons.Cycle, DpSize(23.dp, 23.dp), onOpenHome),
        ToolbarDestination("Workout", MettleIcons.SportsMartialArts, DpSize(20.dp, 23.dp), onOpenWorkout),
        ToolbarDestination("Progress", MettleIcons.AddChart, DpSize(20.dp, 20.dp), onOpenHistory),
        ToolbarDestination("Exercise library", MettleIcons.CardsStack, DpSize(23.dp, 20.dp), onOpenLibrary),
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        GlassContainer(
            modifier = Modifier
                .width(220.dp)
                .height(64.dp),
            shape = CircleShape,
            fillAlpha = 0.30f,
            shadowElevation = 4.dp,
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
                            .size(48.dp)
                            .semantics { selected = isSelected }
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = destination.onClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = destination.contentDescription,
                            tint = Color.White.copy(alpha = if (isSelected) 1f else 0.8f),
                            modifier = Modifier
                                .width(destination.size.width)
                                .height(destination.size.height),
                        )
                    }
                }
            }
        }
    }
}

private data class ToolbarDestination(
    val contentDescription: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val size: DpSize,
    val onClick: () -> Unit,
)

@Composable
private fun EmptyDailyUpdate(
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
    onBringOverLite: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        DailyUpdateAppBar(onOpenSettings, onOpenAccount)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            GlassContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onBringOverLite),
                shape = RoundedCornerShape(28.dp),
                fillAlpha = 0.18f,
                shadowElevation = 4.dp,
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Bring over My Mettle Lite", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Import your existing programme before the first Daily Update can be prepared.",
                        color = MettleOnSurfaceVariant,
                    )
                }
            }
        }
    }
}
