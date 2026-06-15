package com.antonchuraev.homesearchchecklist.feature.paywall.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.antonchuraev.homesearchchecklist.feature.paywall.domain.model.PaywallRemoteConfig
import kotlinx.serialization.json.Json

/**
 * Resolves paywall config from Remote Config (`paywall_config` JSON). Falls back to
 * [PaywallRemoteConfig.DEFAULT_OFFER] on empty/invalid/partial config — offer selection
 * always has a valid baseline. Returned currentOffer is guaranteed non-blank.
 */
class GetPaywallConfigUseCase(
    private val remoteConfigProvider: RemoteConfigProvider,
    private val logger: AppLogger? = null,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    operator fun invoke(): PaywallRemoteConfig {
        val raw = remoteConfigProvider.getString(
            RemoteConfigKeys.PAYWALL_CONFIG,
            RemoteConfigDefaults.PAYWALL_CONFIG,
        )
        if (raw.isBlank()) {
            logger?.info(TAG, "paywall_config RC empty/absent → baseline currentOffer='${PaywallRemoteConfig.DEFAULT_OFFER}'")
            return baseline()
        }
        return try {
            val parsed = json.decodeFromString<PaywallRemoteConfig>(raw)
            val offer = parsed.currentOffer?.takeIf { it.isNotBlank() }
                ?: PaywallRemoteConfig.DEFAULT_OFFER
            logger?.info(TAG, "paywall_config RC raw='$raw' → parsed currentOffer='$offer'")
            parsed.copy(currentOffer = offer)
        } catch (e: Exception) {
            logger?.error(TAG, "paywall_config parse failed (raw='$raw'), using baseline: ${e.message}", e)
            baseline()
        }
    }

    private fun baseline() = PaywallRemoteConfig(currentOffer = PaywallRemoteConfig.DEFAULT_OFFER)

    private companion object {
        const val TAG = "GetPaywallConfig"
    }
}
