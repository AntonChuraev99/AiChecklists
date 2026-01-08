package com.antonchuraev.homesearchchecklist

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavRoute
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.create.presentation.create.CreateChecklistScreen
import com.antonchuraev.homesearchchecklist.feature.create.presentation.templates.TemplatesScreen
import com.antonchuraev.homesearchchecklist.feature.debug.presentation.DebugScreen
import com.antonchuraev.homesearchchecklist.feature.home.presentation.MainScreen
import com.antonchuraev.homesearchchecklist.feature.onboarding.presentation.OnboardingScreen
import com.antonchuraev.homesearchchecklist.feature.splash.presentation.SplashScreen
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

        MaterialTheme {
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

                composable<AppNavRoute.CreateChecklistRoute.CreateChecklist> {
                    CreateChecklistScreen()
                }

                composable<AppNavRoute.CreateChecklistRoute.Templates> {
                    TemplatesScreen()
                }

                /**
                 * todo add only in debug
                 */
                composable<AppNavRoute.Debug> {
                    DebugScreen()
                }
            }
        }
    }
}
