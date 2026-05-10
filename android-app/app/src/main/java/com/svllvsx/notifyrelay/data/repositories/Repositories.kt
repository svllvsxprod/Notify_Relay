package com.svllvsx.notifyrelay.data.repositories

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.os.Build
import androidx.core.content.FileProvider
import com.svllvsx.notifyrelay.notifications.AppNotificationListenerService
import com.svllvsx.notifyrelay.BuildConfig
import com.svllvsx.notifyrelay.core.AppError
import com.svllvsx.notifyrelay.core.AppResult
import com.svllvsx.notifyrelay.data.api.ApiClientFactory
import com.svllvsx.notifyrelay.data.api.dto.EventContentDto
import com.svllvsx.notifyrelay.data.api.dto.EventDto
import com.svllvsx.notifyrelay.data.api.dto.EventSourceDto
import com.svllvsx.notifyrelay.data.api.dto.EventsBatchRequest
import com.svllvsx.notifyrelay.data.api.dto.RegisterDeviceRequest
import com.svllvsx.notifyrelay.data.api.dto.TestMessageRequest
import com.svllvsx.notifyrelay.data.db.EventDao
import com.svllvsx.notifyrelay.data.db.EventEntity
import com.svllvsx.notifyrelay.data.db.EventStatus
import com.svllvsx.notifyrelay.data.db.SelectedAppDao
import com.svllvsx.notifyrelay.data.db.SelectedAppEntity
import com.svllvsx.notifyrelay.data.security.SecureTokenStorage
import com.svllvsx.notifyrelay.data.settings.AppSettingsDataStore
import com.svllvsx.notifyrelay.domain.model.InstalledApp
import com.svllvsx.notifyrelay.domain.model.AppLanguage
import com.svllvsx.notifyrelay.domain.model.PrivacyMode
import com.svllvsx.notifyrelay.util.DeviceInfoUtils
import com.svllvsx.notifyrelay.util.PermissionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import retrofit2.Response
import java.text.Collator
import java.util.Locale
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class SettingsRepository(private val store: AppSettingsDataStore) {
    val settings = store.settings
    suspend fun saveServerUrl(url: String) = store.updateServerUrl(url)
    suspend fun saveDeviceId(deviceId: String) = store.updateDeviceId(deviceId)
    suspend fun setNotificationForwarding(enabled: Boolean) = store.updateNotificationForwarding(enabled)
    suspend fun setSmsForwarding(enabled: Boolean) = store.updateSmsForwarding(enabled)
    suspend fun setPrivacyMode(mode: PrivacyMode) = store.updatePrivacyMode(mode)
    suspend fun setLanguage(language: AppLanguage) = store.updateLanguage(language)
    suspend fun setShowSystemApps(show: Boolean) = store.updateShowSystemApps(show)
    suspend fun completeOnboarding() = store.updateOnboardingCompleted(true)
    suspend fun saveLastSync(error: String? = null) = store.updateLastSync(System.currentTimeMillis(), error)
    suspend fun resetDevice() = store.resetDevice()
}

class DeviceRepository(
    private val apiFactory: ApiClientFactory,
    private val settingsRepository: SettingsRepository,
    private val tokenStorage: SecureTokenStorage,
    private val context: Context,
) {
    suspend fun checkHealth(serverUrl: String): AppResult<Unit> = safeApi {
        settingsRepository.saveServerUrl(validateServerUrl(serverUrl))
        apiFactory.create().health()
    }.mapUnit()

    suspend fun registerDevice(pairingCode: String): AppResult<Unit> = safeApi {
        apiFactory.create().registerDevice(
            RegisterDeviceRequest(
                pairingCode = pairingCode,
                deviceName = DeviceInfoUtils.deviceName(),
                appVersion = DeviceInfoUtils.appVersion(context),
            ),
        )
    }.also { result ->
        if (result is AppResult.Success) {
            settingsRepository.saveDeviceId(result.data.deviceId)
            tokenStorage.saveDeviceToken(result.data.deviceToken)
            settingsRepository.completeOnboarding()
        }
    }.mapUnit()

    suspend fun sendTestMessage(): AppResult<Unit> = safeApi { apiFactory.create().sendTestMessage(TestMessageRequest("Test message from Android app")) }.mapUnit()
    suspend fun getDeviceStatus(): AppResult<Unit> = safeApi { apiFactory.create().getDeviceStatus() }.mapUnit()
    suspend fun resetDevice() {
        runCatching { safeApi { apiFactory.create().revokeDevice() } }
        tokenStorage.clearDeviceToken()
        settingsRepository.resetDevice()
    }

    private fun validateServerUrl(raw: String): String {
        val uri = Uri.parse(raw.trim().trimEnd('/'))
        require(uri.scheme == "https" || (BuildConfig.DEBUG && uri.scheme == "http")) { "Release builds require HTTPS" }
        require(!uri.host.isNullOrBlank()) { "Invalid backend URL" }
        return uri.toString()
    }
}

