package com.antonchuraev.homesearchchecklist.feature.user.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * The server's credit economics, as the SERVER applies them right now: Cloud Function
 * `get_credits_info` → `config`, itself read from Firestore `remote_config/current` on the server
 * (the same source the Telegram bot reads). Never a client constant, never Firebase RC.
 *
 * @param actionCost Credits one Analyze run costs (`action_cost`).
 * @param premiumDailyCreditsCap What a Premium balance refills to every day
 *   (`premium_daily_credits_cap`) — the number the "Become Pro" CTAs promise.
 */
data class CreditsConfig(
    val actionCost: Int,
    val premiumDailyCreditsCap: Int,
)

/**
 * In-memory cache of [CreditsConfig], shared by Analyze (button gate) and AI Chat (out-of-credits
 * banner). Lives in `feature/user` because both features already depend on it and the credit
 * balance ([UserDataRepository]) is here too.
 *
 * [config] is null until the first successful fetch and keeps the last good value after a failed
 * one. Null means "unknown": consumers show copy without a number and do NOT gate on it — the server
 * stays the gate (402). Implementation (IMPLEMENT): Koin single; refreshed on Analyze screen entry
 * and on chat open; failure logged with `AppLogger.warning`, last good value kept; a blank user id
 * is a failure, not a silent skip.
 */
interface CreditsConfigProvider {
    val config: StateFlow<CreditsConfig?>

    suspend fun refresh(): Result<CreditsConfig>
}
