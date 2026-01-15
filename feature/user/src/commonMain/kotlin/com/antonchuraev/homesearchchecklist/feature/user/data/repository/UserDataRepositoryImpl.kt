package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class UserDataRepositoryImpl(
    private val appScope: CoroutineScope
) : UserDataRepository {

    private val appDatastore: AppDatastore = AppDatastore("user/datastore")

    private val userDataFlow = combine(
        appDatastore.observeString(USER_ID_KEY, ""),
        appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false),
        appDatastore.observeBoolean(IS_PREMIUM_KEY, false)
    ) { userId, isOnboardingPassed, isPremium ->
        UserData(
            userId = userId,
            isOnboardingPassed = isOnboardingPassed,
            isPremium = isPremium
        )
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        DEFAULT_USER_DATA
    )

    override fun getUserDataFlow(): Flow<UserData> {
        return userDataFlow
    }

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun getUserData(): UserData {
        val userId = appDatastore.observeString(USER_ID_KEY, "").first()
        val isOnboardingPassed = appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false).first()
        val isPremium = appDatastore.observeBoolean(IS_PREMIUM_KEY, false).first()

        // Generate user ID if not exists
        val actualUserId = if (userId.isBlank()) {
            val newUserId = Uuid.random().toString()
            appDatastore.saveString(USER_ID_KEY, newUserId)
            newUserId
        } else {
            userId
        }

        return UserData(
            userId = actualUserId,
            isOnboardingPassed = isOnboardingPassed,
            isPremium = isPremium
        )
    }

    override suspend fun update(userData: UserData) {
        appDatastore.saveBoolean(IS_ONBOARDING_PASSED_KEY, userData.isOnboardingPassed)
        appDatastore.saveBoolean(IS_PREMIUM_KEY, userData.isPremium)
        if (userData.userId.isNotBlank()) {
            appDatastore.saveString(USER_ID_KEY, userData.userId)
        }
    }

    companion object {

        private const val USER_ID_KEY = "user_id"
        private const val IS_ONBOARDING_PASSED_KEY = "is_onboarding_passed"
        private const val IS_PREMIUM_KEY = "is_premium"

        private val DEFAULT_USER_DATA = UserData(
            userId = "",
            isOnboardingPassed = false,
            isPremium = false
        )

    }
}

