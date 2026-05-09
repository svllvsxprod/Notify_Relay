package com.svllvsx.notifyrelay.domain.usecase

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification
import com.svllvsx.notifyrelay.core.AppResult
import com.svllvsx.notifyrelay.data.api.ApiClientFactory
import com.svllvsx.notifyrelay.data.db.EventEntity
import com.svllvsx.notifyrelay.data.db.EventStatus
import com.svllvsx.notifyrelay.data.repositories.AppsRepository
import com.svllvsx.notifyrelay.data.repositories.EventsRepository
import com.svllvsx.notifyrelay.data.repositories.SettingsRepository
import com.svllvsx.notifyrelay.data.repositories.uploadEvents
import com.svllvsx.notifyrelay.domain.model.PrivacyMode
import com.svllvsx.notifyrelay.util.HashUtils
import com.svllvsx.notifyrelay.workers.WorkerScheduler
import kotlinx.coroutines.flow.first
import java.util.UUID

class ApplyPrivacyModeUseCase(private val settingsRepository: SettingsRepository) {
    suspend fun applyNotification(appLabel: String?, title: String?, text: String?, bigText: String?, subText: String?): PrivacyContent {
        return when (settingsRepository.settings.first().privacyMode) {
            PrivacyMode.FULL -> PrivacyContent(title, text, bigText, subText, null)
            PrivacyMode.MASKED -> PrivacyContent(title, text?.masked(), bigText?.masked(), subText, null)
            PrivacyMode.MINIMAL -> PrivacyContent("Новое уведомление", "Новое уведомление от ${appLabel ?: "приложения"}", null, null, null)
        }
    }

    suspend fun applySms(sender: String?, body: String?): PrivacyContent {
        return when (settingsRepository.settings.first().privacyMode) {
            PrivacyMode.FULL -> PrivacyContent(null, body, null, null, sender)
            PrivacyMode.MASKED -> PrivacyContent(null, body?.masked(), null, null, sender?.masked())
            PrivacyMode.MINIMAL -> PrivacyContent(null, "Новое SMS от ${sender ?: "отправителя"}", null, null, sender)
        }
    }

    private fun String.masked(): String = this
        .replace(Regex("\\b\\d{4,8}\\b")) { "*".repeat(it.value.length) }
        .replace(Regex("\\b(?:\\d[ -]?){13,19}\\b"), "**** **** **** ****")
        .replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "***@***")
        .replace(Regex("\\+?\\d[\\d ()-]{8,}\\d"), "+***********")
}

data class PrivacyContent(val title: String?, val text: String?, val bigText: String?, val subText: String?, val sender: String?)

class SaveNotificationEventUseCase(
    private val context: Context,
    private val eventsRepository: EventsRepository,
    private val appsRepository: AppsRepository,
    private val privacyMode: ApplyPrivacyModeUseCase,
    private val workerScheduler: WorkerScheduler,
) {
    private val recentFingerprints = ArrayDeque<RecentNotificationFingerprint>()

    suspend operator fun invoke(sbn: StatusBarNotification) {
        if (!appsRepository.isEnabled(sbn.packageName)) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && sbn.notification.channelId == "miscellaneous" && sbn.isOngoing) return
        val extras = sbn.notification.extras
        val appLabel = runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(sbn.packageName, 0)).toString() }.getOrDefault(sbn.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val fingerprint = notificationFingerprint(sbn.packageName, title, text, bigText, subText)
        if (isDuplicate(fingerprint, System.currentTimeMillis())) return
        val content = privacyMode.applyNotification(appLabel, title, text, bigText, subText)
        val key = HashUtils.sha256("notification$fingerprint")
        eventsRepository.insert(
            EventEntity(
                id = UUID.randomUUID().toString(),
                eventId = "evt_${UUID.randomUUID()}",
                idempotencyKey = key,
                type = "notification",
                packageName = sbn.packageName,
                appLabel = appLabel,
                sender = null,
                title = content.title,
                text = content.text,
                bigText = content.bigText,
                subText = content.subText,
                timestamp = sbn.postTime,
                createdAt = System.currentTimeMillis(),
                status = EventStatus.PENDING,
                attempts = 0,
                lastError = null,
            ),
        )
        workerScheduler.enqueueUpload()
    }

    private fun isDuplicate(fingerprint: String, now: Long): Boolean {
        val windowMs = 2_000L
        while (recentFingerprints.isNotEmpty() && now - recentFingerprints.first().timestamp > windowMs) {
            recentFingerprints.removeFirst()
        }
        if (recentFingerprints.any { it.fingerprint == fingerprint }) return true
        recentFingerprints.addLast(RecentNotificationFingerprint(fingerprint, now))
        return false
    }

    private fun notificationFingerprint(packageName: String, title: String?, text: String?, bigText: String?, subText: String?): String {
        val body = bigText?.takeIf { it.isNotBlank() } ?: text.orEmpty()
        return HashUtils.sha256(
            listOf(packageName, title.orEmpty(), body, subText.orEmpty())
                .joinToString("|") { it.trim().replace(Regex("\\s+"), " ") },
        )
    }
}

private data class RecentNotificationFingerprint(val fingerprint: String, val timestamp: Long)

class SaveSmsEventUseCase(
    private val eventsRepository: EventsRepository,
    private val settingsRepository: SettingsRepository,
    private val privacyMode: ApplyPrivacyModeUseCase,
    private val workerScheduler: WorkerScheduler,
) {
    suspend operator fun invoke(sender: String?, body: String?, timestamp: Long) {
        if (!settingsRepository.settings.first().smsForwardingEnabled) return
        val content = privacyMode.applySms(sender, body)
        val key = HashUtils.sha256("sms$sender$timestamp$body")
        eventsRepository.insert(
            EventEntity(
                id = UUID.randomUUID().toString(),
                eventId = "evt_${UUID.randomUUID()}",
                idempotencyKey = key,
                type = "sms",
                packageName = null,
                appLabel = null,
                sender = content.sender,
                title = null,
                text = content.text,
                bigText = null,
                subText = null,
                timestamp = timestamp,
                createdAt = System.currentTimeMillis(),
                status = EventStatus.PENDING,
                attempts = 0,
                lastError = null,
            ),
        )
        workerScheduler.enqueueUpload()
    }
}

class UploadPendingEventsUseCase(
    private val eventsRepository: EventsRepository,
    private val apiFactory: ApiClientFactory,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): AppResult<Unit> {
        val events = eventsRepository.getPending(25)
        if (events.isEmpty()) return AppResult.Success(Unit)
        eventsRepository.markSending(events)
        val result = uploadEvents(apiFactory, eventsRepository, events)
        if (result is AppResult.Error) eventsRepository.markPendingWithError(events.map { it.eventId }, result.type.toString())
        settingsRepository.saveLastSync(if (result is AppResult.Error) result.type.toString() else null)
        return result
    }
}
