package com.antonchuraev.homesearchchecklist

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.navigation.Screen
import com.antonchuraev.homesearchchecklist.screens.DebugScreen
import com.antonchuraev.homesearchchecklist.screens.MainScreen
import com.antonchuraev.homesearchchecklist.screens.OnboardingScreen
import com.antonchuraev.homesearchchecklist.screens.create.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.screens.create.TemplatesScreen
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.KoinApplication

@Composable
@Preview
fun App() {
    KoinApplication(
        application = {
            modules(appModule)
        }
    ) {
        MaterialTheme {
            val navController = rememberNavController()

            NavHost(
                navController = navController,
                startDestination = Screen.Onboarding.route
            ) {
                // Экран онбординга
                composable(Screen.Onboarding.route) {
                    OnboardingScreen(
                        onComplete = {
                            navController.navigate(Screen.Main.route) {
                                // Удаляем онбординг из стека
                                popUpTo(Screen.Onboarding.route) {
                                    inclusive = true
                                }
                            }
                        }
                    )
                }

                // Главный экран с нижней навигацией
                composable(Screen.Main.route) {
                    MainScreen(
                        onDebugClick = {
                            navController.navigate(Screen.Debug.route)
                        },
                        openCreateNewChecklistScreen = {
                            navController.navigate(Screen.CreateChecklist.CreateChecklist(null))
                        },
                        openSelectFromTemplatesScreen = {
                            navController.navigate(Screen.CreateChecklist.Templates)
                        }
                    )
                }

                composable<Screen.CreateChecklist.CreateChecklist>(){
                    CreateChecklistScreen()
                }

                composable<Screen.CreateChecklist.Templates>(){
                    TemplatesScreen()
                }

                // Дебаг меню
                composable(Screen.Debug.route) {
                    DebugScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}