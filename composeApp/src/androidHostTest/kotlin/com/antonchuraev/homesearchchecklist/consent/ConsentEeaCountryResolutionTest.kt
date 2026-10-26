package com.antonchuraev.homesearchchecklist.consent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Off-device tests for the EEA/UK country resolution behind the GDPR consent gate.
 *
 * `TelephonyManager.getSimCountryIso()` / `getNetworkCountryIso()` return an EMPTY STRING (not
 * null) when the value is unavailable — a tablet without a SIM. An empty value must fall through
 * to the next signal, otherwise an EEA device without a SIM is treated as non-EEA and gets
 * analytics consent GRANTED without being asked.
 */
class ConsentEeaCountryResolutionTest {

    @Test
    fun emptySimAndNetwork_fallsBackToLocale_germanLocaleIsEea() {
        assertTrue(
            isEeaCountry(simCountryIso = "", networkCountryIso = "", localeCountry = "DE"),
            "SIM \"\" + network \"\" + locale DE must resolve to EEA via the locale fallback",
        )
    }

    @Test
    fun emptySim_fallsBackToNetwork_italianNetworkIsEea() {
        assertTrue(
            isEeaCountry(simCountryIso = "", networkCountryIso = "it", localeCountry = "US"),
            "SIM \"\" must fall through to network \"it\"",
        )
    }

    @Test
    fun frenchSim_isEea() {
        assertTrue(isEeaCountry(simCountryIso = "fr", networkCountryIso = "", localeCountry = "US"))
    }

    @Test
    fun usSim_isNotEea_evenWithEeaNetworkAndLocale() {
        assertFalse(isEeaCountry(simCountryIso = "us", networkCountryIso = "de", localeCountry = "DE"))
    }

    @Test
    fun everythingEmpty_usLocale_isNotEea() {
        assertFalse(isEeaCountry(simCountryIso = "", networkCountryIso = "", localeCountry = "US"))
    }

    @Test
    fun blankAndNullSignals_areSkipped() {
        assertEquals(
            "gb",
            resolveConsentCountryCode(simCountryIso = "  ", networkCountryIso = null, localeCountry = "GB"),
        )
    }

    // --- startup consent decision (applied before the region is resolved) ---

    @Test
    fun startup_regionNotResolvedYet_isDenied() {
        assertFalse(startupConsentGranted(consentCollected = false, consentGranted = false, knownIsEea = null))
    }

    @Test
    fun startup_cachedEea_isDenied() {
        assertFalse(startupConsentGranted(consentCollected = false, consentGranted = false, knownIsEea = true))
    }

    @Test
    fun startup_cachedNonEea_isGranted() {
        assertTrue(startupConsentGranted(consentCollected = false, consentGranted = false, knownIsEea = false))
    }

    @Test
    fun startup_collectedChoice_winsOverRegion() {
        assertFalse(startupConsentGranted(consentCollected = true, consentGranted = false, knownIsEea = false))
        assertTrue(startupConsentGranted(consentCollected = true, consentGranted = true, knownIsEea = true))
    }

    @Test
    fun afterResolution_grantsOnlyNonEeaWithoutExplicitChoice() {
        assertTrue(shouldGrantAfterRegionResolved(isEea = false, consentCollected = false))
        assertFalse(shouldGrantAfterRegionResolved(isEea = true, consentCollected = false))
        assertFalse(shouldGrantAfterRegionResolved(isEea = false, consentCollected = true))
    }

    // --- fresh per-process region vs the cached startup decision ---

    @Test
    fun freshRegion_eeaWithoutChoice_downgradesToDenied() {
        // Installed outside the EEA (cache = non-EEA → started GRANTED), now detected in the EEA:
        // the fresh result must revoke the cached grant until the user chooses.
        assertEquals(
            false,
            consentAfterFreshRegion(isEea = true, consentCollected = false),
            "fresh EEA region must downgrade a cached GRANTED to DENIED",
        )
    }

    @Test
    fun freshRegion_nonEeaWithoutChoice_grants() {
        assertEquals(true, consentAfterFreshRegion(isEea = false, consentCollected = false))
    }

    @Test
    fun freshRegion_explicitChoice_isLeftUntouched() {
        assertNull(consentAfterFreshRegion(isEea = true, consentCollected = true))
        assertNull(consentAfterFreshRegion(isEea = false, consentCollected = true))
    }
}
