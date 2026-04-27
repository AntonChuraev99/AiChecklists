package com.antonchuraev.homesearchchecklist

import android.app.Application
import com.antonchuraev.homesearchchecklist.consent.ConsentManager
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.data.RevenueCatInitializer
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

open class GistiApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Initialize AppContextHolder first (required for DI)
        AppContextHolder.init(this)

        // Apply consent defaults BEFORE Firebase Analytics lazy init.
        // Firebase auto-initializes via google-services ContentProvider,
        // but Analytics events are buffered until setConsent is called.
        initConsent()

        // Initialize Koin if not already started (for widget support)
        val koinAlreadyStarted = GlobalContext.getOrNull() != null
        if (!koinAlreadyStarted) {
            startKoin {
                // Allow definition overrides so instrumented tests (TestApplication)
                // can swap PaywallRepository → FakePaywallRepository for screenshot harness.
                // Production code never overrides bindings.
                allowOverride(true)
                androidLogger()
                androidContext(this@GistiApplication)
                modules(appModule)
            }
            android.util.Log.d("Koin", "startKoin called from GistiApplication.onCreate")
        } else {
            android.util.Log.d("Koin", "startKoin skipped — already started (probably by widget)")
        }

        initRevenueCat()

        // Create notification channel for reminders (required on Android 8+)
        ReminderReceiver.createNotificationChannel(this)

        // Re-schedule active reminders (survives app updates and process death)
        rescheduleReminders()
    }

    private fun rescheduleReminders() {
        applicationScope.launch {
            try {
                val scheduler: ChecklistReminderScheduler =
                    GlobalContext.getOrNull()?.get() ?: return@launch
                scheduler.rescheduleAllActiveReminders()
                scheduler.rescheduleAllActiveRepeats()
            } catch (_: Exception) {
                // Non-critical — reminders will be rescheduled next launch
            }
        }
    }

    /**
     * Apply consent defaults synchronously.
     * Uses SharedPreferences (not DataStore) for instant synchronous read.
     */
    private fun initConsent() {
        consentManager = ConsentManager(this)
        consentManager.applyConsentDefaults()
    }

    /**
     * Initialize RevenueCat for subscription management.
     * Open so that test Application subclass can skip it to avoid
     * creating fake anonymous users in RevenueCat dashboard.
     */
    protected open fun initRevenueCat() {
        RevenueCatInitializer.initialize(
            apiKey = PaywallConfig.ANDROID_API_KEY,
            isDebug = AppBuildConfig.isDebug
        )
    }

    companion object {
        lateinit var consentManager: ConsentManager
            private set
    }
}
