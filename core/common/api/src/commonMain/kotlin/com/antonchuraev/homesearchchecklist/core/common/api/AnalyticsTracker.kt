package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Minimal analytics interface for cross-module access.
 * Android implementation uses Firebase Analytics.
 * iOS is a no-op stub.
 */
interface AnalyticsTracker {
    fun setUserId(userId: String)
    fun setUserProperties(properties: Map<String, Any>)
    fun screenView(name: String)
    fun event(name: String, params: Map<String, Any> = emptyMap())
}
