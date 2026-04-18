package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * Returns the platform-provided Material You "dynamic color" scheme derived from
 * the user's wallpaper, or `null` if the platform does not support it.
 *
 * Supported:
 *   - Android 12+ (API 31) — delegates to `dynamicLightColorScheme` / `dynamicDarkColorScheme`.
 *
 * Unsupported (returns `null`):
 *   - Android < 12
 *   - iOS (no OS-level wallpaper extraction API)
 *
 * Callers should fall back to the static [LightColorScheme] / [DarkColorScheme]
 * when this returns `null`.
 */
@Composable
expect fun rememberDynamicColorScheme(darkTheme: Boolean): ColorScheme?

/**
 * Whether the current platform can produce a dynamic color scheme.
 *
 * Use this to decide whether to show a "Dynamic color" toggle in settings —
 * hiding the option entirely on unsupported platforms avoids presenting a
 * disabled control the user cannot act on.
 */
expect fun supportsDynamicColor(): Boolean