class EventsRepository(private val dao: EventDao) {
    fun pendingCount(): Flow<Int> = dao.countByStatus(EventStatus.PENDING)
    fun failedCount(): Flow<Int> = dao.countByStatus(EventStatus.FAILED)
    fun sentCount(days: Int = 3): Flow<Int> = dao.countSentSince(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L)
    fun recentEvents(days: Int = 3, limit: Int = 50): Flow<List<EventEntity>> = dao.observeRecent(System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L, limit)
    suspend fun insert(event: EventEntity) = dao.insertIgnore(event)
    suspend fun getPending(limit: Int = 25) = dao.getPending(limit)
    suspend fun markSending(events: List<EventEntity>) = dao.markSending(events.map { it.id })
    suspend fun markSent(eventIds: List<String>) = dao.markSentByEventIds(eventIds)
    suspend fun markRejected(eventIds: List<String>, reason: String) = dao.markFailed(eventIds, reason)
    suspend fun markPendingWithError(eventIds: List<String>, error: String) = dao.markPendingWithError(eventIds, error)
    suspend fun deleteSentOlderThan(timestamp: Long) = dao.deleteSentOlderThan(timestamp)
}

class AppsRepository(private val context: Context, private val dao: SelectedAppDao) {
    private val blockedPackages = setOf(context.packageName)
    fun observeSelectedApps(): Flow<List<SelectedAppEntity>> = dao.observeSelectedApps()
    suspend fun setEnabled(app: InstalledApp, enabled: Boolean) = dao.upsert(SelectedAppEntity(app.packageName, app.label, enabled && app.packageName !in blockedPackages, System.currentTimeMillis()))
    suspend fun isEnabled(packageName: String): Boolean = packageName !in blockedPackages && (dao.isEnabled(packageName) ?: false)
    fun installedApps(): List<InstalledApp> {
        val packageManager = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
        }

        val collator = Collator.getInstance(Locale.getDefault())

        return packages
            .mapNotNull { it.applicationInfo }
            .filter { it.packageName !in blockedPackages }
            .map { appInfo ->
                InstalledApp(
                    packageName = appInfo.packageName,
                    label = appInfo.loadLabel(packageManager).toString().ifBlank { appInfo.packageName.substringAfterLast('.') },
                    isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy<InstalledApp> { it.isSystem }.thenComparator { left, right -> collator.compare(left.label, right.label) })
    }
}

