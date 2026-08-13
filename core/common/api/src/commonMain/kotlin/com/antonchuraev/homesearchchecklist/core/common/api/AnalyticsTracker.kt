package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Minimal analytics interface for cross-module access.
 * Android implementation uses Firebase Analytics.
 * iOS is a no-op stub.
 */
interface AnalyticsTracker {
    fun setUserId(userId: String)
    fun setUserProperties(properties: Map<String, Any>)

    /**
     * Publishes properties whose FIRST value must survive every later write
     * (Amplitude `Identify.setOnce`, Firebase has no equivalent — plain `setUserProperty` there).
     *
     * Needed for install-scoped facts such as `install_date`: a plain `set` from a later app start
     * silently rewrites the cohort a user belongs to, and nothing in the resulting report reveals
     * that the date moved.
     *
     * SEAM ONLY — declared so the once-semantics is expressible; the default keeps every existing
     * implementation compiling and at least publishing the value. Platform implementations that
     * have a real set-once primitive MUST override it (see the handoff contract).
     */
    fun setUserPropertiesOnce(properties: Map<String, Any>) = setUserProperties(properties)

    fun screenView(name: String)
    fun event(name: String, params: Map<String, Any> = emptyMap())
}
