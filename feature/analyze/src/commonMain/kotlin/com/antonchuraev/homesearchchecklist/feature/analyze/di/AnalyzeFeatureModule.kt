package com.antonchuraev.homesearchchecklist.feature.analyze.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer.StubAiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.data.repository.AnalyzeRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val analyzeFeatureModule = module {
    // AI Analyzer - use stub for now, replace with real implementation later
    singleOf(::StubAiAnalyzer) bind AiAnalyzer::class

    // Repository
    singleOf(::AnalyzeRepositoryImpl) bind AnalyzeRepository::class

    // ViewModel
    viewModelOf(::AnalyzeViewModel)
}
