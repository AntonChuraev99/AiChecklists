package com.antonchuraev.homesearchchecklist.core.voice.impl

/**
 * Text of the agent as TTS reads it and as the call caption shows it: markdown stripped, split into sentences, and
 * "the whole reply minus what was spoken" for the caption of a cut reply.
 *
 * Pure functions: the server sends checked sentences (`say`), so this is the client side of a reply that came
 * without them (a replay, a turn without stream) and of the caption.
 */
object SpokenText {

    /**
     * [markdown] as plain speech: emphasis, code marks, headings, list markers and links (their text stays) are
     * removed, line breaks become spaces.
     */
    fun plain(markdown: String): String {
        var text = markdown.replace(LINK) { it.groupValues[1] }
        text = text.lines().joinToString(" ") { line ->
            line.trim()
                .replace(HEADING, "")
                .replace(BULLET, "")
                .replace(NUMBERED, "")
                .replace(QUOTE, "")
        }
        text = text.replace(CODE_FENCE, "").replace("`", "")
        text = text.replace(BOLD_STARS) { it.groupValues[1] }
            .replace(BOLD_UNDERSCORES) { it.groupValues[1] }
            .replace(ITALIC_STAR) { it.groupValues[1] }
            .replace(ITALIC_UNDERSCORE) { it.groupValues[1] }
        return text.replace(SPACES, " ").trim()
    }

    /**
     * [text] split into sentences in reading order, each trimmed and non-blank. A sentence ends at `.`, `!`, `?` or
     * `…` (with closing quotes or brackets) followed by a space; not inside a number (`3.5`), not after a single
     * letter (`e.g.`) or a known abbreviation (`Mr.`, `etc.`, `Sr.`, `z.B.`), and `…` only before a capital.
     */
    fun sentences(text: String): List<String> {
        val source = plain(text)
        if (source.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        var start = 0
        var i = 0
        while (i < source.length) {
            val c = source[i]
            if (c in TERMINATORS) {
                var end = i + 1
                while (end < source.length && (source[end] in TERMINATORS || source[end] in CLOSERS)) end++
                val atEnd = end >= source.length
                val nextIsSpace = !atEnd && source[end].isWhitespace()
                if (atEnd || (nextIsSpace && isSentenceEnd(source, start, i, end))) {
                    source.substring(start, end).trim().takeIf { it.isNotEmpty() }?.let(result::add)
                    start = end
                }
                i = end
            } else {
                i++
            }
        }
        source.substring(start).trim().takeIf { it.isNotEmpty() }?.let(result::add)
        return result
    }

    /**
     * The text of one utterance from its segments (a sentence may switch voices): pieces are joined with a space
     * unless one side already has it. `SpeechOutput` counts `Range.endChar` against this same text.
     */
    fun joinSegments(pieces: List<String>): String = buildString {
        pieces.forEach { piece ->
            if (needsSpace(this, piece)) append(' ')
            append(piece)
        }
    }

    /** Start offset of each piece inside [joinSegments] of [pieces]. */
    fun segmentOffsets(pieces: List<String>): List<Int> {
        val offsets = ArrayList<Int>(pieces.size)
        val text = StringBuilder()
        pieces.forEach { piece ->
            if (needsSpace(text, piece)) text.append(' ')
            offsets += text.length
            text.append(piece)
        }
        return offsets
    }

    /** The part of [full] already spoken: the first [spokenChars] characters (clamped), without trailing spaces. */
    fun spokenPart(full: String, spokenChars: Int): String = full.take(spokenChars.coerceIn(0, full.length)).trimEnd()

    /** "Full minus spoken": what TTS did not reach before the cut, without leading spaces. */
    fun unspokenPart(full: String, spokenChars: Int): String = full.drop(spokenChars.coerceIn(0, full.length)).trimStart()

    private fun needsSpace(text: CharSequence, piece: String): Boolean =
        text.isNotEmpty() && piece.isNotEmpty() && !text.last().isWhitespace() && !piece.first().isWhitespace() &&
            piece.first() !in NO_SPACE_BEFORE

    /**
     * A terminator at [dot] ends the sentence: a `.` that is not an abbreviation, an initial or the decimal point of
     * a number; a `…` only before a capital (`Wait… what?` stays one sentence). [after] is the index past the
     * terminator run.
     */
    private fun isSentenceEnd(source: String, sentenceStart: Int, dot: Int, after: Int): Boolean {
        if (source[dot] == '…' && source.lastIndex >= after) {
            val next = source.substring(after).trimStart().firstOrNull() ?: return true
            return next.isUpperCase() || next == '¿' || next == '¡'
        }
        if (source[dot] != '.') return true
        val wordStart = (dot - 1 downTo sentenceStart).firstOrNull { source[it].isWhitespace() }?.plus(1) ?: sentenceStart
        val word = source.substring(wordStart, dot)
        if (word.isEmpty()) return true
        if (word.length == 1 && word[0].isLetter()) return false
        if (word.contains('.')) return false // e.g. / z.B. / i.e.
        return word.lowercase() !in ABBREVIATIONS
    }

    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val HEADING = Regex("""^#{1,6}\s+""")
    private val BULLET = Regex("""^[-*+•]\s+""")
    private val NUMBERED = Regex("""^\d{1,3}[.)]\s+""")
    private val QUOTE = Regex("""^>\s?""")
    private val CODE_FENCE = Regex("```[a-zA-Z]*")
    private val BOLD_STARS = Regex("""\*\*(.+?)\*\*""")
    private val BOLD_UNDERSCORES = Regex("""__(.+?)__""")
    private val ITALIC_STAR = Regex("""(?<![\w*])\*(?!\s)(.+?)(?<!\s)\*(?![\w*])""")
    private val ITALIC_UNDERSCORE = Regex("""(?<![\w_])_(?!\s)(.+?)(?<!\s)_(?![\w_])""")
    private val SPACES = Regex("""\s+""")

    private val TERMINATORS = setOf('.', '!', '?', '…')
    private val CLOSERS = setOf('"', '\'', '»', '”', '’', ')', ']')
    private val NO_SPACE_BEFORE = setOf('.', ',', '!', '?', ';', ':', '…', ')', ']', '»', '”', '’')

    /** Lower-case abbreviations of en/es/fr/de/it/ru that end with a dot inside a sentence. */
    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "st", "vs", "etc", "approx", "no",
        "sr", "sra", "srta", "ud", "uds", "pág",
        "mme", "mlle", "env",
        "bzw", "usw", "ca", "nr", "str",
        "sig", "dott", "ecc",
        "т", "др", "стр", "см",
    )
}
