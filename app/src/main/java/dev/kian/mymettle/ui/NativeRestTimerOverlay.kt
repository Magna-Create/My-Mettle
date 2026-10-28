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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.sin
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
    var elapsedFraction by remember { mutableStateOf(snapshot.elapsedFraction()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // The sheet owns its Haze source. Sampling the workout screen directly used to make
    // these controls almost black or inconsistent as the workout scrolled behind the modal.
    val timerSheetHaze = rememberHazeState()

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
        elapsedFraction = snapshot.elapsedFraction()
        while (snapshot.phase == RestTimerPhase.RUNNING && remainingSeconds > 0) {
            delay(250)
            remainingSeconds = snapshot.remainingSeconds()
            elapsedFraction = snapshot.elapsedFraction()
        }
    }

    if (snapshot.visible && expanded) {
        ModalBottomSheet(
            onDismissRequest = { expanded = false },
            containerColor = TimerInk,
            contentColor = TimerPaper,
            dragHandle = {
                Surface(
                    modifier = Modifier.padding(top = 11.dp).size(width = 32.dp, height = 4.dp),
                    shape = CircleShape,
                    color = TimerPaper.copy(alpha = .42f),
                ) {}
            },
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(440.dp)) {
                // A dedicated, static canvas is the Haze source for the sheet's glass controls.
                // Keep the source separate from the foreground, preventing recursive refraction.
                Canvas(Modifier.fillMaxSize().hazeSource(timerSheetHaze)) {
                    drawRect(TimerInk)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF5E7A7A).copy(alpha = .82f),
                                Color(0xFF3F5652).copy(alpha = .55f),
                                Color.Transparent,
                            ),
                            center = Offset(size.width * .50f, size.height * .92f),
                            radius = size.width * .84f,
                        ),
                    )
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(TimerInk, Color.Transparent),
                            startY = 0f,
                            endY = size.height * .55f,
                        ),
                    )
                }
                CompositionLocalProvider(LocalMettleHazeState provides timerSheetHaze) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 30.dp)
                            .padding(bottom = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            snapshot.exerciseName,
                            color = TimerPaper,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (snapshot.phase == RestTimerPhase.READY) "Ready" else formatTime(remainingSeconds),
                            color = TimerPaper,
                            fontSize = 60.sp,
                            lineHeight = 68.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(5.dp))
                        TimerWaveProgress(
                            fraction = elapsedFraction,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        if (snapshot.phase == RestTimerPhase.READY) {
                            MettleGlassActionButton(
                                onClick = { controller.dismissReady() },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Done")
                            }
                        } else {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TimerCircle("−15s") { controller.adjust(-15) }
                                TimerCircle(
                                    icon = if (snapshot.phase == RestTimerPhase.RUNNING) MettleIcons.Pause else MettleIcons.PlayArrow,
                                    description = if (snapshot.phase == RestTimerPhase.RUNNING) "Pause timer" else "Resume timer",
                                ) {
                                    if (snapshot.phase == RestTimerPhase.RUNNING) controller.pause() else controller.resume()
                                }
                                TimerCircle("+15s") { controller.adjust(15) }
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TimerPill(
                                    label = "Hold to cancel",
                                    colour = TimerError,
                                    modifier = Modifier.weight(1f),
                                    onClick = {},
                                    onLongClick = { controller.stop() },
                                )
                                TimerPill(
                                    label = "Minimise timer",
                                    colour = TimerCyan,
                                    modifier = Modifier.weight(1f),
                                    onClick = { expanded = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val TimerInk = Color(0xFF11140F)
private val TimerPaper = Color(0xFFE1E4DA)
private val TimerCyan = Color(0xFFBBEBED)
private val TimerError = Color(0xFFFFB4AB)

/**
 * The same waveform, dual diffused strokes, marker and radial halo used in
 * WorkoutV2WaveProgress. The fractional value is the existing elapsed timer fraction.
 * Unlike Material's disabled Slider, this track remains cyan and visually active.
 */
@Composable
private fun TimerWaveProgress(fraction: Float, modifier: Modifier = Modifier) {
    Canvas(modifier.padding(horizontal = 9.dp)) {
        val y = size.height / 2f
        val endX = size.width * fraction.coerceIn(0f, 1f)
        val trackWidth = 2.1.dp.toPx()
        drawLine(
            color = TimerPaper.copy(alpha = .84f),
            start = Offset(endX, y),
            end = Offset(size.width, y),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round,
        )
        if (endX > 0f) {
            val amplitude = 3.6.dp.toPx()
            val wavelength = 18.dp.toPx().coerceAtLeast(1f)
            val step = 1.5.dp.toPx().coerceAtLeast(1f)
            val wave = Path().apply {
                moveTo(0f, y)
                var position = step
                while (position < endX) {
                    lineTo(position, y + sin((position / wavelength) * Math.PI * 2.0).toFloat() * amplitude)
                    position += step
                }
                lineTo(endX, y)
            }
            drawPath(wave, TimerCyan.copy(alpha = .07f), style = Stroke(width = 13.dp.toPx()))
            drawPath(wave, TimerCyan.copy(alpha = .18f), style = Stroke(width = 6.dp.toPx()))
            drawPath(wave, TimerCyan, style = Stroke(width = trackWidth))
        }
        // The radial glow follows the marker even on the first frame of a fresh timer.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(TimerCyan.copy(alpha = .27f), Color.Transparent),
                center = Offset(endX, y),
                radius = 19.dp.toPx(),
            ),
            radius = 19.dp.toPx(),
            center = Offset(endX, y),
        )
        drawLine(
            color = TimerPaper,
            start = Offset(endX, y - 12.dp.toPx()),
            end = Offset(endX, y + 12.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun TimerCircle(label: String, onClick: () -> Unit) {
    MettleControlGlassSurface(
        modifier = Modifier.size(84.dp),
        shape = RoundedCornerShape(24.dp),
        tint = TimerCyan.copy(alpha = .13f),
        baseColor = Color(0xFF4C6C6B).copy(alpha = .30f),
        borderColor = TimerCyan.copy(alpha = .43f),
        shadowElevation = 6.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(label, color = TimerCyan, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TimerCircle(icon: ImageVector, description: String, onClick: () -> Unit) {
    MettleControlGlassSurface(
        modifier = Modifier.size(width = 100.dp, height = 84.dp),
        shape = RoundedCornerShape(27.dp),
        tint = TimerCyan.copy(alpha = .16f),
        baseColor = Color(0xFF527573).copy(alpha = .34f),
        borderColor = TimerCyan.copy(alpha = .47f),
        shadowElevation = 7.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, tint = TimerPaper, modifier = Modifier.size(31.dp))
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
            tint = colour.copy(alpha = .13f),
            baseColor = colour.copy(alpha = .19f),
            borderColor = colour.copy(alpha = .48f),
            shadowElevation = 5.dp,
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
