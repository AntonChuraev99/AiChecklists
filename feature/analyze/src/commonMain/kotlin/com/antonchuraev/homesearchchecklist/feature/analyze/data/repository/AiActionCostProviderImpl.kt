package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository.AiActionCostProvider
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Server-side cost of one Analyze run (`get_credits_info` → `config.action_cost`), cached in memory.
 * A failed refresh keeps the last good value and logs a warning; while nothing was ever fetched the
 * cost stays null and the Analyze button is not gated on it (the server stays the gate).
 */
class AiActionCostProviderImpl(
    private val firebaseAiService: FirebaseAiService,
    private val userDataRepository: UserDataRepository,
    private val logger: AppLogger,
) : AiActionCostProvider {

    private val _actionCost = MutableStateFlow<Int?>(null)
    override val actionCost: StateFlow<Int?> = _actionCost.asStateFlow()

    override suspend fun refresh(): Result<Int> {
        val userId = userDataRepository.getUserData().userId
        if (userId.isBlank()) {
            logger.warning(TAG, "action cost not refreshed: user not registered yet (kept ${_actionCost.value})")
            return Result.failure(IllegalStateException("user not registered"))
        }
        val result = try {
            firebaseAiService.getCreditsInfo(userId).map { it.actionCost }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
        result
            .onSuccess { _actionCost.value = it }
            .onFailure { e -> logger.warning(TAG, "action cost refresh failed, kept ${_actionCost.value}: ${e.message}") }
        return result
    }

    private companion object {
        const val TAG = "Analyze"
    }
}
