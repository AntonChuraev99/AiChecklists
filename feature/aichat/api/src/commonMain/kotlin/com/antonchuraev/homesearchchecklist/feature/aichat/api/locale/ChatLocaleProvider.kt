package com.antonchuraev.homesearchchecklist.feature.aichat.api.locale

import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale

/**
 * Provides the current locale for AI Chat intent routing.
 *
 * Implemented per-platform (Android: [Locale.getDefault().language]).
 * Injected into [ChatViewModel] via Koin.
 */
interface ChatLocaleProvider {
    fun current(): ChatLocale
}
