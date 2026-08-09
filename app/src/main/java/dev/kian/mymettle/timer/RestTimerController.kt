package dev.kian.mymettle.timer

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ceil

enum class RestTimerPhase {
    IDLE,
    RUNNING,
    PAUSED,
    READY,
}

data class RestTimerSnapshot(
    val phase: RestTimerPhase = RestTimerPhase.IDLE,
    val exerciseName: String = "",
    val startedElapsedRealtime: Long = 0L,
    val endElapsedRealtime: Long = 0L,
    val pausedRemainingMillis: Long = 0L,
) {
    val visible: Boolean get() = phase != RestTimerPhase.IDLE
    val active: Boolean get() = phase == RestTimerPhase.RUNNING || phase == RestTimerPhase.PAUSED

    fun remainingMillis(nowElapsed: Long = SystemClock.elapsedRealtime()): Long = when (phase) {
        RestTimerPhase.RUNNING -> (endElapsedRealtime - nowElapsed).coerceAtLeast(0L)
        RestTimerPhase.PAUSED -> pausedRemainingMillis.coerceAtLeast(0L)
        RestTimerPhase.READY, RestTimerPhase.IDLE -> 0L
    }

    fun remainingSeconds(nowElapsed: Long = SystemClock.elapsedRealtime()): Int =
        ceil(remainingMillis(nowElapsed) / 1000.0).toInt().coerceAtLeast(0)
}

class RestTimerController private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val persistence = RestTimerPersistence(appContext)
    private val notifications = appContext.getSystemService(NotificationManager::class.java)
    private val _state = MutableStateFlow(persistence.read())

    val state: StateFlow<RestTimerSnapshot> = _state.asStateFlow()

    fun refresh() {
        _state.value = persistence.read()
    }

    fun start(exerciseName: String, seconds: Int) {
        send(
            action = RestTimerService.ACTION_START,
            foregroundStart = true,
            extras = {
                putExtra(RestTimerService.EXTRA_EXERCISE_NAME, exerciseName)
                putExtra(RestTimerService.EXTRA_SECONDS, seconds.coerceAtLeast(1))
            },
        )
    }

    fun adjust(deltaSeconds: Int) {
        send(
            action = RestTimerService.ACTION_ADJUST,
            extras = { putExtra(RestTimerService.EXTRA_DELTA_SECONDS, deltaSeconds) },
        )
    }

    fun pause() = send(RestTimerService.ACTION_PAUSE)

    fun resume() = send(RestTimerService.ACTION_RESUME)

    fun stop() = send(RestTimerService.ACTION_STOP)

    fun dismissReady() {
        notifications.cancel(RestTimerService.READY_NOTIFICATION_ID)
        persistence.clear()
        _state.value = RestTimerSnapshot()
    }

    internal fun publish(snapshot: RestTimerSnapshot) {
        _state.value = snapshot
    }

    private fun send(
        action: String,
        foregroundStart: Boolean = false,
        extras: Intent.() -> Unit = {},
    ) {
        val intent = Intent(appContext, RestTimerService::class.java).apply {
            this.action = action
            extras()
        }
        if (foregroundStart) appContext.startForegroundService(intent) else appContext.startService(intent)
    }

    companion object {
        @Volatile
        private var instance: RestTimerController? = null

        fun get(context: Context): RestTimerController = instance ?: synchronized(this) {
            instance ?: RestTimerController(context).also { instance = it }
        }
    }
}

internal class RestTimerPersistence(context: Context) {
    private val preferences = context.getSharedPreferences("rest-timer", Context.MODE_PRIVATE)

    fun read(): RestTimerSnapshot {
        val phase = runCatching {
            RestTimerPhase.valueOf(preferences.getString(KEY_PHASE, RestTimerPhase.IDLE.name).orEmpty())
        }.getOrDefault(RestTimerPhase.IDLE)
        return RestTimerSnapshot(
            phase = phase,
            exerciseName = preferences.getString(KEY_EXERCISE, "").orEmpty(),
            startedElapsedRealtime = preferences.getLong(KEY_STARTED_ELAPSED, 0L),
            endElapsedRealtime = preferences.getLong(KEY_END_ELAPSED, 0L),
            pausedRemainingMillis = preferences.getLong(KEY_PAUSED_REMAINING, 0L),
        )
    }

    fun write(snapshot: RestTimerSnapshot) {
        preferences.edit()
            .putString(KEY_PHASE, snapshot.phase.name)
            .putString(KEY_EXERCISE, snapshot.exerciseName)
            .putLong(KEY_STARTED_ELAPSED, snapshot.startedElapsedRealtime)
            .putLong(KEY_END_ELAPSED, snapshot.endElapsedRealtime)
            .putLong(KEY_PAUSED_REMAINING, snapshot.pausedRemainingMillis)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val KEY_PHASE = "phase"
        const val KEY_EXERCISE = "exercise"
        const val KEY_STARTED_ELAPSED = "started_elapsed"
        const val KEY_END_ELAPSED = "end_elapsed"
        const val KEY_PAUSED_REMAINING = "paused_remaining"
    }
}
