package com.antonchuraev.homesearchchecklist.core.common.api

sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: Exception) : AppResult<Nothing>()
    data object Loading : AppResult<Nothing>()
}
