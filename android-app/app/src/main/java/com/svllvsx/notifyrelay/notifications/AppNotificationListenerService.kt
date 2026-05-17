package com.svllvsx.notifyrelay.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.svllvsx.notifyrelay.NotifyRelayApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val container = (application as NotifyRelayApp).container
        scope.launch {
            container.saveNotificationEventUseCase(sbn)
            container.flushPendingEventsAsync()
        }
    }
}
