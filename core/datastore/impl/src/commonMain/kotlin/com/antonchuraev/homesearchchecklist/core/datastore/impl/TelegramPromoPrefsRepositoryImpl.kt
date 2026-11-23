package com.antonchuraev.homesearchchecklist.core.datastore.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramPromoCounters
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramPromoPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "TelegramPromoPrefs"
private const val KEY_DISMISS_COUNT = "telegram_promo_dismiss_count"
private const val KEY_LAST_DISMISSED_AT = "telegram_promo_last_dismissed_at"
private const val KEY_CYCLE_SEEN_DAYS = "telegram_promo_cycle_seen_days"
private const val KEY_LAST_SEEN_DAY = "telegram_promo_last_seen_day"

/** "Never" sentinel for the two Long keys (DataStore has no nullable value). */
private const val NONE = Long.MIN_VALUE

class TelegramPromoPrefsRepositoryImpl(
    private val dataStore: AppDatastore,
    private val logger: AppLogger,
) : TelegramPromoPrefsRepository {

    private val writeLock = Mutex()

    override val counters: Flow<TelegramPromoCounters> = combine(
        dataStore.observeInt(KEY_DISMISS_COUNT, 0),
        dataStore.observeLong(KEY_LAST_DISMISSED_AT, NONE),
        dataStore.observeInt(KEY_CYCLE_SEEN_DAYS, 0),
        dataStore.observeLong(KEY_LAST_SEEN_DAY, NONE),
    ) { count, dismissedAt, seenDays, seenDay ->
        TelegramPromoCounters(
            dismissCount = count,
            lastDismissedAtMs = dismissedAt.takeIf { it != NONE },
            cycleSeenDays = seenDays,
            lastSeenDay = seenDay.takeIf { it != NONE },
        )
    }.catch { e ->
        logger.error(TAG, "read failed, using defaults: ${e.message}", e)
        emit(TelegramPromoCounters())
    }

    override suspend fun update(
        transform: (TelegramPromoCounters) -> TelegramPromoCounters,
    ): TelegramPromoCounters = writeLock.withLock {
        val before = counters.first()
        val after = transform(before)
        if (after != before) {
            dataStore.saveInt(KEY_DISMISS_COUNT, after.dismissCount)
            dataStore.saveLong(KEY_LAST_DISMISSED_AT, after.lastDismissedAtMs ?: NONE)
            dataStore.saveInt(KEY_CYCLE_SEEN_DAYS, after.cycleSeenDays)
            dataStore.saveLong(KEY_LAST_SEEN_DAY, after.lastSeenDay ?: NONE)
        }
        after
    }
}
