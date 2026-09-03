package com.antonchuraev.homesearchchecklist

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.android.autocaptureOptions
import com.amplitude.core.events.EventOptions
import com.amplitude.core.events.Identify

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

/**
 * Analytics singleton.
 *
 * NOTE: The Amplitude API key is NOT available here from BuildConfig (which
 * only exists in :androidApp). The key is injected at runtime by calling
 * [Analytics.initialize] from GistiAndroidApplication after Koin starts.
 *
 * Firebase is lazy-initialized from AppContextHolder.context.
 * Amplitude is null until [initialize] is called with a non-blank key.
 */
object Analytics : AnalyticsTracker {

    private val firebase by lazy {
        FirebaseAnalytics.getInstance(AppContextHolder.context).apply {
            // Collection is ON for ALL build types, including debug, so debug /
            // internal-testing builds can validate purchase & conversion events
            // (e.g. the GA4 `purchase` revenue event) end-to-end on a real device
            // via Firebase DebugView. Trade-off: debug builds now send to the
            // production Firebase/GA4 project. Keep dev noise out of reports with
            // GA4 → Admin → Data Settings → "Filter out developer traffic"
            // (debug_mode). Amplitude debug already routes to a separate project
            // via AMPLITUDE_DEBUG_KEY, so only Firebase/GA4 is affected here.
            setAnalyticsCollectionEnabled(true)
        }
    }

    // Nullable Amplitude — initialized lazily after key is injected
    private var _amplitude: Amplitude? = null
    private val amplitude: Amplitude?
        get() = _amplitude

    /**
     * Amplitude's sentinel for "this event belongs to no session".
     *
     * Mirrors `com.amplitude.android.Timeline.DEFAULT_SESSION_ID`, which is private to the SDK.
     * `Timeline.processEvent` compares against it BEFORE deciding whether to open a session:
     * `if (event.sessionId != DEFAULT_SESSION_ID && ownsSessions()) { startNewSessionIfNeeded(...) }`
     * — so an event carrying this value is delivered but never mints `session_start`.
     *
     * Honouring it requires SDK >= 1.26.1 (PR #365); on older versions an out-of-session event
     * still triggered a session and this whole mechanism would be a silent no-op.
     */
    private const val OUT_OF_SESSION_ID = -1L

    /**
     * Initialize Amplitude with the API key.
     * Must be called after AppContextHolder.init() in GistiAndroidApplication.
     * Safe to call multiple times (no-op if already initialized with same key).
     *
     * Construction MUST stay this early (Application.onCreate). The SDK registers its
     * ActivityLifecycleCallbacks when the instance is built, so a later construction misses the
     * foreground transition and never opens the real session — verified and disproven as a fix, see
     * `docs/todos/2026-07-29-amplitude-no-init-in-background-processes.md`.
     *
     * `autocapture` replaces the deprecated `trackingSessionEvents` flag: passing the flag selects a
     * @Deprecated secondary constructor, and on the SDK version this app used to pin (1.22.4) SESSIONS
     * was NOT in `REQUIRES_ACTIVITY_CALLBACKS`, so a SESSIONS-only config skipped
     * `registerActivityLifecycleCallbacks` altogether — the SDK's `foreground` flag stayed false for
     * the whole process life and sessions were cut by timeout even while the user was on screen.
     * Fixed upstream in 1.25.2 (PR #355), which always registers the callbacks.
     */
    fun initialize(amplitudeKey: String) {
        if (amplitudeKey.isBlank()) return
        if (_amplitude != null) return  // already initialized
        _amplitude = Amplitude(
            Configuration(
                apiKey = amplitudeKey,
                context = AppContextHolder.context,
                autocapture = autocaptureOptions { +sessions },
            )
        )
    }

    /** Fresh options each call — [EventOptions] is mutable and the SDK may retain it per event. */
    private fun outOfSessionOptions() = EventOptions().apply { sessionId = OUT_OF_SESSION_ID }

    override fun setUserId(userId: String) {
        firebase.setUserId(userId)
        amplitude?.setUserId(userId)
    }

    override fun setUserProperties(properties: Map<String, Any>) =
        publishUserProperties(properties, options = null)

    /**
     * Same payload as [setUserProperties], but the `$identify` event is stamped out-of-session so a
     * background process wake cannot mint a phantom `session_start`. See [eventOutOfSession].
     */
    override fun setUserPropertiesOutOfSession(properties: Map<String, Any>) =
        publishUserProperties(properties, options = outOfSessionOptions())

    private fun publishUserProperties(properties: Map<String, Any>, options: EventOptions?) {
        // Firebase — one call per property (only accepts String values)
        properties.forEach { (name, value) ->
            firebase.setUserProperty(name, value.toString())
        }
        // Amplitude — single Identify call for all properties (batch)
        amplitude?.let { amp ->
            val identify = Identify()
            properties.forEach { (name, value) ->
                when (value) {
                    is String -> identify.set(name, value)
                    is Int -> identify.set(name, value)
                    is Long -> identify.set(name, value)
                    is Double -> identify.set(name, value)
                    is Boolean -> identify.set(name, value)
                    else -> identify.set(name, value.toString())
                }
            }
            amp.identify(identify, options)
        }
    }

    /**
     * Set-ONCE semantics: the first value written for a property survives every later write.
     *
     * MUST stay overridden. [AnalyticsTracker.setUserPropertiesOnce] has a default body that
     * delegates to [setUserProperties], so dropping this override compiles, keeps every test green,
     * and silently degrades install-scoped facts (`install_date`) to a plain `set` that any later
     * app start can rewrite — moving the user into a different acquisition cohort with nothing in
     * the report to reveal the move.
     *
     * Firebase has no set-once primitive (`setUserProperty` is always a plain set), so the once-ness
     * is guaranteed on the Amplitude side only — which is where the cohort analysis lives.
     */
    override fun setUserPropertiesOnce(properties: Map<String, Any>) {
        // Firebase — plain set, one call per property (only accepts String values).
        properties.forEach { (name, value) ->
            firebase.setUserProperty(name, value.toString())
        }
        // Amplitude — a single Identify carrying setOnce for every property.
        amplitude?.let { amp ->
            val identify = Identify()
            properties.forEach { (name, value) ->
                when (value) {
                    is String -> identify.setOnce(name, value)
                    is Int -> identify.setOnce(name, value)
                    is Long -> identify.setOnce(name, value)
                    is Double -> identify.setOnce(name, value)
                    is Boolean -> identify.setOnce(name, value)
                    else -> identify.setOnce(name, value.toString())
                }
            }
            amp.identify(identify)
        }
    }

    override fun screenView(name: String) {
        firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, name)
        }
        amplitude?.track(
            eventType = "screen_view",
            eventProperties = mapOf("screen_name" to name)
        )
    }

    override fun event(name: String, params: Map<String, Any>) =
        publishEvent(name, params, options = null)

    /**
     * Delivers the event exactly like [event] does, but stamped with Amplitude's out-of-session
     * sentinel so it neither opens nor extends a session.
     *
     * Firebase is unaffected — it is initialized separately, mints no sessions of its own, and keeps
     * receiving the event unchanged.
     */
    override fun eventOutOfSession(name: String, params: Map<String, Any>) =
        publishEvent(name, params, options = outOfSessionOptions())

    private fun publishEvent(name: String, params: Map<String, Any>, options: EventOptions?) {
        // Firebase
        firebase.logEvent(name) {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Double -> param(key, value)
                    is Boolean -> param(key, value.toString())
                }
            }
        }
        // Amplitude
        amplitude?.track(
            eventType = name,
            eventProperties = params,
            options = options,
        )
    }
}
