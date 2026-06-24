package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class GetUserLimitsUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val checklistRepository: ChecklistRepository,
    private val paywallRepository: PaywallRepository,
    private val userDataRepository: UserDataRepository
) {
    operator fun invoke(): Flow<UserLimits> {
        val maxChecklists = remoteConfigProvider.getLong(
            RemoteConfigKeys.MAX_CHECKLISTS_FREE,
            RemoteConfigDefaults.MAX_CHECKLISTS_FREE
        ).toInt()

        val maxFillsPerChecklist = remoteConfigProvider.getLong(
            RemoteConfigKeys.MAX_FILLS_FREE,
            RemoteConfigDefaults.MAX_FILLS_FREE
        ).toInt()

        val maxWeeklyChecklistsFree = remoteConfigProvider.getLong(
            RemoteConfigKeys.MAX_WEEKLY_CHECKLISTS_FREE,
            RemoteConfigDefaults.MAX_WEEKLY_CHECKLISTS_FREE
        ).toInt()

        val maxRecurringRemindersFree = remoteConfigProvider.getLong(
            RemoteConfigKeys.MAX_RECURRING_REMINDERS_FREE,
            RemoteConfigDefaults.MAX_RECURRING_REMINDERS_FREE
        ).toInt()

        val maxAttachmentsPerItemFree = remoteConfigProvider.getLong(
            RemoteConfigKeys.MAX_ATTACHMENTS_PER_ITEM_FREE,
            RemoteConfigDefaults.MAX_ATTACHMENTS_PER_ITEM_FREE
        ).toInt()

        return combine(
            checklistRepository.checklists.map { it.size },
            paywallRepository.subscriptionStatus,
            userDataRepository.getUserDataFlow().map { it.isPremium },
            checklistRepository.weeklyChecklistCount
        ) { checklistCount, subscriptionStatus, firestorePremium, weeklyCount ->
            val revenueCatPremium = subscriptionStatus.activeEntitlements.contains(Entitlements.PREMIUM)
            val isPremium = revenueCatPremium || firestorePremium
            UserLimits(
                maxChecklists = maxChecklists,
                maxFillsPerChecklist = maxFillsPerChecklist,
                currentChecklistCount = checklistCount,
                isPremium = isPremium,
                maxWeeklyChecklists = if (isPremium) Int.MAX_VALUE else maxWeeklyChecklistsFree,
                currentWeeklyChecklistCount = weeklyCount,
                maxRecurringReminders = if (isPremium) Int.MAX_VALUE else maxRecurringRemindersFree,
                maxAttachmentsPerItem = if (isPremium) Int.MAX_VALUE else maxAttachmentsPerItemFree
            )
        }
    }

    suspend fun getFillCount(checklistId: Long): Int {
        return checklistRepository.getFillCountByChecklistId(checklistId)
    }
}
