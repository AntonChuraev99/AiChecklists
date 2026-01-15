package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

class UserDataRepositoryImpl(
    private val appScope: CoroutineScope,
    private val deviceIdProvider: DeviceIdProvider,
    private val userApiService: UserApiService,
    private val logger: AppLogger
) : UserDataRepository {

    companion object {
        private const val TAG = "UserDataRepository"

        private const val USER_ID_KEY = "user_id"
        private const val IS_ONBOARDING_PASSED_KEY = "is_onboarding_passed"
        private const val IS_PREMIUM_KEY = "is_premium"
        private const val AI_CREDITS_KEY = "ai_credits"

        private val DEFAULT_USER_DATA = UserData(
            userId = "",
            isOnboardingPassed = false,
            isPremium = false,
            aiCredits = 0
        )
    }

    private val appDatastore: AppDatastore = AppDatastore("user/datastore")

    private val userDataFlow = combine(
        appDatastore.observeString(USER_ID_KEY, ""),
        appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false),
        appDatastore.observeBoolean(IS_PREMIUM_KEY, false),
        appDatastore.observeInt(AI_CREDITS_KEY, 0)
    ) { userId, isOnboardingPassed, isPremium, aiCredits ->
        UserData(
            userId = userId,
            isOnboardingPassed = isOnboardingPassed,
            isPremium = isPremium,
            aiCredits = aiCredits
        )
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        DEFAULT_USER_DATA
    )

    override fun getUserDataFlow(): Flow<UserData> {
        return userDataFlow
    }

    override suspend fun getUserData(): UserData {
        val userId = appDatastore.observeString(USER_ID_KEY, "").first()
        val isOnboardingPassed = appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false).first()
        val isPremium = appDatastore.observeBoolean(IS_PREMIUM_KEY, false).first()
        val aiCredits = appDatastore.observeInt(AI_CREDITS_KEY, 0).first()

        return UserData(
            userId = userId,
            isOnboardingPassed = isOnboardingPassed,
            isPremium = isPremium,
            aiCredits = aiCredits
        )
    }

    override suspend fun update(userData: UserData) {
        appDatastore.saveBoolean(IS_ONBOARDING_PASSED_KEY, userData.isOnboardingPassed)
        appDatastore.saveBoolean(IS_PREMIUM_KEY, userData.isPremium)
        appDatastore.saveInt(AI_CREDITS_KEY, userData.aiCredits)
        if (userData.userId.isNotBlank()) {
            appDatastore.saveString(USER_ID_KEY, userData.userId)
        }
    }

    override suspend fun ensureUserRegistered(): Result<UserData> {
        logger.debug(TAG, "ensureUserRegistered: starting...")

        // Check if user is already registered locally
        val currentUserId = appDatastore.observeString(USER_ID_KEY, "").first()
        logger.debug(TAG, "ensureUserRegistered: currentUserId='$currentUserId'")

        if (currentUserId.isNotBlank()) {
            // User already registered, sync with server to get fresh data
            logger.debug(TAG, "ensureUserRegistered: user exists, syncing with server...")
            return syncWithServer()
        }

        // User not registered, call the server
        val deviceId = deviceIdProvider.getDeviceId()
        logger.debug(TAG, "ensureUserRegistered: registering new user with deviceId=$deviceId")

        return registerAndSave(deviceId)
    }

    override suspend fun syncWithServer(): Result<UserData> {
        logger.debug(TAG, "syncWithServer: starting...")

        val deviceId = deviceIdProvider.getDeviceId()
        logger.debug(TAG, "syncWithServer: deviceId=$deviceId")

        return registerAndSave(deviceId)
    }

    private suspend fun registerAndSave(deviceId: String): Result<UserData> {
        return userApiService.registerUser(
            deviceId = deviceId,
            appVersion = null,
            platform = getPlatformName()
        ).onSuccess { result ->
            logger.info(TAG, "registerAndSave: SUCCESS - userId=${result.userId}, aiCredits=${result.aiCredits}, isPremium=${result.isPremium}")
            // Save user data locally
            appDatastore.saveString(USER_ID_KEY, result.userId)
            appDatastore.saveBoolean(IS_PREMIUM_KEY, result.isPremium)
            appDatastore.saveInt(AI_CREDITS_KEY, result.aiCredits)
            logger.debug(TAG, "registerAndSave: saved to datastore")
        }.onFailure { error ->
            logger.error(TAG, "registerAndSave: FAILED - ${error.message}", error)
        }.map { result ->
            UserData(
                userId = result.userId,
                isOnboardingPassed = appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false).first(),
                isPremium = result.isPremium,
                aiCredits = result.aiCredits
            )
        }
    }
}

