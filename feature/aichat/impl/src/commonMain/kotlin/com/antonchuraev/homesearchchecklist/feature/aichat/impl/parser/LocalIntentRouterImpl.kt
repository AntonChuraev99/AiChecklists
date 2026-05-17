package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.ChatLocale
import com.antonchuraev.homesearchchecklist.feature.aichat.api.parser.LocalIntentRouter
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon.ChecklistHintExtractor
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon.EnIntentLexicon
import com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon.RuIntentLexicon
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import kotlinx.datetime.TimeZone

/**
 * Layer 1 local intent router.
 *
 * Classifies user input using keyword matching + entity extraction.
 * No network calls. No AI credits. Pure Kotlin, fully KMP-compatible.
 *
 * Confidence scoring:
 *   1.0 — exact keyword at start + all entities extracted (text + hint)
 *   0.8 — keyword match + partial entities (text found, hint missing or vice versa)
 *   0.6 — fuzzy keyword anywhere in input, no clear entity
 *   0.0 — no match → ChatIntent.Unknown
 *
 * [SmartDateParser] is reused for date extraction in SetReminder intent.
 */
internal class LocalIntentRouterImpl(
    private val dateParser: SmartDateParser,
    private val logger: AppLogger,
) : LocalIntentRouter {

    private companion object {
        const val TAG = "LocalIntentRouter"

        const val CONF_FULL = 1.0f
        const val CONF_PARTIAL = 0.8f
        const val CONF_FUZZY = 0.6f
        const val CONF_NONE = 0.0f

        /** Normalizes multiple internal spaces and trims. */
        fun normalize(input: String): String = input.trim().replace(Regex("""\s+"""), " ")

        /** ASCII-safe lowercase for English input (avoids Turkish İ issue). */
        fun toLowerEn(input: String): String = buildString(input.length) {
            for (ch in input) {
                append(if (ch in 'A'..'Z') ch + 32 else ch)
            }
        }

        /** Unicode-safe lowercase for Russian input. */
        fun toLowerRu(input: String): String = input.lowercase()

        /**
         * Returns true when [keyword] appears at a word boundary in [text].
         * Uses explicit boundary regex compatible with both Cyrillic and Latin.
         */
        fun containsAtBoundary(text: String, keyword: String): Boolean {
            if (keyword.isEmpty()) return false
            val escaped = Regex.escape(keyword)
            return Regex("""(?<![а-яёa-z\d])${escaped}(?![а-яёa-z\d])""")
                .containsMatchIn(text)
        }

        /**
         * Returns the index after the first boundary-matched keyword in [text].
         * Used to compute [commandEndIndex] for entity extraction.
         */
        fun firstMatchEnd(text: String, keywords: Set<String>): Int {
            for (kw in keywords.sortedByDescending { it.length }) {
                val escaped = Regex.escape(kw)
                val match = Regex("""(?<![а-яёa-z\d])${escaped}(?![а-яёa-z\d])""")
                    .find(text) ?: continue
                return match.range.last + 1
            }
            return 0
        }

        /**
         * Returns true when [keyword] is at the very beginning of [text]
         * (allowing for leading whitespace/punctuation).
         */
        fun startsWithKeyword(text: String, keyword: String): Boolean {
            val trimmed = text.trimStart()
            val escaped = Regex.escape(keyword)
            return Regex("""^${escaped}(?![а-яёa-z\d])""").containsMatchIn(trimmed)
        }

        /**
         * Extracts the "payload" text from [input] by removing the matched [keyword] prefix.
         * Returns the trimmed remainder, or null if keyword not at start.
         */
        fun extractPayloadAfterKeyword(input: String, keyword: String): String? {
            val trimmed = input.trimStart()
            val escaped = Regex.escape(keyword)
            val match = Regex("""^${escaped}(?![а-яёa-z\d])""").find(trimmed) ?: return null
            return trimmed.substring(match.range.last + 1).trim()
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    override suspend fun route(input: String, locale: ChatLocale): IntentClassification {
        if (input.isBlank()) {
            return unknown(input)
        }

        val normalized = normalize(input)
        val lower = when (locale) {
            ChatLocale.Ru -> toLowerRu(normalized)
            ChatLocale.En -> toLowerEn(normalized)
        }

        return runCatching { classify(normalized, lower, locale) }
            .onFailure { e ->
                logger.error(TAG, "Classification failed for input len=${input.length}: ${e::class.simpleName}", e)
            }
            .getOrElse { unknown(input) }
    }

    // ─── Classification dispatch ──────────────────────────────────────────────

    private fun classify(normalized: String, lower: String, locale: ChatLocale): IntentClassification {
        // Priority order: more specific / longer phrases first
        tryCreateChecklist(normalized, lower, locale)?.let { return it }
        tryMoveReminders(normalized, lower, locale)?.let { return it }
        trySetReminder(normalized, lower, locale)?.let { return it }
        tryCreateItem(normalized, lower, locale)?.let { return it }
        tryDeleteItem(normalized, lower, locale)?.let { return it }
        tryCompleteItem(normalized, lower, locale)?.let { return it }
        tryFindItems(normalized, lower, locale)?.let { return it }
        return unknown(normalized)
    }

    // ─── CreateChecklist ──────────────────────────────────────────────────────

    private fun tryCreateChecklist(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.createChecklist
            ChatLocale.En -> EnIntentLexicon.createChecklist
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        val endIdx = firstMatchEnd(lower, keywords)
        val payload = lower.substring(endIdx).trim()

        // Name is everything after the keyword that is not a preposition
        val hint = payload.removeLeadingStopWords(locale).ifBlank { null }
        val confidence = if (hint != null) CONF_FULL else CONF_PARTIAL

        logger.debug(TAG, "CreateChecklist matched='$matched' name='$hint' conf=$confidence")
        return IntentClassification(ChatIntent.CreateChecklist, confidence, RoutingLayer.Local)
    }

    // ─── MoveReminders ────────────────────────────────────────────────────────

    private fun tryMoveReminders(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.moveReminders
            ChatLocale.En -> EnIntentLexicon.moveReminders
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        // Require "с X на Y" (RU) or "from X to Y" (EN) pattern for high confidence
        val hasFromTo = when (locale) {
            ChatLocale.Ru -> lower.contains(Regex("""\bс\b.+\bна\b"""))
            ChatLocale.En -> lower.contains(Regex("""\bfrom\b.+\bto\b"""))
        }
        val confidence = if (hasFromTo) CONF_FULL else CONF_FUZZY

        logger.debug(TAG, "MoveReminders matched='$matched' hasFromTo=$hasFromTo conf=$confidence")
        return IntentClassification(ChatIntent.MoveReminders, confidence, RoutingLayer.Local)
    }

    // ─── SetReminder ──────────────────────────────────────────────────────────

    private fun trySetReminder(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.setReminder
            ChatLocale.En -> EnIntentLexicon.setReminder
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        // Reuse SmartDateParser to check if a date/time is present in the input
        val dateToken = runCatching {
            dateParser.parse(normalized, nowMillis(), TimeZone.currentSystemDefault())
        }.getOrNull()

        val confidence = when {
            dateToken != null -> CONF_FULL
            else -> CONF_PARTIAL  // keyword matched but no date found yet
        }

        logger.debug(TAG, "SetReminder matched='$matched' hasDate=${dateToken != null} conf=$confidence")
        return IntentClassification(ChatIntent.SetReminder, confidence, RoutingLayer.Local)
    }

    // ─── CreateItem ───────────────────────────────────────────────────────────

    private fun tryCreateItem(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.createItem
            ChatLocale.En -> EnIntentLexicon.createItem
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        val endIdx = firstMatchEnd(lower, keywords)
        val isAtStart = keywords.sortedByDescending { it.length }
            .any { startsWithKeyword(lower, it) }

        val hint = when (locale) {
            ChatLocale.Ru -> ChecklistHintExtractor.extractRu(lower, endIdx)
            ChatLocale.En -> ChecklistHintExtractor.extractEn(lower, endIdx)
        }
        val payload = lower.substring(endIdx).trim()
        val hasItemText = payload.isNotBlank() && payload != hint

        val confidence = when {
            isAtStart && hasItemText && hint != null -> CONF_FULL
            hasItemText -> CONF_PARTIAL
            else -> CONF_FUZZY
        }

        logger.debug(TAG, "CreateItem matched='$matched' hasText=$hasItemText hint='$hint' conf=$confidence")
        return IntentClassification(ChatIntent.CreateItem, confidence, RoutingLayer.Local)
    }

    // ─── DeleteItem ───────────────────────────────────────────────────────────

    private fun tryDeleteItem(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.deleteItem
            ChatLocale.En -> EnIntentLexicon.deleteItem
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        val endIdx = firstMatchEnd(lower, keywords)
        val payload = lower.substring(endIdx).trim()
        val isAtStart = keywords.sortedByDescending { it.length }
            .any { startsWithKeyword(lower, it) }

        val confidence = when {
            isAtStart && payload.isNotBlank() -> CONF_FULL
            payload.isNotBlank() -> CONF_PARTIAL
            else -> CONF_FUZZY
        }

        logger.debug(TAG, "DeleteItem matched='$matched' hasPayload=${payload.isNotBlank()} conf=$confidence")
        return IntentClassification(ChatIntent.DeleteItem, confidence, RoutingLayer.Local)
    }

    // ─── CompleteItem ─────────────────────────────────────────────────────────

    private fun tryCompleteItem(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.completeItem
            ChatLocale.En -> EnIntentLexicon.completeItem
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        val endIdx = firstMatchEnd(lower, keywords)
        val payload = lower.substring(endIdx).trim()
        val isAtStart = keywords.sortedByDescending { it.length }
            .any { startsWithKeyword(lower, it) }

        val confidence = when {
            isAtStart && payload.isNotBlank() -> CONF_FULL
            payload.isNotBlank() -> CONF_PARTIAL
            // "done", "сделано" alone is still a valid signal
            isAtStart -> CONF_PARTIAL
            else -> CONF_FUZZY
        }

        logger.debug(TAG, "CompleteItem matched='$matched' hasPayload=${payload.isNotBlank()} conf=$confidence")
        return IntentClassification(ChatIntent.CompleteItem, confidence, RoutingLayer.Local)
    }

    // ─── FindItems ────────────────────────────────────────────────────────────

    private fun tryFindItems(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.findItems
            ChatLocale.En -> EnIntentLexicon.findItems
        }
        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        val endIdx = firstMatchEnd(lower, keywords)
        val query = lower.substring(endIdx).trim()

        val confidence = when {
            query.isNotBlank() -> CONF_FULL
            else -> CONF_FUZZY
        }

        logger.debug(TAG, "FindItems matched='$matched' query='$query' conf=$confidence")
        return IntentClassification(ChatIntent.FindItems, confidence, RoutingLayer.Local)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun unknown(input: String): IntentClassification =
        IntentClassification(
            intent = ChatIntent.Unknown(input),
            confidence = CONF_NONE,
            layer = RoutingLayer.Local,
        )

    /**
     * Strips leading stop-words/prepositions from a hint candidate.
     * "the shopping" → "shopping", "для работы" → "работы"
     */
    private fun String.removeLeadingStopWords(locale: ChatLocale): String {
        val prepositions = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.hintPrepositions
            ChatLocale.En -> EnIntentLexicon.hintPrepositions
        }
        var result = this.trimStart()
        for (prep in prepositions.sortedByDescending { it.length }) {
            if (result.startsWith(prep)) {
                result = result.removePrefix(prep).trimStart()
                break
            }
        }
        return result
    }

    /** Returns current epoch millis. Platform-neutral via kotlin.time.Clock (Kotlin 2.1+). */
    private fun nowMillis(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()
}
