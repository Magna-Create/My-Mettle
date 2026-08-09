package dev.kian.mymettle.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kian.mymettle.timer.RestTimerController
import dev.kian.mymettle.timer.RestTimerPhase
import kotlinx.coroutines.delay

/**
 * Visual surface for the Android-native rest timer.
 *
 * The timer itself lives outside Compose. This layer can therefore be completely redesigned later
 * without changing its foreground-service, persisted target time or notification behaviour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NativeRestTimerOverlay() {
    val context = LocalContext.current
    val controller = remember(context) { RestTimerController.get(context) }
    val snapshot by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var confirmEnd by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(snapshot.remainingSeconds()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        controller.refresh()
    }

    // A newly logged set deliberately expands the rest surface, even if the previous timer was
    // minimised. ±15 and pause/resume do not change this identity and therefore do not reopen it.
    LaunchedEffect(snapshot.startedElapsedRealtime) {
        if (snapshot.startedElapsedRealtime > 0L && snapshot.active) {
            expanded = true
            requestNotificationPermissionOnce(context, permissionLauncher::launch)
        }
    }

    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == RestTimerPhase.READY) expanded = true
        if (snapshot.phase == RestTimerPhase.IDLE) {
            expanded = false
            confirmEnd = false
        }
    }

    // This 1 Hz ticker exists only while the app is rendering the timer. The Android notification
    // uses the system chronometer, so the service does not wake Kotlin every second in background.
    LaunchedEffect(snapshot.phase, snapshot.endElapsedRealtime, snapshot.pausedRemainingMillis) {
        remainingSeconds = snapshot.remainingSeconds()
        while (snapshot.phase == RestTimerPhase.RUNNING && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds = snapshot.remainingSeconds()
        }
    }

    if (snapshot.visible && !expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(top = 68.dp, end = 12.dp),
            contentAlignment = Alignment.TopEnd,
        ) {
            AssistChip(
                onClick = { expanded = true },
                label = {
                    Text(
                        when (snapshot.phase) {
                            RestTimerPhase.READY -> "Ready"
                            RestTimerPhase.PAUSED -> "Paused · ${formatTime(remainingSeconds)}"
                            RestTimerPhase.RUNNING -> "Rest · ${formatTime(remainingSeconds)}"
                            RestTimerPhase.IDLE -> ""
                        },
                    )
                },
            )
        }
    }

    if (snapshot.visible && expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (snapshot.phase == RestTimerPhase.READY) "Ready" else "Rest",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(snapshot.exerciseName, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(14.dp))
                Text(
                    formatTime(remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(18.dp))

                when (snapshot.phase) {
                    RestTimerPhase.RUNNING,
                    RestTimerPhase.PAUSED,
                    -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { controller.adjust(-15) },
                                modifier = Modifier.weight(1f),
                            ) { Text("−15") }
                            Button(
                                onClick = {
                                    if (snapshot.phase == RestTimerPhase.RUNNING) controller.pause()
                                    else controller.resume()
                                },
                                modifier = Modifier.weight(1.2f),
                            ) {
                                Text(if (snapshot.phase == RestTimerPhase.RUNNING) "Pause" else "Resume")
                            }
                            FilledTonalButton(
                                onClick = { controller.adjust(15) },
                                modifier = Modifier.weight(1f),
                            ) { Text("+15") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { expanded = false },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Minimise") }
                        Spacer(Modifier.height(6.dp))
                        TextButton(onClick = { confirmEnd = true }) { Text("End timer") }
                    }

                    RestTimerPhase.READY -> {
                        Button(
                            onClick = { controller.dismissReady() },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Done") }
                    }

                    RestTimerPhase.IDLE -> Unit
                }
            }
        }
    }

    if (confirmEnd && snapshot.active) {
        AlertDialog(
            onDismissRequest = { confirmEnd = false },
            title = { Text("End rest timer?") },
            text = { Text("The set you logged is already saved. This only ends the countdown.") },
            confirmButton = {
                Button(onClick = {
                    controller.stop()
                    confirmEnd = false
                }) { Text("End timer") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEnd = false }) { Text("Keep resting") }
            },
        )
    }
}

private fun requestNotificationPermissionOnce(
    context: Context,
    launch: (String) -> Unit,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return

    val preferences = context.getSharedPreferences("rest-timer-ui", Context.MODE_PRIVATE)
    if (preferences.getBoolean("notification_permission_requested", false)) return
    preferences.edit().putBoolean("notification_permission_requested", true).apply()
    launch(Manifest.permission.POST_NOTIFICATIONS)
}

private fun formatTime(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
