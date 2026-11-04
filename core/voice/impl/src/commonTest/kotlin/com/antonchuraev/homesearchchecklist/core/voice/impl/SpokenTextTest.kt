package com.antonchuraev.homesearchchecklist.core.voice.impl

import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenTextTest {

    @Test
    fun plainStripsMarkdownAndLinks() {
        val cases = listOf(
            "**Bold** and *italic* and _under_" to "Bold and italic and under",
            "Use `x = 2` here" to "Use x = 2 here",
            "## Step 1\n- first\n- second" to "Step 1 first second",
            "1. One\n2) Two" to "One Two",
            "See [the book](https://example.org) now" to "See the book now",
            "> quoted line" to "quoted line",
            "2 * 3 * 4" to "2 * 3 * 4",
            "snake_case_name stays" to "snake_case_name stays",
        )
        cases.forEach { (input, expected) -> assertEquals(expected, SpokenText.plain(input), input) }
    }

    @Test
    fun sentencesSplitAtTerminatorsButNotAtNumbersInitialsOrAbbreviations() {
        val cases = listOf(
            "Hello. How are you? Fine!" to listOf("Hello.", "How are you?", "Fine!"),
            "It costs 3.50 euros. Pay now." to listOf("It costs 3.50 euros.", "Pay now."),
            "Use e.g. a fork. Then eat." to listOf("Use e.g. a fork.", "Then eat."),
            "Mr. Smith is here. Sr. García too." to listOf("Mr. Smith is here.", "Sr. García too."),
            "Wait… what? ¿Qué? ¡Hola!" to listOf("Wait… what?", "¿Qué?", "¡Hola!"),
            "He said \"Stop.\" Then left." to listOf("He said \"Stop.\"", "Then left."),
            "No terminator at the end" to listOf("No terminator at the end"),
            "Line one\nline two." to listOf("Line one line two."),
            "Это т. е. пример. Дальше." to listOf("Это т. е. пример.", "Дальше."),
            "   " to emptyList(),
        )
        cases.forEach { (input, expected) -> assertEquals(expected, SpokenText.sentences(input), input) }
    }

    @Test
    fun spokenAndUnspokenPartsSplitTheCaptionAtTheCut() {
        val full = "Once upon a time there was a cat."
        assertEquals("Once", SpokenText.spokenPart(full, 5))
        assertEquals("upon a time there was a cat.", SpokenText.unspokenPart(full, 5))
        assertEquals("", SpokenText.spokenPart(full, -3))
        assertEquals(full, SpokenText.unspokenPart(full, 0))
        assertEquals(full, SpokenText.spokenPart(full, 999))
        assertEquals("", SpokenText.unspokenPart(full, 999))
    }

    @Test
    fun segmentsJoinWithOneSpaceAndTheirOffsetsMatchTheJoinedText() {
        val cases = listOf(
            listOf("Say", "¿Qué tal?") to "Say ¿Qué tal?",
            listOf("Say ", "hola") to "Say hola",
            listOf("You say", "hola", ".") to "You say hola.",
            listOf("One") to "One",
            emptyList<String>() to "",
        )
        cases.forEach { (pieces, expected) ->
            val joined = SpokenText.joinSegments(pieces)
            assertEquals(expected, joined, pieces.toString())
            SpokenText.segmentOffsets(pieces).forEachIndexed { i, offset ->
                assertEquals(pieces[i], joined.substring(offset, offset + pieces[i].length), pieces.toString())
            }
        }
    }
}
