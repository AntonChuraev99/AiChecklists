package com.antonchuraev.homesearchchecklist.feature.user.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.UserApiService
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.CreditsConfig
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.CreditsConfigProvider
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory cache of the server's credit economics (`get_credits_info`). Koin single, shared by
 * Analyze (button gate) and AI Chat (out-of-credits banner).
 *
 * A failed refresh keeps the last good value and logs a warning; a blank user id is a failure (the
 * user is not registered yet), not a silent skip. Null = never fetched — consumers show copy
 * without a number and never gate on it.
 */
class CreditsConfigProviderImpl(
    private val userApiService: UserApiService,
    private val userDataRepository: UserDataRepository,
    private val logger: AppLogger,
) : CreditsConfigProvider {

    private val _config = MutableStateFlow<CreditsConfig?>(null)
    override val config: StateFlow<CreditsConfig?> = _config.asStateFlow()

    override suspend fun refresh(): Result<CreditsConfig> {
        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "credits config not refreshed: user not registered yet (kept ${_config.value})")
            return Result.failure(IllegalStateException("user not registered"))
        }
        val result = try {
            userApiService.getCreditsInfo(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result
            .onSuccess { _config.value = it }
            .onFailure { e -> logger.warning(TAG, "credits config refresh failed, kept ${_config.value}: ${e.message}") }
        return result
    }

    private companion object {
        const val TAG = "Credits"
    }
}
