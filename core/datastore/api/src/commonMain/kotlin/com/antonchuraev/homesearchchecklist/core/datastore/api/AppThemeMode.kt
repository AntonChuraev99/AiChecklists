package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * User-selectable theme mode for the Gisti application.
 *
 * Stored in DataStore as a string (enum name). Consumers map this to a boolean
 * darkTheme argument for AppTheme:
 *   Light  → darkTheme = false
 *   Dark   → darkTheme = true
 *   System → darkTheme = isSystemInDarkTheme()
 *
 * Lives in core:datastore:api because it is a core preference type consumed
 * by both the root App composable (theme plumbing) and feature:settings.
 */
enum class AppThemeMode {
    /** Always render with the light color scheme. */
    Light,

    /** Always render with the dark color scheme. */
    Dark,

    /** Follow the OS/system dark mode setting (default for new installs). */
    System,
}
