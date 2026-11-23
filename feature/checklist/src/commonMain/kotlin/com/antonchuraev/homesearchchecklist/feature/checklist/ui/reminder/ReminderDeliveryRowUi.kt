package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge

/**
 * The reminder sheet's "Where all reminders go" row (spec
 * `docs/designs/telegram-channel-banner-2026-11-21/spec.md` §3). Null in [ReminderSheetState.delivery]
 * = no row, the sheet exactly as before.
 */
sealed interface ReminderDeliveryRowUi {

    /** Telegram not linked (signed in or guest): the row leads to the Telegram screen. */
    data class Connect(val web: Boolean) : ReminderDeliveryRowUi

    /**
     * Linked and the server reports a channel.
     *
     * @param confirmed the last value the server confirmed — the collapsed value line shows it, also
     *   while saving.
     * @param saving non-null while a save runs: the tapped option shows a spinner and NO option
     *   shows a selected radio (addendum §4). Web: the "on" row is the one where
     *   `saving.isWebOn()`.
     * @param failed the last save failed and [confirmed] was restored: the collapsed value line shows
     *   `reminder_channel_save_failed_short`, the expanded card the error line. Cleared by the next
     *   tap and the next sheet open.
     * @param web the web build: two rows (`isWebOn()`), never "phone" wording.
     */
    data class Choice(
        val confirmed: ReminderChannel,
        val saving: ReminderChannel?,
        val failed: Boolean,
        val web: Boolean,
    ) : ReminderDeliveryRowUi
}

/**
 * Spec §5.2 mapping: Unknown → no row · SignedOut / NotLinked → Connect · Linked(null) → no row ·
 * Linked(c) → Choice. [confirmedOverride] is the value a save just confirmed, held until the link
 * cache re-emits it, so the radio does not flick back to the old value for a frame.
 */
fun reminderDeliveryRowUi(
    knowledge: TelegramLinkKnowledge,
    saving: ReminderChannel?,
    failed: Boolean,
    confirmedOverride: ReminderChannel?,
    web: Boolean,
): ReminderDeliveryRowUi? = when (knowledge) {
    TelegramLinkKnowledge.Unknown -> null
    TelegramLinkKnowledge.SignedOut, TelegramLinkKnowledge.NotLinked -> ReminderDeliveryRowUi.Connect(web)
    is TelegramLinkKnowledge.Linked -> knowledge.reminderChannel?.let { cached ->
        ReminderDeliveryRowUi.Choice(
            confirmed = confirmedOverride ?: cached,
            saving = saving,
            failed = failed,
            web = web,
        )
    }
}

/**
 * The row's callbacks, grouped so a host wires them with one `copy` (see [rememberReminderDelivery]).
 * Every default is a no-op: an unwired host never shows the row anyway ([ReminderSheetState.delivery]
 * stays null).
 *
 * @param onChannelClick Android radio tap.
 * @param onWebClick web radio tap: `true` = "Send to Telegram".
 * @param onConnectClick the Connect row (opens the Telegram screen).
 * @param onExpandedChange the Choice header was tapped; the new expanded value. Analytics only —
 *   expanded/collapsed itself is UI-local (`rememberSaveable`, collapsed on every open).
 */
data class ReminderDeliveryCallbacks(
    val onChannelClick: (ReminderChannel) -> Unit = {},
    val onWebClick: (on: Boolean) -> Unit = {},
    val onConnectClick: () -> Unit = {},
    val onExpandedChange: (expanded: Boolean) -> Unit = {},
)