class PermissionsRepository(private val context: Context) {
    fun hasNotificationAccess(): Boolean = PermissionUtils.hasNotificationAccess(context)
    fun hasSmsPermission(): Boolean = PermissionUtils.hasSmsPermission(context)
    fun hasPostNotificationsPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    fun isIgnoringBatteryOptimizations(): Boolean = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)
    fun notificationSettingsIntent(): Intent {
        val componentName = ComponentName(context, AppNotificationListenerService::class.java).flattenToString()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS")
                .putExtra("android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME", componentName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun appDetailsSettingsIntent(): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun batteryOptimizationIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

data class UpdateInfo(val latestVersion: String, val releaseUrl: String, val apkUrl: String?, val apkName: String?, val hasUpdate: Boolean)

class UpdatesRepository(private val context: Context) {
    suspend fun checkLatestRelease(): AppResult<UpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val connection = (URL("https://api.github.com/repos/svllvsxprod/Notify_Relay/releases/latest").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NotifyRelay/${BuildConfig.VERSION_NAME}")
            }
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val tag = json.getString("tag_name")
                val url = json.getString("html_url")
                val assets = json.getJSONArray("assets")
                var apkUrl: String? = null
                var apkName: String? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkName = name
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                AppResult.Success(UpdateInfo(tag, url, apkUrl, apkName, isNewerVersion(tag.removePrefix("v"), BuildConfig.VERSION_NAME)))
            }
        } catch (_: Exception) {
            AppResult.Error(AppError.Network)
        }
    }

    suspend fun downloadApk(info: UpdateInfo, onProgress: (Float) -> Unit): AppResult<Uri> = withContext(Dispatchers.IO) {
        try {
            val apkUrl = requireNotNull(info.apkUrl)
            val apkName = info.apkName ?: "Notify-Relay-${info.latestVersion}.apk"
            val connection = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "NotifyRelay/${BuildConfig.VERSION_NAME}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0L } ?: -1L
            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(directory, apkName).apply { if (exists()) delete() }
            var downloaded = 0L
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0L) onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            onProgress(1f)
            AppResult.Success(FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file))
        } catch (_: Exception) {
            AppResult.Error(AppError.Network)
        }
    }

    fun installIntent(uri: Uri): Intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

private fun isNewerVersion(remote: String, current: String): Boolean {
    val remoteParts = remote.split('.', '-').mapNotNull { it.toIntOrNull() }
    val currentParts = current.split('.', '-').mapNotNull { it.toIntOrNull() }
    repeat(maxOf(remoteParts.size, currentParts.size)) { index ->
        val remotePart = remoteParts.getOrNull(index) ?: 0
        val currentPart = currentParts.getOrNull(index) ?: 0
        if (remotePart != currentPart) return remotePart > currentPart
    }
    return false
}

suspend fun uploadEvents(apiFactory: ApiClientFactory, eventsRepository: EventsRepository, events: List<EventEntity>): AppResult<Unit> {
    val response = safeApi {
        apiFactory.create().sendEvents(EventsBatchRequest(events.map { it.toDto() }))
    }
    return when (response) {
        is AppResult.Error -> response
        is AppResult.Success -> {
            val body = response.data
            eventsRepository.markSent(body.accepted + body.duplicates)
            body.rejected.groupBy { it.reason }.forEach { (reason, rejected) -> eventsRepository.markRejected(rejected.map { it.eventId }, reason) }
            AppResult.Success(Unit)
        }
    }
}

private fun EventEntity.toDto() = EventDto(
    eventId = eventId,
    idempotencyKey = idempotencyKey,
    type = type,
    source = EventSourceDto(packageName, appLabel, sender),
    content = EventContentDto(title, text, bigText, subText),
    timestamp = timestamp,
)

private suspend fun <T> safeApi(call: suspend () -> Response<T>): AppResult<T> = try {
    val response = call()
    val body = response.body()
    when {
        response.isSuccessful && body != null -> AppResult.Success(body)
        response.code() == 400 -> AppResult.Error(AppError.InvalidPairingCode)
        response.code() == 401 -> AppResult.Error(AppError.Unauthorized)
        response.code() == 403 -> AppResult.Error(AppError.Forbidden)
        response.code() == 410 -> AppResult.Error(AppError.ExpiredPairingCode)
        response.code() >= 500 -> AppResult.Error(AppError.ServerUnavailable)
        else -> AppResult.Error(AppError.Unknown(response.message()))
    }
} catch (error: IllegalArgumentException) {
    AppResult.Error(AppError.Unknown(error.message))
} catch (_: Exception) {
    AppResult.Error(AppError.Network)
}

private fun <T> AppResult<T>.mapUnit(): AppResult<Unit> = when (this) {
    is AppResult.Success -> AppResult.Success(Unit)
    is AppResult.Error -> this
}
