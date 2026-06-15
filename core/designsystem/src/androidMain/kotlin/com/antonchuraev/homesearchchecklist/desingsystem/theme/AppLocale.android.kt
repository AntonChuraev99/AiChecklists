package com.antonchuraev.homesearchchecklist.desingsystem.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * Android actual for [LocalAppLocale].
 *
 * On Android, locale override requires three coordinated steps:
 *   1. [Locale.setDefault] — affects `java.text.*` formatters, `Locale.getDefault()` callers.
 *   2. `Configuration.setLocale` + `resources.updateConfiguration` — flips the Resources locale
 *      that Android UI components (XML views, legacy APIs) read.
 *   3. `CompositionLocalProvider(LocalConfiguration provides …)` — triggers Compose to re-read
 *      `stringResource()` under the new locale qualifier.
 *
 * The [default] field captures the device locale on first use, so [System] mode ([value] = null)
 * can restore it reliably without querying the system again.
 *
 * Source: Compose Multiplatform official docs, Android section —
 *   https://kotlinlang.org/docs/multiplatform/compose-resource-environment.html
 *
 * Note: `Locale(String)` constructor is deprecated on API 26+ in favour of
 * `Locale.forLanguageTag(String)`. We keep the verbatim form from the official sample
 * since EN / RU BCP-47 two-letter codes map 1:1 to the legacy constructor.
 */
actual object LocalAppLocale {
    private var default: Locale? = null

    /**
     * The device-default locale, captured on first use (before any override) so
     * System mode ([value] = null) restores it reliably. Exposed via this accessor
     * so [isAppLocaleOverrideStale] can read it without duplicating the capture —
     * single source of truth, no second `default` field.
     */
    internal fun deviceDefault(): Locale {
        return default ?: Locale.getDefault().also { default = it }
    }

    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        val deviceDefault = deviceDefault()
        @Suppress("DEPRECATION")
        val new = value?.let { Locale(it) } ?: deviceDefault
        Locale.setDefault(new)
        @Suppress("DEPRECATION")
        configuration.setLocale(new)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration provides Configuration(configuration)
    }
}

/**
 * Android: the override is stale when the chosen language no longer matches the
 * process-global `Locale.getDefault()`. External Activities (Google Play Billing
 * sheet, system pickers, OAuth) reset that global without recreating our Activity,
 * so on ON_RESUME we detect the drift here and trigger [reassertAppLocale].
 *
 * - Explicit language ("en"/"ru"): stale when `getDefault().language` differs.
 * - System mode (`customAppLocale == null`): stale when `getDefault()` no longer
 *   equals the captured device default (billing can also perturb this).
 *
 * Compares only the language subtag for explicit overrides because we set the
 * locale via `Locale("en")` (no region), while the device default may carry a
 * region (e.g. `en_US`) — comparing full locales would report a false drift.
 */
actual fun isAppLocaleOverrideStale(): Boolean {
    val override = customAppLocale
    val current = Locale.getDefault()
    val stale = if (override == null) {
        current != LocalAppLocale.deviceDefault()
    } else {
        !current.language.equals(override, ignoreCase = true)
    }
    return stale
}

/**
 * Synchronously re-applies the chosen override to the JVM-global default locale.
 * Called from `MainActivity.onResume` (before the next Compose frame) so a foreground
 * return after the Google Play Billing sheet reset `Locale.getDefault()` does not flash
 * the device language. Only touches the global Locale here — the follow-up
 * [reassertAppLocale] recomposes [AppLocaleEnvironment], whose `provides()` re-syncs the
 * Configuration / `LocalConfiguration`.
 */
actual fun reapplyAppLocaleNow() {
    @Suppress("DEPRECATION")
    val target = customAppLocale?.let { Locale(it) } ?: LocalAppLocale.deviceDefault()
    Locale.setDefault(target)
}
