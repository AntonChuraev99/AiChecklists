@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.intl.Locale

/**
 * Writes the user-selected locale into a global JS slot read by the
 * Navigator.languages shim in index.html, which overrides what Compose
 * Resources resolves via navigator.language / navigator.languages.
 *
 * null → shim falls back to original browser languages.
 *
 * Why not `external var`: Kotlin/Wasm compiles composeApp.js as an ESM
 * module (strict mode). A top-level `external var` becomes a bare-name
 * assignment in the JS binding, which throws ReferenceError unless the
 * name is declared in module scope. `js("globalThis.X = v")` is the
 * idiomatic Kotlin/Wasm bridge for writing to a global — same pattern
 * used by core/remoteconfig for `__rcFetchPromise`.
 */
private fun writeCustomLocale(value: String?) {
    if (value != null) setCustomLocaleGlobal(value) else clearCustomLocaleGlobal()
}

private fun setCustomLocaleGlobal(value: String) {
    js("globalThis.__customLocale = value")
}

private fun clearCustomLocaleGlobal() {
    js("globalThis.__customLocale = null")
}

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
        writeCustomLocale(value)
        val effective = value ?: deviceDefaultLocale
        return LocalAppLocaleInternal provides effective
    }
}

/**
 * wasmJs: nothing to re-assert on foreground return. The locale flows through a
 * Compose [staticCompositionLocalOf] + a `globalThis.__customLocale` slot read by
 * the Navigator.languages shim — not a mutable JVM-global `Locale.getDefault()` —
 * and there is no Google Play Billing sheet on the web. Always not stale.
 */
actual fun isAppLocaleOverrideStale(): Boolean = false

/**
 * wasmJs: no JVM-global locale to re-apply (it flows through a Compose CompositionLocal
 * + a globalThis slot), and no Google Play Billing sheet on the web. No-op.
 */
actual fun reapplyAppLocaleNow() {}

/**
 * wasmJs: the locale already persists via the `globalThis.__customLocale` slot (written in
 * [LocalAppLocale.provides]) and there is no Google Play Billing sheet on the web. No-op.
 */
actual fun persistAppLocale(tag: String?) {}
