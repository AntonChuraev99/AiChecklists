package com.antonchuraev.homesearchchecklist.feature.analyze.di

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.default_checklist_name
import aichecklists.core.designsystem.generated.resources.default_fill_name
import com.antonchuraev.homesearchchecklist.core.common.api.AiModelExperimentTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyzeEntryArgs
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.analyze.data.analyzer.FirebaseAiAnalyzerAdapter
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.AnalysisJobManager
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.AnalysisJobProcessorImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.AnalysisReadySignalImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.DataStoreAnalysisJobStore
import com.antonchuraev.homesearchchecklist.feature.analyze.data.job.DataStoreAnalysisNotificationAskLatch
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobHandoff
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisNotificationAskLatch
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiServiceImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.repository.AiActionCostProviderImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.repository.AnalyzeRepositoryImpl
import com.antonchuraev.homesearchchecklist.feature.analyze.data.util.FileReader
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.analyzer.AiAnalyzer
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobProcessor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobScheduler
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadySignal
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisScreenAttachments
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AiActionCostProvider
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AnalyzeRepository
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeViewModel
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewViewModel
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.getString
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val analyzeFeatureModule = module {
    // Platform ports of the background analysis (launcher, payload store, downscaler, notifier,
    // foreground monitor) — WorkManager on Android, app-scope coroutine on web / iOS.
    includes(analyzePlatformModule)

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

    // ── Background analysis (FEATURE_SPEC analyze-reliability-background-2026-11-20) ──────────
    // All app-scoped singles: the analysis outlives the screen. The Android worker resolves
    // AnalysisJobProcessor + AnalysisJobStore from here; MainActivity resolves AnalysisReadySignal.
    single<AiActionCostProvider> {
        AiActionCostProviderImpl(firebaseAiService = get(), userDataRepository = get(), logger = get())
    }
    single<AnalysisJobStore> { DataStoreAnalysisJobStore(datastore = get<AppDatastore>(), logger = get()) }
    single { AnalysisScreenAttachments() }
    single { AnalysisJobHandoff() }
    single<AnalysisNotificationAskLatch> { DataStoreAnalysisNotificationAskLatch(datastore = get<AppDatastore>()) }
    single<AnalysisJobProcessor> {
        AnalysisJobProcessorImpl(
            store = get(),
            payloadStore = get(),
            downscaler = get(),
            analyzeRepository = get(),
            findChecklist = get<ChecklistRepository>()::getChecklistById,
            analyticsTracker = get(),
            foregroundMonitor = get(),
            notifier = get(),
            logger = get(),
            defaultChecklistName = { getString(Res.string.default_checklist_name) },
            aiModelExperimentTracker = getOrNull<AiModelExperimentTracker>(),
        )
    }
    single<AnalysisJobScheduler> {
        AnalysisJobManager(
            store = get(),
            payloadStore = get(),
            launcher = get(),
            analyticsTracker = get(),
            logger = get(),
            scope = get<CoroutineScope>(),
            readFileBytes = FileReader::readBytes,
            readFileText = FileReader::readText,
        )
    }
    single<AnalysisReadySignal> {
        AnalysisReadySignalImpl(
            scheduler = get(),
            foregroundMonitor = get(),
            attachments = get(),
            activationCoordinator = get(),
            analyticsTracker = get(),
            notifier = get(),
            logger = get(),
            scope = get<CoroutineScope>(),
            defaultFillName = { getString(Res.string.default_fill_name) },
            handoff = get(),
        )
    }

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
            aiModelExperimentTracker = get(),
            actionCostProvider = get(),
            jobScheduler = get(),
            attachments = get(),
            foregroundMonitor = get(),
            readyNotifier = get(),
            jobHandoff = get(),
            notificationAskLatch = get(),
        )
    }

    // AnalyzeResultPreviewViewModel
    viewModel {
        AnalyzeResultPreviewViewModel(
            appNavigator = get(),
            checklistRepository = get(),
            analyticsTracker = get(),
            activationCoordinator = get()
        )
    }
}
