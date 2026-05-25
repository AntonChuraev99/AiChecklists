package com.antonchuraev.homesearchchecklist

import com.antonchuraev.aichecklists.app.BuildConfig
import com.antonchuraev.homesearchchecklist.di.androidAppModule
import com.antonchuraev.homesearchchecklist.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Android-specific Application subclass for the :androidApp module.
 *
 * Extends [GistiApplication] (which lives in :composeApp's androidMain) and
 * adds androidApp-specific Koin bindings:
 *   - widgetModule (WidgetRepository, WidgetStateManager)
 *   - ChecklistReminderScheduler → ReminderScheduler binding
 *
 * Also initializes Amplitude with the build-type API key from BuildConfig
 * (AMPLITUDE_KEY — set per debug/release buildType).
 *
 * The AndroidManifest.xml registers this class as the Application.
 */
class GistiAndroidApplication : GistiApplication() {

    override fun startKoinIfNeeded() {
        // Initialize Amplitude with the build-type API key from BuildConfig.
        // Analytics.initialize() is a no-op if already initialized or key is blank.
        Analytics.initialize(BuildConfig.AMPLITUDE_KEY)

        val koinAlreadyStarted = GlobalContext.getOrNull() != null
        if (!koinAlreadyStarted) {
            startKoin {
                // Allow definition overrides so instrumented tests (TestApplication)
                // can swap PaywallRepository → FakePaywallRepository for screenshot harness.
                allowOverride(true)
                androidLogger()
                androidContext(this@GistiAndroidApplication)
                properties(mapOf("GOOGLE_WEB_CLIENT_ID" to BuildConfig.GOOGLE_WEB_CLIENT_ID))
                // appModule: base KMP module (from composeApp di/)
                // androidAppModule: Android-specific bindings requiring BuildConfig
                modules(appModule, androidAppModule)
            }
            android.util.Log.d("Koin", "startKoin called from GistiAndroidApplication.startKoinIfNeeded")
        } else {
            android.util.Log.d("Koin", "startKoin skipped — already started (probably by widget)")
        }
    }
}
