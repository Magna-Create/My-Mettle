package dev.kian.mymettle.developer

import android.content.Context
import android.os.Build
import android.os.PowerManager
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Diagnostic-only runtime context for one physical N-BIO adaptive-acceptance run. */
data class NBioAdaptiveRuntimeInfo(
    val supportedAbis: List<String>,
    val availableProcessors: Int,
    val maxJavaHeapBytes: Long,
    val initialPowerSaveMode: Boolean?,
    val thermalHeadroomThresholds: Map<String, Double>?,
    val thermalHeadroomThresholdsUnavailableReason: String?,
)

data class NBioAdaptiveThermalSnapshot(
    val sequence: Int,
    val stage: String,
    val capturedAt: Instant,
    val thermalStatus: Int?,
    val thermalStatusLabel: String?,
    val thermalHeadroom: Double?,
    val thermalHeadroomUnavailableReason: String?,
    val powerSaveMode: Boolean?,
    val interactive: Boolean?,
)

data class NBioAdaptiveProfileTiming(
    val historicalBakeoffWallElapsedMillis: Long,
    val currentEvaluationWallElapsedMillis: Long,
    val frozenV1FitElapsedMillis: Long?,
    val replayFrozenV1FitElapsedMillis: Long?,
    val densePersistenceReloadElapsedMillis: Long?,
    val sparsePersistenceReloadElapsedMillis: Long?,
    val laplacePersistenceReloadElapsedMillis: Long?,
    val denseReplayElapsedMillis: Long?,
    val sparseReplayElapsedMillis: Long?,
    val laplaceReplayElapsedMillis: Long?,
    val sparseFidelityElapsedMillis: Long?,
    val laplaceFidelityElapsedMillis: Long?,
) {
    val persistenceReloadTotalMillis: Long
        get() = listOfNotNull(
            densePersistenceReloadElapsedMillis,
            sparsePersistenceReloadElapsedMillis,
            laplacePersistenceReloadElapsedMillis,
        ).sum()
    val replayTotalMillis: Long
        get() = listOfNotNull(denseReplayElapsedMillis, sparseReplayElapsedMillis, laplaceReplayElapsedMillis).sum()
    val fidelityTotalMillis: Long
        get() = listOfNotNull(sparseFidelityElapsedMillis, laplaceFidelityElapsedMillis).sum()
}

class NBioAdaptiveRuntimeTelemetry(context: Context) {
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private var lastHeadroomSampleNanos: Long = Long.MIN_VALUE

    fun runtimeInfo(): NBioAdaptiveRuntimeInfo {
        val thresholds: Map<String, Double>?
        val thresholdFailure: String?
        if (Build.VERSION.SDK_INT < 35) {
            thresholds = null
            thresholdFailure = "API_LT_35"
        } else if (powerManager == null) {
            thresholds = null
            thresholdFailure = "POWER_MANAGER_UNAVAILABLE"
        } else {
            val result = runCatching {
                powerManager.thermalHeadroomThresholds
                    .toSortedMap()
                    .mapKeys { thermalStatusLabel(it.key) }
                    .mapValues { it.value.toDouble() }
            }
            thresholds = result.getOrNull()
            thresholdFailure = result.exceptionOrNull()?.let { "${it::class.java.simpleName}:${it.message ?: "unknown"}" }
        }
        return NBioAdaptiveRuntimeInfo(
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            availableProcessors = Runtime.getRuntime().availableProcessors(),
            maxJavaHeapBytes = Runtime.getRuntime().maxMemory(),
            initialPowerSaveMode = powerManager?.isPowerSaveMode,
            thermalHeadroomThresholds = thresholds,
            thermalHeadroomThresholdsUnavailableReason = thresholdFailure,
        )
    }

