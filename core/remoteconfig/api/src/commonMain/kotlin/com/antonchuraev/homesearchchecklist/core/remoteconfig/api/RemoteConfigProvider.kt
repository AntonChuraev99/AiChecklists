package com.antonchuraev.homesearchchecklist.core.remoteconfig.api

/**
 * Provider interface for remote configuration values.
 *
 * Android: Firebase Remote Config
 * iOS: Stub implementation (future: Firebase iOS via CocoaPods)
 */
interface RemoteConfigProvider {

    /**
     * Fetches the latest remote config values from the server.
     * @return true if fetch was successful, false otherwise
     */
    suspend fun fetchAndActivate(): Boolean

    /**
     * Gets a boolean feature flag value.
     */
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean

    /**
     * Gets a string configuration value.
     */
    fun getString(key: String, defaultValue: String = ""): String

    /**
     * Gets a long configuration value.
     */
    fun getLong(key: String, defaultValue: Long = 0L): Long
}
