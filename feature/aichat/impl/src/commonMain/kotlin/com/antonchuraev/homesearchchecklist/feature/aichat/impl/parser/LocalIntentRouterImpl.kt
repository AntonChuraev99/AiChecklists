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
        // AttachToItem runs first: "прикрепи к" / "attach to" keywords are very specific
        // and must not be shadowed by the broad CreateItem triggers ("добавь к", "add to").
        tryAttachToItem(normalized, lower, locale)?.let { return it }
        // SetReminder / MoveReminders run before CreateChecklist because the broad
        // CreateChecklist triggers ("создай", "create", "new") would otherwise swallow
        // "создай напоминание" and "create reminder" phrases that belong to SetReminder.
        tryMoveReminders(normalized, lower, locale)?.let { return it }
        // SetReminder skipped in Layer 1 — date/entity extraction is too complex for local
        // parsing. Falls through to Layer 2 (classifier) or Layer 3 (full chat) which handle
        // natural language dates and checklist/item resolution correctly.
        tryCreateChecklist(normalized, lower, locale)?.let { return it }
        tryCreateItem(normalized, lower, locale)?.let { return it }
        tryDeleteItem(normalized, lower, locale)?.let { return it }
        tryCompleteItem(normalized, lower, locale)?.let { return it }
        tryFindItems(normalized, lower, locale)?.let { return it }
        return unknown(normalized)
    }

    // ─── AttachToItem ─────────────────────────────────────────────────────────

    /**
     * Recognises «прикрепи это к молоко в покупках» / "attach this to milk in shopping".
     *
     * Strategy:
     *   1. Look for an attach-trigger keyword at a word-boundary.
     *   2. Extract the sub-phrase AFTER the trigger.
     *   3. Split on the target-preposition («к» / "to") to get [itemText] and optional [checklistHint].
     *
     * The extracted [itemText] and [checklistHint] are stored in the [IntentClassification]
     * via [ChatIntent.AttachToItem]. The dispatcher will receive them on PreviewApply
     * when [buildToolCall] converts the intent to [ToolCall.AttachToItem].
     *
     * Confidence:
     *   FULL  (1.0) — specific multi-word trigger + item text extracted
     *   PARTIAL (0.8) — single-word trigger + item text extracted
     *   FUZZY (0.6) — keyword matched but no item text found
     */
    private fun tryAttachToItem(
        normalized: String,
        lower: String,
        locale: ChatLocale,
    ): IntentClassification? {
        val keywords = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.attachToItem
            ChatLocale.En -> EnIntentLexicon.attachToItem
        }

        val matched = keywords.sortedByDescending { it.length }
            .firstOrNull { containsAtBoundary(lower, it) } ?: return null

        val isMultiWord = matched.contains(' ')
        val endIdx = firstMatchEnd(lower, keywords)
        val remainder = normalized.substring(endIdx).trim()
        val remainderLower = lower.substring(endIdx).trim()

        // After stripping the trigger, the remaining text is "<itemText> [в/in <checklistHint>]"
        // or just "<itemText>" if no checklist hint is present.
        // We split on the last occurrence of a hint-preposition to avoid false splits
        // (e.g. "вода в стакане в покупках" → item="вода в стакане", hint="покупках").
        val (itemText, checklistHint) = extractItemAndHintFromRemainder(remainderLower, remainder, locale)

        val confidence = when {
            isMultiWord && itemText != null -> CONF_FULL
            itemText != null -> CONF_PARTIAL
            else -> CONF_FUZZY
        }

        logger.debug(TAG, "AttachToItem matched='$matched' item='${itemText?.take(30)}' hint='$checklistHint' conf=$confidence")
        return IntentClassification(
            intent = ChatIntent.AttachToItem(
                itemText = itemText ?: remainder,
                checklistHint = checklistHint,
            ),
            confidence = confidence,
            layer = RoutingLayer.Local,
        )
    }

    /**
     * Extracts (itemText, checklistHint) from the remainder after stripping the attach trigger.
     *
     * For RU: splits on last "в <word>" / "из <word>" at the end.
     * For EN: splits on last " in <word>" / " from <word>" at the end.
     *
     * Returns the CASE-PRESERVED version of itemText (from [normalizedRemainder])
     * paired with the lowercase hint for matching.
     */
    private fun extractItemAndHintFromRemainder(
        lowerRemainder: String,
        normalizedRemainder: String,
        locale: ChatLocale,
    ): Pair<String?, String?> {
        if (lowerRemainder.isBlank()) return Pair(null, null)

        val hintPreps = when (locale) {
            ChatLocale.Ru -> listOf(" в ", " из ", " для ")
            ChatLocale.En -> listOf(" in ", " from ", " for ")
        }

        for (prep in hintPreps.sortedByDescending { it.length }) {
            val idx = lowerRemainder.lastIndexOf(prep)
            if (idx > 0) {
                val itemLower = lowerRemainder.substring(0, idx).trim()
                val hintLower = lowerRemainder.substring(idx + prep.length).trim()
                // Mirror split to case-preserved string for display
                val itemText = normalizedRemainder.substring(0, idx).trim().ifBlank { null }
                val hint = hintLower.ifBlank { null }
                if (itemText != null) return Pair(itemText, hint)
            }
        }

        // No preposition found — the whole remainder is the item text, no checklist hint
        return Pair(normalizedRemainder.trim().ifBlank { null }, null)
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
        // Take EVERYTHING after the trigger keyword as the raw name.
        // Layer 1 is broad-recall; if the user wanted a clean name they would type it cleanly.
        // We do strip leading prepositions ("в ", "для ", "to ", "for ") only — they are
        // never part of a user-meaningful list name, but the rest (including typos and noise)
        // is preserved verbatim. Layer 2 may refine if needed.
        // IMPORTANT: extract the name from the ORIGINAL `normalized` (case-preserved) input,
        // not from `lower`, so the user sees their original casing in the preview.
        val rawName = normalized.substring(endIdx).trim().removeLeadingPrepsCaseAware(locale)
        val name = rawName.ifBlank { null }
        // CONF_FUZZY (0.6) when no name was extracted — keeps confidence BELOW the
        // LAYER_1_CONFIDENCE_THRESHOLD (0.7) so Layer 2 can ask the user for a name.
        // Real incident (2026-05-31): «да создай» fired a confident nameless preview
        // because CONF_PARTIAL (0.8) is ≥ threshold. Fix: nameless → escalate.
        val confidence = if (name != null) CONF_FULL else CONF_FUZZY

        logger.debug(TAG, "CreateChecklist matched='$matched' name='${name?.take(40)}' conf=$confidence")
        return IntentClassification(ChatIntent.CreateChecklist(name), confidence, RoutingLayer.Local)
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
        if (isReferentialPayload(payload, locale)) return freeFormEscalation()
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
        if (isReferentialPayload(payload, locale)) return freeFormEscalation()
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
        if (isReferentialPayload(payload, locale)) return freeFormEscalation()
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
     * Escalation result for a command Layer 1 recognised but cannot resolve locally
     * (a context-dependent confirmation). Routed to Layer 3 (full chat), which has the
     * conversation history needed to resolve "all"/«все» against previously-suggested items.
     */
    private fun freeFormEscalation(): IntentClassification =
        IntentClassification(
            intent = ChatIntent.FreeForm,
            confidence = CONF_FULL,
            layer = RoutingLayer.FullChat,
        )

    /**
     * True when [payload] (the text after a command verb) is a bare quantifier/pronoun
     * referring to previously-suggested items — «все», «их», "all", "them" — optionally
     * followed by a checklist hint ("все в покупки") or a completion marker ("all done").
     * Such a payload carries no real item text, so the command must escalate to Layer 3
     * instead of building a junk ToolCall like AddItem("все").
     *
     * A quantifier followed by a real noun («все продукты» / "all groceries") is NOT
     * referential — only the quantifier alone (or quantifier + hint/marker) qualifies.
     */
    private fun isReferentialPayload(payload: String, locale: ChatLocale): Boolean {
        val referents: Set<String>
        val hintPreps: Set<String>
        val completionMarkers: Set<String>
        when (locale) {
            ChatLocale.Ru -> {
                referents = RuIntentLexicon.referentialPayloads
                hintPreps = RuIntentLexicon.hintPrepositions
                completionMarkers = RuIntentLexicon.completionMarkers
            }
            ChatLocale.En -> {
                referents = EnIntentLexicon.referentialPayloads
                hintPreps = EnIntentLexicon.hintPrepositions
                completionMarkers = EnIntentLexicon.completionMarkers
            }
        }
        val tokens = payload.trim().split(' ').filter { it.isNotBlank() }
        if (tokens.isEmpty()) return false
        // Core = tokens before the first checklist-hint preposition ("все в покупки" → ["все"]).
        val core = mutableListOf<String>()
        for (t in tokens) {
            if (t in hintPreps) break
            core += t
        }
        // Drop trailing completion markers ("all done" → ["all"]).
        while (core.isNotEmpty() && core.last() in completionMarkers) {
            core.removeAt(core.lastIndex)
        }
        if (core.isEmpty()) return false
        return core.joinToString(" ") in referents || core.all { it in referents }
    }

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

    /**
     * Strips leading prepositions from a name candidate but preserves case of the rest.
     * "в апки сделать рекламные скрины" → "апки сделать рекламные скрины"
     * "for the trip" → "the trip"
     *
     * Unlike [removeLeadingStopWords], this works on the case-preserved string
     * and performs case-insensitive prefix matching, so the user sees their original
     * capitalisation in the preview.
     */
    private fun String.removeLeadingPrepsCaseAware(locale: ChatLocale): String {
        val prepositions = when (locale) {
            ChatLocale.Ru -> RuIntentLexicon.hintPrepositions
            ChatLocale.En -> EnIntentLexicon.hintPrepositions
        }
        var result = this.trimStart()
        for (prep in prepositions.sortedByDescending { it.length }) {
            if (result.length > prep.length &&
                result.substring(0, prep.length).lowercase() == prep &&
                result[prep.length].isWhitespace()
            ) {
                result = result.substring(prep.length).trimStart()
                break
            }
        }
        return result
    }

    /** Returns current epoch millis. Platform-neutral via kotlin.time.Clock (Kotlin 2.1+). */
    private fun nowMillis(): Long =
        kotlin.time.Clock.System.now().toEpochMilliseconds()
}
