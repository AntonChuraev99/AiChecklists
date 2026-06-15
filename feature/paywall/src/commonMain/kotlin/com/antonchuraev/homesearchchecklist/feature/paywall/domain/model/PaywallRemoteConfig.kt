package com.antonchuraev.homesearchchecklist.feature.paywall.domain.model

import kotlinx.serialization.Serializable

/**
 * Remote-Config-driven paywall configuration, parsed from the `paywall_config` JSON key.
 *
 * Every field MUST have a default value so old/partial JSON never throws MissingFieldException;
 * the parser MUST use ignoreUnknownKeys so adding fields later doesn't break old clients.
 *
 * Extensible: only [currentOffer] today; future A/B fields land here.
 */
@Serializable
data class PaywallRemoteConfig(
    /** RevenueCat offering identifier to show as the main offer. Null/blank → [DEFAULT_OFFER]. */
    val currentOffer: String? = null,
) {
    companion object {
        /** Hard baseline / A/B control offer when RC is empty, invalid, or omits currentOffer. */
        const val DEFAULT_OFFER = "month1.99Year20NoTrial"
    }
}
