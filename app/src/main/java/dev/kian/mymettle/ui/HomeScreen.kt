package dev.kian.mymettle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel
import dev.kian.mymettle.ui.theme.MettleBackground
import dev.kian.mymettle.ui.theme.MettleOnPrimaryContainer
import dev.kian.mymettle.ui.theme.MettleOnSurface
import dev.kian.mymettle.ui.theme.MettleOnSurfaceVariant
import dev.kian.mymettle.ui.theme.MettlePrimary
import dev.kian.mymettle.workout.DailyBriefSessionProfile
import dev.kian.mymettle.workout.dailyBriefGuidance

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
                            0f to Color(0xFF607054),
                            0.25f to Color(0xFF4C5A43),
                            0.5f to Color(0xFF37412F),
                            0.75f to Color(0xFF242B20),
                            1f to MettleBackground,
                        ),
                        center = Offset(size.width / 2f, size.height * 1.06f),
                        radius = size.height * 0.98f,
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
    onChooseIntensity: () -> Unit,
    onOpenWorkout: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    val state = viewModel.uiState
    val context = LocalContext.current
    val backupFactory = remember(context) { NativeBackupViewModelFactory(context) }
    val backupViewModel: NativeBackupViewModel = composeViewModel(factory = backupFactory)
    val backupState = backupViewModel.uiState
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backupViewModel.restoreBackup(uri)
    }

    LaunchedEffect(backupState.completedGeneration) {
        if (backupState.completedGeneration > 0) viewModel.refresh()
    }

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
            ProgrammeBootstrapHome(
                restoring = backupState.restoring,
                onRestoreBackup = {
                    if (!backupState.restoring) {
                        // Browser/download providers can register valid .json backups as
                        // application/octet-stream. The repository already performs strict
                        // kind/format/schema validation after selection, so do not let Android's
                        // MIME metadata prevent a valid portable backup from reaching restoreJson().
                        restoreLauncher.launch(arrayOf("*/*"))
                    }
                },
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )
        }

        else -> {
            val dayDisplaySymbol = when (state.selectedDay) {
                "ψ" -> "ψ"
                "φ" -> "ϕ"
                "π" -> "π"
                "&" -> "&"
                else -> "π"
            }
            val daySpokenName = when (state.selectedDay) {
                "ψ" -> "Psi"
                "φ" -> "Phi"
                "π" -> "Pi"
                "&" -> "&"
                else -> "Pi"
            }
            val plan = state.plans[state.selectedMode]
            val exerciseCount = state.workout
                ?.exercises
                ?.count { it.entity.prescriptionIncluded }
                ?: plan?.exercises?.size
                ?: 0
            val canBegin = state.workout != null ||
                (!state.loading && plan?.exercises?.isNotEmpty() == true)
            val dailyBrief = state.workout?.dailyBriefGuidance()
                ?: plan?.dailyBriefGuidance(state.bodyweightKg)
                ?: dailyBriefGuidance(
                    DailyBriefSessionProfile(
                        workingSets = 0,
                        estimatedDurationSeconds = 0,
                        bodyweightKg = state.bodyweightKg,
                        targetSegments = emptyList(),
                    ),
                )

            FigmaDailyUpdateScreen(
                selectedDay = state.selectedDay,
                dayDisplaySymbol = dayDisplaySymbol,
                daySpokenName = daySpokenName,
                exerciseCount = exerciseCount,
                guidance = dailyBrief,
                daySelectionEnabled = state.workout == null && !state.loading,
                beginEnabled = canBegin,
                beginLabel = if (state.workout == null) "Begin Session" else "Resume Session",
                onDaySelected = viewModel::selectDay,
                onBeginSession = {
                    if (state.workout == null) onChooseIntensity() else onOpenWorkout()
                },
                onOpenSettings = onOpenSettings,
                onOpenAccount = onOpenAccount,
            )
        }
    }

    backupState.error?.let { error ->
        AlertDialog(
            onDismissRequest = backupViewModel::dismissError,
            title = { Text("Couldn’t restore backup") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = backupViewModel::dismissError) {
                    Text("OK")
                }
            },
        )
    }
}

@Composable
private fun ProgrammeBootstrapHome(
    restoring: Boolean,
    onRestoreBackup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
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

            Row(verticalAlignment = Alignment.CenterVertically) {
                BootstrapIconButton(
                    imageVector = MettleIcons.Settings,
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                )
                BootstrapIconButton(
                    imageVector = MettleIcons.AccountCircle,
                    contentDescription = "Account and history",
                    onClick = onOpenAccount,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            MettleControlGlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                tint = MettleOnPrimaryContainer.copy(alpha = 0.055f),
                shadowElevation = 4.dp,
                onClick = { if (!restoring) onRestoreBackup() },
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "Restore My Mettle",
                        color = MettleOnSurface,
                        fontSize = 24.sp,
                        lineHeight = 32.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (restoring) {
                            "Restoring your Native backup…"
                        } else {
                            "Restore a current My Mettle Native backup before the first Daily Update can be prepared."
                        },
                        color = MettleOnSurfaceVariant,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun BootstrapIconButton(
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
