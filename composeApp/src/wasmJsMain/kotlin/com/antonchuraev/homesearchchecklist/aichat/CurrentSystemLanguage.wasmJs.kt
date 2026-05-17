package com.antonchuraev.homesearchchecklist.aichat

/**
 * wasmJs: browser navigator.language returns BCP-47 like "ru-RU", "en-US".
 * We take the first 2 chars to match [ChatLocale.fromLanguageTag] expectations.
 *
 * Fallback to "en" if the API is unavailable.
 */
actual fun currentSystemLanguage(): String {
    return runCatching { js("navigator.language") as String }
        .getOrElse { "en" }
        .take(2)
        .lowercase()
}
