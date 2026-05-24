package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.notification.ReminderScheduler
import com.antonchuraev.homesearchchecklist.widget.data.WidgetRepository
import com.antonchuraev.homesearchchecklist.widget.data.WidgetStateManager
import org.koin.dsl.module

/**
 * Koin module for androidApp-specific bindings.
 *
 * These bindings reference classes that live in :androidApp (widget, notification)
 * to avoid circular dependency with :composeApp.
 *
 * Loaded by GistiAndroidApplication in addition to the base appModule.
 */
val androidAppModule = module {
    // Widget dependencies
    single { WidgetRepository(get(), get()) }
    single { WidgetStateManager(get()) }

    // Reminder scheduler — Android AlarmManager impl of ChecklistReminderScheduler
    single<ChecklistReminderScheduler> { ReminderScheduler(get(), get()) }
}
