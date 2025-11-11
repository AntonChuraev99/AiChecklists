package com.antonchuraev.homesearchchecklist.navigation

/**
 * Навигационные маршруты приложения
 */
sealed class Screen(val route: String) {
    /**
     * Экран онбординга
     */
    data object Onboarding : Screen("onboarding")
    
    /**
     * Главный экран
     */
    data object Main : Screen("main")
    
    /**
     * Дебаг меню
     */
    data object Debug : Screen("debug")
}

/**
 * Табы нижней навигации главного экрана
 */
enum class MainTab {
    HOME,
    FUTURE
}

