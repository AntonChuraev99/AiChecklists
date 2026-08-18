package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Date grouping for the Inbox list.
 *
 * The clock and the zone are BOTH pinned: a "which day is this" rule tested against the machine's
 * own clock passes at 10:00 and fails at 23:59, and a zone-dependent one fails only for whoever
 * runs CI in another country.
 *
 * Run:
 *   ./gradlew :feature:home:testAndroidHostTest --tests "*InboxSectionsTest*"
 */
class InboxSectionsTest {

    // ── 🔴 The invariant: headings need two non-empty groups ─────────────────

    /**
     * The case that describes almost every user of this app: nothing has a date.
     *
     * Catches a lone "Anytime" heading over the whole list — noise, and an implication that
     * something is missing from tasks that are perfectly fine as they are.
     */
    @Test
    fun allTasksUndated_producesOneUnheadedRun() {
        val tasks = listOf(undated("a"), undated("b"), undated("c"))

        val sections = sectionInboxTasks(tasks = tasks, groupByDate = true, nowMillis = NOW, timeZone = ZONE)

        assertEquals(1, sections.size, "one run, not four")
        assertNull(sections.single().header, "a single group must draw no heading at all")
        assertEquals(tasks, sections.single().tasks, "and the order must be untouched")
    }

    /**
     * The trap the design spec calls out by name (§10 п.14): testing "does anything have a date"
     * instead of counting NON-EMPTY GROUPS.
     *
     * A user whose every task is overdue has dated tasks — and must still not be met by a solitary
     * "Overdue" heading over everything they own.
     */
    @Test
    fun allTasksOverdue_stillProducesOneUnheadedRun() {
        val tasks = listOf(
            dueAt("a", NOW - 1.hours),
            dueAt("b", NOW - 2.hours),
            dueAt("c", NOW - 3.hours),
        )

        val sections = sectionInboxTasks(tasks = tasks, groupByDate = true, nowMillis = NOW, timeZone = ZONE)

        assertEquals(1, sections.size)
        assertNull(
            sections.single().header,
            "one non-empty group is one group, whichever group it happens to be",
        )
    }

