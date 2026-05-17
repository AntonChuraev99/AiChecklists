package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser

import com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon.ChecklistHintExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [ChecklistHintExtractor].
 *
 * Coverage: RU + EN preposition patterns, edge cases (no preposition, empty input,
 * stop-word stripping, multiple prepositions, double-space normalization).
 */
class ChecklistHintExtractorTest {

    // ─── RU extraction ────────────────────────────────────────────────────────

    @Test
    fun ru_extractsHintAfterV() {
        val hint = ChecklistHintExtractor.extractRu("добавь молоко в покупки")
        assertEquals("покупки", hint)
    }

    @Test
    fun ru_extractsHintAfterDlya() {
        val hint = ChecklistHintExtractor.extractRu("купи продукты для работы")
        assertEquals("работы", hint)
    }

    @Test
    fun ru_extractsHintAfterK() {
        val hint = ChecklistHintExtractor.extractRu("добавь задачу к делам")
        assertEquals("делам", hint)
    }

    @Test
    fun ru_noPrepositionReturnsNull() {
        val hint = ChecklistHintExtractor.extractRu("добавь молоко")
        assertNull(hint)
    }

    @Test
    fun ru_emptyInputReturnsNull() {
        val hint = ChecklistHintExtractor.extractRu("")
        assertNull(hint)
    }

    @Test
    fun ru_blankInputReturnsNull() {
        val hint = ChecklistHintExtractor.extractRu("   ")
        assertNull(hint)
    }

    @Test
    fun ru_extractsHintWhenMultiplePrepositions() {
        // "добавь в покупки для работы" — longest preposition "для" (3 chars) wins over "в" (1 char)
        // longest-first matching ensures "для" is found first, returning the hint after it
        val hint = ChecklistHintExtractor.extractRu("добавь в покупки для работы")
        assertEquals("работы", hint)
    }

    @Test
    fun ru_hintIsLowercased() {
        val hint = ChecklistHintExtractor.extractRu("добавь Молоко В Покупки")
        assertEquals("покупки", hint)
    }

    @Test
    fun ru_commandEndIndexSkipsCommandWord() {
        // commandEndIndex = length of "добавь " (7) → search starts after command
        val hint = ChecklistHintExtractor.extractRu("добавь молоко в покупки", commandEndIndex = 7)
        assertEquals("покупки", hint)
    }

    @Test
    fun ru_hintWithMultipleWordsExtractsFirstToken() {
        // "в список покупок" → hint = "список" (first token after prep)
        val hint = ChecklistHintExtractor.extractRu("положи масло в список покупок")
        assertEquals("список", hint)
    }

    // ─── EN extraction ────────────────────────────────────────────────────────

    @Test
    fun en_extractsHintAfterTo() {
        val hint = ChecklistHintExtractor.extractEn("add milk to shopping list")
        assertEquals("shopping", hint)
    }

    @Test
    fun en_extractsHintAfterIn() {
        val hint = ChecklistHintExtractor.extractEn("add butter in groceries")
        assertEquals("groceries", hint)
    }

    @Test
    fun en_extractsHintAfterFor() {
        val hint = ChecklistHintExtractor.extractEn("write down call dentist for work")
        assertEquals("work", hint)
    }

    @Test
    fun en_extractsHintAfterToSingleToken() {
        val hint = ChecklistHintExtractor.extractEn("add milk to groceries")
        assertEquals("groceries", hint)
    }

    @Test
    fun en_noPrepositionReturnsNull() {
        val hint = ChecklistHintExtractor.extractEn("add eggs")
        assertNull(hint)
    }

    @Test
    fun en_emptyInputReturnsNull() {
        val hint = ChecklistHintExtractor.extractEn("")
        assertNull(hint)
    }

    @Test
    fun en_hintIsLowercased() {
        val hint = ChecklistHintExtractor.extractEn("add Milk In Shopping")
        assertEquals("shopping", hint)
    }

    @Test
    fun en_stopWordStripped() {
        // "in the shopping" → strip "the" → "shopping"
        val hint = ChecklistHintExtractor.extractEn("add milk in the shopping list")
        // "the" matches as first token after "in" — should be stripped, next token "shopping" returned
        // OR "the" is extracted but our logic returns the next non-stop word
        // Current impl: stops at first token, strips stop word only if it IS a stop word
        // So "the" = stop word → skip → take "shopping"
        assertEquals("shopping", hint)
    }

    @Test
    fun en_commandEndIndexSkipsCommandWord() {
        val hint = ChecklistHintExtractor.extractEn("add milk to shopping", commandEndIndex = 3)
        assertEquals("shopping", hint)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun ru_prepositionAtEndReturnsNull() {
        // "добавь в" with nothing after preposition → null
        val hint = ChecklistHintExtractor.extractRu("добавь в")
        assertNull(hint)
    }

    @Test
    fun en_prepositionAtEndReturnsNull() {
        val hint = ChecklistHintExtractor.extractEn("add to")
        assertNull(hint)
    }

    @Test
    fun ru_onlyPrepositionReturnsNull() {
        val hint = ChecklistHintExtractor.extractRu("в")
        assertNull(hint)
    }
}
