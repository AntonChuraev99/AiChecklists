package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

/**
 * A single selectable AI reply language for the "Response language" picker.
 *
 * @property code    BCP-47 primary subtag ("en", "hi", "es", …). This is the value persisted and
 *                   forwarded to the server as `response_language`. Never shown to the user.
 * @property endonym The language's own name in its own script ("Español", "हिन्दी", "日本語").
 *
 * The endonyms are language-INVARIANT data, deliberately NOT stored in `strings.xml`: a language
 * name is written the same way regardless of the app's UI locale (Spanish is "Español" on an
 * English phone and a Russian phone alike). Translating them 16× would be wrong, not just wasteful —
 * same rationale as the parser lexicons. Do not "fix" this into localized resources.
 *
 * The "Auto" option is NOT in this list: it is the `null` selection (server decides), rendered from
 * its own string resource in [components.ChatResponseLanguageSheet] and the settings row.
 */
internal data class ChatLanguageOption(
    val code: String,
    val endonym: String,
) {
    companion object {
        /**
         * The offered reply languages, in a curated display order (English first, then the highest-
         * reach markets). Order is product-driven, not alphabetical.
         */
        val ALL: List<ChatLanguageOption> = listOf(
            ChatLanguageOption("en", "English"),
            ChatLanguageOption("hi", "हिन्दी"),
            ChatLanguageOption("es", "Español"),
            ChatLanguageOption("pt", "Português"),
            ChatLanguageOption("de", "Deutsch"),
            ChatLanguageOption("fr", "Français"),
            ChatLanguageOption("it", "Italiano"),
            ChatLanguageOption("nl", "Nederlands"),
            ChatLanguageOption("pl", "Polski"),
            ChatLanguageOption("tr", "Türkçe"),
            ChatLanguageOption("ru", "Русский"),
            ChatLanguageOption("uk", "Українська"),
            ChatLanguageOption("ar", "العربية"),
            ChatLanguageOption("zh", "中文"),
            ChatLanguageOption("ja", "日本語"),
            ChatLanguageOption("ko", "한국어"),
        )

        /**
         * The endonym for a persisted [code], or null when [code] is null (Auto) OR names a language
         * no longer offered. Callers render "Auto" for a null result, so an unknown code degrades
         * safely to Auto rather than showing a raw subtag.
         */
        fun endonymFor(code: String?): String? =
            code?.let { c -> ALL.firstOrNull { it.code == c }?.endonym }
    }
}
