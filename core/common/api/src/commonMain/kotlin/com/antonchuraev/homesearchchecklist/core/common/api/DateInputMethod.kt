package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * HOW the due date on a captured task was set — the `date_input_method` dimension of
 * [AnalyticsEvents.Inbox.QUICK_ADDED].
 *
 * The point of the split is that "tapped a preset" and "opened a calendar" are the same outcome and
 * opposite UX verdicts: the rail exists precisely to make the first one cheap, so a rail that moves
 * [HAS_DUE_DATE][AnalyticsParams.HAS_DUE_DATE] while every date arrives through [PICKER] has not
 * done its job — it has only made the old detour easier to find.
 *
 * Lives in `core:common:api` for the same reason [AnalyzeInputKind] does: the value is produced in
 * `feature:home` (two capture ViewModels) and read by the analytics layer, and a copy per producer
 * is how two spellings of one dimension end up splitting a single series.
 *
 * ## [wire] is the analytics contract
 * Amplitude registers a property value on first ingest; renaming one later costs a split series that
 * cannot be merged retroactively. Treat these five strings as frozen once shipped.
 */
enum class DateInputMethod(val wire: String) {

    /** One of the rail's preset chips or the planner grid's cells — the one-tap path. */
    PRESET("preset"),

    /**
     * The date/time picker behind the planner's "Pick date" / "Time" controls, i.e. the v1
     * `ReminderSheet` flow.
     *
     * Counts the DETOUR the rail is meant to make unnecessary. Deliberately not folded into [PRESET]
     * even though both end in a stored `reminderAt`: the whole design bet is that the detour is what
     * kept 96.6% of tasks undated.
     */
    PICKER("picker"),

    /**
     * Smart-Add recognised a date inside the typed text and nothing overrode it — the user never
     * touched a date control at all.
     *
     * Its own value rather than [PRESET] because it measures the PARSER, not the rail: a rise here is
     * an argument for teaching the lexicon more phrases, a rise in [PRESET] is an argument for more
     * chips.
     */
    PARSED_FROM_TEXT("parsed_from_text"),

    /**
     * The task was captured with no due date at all.
     *
     * Emitted rather than omitted: absence-as-a-value cannot be counted as a share of anything, and
     * this is the denominator half of the rail's primary metric.
     */
    NONE("none"),
}
