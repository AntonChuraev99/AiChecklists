package com.antonchuraev.homesearchchecklist.feature.analyze.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.repository.AnalyzeRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeViewModel
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val analyzeFeatureModule = module {
    // Firebase AI Service - all AI operations go through Firebase Functions
    single<FirebaseAiService> { FirebaseAiServiceImpl(logger = get()) }

    // Repository - uses Firebase AI Service for all AI operations
    single<AnalyzeRepository> {
        AnalyzeRepositoryImpl(
            firebaseAiService = get(),
            checklistRepository = get(),
            userDataRepository = get()
        )
    }

    // ViewModel with optional checklistId and fillDefault parameters
    viewModel { (checklistId: Long?, fillDefault: Boolean) ->
        AnalyzeViewModel(
            checklistId = checklistId,
            fillDefault = fillDefault,
            analyzeRepository = get(),
            checklistRepository = get(),
            appNavigator = get(),
            userDataRepository = get(),
            getSubscriptionStatusUseCase = get(),
            analyticsTracker = get()
        )
    }

    // AnalyzeResultPreviewViewModel
    viewModel {
        AnalyzeResultPreviewViewModel(
            appNavigator = get(),
            checklistRepository = get(),
            analyticsTracker = get()
        )
    }
}
