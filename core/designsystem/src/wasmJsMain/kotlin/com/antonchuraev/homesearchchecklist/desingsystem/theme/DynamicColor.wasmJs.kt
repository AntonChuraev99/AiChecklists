package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Dynamic color is not supported on the web target. */
@Composable
actual fun rememberDynamicColorScheme(darkTheme: Boolean): ColorScheme? = null

/** Dynamic color is not supported on the web target. */
actual fun supportsDynamicColor(): Boolean = false
