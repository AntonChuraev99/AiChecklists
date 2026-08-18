package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * How the task list renders. Todoist's "Раскладка" row, cut to the two modes that mean something
 * for a flat checklist.
 *
 * Board is deliberately absent: a board needs columns, and columns here would have to come from
 * folders — which the Inbox pager does not render at all (it shows a checklist's leaf items). A
 * layout picker offering a mode that renders identically to another one is worse than a shorter
 * picker.
 */
enum class InboxLayout {
    /** One card per task, 8dp apart — today's look, and the default. */
    CARDS,

    /** Single-line rows separated by hairlines: ~40% more tasks per screen, no per-card chrome. */
    COMPACT,
}

/**
 * Task order within a page.
 *
 * [MANUAL] is the checklist's own item order, which is the ONLY mode that round-trips: it is the
 * order stored in the template, so reordering elsewhere (detail screen, chat, MCP) shows up here.
 * The others are view-level projections and never write back — sorting a list must not silently
 * rewrite the data other surfaces read.
 */
enum class InboxSort {
    MANUAL,
    NAME,

    /** Important tasks first (priority descending), ties keep manual order. */
    PRIORITY,
}

/**
 * Display preferences for the v2 Inbox pager.
 *
 * ## Why global rather than per-checklist
 * Todoist stores these per project because a project is a durable, named workspace the user
 * configures once. Here the same pager shows the Inbox and every checklist, and the user swipes
 * between them constantly — per-page settings would mean the list silently re-sorts itself mid-swipe
 * with no visible cause. One setting for the pager keeps the swipe predictable.
 *
 * Persisted rather than held in the ViewModel: a display preference that resets on every process
 * death reads as the app forgetting what you told it.
 */
data class InboxDisplayOptions(
    val layout: InboxLayout = InboxLayout.CARDS,
    val sort: InboxSort = InboxSort.MANUAL,
    /**
     * Whether completed tasks stay in the list.
     *
     * Default true — hiding them by default would make a just-ticked task vanish, and "did that
     * save?" is a worse first impression than a slightly longer list.
     */
    val showCompleted: Boolean = true,
    /**
     * Whether the list is broken into Overdue / Today / Upcoming / Anytime groups.
     *
     * A **projection**, exactly like [sort]: grouping reads the reminder fields and never writes
     * them, so switching it off restores the stored order byte for byte.
     *
     * Default true, and it costs nothing to a user who has no dated tasks at all: headers are drawn
     * only once **two or more** groups are non-empty, so the common "everything is undated" list
     * renders exactly as it does with grouping off.
     */
    val groupByDate: Boolean = true,
)

interface InboxDisplayPrefsRepository {

    /**
     * Never fails: absent, unknown AND unreadable stored values all fall back to
     * [InboxDisplayOptions]'s defaults, so the flow always emits.
     *
     * The guarantee is load-bearing, not politeness. This is the first fallible source inside
     * `InboxViewModel.screenState`'s combine, and that combine has no `catch` of its own — a throw
     * here would cancel the sharing scope and pin the whole Inbox tab on Loading forever. A display
     * preference is never worth a screen.
     */
    fun observeDisplayOptions(): Flow<InboxDisplayOptions>

    /**
     * When the user last swiped the "plan your day" nudge away, epoch millis; `0` = never.
     *
     * Not part of [InboxDisplayOptions]: that value is what the display sheet edits, and a dismissal
     * timestamp is not an option the user picks — folding it in would put a field on the sheet's own
     * model that the sheet must remember never to render.
     *
     * Carries the same never-fails contract as [observeDisplayOptions] and for the same reason: it
     * is combined into `InboxViewModel.screenState`, whose combine has no `catch` of its own.
     */
    fun observePlanNudgeDismissedAt(): Flow<Long>

    // Writes deliberately PROPAGATE their failure instead of degrading: a swallowed write leaves the
    // sheet showing the old value with nothing to explain why the tap did nothing. The caller owns
    // the user-facing message.
    suspend fun setLayout(layout: InboxLayout)
    suspend fun setSort(sort: InboxSort)
    suspend fun setShowCompleted(show: Boolean)
    suspend fun setGroupByDate(group: Boolean)

    /** @param atMillis when the dismissal happened. The 24h window is computed by the reader. */
    suspend fun setPlanNudgeDismissedAt(atMillis: Long)
}
