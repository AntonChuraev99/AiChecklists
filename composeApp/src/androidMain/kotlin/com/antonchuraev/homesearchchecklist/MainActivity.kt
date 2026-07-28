package com.antonchuraev.homesearchchecklist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.antonchuraev.homesearchchecklist.consent.ConsentDialog
import com.antonchuraev.homesearchchecklist.desingsystem.theme.isAppLocaleOverrideStale
import com.antonchuraev.homesearchchecklist.desingsystem.theme.reapplyAppLocaleNow
import com.antonchuraev.homesearchchecklist.desingsystem.theme.reassertAppLocale
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsUtm
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.deeplink.GalleryDeepLink
import com.antonchuraev.homesearchchecklist.deeplink.PendingGalleryDeepLink
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import com.antonchuraev.homesearchchecklist.push.PushAnalytics
import com.antonchuraev.homesearchchecklist.retention.RetentionPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {

    private val appNavigator: AppNavigator by inject()
    private val analyticsTracker: AnalyticsTracker by inject()
    private val retentionPrefs: RetentionPrefs by inject()
    private val pendingGalleryDeepLink: PendingGalleryDeepLink by inject()
    private val debugMenuDetector = if (AppBuildConfig.isDebug) {
        DebugMenuDetector { appNavigator.navigateToDebugMenu() }
    } else {
        null
    }

    var pendingChecklistId: Long? = null
        private set

    fun consumePendingChecklistId(): Long? {
        val id = pendingChecklistId
        pendingChecklistId = null
        return id
    }

    /**
     * Pending ACTION_PROCESS_TEXT request captured on cold start (before the NavController is
     * ready). Consumed in the setContent LaunchedEffect once navigation can run.
     */
    private var pendingProcessText: ProcessTextRequest? = null

    private data class ProcessTextRequest(val text: String, val mode: ProcessTextMode)

    private fun extractProcessTextRequest(intent: Intent?): ProcessTextRequest? {
        if (intent?.action != ProcessTextContract.ACTION_PROCESS_TEXT) return null
        val text = intent.getStringExtra(ProcessTextContract.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: return null
        val mode = intent.getStringExtra(ProcessTextContract.EXTRA_MODE)
            ?.let { name -> ProcessTextMode.entries.firstOrNull { it.name == name } }
            ?: ProcessTextMode.CREATE_AI
        return ProcessTextRequest(text, mode)
    }

    private fun routeProcessText(request: ProcessTextRequest) {
        analyticsTracker.event(
            "process_text_entry",
            mapOf("mode" to request.mode.name.lowercase())
        )
        when (request.mode) {
            ProcessTextMode.CREATE_AI ->
                appNavigator.navigateToAnalyzeScreen(initialText = request.text, fillDefault = false)
            ProcessTextMode.FILL_AI ->
                appNavigator.navigateToAddToChecklistPicker(
                    text = request.text,
                    purpose = AddToChecklistPurpose.FILL_AI,
                )
            ProcessTextMode.NEW_CHECKLIST ->
                appNavigator.navigateToCreateChecklistScreen(initialText = request.text)
            ProcessTextMode.ADD_TO_EXISTING ->
                appNavigator.navigateToAddToChecklistPicker(request.text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppContextHolder.init(applicationContext)
        // Initial edge-to-edge (light default) — overridden reactively in setContent once theme is resolved
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Check for deep link in launch intent (cold start from notification)
        extractDeepLinkChecklistId(intent)?.let { id ->
            pendingChecklistId = id
        }
        // Cold-start push tap: emit push_opened once. Guarded on savedInstanceState == null so a
        // config-change recreate (which re-delivers the same launch intent) never double-counts.
        if (savedInstanceState == null) {
            emitPushOpenedIfPresent(intent)
        }

        // Check for an ACTION_PROCESS_TEXT request (cold start from the selection toolbar).
        // Navigation can't run until the NavController is ready, so stash and consume below.
        extractProcessTextRequest(intent)?.let { request ->
            pendingProcessText = request
        }

        // Cold start from a gallery App Link (app.gisti-ai.com/?g=create&template={slug}). The slug
        // is stashed in the retained StateFlow; App.kt's LaunchedEffect consumes it once mounted.
        //
        // Guarded on savedInstanceState == null for the same reason as emitPushOpenedIfPresent
        // above: a config-change recreate (rotation / theme / locale) re-delivers the SAME launch
        // intent, so an unguarded call re-submits an already-handled arrival. Worse than
        // double-counting `gallery_deeplink_opened` — once the first arrival was consumed the
        // holder is empty, so the re-submit emits again and creates a DUPLICATE checklist.
        // PendingGalleryDeepLink's own guards cannot cover this: they scope to one arrival, and a
        // re-submit is indistinguishable from a genuinely new tap at that layer.
        if (savedInstanceState == null) {
            submitGalleryDeepLinkIfPresent(intent)
        }

        setContent {
            // Reactive edge-to-edge: switch status bar icon style when app theme changes
            val themeRepository: ThemeRepository = koinInject()
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.Light)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
                AppThemeMode.System -> systemDark
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            App()

            // Provide Activity reference to GoogleAuthRepository so Credential Manager
            // can display its bottom sheet. Must be called after Koin is initialized.
            val googleAuthRepository: GoogleAuthRepository = koinInject()
            val activity = this@MainActivity
            LaunchedEffect(activity) {
                googleAuthRepository.setActivityContext(activity)
            }

            // Show consent dialog for EEA/UK users on first launch
            val consentManager = GistiApplication.consentManager
            var showConsentDialog by remember {
                mutableStateOf(consentManager.isConsentRequired())
            }
            if (showConsentDialog) {
                ConsentDialog(
                    onAccept = {
                        consentManager.setConsent(granted = true)
                        showConsentDialog = false
                    },
                    onDecline = {
                        consentManager.setConsent(granted = false)
                        showConsentDialog = false
                    }
                )
            }

            // Consume pending deep link after NavController is ready (cold start from notification)
            LaunchedEffect(Unit) {
                consumePendingChecklistId()?.let { id ->
                    appNavigator.navigateToChecklistDetail(id)
                    analyticsTracker.event(AnalyticsEvents.Reminder.NOTIFICATION_TAPPED, mapOf(
                        "checklist_id" to id.toString()
                    ))
                }

                // Consume pending ACTION_PROCESS_TEXT request (cold start from selection toolbar)
                pendingProcessText?.let { request ->
                    pendingProcessText = null
                    routeProcessText(request)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Fallback path for Android < 33 (no system per-app-locale API). External Activities
        // (the Google Play Billing sheet, system pickers, OAuth) reset the process-global
        // Locale.getDefault() back to the device language WITHOUT recreating us (onResume, not
        // onCreate). Re-apply the chosen language so Compose string resources — which on Android
        // follow the global Locale, not LocalConfiguration (Google issue 240191036) — don't stay
        // reverted. The staleness gate keeps a normal resume (no drift) from recomposing the tree.
        //
        // On Android 33+ the language is persisted via LocaleManager (see persistAppLocale in
        // App.kt): the OS keeps our process locale correct across the billing round-trip, so by
        // the time we resume the locale is already restored, isAppLocaleOverrideStale() returns
        // false, and this block is a harmless no-op (the visible flash is gone there entirely).
        if (isAppLocaleOverrideStale()) {
            reapplyAppLocaleNow()
            reassertAppLocale()
        }

        // Behavioral-timing signal: record a foreground as an activity sample (debounced inside
        // RetentionPrefs to at most once per 15 min). Best-effort — never blocks resume.
        recordActiveForRetentionTiming()
    }

    /**
     * Feed a warm-foreground activity sample into the behavioral-timing histogram. Only affects the
     * delivery hour of OUR local auto-pushes; user-set reminders are unaffected. Debounced in prefs.
     */
    private fun recordActiveForRetentionTiming() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val now = System.currentTimeMillis()
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { retentionPrefs.recordActiveHour(hour, now) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A push deep-link reuses ACTION_OPEN_CHECKLIST for navigation, so distinguish it from a
        // LOCAL reminder tap — otherwise one push tap would inflate reminder_notification_tapped too.
        val isPush = PushAnalytics.isPushIntent(intent)
        // Warm start — NavController is already ready
        extractDeepLinkChecklistId(intent)?.let { id ->
            appNavigator.navigateToChecklistDetail(id)
            if (!isPush) {
                analyticsTracker.event(AnalyticsEvents.Reminder.NOTIFICATION_TAPPED, mapOf(
                    "checklist_id" to id.toString()
                ))
            }
        }
        // Warm start from the ACTION_PROCESS_TEXT selection toolbar — navigate immediately.
        extractProcessTextRequest(intent)?.let { request ->
            routeProcessText(request)
        }
        // Warm start from a gallery App Link — submit the slug; App.kt's collector re-fires on the
        // distinct value and invokes the create-from-template UseCase.
        submitGalleryDeepLinkIfPresent(intent)
        // Warm-start push tap: emit push_opened. Cold vs warm are mutually exclusive per tap
        // (onCreate handles cold, onNewIntent handles warm), so this fires exactly once.
        emitPushOpenedIfPresent(intent)
    }

    /**
     * Emit [AnalyticsEvents.Push.OPENED] when this intent came from an FCM push notification tap.
     * Sibling of the reminder [AnalyticsEvents.Reminder.NOTIFICATION_TAPPED] flow, but keyed off
     * the push metadata extras ([PushAnalytics]) rather than the deep-link action — so it fires for
     * BOTH deep-link (checklist_id) and plain re-engagement pushes.
     */
    private fun emitPushOpenedIfPresent(intent: Intent?) {
        if (intent == null || !PushAnalytics.isPushIntent(intent)) return
        analyticsTracker.event(AnalyticsEvents.Push.OPENED, PushAnalytics.paramsFromIntent(intent))
    }

    private fun extractDeepLinkChecklistId(intent: Intent?): Long? {
        if (intent?.action != ReminderReceiver.ACTION_OPEN_CHECKLIST) return null
        val id = intent.getLongExtra(ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID, -1L)
        return if (id != -1L) id else null
    }

    /**
     * App Links entry for the SEO-gallery deep-link
     * `https://app.gisti-ai.com/?g=create&template={slug}`. The manifest claims the whole
     * `app.gisti-ai.com` host (App Links can't match a query string), so we inspect the query
     * params here: only `g=create` with a non-blank `template` slug is a gallery link. The link is
     * stashed in [PendingGalleryDeepLink] (a StateFlow retained until App.kt's collector mounts), so
     * submitting on cold start in onCreate — before navigation is ready — is enough. Non-matching
     * VIEW intents (or a normal launcher launch) return null and do nothing.
     *
     * utm_* is captured alongside the slug (mirrors wasmJs `main.kt`) so a gallery-sourced create
     * is attributable to its landing page on BOTH platforms. This is the CLICK campaign of an
     * already-installed app — distinct from the Play Install Referrer, which attributes the
     * install itself and is delivered by Play only via the encoded `referrer` param.
     * `getQueryParameter` percent-decodes and never throws on a malformed query.
     */
    private fun submitGalleryDeepLinkIfPresent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.getQueryParameter("g") != "create") return
        val slug = uri.getQueryParameter("template")?.takeIf { it.isNotBlank() } ?: return
        pendingGalleryDeepLink.submit(
            GalleryDeepLink(
                slug = slug,
                utm = AnalyticsUtm.from { key -> uri.getQueryParameter(key) },
            )
        )
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (debugMenuDetector?.onKeyDown(keyCode) == true) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private class DebugMenuDetector(
    private val onDebugMenuTriggered: () -> Unit
) {
    private var lastVolumeUpTime: Long = 0
    private var lastVolumeDownTime: Long = 0
    private var sequenceCount: Int = 0
    private var expectingVolumeDown: Boolean = true

    companion object {
        private const val SEQUENCE_TIMEOUT_MS = 500L
        private const val REQUIRED_SEQUENCE_COUNT = 3 // Up-Down-Up or Down-Up-Down
    }

    fun onKeyDown(keyCode: Int): Boolean {
        val currentTime = System.currentTimeMillis()

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (expectingVolumeDown) {
                    // Starting new sequence or continuing after down
                    if (sequenceCount == 0 || currentTime - lastVolumeDownTime < SEQUENCE_TIMEOUT_MS) {
                        sequenceCount++
                        lastVolumeUpTime = currentTime
                        expectingVolumeDown = true

                        if (sequenceCount >= REQUIRED_SEQUENCE_COUNT) {
                            resetSequence()
                            onDebugMenuTriggered()
                            return true
                        }
                    } else {
                        resetSequence()
                        sequenceCount = 1
                        lastVolumeUpTime = currentTime
                    }
                } else {
                    if (currentTime - lastVolumeDownTime < SEQUENCE_TIMEOUT_MS) {
                        sequenceCount++
                        lastVolumeUpTime = currentTime
                        expectingVolumeDown = true

                        if (sequenceCount >= REQUIRED_SEQUENCE_COUNT) {
                            resetSequence()
                            onDebugMenuTriggered()
                            return true
                        }
                    } else {
                        resetSequence()
                        sequenceCount = 1
                        lastVolumeUpTime = currentTime
                        expectingVolumeDown = true
                    }
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentTime - lastVolumeUpTime < SEQUENCE_TIMEOUT_MS && sequenceCount > 0) {
                    sequenceCount++
                    lastVolumeDownTime = currentTime
                    expectingVolumeDown = false

                    if (sequenceCount >= REQUIRED_SEQUENCE_COUNT) {
                        resetSequence()
                        onDebugMenuTriggered()
                        return true
                    }
                } else {
                    resetSequence()
                }
            }
        }
        return false
    }

    private fun resetSequence() {
        sequenceCount = 0
        lastVolumeUpTime = 0
        lastVolumeDownTime = 0
        expectingVolumeDown = true
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val context = LocalContext.current
    AppContextHolder.init(context)
    App()
}