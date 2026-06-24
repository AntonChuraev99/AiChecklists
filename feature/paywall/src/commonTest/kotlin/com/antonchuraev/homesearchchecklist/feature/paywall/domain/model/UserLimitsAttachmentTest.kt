package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Coverage for [UserLimits.canAddAttachment] (cross-device attachments, Phase 6).
 *
 * Contract: a free user may add an attachment only while the item holds fewer than
 * [UserLimits.maxAttachmentsPerItem] (default 3); premium is always allowed regardless of count.
 */
class UserLimitsAttachmentTest {

    // ── Free tier: gated by the per-item limit (default 3) ──

    @Test
    fun freeUser_belowLimit_canAddAttachment() {
        val limits = freeLimits()
        assertTrue(limits.canAddAttachment(currentAttachmentCount = 0))
        assertTrue(limits.canAddAttachment(currentAttachmentCount = 2), "2 < 3 default limit")
    }

    @Test
    fun freeUser_atLimit_cannotAddAttachment() {
        val limits = freeLimits()
        assertFalse(limits.canAddAttachment(currentAttachmentCount = 3), "3 == 3 default limit → blocked")
    }

    @Test
    fun freeUser_overLimit_cannotAddAttachment() {
        val limits = freeLimits()
        assertFalse(limits.canAddAttachment(currentAttachmentCount = 4))
    }

    @Test
    fun freeUser_maxAttachmentsPerItem_defaultsToThree() {
        assertEquals(3, freeLimits().maxAttachmentsPerItem)
    }

    // ── Premium tier: always allowed ──

    @Test
    fun premiumUser_alwaysCanAddAttachment_regardlessOfCount() {
        val limits = premiumLimits()
        assertTrue(limits.canAddAttachment(currentAttachmentCount = 0))
        assertTrue(limits.canAddAttachment(currentAttachmentCount = 100), "premium ignores the per-item limit")
        assertTrue(limits.canAddAttachment(currentAttachmentCount = Int.MAX_VALUE - 1))
    }

    @Test
    fun premiumUser_maxAttachmentsPerItem_isUnlimited() {
        assertEquals(Int.MAX_VALUE, premiumLimits().maxAttachmentsPerItem)
    }

    private fun freeLimits() = UserLimits(
        maxChecklists = 5,
        maxFillsPerChecklist = 5,
        currentChecklistCount = 0,
        isPremium = false,
    )

    private fun premiumLimits() = UserLimits(
        maxChecklists = Int.MAX_VALUE,
        maxFillsPerChecklist = Int.MAX_VALUE,
        currentChecklistCount = 0,
        isPremium = true,
    )
}
