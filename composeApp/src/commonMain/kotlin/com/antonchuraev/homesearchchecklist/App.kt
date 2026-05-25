package com.antonchuraev.homesearchchecklist

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.feature.create.domain.usecase.CreateWeeklyChecklistUseCase
import com.antonchuraev.homesearchchecklist.csat.CsatBottomSheet
import com.antonchuraev.homesearchchecklist.csat.CsatIntent
import com.antonchuraev.homesearchchecklist.csat.CsatViewModel
import com.antonchuraev.homesearchchecklist.csat.InAppReviewLauncher
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppLanguage
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.LanguageRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppLocaleEnvironment
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import com.antonchuraev.homesearchchecklist.desingsystem.theme.customAppLocale
import com.antonchuraev.homesearchchecklist.settings.presentation.SettingsScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.components.WidgetInstructionOverlay
import com.antonchuraev.homesearchchecklist.navigation.AdaptiveNavigationShell
import com.antonchuraev.homesearchchecklist.navigation.DrawerDestination
import com.antonchuraev.homesearchchecklist.navigation.EmptyDetailPlaceholder
import com.antonchuraev.homesearchchecklist.gestures.ApplyEdgeSwipeExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.feedback_thanks_message
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.preview.TemplatePreviewScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.OnboardingsScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.ScreenCatalogScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.StoreScreenshotScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreen
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.ChatRoute
import com.antonchuraev.homesearchchecklist.feature.home.presentation.calendar.CalendarRoute
import com.antonchuraev.homesearchchecklist.feature.home.presentation.today.TodayRoute
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallRoute
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.SubscriptionStatusScreen
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.ShareScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.UpdateFeedScreen
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import org.jetbrains.compose.ui.tooling.preview.Preview
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
@Preview
fun App() {
    // Koin is started globally in GistiApplication.onCreate().
    // We hold a remembered reference to the already-started Koin so that
    // NavDisplay recompositions never lose the scope — `remember` keeps the same instance
    // across recompositions without ever calling stopKoin. This pattern fixes Crashlytics
    // issue edf060726547384d4e61afa21b1529ea (Koin #2240, marked wontfix).
    val koin = remember { GlobalContext.get() }
    KoinContext(koin) {
        val logger: AppLogger = remember { koin.get<AppLogger>() }
        LaunchedEffect(Unit) {
            logger.debug("App", "App composable start, Koin ref stable")
        }

        // Nav3: AppNavigator owns the NavBackStack (SnapshotStateList<NavKey>).
        // No NavController, no async Channel — mutations are synchronous.
        // Splash is seeded inside AppNavigatorImpl.init {} (DI-time, BEFORE first
        // composition). A LaunchedEffect-based seed would run after composition
        // and crash NavDisplay with "backstack cannot be empty".
        val navigator: AppNavigator = remember { koin.get<AppNavigator>() }

        val themeRepository: ThemeRepository = remember { koin.get<ThemeRepository>() }
        val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.Light)
        val dynamicColor by themeRepository.dynamicColor.collectAsStateWithLifecycle(initialValue = false)
        val systemDark = isSystemInDarkTheme()
        val darkTheme = when (themeMode) {
            AppThemeMode.Light -> false
            AppThemeMode.Dark -> true
            AppThemeMode.System -> systemDark
        }

        val languageRepository: LanguageRepository = remember { koin.get<LanguageRepository>() }
        val language by languageRepository.language.collectAsStateWithLifecycle(initialValue = AppLanguage.System)
        LaunchedEffect(language) { customAppLocale = language.tag }

        val csatViewModel: CsatViewModel = koinInject()
        val csatState by csatViewModel.screenState.collectAsState()

        var showWidgetInstruction by remember { mutableStateOf(false) }
        val createWeeklyChecklistUseCase: CreateWeeklyChecklistUseCase = koinInject()
        LaunchedEffect(Unit) {
            navigator.events.collect { event ->
                when (event) {
                    AppNavEvent.ShowWidgetInstruction -> {
                        if (!showWidgetInstruction) {
                            showWidgetInstruction = true
                        }
                    }
                    AppNavEvent.CreateWeeklyChecklistRequested -> {
                        when (val result = createWeeklyChecklistUseCase()) {
                            is CreateWeeklyChecklistUseCase.Result.Created ->
                                navigator.navigateToChecklistDetail(result.checklistId, clearBackStack = true)
                            CreateWeeklyChecklistUseCase.Result.RequiresUpgrade ->
                                navigator.navigateToPaywall(source = "weekly_mode_limit")
                        }
                    }
                }
            }
        }

        AppTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
            AppLocaleEnvironment {
            val snackbarHostState = remember { SnackbarHostState() }
            val feedbackThanksMessage = stringResource(Res.string.feedback_thanks_message)
            LaunchedEffect(csatState.showFeedbackThanks) {
                if (csatState.showFeedbackThanks) {
                    snackbarHostState.showSnackbar(feedbackThanksMessage)
                    csatViewModel.sendIntent(CsatIntent.FeedbackThanksShown)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {

            // ----------------------------------------------------------------
            // Navigation 3: NavDisplay renders the top NavKey in backStack.
            // Each entry<T> lambda receives the typed route as its receiver.
            // Top-level destinations (Main/Today/Calendar/AiChat/UpdateFeed/Settings)
            // are wrapped in AdaptiveNavigationShell — a single composable that
            // switches between ModalNavigationDrawer (Compact), NavigationRail (Medium)
            // and PermanentNavigationDrawer (Expanded) based on window size class.
            //
            // Stage 5 — Adaptive list-detail: ListDetailSceneStrategy shows list and
            // detail panes side-by-side on Medium/Expanded screens. On Compact it
            // falls back to single-pane push navigation (identical to prior behavior).
            // ----------------------------------------------------------------
            val sceneStrategy = rememberListDetailSceneStrategy<NavKey>()
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { navigator.onBack() },
                sceneStrategy = sceneStrategy,
                entryProvider = entryProvider {

                    entry<AppNavRoute.Splash> {
                        SplashScreen()
                    }

                    entry<AppNavRoute.Onboarding> {
                        OnboardingScreen()
                    }

                    entry<AppNavRoute.InteractiveOnboarding> {
                        InteractiveOnboardingScreen()
                    }

                    entry<AppNavRoute.Main>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        var isEditMode by rememberSaveable { mutableStateOf(false) }
                        AdaptiveNavigationShell(
                            selectedDestination = DrawerDestination.Main,
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> { /* already here */ }
                                    DrawerDestination.Today -> navigator.navigateToToday()
                                    DrawerDestination.Calendar -> navigator.navigateToCalendar()
                                    DrawerDestination.AiChat -> navigator.navigateToAiChat()
                                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                                    DrawerDestination.Settings -> navigator.navigateToSettings()
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                        ) { drawerState ->
                            // Reserve the left ~48dp as our own gesture area so
                            // Android's edge swipe-back doesn't steal swipes meant
                            // for opening the ModalNavigationDrawer.
                            // drawerState == null on Medium/Expanded — edge exclusion off
                            // (rail/permanent drawer handles navigation without swipe).
                            ApplyEdgeSwipeExclusion(
                                enabled = drawerState != null && drawerState.isClosed && !isEditMode
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                MainScreen(
                                    drawerState = drawerState,
                                    isEditMode = isEditMode,
                                    onEditModeChange = { isEditMode = it },
                                    onNavigateToAiChat = { navigator.navigateToAiChat() },
                                )
                            }
                        }
                    }

                    entry<AppNavRoute.CreateChecklistRoute.CreateChecklist> { route ->
                        CreateChecklistScreen(editChecklistId = route.editChecklistId)
                    }

                    entry<AppNavRoute.CreateChecklistRoute.Templates>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        TemplatesScreen()
                    }

                    entry<AppNavRoute.CreateChecklistRoute.TemplatePreview>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        TemplatePreviewScreen(templateId = route.templateId)
                    }

                    // Debug screens — only available in debug builds
                    if (AppBuildConfig.isDebug) {
                        entry<AppNavRoute.Debug> {
                            DebugScreen(
                                onShowCsat = { csatViewModel.sendIntent(CsatIntent.ForceShow) }
                            )
                        }

                        entry<AppNavRoute.StoreScreenshot> {
                            StoreScreenshotScreen()
                        }

                        entry<AppNavRoute.ScreenCatalog> {
                            ScreenCatalogScreen()
                        }

                        entry<AppNavRoute.Onboardings> {
                            OnboardingsScreen()
                        }
                    }

                    entry<AppNavRoute.Analyze> { route ->
                        AnalyzeScreen(checklistId = route.checklistId, fillDefault = route.fillDefault)
                    }

                    entry<AppNavRoute.AnalyzeResultPreview> {
                        AnalyzeResultPreviewScreen()
                    }

                    entry<AppNavRoute.ChecklistDetail>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        ChecklistDetailScreen(
                            checklistId = route.checklistId,
                            focusItemId = route.focusItemId,
                        )
                    }

                    entry<AppNavRoute.FillDetail>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        FillDetailScreen(fillId = route.fillId)
                    }

                    entry<AppNavRoute.FillsList>(
                        metadata = ListDetailSceneStrategy.detailPane()
                    ) { route ->
                        FillsListScreen(checklistId = route.checklistId)
                    }

                    entry<AppNavRoute.Paywall> {
                        PaywallRoute()
                    }

                    entry<AppNavRoute.SubscriptionStatus> { route ->
                        SubscriptionStatusScreen(showSuccessMessage = route.showSuccessMessage)
                    }

                    entry<AppNavRoute.ShareChecklist> { route ->
                        ShareScreen(checklistId = route.checklistId)
                    }

                    entry<AppNavRoute.Settings> {
                        AdaptiveNavigationShell(
                            selectedDestination = DrawerDestination.Settings,
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> {
                                        val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                        if (mainIdx >= 0) {
                                            while (navigator.backStack.size > mainIdx + 1) {
                                                navigator.backStack.removeAt(navigator.backStack.size - 1)
                                            }
                                        } else navigator.onBack()
                                    }
                                    DrawerDestination.Today -> navigator.navigateToToday()
                                    DrawerDestination.Calendar -> navigator.navigateToCalendar()
                                    DrawerDestination.AiChat -> navigator.navigateToAiChat()
                                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                                    DrawerDestination.Settings -> { /* already here */ }
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                        ) { drawerState ->
                            SettingsScreen(
                                onBackClick = { navigator.onBack() },
                                drawerState = drawerState,
                            )
                        }
                    }

                    entry<AppNavRoute.UpdateFeed> {
                        AdaptiveNavigationShell(
                            selectedDestination = DrawerDestination.UpdateFeed,
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> navigator.onBack()
                                    DrawerDestination.Today -> navigator.navigateToToday()
                                    DrawerDestination.Calendar -> navigator.navigateToCalendar()
                                    DrawerDestination.AiChat -> navigator.navigateToAiChat()
                                    DrawerDestination.UpdateFeed -> { /* already here */ }
                                    DrawerDestination.Settings -> navigator.navigateToSettings()
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                        ) { drawerState ->
                            UpdateFeedScreen(
                                onBackClick = { navigator.onBack() },
                                drawerState = drawerState,
                            )
                        }
                    }

                    entry<AppNavRoute.Today>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        AdaptiveNavigationShell(
                            selectedDestination = DrawerDestination.Today,
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> {
                                        val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                        if (mainIdx >= 0) {
                                            while (navigator.backStack.size > mainIdx + 1) {
                                                navigator.backStack.removeAt(navigator.backStack.size - 1)
                                            }
                                        } else navigator.onBack()
                                    }
                                    DrawerDestination.Today -> { /* already here */ }
                                    DrawerDestination.Calendar -> navigator.navigateToCalendar()
                                    DrawerDestination.AiChat -> navigator.navigateToAiChat()
                                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                                    DrawerDestination.Settings -> navigator.navigateToSettings()
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                        ) { drawerState ->
                            TodayRoute(
                                drawerState = drawerState,
                                onCreateChecklistClick = { navigator.navigateToTemplatesScreen() },
                            )
                        }
                    }

                    entry<AppNavRoute.Calendar>(
                        metadata = ListDetailSceneStrategy.listPane(
                            detailPlaceholder = { EmptyDetailPlaceholder() }
                        )
                    ) {
                        AdaptiveNavigationShell(
                            selectedDestination = DrawerDestination.Calendar,
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> {
                                        val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                        if (mainIdx >= 0) {
                                            while (navigator.backStack.size > mainIdx + 1) {
                                                navigator.backStack.removeAt(navigator.backStack.size - 1)
                                            }
                                        } else navigator.onBack()
                                    }
                                    DrawerDestination.Today -> navigator.navigateToToday()
                                    DrawerDestination.Calendar -> { /* already here */ }
                                    DrawerDestination.AiChat -> navigator.navigateToAiChat()
                                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                                    DrawerDestination.Settings -> navigator.navigateToSettings()
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                        ) { drawerState ->
                            CalendarRoute(
                                drawerState = drawerState,
                                onCreateChecklistClick = { navigator.navigateToTemplatesScreen() },
                            )
                        }
                    }

                    entry<AppNavRoute.AiChat> {
                        AdaptiveNavigationShell(
                            selectedDestination = DrawerDestination.AiChat,
                            onNavigate = { dest ->
                                when (dest) {
                                    DrawerDestination.Main -> {
                                        val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                        if (mainIdx >= 0) {
                                            while (navigator.backStack.size > mainIdx + 1) {
                                                navigator.backStack.removeAt(navigator.backStack.size - 1)
                                            }
                                        } else navigator.onBack()
                                    }
                                    DrawerDestination.Today -> navigator.navigateToToday()
                                    DrawerDestination.Calendar -> navigator.navigateToCalendar()
                                    DrawerDestination.AiChat -> { /* already here */ }
                                    DrawerDestination.UpdateFeed -> navigator.navigateToUpdateFeed()
                                    DrawerDestination.Settings -> navigator.navigateToSettings()
                                }
                            },
                            onRateApp = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                            onLeaveFeedback = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                            versionName = AppBuildConfig.versionName,
                        ) { drawerState ->
                            ChatRoute(
                                drawerState = drawerState,
                                snackbarHostState = snackbarHostState,
                                onBack = { navigator.onBack() },
                                onNavigateToChecklist = { checklistId ->
                                    navigator.navigateToChecklistDetail(checklistId)
                                },
                                onNavigateToPaywall = {
                                    navigator.navigateToPaywall(source = "chat_credits_chip")
                                },
                            )
                        }
                    }
                }, // end entryProvider
            ) // end NavDisplay

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
            } // Box

            // CSAT survey — global overlay
            if (csatState.showBottomSheet) {
                CsatBottomSheet(
                    state = csatState,
                    onIntent = csatViewModel::sendIntent,
                )
            }

            // Widget instruction overlay — triggered by gisti://widget_instruction deeplink
            if (showWidgetInstruction) {
                ModalBottomSheet(
                    onDismissRequest = { showWidgetInstruction = false },
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    WidgetInstructionOverlay(
                        onDone = { showWidgetInstruction = false }
                    )
                }
            }

            // In-App Review launcher — side-effect composable, no UI
            InAppReviewLauncher(
                shouldLaunch = csatState.shouldLaunchReview,
                onComplete = { csatViewModel.sendIntent(CsatIntent.ReviewComplete) },
            )
            } // AppLocaleEnvironment
        }
    }
}
