package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Exposes the currently active app theme (not system theme). Prefer this over
 * `isSystemInDarkTheme()` when a component needs to adapt its appearance —
 * user-selected theme may differ from system setting.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * Gisti application theme.
 *
 * @param darkTheme Whether to apply the dark color scheme. Default is false (light).
 *   The caller is responsible for reading the user's theme preference from DataStore
 *   and passing the correct value. Call-sites that omit this parameter keep light theme
 *   behavior unchanged — no breaking change.
 * @param content The composable content to theme.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
