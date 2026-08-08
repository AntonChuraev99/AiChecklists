package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * WHERE the chat was opened — the screen behind the dock.
 *
 * [wireValue] is the ONLY thing that travels to the server, and a null wireValue means
 * "send nothing": that is what keeps the CONTROL arm's request byte-identical to today's.
 * [ChecklistDetail] is deliberately null too — an open checklist already travels as
 * `context_checklist`, and sending it twice would give the server prompt two sources for
 * one fact.
 *
 * Deliberately NOT reusing `V2Destination`'s strings: those are also the `nav_tab_selected`
 * analytics wire values (V2NavigationShell.kt), and one rename must not silently break
 * three consumers. The mapping between them lives in exactly one function, `chatSurfaceFor`
 * (App.kt).
 */
enum class ChatSurface(val wireValue: String?) {
    /** Control arm, or a route the chat has nothing structural to say about. */
    Unknown(null),

    /** A checklist is open behind the dock — carried by `context_checklist`. */
    ChecklistDetail(null),

    Inbox("inbox"),
    Agenda("agenda"),
    Projects("projects"),
    Overview("overview"),
}
