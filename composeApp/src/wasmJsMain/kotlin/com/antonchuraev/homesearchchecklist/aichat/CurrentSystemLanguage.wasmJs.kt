package com.antonchuraev.homesearchchecklist.aichat

// Kotlin/Wasm requires `js(...)` calls to be a single expression in the body of a
// top-level function or a property initializer — they cannot live inside lambdas.
private fun navigatorLanguageRaw(): String = js("navigator.language")

/**
 * wasmJs: browser navigator.language returns BCP-47 like "ru-RU", "en-US".
 * We take the first 2 chars to match [ChatLocale.fromLanguageTag] expectations.
 *
 * Fallback to "en" if the API is unavailable.
 */
actual fun currentSystemLanguage(): String {
    return runCatching { navigatorLanguageRaw() }
        .getOrElse { "en" }
        .take(2)
        .lowercase()
}
