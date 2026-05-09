package com.svllvsx.notifyrelay.core

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Error(val type: AppError) : AppResult<Nothing>
}

sealed interface AppError {
    data object Network : AppError
    data object Unauthorized : AppError
    data object Forbidden : AppError
    data object ServerUnavailable : AppError
    data object InvalidPairingCode : AppError
    data object ExpiredPairingCode : AppError
    data class Unknown(val message: String?) : AppError
}
