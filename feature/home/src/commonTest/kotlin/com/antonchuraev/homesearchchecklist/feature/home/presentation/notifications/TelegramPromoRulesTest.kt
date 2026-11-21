package com.antonchuraev.homesearchchecklist.feature.home.presentation.notifications

import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramPromoCounters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TelegramPromoRulesTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    /** Everything passes: NotLinked, never dismissed, Inbox page with tasks, no other banner. */
    private fun base() = TelegramPromoInput(
        link = TelegramLinkKnowledge.NotLinked,
        retired = false,
        dismissCount = 0,
        lastDismissedAtMs = null,
        nowMs = now,
        isInboxPage = true,
        inboxHasTasks = true,
        notificationsBannerVisible = false,
        dismissedThisSession = false,
        cycleSeenDays = 0,
    )

    @Test fun baseline_notLinked_shows() = assertTrue(shouldShowTelegramPromo(base()))

    @Test fun guest_signedOut_shows() =
        assertTrue(shouldShowTelegramPromo(base().copy(link = TelegramLinkKnowledge.SignedOut)))

    @Test fun unknown_hides() =
        assertFalse(shouldShowTelegramPromo(base().copy(link = TelegramLinkKnowledge.Unknown)))

    @Test fun linked_hides_withOrWithoutChannel() {
        assertFalse(shouldShowTelegramPromo(base().copy(link = TelegramLinkKnowledge.Linked(null))))
        assertFalse(shouldShowTelegramPromo(base().copy(link = TelegramLinkKnowledge.Linked(ReminderChannel.Both))))
    }

    @Test fun rule1_retired_beatsEverything_evenNotLinkedOrGuest() {
        assertFalse(shouldShowTelegramPromo(base().copy(retired = true)))
        assertFalse(shouldShowTelegramPromo(base().copy(retired = true, link = TelegramLinkKnowledge.SignedOut)))
    }

    @Test fun rule3_thirdCycleClosed_neverAgain_evenAfter14Days() {
        val input = base().copy(dismissCount = 3, lastDismissedAtMs = now - 400 * day)
        assertFalse(shouldShowTelegramPromo(input))
    }

    @Test fun rule4_14dayBoundary() {
        val dismissed = now - 14 * day
        assertFalse(shouldShowTelegramPromo(base().copy(dismissCount = 1, lastDismissedAtMs = dismissed + 60 * 60 * 1000)))
        assertTrue(shouldShowTelegramPromo(base().copy(dismissCount = 1, lastDismissedAtMs = dismissed)))
    }

    @Test fun rule4_13d23h_hides() {
        val input = base().copy(dismissCount = 2, lastDismissedAtMs = now - (14 * day - 60 * 60 * 1000))
        assertFalse(shouldShowTelegramPromo(input))
    }

    @Test fun rule4_clockMovedBack_hides() {
        assertFalse(shouldShowTelegramPromo(base().copy(dismissCount = 1, lastDismissedAtMs = now + day)))
    }

    @Test fun rule4_countWithoutTimestamp_hides() {
        assertFalse(shouldShowTelegramPromo(base().copy(dismissCount = 1, lastDismissedAtMs = null)))
    }

    @Test fun rule5_dismissedThisSession_hides() =
        assertFalse(shouldShowTelegramPromo(base().copy(dismissedThisSession = true)))

    @Test fun rule6_projectPage_hides() =
        assertFalse(shouldShowTelegramPromo(base().copy(isInboxPage = false)))

    @Test fun rule7_emptyInbox_hides() =
        assertFalse(shouldShowTelegramPromo(base().copy(inboxHasTasks = false)))

    @Test fun rule8_notificationsBannerVisible_hides() =
        assertFalse(shouldShowTelegramPromo(base().copy(notificationsBannerVisible = true)))

    @Test fun rule9_sevenSeenDays_autoRests_onTheNextDay() {
        assertEquals(TelegramPromoDecision.AutoRest, decideTelegramPromo(base().copy(cycleSeenDays = 7)))
        assertFalse(shouldShowTelegramPromo(base().copy(cycleSeenDays = 7)))
    }

    @Test fun rule9_seventhDayCountedToday_stillShowsToday() =
        assertTrue(shouldShowTelegramPromo(base().copy(cycleSeenDays = 7, seenToday = true)))

    @Test fun rule9_sixSeenDays_shows() =
        assertTrue(shouldShowTelegramPromo(base().copy(cycleSeenDays = 6)))

    @Test fun rule9_onlyWhenNothingElseHides() {
        // An earlier rule wins: a hidden banner on a project page does not close the cycle.
        assertEquals(TelegramPromoDecision.Hide, decideTelegramPromo(base().copy(cycleSeenDays = 7, isInboxPage = false)))
    }

    @Test fun twoAutoRestsPlusOneDismissal_isThreeCycles_neverAgain() {
        var counters = TelegramPromoCounters()
        counters = counters.afterCycleClosed(now - 100 * day) // auto-rest 1
        counters = counters.afterCycleClosed(now - 60 * day) // auto-rest 2
        counters = counters.afterCycleClosed(now - 20 * day) // ×
        assertEquals(3, counters.dismissCount)
        val input = base().copy(dismissCount = counters.dismissCount, lastDismissedAtMs = counters.lastDismissedAtMs)
        assertFalse(shouldShowTelegramPromo(input))
    }

    @Test fun counters_seenDayCountsOncePerDay_andResetsOnClose() {
        var c = TelegramPromoCounters()
        c = c.afterSeenOn(100).afterSeenOn(100).afterSeenOn(101)
        assertEquals(2, c.cycleSeenDays)
        assertEquals(101L, c.lastSeenDay)
        c = c.afterCycleClosed(now)
        assertEquals(TelegramPromoCounters(1, now, 0, 101), c)
    }

    @Test fun bookkeeping_showWithoutSlotOnScreen_countsNothing() {
        val step = telegramPromoBookkeeping(TelegramPromoDecision.Show, slotOnScreen = false, lastSeenDay = 99, today = 100)
        assertEquals(TelegramPromoBookkeeping(), step)
    }

    @Test fun bookkeeping_autoRestWithoutSlotOnScreen_doesNotCloseTheCycle() {
        val step = telegramPromoBookkeeping(TelegramPromoDecision.AutoRest, slotOnScreen = false, lastSeenDay = 99, today = 100)
        assertEquals(TelegramPromoBookkeeping(), step)
    }

    @Test fun bookkeeping_showOnScreen_reportsAndCountsANewDayOnce() {
        assertEquals(
            TelegramPromoBookkeeping(reportShown = true, countSeenDay = 100),
            telegramPromoBookkeeping(TelegramPromoDecision.Show, slotOnScreen = true, lastSeenDay = 99, today = 100),
        )
        assertEquals(
            TelegramPromoBookkeeping(reportShown = true, countSeenDay = null),
            telegramPromoBookkeeping(TelegramPromoDecision.Show, slotOnScreen = true, lastSeenDay = 100, today = 100),
        )
    }

    @Test fun bookkeeping_autoRestOnScreen_closesTheCycle() {
        assertEquals(
            TelegramPromoBookkeeping(closeCycle = true),
            telegramPromoBookkeeping(TelegramPromoDecision.AutoRest, slotOnScreen = true, lastSeenDay = 99, today = 100),
        )
    }

    @Test fun analyticsState_onlyForShowableLinks() {
        assertEquals("signed_out", TelegramLinkKnowledge.SignedOut.promoAnalyticsState())
        assertEquals("not_linked", TelegramLinkKnowledge.NotLinked.promoAnalyticsState())
        assertEquals(null, TelegramLinkKnowledge.Unknown.promoAnalyticsState())
    }
}
