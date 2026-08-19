package com.antonchuraev.homesearchchecklist.feature.home.presentation.create

import com.antonchuraev.homesearchchecklist.core.common.api.DateInputMethod
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.SmartDateParser
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ChipDisplay
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.DayKey
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.parser.model.ParsedDateToken
import com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder.PendingRepeatConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * Smart-Add in the capture dock: the wire from typed text to `TaskDraft.parsedToken`, and the rules
 * that decide whether the recognised phrase is allowed to answer "when".
 *
 * Until 2026-08-19 there was no wire. `TaskDraft.parsedToken` was read by the leading chip and by
 * nothing else wrote it, so the chip's third branch, the value `DateInputMethod.PARSED_FROM_TEXT`
 * and the whole "type the date" half of the dock were unreachable — a feature that existed only as
 * a field. That is the shape these tests defend: not "the parser works" (its own suite owns that),
 * but that the host RUNS it, that what the dock shows is what Send writes, and that a tapped chip
 * still wins.
 *
 * The parser is a fake in every case. A real one would tie each assertion to today's date and to
 * whichever words the lexicon happens to carry, which is the parser's contract, not this one's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CaptureDockSmartAddTest {

    /**
     * The collector runs in a PLAIN scope on the test dispatcher, never in `runTest`'s
     * `backgroundScope` — `advanceUntilIdle()` deliberately skips that one (its coroutines are
     * infinite by definition), so a debounce parked there never fires and every assertion below
     * would read "the parser was never called" against a perfectly wired host.
     */
    private val dispatcher = StandardTestDispatcher()
    private val hostScope = CoroutineScope(dispatcher)

    @AfterTest
    fun tearDown() = hostScope.cancel()

    private val tz = TimeZone.of("Europe/Moscow")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime(LocalDate(year, month, day), LocalTime(hour, minute)).toInstant(tz)

    private val tomorrowNineAm = at(2026, 8, 20, 9, 0).toEpochMilliseconds()

    // ── The wire ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun recognisedPhrase_reachesTheDraftAfterTypingStops() = runTest(dispatcher) {
        val parser = RecordingParser { dayToken(tomorrowNineAm) }
        val draft = MutableStateFlow(TaskDraft())
        hostScope.observeDraftTextForSmartAdd(draft, parser, now = { 0L }, timeZone = { tz })

        draft.update { it.copy(text = "call mum tomorrow") }
        advanceUntilIdle()

        assertEquals(
            tomorrowNineAm,
            draft.value.parsedToken?.reminderAt,
            "The phrase the user typed has to reach the draft — this is the wire that did not exist",
        )
    }

    /**
     * The debounce, stated as the thing it protects: the parser is a regex cascade over six priority
     * levels in two languages, and this field is typed into.
     *
     * Seventeen characters, ONE parse. Without the debounce this assertion reads 17 — which is what
     * running the cascade per keystroke costs, on the main-thread-adjacent path of a text field.
     */
    @Test
    fun parserIsNotRunPerKeystroke() = runTest(dispatcher) {
        val parser = RecordingParser { dayToken(tomorrowNineAm) }
        val draft = MutableStateFlow(TaskDraft())
        hostScope.observeDraftTextForSmartAdd(draft, parser, now = { 0L }, timeZone = { tz })

        val typed = "call mum tomorrow"
        typed.indices.forEach { i ->
            draft.update { it.copy(text = typed.substring(0, i + 1)) }
            advanceTimeBy(20)
        }
        advanceUntilIdle()

        assertEquals(
            listOf(typed),
            parser.calls,
            "Only the settled text may reach the parser, once",
        )
    }

    /** Nothing typed, nothing parsed — a blank field must not even reach the cascade. */
    @Test
    fun blankText_isNotParsed() = runTest(dispatcher) {
        val parser = RecordingParser { dayToken(tomorrowNineAm) }
        val draft = MutableStateFlow(TaskDraft(text = "tomorrow", parsedToken = dayToken(tomorrowNineAm)))
        hostScope.observeDraftTextForSmartAdd(draft, parser, now = { 0L }, timeZone = { tz })

        draft.update { it.copy(text = "   ") }
        advanceUntilIdle()

        assertTrue(parser.calls.isEmpty(), "A blank field is not a phrase")
        assertNull(draft.value.parsedToken, "and it must clear whatever the last phrase left behind")
    }

    /**
     * A recognised REPEAT is dropped rather than staged.
     *
     * Two independent reasons, either of them sufficient: a recurring reminder is behind the
     * free-tier gate the planner's Repeat control enforces, and the draft can only carry a repeat as
     * a `PendingRepeatConfig`, which this token cannot produce — so staging it would file a one-shot
     * at the rule's first occurrence while the chip said "Weekly".
     */
    @Test
    fun recognisedRepeat_isNotStaged() = runTest(dispatcher) {
        val parser = RecordingParser {
            dayToken(tomorrowNineAm).copy(
                repeatRule = ReminderRepeatRule(type = RepeatType.WEEKLY),
            )
        }
        val draft = MutableStateFlow(TaskDraft())
        hostScope.observeDraftTextForSmartAdd(draft, parser, now = { 0L }, timeZone = { tz })

        draft.update { it.copy(text = "water the plants every monday") }
        advanceUntilIdle()

        assertNull(
            draft.value.parsedToken,
            "A repeat the dock cannot stage must not be shown as if it had been",
        )
    }

    /**
     * The `x` on the leading chip has to STAY clicked.
     *
     * [TaskDraft.parsedToken] is a live derivative of the text, so without
     * [TaskDraft.dueDismissed] the clear would last exactly until the next character re-derived the
     * same phrase — a control that visibly does nothing.
     */
    @Test
    fun dismissedDate_isNotRevivedByFurtherTyping() = runTest(dispatcher) {
        val parser = RecordingParser { dayToken(tomorrowNineAm) }
        val draft = MutableStateFlow(TaskDraft())
        hostScope.observeDraftTextForSmartAdd(draft, parser, now = { 0L }, timeZone = { tz })

        draft.update { it.copy(text = "call mum tomorrow") }
        advanceUntilIdle()
        // The `x` on the leading chip, exactly as DraftDueController applies it.
        draft.update { it.withCustomReminder(null) }
        draft.update { it.copy(text = "call mum tomorrow!") }
        advanceUntilIdle()

        assertNull(
            draft.value.parsedToken,
            "A date the user removed must not come back on the next keystroke",
        )
    }

    /** Answering "when" again re-arms Smart-Add: dismissal is scoped to the draft, not permanent. */
    @Test
    fun dismissalIsLiftedByANewAnswer() = runTest(dispatcher) {
        val parser = RecordingParser { dayToken(tomorrowNineAm) }
        val draft = MutableStateFlow(TaskDraft(text = "call mum tomorrow").withCustomReminder(null))
        hostScope.observeDraftTextForSmartAdd(draft, parser, now = { 0L }, timeZone = { tz })

        draft.update { it.withPreset(ItemCreateReminderPreset.TONIGHT, at(2026, 8, 19, 14, 0), tz) }
        // …and off again: re-tapping the active chip clears the CHIP, not the question.
        draft.update { it.withPreset(ItemCreateReminderPreset.TONIGHT, at(2026, 8, 19, 14, 0), tz) }
        draft.update { it.copy(text = "call mum tomorrow please") }
        advanceUntilIdle()

        assertEquals(
            tomorrowNineAm,
            draft.value.parsedToken?.reminderAt,
            "Re-tapping a preset means 'not this chip', not 'no date' — the phrase may answer again",
        )
    }

    // ── Precedence, and the value Send actually writes ───────────────────────────────────────

    /**
     * The rule the whole feature hangs on: a tap is a decision, a recognised phrase is a guess about
     * one. Both are present here and the chip has to win — in the value written AND in the value
     * reported.
     */
    @Test
    fun tappedChip_beatsTheRecognisedPhrase() {
        val now = at(2026, 8, 19, 14, 0)
        val draft = TaskDraft(text = "call mum tomorrow", parsedToken = dayToken(tomorrowNineAm))
            .withPreset(ItemCreateReminderPreset.TONIGHT, now, tz)

        val outcome = draft.resolveDueOutcome(now, tz)

        assertEquals(
            at(2026, 8, 19, 18, 0).toEpochMilliseconds(),
            outcome.reminderAt,
            "Tonight was TAPPED; the parsed tomorrow must not overwrite it",
        )
        assertEquals(DateInputMethod.PRESET, draft.dateInputMethod(outcome.dueAtMillis))
    }

    /**
     * With no chip active the phrase IS the answer — written on Send and reported as
     * [DateInputMethod.PARSED_FROM_TEXT], the value that could not be produced at all before the
     * wire existed.
     */
    @Test
    fun recognisedPhrase_isWhatSendWrites_whenNoChipIsActive() {
        val now = at(2026, 8, 19, 14, 0)
        val draft = TaskDraft(text = "call mum tomorrow", parsedToken = dayToken(tomorrowNineAm))

        val outcome = draft.resolveDueOutcome(now, tz)

        assertEquals(
            tomorrowNineAm,
            outcome.reminderAt,
            "The date on the chip and the date on the task have to be the same date",
        )
        assertEquals(
            DateInputMethod.PARSED_FROM_TEXT,
            draft.dateInputMethod(outcome.dueAtMillis),
            "…and the funnel has to be able to tell this apart from a tapped preset",
        )
    }

    /**
     * A phrase whose moment has already gone by the time Send is pressed is DROPPED and said out
     * loud, exactly as a picked time is — the dock stays open while the user types, and a trigger in
     * the past makes AlarmManager fire the instant the task is created.
     */
    @Test
    fun recognisedPhrase_thatWentStaleWhileTyping_isDroppedLoudly() {
        val now = at(2026, 8, 20, 10, 0)
        val draft = TaskDraft(text = "call mum at 9", parsedToken = dayToken(tomorrowNineAm))

        val outcome = draft.resolveDueOutcome(now, tz)

        assertNull(outcome.reminderAt, "A moment that has passed must not be written")
        assertTrue(outcome.pickedTimeExpired, "and the host must be told, so it can say so")
    }

    /** A staged repeat is a different answer to "when" and still outranks the phrase. */
    @Test
    fun stagedRepeat_beatsTheRecognisedPhrase() {
        val now = at(2026, 8, 19, 14, 0)
        val draft = TaskDraft(text = "water the plants tomorrow", parsedToken = dayToken(tomorrowNineAm))
            .withRepeat(pendingWeekly())

        val outcome = draft.resolveDueOutcome(now, tz)

        assertNull(outcome.reminderAt, "A repeating task carries a rule, not a one-shot")
        assertEquals(DateInputMethod.PICKER, draft.dateInputMethod(outcome.dueAtMillis))
    }

    // ── Which characters the field may tint ─────────────────────────────────────────────────
    //
    // `ParsedDateToken`'s offsets address the parser's whitespace-NORMALISED copy of the input, not
    // the string in the text field. These cases are the whole contract of `smartAddHighlightRange`:
    // tint when the offsets provably land on the phrase, show nothing at all when they do not.
    // There is deliberately no case where it returns a "best effort" range.

    /** The ordinary case: nothing to normalise, so the parser's offsets already fit the raw text. */
    @Test
    fun highlightRange_coversThePhrase_whenTheTextNeededNoNormalising() {
        val draft = TaskDraft(text = "call mum tomorrow", parsedToken = dayToken(tomorrowNineAm))

        assertEquals(
            9 until 17,
            draft.smartAddHighlightRange(),
            "tomorrow sits at 9..16 in this string and the token says so",
        )
    }

    /**
     * ONE leading space, and every offset the parser reported is now one character early.
     *
     * The assertion is `null`, not "a range shifted by one": a highlight covering a space and a
     * clipped word tells the user the app read something other than it did, immediately before it
     * writes a date from that reading.
     */
    @Test
    fun highlightRange_isNothing_whenALeadingSpaceShiftedTheOffsets() {
        val draft = TaskDraft(text = " call mum tomorrow", parsedToken = dayToken(tomorrowNineAm))

        assertNull(
            draft.smartAddHighlightRange(),
            "Offsets that no longer land on the phrase must give NO highlight, never a shifted one",
        )
    }

    /** The same failure from inside the string — a double space the parser collapsed. */
    @Test
    fun highlightRange_isNothing_whenADoubleSpaceShiftedTheOffsets() {
        val draft = TaskDraft(text = "call  mum tomorrow", parsedToken = dayToken(tomorrowNineAm))

        assertNull(draft.smartAddHighlightRange(), "A collapsed run shifts every later offset")
    }

    /**
     * The text got SHORTER than the offsets — the user deleted while a parse was in flight.
     *
     * Worth its own case because it is the one that throws rather than merely lying: substring past
     * the end takes the whole text field down, inside a VisualTransformation, on the capture path.
     */
    @Test
    fun highlightRange_isNothing_whenTheTextIsShorterThanTheOffsets() {
        val draft = TaskDraft(text = "call mum", parsedToken = dayToken(tomorrowNineAm))

        assertNull(draft.smartAddHighlightRange(), "Out-of-bounds offsets must not reach substring()")
    }

    /** No parse, no highlight — the state every non-dock call site of the field is in. */
    @Test
    fun highlightRange_isNothing_withoutAParse() {
        assertNull(TaskDraft(text = "call mum tomorrow").smartAddHighlightRange())
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────────────────────

    private fun dayToken(at: Long?): ParsedDateToken = ParsedDateToken(
        originalSubstring = "tomorrow",
        startIndex = 9,
        endIndex = 17,
        display = ChipDisplay.RelativeDay(DayKey.TOMORROW, null),
        reminderAt = at,
        repeatRule = null,
        timeOfDayMinutes = null,
    )

    private fun pendingWeekly() = PendingRepeatConfig(
        type = RepeatType.WEEKLY,
        timeHour = 9,
        timeMinute = 0,
    )

    /**
     * Records what it was asked to parse. The call LIST, not a count: "parsed once" and "parsed once
     * with the settled text" are different claims, and only the second one is worth making.
     */
    private class RecordingParser(
        private val result: (String) -> ParsedDateToken?,
    ) : SmartDateParser {
        val calls = mutableListOf<String>()

        override fun parse(input: String, now: Long, timeZone: TimeZone): ParsedDateToken? {
            calls += input
            return result(input)
        }
    }
}
