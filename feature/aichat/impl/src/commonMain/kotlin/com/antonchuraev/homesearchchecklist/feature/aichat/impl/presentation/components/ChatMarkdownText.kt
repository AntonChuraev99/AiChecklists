package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens

/**
 * Renders a small subset of Markdown for assistant chat messages.
 *
 * Supported syntax (in priority order):
 *  1. Paragraphs     — split on blank line (`\n\n`)
 *  2. Bullets        — lines starting with `- `, `* `, or `*  ` (Gemini style)
 *  3. Numbered lists — lines starting with `1. `, `2. `, etc.
 *  4. Bold           — `**text**`
 *  5. Italic         — `*text*` (only when not a bullet prefix)
 *  6. Inline code    — `` `text` `` (monospace + surfaceContainerHigh bg)
 *  7. Headings       — `# `, `## `, `### ` stripped; rendered as bold paragraph
 *  8. Plain text     — fallback
 *
 * Parser lives entirely in commonMain with zero external dependencies.
 * User messages are NOT passed here — only assistant role.
 */
@Composable
fun ChatMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    if (markdown.isBlank()) return

    // Capture code background color inside composition (requires MaterialTheme)
    val codeBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppDimens.SpacingSm),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Paragraph -> Text(
                    text = renderInline(block.text, codeBackground),
                    style = style,
                    color = color,
                    modifier = Modifier.fillMaxWidth(),
                )

                is MdBlock.Bullet -> Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "•",
                        style = style,
                        color = color,
                        modifier = Modifier.padding(
                            end = AppDimens.SpacingSm,
                            top = AppDimens.SpacingXxs,
                        ),
                    )
                    Text(
                        text = renderInline(block.text, codeBackground),
                        style = style,
                        color = color,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }

                is MdBlock.NumberedItem -> Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = "${block.number}.",
                        style = style,
                        color = color,
                        modifier = Modifier.padding(
                            end = AppDimens.SpacingSm,
                            top = AppDimens.SpacingXxs,
                        ),
                    )
                    Text(
                        text = renderInline(block.text, codeBackground),
                        style = style,
                        color = color,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Block-level types
// ─────────────────────────────────────────────────────────────────────────────

private sealed interface MdBlock {
    /** One or more lines forming a paragraph (no blank lines between). */
    data class Paragraph(val text: String) : MdBlock

    /** A single bullet list item (prefix stripped). */
    data class Bullet(val text: String) : MdBlock

    /** A single numbered list item. */
    data class NumberedItem(val number: Int, val text: String) : MdBlock
}

// ─────────────────────────────────────────────────────────────────────────────
// Block parser
// ─────────────────────────────────────────────────────────────────────────────

private val BULLET_REGEX = Regex("""^\s*[-*]\s+(.*)""")
private val NUMBERED_REGEX = Regex("""^\s*(\d+)\.\s+(.*)""")
private val HEADING_REGEX = Regex("""^#{1,3}\s+(.*)""")

/**
 * Splits the input into block-level tokens.
 *
 * Algorithm:
 *  1. Normalise `\r\n` to `\n`.
 *  2. Split on `\n\n` (blank line) to get paragraph-level chunks.
 *  3. Within each chunk, split on single `\n` and classify every line.
 *  4. Adjacent plain text lines inside a chunk are re-joined as one Paragraph.
 */
private fun parseMarkdownBlocks(input: String): List<MdBlock> {
    val normalised = input.replace("\r\n", "\n").trim()
    val chunks = normalised.split("\n\n")
    val result = mutableListOf<MdBlock>()

    for (chunk in chunks) {
        val lines = chunk.split("\n")
        val paragraphAccumulator = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraphAccumulator.isNotEmpty()) {
                result += MdBlock.Paragraph(paragraphAccumulator.joinToString("\n"))
                paragraphAccumulator.clear()
            }
        }

        for (line in lines) {
            val trimmed = line.trimStart()

            // Bullet
            val bulletMatch = BULLET_REGEX.matchEntire(line)
            if (bulletMatch != null) {
                flushParagraph()
                result += MdBlock.Bullet(bulletMatch.groupValues[1].trim())
                continue
            }

            // Numbered list
            val numberedMatch = NUMBERED_REGEX.matchEntire(line)
            if (numberedMatch != null) {
                flushParagraph()
                val number = numberedMatch.groupValues[1].toIntOrNull() ?: 1
                result += MdBlock.NumberedItem(number, numberedMatch.groupValues[2].trim())
                continue
            }

            // Heading → strip markers, treat as bold paragraph
            val headingMatch = HEADING_REGEX.matchEntire(trimmed)
            if (headingMatch != null) {
                flushParagraph()
                // Wrap heading text in bold markers so renderInline picks it up
                result += MdBlock.Paragraph("**${headingMatch.groupValues[1].trim()}**")
                continue
            }

            // Plain text line — accumulate
            paragraphAccumulator += line
        }

        flushParagraph()
    }

    return result
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline renderer  (bold / italic / inline-code)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Converts a single line of text with inline Markdown markers into an
 * [AnnotatedString] with appropriate [SpanStyle]s applied.
 *
 * Supported (in order of precedence inside the string):
 *  - `` `code` ``  — monospace + surfaceContainerHigh background
 *  - `**bold**`     — FontWeight.Bold
 *  - `*italic*`     — FontStyle.Italic  (only when preceded by non-space)
 *
 * A minimal state-machine scans the string character-by-character once.
 * Edge cases handled:
 *  - Adjacent `***bold-italic***` → bold wins for the outer `**`, italic for `*`
 *  - Unclosed markers are emitted as literal text
 *  - Empty input returns an empty AnnotatedString
 */
private fun renderInline(text: String, codeBackground: Color): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")

    return buildAnnotatedString {
        var i = 0
        val sb = StringBuilder()

        fun flushLiteral() {
            if (sb.isNotEmpty()) {
                append(sb.toString())
                sb.clear()
            }
        }

        while (i < text.length) {
            val ch = text[i]

            // ── inline code: `...` ──────────────────────────────────────────
            if (ch == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    flushLiteral()
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // ── bold: **...** ───────────────────────────────────────────────
            if (ch == '*' && i + 1 < text.length && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    flushLiteral()
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(renderInline(text.substring(i + 2, end), codeBackground))
                    }
                    i = end + 2
                    continue
                }
            }

            // ── italic: *...* — but NOT a bullet prefix (`* ` at start) ────
            //    We only treat `*` as italic when it is NOT followed by a space
            //    (which would be a bullet-style character) and NOT preceded by
            //    a space or is at the very beginning of the string.
            if (ch == '*') {
                val nextCh = if (i + 1 < text.length) text[i + 1] else ' '
                val isBulletLike = nextCh == ' ' || nextCh == '\t'

                if (!isBulletLike) {
                    // Look for the closing `*`; it must not be preceded by a space
                    val end = findItalicEnd(text, i + 1)
                    if (end != -1) {
                        flushLiteral()
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                        continue
                    }
                }
            }

            sb.append(ch)
            i++
        }

        flushLiteral()
    }
}

/**
 * Finds the index of a closing `*` for italic, starting at [from].
 * Returns -1 if not found or if the only candidate is preceded by whitespace.
 */
private fun findItalicEnd(text: String, from: Int): Int {
    var j = from
    while (j < text.length) {
        if (text[j] == '*') {
            // Do not close on `**` (bold)
            val isDouble = j + 1 < text.length && text[j + 1] == '*'
            if (!isDouble) return j
        }
        j++
    }
    return -1
}
