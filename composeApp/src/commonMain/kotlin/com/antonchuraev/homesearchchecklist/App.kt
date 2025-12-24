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
import com.antonchuraev.homesearchchecklist.navigation.Screen
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
                startDestination = Screen.Onboarding.route
            ) {
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onComplete = {
                            navController.navigate(Screen.Main.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Main.route) {
                    MainScreen(
                        onDebugClick = { navController.navigate(Screen.Debug.route) },
                        openCreateNewChecklistScreen = {
                            navController.navigate(Screen.CreateChecklist.CreateChecklist(null))
                        },
                        openSelectFromTemplatesScreen = {
                            navController.navigate(Screen.CreateChecklist.Templates)
                        }
                    )
                }

                composable<Screen.CreateChecklist.CreateChecklist> {
                    CreateChecklistScreen(onBackButtonClick = navController::popBackStack)
                }

                composable<Screen.CreateChecklist.Templates> {
                    TemplatesScreen(onBackButtonClick = navController::popBackStack)
                }

                composable(Screen.Debug.route) {
                    DebugScreen(onBack = navController::popBackStack)
                }
            }
        }
    }
}
