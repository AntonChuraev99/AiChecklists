package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository

class PurchaseProductUseCase(
    private val repository: PaywallRepository
) {
    suspend operator fun invoke(packageId: String): PurchaseResult =
        repository.purchase(packageId)
}
