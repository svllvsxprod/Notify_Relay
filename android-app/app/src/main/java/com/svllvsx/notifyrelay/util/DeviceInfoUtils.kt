package com.svllvsx.notifyrelay.util

import android.content.Context
import android.os.Build
import com.svllvsx.notifyrelay.BuildConfig

object DeviceInfoUtils {
    fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL).joinToString(" ").trim()
    fun appVersion(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: BuildConfig.VERSION_NAME
    }.getOrDefault(BuildConfig.VERSION_NAME)
}
