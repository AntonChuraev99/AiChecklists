package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.SubscriptionStatus
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import kotlinx.coroutines.flow.Flow

class GetSubscriptionStatusUseCase(
    private val repository: PaywallRepository
) {
    operator fun invoke(): Flow<SubscriptionStatus> = repository.subscriptionStatus

    suspend fun refresh() {
        repository.refreshSubscriptionStatus()
    }
}
