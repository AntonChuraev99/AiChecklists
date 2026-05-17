package com.antonchuraev.homesearchchecklist.feature.aichat.api.parser

/**
 * Language used for intent parsing in [LocalIntentRouter].
 *
 * Detected automatically from the device locale, or can be overridden by the user.
 */
enum class ChatLocale {
    Ru,
    En;

    companion object {
        /**
         * Resolves a BCP-47 language tag (e.g. "ru-RU", "en-US", "en") to a [ChatLocale].
         * Defaults to [En] for any unrecognized tag.
         */
        fun fromLanguageTag(tag: String): ChatLocale {
            val lang = tag.take(2).lowercase()
            return if (lang == "ru") Ru else En
        }
    }
}
