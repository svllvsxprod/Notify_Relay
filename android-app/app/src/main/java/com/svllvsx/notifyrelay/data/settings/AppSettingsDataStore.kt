package com.svllvsx.notifyrelay.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.svllvsx.notifyrelay.domain.model.AppSettings
import com.svllvsx.notifyrelay.domain.model.AppLanguage
import com.svllvsx.notifyrelay.domain.model.PrivacyMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("notify_relay_settings")

class AppSettingsDataStore(private val context: Context) {
    private object Keys {
        val serverUrl = stringPreferencesKey("server_url")
        val deviceId = stringPreferencesKey("device_id")
        val notificationForwardingEnabled = booleanPreferencesKey("notification_forwarding_enabled")
        val smsForwardingEnabled = booleanPreferencesKey("sms_forwarding_enabled")
        val privacyMode = stringPreferencesKey("privacy_mode")
        val language = stringPreferencesKey("language")
        val showSystemApps = booleanPreferencesKey("show_system_apps")
        val onboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            serverUrl = prefs[Keys.serverUrl].orEmpty(),
            deviceId = prefs[Keys.deviceId].orEmpty(),
            notificationForwardingEnabled = prefs[Keys.notificationForwardingEnabled] ?: true,
            smsForwardingEnabled = prefs[Keys.smsForwardingEnabled] ?: false,
            privacyMode = prefs[Keys.privacyMode]?.let { runCatching { PrivacyMode.valueOf(it) }.getOrNull() } ?: PrivacyMode.MASKED,
            language = prefs[Keys.language]?.let { runCatching { AppLanguage.valueOf(it) }.getOrNull() } ?: AppLanguage.SYSTEM,
            showSystemApps = prefs[Keys.showSystemApps] ?: true,
            onboardingCompleted = prefs[Keys.onboardingCompleted] ?: false,
            lastSyncAt = prefs[Keys.lastSyncAt] ?: 0L,
            lastSyncError = prefs[Keys.lastSyncError],
        )
    }

    suspend fun updateServerUrl(url: String) = context.dataStore.edit { it[Keys.serverUrl] = url.trimEnd('/') }
    suspend fun updateDeviceId(deviceId: String) = context.dataStore.edit { it[Keys.deviceId] = deviceId }
    suspend fun updateNotificationForwarding(enabled: Boolean) = context.dataStore.edit { it[Keys.notificationForwardingEnabled] = enabled }
    suspend fun updateSmsForwarding(enabled: Boolean) = context.dataStore.edit { it[Keys.smsForwardingEnabled] = enabled }
    suspend fun updatePrivacyMode(mode: PrivacyMode) = context.dataStore.edit { it[Keys.privacyMode] = mode.name }
    suspend fun updateLanguage(language: AppLanguage) = context.dataStore.edit { it[Keys.language] = language.name }
    suspend fun updateShowSystemApps(show: Boolean) = context.dataStore.edit { it[Keys.showSystemApps] = show }
    suspend fun updateOnboardingCompleted(completed: Boolean) = context.dataStore.edit { it[Keys.onboardingCompleted] = completed }
    suspend fun updateLastSync(successAt: Long, error: String?) = context.dataStore.edit { prefs ->
        prefs[Keys.lastSyncAt] = successAt
        if (error == null) prefs.remove(Keys.lastSyncError) else prefs[Keys.lastSyncError] = error
    }
    suspend fun resetDevice() = context.dataStore.edit { prefs ->
        prefs.remove(Keys.deviceId)
        prefs[Keys.onboardingCompleted] = false
    }
}
