package com.antonchuraev.homesearchchecklist.feature.user.domain.usecase

import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository

/**
 * Marks onboarding as passed. Writes ONLY the onboarding flag — never credits or premium,
 * which `register_user` has already saved by the time onboarding completes.
 */
class CompleteOnboardingUseCase(
    private val repository: UserDataRepository
) {
    suspend operator fun invoke() {
        repository.setOnboardingPassed(true)
    }
}
