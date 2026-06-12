package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.user.data.device.DeviceIdProvider
import com.antonchuraev.homesearchchecklist.feature.user.data.device.getPlatformName
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.RegisterUserResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.LinkGoogleAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

class UserDataRepositoryImpl(
    private val appScope: CoroutineScope,
    private val deviceIdProvider: DeviceIdProvider,
    private val userApiService: UserApiService,
    private val logger: AppLogger,
    private val appDatastore: AppDatastore,
    private val analyticsTracker: AnalyticsTracker
) : UserDataRepository {

    companion object {
        private const val TAG = "UserDataRepository"

        private const val USER_ID_KEY = "user_id"
        private const val IS_ONBOARDING_PASSED_KEY = "is_onboarding_passed"
        private const val IS_PREMIUM_KEY = "is_premium"
        private const val AI_CREDITS_KEY = "ai_credits"
        internal const val IS_PAYWALL_LINKED_KEY = "is_paywall_linked"
        private const val FIRST_LAUNCH_AT_KEY = "first_launch_at"
        private const val GOOGLE_EMAIL_KEY = "google_email"
        private const val GOOGLE_DISPLAY_NAME_KEY = "google_display_name"
        private const val GOOGLE_PHOTO_URL_KEY = "google_photo_url"
        private const val IS_GOOGLE_LINKED_KEY = "is_google_linked"

        private const val MAX_RESTORE_RETRIES = 3
        private const val RESTORE_RETRY_BASE_DELAY_MS = 2000L

        private val DEFAULT_USER_DATA = UserData(
            userId = "",
            isOnboardingPassed = false,
            isPremium = false,
            aiCredits = 0
        )
    }

    private val baseUserFlow = combine(
        appDatastore.observeString(USER_ID_KEY, ""),
        appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false),
        appDatastore.observeBoolean(IS_PREMIUM_KEY, false),
        appDatastore.observeInt(AI_CREDITS_KEY, 0)
    ) { userId, isOnboardingPassed, isPremium, aiCredits ->
        UserData(
            userId = userId,
            isOnboardingPassed = isOnboardingPassed,
            isPremium = isPremium,
            aiCredits = aiCredits,
        )
    }

    private val googleFlow = combine(
        appDatastore.observeString(GOOGLE_EMAIL_KEY, ""),
        appDatastore.observeString(GOOGLE_DISPLAY_NAME_KEY, ""),
        appDatastore.observeBoolean(IS_GOOGLE_LINKED_KEY, false),
    ) { email, displayName, isLinked ->
        Triple(email.ifBlank { null }, displayName.ifBlank { null }, isLinked)
    }

    private val userDataFlow = combine(baseUserFlow, googleFlow) { base, (email, name, linked) ->
        base.copy(
            googleEmail = email,
            googleDisplayName = name,
            isGoogleLinked = linked,
        )
    }.stateIn(
        appScope,
        SharingStarted.Eagerly,
        DEFAULT_USER_DATA
    )

    override fun getUserDataFlow(): StateFlow<UserData> {
        return userDataFlow
    }

    override suspend fun getUserData(): UserData {
        // Fast path: StateFlow already has data from DataStore (common case for returning users)
        val snapshot = userDataFlow.value
        if (snapshot.userId.isNotBlank()) return snapshot

        // Cold start: DataStore may not have emitted yet, wait briefly for real data
        return withTimeoutOrNull(100) {
            userDataFlow.first { it.userId.isNotBlank() }
        } ?: snapshot
    }

    override suspend fun update(userData: UserData) {
        appDatastore.saveBoolean(IS_ONBOARDING_PASSED_KEY, userData.isOnboardingPassed)
        appDatastore.saveBoolean(IS_PREMIUM_KEY, userData.isPremium)
        appDatastore.saveInt(AI_CREDITS_KEY, userData.aiCredits)
        if (userData.userId.isNotBlank()) {
            appDatastore.saveString(USER_ID_KEY, userData.userId)
        }
    }

    override suspend fun ensureUserRegistered(): Result<RegistrationData> {
        logger.debug(TAG, "ensureUserRegistered: starting...")

        // Check if user is already registered locally
        val currentUserId = appDatastore.observeString(USER_ID_KEY, "").first()
        logger.debug(TAG, "ensureUserRegistered: currentUserId='${currentUserId.take(8)}...'")

        if (currentUserId.isNotBlank()) {
            // User already registered, sync with server to get fresh data
            logger.debug(TAG, "ensureUserRegistered: user exists, syncing with server...")
            return syncWithServer()
        }

        // User not registered, call the server
        val deviceId = deviceIdProvider.getOrCreateDeviceId()
        logger.debug(TAG, "ensureUserRegistered: registering new user with deviceId=${deviceId.take(8)}...")

        return registerAndSave(deviceId)
    }

    override suspend fun syncWithServer(): Result<RegistrationData> {
        logger.debug(TAG, "syncWithServer: starting...")

        val deviceId = deviceIdProvider.getOrCreateDeviceId()
        logger.debug(TAG, "syncWithServer: deviceId=${deviceId.take(8)}...")

        return registerAndSave(deviceId)
    }

    override suspend fun isPaywallLinked(): Boolean {
        return appDatastore.observeBoolean(IS_PAYWALL_LINKED_KEY, false).first()
    }

    override suspend fun setPaywallLinked(linked: Boolean) {
        appDatastore.saveBoolean(IS_PAYWALL_LINKED_KEY, linked)
    }

    override suspend fun restoreCreditsAfterPurchase(): Result<Int> {
        analyticsTracker.event(AnalyticsEvents.Credits.RESTORE_STARTED)
        logger.debug(TAG, "restoreCreditsAfterPurchase: starting...")

        val userId = appDatastore.observeString(USER_ID_KEY, "").first()
        if (userId.isBlank()) {
            logger.error(TAG, "restoreCreditsAfterPurchase: no user_id found")
            analyticsTracker.event(AnalyticsEvents.Credits.RESTORE_FAILED, mapOf(AnalyticsParams.ERROR to "no_user_id"))
            return Result.failure(IllegalStateException("User not registered"))
        }

        logger.debug(TAG, "restoreCreditsAfterPurchase: calling API for userId=${userId.take(8)}...")

        var lastError: Throwable? = null
        for (attempt in 1..MAX_RESTORE_RETRIES) {
            val apiResult = userApiService.restoreCreditsAfterPurchase(userId)

            if (apiResult.isSuccess) {
                val result = apiResult.getOrThrow()
                logger.info(TAG, "restoreCreditsAfterPurchase: SUCCESS (attempt $attempt) - aiCredits=${result.aiCredits}, isPremium=${result.isPremium}")
                appDatastore.saveInt(AI_CREDITS_KEY, result.aiCredits)
                appDatastore.saveBoolean(IS_PREMIUM_KEY, result.isPremium)
                analyticsTracker.event(AnalyticsEvents.Credits.RESTORE_SUCCESS, mapOf(
                    "credits" to result.aiCredits,
                    "attempt" to attempt
                ))
                return Result.success(result.aiCredits)
            }

            lastError = apiResult.exceptionOrNull()
            logger.error(TAG, "restoreCreditsAfterPurchase: attempt $attempt/$MAX_RESTORE_RETRIES FAILED - ${lastError?.message}", lastError)

            if (attempt < MAX_RESTORE_RETRIES) {
                analyticsTracker.event(AnalyticsEvents.Credits.RESTORE_RETRY, mapOf(
                    "attempt" to attempt,
                    AnalyticsParams.ERROR to (lastError?.message ?: "unknown")
                ))
                delay(RESTORE_RETRY_BASE_DELAY_MS * attempt)
            }
        }

        val errorMessage = lastError?.message ?: "unknown"
        logger.error(TAG, "restoreCreditsAfterPurchase: ALL $MAX_RESTORE_RETRIES attempts FAILED")
        analyticsTracker.event(AnalyticsEvents.Credits.RESTORE_FAILED, mapOf(
            AnalyticsParams.ERROR to errorMessage,
            "attempts" to MAX_RESTORE_RETRIES
        ))
        return Result.failure(lastError ?: IllegalStateException("All retries failed"))
    }

    override suspend fun linkGoogleAccount(
        idToken: String,
        platform: String,
    ): Result<LinkGoogleAccountResult> {
        val userId = appDatastore.observeString(USER_ID_KEY, "").first()
        if (userId.isBlank()) {
            return Result.failure(IllegalStateException("User not registered"))
        }

        return userApiService.linkGoogleAccount(
            userId = userId,
            idToken = idToken,
            platform = platform,
        ).map { apiResult ->
            appDatastore.saveInt(AI_CREDITS_KEY, apiResult.aiCredits)
            appDatastore.saveBoolean(IS_PREMIUM_KEY, apiResult.isPremium)
            appDatastore.saveString(GOOGLE_EMAIL_KEY, apiResult.googleEmail)
            appDatastore.saveBoolean(IS_GOOGLE_LINKED_KEY, true)
            if (apiResult.userId != userId) {
                appDatastore.saveString(USER_ID_KEY, apiResult.userId)
            }

            LinkGoogleAccountResult(
                googleEmail = apiResult.googleEmail,
                aiCredits = apiResult.aiCredits,
                isPremium = apiResult.isPremium,
                bonusCreditsGranted = apiResult.bonusCreditsGranted,
                isExistingAccount = apiResult.isExistingAccount,
            )
        }
    }

    override suspend fun clearGoogleAccountData() {
        appDatastore.saveString(GOOGLE_EMAIL_KEY, "")
        appDatastore.saveString(GOOGLE_DISPLAY_NAME_KEY, "")
        appDatastore.saveString(GOOGLE_PHOTO_URL_KEY, "")
        appDatastore.saveBoolean(IS_GOOGLE_LINKED_KEY, false)
    }

    override suspend fun getFirstLaunchAtMillis(): Long {
        return appDatastore.observeString(FIRST_LAUNCH_AT_KEY, "").first().toLongOrNull() ?: 0L
    }

    private suspend fun ensureFirstLaunchRecorded() {
        val existing = appDatastore.observeString(FIRST_LAUNCH_AT_KEY, "").first()
        if (existing.isBlank()) {
            appDatastore.saveString(FIRST_LAUNCH_AT_KEY, currentTimeMillis().toString())
        }
    }

    private suspend fun registerAndSave(deviceId: String): Result<RegistrationData> {
        return userApiService.registerUser(
            deviceId = deviceId,
            appVersion = null,
            platform = getPlatformName()
        ).onSuccess { result ->
            logger.info(TAG, "registerAndSave: SUCCESS - userId=${result.userId.take(8)}..., aiCredits=${result.aiCredits}, isPremium=${result.isPremium}")
            // Save user data locally
            appDatastore.saveString(USER_ID_KEY, result.userId)
            appDatastore.saveBoolean(IS_PREMIUM_KEY, result.isPremium)
            appDatastore.saveInt(AI_CREDITS_KEY, result.aiCredits)
            ensureFirstLaunchRecorded()
            logger.debug(TAG, "registerAndSave: saved to datastore")
        }.onFailure { error ->
            logger.error(TAG, "registerAndSave: FAILED - ${error.message}", error)
        }.map { result ->
            RegistrationData(
                userData = UserData(
                    userId = result.userId,
                    isOnboardingPassed = appDatastore.observeBoolean(IS_ONBOARDING_PASSED_KEY, false).first(),
                    isPremium = result.isPremium,
                    aiCredits = result.aiCredits
                ),
                isNewUser = result.isNewUser
            )
        }
    }
}
