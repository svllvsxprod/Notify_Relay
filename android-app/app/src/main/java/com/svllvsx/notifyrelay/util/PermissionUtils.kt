package com.svllvsx.notifyrelay.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionUtils {
    fun hasSmsPermission(context: Context): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    fun hasNotificationAccess(context: Context): Boolean = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
