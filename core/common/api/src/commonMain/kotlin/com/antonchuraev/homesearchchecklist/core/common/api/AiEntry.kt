package com.antonchuraev.homesearchchecklist.core.common.api

import kotlinx.serialization.Serializable

/**
 * The MATERIAL a user hands to the AI — a closed set shared by navigation and analytics.
 *
 * Lives in `core:common:api` rather than in `feature:analyze` because three unrelated layers need
 * the same vocabulary and none of them may depend on the analyze feature: `AppNavRoute.Analyze`
 * carries it as an argument, the v2 capture dock and the Templates gallery emit it on
 * `ai_entry_tapped`, and `AnalyzeViewModel` maps it onto its own `InputDataType`. A copy per layer
 * is exactly how a funnel ends up joining `link` against `web_link` and reading as zero.
 *
 * ## [wire] is frozen — it is 30+ days of history
 * These strings are NOT free to rename. `ai_analyze_started` has been emitting
 * `selectedInputType.name.lowercase()` since before this enum existed, so `WEB_LINK` is on the wire
 * as `web_link` and `RAW_TEXT` as `raw_text`. The entry event reuses those exact spellings so that
 * `ai_entry_tapped → ai_analyze_started` joins on an equal `input_type`; shortening them to `link`
 * and `text` here would silently split every cross-event funnel while both events kept looking
 * healthy on their own. Keep [wire] in lockstep with `InputDataType.name.lowercase()`.
 */
@Serializable
enum class AnalyzeInputKind(val wire: String) {
    PHOTO("photo"),
    PDF("pdf"),
    WEB_LINK("web_link"),
    VOICE("voice"),
    RAW_TEXT("raw_text"),
}

/**
 * The pair "which material, from which door", travelling together from a tapped affordance to the
 * Analyze ViewModel.
 *
 * One object rather than two loose parameters for a concrete reason: Koin's `ParametersHolder`
 * destructures only as far as `component5()`, and Analyze already consumed four positional slots.
 * Grouping them also makes the pair impossible to half-wire — a door that sets the material but
 * forgets the source is exactly how `ai_analyze_started` ends up unattributable again.
 */
data class AnalyzeEntryArgs(
    val inputKind: AnalyzeInputKind? = null,
    val source: AiEntrySource? = null,
)

/** Whether an AI entry affordance opens the Analyze flow or the chat-backed "create with AI". */
@Serializable
enum class AiEntryDestination(val wire: String) {
    ANALYZE("analyze"),
    AI_CREATE("ai_create"),
}

/**
 * WHICH affordance the user tapped to reach an AI flow.
 *
 * A closed enum rather than a free string so a surface cannot be invented at the call site: the v2
 * shell shipped `AppCreditsChip` and the Analyze entry with no `source` at all, and the resulting
 * "nobody wanted it" reading is what this vocabulary exists to prevent.
 *
 * ⚠️ Add a constant only together with the call site that emits it. A value with no emitter is a
 * lie in the taxonomy — Amplitude registers a name on first ingest, so an unemitted constant simply
 * never appears and the next reader cannot tell "never built" from "built and unused".
 */
@Serializable
enum class AiEntrySource(val wire: String) {
    /** Source row inside the v2 quick-capture dock, Inbox tab. */
    CAPTURE_DOCK_INBOX("capture_dock_inbox"),

    /** Source row inside the v2 quick-capture dock, Calendar tab. */
    CAPTURE_DOCK_CALENDAR("capture_dock_calendar"),

    /** The source row on an Inbox page with NO tasks at all. */
    INBOX_EMPTY("inbox_empty"),

    /**
     * The same row on a sparse Inbox page — one or two tasks, not yet zero.
     *
     * Named after the LIST STATE, matching its sibling [INBOX_EMPTY], and deliberately not after a
     * position: this value spent one draft as `inbox_header` while the row rendered at the FOOT of
     * the short list, under the add-task row (which owns the "last element" slot by an owner
     * decision). A taxonomy value that describes where a thing sits is wrong the first time the
     * layout moves, and every later reader inherits the wrong reading with nothing to detect it.
     * Renamed before the first ingest — Amplitude registers a name on first event and after that
     * the fix costs a split series.
     */
    INBOX_SPARSE("inbox_sparse"),

    /** Full-width row under the Templates search field, above the category list. */
    TEMPLATES_HEADER("templates_header"),

    /** CTA in the Templates "no templates at all" empty state. */
    TEMPLATES_EMPTY("templates_empty"),

    /** CTA in the Templates "search matched nothing" empty state — carries the query. */
    TEMPLATES_EMPTY_SEARCH("templates_empty_search"),
}
