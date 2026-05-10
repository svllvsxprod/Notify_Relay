package com.svllvsx.notifyrelay.core

import android.content.Context
import androidx.room.Room
import com.svllvsx.notifyrelay.data.api.ApiClientFactory
import com.svllvsx.notifyrelay.data.db.AppDatabase
import com.svllvsx.notifyrelay.data.repositories.AppsRepository
import com.svllvsx.notifyrelay.data.repositories.DeviceRepository
import com.svllvsx.notifyrelay.data.repositories.EventsRepository
import com.svllvsx.notifyrelay.data.repositories.PermissionsRepository
import com.svllvsx.notifyrelay.data.repositories.SettingsRepository
import com.svllvsx.notifyrelay.data.repositories.UpdatesRepository
import com.svllvsx.notifyrelay.data.security.SecureTokenStorage
import com.svllvsx.notifyrelay.data.settings.AppSettingsDataStore
import com.svllvsx.notifyrelay.domain.usecase.ApplyPrivacyModeUseCase
import com.svllvsx.notifyrelay.domain.usecase.SaveNotificationEventUseCase
import com.svllvsx.notifyrelay.domain.usecase.SaveSmsEventUseCase
import com.svllvsx.notifyrelay.domain.usecase.UploadPendingEventsUseCase
import com.svllvsx.notifyrelay.workers.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val settingsStore = AppSettingsDataStore(context)
    val tokenStorage = SecureTokenStorage(context)
    val database: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "notify_relay.db").build()
    val settingsRepository = SettingsRepository(settingsStore)
    val apiClientFactory = ApiClientFactory(settingsRepository, tokenStorage)
    val deviceRepository = DeviceRepository(apiClientFactory, settingsRepository, tokenStorage, context)
    val eventsRepository = EventsRepository(database.eventDao())
    val appsRepository = AppsRepository(context, database.selectedAppDao())
    val permissionsRepository = PermissionsRepository(context)
    val updatesRepository = UpdatesRepository(context)
    val workerScheduler = WorkerScheduler(context)
    val privacyModeUseCase = ApplyPrivacyModeUseCase(settingsRepository)
    val saveNotificationEventUseCase = SaveNotificationEventUseCase(context, eventsRepository, appsRepository, privacyModeUseCase, workerScheduler)
    val saveSmsEventUseCase = SaveSmsEventUseCase(eventsRepository, settingsRepository, privacyModeUseCase, workerScheduler)
    val uploadPendingEventsUseCase = UploadPendingEventsUseCase(eventsRepository, apiClientFactory, settingsRepository)

    fun cleanupOldEvents() {
        scope.launch { eventsRepository.deleteSentOlderThan(System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L) }
    }
}
