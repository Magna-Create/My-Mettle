package dev.kian.mymettle.ui

import android.os.Build
import android.os.Trace
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

enum class MettleGlassLabMode {
    Adaptive,
    Fixed,
}

data class MettleGlassLabState(
    val hazeEnabled: Boolean = true,
    val mode: MettleGlassLabMode = MettleGlassLabMode.Adaptive,
    val fixedQuality: Float = 0.50f,
    val checkpointId: Int = 0,
)

/**
 * Debug-only runtime controls for isolating Haze cost without changing Workout layout/composition.
 *
 * State deliberately lives in memory: every fresh process starts from the untouched alpha31 glass
 * baseline (Haze enabled + Default/Adaptive). Perfetto markers/counters are emitted only when the
 * tester changes a control or explicitly creates a checkpoint, never once per frame.
 */
object MettleGlassPerformanceLab {
    private val stateHolder = mutableStateOf(MettleGlassLabState())

    val state: MettleGlassLabState
        get() = stateHolder.value

    fun setHazeEnabled(enabled: Boolean) {
        if (state.hazeEnabled == enabled) return
        stateHolder.value = state.copy(hazeEnabled = enabled)
        emitTrace(if (enabled) "HAZE_ENABLED" else "HAZE_BYPASSED")
    }

    fun setMode(mode: MettleGlassLabMode) {
        if (state.mode == mode) return
        stateHolder.value = state.copy(mode = mode)
        emitTrace("MODE_${mode.name.uppercase()}")
    }

    fun setFixedQuality(quality: Float) {
        val clamped = quality.coerceIn(0.25f, 1f)
        if (state.fixedQuality == clamped) return
        stateHolder.value = state.copy(fixedQuality = clamped)
        emitTrace("QUALITY_${qualityPercent(clamped)}")
    }

    fun checkpoint() {
        stateHolder.value = state.copy(checkpointId = state.checkpointId + 1)
        emitTrace("CHECKPOINT_${state.checkpointId}")
    }

    fun resetBaseline() {
        stateHolder.value = MettleGlassLabState(checkpointId = state.checkpointId + 1)
        emitTrace("RESET_BASELINE_${state.checkpointId}")
    }

    private fun emitTrace(reason: String) {
        val snapshot = state
        val effectiveMode = when {
            !snapshot.hazeEnabled -> 0L
            snapshot.mode == MettleGlassLabMode.Adaptive -> 1L
            else -> 2L
        }
        val qualityPercent = qualityPercent(snapshot.fixedQuality).toLong()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.setCounter("MettleGlassLab.HazeEnabled", if (snapshot.hazeEnabled) 1L else 0L)
            Trace.setCounter("MettleGlassLab.Mode", effectiveMode)
            Trace.setCounter("MettleGlassLab.QualityPct", qualityPercent)
            Trace.setCounter("MettleGlassLab.Checkpoint", snapshot.checkpointId.toLong())
        }

        Trace.beginSection(
            "MettleGlassLab|$reason|haze=${if (snapshot.hazeEnabled) 1 else 0}" +
                "|mode=${snapshot.mode.name}|quality=$qualityPercent|checkpoint=${snapshot.checkpointId}",
        )
        Trace.endSection()
    }

    private fun qualityPercent(quality: Float): Int = (quality * 100f).roundToInt()
}

@Composable
internal fun MettleGlassPerformanceLabControls() {
    val state = MettleGlassPerformanceLab.state
    var draftQuality by remember { mutableFloatStateOf(state.fixedQuality) }

    LaunchedEffect(state.fixedQuality) {
        draftQuality = state.fixedQuality
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Glass performance lab",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "Runtime-only controls for Perfetto/Winscope ablations. Fresh app processes reset to alpha31 baseline.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Haze rendering", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (state.hazeEnabled) "Enabled" else "Bypassed; layout, tint, shadows, border and grain remain",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.hazeEnabled,
                onCheckedChange = MettleGlassPerformanceLab::setHazeEnabled,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.mode == MettleGlassLabMode.Adaptive,
                onClick = { MettleGlassPerformanceLab.setMode(MettleGlassLabMode.Adaptive) },
                enabled = state.hazeEnabled,
                label = { Text("Adaptive") },
            )
            FilterChip(
                selected = state.mode == MettleGlassLabMode.Fixed,
                onClick = { MettleGlassPerformanceLab.setMode(MettleGlassLabMode.Fixed) },
                enabled = state.hazeEnabled,
                label = { Text("Fixed") },
            )
        }

        Text(
            "Fixed quality: ${(draftQuality * 100f).roundToInt()}%",
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = draftQuality,
            onValueChange = { draftQuality = it },
            onValueChangeFinished = {
                MettleGlassPerformanceLab.setFixedQuality(draftQuality)
            },
            enabled = state.hazeEnabled && state.mode == MettleGlassLabMode.Fixed,
            valueRange = 0.25f..1f,
            steps = 14,
        )
        Text(
            "The renderer only receives the new fixed quality when you release the slider, preventing slider-drag churn from contaminating the test.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = MettleGlassPerformanceLab::checkpoint) {
                Text("Trace checkpoint #${state.checkpointId + 1}")
            }
            TextButton(onClick = MettleGlassPerformanceLab::resetBaseline) {
                Text("Reset baseline")
            }
        }

        Text(
            "Perfetto counters: MettleGlassLab.HazeEnabled (0/1), Mode (0 bypass / 1 adaptive / 2 fixed), QualityPct and Checkpoint. Every change also emits a MettleGlassLab|… trace slice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
