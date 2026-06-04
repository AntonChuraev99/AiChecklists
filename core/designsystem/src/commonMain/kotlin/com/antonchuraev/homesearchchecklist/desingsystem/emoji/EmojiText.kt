package com.antonchuraev.homesearchchecklist.desingsystem.emoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle

/**
 * Holds an [AnnotatedString] together with the [FontFamily] that must be applied at Text level
 * for emoji glyphs to resolve via NotoColorEmoji on wasmJs.
 *
 * Skiko on wasmJs does not fall back from one FontFamily to another for missing glyphs:
 * - applying the emoji font to the whole Text makes Latin text disappear
 * - applying the emoji font only via SpanStyle on individual emoji code points does not always
 *   resolve glyphs reliably (mixed results across emoji)
 *
 * The reliable workaround is the inverse: keep emoji font on the Text level (so Skia uses the
 * emoji font as the primary), and override every non-emoji run with [FontFamily.Default] via
 * SpanStyle (so Latin/digits/punctuation render with the system font).
 */
data class EmojiAwareText(
    val text: AnnotatedString,
    val fontFamily: FontFamily,
)

fun buildEmojiAwareText(text: String, emojiFont: FontFamily): EmojiAwareText {
    val annotated = buildAnnotatedString {
        var i = 0
        var nonEmojiStart = -1

        fun flushNonEmoji(end: Int) {
            if (nonEmojiStart >= 0) {
                withStyle(SpanStyle(fontFamily = FontFamily.Default)) {
                    append(text.substring(nonEmojiStart, end))
                }
                nonEmojiStart = -1
            }
        }

        var prevWasEmoji = false
        while (i < text.length) {
            val c = text[i]
            val isHighSurrogate = c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()
            val codePoint = if (isHighSurrogate) {
                ((c.code - 0xD800) * 0x400) + (text[i + 1].code - 0xDC00) + 0x10000
            } else {
                c.code
            }
            val charLen = if (isHighSurrogate) 2 else 1

            val isEmoji = isEmojiBaseCodePoint(codePoint) ||
                (prevWasEmoji && isEmojiContinuation(codePoint))

            if (isEmoji) {
                flushNonEmoji(i)
                append(text.substring(i, i + charLen))
                prevWasEmoji = true
            } else {
                if (nonEmojiStart < 0) nonEmojiStart = i
                prevWasEmoji = false
            }
            i += charLen
        }
        flushNonEmoji(text.length)
    }
    return EmojiAwareText(annotated, emojiFont)
}

@Composable
fun rememberEmojiAwareText(text: String): EmojiAwareText {
    val emojiFont = LocalEmojiFont.current
    return remember(text, emojiFont) { buildEmojiAwareText(text, emojiFont) }
}

private fun isEmojiBaseCodePoint(cp: Int): Boolean =
    cp in 0x1F000..0x1FFFF ||
        cp in 0x2600..0x27BF ||
        cp in 0x2300..0x23FF ||
        cp in 0x2B00..0x2BFF ||
        cp == 0x303D ||
        cp in 0x3297..0x3299 ||
        cp == 0x00A9 ||
        cp == 0x00AE ||
        cp == 0x2122 ||
        cp == 0x2139 ||
        cp in 0x2194..0x2199 ||
        cp in 0x21A9..0x21AA ||
        cp == 0x231A ||
        cp == 0x231B ||
        cp == 0x24C2 ||
        cp in 0x25AA..0x25AB ||
        cp == 0x25B6 ||
        cp == 0x25C0 ||
        cp in 0x25FB..0x25FE

private fun isEmojiContinuation(cp: Int): Boolean =
    cp == 0x200D ||
        cp == 0xFE0F ||
        cp == 0x20E3 ||
        cp in 0x1F3FB..0x1F3FF ||
        cp in 0x1F1E6..0x1F1FF ||
        cp in 0xE0020..0xE007F
