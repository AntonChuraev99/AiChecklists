package com.antonchuraev.homesearchchecklist

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
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
     * Initialize Amplitude with the API key.
     * Must be called after AppContextHolder.init() in GistiAndroidApplication.
     * Safe to call multiple times (no-op if already initialized with same key).
     */
    fun initialize(amplitudeKey: String) {
        if (amplitudeKey.isBlank()) return
        if (_amplitude != null) return  // already initialized
        _amplitude = Amplitude(
            Configuration(
                apiKey = amplitudeKey,
                context = AppContextHolder.context,
                trackingSessionEvents = true
            )
        )
    }

    override fun setUserId(userId: String) {
        firebase.setUserId(userId)
        amplitude?.setUserId(userId)
    }

    override fun setUserProperties(properties: Map<String, Any>) {
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

    override fun event(name: String, params: Map<String, Any>) {
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
            eventProperties = params
        )
    }
}
