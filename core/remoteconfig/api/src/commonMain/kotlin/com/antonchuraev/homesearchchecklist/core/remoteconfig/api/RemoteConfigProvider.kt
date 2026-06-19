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
     * The exception from the most recent [fetchAndActivate] call, or null if it succeeded.
     *
     * Surfaced into onboarding analytics so a production-only RC fetch failure — e.g. a
     * Google-Play-signed build whose SHA is unknown to App Check / API-key restrictions,
     * which never reproduces on a debug/emulator build — is diagnosable without device logcat.
     * Default null for platforms and test fakes that don't track it.
     */
    fun lastFetchError(): Throwable? = null

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
