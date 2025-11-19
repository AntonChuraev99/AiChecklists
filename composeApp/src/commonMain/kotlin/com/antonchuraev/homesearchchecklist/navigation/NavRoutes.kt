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

    sealed class CreateChecklist(route: String): Screen(route){

        data class CreateChecklist(
            val templateId: Int?
        ): Screen.CreateChecklist("create_checklist")

        data object Templates: Screen.CreateChecklist("templates")
    }

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

