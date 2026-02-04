package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PurchaseResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository

class PurchaseProductUseCase(
    private val repository: PaywallRepository,
    private val userDataRepository: UserDataRepository
) {
    suspend operator fun invoke(packageId: String): PurchaseResult {
        val result = repository.purchase(packageId)

        // After successful purchase, restore credits immediately
        // Server will set credits to premium_daily_credits_cap (300)
        if (result is PurchaseResult.Success) {
            userDataRepository.restoreCreditsAfterPurchase()
        }

        return result
    }
}
