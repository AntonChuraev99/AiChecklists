package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.Entitlements
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.UserLimits
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class GetUserLimitsUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val checklistRepository: ChecklistRepository,
    private val paywallRepository: PaywallRepository
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

        return combine(
            checklistRepository.checklists.map { it.size },
            paywallRepository.subscriptionStatus
        ) { checklistCount, subscriptionStatus ->
            val isPremium = subscriptionStatus.activeEntitlements.contains(Entitlements.PREMIUM)
            UserLimits(
                maxChecklists = maxChecklists,
                maxFillsPerChecklist = maxFillsPerChecklist,
                currentChecklistCount = checklistCount,
                isPremium = isPremium
            )
        }
    }

    suspend fun getFillCount(checklistId: Long): Int {
        return checklistRepository.getFillCountByChecklistId(checklistId)
    }
}
