package com.antonchuraev.homesearchchecklist.feature.analyze.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer.GeminiAiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer.StubAiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.ApiKeyProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.DefaultApiKeyProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.repository.AnalyzeRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val analyzeFeatureModule = module {
    // ApiKeyProvider - uses GeminiConfig from app module
    single<ApiKeyProvider> { DefaultApiKeyProvider(get()) }

    // Firebase AI Service - all AI operations go through Firebase Functions
    single<FirebaseAiService> { FirebaseAiServiceImpl() }

    // AI Analyzer - use Gemini if API key is configured, otherwise fall back to Stub
    // Note: This is for backward compatibility. New features should use FirebaseAiService
    single<AiAnalyzer> {
        val apiKeyProvider: ApiKeyProvider = get()
        val apiKey = apiKeyProvider.getGeminiApiKey()
        if (apiKey.isNotBlank()) {
            GeminiAiAnalyzer(apiKeyProvider)
        } else {
            StubAiAnalyzer()
        }
    }

    // Repository
    singleOf(::AnalyzeRepositoryImpl) bind AnalyzeRepository::class

    // ViewModel
    viewModelOf(::AnalyzeViewModel)
}
