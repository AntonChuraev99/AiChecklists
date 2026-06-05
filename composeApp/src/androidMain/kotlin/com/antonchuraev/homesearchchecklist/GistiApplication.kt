package com.antonchuraev.homesearchchecklist

import android.app.Application
import com.antonchuraev.homesearchchecklist.consent.ConsentManager
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.data.RevenueCatInitializer
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import com.antonchuraev.homesearchchecklist.push.PushNotificationChannels
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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

        // Initialize Koin — subclasses (GistiAndroidApplication in :androidApp) can
        // override startKoinIfNeeded() to add androidApp-specific modules.
        startKoinIfNeeded()

        initRevenueCat()

        // Create notification channels (required on Android 8+).
        // Channels MUST exist before any notification is posted — creating them lazily in
        // onMessageReceived would silently drop the first push.
        ReminderReceiver.createNotificationChannel(this)
        PushNotificationChannels.createAll(this)

        // Re-schedule active reminders (survives app updates and process death)
        rescheduleReminders()

        // Register the FCM token + bump lastActiveAt for the re-engagement campaign.
        registerPushToken()
    }

    /**
     * Initialize and start Koin with the base appModule.
     *
     * Override in subclasses (e.g. GistiAndroidApplication in :androidApp) to
     * provide additional modules that depend on androidApp-specific bindings
     * (widget, reminder scheduler) or to change startKoin parameters.
     *
     * Default implementation loads only appModule — suitable for widget process,
     * test overrides, or standalone library usage.
     */
    protected open fun startKoinIfNeeded() {
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
     * On app start, push the current FCM token and refresh lastActiveAt to Firestore so the
     * re-engagement campaign always has a fresh token + recency signal for this user.
     *
     * No-ops gracefully when the user is not signed in (the repository logs a warning) — the
     * token is re-registered on a later start once the user authenticates. Token fetch failures
     * are non-critical: onNewToken still fires when FCM rotates the token.
     */
    private fun registerPushToken() {
        applicationScope.launch {
            val repository: PushTokenRepository =
                GlobalContext.getOrNull()?.getOrNull() ?: return@launch
            // Always bump activity, even if the token fetch fails.
            repository.touchLastActive()
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                repository.registerToken(token)
            } catch (e: Exception) {
                GlobalContext.getOrNull()?.getOrNull<AppLogger>()
                    ?.warning("PushFcm", "App-start FCM token fetch failed: ${e.message}")
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
