package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

/**
 * What the user is looking at, for the screens whose content the model cannot otherwise see.
 *
 * Deliberately NOT built for Projects / Overview: those are the checklists the
 * `checklists_summary` already carries, and duplicating them would pay tokens per round for
 * nothing. The Inbox and the day are the two surfaces that are structurally invisible today —
 * `checklists_summary` reads `ChecklistRepository.projects`, which excludes the system Inbox,
 * and no payload has ever carried "what is scheduled for today".
 *
 * [totalItems] is the REAL total; [items] may be shorter. The server prompt states the
 * difference so the model never asserts a count from a truncated list.
 *
 * @param kind [ChatSurface.wireValue] of the surface this snapshot describes.
 * @param label Localized display label — the inbox row name, or the formatted day.
 * @param focusedDate ISO local date (agenda only); null elsewhere.
 */
data class ChatScreenSnapshot(
    val kind: String,
    val label: String? = null,
    val focusedDate: String? = null,
    val items: List<ChatScreenItem> = emptyList(),
    val totalItems: Int = 0,
)

/**
 * One row of a [ChatScreenSnapshot].
 *
 * [hasReminder] / [hasAttachment] are not decoration: `move_item` refuses to move such an item
 * (the per-item alarm is keyed `(checklistId, fillId, itemId)` and the dispatcher owns no
 * scheduler), so the model must be able to see WHY before it proposes the move.
 */
data class ChatScreenItem(
    val text: String,
    val checked: Boolean = false,
    /** Agenda only — which list the entry lives in. */
    val listName: String? = null,
    /** Agenda only — the ISO instant the entry is due at. */
    val dueIso: String? = null,
    val hasReminder: Boolean = false,
    val hasAttachment: Boolean = false,
)
