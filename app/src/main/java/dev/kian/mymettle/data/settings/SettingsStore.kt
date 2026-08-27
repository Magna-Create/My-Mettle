package dev.kian.mymettle.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kian.mymettle.data.migration.LegacyRestTimerSettings
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "my_mettle_settings")

data class RestTimerPreferences(
    val autoStart: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val vibrationStrength: String = "strong",
    val chimeEnabled: Boolean = false,
)

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    suspend fun importLegacyRestTimer(settings: LegacyRestTimerSettings) {
        writeRestTimer(
            RestTimerPreferences(
                autoStart = settings.autoStart,
                vibrationEnabled = settings.vibrationEnabled,
                vibrationStrength = settings.vibrationStrength,
                chimeEnabled = settings.chimeEnabled,
            ),
        )
    }

    suspend fun restTimerPreferences(): RestTimerPreferences {
        val preferences = dataStore.data.first()
        return RestTimerPreferences(
            autoStart = preferences[Keys.restAutoStart] ?: true,
            vibrationEnabled = preferences[Keys.restVibrationEnabled] ?: true,
            vibrationStrength = preferences[Keys.restVibrationStrength] ?: "strong",
            chimeEnabled = preferences[Keys.restChimeEnabled] ?: false,
        )
    }

    suspend fun writeRestTimer(value: RestTimerPreferences) {
        dataStore.edit { preferences ->
            preferences[Keys.restAutoStart] = value.autoStart
            preferences[Keys.restVibrationEnabled] = value.vibrationEnabled
            preferences[Keys.restVibrationStrength] = value.vibrationStrength
            preferences[Keys.restChimeEnabled] = value.chimeEnabled
            preferences.remove(Keys.legacyRestBackgroundNotificationEnabled)
        }
    }

    private object Keys {
        val restAutoStart = booleanPreferencesKey("rest_auto_start")
        val restVibrationEnabled = booleanPreferencesKey("rest_vibration_enabled")
        val restVibrationStrength = stringPreferencesKey("rest_vibration_strength")
        val restChimeEnabled = booleanPreferencesKey("rest_chime_enabled")
        val legacyRestBackgroundNotificationEnabled = booleanPreferencesKey("rest_background_notification_enabled")
    }
}
