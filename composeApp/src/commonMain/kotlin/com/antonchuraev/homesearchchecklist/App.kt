package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavEvent
import com.antonchuraev.homesearchchecklist.core.navigation.api.NavCommand
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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
import androidx.navigation.toRoute
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
    // NavHost recompositions (which may recreate this composable) never lose
    // the scope — `remember` keeps the same instance across recompositions
    // without ever calling stopKoin. KoinContext then simply provides
    // LocalKoinScope to children. This pattern fixes Crashlytics issue
    // edf060726547384d4e61afa21b1529ea (Koin #2240, marked wontfix).
    val koin = remember { GlobalContext.get() }
    KoinContext(koin) {
        val logger: AppLogger = remember { koin.get<AppLogger>() }
        LaunchedEffect(Unit) {
            logger.debug("App", "App composable start, Koin ref stable")
        }

        val navController = rememberNavController()

        // Channel-based navigation: AppNavigator emits NavCommand, App.kt translates
        // each command into a NavController call. NavController never leaves Compose layer.
        // Channel.BUFFERED ensures commands emitted by ViewModel.init before this
        // LaunchedEffect starts collecting are queued and delivered in order.
        val navigator: AppNavigator = remember { koin.get<AppNavigator>() }
        LaunchedEffect(navController) {
            logger.debug("App", "NavCommand collector started")
            navigator.commands.collect { command ->
                navController.handle(command)
            }
        }

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
            NavHost(
                navController = navController,
                startDestination = AppNavRoute.Splash
            ) {
                composable<AppNavRoute.Splash> {
                    SplashScreen()
                }

                composable<AppNavRoute.Onboarding> {
                    OnboardingScreen()
                }

                composable<AppNavRoute.InteractiveOnboarding> {
                    InteractiveOnboardingScreen()
                }

                composable<AppNavRoute.Main> {
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
                                        navController.navigate(AppNavRoute.Today) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onCalendarClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Calendar) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onAiChatClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.AiChat) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onUpdateFeedClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.UpdateFeed) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSettingsClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Settings) {
                                            launchSingleTop = true
                                        }
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
                        MainScreen(
                            drawerState = drawerState,
                            isEditMode = isEditMode,
                            onEditModeChange = { isEditMode = it },
                            onNavigateToAiChat = {
                                if (navConsumed) return@MainScreen
                                navConsumed = true
                                navController.navigate(AppNavRoute.AiChat) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }

                composable<AppNavRoute.CreateChecklistRoute.CreateChecklist> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.CreateChecklistRoute.CreateChecklist>()
                    CreateChecklistScreen(editChecklistId = route.editChecklistId)
                }

                composable<AppNavRoute.CreateChecklistRoute.Templates> {
                    TemplatesScreen()
                }

                composable<AppNavRoute.CreateChecklistRoute.TemplatePreview> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.CreateChecklistRoute.TemplatePreview>()
                    TemplatePreviewScreen(templateId = route.templateId)
                }

                // Debug screens - only available in debug builds
                if (AppBuildConfig.isDebug) {
                    composable<AppNavRoute.Debug> {
                        DebugScreen(
                            onShowCsat = { csatViewModel.sendIntent(CsatIntent.ForceShow) }
                        )
                    }

                    composable<AppNavRoute.StoreScreenshot> {
                        StoreScreenshotScreen()
                    }

                    composable<AppNavRoute.ScreenCatalog> {
                        ScreenCatalogScreen()
                    }

                    composable<AppNavRoute.Onboardings> {
                        OnboardingsScreen()
                    }
                }

                composable<AppNavRoute.Analyze> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.Analyze>()
                    AnalyzeScreen(checklistId = route.checklistId, fillDefault = route.fillDefault)
                }

                composable<AppNavRoute.AnalyzeResultPreview> {
                    AnalyzeResultPreviewScreen()
                }

                composable<AppNavRoute.ChecklistDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.ChecklistDetail>()
                    ChecklistDetailScreen(
                        checklistId = route.checklistId,
                        focusItemId = route.focusItemId,
                    )
                }

                composable<AppNavRoute.FillDetail> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.FillDetail>()
                    FillDetailScreen(fillId = route.fillId)
                }

                composable<AppNavRoute.FillsList> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.FillsList>()
                    FillsListScreen(checklistId = route.checklistId)
                }

                composable<AppNavRoute.Paywall> {
                    PaywallRoute()
                }

                composable<AppNavRoute.SubscriptionStatus> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.SubscriptionStatus>()
                    SubscriptionStatusScreen(showSuccessMessage = route.showSuccessMessage)
                }

                composable<AppNavRoute.ShareChecklist> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.ShareChecklist>()
                    ShareScreen(checklistId = route.checklistId)
                }

                composable<AppNavRoute.Settings> {
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
                                        navController.popBackStack(AppNavRoute.Main, inclusive = false)
                                    },
                                    onTodayClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Today) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onCalendarClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Calendar) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onAiChatClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.AiChat) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onUpdateFeedClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.UpdateFeed) {
                                            launchSingleTop = true
                                        }
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
                            onBackClick = { navController.popBackStack() },
                            drawerState = drawerState,
                        )
                    }
                }

                composable<AppNavRoute.UpdateFeed> {
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
                                        navController.popBackStack()
                                    },
                                    onTodayClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Today) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onCalendarClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Calendar) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onAiChatClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.AiChat) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onUpdateFeedClick = { /* already here */ },
                                    onSettingsClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Settings) {
                                            launchSingleTop = true
                                        }
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
                            onBackClick = { navController.popBackStack() },
                            drawerState = drawerState,
                        )
                    }
                }

                composable<AppNavRoute.Today> {
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
                                        navController.popBackStack(AppNavRoute.Main, inclusive = false)
                                    },
                                    onTodayClick = { /* already here */ },
                                    onCalendarClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Calendar) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onAiChatClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.AiChat) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onUpdateFeedClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.UpdateFeed) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSettingsClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Settings) {
                                            launchSingleTop = true
                                        }
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

                composable<AppNavRoute.Calendar> {
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
                                        navController.popBackStack(AppNavRoute.Main, inclusive = false)
                                    },
                                    onTodayClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Today) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onCalendarClick = { /* already here */ },
                                    onAiChatClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.AiChat) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onUpdateFeedClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.UpdateFeed) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSettingsClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Settings) {
                                            launchSingleTop = true
                                        }
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

                composable<AppNavRoute.AiChat> {
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
                                        navController.popBackStack(AppNavRoute.Main, inclusive = false)
                                    },
                                    onTodayClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Today) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onCalendarClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Calendar) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onAiChatClick = { /* already here */ },
                                    onUpdateFeedClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.UpdateFeed) {
                                            launchSingleTop = true
                                        }
                                    },
                                    onSettingsClick = {
                                        if (navConsumed) return@AppNavigationDrawerContent
                                        navConsumed = true
                                        navController.navigate(AppNavRoute.Settings) {
                                            launchSingleTop = true
                                        }
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
                            onBack = { navController.popBackStack() },
                            onNavigateToChecklist = { checklistId ->
                                navController.navigate(AppNavRoute.ChecklistDetail(checklistId))
                            },
                            onNavigateToPaywall = {
                                navController.navigate(AppNavRoute.Paywall(source = "chat_credits_chip")) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
            }

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

/**
 * Translates a [NavCommand] into the corresponding [NavController] call.
 * All navigation logic that was previously scattered across AppNavigatorImpl
 * now lives here, next to the NavController that owns the back stack.
 */
private fun NavController.handle(command: NavCommand) {
    when (command) {
        is NavCommand.Back -> popBackStack()

        is NavCommand.ToOnboarding -> navigate(AppNavRoute.Onboarding) {
            popUpTo<AppNavRoute.Splash> { inclusive = true }
            launchSingleTop = true
        }

        is NavCommand.ToInteractiveOnboarding -> navigate(AppNavRoute.InteractiveOnboarding) {
            popUpTo<AppNavRoute.Splash> { inclusive = true }
            launchSingleTop = true
        }

        is NavCommand.ToMainScreen -> {
            if (command.clearBackStack) {
                navigate(AppNavRoute.Main) {
                    popUpTo(0) { inclusive = true }
                }
            } else {
                navigate(AppNavRoute.Main)
            }
        }

        is NavCommand.ToDebugMenu -> navigate(AppNavRoute.Debug)

        is NavCommand.ToStoreScreenshot -> navigate(AppNavRoute.StoreScreenshot)

        is NavCommand.ToCreateChecklistScreen ->
            navigate(AppNavRoute.CreateChecklistRoute.CreateChecklist(command.templateId))

        is NavCommand.ToEditChecklist ->
            navigate(AppNavRoute.CreateChecklistRoute.CreateChecklist(editChecklistId = command.checklistId))

        is NavCommand.ToTemplatesScreen ->
            navigate(AppNavRoute.CreateChecklistRoute.Templates)

        is NavCommand.ToTemplatePreview ->
            navigate(AppNavRoute.CreateChecklistRoute.TemplatePreview(command.templateId))

        is NavCommand.ToAnalyzeScreen ->
            navigate(AppNavRoute.Analyze(command.checklistId, command.fillDefault))

        is NavCommand.ToAnalyzeResultPreview -> navigate(AppNavRoute.AnalyzeResultPreview)

        is NavCommand.ToChecklistDetail -> {
            val route = AppNavRoute.ChecklistDetail(
                checklistId = command.checklistId,
                focusItemId = command.focusItemId,
            )
            if (command.clearBackStack) {
                navigate(route) {
                    popUpTo(AppNavRoute.Main) { inclusive = false }
                }
            } else {
                navigate(route)
            }
        }

        is NavCommand.ToFillDetail -> {
            if (command.clearBackStack) {
                navigate(AppNavRoute.FillDetail(command.fillId)) {
                    popUpTo(AppNavRoute.Main) { inclusive = false }
                }
            } else {
                navigate(AppNavRoute.FillDetail(command.fillId))
            }
        }

        is NavCommand.ToFillsList -> navigate(AppNavRoute.FillsList(command.checklistId))

        is NavCommand.ToPaywall -> navigate(AppNavRoute.Paywall(command.source))

        is NavCommand.ToPaywallVariant ->
            navigate(AppNavRoute.Paywall(source = command.source, forceVariant = command.forceVariant))

        is NavCommand.ToSubscriptionStatus ->
            navigate(AppNavRoute.SubscriptionStatus(command.showSuccessMessage)) {
                // Remove Paywall from back stack so "back" returns to the screen before Paywall
                popUpTo<AppNavRoute.Paywall> { inclusive = true }
            }

        is NavCommand.ToShareChecklist -> navigate(AppNavRoute.ShareChecklist(command.checklistId))

        is NavCommand.ToUpdateFeed -> navigate(AppNavRoute.UpdateFeed) {
            launchSingleTop = true
        }

        is NavCommand.ToSettings -> navigate(AppNavRoute.Settings) {
            launchSingleTop = true
        }

        is NavCommand.ToToday -> navigate(AppNavRoute.Today) {
            launchSingleTop = true
        }

        is NavCommand.ToCalendar -> navigate(AppNavRoute.Calendar) {
            launchSingleTop = true
        }

        is NavCommand.ToAiChat -> navigate(AppNavRoute.AiChat) {
            launchSingleTop = true
        }

        is NavCommand.ToScreenCatalog -> navigate(AppNavRoute.ScreenCatalog)

        is NavCommand.ToOnboardings -> navigate(AppNavRoute.Onboardings)
    }
}
