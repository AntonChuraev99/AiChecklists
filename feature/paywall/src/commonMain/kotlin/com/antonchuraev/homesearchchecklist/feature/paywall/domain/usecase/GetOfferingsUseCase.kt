package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallOffering
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository

class GetOfferingsUseCase(
    private val repository: PaywallRepository
) {
    suspend operator fun invoke(): Result<PaywallOffering?> = repository.getOfferings()
}
