package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

/**
 * Represents the limits for free users.
 * Premium users have no limits.
 */
data class UserLimits(
    val maxChecklists: Int,
    val maxFillsPerChecklist: Int,
    val currentChecklistCount: Int,
    val isPremium: Boolean
) {
    val canCreateChecklist: Boolean
        get() = isPremium || currentChecklistCount < maxChecklists

    val remainingChecklists: Int
        get() = if (isPremium) Int.MAX_VALUE else maxOf(0, maxChecklists - currentChecklistCount)

    fun canCreateFill(currentFillCount: Int): Boolean =
        isPremium || currentFillCount < maxFillsPerChecklist

    fun remainingFills(currentFillCount: Int): Int =
        if (isPremium) Int.MAX_VALUE else maxOf(0, maxFillsPerChecklist - currentFillCount)
}
