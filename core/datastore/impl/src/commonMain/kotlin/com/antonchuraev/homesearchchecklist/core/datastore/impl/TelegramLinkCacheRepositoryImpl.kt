package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkCacheRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val TAG = "TelegramLinkCache"
private const val KEY_SNAPSHOT = "telegram_link_snapshot"

private const val FORMAT_V1 = "v1"
private const val LINKED = "1"
private const val NOT_LINKED = "0"
private const val NO_CHANNEL = "-"
private const val SEPARATOR = '|'

/**
 * One string key, so the account and the state are written atomically:
 * `v1|<1|0>|<channel wire or ->|<accountId>`. The account id goes last, so a separator inside it
 * cannot shift the other fields.
 */
class TelegramLinkCacheRepositoryImpl(
    private val dataStore: AppDatastore,
    private val logger: AppLogger,
) : TelegramLinkCacheRepository {

    override val snapshot: Flow<TelegramLinkSnapshot?> =
        dataStore.observeString(KEY_SNAPSHOT, defaultValue = "")
            .map { decode(it) }
            .catch { e ->
                // Readers treat null as "nothing known" (Unknown for a signed-in user): the safe side.
                logger.error(TAG, "snapshot unreadable — treating as unknown: ${e.message}", e)
                emit(null)
            }
            .distinctUntilChanged()

    override suspend fun write(snapshot: TelegramLinkSnapshot) {
        dataStore.saveString(KEY_SNAPSHOT, encode(snapshot))
    }

    private fun decode(raw: String): TelegramLinkSnapshot? {
        if (raw.isEmpty()) return null
        val parts = raw.split(SEPARATOR, limit = 4)
        val accountId = parts.getOrNull(3)
        if (parts[0] != FORMAT_V1 || accountId.isNullOrEmpty()) {
            logger.warning(TAG, "unknown snapshot format — ignored")
            return null
        }
        val linked = when (parts[1]) {
            LINKED -> true
            NOT_LINKED -> false
            else -> return null
        }
        return TelegramLinkSnapshot(
            accountId = accountId,
            linked = linked,
            reminderChannel = ReminderChannel.fromWire(parts[2]),
        )
    }

    private fun encode(snapshot: TelegramLinkSnapshot): String = listOf(
        FORMAT_V1,
        if (snapshot.linked) LINKED else NOT_LINKED,
        snapshot.reminderChannel?.wire ?: NO_CHANNEL,
        snapshot.accountId,
    ).joinToString(SEPARATOR.toString())
}
