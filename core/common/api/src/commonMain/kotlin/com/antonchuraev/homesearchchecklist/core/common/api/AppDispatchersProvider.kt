package com.antonchuraev.homesearchchecklist.core.common.api

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

interface AppDispatchersProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher

    companion object Companion {
        val DEFAULT = object : AppDispatchersProvider {
            // Dispatchers.IO is JVM/Android-only; Dispatchers.Default is used on all platforms.
            // On Android, this is functionally equivalent for most non-blocking async operations.
            override val io = Dispatchers.Default
            override val main = Dispatchers.Main
            override val default = Dispatchers.Default
        }
    }
}
