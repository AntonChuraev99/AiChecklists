package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon

/**
 * Extracts a fuzzy checklist name (hint) from a natural-language command.
 *
 * Looks for preposition + name patterns after the command keyword:
 *   RU: "в покупки", "к делам", "для работы"    → hint = "покупки" / "делам" / "работы"
 *   EN: "in shopping", "to groceries", "for work" → hint = "shopping" / "groceries" / "work"
 *
 * The hint is lowercased and trimmed. Stop words (articles, conjunctions) at the
 * start of the hint token are stripped to avoid false matches.
 *
 * Returns null when no preposition pattern is found.
 */
internal object ChecklistHintExtractor {

    /** Stop-words that may follow a preposition but are not part of the checklist name. */
    private val enStopWords = setOf("the", "a", "an", "my", "our")
    private val ruStopWords = setOf("мой", "мою", "мои", "своё", "своего", "наш", "наши")

    /**
     * Extracts a checklist hint from [input] using the given [hintPrepositions].
     *
     * [commandEndIndex] indicates the end of the matched command keyword; the search
     * for the preposition starts from this position. If unknown, pass 0 to search globally.
     *
     * [stopWords] are removed from the beginning of the extracted hint token.
     */
    fun extract(
        input: String,
        commandEndIndex: Int,
        hintPrepositions: Set<String>,
        stopWords: Set<String>,
    ): String? {
        val lower = input.lowercase()
        val searchArea = lower.substring(commandEndIndex.coerceAtLeast(0))

        // Sort prepositions longest-first to avoid "в" shadowing "во"
        for (prep in hintPrepositions.sortedByDescending { it.length }) {
            val prepIdx = findWordBoundary(searchArea, prep) ?: continue
            val afterPrep = searchArea.substring(prepIdx + prep.length).trimStart()
            if (afterPrep.isBlank()) continue

            // Take the first token after the preposition (up to next space or end)
            val hint = afterPrep.split(Regex("""\s+""")).firstOrNull()?.trim() ?: continue
            if (hint.isBlank()) continue

            // Strip stop words
            val cleaned = if (hint in stopWords) {
                afterPrep.split(Regex("""\s+""")).drop(1).firstOrNull()?.trim() ?: continue
            } else {
                hint
            }

            return cleaned.ifBlank { null }
        }
        return null
    }

    /**
     * Extracts a checklist hint from a Russian [input].
     * Automatically selects Russian prepositions and stop words.
     */
    fun extractRu(input: String, commandEndIndex: Int = 0): String? =
        extract(input, commandEndIndex, RuIntentLexicon.hintPrepositions, ruStopWords)

    /**
     * Extracts a checklist hint from an English [input].
     * Automatically selects English prepositions and stop words.
     */
    fun extractEn(input: String, commandEndIndex: Int = 0): String? =
        extract(input, commandEndIndex, EnIntentLexicon.hintPrepositions, enStopWords)

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Finds the start index of [word] in [text] at a word boundary.
     * Uses explicit boundary check compatible with both Cyrillic and Latin.
     */
    private fun findWordBoundary(text: String, word: String): Int? {
        if (word.isEmpty()) return null
        val escaped = Regex.escape(word)
        val pattern = Regex("""(?<![а-яёa-z\d])${escaped}(?![а-яёa-z\d])""")
        return pattern.find(text)?.range?.first
    }
}
