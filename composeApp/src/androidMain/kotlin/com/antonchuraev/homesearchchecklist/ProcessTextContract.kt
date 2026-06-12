package com.antonchuraev.homesearchchecklist

/**
 * Shared contract between [ProcessTextActivity] (the transparent trampoline that catches the
 * system ACTION_PROCESS_TEXT selection-toolbar items) and [MainActivity] (which performs the
 * actual in-app navigation).
 *
 * The system shows FOUR separate Gisti entries in the text-selection toolbar — one per
 * [ProcessTextMode]. Each is a distinct `<activity-alias>` in the manifest pointing at
 * [ProcessTextActivity]; the alias class name identifies which mode was tapped.
 */
object ProcessTextContract {

    /** Internal action MainActivity listens for to route a process-text selection. */
    const val ACTION_PROCESS_TEXT = "com.antonchuraev.aichecklists.ACTION_PROCESS_TEXT"

    /** String extra: the user's selected text. */
    const val EXTRA_TEXT = "com.antonchuraev.aichecklists.extra.PROCESS_TEXT"

    /** String extra: the [ProcessTextMode] name(). */
    const val EXTRA_MODE = "com.antonchuraev.aichecklists.extra.PROCESS_TEXT_MODE"

    /** Fully-qualified class names of the four manifest aliases, mapped to their mode. */
    private const val ALIAS_PACKAGE = "com.antonchuraev.homesearchchecklist"
    const val ALIAS_CREATE_AI = "$ALIAS_PACKAGE.ProcessTextCreateAiAlias"
    const val ALIAS_ADD_TO_EXISTING = "$ALIAS_PACKAGE.ProcessTextAddToExistingAlias"
    const val ALIAS_NEW_CHECKLIST = "$ALIAS_PACKAGE.ProcessTextNewChecklistAlias"
    const val ALIAS_FILL_AI = "$ALIAS_PACKAGE.ProcessTextFillAiAlias"

    /** Resolve a launched alias class name to its [ProcessTextMode]. Null if unrecognized. */
    fun modeForAlias(aliasClassName: String?): ProcessTextMode? = when (aliasClassName) {
        ALIAS_CREATE_AI -> ProcessTextMode.CREATE_AI
        ALIAS_ADD_TO_EXISTING -> ProcessTextMode.ADD_TO_EXISTING
        ALIAS_NEW_CHECKLIST -> ProcessTextMode.NEW_CHECKLIST
        ALIAS_FILL_AI -> ProcessTextMode.FILL_AI
        else -> null
    }
}

/** The four ACTION_PROCESS_TEXT actions a user can pick from the selection toolbar. */
enum class ProcessTextMode {
    /** Open Analyze with prefilled RAW_TEXT (no auto-analyze) — "Checklist from text". */
    CREATE_AI,

    /** Show the existing-checklist picker; append text as one item — "Add to checklist". */
    ADD_TO_EXISTING,

    /** Open the manual Create screen with the text as item(s) — "New checklist". */
    NEW_CHECKLIST,

    /** Open Analyze in fill mode with prefilled RAW_TEXT (no auto-analyze) — "Fill (AI)". */
    FILL_AI,
}
