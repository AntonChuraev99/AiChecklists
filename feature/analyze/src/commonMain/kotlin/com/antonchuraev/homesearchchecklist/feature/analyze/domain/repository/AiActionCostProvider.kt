package com.antonchuraev.homesearchchecklist.feature.analyze.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Credits one Analyze run costs, as the SERVER charges it (`get_credits_info` → `config.action_cost`,
 * itself read from Remote Config `ai_action_cost` on the server). Never a client constant: the client
 * shipped `aiActionCost = 30` while the server charged 20, which disabled the button at 20 credits
 * and gave Free users 4 runs instead of 5.
 *
 * [actionCost] is null until the first successful fetch (and stays null when it fails). Null means
 * "unknown": the Analyze button is NOT gated on it — the server stays the gate (402 → credits message).
 * Implementation (commonMain, `@feature-expert`): Koin single, in-memory cache, refreshed on Analyze
 * screen entry; failure logged with `AppLogger.warning`, last good value kept.
 */
interface AiActionCostProvider {
    val actionCost: StateFlow<Int?>

    suspend fun refresh(): Result<Int>
}
