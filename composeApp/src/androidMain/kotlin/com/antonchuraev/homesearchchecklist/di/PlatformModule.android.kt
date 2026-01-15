package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.aichecklists.BuildConfig
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.GeminiConfig
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single { AppContextHolder.context }
    single { GeminiConfig(apiKey = BuildConfig.GEMINI_API_KEY) }
}
