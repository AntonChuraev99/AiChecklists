package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AiActionCostProvider
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.CreditsConfigProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Server-side cost of one Analyze run — a thin projection of the shared [CreditsConfigProvider]
 * (`get_credits_info` → `config.action_cost`). The fetch, the cache and the "keep last good value,
 * log a warning" policy live there; Analyze and AI Chat read the same single source.
 */
class AiActionCostProviderImpl(
    private val creditsConfigProvider: CreditsConfigProvider,
    appScope: CoroutineScope,
) : AiActionCostProvider {

    override val actionCost: StateFlow<Int?> = creditsConfigProvider.config
        .map { it?.actionCost }
        .stateIn(appScope, SharingStarted.Eagerly, creditsConfigProvider.config.value?.actionCost)

    override suspend fun refresh(): Result<Int> = creditsConfigProvider.refresh().map { it.actionCost }
}
