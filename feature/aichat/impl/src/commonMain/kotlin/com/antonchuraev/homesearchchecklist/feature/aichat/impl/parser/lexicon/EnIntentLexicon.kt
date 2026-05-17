package com.antonchuraev.homesearchchecklist.feature.aichat.impl.parser.lexicon

/**
 * English keyword sets per intent for [LocalIntentRouterImpl].
 *
 * All values are lowercase. Matching code lowercases input (ASCII fold) before comparison.
 * Phrases are sorted longest-first during matching to avoid substring shadowing.
 */
internal object EnIntentLexicon {

    // ─── CreateItem ───────────────────────────────────────────────────────────
    // "add milk to shopping", "buy milk", "write down milk"
    val createItem: Set<String> = setOf(
        "write down",
        "add",
        "buy",
        "get",
        "put",
        "pick up",
        "include",
        "insert",
    )

    // ─── DeleteItem ───────────────────────────────────────────────────────────
    // "remove milk", "delete first item", "drop eggs from list"
    val deleteItem: Set<String> = setOf(
        "remove",
        "delete",
        "drop",
        "erase",
        "eliminate",
        "take off",
        "scratch off",
        "strike off",
    )

    // ─── CompleteItem ─────────────────────────────────────────────────────────
    // "mark milk done", "check off eggs", "completed: butter"
    val completeItem: Set<String> = setOf(
        "check off",
        "mark done",
        "mark as done",
        "mark complete",
        "mark as complete",
        "mark",
        "done",
        "complete",
        "completed",
        "finish",
        "finished",
        "tick off",
        "tick",
    )

    // ─── SetReminder ──────────────────────────────────────────────────────────
    // "remind me tomorrow at 9", "set a reminder for milk"
    val setReminder: Set<String> = setOf(
        "remind me",
        "set a reminder",
        "set reminder",
        "create a reminder",
        "create reminder",
        "alert me",
        "remind",
        "reminder",
        "alert",
        "notify me",
    )

    // ─── FindItems ────────────────────────────────────────────────────────────
    // "find milk", "where is my shopping list", "search for butter"
    val findItems: Set<String> = setOf(
        "search for",
        "look for",
        "where is",
        "where are",
        "find",
        "search",
        "show me",
        "show",
        "list",
        "where",
    )

    // ─── CreateChecklist ──────────────────────────────────────────────────────
    // "new shopping list", "create list for trip", "create marketing screenshots todo"
    val createChecklist: Set<String> = setOf(
        // Single-word broad triggers — match anything starting with these
        "create", "new", "make",
        // Multi-word specific triggers (kept for precision when present)
        "new list",
        "create a list",
        "create list",
        "list for",
        "new checklist",
        "create a checklist",
        "create checklist",
        "make a list",
        "make list",
    )

    // ─── MoveReminders ────────────────────────────────────────────────────────
    // "move all reminders from today to tomorrow", "reschedule reminders"
    val moveReminders: Set<String> = setOf(
        "move all reminders",
        "move reminders",
        "reschedule reminders",
        "reschedule",
        "shift reminders",
        "shift",
        "move",
    )

    // ─── Hint prepositions (for ChecklistHintExtractor) ───────────────────────
    val hintPrepositions: Set<String> = setOf("in", "to", "for", "from", "into", "on")
}
