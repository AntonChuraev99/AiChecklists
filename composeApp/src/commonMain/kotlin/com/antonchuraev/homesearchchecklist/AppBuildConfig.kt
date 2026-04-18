package com.antonchuraev.homesearchchecklist

/**
 * Platform-specific build configuration.
 * Provides access to build-time flags like debug mode and version info.
 */
expect object AppBuildConfig {
    /**
     * Returns true if this is a debug build.
     * Used to conditionally enable debug features like debug menu.
     */
    val isDebug: Boolean

    /**
     * Returns the human-readable version name (e.g. "1.12.3").
     * Empty string if unavailable (safe fallback for iOS release builds).
     */
    val versionName: String
}
