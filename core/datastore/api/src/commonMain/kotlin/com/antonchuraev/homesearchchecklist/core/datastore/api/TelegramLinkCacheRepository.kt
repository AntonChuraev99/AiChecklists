package com.antonchuraev.homesearchchecklist.core.datastore.api

import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import kotlinx.coroutines.flow.Flow

/**
 * The last Telegram link state the server reported for [accountId] (Firebase uid).
 *
 * @property reminderChannel meaningful only when [linked]: the confirmed channel, null when the
 *   server does not report one (pre-phase-4). Unlike [ReminderDeliveryPrefsRepository]'s channel
 *   (which defaults to Push for `ReminderReceiver`), null here is kept as "not reported".
 */
data class TelegramLinkSnapshot(
    val accountId: String,
    val linked: Boolean,
    val reminderChannel: ReminderChannel? = null,
)

/**
 * Device-local cache of the Telegram link, for surfaces that must not wait for the network (the
 * reminder sheet, the Inbox promo). One snapshot, tagged with its account: readers compare
 * [TelegramLinkSnapshot.accountId] with the signed-in uid, so an account switch never shows another
 * account's link.
 *
 * Written only by the Telegram account repository (composeApp). Never throws: an unreadable or
 * malformed value reads as null (= nothing known).
 */
interface TelegramLinkCacheRepository {
    val snapshot: Flow<TelegramLinkSnapshot?>

    suspend fun write(snapshot: TelegramLinkSnapshot)
}
