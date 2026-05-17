package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.feature.aichat.api.locale.ChatLocaleProvider
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * KMP-compatible [ChatLocaleProvider] implemented via [currentSystemLanguage] expect/actual.
 *
 * On Android: delegates to [Locale.getDefault().language].
 * On wasmJs/iOS: falls back to "en" (system locale APIs are not uniformly available
 * across KMP targets in Phase A; Phase B will add proper expect/actual if needed).
 *
 * // Pending: docs/todos/2026-05-13-ai-chat-assistant.md (Phase B: proper iOS/wasmJs locale)
 */
class AndroidChatLocaleProvider : ChatLocaleProvider {
    override fun current(): ChatLocale = ChatLocale.fromLanguageTag(currentSystemLanguage())
}

/**
 * Returns the current system language tag (e.g. "ru", "en").
 * Resolved via platform-specific expect/actual.
 */
expect fun currentSystemLanguage(): String
