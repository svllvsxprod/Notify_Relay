package com.svllvsx.notifyrelay.workers

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.svllvsx.notifyrelay.NotifyRelayApp
import com.svllvsx.notifyrelay.core.AppError
import com.svllvsx.notifyrelay.core.AppResult
import java.util.concurrent.TimeUnit

class UploadEventsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return when (val result = (applicationContext as NotifyRelayApp).container.uploadPendingEventsUseCase()) {
            is AppResult.Success -> Result.success()
            is AppResult.Error -> when (result.type) {
                AppError.Unauthorized, AppError.Forbidden -> Result.failure()
                else -> Result.retry()
            }
        }
    }
}

class WorkerScheduler(private val context: Context) {
    fun enqueueUpload() {
        val request = OneTimeWorkRequestBuilder<UploadEventsWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("upload_events", ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
