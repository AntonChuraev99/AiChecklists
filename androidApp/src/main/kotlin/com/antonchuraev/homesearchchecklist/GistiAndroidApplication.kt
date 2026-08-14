package com.antonchuraev.homesearchchecklist

import androidx.work.Configuration
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
 * adds androidApp-specific Koin bindings via [androidAppModule]:
 *   - WidgetRepository, WidgetStateManager (home-screen widget)
 *   - ChecklistReminderScheduler → ReminderScheduler binding
 *
 * Also initializes Amplitude with the build-type API key from BuildConfig
 * (AMPLITUDE_KEY — set per debug/release buildType).
 *
 * The AndroidManifest.xml registers this class as the Application.
 *
 * Also supplies WorkManager's [Configuration] on demand — see [workManagerConfiguration].
 */
open class GistiAndroidApplication : GistiApplication(), Configuration.Provider {

    /**
     * WorkManager's on-demand initialization hook.
     *
     * AndroidManifest.xml removes `androidx.work.WorkManagerInitializer` from the
     * `androidx.startup` provider, so WorkManager is no longer built during
     * `ContentProvider.onCreate()` on every process start (that is where the 2026-08-13
     * `JobScheduler.forNamespace` NoSuchMethodError killed 1.19.1 before any app code ran).
     * Without this property the first `WorkManager.getInstance(context)` would instead throw
     * IllegalStateException — one crash traded for another.
     *
     * Returns the plain default configuration, byte-for-byte what `WorkManagerInitializer`
     * built (`Configuration.Builder().build()`); the only change is *when* it is built.
     *
     * This is load-bearing, not defensive. No app code enqueues work, but Glance does:
     * `GlanceAppWidget.update()` runs through `SessionManager`, and
     * `SessionManagerImpl.startSession()` calls `WorkManager.getInstance(context)` +
     * `enqueueUniqueWork(...)` on every widget session (verified against glance 1.1.1
     * bytecode). So the home-screen widget IS the path that builds WorkManager now — lazily,
     * on first widget update, instead of on every process start.
     *
     * Scope of the fix, stated plainly: on the broken ROM the app now LAUNCHES, but the widget
     * still will not. The same NoSuchMethodError moves to whatever first builds WorkManager.
     * Glance's own broadcast path catches it (`GlanceAppWidgetReceiver` wraps in
     * `catch Throwable -> logException`), but two app call sites do not —
     * `widget/ToggleItemActivity.kt` (`updateAll` after a tap) and
     * `widget/config/WidgetConfigActivity.kt` (`update` when adding the widget) — so on that ROM
     * those two paths still take the process down. Deliberately left alone: guarding them is a
     * separate change, and this one stays a single fix to the startup path.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

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
