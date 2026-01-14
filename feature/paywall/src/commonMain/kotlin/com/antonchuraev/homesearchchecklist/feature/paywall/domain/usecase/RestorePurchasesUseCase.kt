package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.RestoreResult
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.repository.PaywallRepository

class RestorePurchasesUseCase(
    private val repository: PaywallRepository
) {
    suspend operator fun invoke(): RestoreResult = repository.restorePurchases()
}
