package com.svllvsx.notifyrelay.data.api

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.svllvsx.notifyrelay.BuildConfig
import com.svllvsx.notifyrelay.data.repositories.SettingsRepository
import com.svllvsx.notifyrelay.data.security.SecureTokenStorage
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

class ApiClientFactory(
    private val settingsRepository: SettingsRepository,
    private val tokenStorage: SecureTokenStorage,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun create(): BackendApi {
        val settings = settingsRepository.settings.first()
        val baseUrl = settings.serverUrl.trimEnd('/') + "/"
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val token = tokenStorage.getDeviceToken()
                if (settings.deviceId.isNotBlank() && !token.isNullOrBlank()) {
                    requestBuilder.header("Authorization", "Bearer $token")
                    requestBuilder.header("X-Device-Id", settings.deviceId)
                }
                chain.proceed(requestBuilder.build())
            }
            .apply {
                if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BackendApi::class.java)
    }
}
