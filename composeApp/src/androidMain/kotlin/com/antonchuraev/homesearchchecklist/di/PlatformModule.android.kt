package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.Analytics
import com.antonchuraev.homesearchchecklist.AppBuildConfig
import com.antonchuraev.homesearchchecklist.CrashlyticsAppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStorage
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.impl.AndroidAppLogger
import com.antonchuraev.homesearchchecklist.csat.ObservableAnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-level Koin bindings for composeApp KMP library.
 *
 * NOTE: GeminiConfig, widgetModule, and ReminderScheduler are intentionally
 * NOT included here. They depend on BuildConfig (generated only in androidApp)
 * or on classes that live in androidApp to avoid circular dependency.
 *
 * androidApp/GistiApplication.startKoin{} adds an additional Koin module
 * that provides:
 *   - GeminiConfig (reads BuildConfig.GEMINI_API_KEY from androidApp)
 *   - widgetModule (WidgetRepository, WidgetStateManager)
 *   - ChecklistReminderScheduler binding → ReminderScheduler
 */
actual fun platformModule(): Module = module {
    single { AppContextHolder.context }
    single { DeviceIdProvider(UserAppDatastoreProvider.instance) }
    single<AnalyticsTracker> { ObservableAnalyticsTracker(Analytics) }

    // Override AppLogger with Crashlytics breadcrumbs and non-fatal recording
    single<AppLogger> { CrashlyticsAppLogger(AndroidAppLogger()) }

    // AttachmentStorage: Android-only real implementation (Phase 2).
    // Bound as AttachmentStoragePort so ViewModel stays platform-agnostic in commonMain.
    single<AttachmentStoragePort> { AttachmentStorage() }

    // AttachmentOpener: Android implementation uses FileProvider + Intent.ACTION_VIEW.
    // iOS/wasmJs stubs return false (attachments unsupported until Phase 5/v2).
    single { AttachmentOpener() }
}
