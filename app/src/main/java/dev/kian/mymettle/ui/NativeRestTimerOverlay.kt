package dev.kian.mymettle.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kian.mymettle.timer.RestTimerController
import dev.kian.mymettle.timer.RestTimerPhase
import kotlinx.coroutines.delay

/** A tiny app-local bridge lets the timer replace the hotbar's left action while minimised. */
internal object RestTimerOverlayUi {
    var expandRequest by mutableIntStateOf(0)
        private set

    fun expand() {
        expandRequest += 1
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NativeRestTimerOverlay() {
    val context = LocalContext.current
    val controller = remember(context) { RestTimerController.get(context) }
    val snapshot by controller.state.collectAsState()
    var expanded by remember { mutableStateOf(false) }
    var remainingSeconds by remember { mutableIntStateOf(snapshot.remainingSeconds()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) { controller.refresh() }
    LaunchedEffect(RestTimerOverlayUi.expandRequest) {
        if (snapshot.visible) expanded = true
    }
    LaunchedEffect(snapshot.startedElapsedRealtime) {
        if (snapshot.startedElapsedRealtime > 0L && snapshot.active) {
            expanded = true
            requestNotificationPermissionOnce(context, permissionLauncher::launch)
        }
    }
    LaunchedEffect(snapshot.phase) {
        if (snapshot.phase == RestTimerPhase.READY) expanded = true
        if (snapshot.phase == RestTimerPhase.IDLE) expanded = false
    }
    LaunchedEffect(snapshot.phase, snapshot.endElapsedRealtime, snapshot.pausedRemainingMillis) {
        remainingSeconds = snapshot.remainingSeconds()
        while (snapshot.phase == RestTimerPhase.RUNNING && remainingSeconds > 0) {
            delay(1_000)
            remainingSeconds = snapshot.remainingSeconds()
        }
    }

    if (snapshot.visible && expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = Color(0xFF11140F),
            contentColor = Color(0xFFE1E4DA),
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(top = 11.dp).size(width = 42.dp, height = 5.dp),
                    shape = CircleShape,
                    color = Color(0xFFE1E4DA).copy(alpha = .28f),
                ) {}
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(horizontal = 50.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(16.dp))
                Text(snapshot.exerciseName, color = Color(0xFFE1E4DA), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (snapshot.phase == RestTimerPhase.READY) "Ready" else formatTime(remainingSeconds),
                    color = Color(0xFFE1E4DA),
                    fontSize = 60.sp,
                    lineHeight = 68.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(15.dp))
                TimerWaveform(Modifier.fillMaxWidth().height(42.dp))
                Spacer(Modifier.height(18.dp))

                if (snapshot.phase == RestTimerPhase.READY) {
                    MettleGlassActionButton(onClick = { controller.dismissReady() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Done")
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        TimerCircle("−15") { controller.adjust(-15) }
                        TimerCircle(if (snapshot.phase == RestTimerPhase.RUNNING) "Ⅱ" else "▶") {
                            if (snapshot.phase == RestTimerPhase.RUNNING) controller.pause() else controller.resume()
                        }
                        TimerCircle("+15") { controller.adjust(15) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimerPill(
                            label = "Hold to cancel",
                            colour = Color(0xFFFFB4AB),
                            modifier = Modifier.weight(1f),
                            onClick = {},
                            onLongClick = { controller.stop() },
                        )
                        TimerPill(
                            label = "Minimise timer",
                            colour = Color(0xFFBBEBED),
                            modifier = Modifier.weight(1f),
                            onClick = { expanded = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimerWaveform(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        val path = Path().apply {
            moveTo(0f, y)
            val points = 16
            for (index in 1..points) {
                val x = size.width * index / points
                val amplitude = if (index in 5..11) size.height * .32f else size.height * .12f
                val pointY = y + if (index % 2 == 0) amplitude else -amplitude
                lineTo(x, pointY)
            }
        }
        drawPath(path, Color(0xFFA0CFD0), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Color(0xFFBBEBED), radius = 4.dp.toPx(), center = Offset(size.width * .62f, y))
    }
}

@Composable
private fun TimerCircle(label: String, onClick: () -> Unit) {
    MettleControlGlassSurface(
        modifier = Modifier.size(100.dp),
        shape = CircleShape,
        tint = Color(0xFFBBEBED).copy(alpha = .028f),
        borderColor = Color(0xFFBBEBED).copy(alpha = .36f),
        shadowElevation = 5.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = Color(0xFFE1E4DA), fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimerPill(
    label: String,
    colour: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(50.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        MettleControlGlassSurface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            tint = colour.copy(alpha = .055f),
            borderColor = colour.copy(alpha = .48f),
            shadowElevation = 4.dp,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(label, color = colour, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }
    }
}

private fun requestNotificationPermissionOnce(context: Context, launch: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    val preferences = context.getSharedPreferences("rest-timer-ui", Context.MODE_PRIVATE)
    if (preferences.getBoolean("notification_permission_requested", false)) return
    preferences.edit().putBoolean("notification_permission_requested", true).apply()
    launch(Manifest.permission.POST_NOTIFICATIONS)
}

private fun formatTime(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
