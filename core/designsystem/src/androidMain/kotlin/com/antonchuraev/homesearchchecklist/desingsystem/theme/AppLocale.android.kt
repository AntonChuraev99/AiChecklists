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

    actual val current: String
        @Composable get() = Locale.getDefault().toString()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val configuration = LocalConfiguration.current
        if (default == null) {
            default = Locale.getDefault()
        }
        @Suppress("DEPRECATION")
        val new = value?.let { Locale(it) } ?: default!!
        Locale.setDefault(new)
        @Suppress("DEPRECATION")
        configuration.setLocale(new)
        val resources = LocalContext.current.resources
        @Suppress("DEPRECATION")
        resources.updateConfiguration(configuration, resources.displayMetrics)
        return LocalConfiguration provides Configuration(configuration)
    }
}
