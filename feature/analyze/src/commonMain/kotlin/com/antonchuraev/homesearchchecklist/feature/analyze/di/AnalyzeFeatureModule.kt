package com.antonchuraev.homesearchchecklist.feature.analyze.di

import com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer.FirebaseAiAnalyzerAdapter
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.repository.AnalyzeRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeEntryArgs
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

    // AiAnalyzer interface — adapter that exposes AnalyzeRepository under the
    // domain abstraction. Consumed by ToolCallDispatcherImpl for AI Chat
    // attachment flows (CreateChecklistFromAttachment).
    single<AiAnalyzer> { FirebaseAiAnalyzerAdapter(analyzeRepository = get()) }

    // ViewModel with optional checklistId, fillDefault, initialText, autoAnalyze parameters, plus
    // the v2 entry-point pair (which material the door chose, and which door it was).
    //
    // Koin's destructured `parametersOf` is POSITIONAL and untyped at the call site — the two new
    // values go LAST so every existing 4-arg call keeps its meaning. Adding them anywhere else
    // would silently shift `initialText` into `autoAnalyze` at runtime, with no compiler warning.
    viewModel { (
        checklistId: Long?,
        fillDefault: Boolean,
        initialText: String?,
        autoAnalyze: Boolean,
        entryArgs: AnalyzeEntryArgs,
    ) ->
        AnalyzeViewModel(
            checklistId = checklistId,
            fillDefault = fillDefault,
            initialText = initialText,
            autoAnalyze = autoAnalyze,
            initialInputKind = entryArgs.inputKind,
            entrySource = entryArgs.source,
            analyzeRepository = get(),
            checklistRepository = get(),
            appNavigator = get(),
            userDataRepository = get(),
            getSubscriptionStatusUseCase = get(),
            analyticsTracker = get(),
            activationCoordinator = get(),
            remoteConfigProvider = get(),
            aiModelExperimentTracker = get(),
        )
    }

    // AnalyzeResultPreviewViewModel
    viewModel {
        AnalyzeResultPreviewViewModel(
            appNavigator = get(),
            checklistRepository = get(),
            analyticsTracker = get(),
            activationCoordinator = get(),
            remoteConfigProvider = get()
        )
    }
}
