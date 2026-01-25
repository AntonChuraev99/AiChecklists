package com.antonchuraev.homesearchchecklist

import androidx.compose.ui.window.ComposeUIViewController
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import org.koin.mp.KoinPlatform

fun MainViewController() = ComposeUIViewController { App() }

fun navigateToDebugMenu() {
    if (!AppBuildConfig.isDebug) {
        println("Debug menu is only available in debug builds")
        return
    }
    try {
        val navigator = KoinPlatform.getKoin().get<AppNavigator>()
        navigator.navigateToDebugMenu()
    } catch (e: Exception) {
        // Koin might not be initialized yet
        println("Cannot navigate to debug menu: ${e.message}")
    }
}