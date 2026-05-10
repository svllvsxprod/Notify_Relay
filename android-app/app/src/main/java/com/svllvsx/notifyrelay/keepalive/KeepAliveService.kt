package com.svllvsx.notifyrelay.keepalive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.svllvsx.notifyrelay.R

class KeepAliveService : Service() {
    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Notify Relay")
        .setContentText("Relay is active and waiting for notifications")
        .setOngoing(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Notify Relay status", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps Notify Relay active for notification forwarding"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "notify_relay_keep_alive"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.svllvsx.notifyrelay.keepalive.STOP"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, KeepAliveService::class.java)) }
        }

        fun stop(context: Context) {
            runCatching { context.startService(Intent(context, KeepAliveService::class.java).setAction(ACTION_STOP)) }
        }

        fun sync(context: Context, enabled: Boolean) {
            if (enabled) start(context) else stop(context)
        }
    }
}
