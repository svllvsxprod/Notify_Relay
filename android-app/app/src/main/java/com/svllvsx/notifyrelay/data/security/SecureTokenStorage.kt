package com.svllvsx.notifyrelay.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureTokenStorage(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "notify_relay_secure",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun getDeviceToken(): String? = prefs.getString("device_token", null)
    fun saveDeviceToken(token: String) = prefs.edit().putString("device_token", token).apply()
    fun clearDeviceToken() = prefs.edit().remove("device_token").apply()
}
