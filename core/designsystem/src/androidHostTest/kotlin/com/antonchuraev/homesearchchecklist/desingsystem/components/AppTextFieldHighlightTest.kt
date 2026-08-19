package com.antonchuraev.homesearchchecklist.desingsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.VisualTransformation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The span [AppTextField] paints behind a recognised phrase — asserted by OFFSET, not by looking at
 * a picture.
 *
 * A golden proves that something in the field is tinted. It cannot tell a highlight that covers
 * "tomorrow" from one that covers " tomorro", and that one-character slip is the exact failure mode
 * the whole Smart-Add highlight path is built to avoid (the parser reports offsets into a
 * whitespace-normalised copy of the input). So the frames show that it looks right and these show
 * that it IS right.
 *
 * The second thing under test is the offset mapping. A `VisualTransformation` that changes the
 * character count moves the caret away from where the user tapped — a defect no screenshot can
 * contain, because a screenshot has no caret and no tap.
 *
 * Run: `./gradlew :core:designsystem:testAndroidHostTest --tests "*AppTextFieldHighlightTest*"`
 */
class AppTextFieldHighlightTest {

    private val background = Color(0xFFD3E3FD)
    private val text = AnnotatedString("call mum tomorrow")

    @Test
    fun spanCoversExactlyTheGivenRange() {
        val transformed = spanBackgroundTransformation(9 until 17, background).filter(text)

        val spans = transformed.text.spanStyles
        assertEquals("exactly one span, over the phrase", 1, spans.size)
        assertEquals("start of \"tomorrow\"", 9, spans[0].start)
        assertEquals("end of \"tomorrow\", exclusive", 17, spans[0].end)
        assertEquals(
            "the tinted characters",
            "tomorrow",
            transformed.text.text.substring(spans[0].start, spans[0].end),
        )
    }

    /**
     * Background ONLY. Not bold, not a different text colour — the field shows what the user typed,
     * and re-weighting a fragment of their own sentence reads as the app having edited it.
     */
    @Test
    fun spanCarriesABackgroundAndNothingElse() {
        val style = spanBackgroundTransformation(9 until 17, background).filter(text).text.spanStyles[0].item

        assertEquals(background, style.background)
        assertEquals(
            "no other property may be set — that is the whole visual contract",
            androidx.compose.ui.text.SpanStyle(background = background),
            style,
        )
    }

    /** The characters themselves are untouched, so the caret still lands where it is tapped. */
    @Test
    fun theTextAndItsOffsetsAreUnchanged() {
        val transformed = spanBackgroundTransformation(9 until 17, background).filter(text)

        assertEquals(text.text, transformed.text.text)
        assertSame(OffsetMapping.Identity, transformed.offsetMapping)
    }

    /**
     * Every out-of-contract input degrades to no transformation at all.
     *
     * `VisualTransformation.None` by identity, not merely "no spans": it is what every existing
     * call site of [AppTextField] gets, and identity is the only assertion that proves those four
     * screens render exactly as they did before this parameter existed.
     */
    @Test
    fun degradesToNoTransformation_onEveryOutOfContractRange() {
        assertSame("null range", VisualTransformation.None, spanBackgroundTransformation(null, background))
        assertSame(
            "empty range",
            VisualTransformation.None,
            spanBackgroundTransformation(IntRange.EMPTY, background),
        )
        assertSame(
            "negative start",
            VisualTransformation.None,
            spanBackgroundTransformation(-3 until 4, background),
        )
    }

    /**
     * A range past the end of the string paints NOTHING rather than throwing or clamping.
     *
     * Reachable in one frame: the user deletes while a debounced parse is still in flight, so the
     * range describes a string longer than the one being drawn. Clamping would tint whatever
     * happens to sit at the end; throwing would take the text field down mid-capture.
     */
    @Test
    fun rangePastTheEnd_paintsNothingAndDoesNotThrow() {
        val transformed = spanBackgroundTransformation(9 until 40, background).filter(text)

        assertTrue("no span may be produced", transformed.text.spanStyles.isEmpty())
        assertEquals(text.text, transformed.text.text)
    }
}
