package com.antonchuraev.homesearchchecklist.consent

/**
 * Picks the country code used for the EEA/UK consent decision from the three signals, in order of
 * reliability: SIM country, network country, device locale.
 *
 * `TelephonyManager.getSimCountryIso()` / `getNetworkCountryIso()` return an EMPTY STRING, not
 * null, when the value is unavailable (no SIM, no cell radio — typical for a tablet), so a blank
 * value must fall through to the next signal. An elvis chain on the raw values never did: an EEA
 * tablet resolved to `""`, was treated as non-EEA and got analytics consent granted unasked.
 */
internal fun resolveConsentCountryCode(
    simCountryIso: String?,
    networkCountryIso: String?,
    localeCountry: String,
): String = sequenceOf(simCountryIso, networkCountryIso, localeCountry)
    .mapNotNull { it?.trim()?.lowercase()?.takeIf(String::isNotEmpty) }
    .firstOrNull()
    .orEmpty()

/** True when the resolved country is in the EEA or the UK. */
internal fun isEeaCountry(
    simCountryIso: String?,
    networkCountryIso: String?,
    localeCountry: String,
): Boolean = resolveConsentCountryCode(simCountryIso, networkCountryIso, localeCountry) in
    ConsentManager.EEA_COUNTRY_CODES

/**
 * Firebase consent applied synchronously at process start, before the region is known.
 *
 * - consent already collected → the user's own choice;
 * - region known (cached from an earlier resolution) → granted only outside the EEA/UK;
 * - region not resolved yet → DENIED: an EEA/UK user must never be granted, even for a moment,
 *   before they choose. A non-EEA user is upgraded to granted once the background resolution
 *   finishes ([shouldGrantAfterRegionResolved]).
 */
internal fun startupConsentGranted(
    consentCollected: Boolean,
    consentGranted: Boolean,
    knownIsEea: Boolean?,
): Boolean = when {
    consentCollected -> consentGranted
    knownIsEea == null -> false
    else -> !knownIsEea
}

/**
 * After the background region resolution: grant only a non-EEA user who has not made an explicit
 * choice in the meantime (an explicit choice always wins and is already applied).
 */
internal fun shouldGrantAfterRegionResolved(isEea: Boolean, consentCollected: Boolean): Boolean =
    !isEea && !consentCollected

/**
 * Consent to apply once this process has a FRESH region (detected on every process start while
 * consent is not collected); null = leave the current consent alone.
 *
 * - explicit choice → null (it is already applied and always wins);
 * - fresh EEA/UK → DENIED, which also downgrades a GRANTED that came from a stale non-EEA cache
 *   (installed outside the EEA, then moved / swapped the SIM);
 * - fresh non-EEA → GRANTED ([shouldGrantAfterRegionResolved]).
 */
internal fun consentAfterFreshRegion(isEea: Boolean, consentCollected: Boolean): Boolean? = when {
    consentCollected -> null
    isEea -> false
    else -> shouldGrantAfterRegionResolved(isEea, consentCollected)
}
