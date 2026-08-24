package dev.kian.mymettle.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import dev.kian.mymettle.MainActivity
import dev.kian.mymettle.R
import dev.kian.mymettle.data.settings.RestTimerPreferences
import dev.kian.mymettle.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.ceil

class RestTimerService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var persistence: RestTimerPersistence
    private lateinit var notifications: NotificationManager
    private lateinit var vibrator: Vibrator

    @Volatile
    private var alertPreferences = RestTimerPreferences()

    private val finishRunnable = Runnable { finishTimer() }

    override fun onCreate() {
        super.onCreate()
        persistence = RestTimerPersistence(applicationContext)
        notifications = getSystemService(NotificationManager::class.java)
        vibrator = getSystemService(Vibrator::class.java)
        createChannels()
        serviceScope.launch {
            runCatching { SettingsStore(applicationContext).restTimerPreferences() }
                .onSuccess { alertPreferences = it }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTimer(
                exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME).orEmpty(),
                seconds = intent.getIntExtra(EXTRA_SECONDS, 1),
            )
            ACTION_ADJUST -> adjustTimer(intent.getIntExtra(EXTRA_DELTA_SECONDS, 0))
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP -> stopTimer()
            ACTION_DISMISS_READY -> dismissReady()
            ACTION_TEST_ALERT -> testAlert()
            else -> restoreAfterRestart()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(finishRunnable)
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun testAlert() {
        serviceScope.launch {
            val preferences = runCatching { SettingsStore(applicationContext).restTimerPreferences() }
                .getOrDefault(alertPreferences)
            handler.post {
                signalRestComplete(preferences)
                stopSelf()
            }
        }
    }

    private fun startTimer(exerciseName: String, seconds: Int) {
        notifications.cancel(READY_NOTIFICATION_ID)
        val now = SystemClock.elapsedRealtime()
        val duration = seconds.coerceAtLeast(1) * 1_000L
        val snapshot = RestTimerSnapshot(
            phase = RestTimerPhase.RUNNING,
            exerciseName = exerciseName,
            startedElapsedRealtime = now,
            endElapsedRealtime = now + duration,
            pausedRemainingMillis = 0L,
            totalDurationMillis = duration,
        )
        persistAndPublish(snapshot)
        showForeground(snapshot)
        scheduleFinish(snapshot)
    }

    private fun adjustTimer(deltaSeconds: Int) {
        if (deltaSeconds == 0) return
        val current = persistence.read()
        if (!current.active) return
        val deltaMillis = deltaSeconds * 1_000L
        val now = SystemClock.elapsedRealtime()
        val updated = when (current.phase) {
            RestTimerPhase.RUNNING -> {
                val remaining = (current.endElapsedRealtime - now + deltaMillis).coerceAtLeast(0L)
                if (remaining == 0L) {
                    finishTimer()
                    return
                }
                current.copy(
                    endElapsedRealtime = now + remaining,
                    totalDurationMillis = (current.totalDurationMillis + deltaMillis).coerceAtLeast(remaining),
                )
            }
            RestTimerPhase.PAUSED -> {
                val remaining = (current.pausedRemainingMillis + deltaMillis).coerceAtLeast(0L)
                if (remaining == 0L) {
                    finishTimer()
                    return
                }
                current.copy(
                    pausedRemainingMillis = remaining,
                    totalDurationMillis = (current.totalDurationMillis + deltaMillis).coerceAtLeast(remaining),
                )
            }
            else -> return
        }
        persistAndPublish(updated)
        showForeground(updated)
        scheduleFinish(updated)
    }

    private fun pauseTimer() {
        val current = persistence.read()
        if (current.phase != RestTimerPhase.RUNNING) return
        val remaining = current.remainingMillis()
        if (remaining <= 0L) {
            finishTimer()
            return
        }
        val updated = current.copy(
            phase = RestTimerPhase.PAUSED,
            endElapsedRealtime = 0L,
            pausedRemainingMillis = remaining,
        )
        handler.removeCallbacks(finishRunnable)
        persistAndPublish(updated)
        showForeground(updated)
    }

    private fun resumeTimer() {
        val current = persistence.read()
        if (current.phase != RestTimerPhase.PAUSED) return
        val remaining = current.pausedRemainingMillis.coerceAtLeast(1_000L)
        val updated = current.copy(
            phase = RestTimerPhase.RUNNING,
            endElapsedRealtime = SystemClock.elapsedRealtime() + remaining,
            pausedRemainingMillis = 0L,
        )
        persistAndPublish(updated)
        showForeground(updated)
        scheduleFinish(updated)
    }

    private fun stopTimer() {
        handler.removeCallbacks(finishRunnable)
        persistence.clear()
        RestTimerController.get(applicationContext).publish(RestTimerSnapshot())
        notifications.cancel(ACTIVE_NOTIFICATION_ID)
        notifications.cancel(READY_NOTIFICATION_ID)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun dismissReady() {
        notifications.cancel(READY_NOTIFICATION_ID)
        persistence.clear()
        RestTimerController.get(applicationContext).publish(RestTimerSnapshot())
        stopSelf()
    }

    private fun finishTimer() {
        handler.removeCallbacks(finishRunnable)
        val current = persistence.read()
        if (current.phase == RestTimerPhase.IDLE || current.phase == RestTimerPhase.READY) return
        val ready = current.copy(
            phase = RestTimerPhase.READY,
            endElapsedRealtime = 0L,
            pausedRemainingMillis = 0L,
        )
        persistAndPublish(ready)
        stopForeground(STOP_FOREGROUND_REMOVE)
        notifications.cancel(ACTIVE_NOTIFICATION_ID)
        notifications.notify(READY_NOTIFICATION_ID, readyNotification(ready))
        signalRestComplete(alertPreferences)
        stopSelf()
    }

    private fun signalRestComplete(preferences: RestTimerPreferences) {
        if (preferences.vibrationEnabled && vibrator.hasVibrator()) {
            val pattern = when (preferences.vibrationStrength.lowercase()) {
                "gentle", "light" -> longArrayOf(0L, 100L, 80L, 140L)
                "medium" -> longArrayOf(0L, 140L, 80L, 180L)
                else -> longArrayOf(0L, 180L, 80L, 180L, 80L, 260L)
            }
            val effect = VibrationEffect.createWaveform(pattern, -1)
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(
                    effect,
                    VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(
                    effect,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }

        if (preferences.chimeEnabled) {
            runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_ALARM, 82)
                tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 420)
                handler.postDelayed({ tone.release() }, 700L)
            }
        }
    }

    private fun restoreAfterRestart() {
        val current = persistence.read()
        when (current.phase) {
            RestTimerPhase.RUNNING -> {
                if (current.remainingMillis() <= 0L) finishTimer()
                else {
                    RestTimerController.get(applicationContext).publish(current)
                    showForeground(current)
                    scheduleFinish(current)
                }
            }
            RestTimerPhase.PAUSED -> {
                RestTimerController.get(applicationContext).publish(current)
                showForeground(current)
            }
            RestTimerPhase.READY -> {
                RestTimerController.get(applicationContext).publish(current)
                notifications.notify(READY_NOTIFICATION_ID, readyNotification(current))
                stopSelf()
            }
            RestTimerPhase.IDLE -> stopSelf()
        }
    }

    private fun scheduleFinish(snapshot: RestTimerSnapshot) {
        handler.removeCallbacks(finishRunnable)
        if (snapshot.phase != RestTimerPhase.RUNNING) return
        val delayMillis = snapshot.remainingMillis()
        if (delayMillis <= 0L) finishTimer()
        else handler.postDelayed(finishRunnable, delayMillis)
    }

    private fun showForeground(snapshot: RestTimerSnapshot) {
        val notification = activeNotification(snapshot)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                ACTIVE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(ACTIVE_NOTIFICATION_ID, notification)
        }
    }

    private fun activeNotification(snapshot: RestTimerSnapshot): Notification {
        val builder = Notification.Builder(this, ACTIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rest_timer)
            .setContentTitle("Rest · ${snapshot.exerciseName}")
            .setCategory(Notification.CATEGORY_STATUS)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())

        when (snapshot.phase) {
            RestTimerPhase.RUNNING -> {
                val remaining = snapshot.remainingMillis()
                builder
                    .setContentText("Rest timer running")
                    .setWhen(System.currentTimeMillis() + remaining)
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true)
                    .addAction(notificationAction("−15", ACTION_ADJUST, 11, -15))
                    .addAction(notificationAction("Pause", ACTION_PAUSE, 12))
                    .addAction(notificationAction("+15", ACTION_ADJUST, 13, 15))
            }
            RestTimerPhase.PAUSED -> {
                builder
                    .setContentText("Paused · ${formatMillis(snapshot.pausedRemainingMillis)}")
                    .setShowWhen(false)
                    .addAction(notificationAction("−15", ACTION_ADJUST, 21, -15))
                    .addAction(notificationAction("Resume", ACTION_RESUME, 22))
                    .addAction(notificationAction("+15", ACTION_ADJUST, 23, 15))
            }
            else -> Unit
        }

        builder.addAction(notificationAction("End", ACTION_STOP, 30))
        return builder.build()
    }

    private fun readyNotification(snapshot: RestTimerSnapshot): Notification =
        Notification.Builder(this, READY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rest_timer)
            .setContentTitle("Ready")
            .setContentText("${snapshot.exerciseName} · rest complete")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun notificationAction(
        title: String,
        action: String,
        requestCode: Int,
        deltaSeconds: Int? = null,
    ): Notification.Action {
        val intent = Intent(this, RestTimerService::class.java).apply {
            this.action = action
            if (deltaSeconds != null) putExtra(EXTRA_DELTA_SECONDS, deltaSeconds)
        }
        val pendingIntent = PendingIntent.getService(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Action.Builder(0, title, pendingIntent).build()
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this,
            100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannels() {
        val active = NotificationChannel(
            ACTIVE_CHANNEL_ID,
            "Rest timer",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Visible countdown while a My Mettle rest timer is active."
            setSound(null, null)
            enableVibration(false)
        }
        val ready = NotificationChannel(
            READY_CHANNEL_ID,
            "Rest complete",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Alerts when a My Mettle rest timer finishes."
            enableVibration(true)
        }
        notifications.createNotificationChannels(listOf(active, ready))
    }

    private fun persistAndPublish(snapshot: RestTimerSnapshot) {
        persistence.write(snapshot)
        RestTimerController.get(applicationContext).publish(snapshot)
    }

    companion object {
        const val ACTION_START = "dev.kian.mymettle.timer.START"
        const val ACTION_ADJUST = "dev.kian.mymettle.timer.ADJUST"
        const val ACTION_PAUSE = "dev.kian.mymettle.timer.PAUSE"
        const val ACTION_RESUME = "dev.kian.mymettle.timer.RESUME"
        const val ACTION_STOP = "dev.kian.mymettle.timer.STOP"
        const val ACTION_DISMISS_READY = "dev.kian.mymettle.timer.DISMISS_READY"
        const val ACTION_TEST_ALERT = "dev.kian.mymettle.timer.TEST_ALERT"

        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_SECONDS = "seconds"
        const val EXTRA_DELTA_SECONDS = "delta_seconds"

        const val ACTIVE_NOTIFICATION_ID = 2001
        const val READY_NOTIFICATION_ID = 2002
        const val ACTIVE_CHANNEL_ID = "rest_timer_active"
        const val READY_CHANNEL_ID = "rest_timer_ready"
    }
}

private fun formatMillis(value: Long): String {
    val seconds = ceil(value.coerceAtLeast(0L) / 1000.0).toInt()
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
