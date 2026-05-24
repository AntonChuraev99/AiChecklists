package com.antonchuraev.homesearchchecklist

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
import com.antonchuraev.homesearchchecklist.navigation.AppNavigationDrawerContent
import com.antonchuraev.homesearchchecklist.navigation.DrawerDestination
import com.antonchuraev.homesearchchecklist.gestures.ApplyEdgeSwipeExclusion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalMaterial3Api::class)
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
            // Drawer blocks are kept identical to the old composable<T> bodies —
            // only navController.navigate/popBackStack calls are replaced with
            // navigator.navigateToX() / navigator.onBack().
            // ----------------------------------------------------------------
            NavDisplay(
                backStack = navigator.backStack,
                onBack = { navigator.onBack() },
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

                    entry<AppNavRoute.Main> {
                        // Use plain `remember` instead of `rememberDrawerState` to avoid
                        // the Saver restoring an Open state after back navigation — a fresh
                        // Closed state on every composition is the correct UX contract.
                        val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
                        val scope = rememberCoroutineScope()
                        var isEditMode by rememberSaveable { mutableStateOf(false) }
                        // Debounce guard: rapid drawer clicks can double-navigate and flash an
                        // empty screen while Koin rebinds the second entry. See memory
                        // feedback_rapid_click_vs_di_race.md.
                        var navConsumed by remember { mutableStateOf(false) }
                        LaunchedEffect(navConsumed) {
                            if (navConsumed) {
                                delay(500)
                                navConsumed = false
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            // Disable swipe-to-open while in edit mode (drag-reorder conflict)
                            gesturesEnabled = !isEditMode,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppNavigationDrawerContent(
                                        selectedItemId = DrawerDestination.Main,
                                        onCloseDrawer = { scope.launch { drawerState.close() } },
                                        onHomeClick = { /* already on Main; drawer close is enough */ },
                                        onTodayClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToToday()
                                        },
                                        onCalendarClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToCalendar()
                                        },
                                        onAiChatClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToAiChat()
                                        },
                                        onUpdateFeedClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToUpdateFeed()
                                        },
                                        onSettingsClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToSettings()
                                        },
                                        onRateAppClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShow)
                                        },
                                        onLeaveFeedbackClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShowFeedback)
                                        },
                                        versionName = AppBuildConfig.versionName,
                                    )
                                }
                            }
                        ) {
                            // Reserve the left ~48dp as our own gesture area so
                            // Android's edge swipe-back doesn't steal swipes meant
                            // for opening the ModalNavigationDrawer.
                            ApplyEdgeSwipeExclusion(
                                enabled = drawerState.isClosed && !isEditMode
                            )
                            Box(modifier = Modifier.fillMaxSize()) {
                                MainScreen(
                                    drawerState = drawerState,
                                    isEditMode = isEditMode,
                                    onEditModeChange = { isEditMode = it },
                                    onNavigateToAiChat = {
                                        if (navConsumed) return@MainScreen
                                        navConsumed = true
                                        navigator.navigateToAiChat()
                                    },
                                )
                            }
                        }
                    }

                    entry<AppNavRoute.CreateChecklistRoute.CreateChecklist> { route ->
                        CreateChecklistScreen(editChecklistId = route.editChecklistId)
                    }

                    entry<AppNavRoute.CreateChecklistRoute.Templates> {
                        TemplatesScreen()
                    }

                    entry<AppNavRoute.CreateChecklistRoute.TemplatePreview> { route ->
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

                    entry<AppNavRoute.ChecklistDetail> { route ->
                        ChecklistDetailScreen(
                            checklistId = route.checklistId,
                            focusItemId = route.focusItemId,
                        )
                    }

                    entry<AppNavRoute.FillDetail> { route ->
                        FillDetailScreen(fillId = route.fillId)
                    }

                    entry<AppNavRoute.FillsList> { route ->
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
                        val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
                        val scope = rememberCoroutineScope()
                        var navConsumed by remember { mutableStateOf(false) }
                        LaunchedEffect(navConsumed) {
                            if (navConsumed) {
                                delay(500)
                                navConsumed = false
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppNavigationDrawerContent(
                                        selectedItemId = DrawerDestination.Settings,
                                        onCloseDrawer = { scope.launch { drawerState.close() } },
                                        onHomeClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            // popUpTo(Main, inclusive = false) — pop back to Main
                                            val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                            if (mainIdx >= 0) {
                                                while (navigator.backStack.size > mainIdx + 1) {
                                                    navigator.backStack.removeAt(navigator.backStack.size - 1)
                                                }
                                            } else {
                                                navigator.onBack()
                                            }
                                        },
                                        onTodayClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToToday()
                                        },
                                        onCalendarClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToCalendar()
                                        },
                                        onAiChatClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToAiChat()
                                        },
                                        onUpdateFeedClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToUpdateFeed()
                                        },
                                        onSettingsClick = { /* already here */ },
                                        onRateAppClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShow)
                                        },
                                        onLeaveFeedbackClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShowFeedback)
                                        },
                                        versionName = AppBuildConfig.versionName,
                                    )
                                }
                            }
                        ) {
                            SettingsScreen(
                                onBackClick = { navigator.onBack() },
                                drawerState = drawerState,
                            )
                        }
                    }

                    entry<AppNavRoute.UpdateFeed> {
                        // Fresh Closed DrawerState per entry — same rationale as Main route.
                        val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
                        val scope = rememberCoroutineScope()
                        var navConsumed by remember { mutableStateOf(false) }
                        LaunchedEffect(navConsumed) {
                            if (navConsumed) {
                                delay(500)
                                navConsumed = false
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppNavigationDrawerContent(
                                        selectedItemId = DrawerDestination.UpdateFeed,
                                        onCloseDrawer = { scope.launch { drawerState.close() } },
                                        onHomeClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.onBack()
                                        },
                                        onTodayClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToToday()
                                        },
                                        onCalendarClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToCalendar()
                                        },
                                        onAiChatClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToAiChat()
                                        },
                                        onUpdateFeedClick = { /* already here */ },
                                        onSettingsClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToSettings()
                                        },
                                        onRateAppClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShow)
                                        },
                                        onLeaveFeedbackClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShowFeedback)
                                        },
                                        versionName = AppBuildConfig.versionName,
                                    )
                                }
                            }
                        ) {
                            UpdateFeedScreen(
                                onBackClick = { navigator.onBack() },
                                drawerState = drawerState,
                            )
                        }
                    }

                    entry<AppNavRoute.Today> {
                        // Fresh Closed DrawerState per entry — same rationale as Main route.
                        val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
                        val scope = rememberCoroutineScope()
                        var navConsumed by remember { mutableStateOf(false) }
                        LaunchedEffect(navConsumed) {
                            if (navConsumed) {
                                delay(500)
                                navConsumed = false
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppNavigationDrawerContent(
                                        selectedItemId = DrawerDestination.Today,
                                        onCloseDrawer = { scope.launch { drawerState.close() } },
                                        onHomeClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                            if (mainIdx >= 0) {
                                                while (navigator.backStack.size > mainIdx + 1) {
                                                    navigator.backStack.removeAt(navigator.backStack.size - 1)
                                                }
                                            } else {
                                                navigator.onBack()
                                            }
                                        },
                                        onTodayClick = { /* already here */ },
                                        onCalendarClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToCalendar()
                                        },
                                        onAiChatClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToAiChat()
                                        },
                                        onUpdateFeedClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToUpdateFeed()
                                        },
                                        onSettingsClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToSettings()
                                        },
                                        onRateAppClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShow)
                                        },
                                        onLeaveFeedbackClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShowFeedback)
                                        },
                                        versionName = AppBuildConfig.versionName,
                                    )
                                }
                            }
                        ) {
                            TodayRoute(
                                drawerState = drawerState,
                                onCreateChecklistClick = {
                                    navigator.navigateToTemplatesScreen()
                                },
                            )
                        }
                    }

                    entry<AppNavRoute.Calendar> {
                        // Fresh Closed DrawerState per entry — same rationale as Main/Today routes.
                        val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
                        val scope = rememberCoroutineScope()
                        var navConsumed by remember { mutableStateOf(false) }
                        LaunchedEffect(navConsumed) {
                            if (navConsumed) {
                                delay(500)
                                navConsumed = false
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppNavigationDrawerContent(
                                        selectedItemId = DrawerDestination.Calendar,
                                        onCloseDrawer = { scope.launch { drawerState.close() } },
                                        onHomeClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                            if (mainIdx >= 0) {
                                                while (navigator.backStack.size > mainIdx + 1) {
                                                    navigator.backStack.removeAt(navigator.backStack.size - 1)
                                                }
                                            } else {
                                                navigator.onBack()
                                            }
                                        },
                                        onTodayClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToToday()
                                        },
                                        onCalendarClick = { /* already here */ },
                                        onAiChatClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToAiChat()
                                        },
                                        onUpdateFeedClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToUpdateFeed()
                                        },
                                        onSettingsClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToSettings()
                                        },
                                        onRateAppClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShow)
                                        },
                                        onLeaveFeedbackClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShowFeedback)
                                        },
                                        versionName = AppBuildConfig.versionName,
                                    )
                                }
                            }
                        ) {
                            CalendarRoute(
                                drawerState = drawerState,
                                onCreateChecklistClick = {
                                    navigator.navigateToTemplatesScreen()
                                },
                            )
                        }
                    }

                    entry<AppNavRoute.AiChat> {
                        // Fresh Closed DrawerState per entry — same rationale as Main/Today/Calendar.
                        val drawerState = remember { DrawerState(initialValue = DrawerValue.Closed) }
                        val scope = rememberCoroutineScope()
                        var navConsumed by remember { mutableStateOf(false) }
                        LaunchedEffect(navConsumed) {
                            if (navConsumed) {
                                delay(500)
                                navConsumed = false
                            }
                        }

                        ModalNavigationDrawer(
                            drawerState = drawerState,
                            drawerContent = {
                                ModalDrawerSheet(
                                    drawerContainerColor = MaterialTheme.colorScheme.surface
                                ) {
                                    AppNavigationDrawerContent(
                                        selectedItemId = DrawerDestination.AiChat,
                                        onCloseDrawer = { scope.launch { drawerState.close() } },
                                        onHomeClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            val mainIdx = navigator.backStack.indexOfFirst { it is AppNavRoute.Main }
                                            if (mainIdx >= 0) {
                                                while (navigator.backStack.size > mainIdx + 1) {
                                                    navigator.backStack.removeAt(navigator.backStack.size - 1)
                                                }
                                            } else {
                                                navigator.onBack()
                                            }
                                        },
                                        onTodayClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToToday()
                                        },
                                        onCalendarClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToCalendar()
                                        },
                                        onAiChatClick = { /* already here */ },
                                        onUpdateFeedClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToUpdateFeed()
                                        },
                                        onSettingsClick = {
                                            if (navConsumed) return@AppNavigationDrawerContent
                                            navConsumed = true
                                            navigator.navigateToSettings()
                                        },
                                        onRateAppClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShow)
                                        },
                                        onLeaveFeedbackClick = {
                                            csatViewModel.sendIntent(CsatIntent.ForceShowFeedback)
                                        },
                                        versionName = AppBuildConfig.versionName,
                                    )
                                }
                            }
                        ) {
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
