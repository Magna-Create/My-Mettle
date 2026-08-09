package dev.kian.mymettle.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.kian.mymettle.data.migration.LegacyRestTimerSettings

private val Context.settingsDataStore by preferencesDataStore(name = "my_mettle_settings")

class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    suspend fun importLegacyRestTimer(settings: LegacyRestTimerSettings) {
        dataStore.edit { preferences ->
            preferences[Keys.restAutoStart] = settings.autoStart
            preferences[Keys.restVibrationEnabled] = settings.vibrationEnabled
            preferences[Keys.restVibrationStrength] = settings.vibrationStrength
            preferences[Keys.restChimeEnabled] = settings.chimeEnabled
            preferences[Keys.restBackgroundNotificationEnabled] = settings.backgroundNotificationEnabled
        }
    }

    private object Keys {
        val restAutoStart = booleanPreferencesKey("rest_auto_start")
        val restVibrationEnabled = booleanPreferencesKey("rest_vibration_enabled")
        val restVibrationStrength = stringPreferencesKey("rest_vibration_strength")
        val restChimeEnabled = booleanPreferencesKey("rest_chime_enabled")
        val restBackgroundNotificationEnabled = booleanPreferencesKey("rest_background_notification_enabled")
    }
}
