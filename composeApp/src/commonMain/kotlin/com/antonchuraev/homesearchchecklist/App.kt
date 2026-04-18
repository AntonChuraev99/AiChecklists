package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.csat.CsatBottomSheet
import com.antonchuraev.homesearchchecklist.csat.CsatIntent
import com.antonchuraev.homesearchchecklist.csat.CsatViewModel
import com.antonchuraev.homesearchchecklist.csat.InAppReviewLauncher
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.feedback_thanks_message
import org.jetbrains.compose.resources.stringResource
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.preview.TemplatePreviewScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.StoreScreenshotScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.interactive.InteractiveOnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.SubscriptionStatusScreen
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.ShareScreen
import com.antonchuraev.homesearchchecklist.feature.updatefeed.presentation.UpdateFeedScreen
import androidx.navigation.toRoute
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinContext
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.context.GlobalContext

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

        val viewModel: AppViewModel = koinViewModel()

        val navController = rememberNavController()
        LaunchedEffect(navController) {
            viewModel.installNavController(navController)
            logger.debug("App", "NavController installed")
        }

        val csatViewModel: CsatViewModel = koinInject()
        val csatState by csatViewModel.screenState.collectAsState()

        AppTheme {
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
                    MainScreen(
                        onRateAppClick = { csatViewModel.sendIntent(CsatIntent.ForceShow) },
                        onLeaveFeedbackClick = { csatViewModel.sendIntent(CsatIntent.ForceShowFeedback) },
                        versionName = AppBuildConfig.versionName,
                    )
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
                    ChecklistDetailScreen(checklistId = route.checklistId)
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
                    PaywallScreen()
                }

                composable<AppNavRoute.SubscriptionStatus> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.SubscriptionStatus>()
                    SubscriptionStatusScreen(showSuccessMessage = route.showSuccessMessage)
                }

                composable<AppNavRoute.ShareChecklist> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.ShareChecklist>()
                    ShareScreen(checklistId = route.checklistId)
                }

                composable<AppNavRoute.UpdateFeed> {
                    UpdateFeedScreen(
                        onBackClick = { navController.popBackStack() }
                    )
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

            // In-App Review launcher — side-effect composable, no UI
            InAppReviewLauncher(
                shouldLaunch = csatState.shouldLaunchReview,
                onComplete = { csatViewModel.sendIntent(CsatIntent.ReviewComplete) },
            )
        }
    }
}
