package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderDeliveryPrefsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

private const val TAG = "ReminderDeliveryPrefs"

private const val KEY_CHANNEL = "reminder_channel"
private const val KEY_WRITTEN_TIMEZONE = "reminder_written_timezone_key"

/**
 * The channel is stored as its [ReminderChannel.wire] value and read back with `fromWire`, so a value
 * written by a later version that this build does not know degrades to Push instead of throwing.
 */
class ReminderDeliveryPrefsRepositoryImpl(
    private val dataStore: AppDatastore,
    private val logger: AppLogger,
) : ReminderDeliveryPrefsRepository {

    override suspend fun getReminderChannel(): ReminderChannel {
        val stored = readString(KEY_CHANNEL, "channel") ?: return ReminderChannel.Push
        return ReminderChannel.fromWire(stored) ?: ReminderChannel.Push
    }

    override suspend fun setReminderChannel(channel: ReminderChannel) {
        dataStore.saveString(KEY_CHANNEL, channel.wire)
    }

    override suspend fun getLastWrittenTimezoneKey(): String? =
        readString(KEY_WRITTEN_TIMEZONE, "written timezone")?.takeIf { it.isNotBlank() }

    override suspend fun setLastWrittenTimezoneKey(key: String) {
        dataStore.saveString(KEY_WRITTEN_TIMEZONE, key)
    }

    private suspend fun readString(key: String, what: String): String? = try {
        dataStore.observeString(key, defaultValue = "").first().takeIf { it.isNotEmpty() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // A reminder must still fire on a corrupt preferences file: the default (Push) is the
        // safe reading — the phone notifies, at worst twice.
        logger.error(TAG, "$what unreadable — using the default: ${e.message}", e)
        null
    }
}
