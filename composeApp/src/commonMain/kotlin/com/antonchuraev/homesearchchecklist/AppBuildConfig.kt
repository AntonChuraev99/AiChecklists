package com.antonchuraev.homesearchchecklist

/**
 * Platform-specific build configuration.
 * Provides access to build-time flags like debug mode.
 */
expect object AppBuildConfig {
    /**
     * Returns true if this is a debug build.
     * Used to conditionally enable debug features like debug menu.
     */
    val isDebug: Boolean
}
