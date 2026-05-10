package com.svllvsx.notifyrelay.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.svllvsx.notifyrelay.data.settings.AppSettingsDataStore
import com.svllvsx.notifyrelay.keepalive.KeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StartupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in supportedActions) {
            WorkerScheduler(context.applicationContext).apply {
                schedulePeriodicUpload()
                enqueueUpload()
            }
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    val settings = AppSettingsDataStore(context.applicationContext).settings.first()
                    KeepAliveService.sync(context.applicationContext, settings.notificationForwardingEnabled)
                }
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val supportedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
