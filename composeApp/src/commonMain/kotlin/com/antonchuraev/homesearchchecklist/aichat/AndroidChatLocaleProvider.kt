package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * KMP-compatible [ChatLocaleProvider] implemented via [currentSystemLanguage] expect/actual.
 *
 * On Android: delegates to [Locale.getDefault().language].
 * On iOS: delegates to [NSLocale.currentLocale.languageCode].
 * On wasmJs: delegates to [navigator.language] (first 2 chars, lowercase).
 *
 * All three targets have real platform implementations — no fallback stubs.
 */
class AndroidChatLocaleProvider : ChatLocaleProvider {
    override fun current(): ChatLocale = ChatLocale.fromLanguageTag(currentSystemLanguage())
}

/**
 * Returns the current system language tag (e.g. "ru", "en").
 * Resolved via platform-specific expect/actual.
 */
expect fun currentSystemLanguage(): String
