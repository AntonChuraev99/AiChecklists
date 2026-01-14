package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class UserDataRepositoryImpl(
    private val appScope: CoroutineScope
) : UserDataRepository {

    private val appDatastore: AppDatastore = AppDatastore("user/datastore")

    private val userDataFlow = appDatastore
        .observeBoolean(IS_ONBOARDING_PASSED_KEY, false)
        .map { isOnboardingPassed -> UserData(isOnboardingPassed) }
        .stateIn(
            appScope,
            SharingStarted.Eagerly,
            DEFAULT_USER_DATA
        )

    override fun getUserDataFlow(): Flow<UserData> {
        return userDataFlow
    }

    override suspend fun getUserData(): UserData {
        return appDatastore
            .observeBoolean(IS_ONBOARDING_PASSED_KEY, false)
            .map { isOnboardingPassed -> UserData(isOnboardingPassed) }
            .first()
    }

    override suspend fun update(userData: UserData) {
        appDatastore.saveBoolean(IS_ONBOARDING_PASSED_KEY , userData.isOnboardingPassed)
    }

    companion object {

        private const val IS_ONBOARDING_PASSED_KEY = "is_onboarding_passed"

        private val DEFAULT_USER_DATA = UserData(isOnboardingPassed = false)

    }
}

