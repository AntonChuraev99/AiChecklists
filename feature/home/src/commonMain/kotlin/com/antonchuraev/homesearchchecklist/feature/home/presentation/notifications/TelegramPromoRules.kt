package com.antonchuraev.homesearchchecklist.feature.home.presentation.notifications

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge

/** At most this many cycles (× or auto-rest each close one), then never again. */
const val TG_PROMO_MAX_CYCLES = 3

/** A closed cycle reopens this many days after it closed. */
const val TG_PROMO_REAPPEAR_DAYS = 14

/** A cycle closes by itself after the banner was visible on this many distinct days (owner Q3). */
const val TG_PROMO_AUTO_REST_SEEN_DAYS = 7

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Inputs of the Inbox Telegram promo rule (spec §4.3).
 *
 * @param seenToday the current cycle already counted TODAY. Rule 9 waits for the next day, so the
 *   banner never vanishes under the user on the very day its 7th seen day was counted.
 */
data class TelegramPromoInput(
    val link: TelegramLinkKnowledge,
    val retired: Boolean,
    val dismissCount: Int,
    val lastDismissedAtMs: Long?,
    val nowMs: Long,
    val isInboxPage: Boolean,
    val inboxHasTasks: Boolean,
    val notificationsBannerVisible: Boolean,
    val dismissedThisSession: Boolean,
    val cycleSeenDays: Int,
    val seenToday: Boolean = false,
)

sealed interface TelegramPromoDecision {
    data object Show : TelegramPromoDecision
    data object Hide : TelegramPromoDecision

    /** Rule 9: the cycle has had its 7 days — close it (persist like a dismissal), stay hidden. */
    data object AutoRest : TelegramPromoDecision
}

/** Rules 1–9 of spec §4.3, in order; the first match hides. */
fun decideTelegramPromo(input: TelegramPromoInput): TelegramPromoDecision = with(input) {
    val hide = TelegramPromoDecision.Hide
    when {
        retired -> hide // 1
        link is TelegramLinkKnowledge.Unknown || link is TelegramLinkKnowledge.Linked -> hide // 2
        dismissCount >= TG_PROMO_MAX_CYCLES -> hide // 3
        dismissCount > 0 && withinReappearWindow(lastDismissedAtMs, nowMs) -> hide // 4
        dismissedThisSession -> hide // 5
        !isInboxPage -> hide // 6
        !inboxHasTasks -> hide // 7
        notificationsBannerVisible -> hide // 8
        cycleSeenDays >= TG_PROMO_AUTO_REST_SEEN_DAYS && !seenToday -> TelegramPromoDecision.AutoRest // 9
        else -> TelegramPromoDecision.Show
    }
}

fun shouldShowTelegramPromo(input: TelegramPromoInput): Boolean =
    decideTelegramPromo(input) == TelegramPromoDecision.Show

/**
 * What one evaluation is allowed to write / report (spec §4.3 "Writes", §6).
 *
 * @param reportShown the `telegram_promo_shown` candidate (the caller still dedupes per session).
 * @param countSeenDay the day to add to the cycle's seen days, null = nothing to count.
 * @param closeCycle rule 9: persist the cycle as closed (+ `telegram_promo_auto_rested`).
 */
data class TelegramPromoBookkeeping(
    val reportShown: Boolean = false,
    val countSeenDay: Long? = null,
    val closeCycle: Boolean = false,
)

/**
 * Bookkeeping for one evaluation. [slotOnScreen] = the Inbox page's banner slot is composed and the
 * screen is RESUMED (reported by the UI). Without it nothing counts: a ticking clock in a backgrounded
 * or back-stacked Inbox must not burn seen days or close a cycle nobody saw.
 */
fun telegramPromoBookkeeping(
    decision: TelegramPromoDecision,
    slotOnScreen: Boolean,
    lastSeenDay: Long?,
    today: Long,
): TelegramPromoBookkeeping = when {
    !slotOnScreen -> TelegramPromoBookkeeping()
    decision == TelegramPromoDecision.Show -> TelegramPromoBookkeeping(
        reportShown = true,
        countSeenDay = today.takeIf { lastSeenDay != it },
    )
    decision == TelegramPromoDecision.AutoRest -> TelegramPromoBookkeeping(closeCycle = true)
    else -> TelegramPromoBookkeeping()
}

/** A missing timestamp with a positive count, or a clock moved back, both read as "not yet". */
private fun withinReappearWindow(lastDismissedAtMs: Long?, nowMs: Long): Boolean {
    val since = lastDismissedAtMs ?: return true
    val elapsed = nowMs - since
    return elapsed < 0 || elapsed < TG_PROMO_REAPPEAR_DAYS * DAY_MS
}

/** Analytics `state` of the promo; null when the banner cannot show for this link state. */
fun TelegramLinkKnowledge.promoAnalyticsState(): String? = when (this) {
    TelegramLinkKnowledge.SignedOut -> AnalyticsEvents.Telegram.STATE_SIGNED_OUT
    TelegramLinkKnowledge.NotLinked -> AnalyticsEvents.Telegram.STATE_NOT_LINKED
    TelegramLinkKnowledge.Unknown, is TelegramLinkKnowledge.Linked -> null
}
