package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository

class CompleteOnboardingUseCase(
    private val repository: UserDataRepository
) {
    suspend operator fun invoke() {
        repository.update(
            UserData(isOnboardingPassed = true)
        )
    }
}