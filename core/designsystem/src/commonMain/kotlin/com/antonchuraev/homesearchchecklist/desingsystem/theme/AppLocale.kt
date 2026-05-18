package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Single source of truth for the currently overridden UI locale.
 *
 * Top-level [mutableStateOf] — read by the platform-specific [LocalAppLocale]
 * `provides` implementations. Write to it ONLY from a [LaunchedEffect] (not from
 * inside composition) to avoid snapshot loops.
 *
 *   null  → restore device default (System mode)
 *   "en"  → English
 *   "ru"  → Russian
 *
 * Pattern source: official Compose Multiplatform docs,
 *   https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html
 */
var customAppLocale: String? by mutableStateOf<String?>(null)

/**
 * Platform-specific override that flips:
 *   1. Compose Resources' `Locale.current` (drives `stringResource()`)
 *   2. The platform-level locale APIs (so `Locale.getDefault()` on JVM, etc.)
 *
 * Android: mutates `LocalConfiguration` + calls `Locale.setDefault`.
 * iOS:     writes `NSUserDefaults["AppleLanguages"]` + provides `LocalLocale`.
 * wasmJs:  sets `window.__customLocale` (read by an index.html shim that
 *          patches `Navigator.languages`).
 *
 * RTL note: this env does NOT flip `LocalLayoutDirection`. We currently ship
 * only EN/RU (both LTR). When adding Arabic/Hebrew, extend [AppLocaleEnvironment]
 * to also `provides LocalLayoutDirection.Rtl`.
 */
expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Wraps [content] in a locale-aware sub-tree. Whenever [customAppLocale] changes,
 * the [key] block forces a recomposition of the entire content so that
 * Compose Resources re-reads the resource cache under the new qualifier.
 *
 * Place INSIDE `AppTheme` (so theme state survives the locale change) but
 * OUTSIDE `NavHost` (so back stack survives — Compose Navigation 2.9+ handles
 * route key changes gracefully).
 */
@Composable
fun AppLocaleEnvironment(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAppLocale provides customAppLocale) {
        key(customAppLocale) {
            content()
        }
    }
}
