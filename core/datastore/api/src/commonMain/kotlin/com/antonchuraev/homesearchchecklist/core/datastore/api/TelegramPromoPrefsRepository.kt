package com.antonchuraev.homesearchchecklist.core.datastore.api

import kotlinx.coroutines.flow.Flow

/**
 * Persisted cycle counters of the Inbox Telegram promo banner (design spec
 * `docs/designs/telegram-channel-banner-2026-11-21/spec.md` §4.3). The "gone for good" flag lives in
 * [HintsRepository.telegramPromoRetired] — written by the Telegram link cache writer, not here.
 *
 * @param dismissCount closed cycles (× taps AND 7-seen-day auto-rests), 0..3.
 * @param lastDismissedAtMs when the last cycle closed; null = never.
 * @param cycleSeenDays distinct calendar days the banner was visible in the CURRENT cycle.
 * @param lastSeenDay the last counted day (`LocalDate.toEpochDays()` in the device zone); null = never.
 */
data class TelegramPromoCounters(
    val dismissCount: Int = 0,
    val lastDismissedAtMs: Long? = null,
    val cycleSeenDays: Int = 0,
    val lastSeenDay: Long? = null,
) {
    /** × or auto-rest: one more closed cycle, stamped [nowMs], seen days back to zero. */
    fun afterCycleClosed(nowMs: Long): TelegramPromoCounters =
        copy(dismissCount = dismissCount + 1, lastDismissedAtMs = nowMs, cycleSeenDays = 0)

    /** The banner was visible on [epochDay]: counts once per calendar day. */
    fun afterSeenOn(epochDay: Long): TelegramPromoCounters =
        if (lastSeenDay == epochDay) this else copy(cycleSeenDays = cycleSeenDays + 1, lastSeenDay = epochDay)
}

interface TelegramPromoPrefsRepository {
    /** Never throws: a read failure degrades to the defaults (logged). */
    val counters: Flow<TelegramPromoCounters>

    /** Read-modify-write, serialized; returns the stored value. */
    suspend fun update(transform: (TelegramPromoCounters) -> TelegramPromoCounters): TelegramPromoCounters
}
