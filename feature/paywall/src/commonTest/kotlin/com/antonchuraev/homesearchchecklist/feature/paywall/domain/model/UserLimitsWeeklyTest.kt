package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserLimitsWeeklyTest {

    // ── Free tier weekly limits ──

    @Test
    fun freeUser_zeroWeeklyChecklists_canCreate() {
        val limits = freeLimits(currentWeeklyChecklistCount = 0)
        assertTrue(limits.canCreateWeeklyChecklist)
    }

    @Test
    fun freeUser_oneWeeklyChecklist_cannotCreateSecond() {
        val limits = freeLimits(currentWeeklyChecklistCount = 1)
        assertFalse(limits.canCreateWeeklyChecklist, "Free tier max 1 weekly checklist")
    }

    @Test
    fun freeUser_overLimit_cannotCreate() {
        val limits = freeLimits(currentWeeklyChecklistCount = 5)
        assertFalse(limits.canCreateWeeklyChecklist)
    }

    @Test
    fun freeUser_maxWeeklyChecklists_isOne() {
        val limits = freeLimits()
        assertEquals(1, limits.maxWeeklyChecklists)
    }

    // ── Premium tier ──

    @Test
    fun premiumUser_canAlwaysCreateWeekly() {
        val noWeekly = premiumLimits(currentWeeklyChecklistCount = 0)
        val tenWeekly = premiumLimits(currentWeeklyChecklistCount = 10)
        val hundredWeekly = premiumLimits(currentWeeklyChecklistCount = 100)

        assertTrue(noWeekly.canCreateWeeklyChecklist)
        assertTrue(tenWeekly.canCreateWeeklyChecklist)
        assertTrue(hundredWeekly.canCreateWeeklyChecklist)
    }

    @Test
    fun premiumUser_maxWeeklyChecklists_isUnlimited() {
        val limits = premiumLimits()
        assertEquals(Int.MAX_VALUE, limits.maxWeeklyChecklists)
    }

    // ── Independence from standard checklist limit ──

    @Test
    fun weeklyLimit_independentOfStandardChecklistLimit() {
        // Free user with max standard checklists but no weekly — should still be able to create weekly
        val limits = UserLimits(
            maxChecklists = 4,
            maxFillsPerChecklist = 5,
            currentChecklistCount = 4,        // at standard limit
            isPremium = false,
            currentWeeklyChecklistCount = 0   // but no weekly yet
        )
        assertFalse(limits.canCreateChecklist, "standard limit reached")
        assertTrue(limits.canCreateWeeklyChecklist, "weekly limit independent")
    }

    @Test
    fun standardLimit_independentOfWeeklyLimit() {
        // Free user has weekly but standard slots free — should still create standard
        val limits = UserLimits(
            maxChecklists = 4,
            maxFillsPerChecklist = 5,
            currentChecklistCount = 2,
            isPremium = false,
            currentWeeklyChecklistCount = 1
        )
        assertTrue(limits.canCreateChecklist)
        assertFalse(limits.canCreateWeeklyChecklist, "weekly limit reached")
    }

    // ── Edge: explicit override of maxWeeklyChecklists ──

    @Test
    fun explicitMaxWeeklyChecklists_overrideAllowsHigherFreeLimit() {
        // Imagine a future Remote Config bump from 1 → 3 for Free tier
        val limits = UserLimits(
            maxChecklists = 4,
            maxFillsPerChecklist = 5,
            currentChecklistCount = 0,
            isPremium = false,
            maxWeeklyChecklists = 3,
            currentWeeklyChecklistCount = 2
        )
        assertTrue(limits.canCreateWeeklyChecklist)

        val atLimit = limits.copy(currentWeeklyChecklistCount = 3)
        assertFalse(atLimit.canCreateWeeklyChecklist)
    }

    private fun freeLimits(currentWeeklyChecklistCount: Int = 0) = UserLimits(
        maxChecklists = 4,
        maxFillsPerChecklist = 5,
        currentChecklistCount = 0,
        isPremium = false,
        currentWeeklyChecklistCount = currentWeeklyChecklistCount
    )

    private fun premiumLimits(currentWeeklyChecklistCount: Int = 0) = UserLimits(
        maxChecklists = Int.MAX_VALUE,
        maxFillsPerChecklist = Int.MAX_VALUE,
        currentChecklistCount = 0,
        isPremium = true,
        currentWeeklyChecklistCount = currentWeeklyChecklistCount
    )
}
