package dev.kian.mymettle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

private const val TRAIN_ROUTE = "train"
private const val LIBRARY_ROUTE = "library"
private const val HISTORY_ROUTE = "history"
private const val SETTINGS_ROUTE = "settings"

@Composable
fun MyMettleApp() {
    val context = LocalContext.current
    val workoutViewModel: N2WorkoutViewModel = viewModel(
        factory = remember(context) { N2WorkoutViewModelFactory(context) },
    )
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TRAIN_ROUTE

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == TRAIN_ROUTE,
                    onClick = {
                        navController.navigate(TRAIN_ROUTE) {
                            popUpTo(TRAIN_ROUTE) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    icon = { Text("●") },
                    label = { Text("Train") },
                )
                NavigationBarItem(
                    selected = currentRoute == LIBRARY_ROUTE,
                    onClick = { navController.navigate(LIBRARY_ROUTE) { launchSingleTop = true } },
                    icon = { Text("≡") },
                    label = { Text("Library") },
                )
                NavigationBarItem(
                    selected = currentRoute == HISTORY_ROUTE,
                    onClick = { navController.navigate(HISTORY_ROUTE) { launchSingleTop = true } },
                    icon = { Text("◷") },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = currentRoute == SETTINGS_ROUTE,
                    onClick = { navController.navigate(SETTINGS_ROUTE) { launchSingleTop = true } },
                    icon = { Text("⚙") },
                    label = { Text("Settings") },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            NavHost(navController = navController, startDestination = TRAIN_ROUTE) {
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
