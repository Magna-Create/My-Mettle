package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private const val HOME_ROUTE = "home"
private const val INTENSITY_ROUTE = "intensity"
private const val TRAIN_ROUTE = "train"
private const val LIBRARY_ROUTE = "library"
private const val HISTORY_ROUTE = "history"
private const val SETTINGS_ROUTE = "settings"

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
    val hazeState = rememberHazeState()
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
        // Haze samples the app-level artwork. The selector gets its own cool backdrop so its glass
        // surfaces do not inherit the green Daily Update source.
        if (currentRoute == INTENSITY_ROUTE) {
            IntensityHazeBackdrop(
                modifier = Modifier.hazeSource(hazeState),
            )
        } else {
            MettleGradientBackground(
                modifier = Modifier.hazeSource(hazeState),
                content = {},
            )
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
                    if (currentRoute == INTENSITY_ROUTE) {
                        IntensityBottomToolbar(
                            onOpenHome = ::openHomeDestination,
                            onOpenWorkout = { openMainDestination(TRAIN_ROUTE) },
                            onOpenHistory = { openMainDestination(HISTORY_ROUTE) },
                            onOpenLibrary = { openMainDestination(LIBRARY_ROUTE) },
                        )
                    } else {
                        MettleBottomToolbar(
                            selectedIndex = when (currentRoute) {
                                HOME_ROUTE -> 0
                                TRAIN_ROUTE -> 1
                                HISTORY_ROUTE -> 2
                                LIBRARY_ROUTE -> 3
                                else -> -1
                            },
                            onOpenHome = ::openHomeDestination,
                            onOpenWorkout = { openMainDestination(TRAIN_ROUTE) },
                            onOpenHistory = { openMainDestination(HISTORY_ROUTE) },
                            onOpenLibrary = { openMainDestination(LIBRARY_ROUTE) },
                        )
                    }
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
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
                                onOpenAccount = { openMainDestination(HISTORY_ROUTE) },
                            )
                        }
                        composable(INTENSITY_ROUTE) {
                            IntensitySelectorScreenV2(
                                viewModel = workoutViewModel,
                                onOpenWorkout = ::openWorkoutFromIntensity,
                                onOpenSettings = { openMainDestination(SETTINGS_ROUTE) },
                                onOpenAccount = { openMainDestination(HISTORY_ROUTE) },
                            )
                        }
                        composable(TRAIN_ROUTE) { TrainScreen(workoutViewModel) }
                        composable(LIBRARY_ROUTE) { ExerciseLibraryScreen() }
                        composable(HISTORY_ROUTE) { HistoryScreen() }
                        composable(SETTINGS_ROUTE) { SettingsScreen() }
                    }
                    NativeRestTimerOverlay()
                    ExerciseReflectionOverlay(workoutViewModel)
                    SessionOutcomeOverlay(workoutViewModel)
                }
            }
        }
    }
}
