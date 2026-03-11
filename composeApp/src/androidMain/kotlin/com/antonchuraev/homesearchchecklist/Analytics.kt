package com.antonchuraev.homesearchchecklist

import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.amplitude.core.events.Identify

import com.antonchuraev.aichecklists.BuildConfig
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

object Analytics : AnalyticsTracker {

    private val firebase by lazy {
        FirebaseAnalytics.getInstance(AppContextHolder.context).apply {
            setAnalyticsCollectionEnabled(!AppBuildConfig.isDebug)
        }
    }

    // Nullable Amplitude - handles empty API key gracefully
    // Debug builds use a separate Amplitude project (AMPLITUDE_KEY_DEBUG)
    // so debug events go to sandbox, not production
    private val amplitude: Amplitude? by lazy {
        val key = BuildConfig.AMPLITUDE_KEY
        if (key.isBlank()) return@lazy null

        Amplitude(
            Configuration(
                apiKey = key,
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
