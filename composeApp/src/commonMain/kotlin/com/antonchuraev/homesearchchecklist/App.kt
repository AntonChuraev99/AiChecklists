package com.antonchuraev.homesearchchecklist

import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.preview.TemplatePreviewScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.StoreScreenshotScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.AnalyzeScreen
import com.antonchuraev.homesearchchecklist.feature.analyze.presentation.preview.AnalyzeResultPreviewScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.ChecklistDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fill.FillDetailScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.fills.FillsListScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.PaywallScreen
import com.antonchuraev.homesearchchecklist.feature.paywall.presentation.SubscriptionStatusScreen
import com.antonchuraev.homesearchchecklist.feature.sharing.presentation.ShareScreen
import androidx.navigation.toRoute
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication
import org.koin.compose.viewmodel.koinViewModel

@Composable
@Preview
fun App() {
    KoinApplication(
        application = { modules(appModule) }
    ) {

        val viewModel: AppViewModel = koinViewModel()

        val navController = rememberNavController().also {
            viewModel.installNavController(it)
        }

        AppTheme {
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

                composable<AppNavRoute.Main> {
                    MainScreen()
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
                        DebugScreen()
                    }

                    composable<AppNavRoute.StoreScreenshot> {
                        StoreScreenshotScreen()
                    }
                }

                composable<AppNavRoute.Analyze> { backStackEntry ->
                    val route = backStackEntry.toRoute<AppNavRoute.Analyze>()
                    AnalyzeScreen(checklistId = route.checklistId)
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
            }
        }
    }
}
