package com.antonchuraev.homesearchchecklist.core.common.api

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

interface AppDispatchersProvider {
    val io: CoroutineDispatcher
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher

    companion object Companion {
        val DEFAULT = object : AppDispatchersProvider {
            override val io = Dispatchers.IO
            override val main = Dispatchers.Main
            override val default = Dispatchers.Default
        }
    }
}
