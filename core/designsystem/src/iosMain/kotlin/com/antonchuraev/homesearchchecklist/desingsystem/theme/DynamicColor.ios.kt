package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/**
 * iOS has no OS-level API to extract a palette from the user's wallpaper,
 * so dynamic color is not supported — callers fall back to the static
 * [LightColorScheme] / [DarkColorScheme].
 */
@Composable
actual fun rememberDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null

actual fun supportsDynamicColor(): Boolean = false
