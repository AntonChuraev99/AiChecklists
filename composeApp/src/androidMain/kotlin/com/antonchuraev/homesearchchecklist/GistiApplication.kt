package com.antonchuraev.homesearchchecklist

import android.app.Application
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.antonchuraev.homesearchchecklist.consent.ConsentManager
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.antonchuraev.homesearchchecklist.di.appModule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.paywall.data.PaywallConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.data.RevenueCatInitializer
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import com.antonchuraev.homesearchchecklist.push.PushNotificationChannels
import com.antonchuraev.homesearchchecklist.retention.RetentionPrefs
import com.antonchuraev.homesearchchecklist.retention.RetentionPushScheduler
import java.util.Calendar
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
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

        // Sample the notification opt-out state once per start (Android has no channel-disable
        // callback) so we can measure opt-out drift on the promotions channel over time.
        emitPushPermissionState()

        // Record this cold start as an activity sample (behavioral timing) and (re)schedule the
        // LOCAL retention auto-pushes (streak-save / overdue / weekly digest). Idempotent — user-set
        // reminders are handled entirely separately in rescheduleReminders() above and are untouched.
        scheduleRetentionPushes()

        // Arm the one-shot D0->D1 come-back nudge the first time the user creates a checklist.
        scheduleComebackOnFirstChecklist()
    }

    /**
     * Observe [ChecklistRepository.checklists] and arm the one-shot come-back nudge on the FIRST
     * genuine empty -> non-empty transition (the user's very first checklist). This is the day-1
     * lever the recurring/overdue/digest pushes structurally cannot reach.
     *
     * Guards:
     *  - Arms at most once per user lifetime ([RetentionPrefs.isComebackArmed]) — surviving process
     *    death: an armed-or-fired come-back is never re-armed, even if the user deletes everything
     *    and creates a new "first" checklist.
     *  - Requires observing the empty state FIRST, so an existing user whose first emission is
     *    already non-empty (had checklists before this shipped) is never nudged.
     *
     * All best-effort — a failure here never affects app start or user reminders.
     */
    private fun scheduleComebackOnFirstChecklist() {
        applicationScope.launch {
            val koin = GlobalContext.getOrNull() ?: return@launch
            val prefs: RetentionPrefs = koin.getOrNull() ?: return@launch
            val repository: ChecklistRepository = koin.getOrNull() ?: return@launch
            val scheduler: RetentionPushScheduler = koin.getOrNull() ?: return@launch
            val analytics: AnalyticsTracker? = koin.getOrNull()
            val logger: AppLogger? = koin.getOrNull()

            // Already armed once (in this or a previous process) — nothing to observe.
            if (prefs.isComebackArmed()) return@launch

            var sawEmpty = false
            repository.checklists.collect { list ->
                when {
                    list.isEmpty() -> sawEmpty = true
                    // First checklist after we saw the empty day-0 state -> arm, then stop observing.
                    sawEmpty -> {
                        val first = list.minByOrNull { it.id } ?: list.first()
                        val armedAt = System.currentTimeMillis()
                        runCatching {
                            scheduler.scheduleComeback(first.id, first.name)
                            prefs.markComebackScheduled(first.id, armedAt)
                            analytics?.event(
                                AnalyticsEvents.Retention.COMEBACK_SCHEDULED,
                                mapOf(
                                    AnalyticsParams.CHECKLIST_ID to first.id.toString(),
                                    AnalyticsParams.DELAY_HOURS to RetentionPushScheduler.COMEBACK_DELAY_HOURS,
                                ),
                            )
                        }.onFailure { logger?.warning("Retention", "comeback arm failed: ${it.message}") }
                        throw CancellationException("comeback armed")
                    }
                    // Non-empty first emission = existing user -> never nudge; stop observing.
                    else -> throw CancellationException("existing user — no come-back nudge")
                }
            }
        }
    }

    /**
     * Feed a cold-start activity sample into the behavioral-timing histogram, then (re)schedule the
     * two retention alarms at the resolved delivery hour. All best-effort: a failure here never
     * affects app start (or user reminders).
     */
    private fun scheduleRetentionPushes() {
        applicationScope.launch {
            val koin = GlobalContext.getOrNull() ?: return@launch
            try {
                val prefs: RetentionPrefs = koin.get()
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                prefs.recordActiveHour(hour, System.currentTimeMillis())

                val scheduler: RetentionPushScheduler = koin.get()
                scheduler.scheduleAll()
            } catch (e: Exception) {
                koin.getOrNull<AppLogger>()?.warning("Retention", "schedule on start failed: ${e.message}")
            }
        }
    }

    /**
     * Emit [AnalyticsEvents.Push.PERMISSION_STATE] once per start with the app-level notification
     * toggle and the promotions-channel importance (0 = the user muted "Tips & Offers" alone). Runs
     * after channel creation so [NotificationManager.getNotificationChannel] resolves. Null-safe
     * against the widget process where the tracker may not be bound.
     */
    private fun emitPushPermissionState() {
        val tracker: AnalyticsTracker = GlobalContext.getOrNull()?.getOrNull() ?: return
        val notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val promoImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(PushNotificationChannels.PROMOTIONS_CHANNEL_ID)
                ?.importance ?: 0
        } else {
            0
        }
        runCatching {
            tracker.event(
                AnalyticsEvents.Push.PERMISSION_STATE,
                mapOf(
                    AnalyticsParams.NOTIFICATIONS_ENABLED to notificationsEnabled,
                    AnalyticsParams.CHANNEL to PushNotificationChannels.DATA_CHANNEL_PROMO,
                    AnalyticsParams.CHANNEL_IMPORTANCE to promoImportance,
                ),
            )
        }.onFailure { e ->
            GlobalContext.getOrNull()?.getOrNull<AppLogger>()
                ?.warning("PushFcm", "permission_state emit failed: ${e.message}")
        }
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
                // can swap PaywallRepository -> FakePaywallRepository for screenshot harness.
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
