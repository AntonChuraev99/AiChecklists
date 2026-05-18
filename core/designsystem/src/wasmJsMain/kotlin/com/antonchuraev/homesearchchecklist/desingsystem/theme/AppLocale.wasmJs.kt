@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * External binding to the window-level custom locale slot.
 *
 * Written by [LocalAppLocale.provides] when the user picks a language.
 * Read by the Navigator.languages shim in index.html to override the
 * locale that Compose Resources resolves (via navigator.language /
 * navigator.languages).
 *
 * null / undefined → shim falls back to original browser languages.
 */
private external var __customLocale: String?

/**
 * Snapshot of the device's default language tag taken on first use
 * (before any override is applied). Restored when the user selects System.
 *
 * Mirrors the iOS actual pattern: capture once, restore on null.
 * Uses Locale.current which reads navigator.language at Kotlin startup,
 * before any runtime override is applied.
 */
private val deviceDefaultLocale: String = Locale.current.toLanguageTag()

private val LocalAppLocaleInternal = staticCompositionLocalOf { deviceDefaultLocale }

actual object LocalAppLocale {

    actual val current: String
        @Composable
        get() = LocalAppLocaleInternal.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val effective = if (value != null) {
            __customLocale = value
            value
        } else {
            __customLocale = null
            deviceDefaultLocale
        }
        return LocalAppLocaleInternal provides effective
    }
}