    /**
     * And the order is returned verbatim in that case, not merely un-headed.
     *
     * With one group there is no grouping to express, so sorting the run by time would silently
     * reorder the user's own manual order for no visible reason — `b` was put first and stays first,
     * even though its time is later.
     */
    @Test
    fun singleGroup_keepsTheIncomingOrderRatherThanSortingByTime() {
        val later = dueAt("b", NOW - 1.hours)
        val earlier = dueAt("a", NOW - 5.hours)

        val sections = sectionInboxTasks(
            tasks = listOf(later, earlier),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(listOf(later, earlier), sections.single().tasks)
    }

    /** Two non-empty groups is where headings start. */
    @Test
    fun twoNonEmptyGroups_drawHeadings() {
        val sections = sectionInboxTasks(
            tasks = listOf(undated("a"), dueAt("b", NOW - 1.hours)),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(
            listOf(InboxSectionKind.OVERDUE, InboxSectionKind.ANYTIME),
            sections.map { it.header },
            "overdue first, undated last — the enum order is the render order",
        )
    }

    // ── Which group a task lands in ──────────────────────────────────────────

    @Test
    fun tasksAreFiledByHowFarAwayTheirDateIs() {
        val sections = sectionInboxTasks(
            tasks = listOf(
                undated("anytime"),
                dueAt("overdue", NOW - 1.hours),
                dueAt("today", NOW + 2.hours),
                dueAt("upcoming", NOW + 30.hours),
            ),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        // Explicit type argument: `header` is nullable, so inference cannot reconcile the literal
        // map's non-null keys with the actual's `InboxSectionKind?` ones on its own.
        assertEquals<Map<InboxSectionKind?, List<String>>>(
            mapOf(
                InboxSectionKind.OVERDUE to listOf("overdue"),
                InboxSectionKind.TODAY to listOf("today"),
                InboxSectionKind.UPCOMING to listOf("upcoming"),
                InboxSectionKind.ANYTIME to listOf("anytime"),
            ),
            sections.associate { section -> section.header to section.tasks.map { it.text } },
        )
    }

    /**
     * A time earlier TODAY is overdue, not "today".
     *
     * The two rules overlap by construction — 09:00 is both in the past and in the current day — and
     * the past wins, matching the chip the row already draws for itself.
     */
    @Test
    fun aTimeEarlierTodayIsOverdueRatherThanToday() {
        val sections = sectionInboxTasks(
            tasks = listOf(dueAt("missed", NOW - 1.hours), dueAt("later", NOW + 1.hours)),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(listOf(InboxSectionKind.OVERDUE, InboxSectionKind.TODAY), sections.map { it.header })
    }

    /**
     * A repeat rule outranks a one-shot timestamp, exactly as `resolveDueLabel` does.
     *
     * Without that the heading and the chip on the row underneath it would describe two different
     * dates for the same task.
     */
    @Test
    fun aRepeatingTaskIsFiledByItsNextOccurrence() {
        val repeating = InboxTask(
            item = ChecklistFillItem(text = "standup", checked = false)
                .withReminderAt(NOW - 10.hours)
                .withRepeatRule(
                    rule = ReminderRepeatRule(type = RepeatType.DAILY),
                    timeOfDayMinutes = 9 * 60,
                    nextAt = NOW + 20.hours,
                ),
        )

        val sections = sectionInboxTasks(
            tasks = listOf(repeating, undated("a")),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(
            listOf(InboxSectionKind.UPCOMING, InboxSectionKind.ANYTIME),
            sections.map { it.header },
            "the stale reminderAt must not drag a live repeating task into Overdue",
        )
    }

    /**
     * A repeating task whose series has run out has a rule but no next date.
     *
     * It is NOT undated — the user did schedule it — so it must stay out of "Anytime", where it
     * would be counted as something still to plan.
     */
    @Test
    fun anExhaustedRepeatIsNotFiledAsUndated() {
        val exhausted = InboxTask(
            item = ChecklistFillItem(text = "done series", checked = false)
                .withRepeatRule(
                    rule = ReminderRepeatRule(type = RepeatType.DAILY),
                    timeOfDayMinutes = 9 * 60,
                    nextAt = NOW + 1.hours,
                )
                .withRepeatAdvanced(nextAt = null, newCount = 5),
        )

        val sections = sectionInboxTasks(
            tasks = listOf(exhausted, undated("a")),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(listOf(InboxSectionKind.UPCOMING, InboxSectionKind.ANYTIME), sections.map { it.header })
    }

    // ── Order inside a group ─────────────────────────────────────────────────

    /** Oldest first in Overdue: the thing that has been waiting longest is at the top. */
    @Test
    fun datedGroupsSortAscendingByTime() {
        val sections = sectionInboxTasks(
            tasks = listOf(
                dueAt("recent", NOW - 1.hours),
                dueAt("ancient", NOW - 40.hours),
                dueAt("older", NOW - 5.hours),
                undated("anytime"),
            ),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(
            listOf("ancient", "older", "recent"),
            sections.first { it.header == InboxSectionKind.OVERDUE }.tasks.map { it.text },
        )
    }

    /**
     * The undated group keeps the incoming order — which is whatever `InboxSort` produced.
     *
     * There is no time to sort by there, so the user's chosen sort is what has to survive; grouping
     * is a projection ON TOP of it, not a replacement for it.
     */
    @Test
    fun theUndatedGroupKeepsTheSortItArrivedIn() {
        val incoming = listOf(undated("c"), undated("a"), undated("b"))

        val sections = sectionInboxTasks(
            tasks = incoming + dueAt("dated", NOW - 1.hours),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(
            incoming,
            sections.first { it.header == InboxSectionKind.ANYTIME }.tasks,
        )
    }

    // ── The switch ───────────────────────────────────────────────────────────

    /** Off is today's behaviour, byte for byte: one run, no heading, original order. */
    @Test
    fun groupingOff_returnsTheListVerbatim() {
        val tasks = listOf(
            dueAt("upcoming", NOW + 30.hours),
            dueAt("overdue", NOW - 1.hours),
            undated("anytime"),
        )

        val sections = sectionInboxTasks(tasks = tasks, groupByDate = false, nowMillis = NOW, timeZone = ZONE)

        assertEquals(1, sections.size)
        assertNull(sections.single().header)
        assertEquals(tasks, sections.single().tasks)
    }

    /** Nothing to group. The empty run still exists so the list has a section to iterate. */
    @Test
    fun anEmptyPage_producesOneEmptyUnheadedRun() {
        val sections = sectionInboxTasks(
            tasks = emptyList(),
            groupByDate = true,
            nowMillis = NOW,
            timeZone = ZONE,
        )

        assertEquals(1, sections.size)
        assertNull(sections.single().header)
        assertTrue(sections.single().tasks.isEmpty())
    }

    // ── Undated, as the plan nudge counts it ─────────────────────────────────

    @Test
    fun isUndated_isFalseForAnythingCarryingAScheduleOfAnyKind() {
        assertTrue(undated("a").isUndated())
        assertTrue(!dueAt("b", NOW).isUndated())
        assertTrue(
            !InboxTask(
                item = ChecklistFillItem(text = "c", checked = false).withRepeatRule(
                    rule = ReminderRepeatRule(type = RepeatType.DAILY),
                    timeOfDayMinutes = 0,
                    nextAt = NOW,
                ),
            ).isUndated(),
            "a repeating task is scheduled even when its next occurrence is not known",
        )
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private fun undated(text: String) =
        InboxTask(item = ChecklistFillItem(text = text, checked = false))

    private fun dueAt(text: String, atMillis: Long) =
        InboxTask(item = ChecklistFillItem(text = text, checked = false).withReminderAt(atMillis))

    private val Int.hours: Long get() = this * 60L * 60L * 1000L

    private companion object {
        /**
         * A fixed zone with no DST surprises, and a fixed "now" at midday.
         *
         * Midday matters: at 23:00 a "+2h" fixture would be tomorrow and half these tests would
         * assert the wrong group — the failure would look like a bug in the production rule.
         */
        val ZONE = TimeZone.of("UTC")
        val NOW = LocalDateTime(2026, 8, 13, 12, 0).toInstant(ZONE).toEpochMilliseconds()
    }
}
