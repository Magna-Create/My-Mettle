package dev.kian.mymettle.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.kian.mymettle.developer.BiologyTaskController
import dev.kian.mymettle.developer.BiologyTaskPhase
import dev.kian.mymettle.ui.theme.MettleBackground
import dev.kian.mymettle.timer.RestTimerController
import dev.kian.mymettle.timer.RestTimerPhase
import kotlinx.coroutines.delay

private const val HOME_ROUTE = "home"
private const val INTENSITY_ROUTE = "intensity"
private const val TRAIN_ROUTE = "train"
private const val LIBRARY_ROUTE = "library"
private const val HISTORY_ROUTE = "history"
private const val ACCOUNT_ROUTE = "account"
private const val SETTINGS_ROUTE = "settings"
private const val BIOLOGY_DEVELOPER_ROUTE = "settings/biology-developer"

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun MyMettleApp() {
    val context = LocalContext.current
    val workoutViewModel: N2WorkoutViewModel = viewModel(
        factory = remember(context) { N2WorkoutViewModelFactory(context) },
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: HOME_ROUTE
    val biologyTask by BiologyTaskController.state.collectAsState()
    val restTimer = remember(context) { RestTimerController.get(context) }
    val restTimerSnapshot by restTimer.state.collectAsState()
    var restTimerProgress by remember { mutableFloatStateOf(restTimerSnapshot.elapsedFraction()) }
    LaunchedEffect(
        restTimerSnapshot.phase,
        restTimerSnapshot.endElapsedRealtime,
        restTimerSnapshot.pausedRemainingMillis,
        restTimerSnapshot.totalDurationMillis,
    ) {
        restTimerProgress = restTimerSnapshot.elapsedFraction()
        while (restTimerSnapshot.phase == RestTimerPhase.RUNNING) {
            delay(250)
            restTimerProgress = restTimerSnapshot.elapsedFraction()
        }
    }

    // General glass (headers, selector lens, page controls, etc.) samples destination-appropriate
    // backdrop sources. Daily Update gets its live green field, Intensity registers its animated
    // Canvas, while the information-dense native screens sample the same dark base they render on.
    val hazeState = rememberHazeState()
    // The global hotbar needs the *composited destination* as its source so opaque screens, cards
    // and scrolling content are sampled exactly as rendered rather than exposing the app gradient
    // that happens to sit underneath them.
    val bottomBarHazeState = rememberHazeState()

    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val windowWidthClass = when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> {
            MettleWindowWidthClass.Expanded
        }
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> {
            MettleWindowWidthClass.Medium
        }
        else -> MettleWindowWidthClass.Compact
    }

    fun openHomeDestination() {
        // Intensity is a transient step, not a restorable main destination. Popping directly to
        // Home removes it from the stack so returning after a completed workout always lands on
        // Daily Update rather than resurrecting the selector.
        val popped = navController.popBackStack(HOME_ROUTE, inclusive = false)
        if (!popped) {
            navController.navigate(HOME_ROUTE) {
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    fun openMainDestination(route: String) {
        if (route == HOME_ROUTE) {
            openHomeDestination()
            return
        }
        navController.navigate(route) {
            popUpTo(HOME_ROUTE) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openWorkoutFromIntensity() {
        // Do not save the selector state when moving into a workout. Saving this leaf route was
        // the reason Home could later restore the Intensity screen after session completion.
        navController.navigate(TRAIN_ROUTE) {
            popUpTo(HOME_ROUTE) { saveState = false }
            launchSingleTop = true
            restoreState = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // One app-owned HazeState, with the visible destination deciding what base artwork belongs
        // in it. This avoids repeating the old hotbar bug where controls on an opaque dark screen
        // accidentally refracted the green Daily Update gradient hidden underneath that screen.
        when (currentRoute) {
            INTENSITY_ROUTE -> {
                IntensityHazeBase(
                    modifier = Modifier.hazeSource(hazeState),
                )
            }

            HOME_ROUTE, ACCOUNT_ROUTE -> {
                MettleGradientBackground(
                    modifier = Modifier.hazeSource(hazeState),
                    content = {},
                )
            }

            TRAIN_ROUTE -> {
                // The workout owns a richer radial Haze source so its header and exercise controls
                // refract the actual session artwork rather than a flat app-level colour.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MettleBackground),
                )
            }

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .background(MettleBackground),
                )
            }
        }

        CompositionLocalProvider(
            LocalMettleHazeState provides hazeState,
            LocalMettleWindowWidthClass provides windowWidthClass,
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    val finishOverlayVisible = currentRoute == TRAIN_ROUTE &&
                        workoutViewModel.uiState.workoutSurface in setOf(WorkoutSurface.FINISH, WorkoutSurface.DELETE_CONFIRM)
                    // Override only the hotbar's Haze input. Its source is the complete rendered
                    // destination below, while glass inside that destination continues using the
                    // normal destination-owned hazeState and therefore cannot self-sample.
                    if (!finishOverlayVisible) CompositionLocalProvider(LocalMettleHazeState provides bottomBarHazeState) {
                        MettleBottomToolbarV2(
                            selectedIndex = when (currentRoute) {
                                HOME_ROUTE -> 0
                                HISTORY_ROUTE -> 1
                                LIBRARY_ROUTE -> 2
                                TRAIN_ROUTE -> 3
                                else -> -1
                            },
                            onOpenHome = ::openHomeDestination,
                            onOpenWorkout = {
                                if (currentRoute == TRAIN_ROUTE && workoutViewModel.uiState.workout != null) {
                                    Unit
                                } else {
                                    openMainDestination(
                                        if (workoutViewModel.uiState.workout == null) INTENSITY_ROUTE else TRAIN_ROUTE,
                                    )
                                }
                            },
                            onOpenHistory = { openMainDestination(HISTORY_ROUTE) },
                            onOpenLibrary = { openMainDestination(LIBRARY_ROUTE) },
                            onQuickSelect = {
                                when {
                                    restTimerSnapshot.visible -> RestTimerOverlayUi.expand()
                                    currentRoute == TRAIN_ROUTE && workoutViewModel.uiState.workout != null -> {
                                        workoutViewModel.showQuickSelect()
                                    }
                                    else -> openMainDestination(
                                        if (workoutViewModel.uiState.workout == null) INTENSITY_ROUTE else TRAIN_ROUTE,
                                    )
                                }
                            },
                            onLongPressWorkout = {
                                if (currentRoute == TRAIN_ROUTE && workoutViewModel.uiState.workout != null) {
                                    workoutViewModel.showFinishSheet()
                                } else {
                                    openMainDestination(
                                        if (workoutViewModel.uiState.workout == null) INTENSITY_ROUTE else TRAIN_ROUTE,
                                    )
                                }
                            },
                            leadingIcon = if (restTimerSnapshot.visible) MettleIcons.Timer else MettleIcons.QuickSelect,
                            leadingDescription = if (restTimerSnapshot.visible) "Rest timer" else "Quick select",
                            leadingProgress = restTimerProgress.takeIf { restTimerSnapshot.visible },
                            showWorkoutControls = workoutViewModel.uiState.workout != null,
                            transparentMaterial = currentRoute == INTENSITY_ROUTE,
                        )
                    }
                },
            ) { _ ->
                // The hotbar is a floating glass surface on every destination. Let each screen
                // continue underneath it so there is never a Scaffold-reserved colour strip behind
                // the bar. This exact composited content is also the dedicated hotbar Haze source.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(bottomBarHazeState),
                ) {
                    NavHost(navController = navController, startDestination = HOME_ROUTE) {
                        composable(HOME_ROUTE) {
                            HomeScreen(
                                viewModel = workoutViewModel,
                                onChooseIntensity = {
                                    navController.navigate(INTENSITY_ROUTE) {
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                },
                                onOpenWorkout = { openMainDestination(TRAIN_ROUTE) },
                                onOpenSettings = { openMainDestination(SETTINGS_ROUTE) },
                                onOpenAccount = { openMainDestination(ACCOUNT_ROUTE) },
                            )
                        }
                        composable(INTENSITY_ROUTE) {
                            IntensitySelectorScreenV3(
                                viewModel = workoutViewModel,
                                onOpenWorkout = ::openWorkoutFromIntensity,
                                onOpenSettings = { openMainDestination(SETTINGS_ROUTE) },
                                onOpenAccount = { openMainDestination(ACCOUNT_ROUTE) },
                            )
                        }
                        composable(TRAIN_ROUTE) {
                            if (workoutViewModel.uiState.workout == null) {
                                LaunchedEffect(Unit) {
                                    openHomeDestination()
                                }
                            } else {
                                TrainScreen(
                                    viewModel = workoutViewModel,
                                    onOpenSettings = { openMainDestination(SETTINGS_ROUTE) },
                                    onOpenAccount = { openMainDestination(ACCOUNT_ROUTE) },
                                )
                            }
                        }
                        composable(LIBRARY_ROUTE) {
                            ExerciseLibraryScreen(
                                onOpenSettings = { openMainDestination(SETTINGS_ROUTE) },
                                onOpenAccount = { openMainDestination(ACCOUNT_ROUTE) },
                            )
                        }
                        composable(HISTORY_ROUTE) {
                            HistoryScreen(
                                onOpenSettings = { openMainDestination(SETTINGS_ROUTE) },
                                onOpenAccount = { openMainDestination(ACCOUNT_ROUTE) },
                            )
                        }
                        composable(ACCOUNT_ROUTE) {
                            AccountScreen(
                                onBack = { navController.popBackStack() },
                                onProfileSaved = workoutViewModel::refresh,
                            )
                        }
                        composable(SETTINGS_ROUTE) {
                            SettingsScreen(
                                onOpenDeveloper = {
                                    navController.navigate(BIOLOGY_DEVELOPER_ROUTE) {
                                        launchSingleTop = true
                                    }
                                },
                                onOpenAccount = { openMainDestination(ACCOUNT_ROUTE) },
                            )
                        }
                        composable(BIOLOGY_DEVELOPER_ROUTE) {
                            BiologyDeveloperScreen(onBack = { navController.popBackStack() })
                        }
                    }
                    NativeRestTimerOverlay()
                    ExerciseReflectionOverlay(workoutViewModel)
                    SessionOutcomeOverlay(workoutViewModel)
                    if (biologyTask.phase != BiologyTaskPhase.IDLE) {
                        AssistChip(
                            onClick = {
                                navController.navigate(BIOLOGY_DEVELOPER_ROUTE) {
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            label = { Text(biologyTask.label ?: "Biological task") },
                        )
                    }
                }
            }
        }
    }
}
