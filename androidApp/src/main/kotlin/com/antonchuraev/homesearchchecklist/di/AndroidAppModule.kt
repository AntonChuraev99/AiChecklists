package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.aichecklists.app.BuildConfig
import com.antonchuraev.homesearchchecklist.feature.analyze.data.config.GeminiConfig
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.notification.ReminderScheduler
import com.antonchuraev.homesearchchecklist.widget.data.WidgetRepository
import com.antonchuraev.homesearchchecklist.widget.data.WidgetStateManager
import org.koin.dsl.module

/**
 * Koin module for androidApp-specific bindings.
 *
 * These bindings require BuildConfig (generated only in :androidApp),
 * or reference classes that live in :androidApp to avoid circular dependency
 * with :composeApp.
 *
 * Loaded by GistiAndroidApplication in addition to the base appModule.
 */
val androidAppModule = module {
    // GeminiConfig — requires BuildConfig.GEMINI_API_KEY from :androidApp
    single { GeminiConfig(apiKey = BuildConfig.GEMINI_API_KEY) }

    // Widget dependencies
    single { WidgetRepository(get(), get()) }
    single { WidgetStateManager(get()) }

    // Reminder scheduler — Android AlarmManager impl of ChecklistReminderScheduler
    single<ChecklistReminderScheduler> { ReminderScheduler(get(), get()) }
}
