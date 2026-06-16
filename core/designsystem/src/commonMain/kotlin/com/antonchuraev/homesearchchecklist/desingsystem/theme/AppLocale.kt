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
 * Bumped on every return to the foreground (Lifecycle ON_RESUME) when the
 * process-global locale has drifted away from [customAppLocale].
 *
 * Why this exists: external Activities — the Google Play Billing sheet, system
 * pickers, OAuth screens — reset the JVM-global `Locale.getDefault()` back to the
 * device locale. The Activity is NOT recreated on return (it's `onResume`, not
 * `onCreate`), so [customAppLocale] never changes and [LocalAppLocale.provides]
 * (which is what re-applies `Locale.setDefault`) never re-runs. The whole UI then
 * silently reverts to the device language until the process restarts.
 *
 * Reading this epoch inside [AppLocaleEnvironment] subscribes that node to it, so
 * calling [reassertAppLocale] recomposes the env → `provides()` re-applies
 * `Locale.setDefault(...)` and the keyed block re-reads string resources under the
 * restored locale. Private so the only mutation path is [reassertAppLocale].
 */
private var localeReassertEpoch: Int by mutableStateOf(0)

/**
 * Force [AppLocaleEnvironment] to re-apply the chosen locale override.
 *
 * Call from a Lifecycle ON_RESUME observer at the app root (see `App.kt`). Gate the
 * call with [isAppLocaleOverrideStale] so it only fires when the global locale has
 * actually drifted — an unconditional bump would recompose the entire app content
 * (the whole `NavDisplay`) on every foreground return, risking flicker / lost
 * scroll state.
 */
fun reassertAppLocale() {
    localeReassertEpoch++
}

/**
 * True when the process-global locale has drifted from [customAppLocale] and a
 * re-assert is needed. Cheap, side-effect-free check called from the ON_RESUME
 * observer before [reassertAppLocale].
 *
 * Android compares `Locale.getDefault()` against the chosen override (and against
 * the captured device default when in System mode). iOS / wasmJs return `false`:
 * their string-resource resolution is not driven by a JVM-global `Locale.getDefault()`
 * and they are not affected by Google Play Billing, so there is nothing to re-assert.
 */
expect fun isAppLocaleOverrideStale(): Boolean

/**
 * Synchronously re-applies [customAppLocale] to the platform-global locale, OUTSIDE
 * composition. Called from Android's `MainActivity.onResume` BEFORE Compose draws the
 * next frame, so returning from an external Activity that reset the process locale
 * (the Google Play Billing sheet) does not flash the device language for a frame —
 * the Compose-side [reassertAppLocale] alone would fire one frame too late. Pair the
 * two: [reapplyAppLocaleNow] kills the flash, [reassertAppLocale] recomposes string
 * resources. No-op on iOS / wasmJs (their locale isn't a mutable JVM global).
 */
expect fun reapplyAppLocaleNow()

/**
 * Persists [tag] as the platform's **per-app** locale, so it survives external
 * processes resetting the JVM-global `Locale.getDefault()` (the Google Play Billing
 * sheet). Unlike [reapplyAppLocaleNow] — which reactively restores the global locale
 * AFTER an external reset, one frame late — this proactively hands the locale to the
 * OS, which then keeps the process locale correct across the whole billing round-trip.
 * That is what removes the visible `ru → en` flash, not just the "stuck until restart" bug.
 *
 * Platform behaviour:
 * - **Android API 33+ (Tiramisu):** `LocaleManager.setApplicationLocales`. The system
 *   owns the per-app locale; the billing sheet's process can no longer perturb ours, so
 *   the flash disappears. `null` (System mode) → empty `LocaleList` → follow device.
 * - **Android < 33:** no-op — there is no system per-app-locale API; the existing
 *   [reapplyAppLocaleNow] + [reassertAppLocale] onResume path stays as the fallback
 *   (the residual one-frame flash there is an accepted, deferred limitation).
 * - **iOS / wasmJs:** no-op — their locale already persists through `provides()`
 *   (`NSUserDefaults["AppleLanguages"]` / `globalThis.__customLocale`) and no Google
 *   Play Billing sheet exists to perturb it.
 *
 * Call from the same place that writes [customAppLocale] (the root `LaunchedEffect`
 * keyed on the chosen [AppLanguage]); the two are complementary — [customAppLocale]
 * drives Compose immediately, [persistAppLocale] makes the choice durable in the OS.
 *
 * Note (Android 33+): when the value actually changes, the system recreates the
 * Activity (standard per-app-locale behaviour). The actual no-ops when the locale is
 * unchanged, so app start (DataStore value already applied) does not trigger a recreate.
 */
expect fun persistAppLocale(tag: String?)

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
    // Reading the epoch here subscribes this node to it: a reassertAppLocale() bump
    // recomposes the env → CompositionLocalProvider re-runs LocalAppLocale.provides()
    // (re-applying Locale.setDefault on Android) AND the keyed block below re-reads
    // string resources under the restored locale. See [reassertAppLocale].
    val epoch = localeReassertEpoch
    CompositionLocalProvider(LocalAppLocale provides customAppLocale) {
        key(customAppLocale, epoch) {
            content()
        }
    }
}
