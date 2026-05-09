package com.svllvsx.notifyrelay.domain.model

data class RelayEvent(
    val eventId: String,
    val idempotencyKey: String,
    val type: RelayEventType,
    val packageName: String?,
    val appLabel: String?,
    val sender: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val timestamp: Long,
)

enum class RelayEventType { NOTIFICATION, SMS }
enum class PrivacyMode { FULL, MASKED, MINIMAL }
enum class AppLanguage { SYSTEM, RU, EN }

data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

data class AppSettings(
    val serverUrl: String = "",
    val deviceId: String = "",
    val notificationForwardingEnabled: Boolean = true,
    val smsForwardingEnabled: Boolean = false,
    val privacyMode: PrivacyMode = PrivacyMode.MASKED,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val showSystemApps: Boolean = true,
    val onboardingCompleted: Boolean = false,
    val lastSyncAt: Long = 0L,
    val lastSyncError: String? = null,
)
