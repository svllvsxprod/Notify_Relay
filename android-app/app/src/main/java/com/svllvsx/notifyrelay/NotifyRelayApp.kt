package com.svllvsx.notifyrelay

import android.app.Application
import com.svllvsx.notifyrelay.core.AppContainer

class NotifyRelayApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.cleanupOldEvents()
        container.scheduleBackgroundWork()
    }
}
