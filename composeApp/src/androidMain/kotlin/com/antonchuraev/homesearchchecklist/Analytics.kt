package com.antonchuraev.homesearchchecklist

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

    override fun setUserId(userId: String) {
        firebase.setUserId(userId)
    }

    override fun screenView(name: String) {
        firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW) {
            param(FirebaseAnalytics.Param.SCREEN_NAME, name)
        }
    }

    override fun event(name: String, params: Map<String, Any>) {
        firebase.logEvent(name) {
            params.forEach { (key, value) ->
                when (value) {
                    is String -> param(key, value)
                    is Long -> param(key, value)
                    is Int -> param(key, value.toLong())
                    is Double -> param(key, value)
                }
            }
        }
    }
}
