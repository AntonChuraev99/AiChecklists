package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.premium.isPremiumUser
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
            // `projects`, NOT `checklists`: the auto-created system Inbox (v2 nav arm) must never
            // consume one of the free-tier slots. Counting it would cut free users from 5 lists to 4
            // in the v2 arm only — a paywall delta INSIDE the A/B experiment that would confound the
            // whole result. Identical to `checklists` in the control arm, where no Inbox row exists.
            checklistRepository.projects.map { it.size },
            paywallRepository.subscriptionStatus,
            userDataRepository.getUserDataFlow().map { it.isPremium },
            checklistRepository.weeklyChecklistCount
        ) { checklistCount, subscriptionStatus, firestorePremium, weeklyCount ->
            // The OR used to be written out here. It now lives in exactly one place, shared with the
            // credits chip in every v2 toolbar — two copies of a premium gate is how one of them ends
            // up fixed and the other not.
            val isPremium = isPremiumUser(subscriptionStatus, firestorePremium)
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