    fun snapshot(sequence: Int, stage: String): NBioAdaptiveThermalSnapshot {
        val status = if (Build.VERSION.SDK_INT >= 29 && powerManager != null) {
            runCatching { powerManager.currentThermalStatus }.getOrNull()
        } else null

        val nowNanos = System.nanoTime()
        val headroomResult: Result<Double>? = when {
            Build.VERSION.SDK_INT < 30 -> null
            powerManager == null -> null
            lastHeadroomSampleNanos != Long.MIN_VALUE && nowNanos - lastHeadroomSampleNanos < 1_000_000_000L -> null
            else -> {
                lastHeadroomSampleNanos = nowNanos
                runCatching { powerManager.getThermalHeadroom(0).toDouble() }
            }
        }
        val rawHeadroom = headroomResult?.getOrNull()
        val headroom = rawHeadroom?.takeIf { it.isFinite() }
        val headroomReason = when {
            Build.VERSION.SDK_INT < 30 -> "API_LT_30"
            powerManager == null -> "POWER_MANAGER_UNAVAILABLE"
            headroomResult == null -> "NOT_SAMPLED_PLATFORM_RATE_GUIDANCE"
            headroomResult.isFailure -> headroomResult.exceptionOrNull()?.let { "${it::class.java.simpleName}:${it.message ?: "unknown"}" }
            rawHeadroom == null || !rawHeadroom.isFinite() -> "UNSUPPORTED_OR_NAN"
            else -> null
        }
        return NBioAdaptiveThermalSnapshot(
            sequence = sequence,
            stage = stage,
            capturedAt = Instant.now(),
            thermalStatus = status,
            thermalStatusLabel = status?.let(::thermalStatusLabel),
            thermalHeadroom = headroom,
            thermalHeadroomUnavailableReason = headroomReason,
            powerSaveMode = powerManager?.isPowerSaveMode,
            interactive = powerManager?.isInteractive,
        )
    }

    private fun thermalStatusLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NONE"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN_$status"
    }
}

internal fun NBioAdaptiveRuntimeInfo.toJson(): JSONObject = JSONObject()
    .put("supportedAbis", JSONArray(supportedAbis))
    .put("availableProcessors", availableProcessors)
    .put("maxJavaHeapBytes", maxJavaHeapBytes)
    .put("initialPowerSaveMode", initialPowerSaveMode ?: JSONObject.NULL)
    .put(
        "thermalHeadroomThresholds",
        thermalHeadroomThresholds?.let(::JSONObject) ?: JSONObject.NULL,
    )
    .put(
        "thermalHeadroomThresholdsUnavailableReason",
        thermalHeadroomThresholdsUnavailableReason ?: JSONObject.NULL,
    )

internal fun NBioAdaptiveThermalSnapshot.toJson(): JSONObject = JSONObject()
    .put("sequence", sequence)
    .put("stage", stage)
    .put("capturedAt", capturedAt.toString())
    .put("thermalStatus", thermalStatus ?: JSONObject.NULL)
    .put("thermalStatusLabel", thermalStatusLabel ?: JSONObject.NULL)
    .put("thermalHeadroom", thermalHeadroom ?: JSONObject.NULL)
    .put("thermalHeadroomUnavailableReason", thermalHeadroomUnavailableReason ?: JSONObject.NULL)
    .put("powerSaveMode", powerSaveMode ?: JSONObject.NULL)
    .put("interactive", interactive ?: JSONObject.NULL)

internal fun NBioAdaptiveProfileTiming.toJson(): JSONObject = JSONObject()
    .put("historicalBakeoffWallElapsedMillis", historicalBakeoffWallElapsedMillis)
    .put("currentEvaluationWallElapsedMillis", currentEvaluationWallElapsedMillis)
    .put("frozenV1FitElapsedMillis", frozenV1FitElapsedMillis ?: JSONObject.NULL)
    .put("replayFrozenV1FitElapsedMillis", replayFrozenV1FitElapsedMillis ?: JSONObject.NULL)
    .put("densePersistenceReloadElapsedMillis", densePersistenceReloadElapsedMillis ?: JSONObject.NULL)
    .put("sparsePersistenceReloadElapsedMillis", sparsePersistenceReloadElapsedMillis ?: JSONObject.NULL)
    .put("laplacePersistenceReloadElapsedMillis", laplacePersistenceReloadElapsedMillis ?: JSONObject.NULL)
    .put("persistenceReloadTotalMillis", persistenceReloadTotalMillis)
    .put("denseReplayElapsedMillis", denseReplayElapsedMillis ?: JSONObject.NULL)
    .put("sparseReplayElapsedMillis", sparseReplayElapsedMillis ?: JSONObject.NULL)
    .put("laplaceReplayElapsedMillis", laplaceReplayElapsedMillis ?: JSONObject.NULL)
    .put("replayTotalMillis", replayTotalMillis)
    .put("sparseFidelityElapsedMillis", sparseFidelityElapsedMillis ?: JSONObject.NULL)
    .put("laplaceFidelityElapsedMillis", laplaceFidelityElapsedMillis ?: JSONObject.NULL)
    .put("fidelityTotalMillis", fidelityTotalMillis)
