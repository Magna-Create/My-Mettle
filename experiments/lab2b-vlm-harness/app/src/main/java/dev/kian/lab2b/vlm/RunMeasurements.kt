package dev.kian.lab2b.vlm

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.os.health.SystemHealthManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import org.json.JSONArray
import org.json.JSONObject

/** Whole-device/component counters, never claimed as process-specific energy. No idle polling. */
class RunMeasurements(private val context: Context) {
    private val samples = mutableListOf<JSONObject>()
    private var beforeRails = emptyList<RailReading>()
    private var railError: String? = null
    private var started = 0L
    private var poll: Job? = null
    suspend fun start(scope: CoroutineScope) {
        beforeRails = readRails()
        started = SystemClock.elapsedRealtime()
        synchronized(samples) { samples.add(sample()) }
        poll = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                synchronized(samples) { samples.add(sample()) }
            }
        }
    }
    suspend fun finish(): JSONObject {
        poll?.cancelAndJoin()
        val ended = SystemClock.elapsedRealtime()
        synchronized(samples) { samples.add(sample()) }
        val after = readRails()
        val railValues = JSONArray()
        after.forEach { end ->
            val start = beforeRails.find { it.name == end.name && it.type == end.type }
            val delta = start?.let { EnergyMath.microJouleDelta(it.energy, end.energy, it.time, end.time) }
            railValues.put(JSONObject().put("name", end.name).put("type", end.type)
                .put("start_microjoules", start?.energy ?: JSONObject.NULL).put("end_microjoules", end.energy)
                .put("start_elapsed_ms", start?.time ?: JSONObject.NULL).put("end_elapsed_ms", end.time)
                .put("joules", delta ?: JSONObject.NULL)
                .put("status", if (delta == null) "UNAVAILABLE_OR_UNRESOLVED" else "COUNTER_DELTA"))
        }
        val captured = synchronized(samples) { samples.toList() }
        return JSONObject().put("measurement_window_ms", ended - started)
            .put("scope", "Device/component energy, not app-only. Rails may overlap: do not sum them. No idle-baseline subtraction.")
            .put("power_monitors", railValues).put("monitor_error", railError ?: JSONObject.NULL)
            .put("energy_status", if (railValues.length() == 0) "UNAVAILABLE" else "SEE_EACH_MONITOR")
            .put("battery_note", "Raw battery gauges only; no per-turn energy inferred from percentage/current. Battery temperature is not CPU temperature.")
            .put("sampled_peak_pss_kb", captured.maxOfOrNull { it.optInt("pss_kb") } ?: 0)
            .put("sampling_note", "PSS sampled each second during this operation; brief peaks may be missed. Sampling overhead included.")
            .put("samples", JSONArray(captured))
    }
    private fun sample(): JSONObject {
        val battery = context.getSystemService(BatteryManager::class.java)
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        fun property(id: Int): Any = battery.getLongProperty(id).let { if (it == Long.MIN_VALUE) JSONObject.NULL else it }
        return JSONObject().put("elapsed_ms", SystemClock.elapsedRealtime())
            .put("pss_kb", memory.totalPss)
            .put("current_microamps", property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW))
            .put("remaining_energy_nanowatt_hours", property(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER))
            .put("charge_microamp_hours", property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER))
            .put("voltage_millivolts", intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1)
            .put("battery_temperature_tenths_c", intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1)
            .put("plugged", intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1)
            .put("thermal_status", context.getSystemService(PowerManager::class.java).currentThermalStatus)
    }
    private suspend fun readRails(): List<RailReading> {
        if (Build.VERSION.SDK_INT < 35) { railError = "Requires Android API 35"; return emptyList() }
        return try {
            withTimeoutOrNull(1500) { readApi35() } ?: emptyList<RailReading>().also { railError = "Power monitor query timed out" }
        } catch (e: Exception) { railError = e.message ?: e.javaClass.simpleName; emptyList() }
    }
    @RequiresApi(35)
    private suspend fun readApi35(): List<RailReading> {
        val manager = context.getSystemService(SystemHealthManager::class.java) ?: return emptyList()
        val monitors = suspendCancellableCoroutine<List<PowerMonitor>> { continuation ->
            manager.getSupportedPowerMonitors(context.mainExecutor) { if (continuation.isActive) continuation.resume(it) }
        }
        if (monitors.isEmpty()) { railError = "No supported device power monitors"; return emptyList() }
        val result = suspendCancellableCoroutine<PowerMonitorReadings?> { continuation ->
            manager.getPowerMonitorReadings(monitors, context.mainExecutor, object : OutcomeReceiver<PowerMonitorReadings, RuntimeException> {
                override fun onResult(result: PowerMonitorReadings) { if (continuation.isActive) continuation.resume(result) }
                override fun onError(error: RuntimeException) { railError = error.message; if (continuation.isActive) continuation.resume(null) }
            })
        } ?: return emptyList()
        return monitors.map { RailReading(it.name, it.type, result.getConsumedEnergy(it), result.getTimestampMillis(it)) }
    }
}
data class RailReading(val name: String, val type: Int, val energy: Long, val time: Long)
