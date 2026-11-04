package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent.voice

import com.antonchuraev.homesearchchecklist.core.voice.api.VoiceAgentAction
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome

/**
 * A dispatched tool call → the card the call screen shows ("Created checklist «Trip»"), or `null` when
 * nothing in the user's data changed (a read, a search, "already done", a refusal).
 *
 * Keyed by the dispatcher's `messageKey`, not by the tool name: the key says what HAPPENED (a
 * `complete_item` on a checked row is `chat_dispatch_already_done` — no change, no card), and its
 * positional [DispatchOutcome.Success.args] are the same ones the text chat renders, so the card names
 * exactly what the chat bubble would.
 */
internal object VoiceActionMapper {

    const val KIND_CHECKLIST_CREATED = "checklist_created"
    const val KIND_ITEM_ADDED = "item_added"
    const val KIND_ITEMS_ADDED = "items_added"
    const val KIND_UPDATED = "updated"

    fun toAction(outcome: DispatchOutcome): VoiceAgentAction? {
        if (outcome !is DispatchOutcome.Success) return null
        val args = outcome.args
        val target = outcome.linkedChecklistId?.toString()
        fun action(kind: String, titleIndex: Int, count: Int? = null): VoiceAgentAction? {
            val title = args.getOrNull(titleIndex)?.takeIf { it.isNotBlank() } ?: return null
            return VoiceAgentAction(title = title, kind = kind, targetId = target, count = count)
        }
        return when (outcome.messageKey) {
            // args: [name] / [name, count]
            "chat_dispatch_created_empty",
            "chat_dispatch_created_with_one",
            "chat_dispatch_created_with_many",
            "chat_dispatch_created_from_attachment" -> action(KIND_CHECKLIST_CREATED, titleIndex = 0)

            // args: [item] / [item, list]
            "chat_dispatch_added", "chat_dispatch_added_to" -> action(KIND_ITEM_ADDED, titleIndex = 0)

            // args: [count, list]
            "chat_dispatch_added_many_to" ->
                action(KIND_ITEMS_ADDED, titleIndex = 1, count = args.getOrNull(0)?.toIntOrNull())

            // args: [item, …]
            "chat_dispatch_completed",
            "chat_dispatch_deleted",
            "chat_dispatch_reminder_set",
            "chat_dispatch_moved_item" -> action(KIND_UPDATED, titleIndex = 0)

            // args: [old, new] / [count, list]
            "chat_dispatch_renamed",
            "chat_dispatch_completed_items_removed" -> action(KIND_UPDATED, titleIndex = 1)

            // args: [day] / [count, day] — reminders moved to a day: no single checklist to name.
            "chat_dispatch_moved_one" -> action(KIND_UPDATED, titleIndex = 0)
            "chat_dispatch_moved_many" -> action(KIND_UPDATED, titleIndex = 1)

            // Searches, "already done", "nothing to clear", "same list": nothing changed.
            else -> null
        }
    }
}
