package com.antonchuraev.homesearchchecklist.core.datastore.api

/**
 * The class moved to `core:common:api` so feature modules without a datastore dependency can name
 * it (reminder sheet). Kept here as an alias so existing callers compile unchanged.
 */
typealias ReminderChannel = com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel

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
