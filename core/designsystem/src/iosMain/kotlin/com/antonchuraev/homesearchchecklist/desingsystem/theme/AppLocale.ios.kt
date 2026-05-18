package com.antonchuraev.homesearchchecklist.desingsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.InternalComposeUiApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults

@OptIn(InternalComposeUiApi::class)
actual object LocalAppLocale {
    private const val LANGUAGES_KEY = "AppleLanguages"

    // Snapshot of the device-default language taken the first time this object
    // is used (before any override). Restored when the user selects System.
    private val default: String = NSLocale.preferredLanguages.first() as String

    // Internal CompositionLocal that carries the BCP-47 tag string through
    // the Compose tree. Named differently to avoid shadowing the outer object.
    private val LocalLocaleOverride = staticCompositionLocalOf { default }

    actual val current: String
        @Composable
        get() = LocalLocaleOverride.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val new = if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANGUAGES_KEY)
            default
        } else {
            NSUserDefaults.standardUserDefaults.setObject(arrayListOf(value), LANGUAGES_KEY)
            value
        }
        return LocalLocaleOverride provides new
    }
}
