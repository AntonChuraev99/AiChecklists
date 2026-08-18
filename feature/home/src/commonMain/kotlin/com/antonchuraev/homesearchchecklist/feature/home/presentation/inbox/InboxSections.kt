package com.antonchuraev.homesearchchecklist.feature.home.presentation.inbox

import androidx.compose.runtime.Immutable
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * The four groups the Inbox list can be broken into, in the order they are drawn.
 *
 * Enum ORDER is the rendering order — Overdue first, undated last — so the list never has to carry a
 * separate sort of its own.
 */
enum class InboxSectionKind {
    /** The moment has passed: the reminder, or the next occurrence of a repeat, is behind `now`. */
    OVERDUE,

    /** Due later in the current local day. */
    TODAY,

    /** Due after the end of the current local day. */
    UPCOMING,

    /** No reminder and no repeat rule. The common case, and the one the plan nudge speaks to. */
    ANYTIME,
}

/**
 * One rendered run of tasks.
 *
 * @property header the group these tasks belong to, or **null** when no heading is to be drawn —
 *   see [sectionInboxTasks] for when that happens. A nullable header rather than a sibling
 *   `showHeaders` flag on the list: the flag can disagree with the data, this cannot.
 */
@Immutable
data class InboxTaskSection(
    val header: InboxSectionKind?,
    val tasks: List<InboxTask>,
)

/**
 * Groups one page's tasks by how far away their due date is.
 *
 * ## A projection, like the sort
 * Nothing here writes back. Grouping READS `reminderAt` / `repeatRule` / `repeatNextAt` and rebuilds
 * the display order; the stored template order is untouched, so switching the option off restores
 * the list byte for byte. That is the same contract `InboxSort` already carries, and it is what lets
 * both live on the same sheet.
 *
 * ## 🔴 The invariant: headings need TWO groups
 * With fewer than two non-empty groups this returns the incoming list **verbatim, in its incoming
 * order, with no header** — i.e. exactly what the screen renders today. Two reasons, and both are
 * load-bearing:
 *
 *  - For the overwhelming majority of users every task is undated, so a lone "Anytime" over the
 *    whole list would be pure noise *and* an implication that something is missing.
 *  - The test that matters is the number of NON-EMPTY GROUPS, not "does anything have a date". A
 *    user whose tasks are all overdue has dated tasks and still must not be met by a solitary
 *    "Overdue" heading over everything they own.
 *
 * The order is returned untouched in that case as well, not merely un-headed: with one group there
 * is no grouping to express, so re-sorting the list by time would be a silent reorder of the user's
 * manual order for no visible reason.
 *
 * ## Order inside a group
 * The three dated groups sort by time ascending — oldest first in Overdue, next-up first in Today
 * and Upcoming — because within a run of dated tasks "when" is the only question. [InboxSectionKind.ANYTIME]
 * keeps the incoming order, which is already the user's chosen [InboxSort] projection: there is no
 * time to sort by, so the selected sort is what survives.
 *
 * @param tasks the page's tasks, already filtered and ordered by the display options.
 * @param groupByDate the user's `Group by date` setting. `false` short-circuits to a single unheaded
 *   run — today's behaviour, byte for byte.
 * @param nowMillis the clock. A parameter, not an ambient read, so a test pins it and so the screen
 *   can hold the value steady while the user is scrolling.
 * @param timeZone zone the "current day" boundary is resolved in.
 */
fun sectionInboxTasks(
    tasks: List<InboxTask>,
    groupByDate: Boolean,
    nowMillis: Long,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
): List<InboxTaskSection> {
    val flat = listOf(InboxTaskSection(header = null, tasks = tasks))
    if (!groupByDate || tasks.isEmpty()) return flat

    val today = epochDayOf(nowMillis, timeZone)
    val buckets = tasks.groupBy { it.sectionKind(nowMillis, today, timeZone) }

    // Counted over the KINDS, never over "are there dated tasks" — see the KDoc.
    val present = InboxSectionKind.entries.filter { !buckets[it].isNullOrEmpty() }
    if (present.size < MIN_SECTIONS_FOR_HEADERS) return flat

    return present.map { kind ->
        val bucket = buckets.getValue(kind)
        InboxTaskSection(
            header = kind,
            tasks = if (kind == InboxSectionKind.ANYTIME) bucket else bucket.sortedBy { it.dueAtMillis() },
        )
    }
}

/** Whether the task carries neither a one-shot reminder nor a repeat rule. */
fun InboxTask.isUndated(): Boolean = item.reminderAt == null && item.repeatRule == null

/**
 * The instant this task is due, or `null` when it is undated.
 *
 * A repeat rule outranks a one-shot timestamp when both are set, matching `resolveDueLabel` — the
 * row's own chip describes the rule, so the row must be filed under the rule's next occurrence or
 * the chip and the heading above it would disagree.
 */
private fun InboxTask.dueAtMillis(): Long? =
    if (item.repeatRule != null) item.repeatNextAt else item.reminderAt

private fun InboxTask.sectionKind(
    nowMillis: Long,
    todayEpochDay: Long,
    timeZone: TimeZone,
): InboxSectionKind {
    if (isUndated()) return InboxSectionKind.ANYTIME
    // A repeating task whose series has run out (or whose next occurrence has not been computed
    // yet) has a rule but no date. It is NOT undated — the user did schedule it — so it stays out
    // of Anytime and files under Upcoming, which is also the colour its chip already shows.
    val due = dueAtMillis() ?: return InboxSectionKind.UPCOMING

    return when {
        due < nowMillis -> InboxSectionKind.OVERDUE
        epochDayOf(due, timeZone) == todayEpochDay -> InboxSectionKind.TODAY
        else -> InboxSectionKind.UPCOMING
    }
}

/**
 * Local calendar day as a day count since the epoch.
 *
 * Compared as day numbers rather than against a computed midnight so the boundary cannot be off by
 * the DST hour, and so a task at 23:59 and one at 00:01 land on the days a person would name.
 */
private fun epochDayOf(millis: Long, timeZone: TimeZone): Long =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(timeZone).date.toEpochDays()

/** Below this many non-empty groups the list renders unheaded. See [sectionInboxTasks]. */
private const val MIN_SECTIONS_FOR_HEADERS = 2
