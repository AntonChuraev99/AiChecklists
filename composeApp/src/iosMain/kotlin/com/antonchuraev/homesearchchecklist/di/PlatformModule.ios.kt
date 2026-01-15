package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.GeminiConfig
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSBundle

actual fun platformModule(): Module = module {
    single {
        val apiKey = NSBundle.mainBundle.objectForInfoDictionaryKey("GEMINI_API_KEY") as? String ?: ""
        GeminiConfig(apiKey = apiKey)
    }
    single { DeviceIdProvider() }
}
