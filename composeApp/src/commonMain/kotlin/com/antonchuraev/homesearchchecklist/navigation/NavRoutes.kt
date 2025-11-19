package com.antonchuraev.homesearchchecklist.navigation

import kotlinx.serialization.Serializable

/**
 * Навигационные маршруты приложения
 */
@Serializable
sealed class Screen(val route: String) {
    /**
     * Экран онбординга
     */
    data object Onboarding : Screen("onboarding")
    
    /**
     * Главный экран
     */
    data object Main : Screen("main")

    @Serializable
    sealed class CreateChecklist(private val routeParam: String): Screen(routeParam){

        @Serializable
        data class CreateChecklist(
            val templateId: Int?
        ): Screen.CreateChecklist("create_checklist")

        @Serializable
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

