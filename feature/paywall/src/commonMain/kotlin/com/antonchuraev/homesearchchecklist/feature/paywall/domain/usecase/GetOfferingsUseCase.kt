package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallRemoteConfig
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository

class GetOfferingsUseCase(
    private val repository: PaywallRepository,
    private val getPaywallConfig: GetPaywallConfigUseCase,
) {
    suspend operator fun invoke(): Result<PaywallOffering?> {
        val offeringId = getPaywallConfig().currentOffer ?: PaywallRemoteConfig.DEFAULT_OFFER
        return repository.getOfferings(offeringId)
    }
}
