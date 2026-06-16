package com.antonchuraev.homesearchchecklist.di

import com.antonchuraev.homesearchchecklist.Analytics
import com.antonchuraev.homesearchchecklist.AppBuildConfig
import com.antonchuraev.homesearchchecklist.CrashlyticsAppLogger
import com.antonchuraev.homesearchchecklist.appupdate.AppUpdateController
import com.antonchuraev.homesearchchecklist.calendar.AndroidCalendarEventLauncher
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentOpener
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStorage
import com.antonchuraev.homesearchchecklist.core.common.api.AttachmentStoragePort
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.antonchuraev.homesearchchecklist.core.common.impl.AndroidAppLogger
import com.antonchuraev.homesearchchecklist.csat.ObservableAnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.datastore.api.UserAppDatastoreProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.calendar.CalendarEventLauncher
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import com.antonchuraev.homesearchchecklist.push.PushTokenRepositoryAndroid
import com.antonchuraev.homesearchchecklist.sync.AndroidFirestoreSyncDataSource
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Platform-level Koin bindings for composeApp KMP library.
 *
 * NOTE: widgetModule and ReminderScheduler are intentionally NOT included here —
 * they live in :androidApp to avoid circular dependency. GistiAndroidApplication's
 * startKoin{} loads an additional androidAppModule providing those bindings.
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

    // Exposes AppBuildConfig.isDebug to feature modules that live in commonMain
    // and cannot import AppBuildConfig directly (it is an expect object in :composeApp).
    // Used by OnboardingViewModel / InteractiveOnboardingViewModel to suppress
    // analytics events when launched from the debug menu.
    single(named("isDebugBuild")) { AppBuildConfig.isDebug }

    // Firestore sync data source — Android implementation using the Firebase Android SDK.
    single<FirestoreSyncDataSource> { AndroidFirestoreSyncDataSource() }

    // FCM token + activity tracking — writes users/{uid}.fcmToken / lastActiveAt for
    // the re-engagement campaign. Resolved by GistiFirebaseMessagingService (onNewToken)
    // and GistiApplication (app-start registration) via GlobalContext.
    single<PushTokenRepository> { PushTokenRepositoryAndroid(get(), get()) }

    // Google Play in-app update controller (Android-only). Observed by AppUpdateLauncher;
    // get() resolves Context (AppContextHolder.context), AppLogger and AnalyticsTracker above.
    single { AppUpdateController(get(), get(), get()) }

    // One-way calendar export (ACTION_INSERT into the system calendar). Lives here in
    // composeApp/androidMain (not :androidApp) — unlike ReminderScheduler it needs no
    // BroadcastReceiver/repository, so there is no circular dependency. get() resolves the
    // AppLogger override above.
    single<CalendarEventLauncher> { AndroidCalendarEventLauncher(AppContextHolder.context, get()) }
}
