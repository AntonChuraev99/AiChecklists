package com.antonchuraev.homesearchchecklist.core.navigation.api

/**
 * One-shot navigation events published by AppNavigator and consumed by App.kt.
 * SharedFlow with replay=0 ensures each event is delivered once.
 */
sealed interface AppNavEvent {
    /** Open the widget instruction bottom sheet overlay (global, above all screens). */
    data object ShowWidgetInstruction : AppNavEvent

    /** Request creation of a new weekly checklist (with premium gate). App.kt handles the logic. */
    data object CreateWeeklyChecklistRequested : AppNavEvent
}
