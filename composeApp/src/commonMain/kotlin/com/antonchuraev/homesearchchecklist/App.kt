package com.antonchuraev.homesearchchecklist

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.create.presentation.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
import com.antonchuraev.homesearchchecklist.navigation.AppNavRoute
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

@Composable
@Preview
fun App() {
    KoinApplication(
        application = { modules(appModule) }
    ) {
        MaterialTheme {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = AppNavRoute.Splash
            ) {
                composable<AppNavRoute.Splash> {
                    SplashScreen()
                }

                composable<AppNavRoute.Onboarding> {
                    OnboardingScreen(
                        onComplete = {
                            navController.navigate(AppNavRoute.Main) {
                                popUpTo(AppNavRoute.Onboarding) { inclusive = true }
                            }
                        }
                    )
                }

                composable<AppNavRoute.Main> {
                    MainScreen(
                        onDebugClick = { navController.navigate(AppNavRoute.Debug) },
                        openCreateNewChecklistScreen = {
                            navController.navigate(AppNavRoute.CreateChecklistRoute.CreateChecklist(null))
                        },
                        openSelectFromTemplatesScreen = {
                            navController.navigate(AppNavRoute.CreateChecklistRoute.Templates)
                        }
                    )
                }

                composable<AppNavRoute.CreateChecklistRoute.CreateChecklist> {
                    CreateChecklistScreen(onBackButtonClick = navController::popBackStack)
                }

                composable<AppNavRoute.CreateChecklistRoute.Templates> {
                    TemplatesScreen(onBackButtonClick = navController::popBackStack)
                }

                composable<AppNavRoute.Debug> {
                    DebugScreen(onBack = navController::popBackStack)
                }
            }
        }
    }
}
