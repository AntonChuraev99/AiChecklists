package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel

/**
 * Server → device control message sent when the user's reminder channel changes (bot `/unlink`,
 * `/reminders`, the app's Telegram screen): data-only, high priority,
 * `{"type": "reminder_channel", "channel": "push" | "telegram" | "both"}`.
 *
 * It refreshes the local cache `ReminderReceiver` reads, so a user who turned Telegram off in the bot
 * does not keep a silent phone until the next app start. It is NOT a user-visible push: no
 * notification and no `push_received` analytics.
 */
internal sealed interface ReminderChannelPushDecision {

    /** Any other FCM payload — handled by the regular display path. */
    data object NotAChannelUpdate : ReminderChannelPushDecision

    /** A valid update: persist [channel]. */
    data class Apply(val channel: ReminderChannel) : ReminderChannelPushDecision

    /** A channel-update message with an absent or unknown value: keep the cache, log a warning. */
    data class Ignore(val rawChannel: String?) : ReminderChannelPushDecision
}

internal const val PUSH_KEY_TYPE = "type"
internal const val PUSH_KEY_REMINDER_CHANNEL = "channel"
internal const val PUSH_TYPE_REMINDER_CHANNEL = "reminder_channel"

/** Pure parse of an FCM data map into a [ReminderChannelPushDecision]. */
internal fun parseReminderChannelPush(data: Map<String, String>): ReminderChannelPushDecision {
    if (data[PUSH_KEY_TYPE] != PUSH_TYPE_REMINDER_CHANNEL) return ReminderChannelPushDecision.NotAChannelUpdate
    val raw = data[PUSH_KEY_REMINDER_CHANNEL]
    val channel = ReminderChannel.fromWire(raw) ?: return ReminderChannelPushDecision.Ignore(raw)
    return ReminderChannelPushDecision.Apply(channel)
}
