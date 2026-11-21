package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * Where the user's reminders are delivered (Telegram bot phase 4). Per USER, not per device: the
 * server stores it in `users/{google_uid}.reminder_channel`, the bot and the Telegram screen change it.
 *
 * [wire] is the server / Firestore value and the persisted format.
 */
enum class ReminderChannel(val wire: String) {
    /** Phone notification only — the default, and what an absent value means. */
    Push("push"),

    /** Telegram message only: the phone posts NO notification (bookkeeping still runs). */
    Telegram("telegram"),

    /** Phone notification and Telegram message. */
    Both("both"),
    ;

    companion object {
        /** Null for an absent or unknown value — callers decide what "unknown" means for them. */
        fun fromWire(value: String?): ReminderChannel? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Device-local cache of the reminder delivery settings.
 *
 * - The channel is the last value the server confirmed (`telegram_account status` /
 *   `set_reminder_channel`), so Android's `ReminderReceiver` can decide without a network call.
 * - The written-zone key remembers which device time zone was last written to the user doc, so the
 *   app writes only when it changes.
 *
 * Every getter is safe from a `BroadcastReceiver` (call it inside `goAsync()` + a coroutine): it is
 * a single DataStore read, never throws, and degrades to the default on an unreadable store.
 */
interface ReminderDeliveryPrefsRepository {

    /** Last confirmed channel. Never fetched, unknown or unreadable → [ReminderChannel.Push]. */
    suspend fun getReminderChannel(): ReminderChannel

    suspend fun setReminderChannel(channel: ReminderChannel)

    /** `"<google_uid>|<IANA zone>"` last written to `users/{google_uid}`, or null. */
    suspend fun getLastWrittenTimezoneKey(): String?

    suspend fun setLastWrittenTimezoneKey(key: String)
}
