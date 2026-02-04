package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository

class RestorePurchasesUseCase(
    private val repository: PaywallRepository,
    private val userDataRepository: UserDataRepository
) {
    suspend operator fun invoke(): RestoreResult {
        val result = repository.restorePurchases()

        // After successful restore, restore credits immediately
        // Server will set credits to premium_daily_credits_cap (300)
        if (result is RestoreResult.Success) {
            userDataRepository.restoreCreditsAfterPurchase()
        }

        return result
    }
}
