package com.antonchuraev.homesearchchecklist.consent

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Manages GDPR/ePrivacy consent for EEA/UK users.
 *
 * Uses Firebase Analytics setConsent{} API directly (no UMP SDK needed,
 * since the app doesn't use AdMob).
 *
 * Consent state is persisted in a dedicated SharedPreferences file
 * (separate from the app's main DataStore) because consent must be
 * applied synchronously before Firebase initialization.
 */
class ConsentManager(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val isConsentCollected: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_COLLECTED, false)

    val isConsentGranted: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_GRANTED, false)

    /**
     * Check if consent dialog is required.
     * Only EEA/UK users need explicit consent under GDPR/ePrivacy.
     */
    fun isConsentRequired(): Boolean {
        if (isConsentCollected) return false
        return isDeviceInEea()
    }

    /**
     * Apply consent defaults to Firebase Analytics.
     * Must be called BEFORE any Firebase Analytics events are logged.
     *
     * - If consent was previously collected → apply saved state
     * - If not collected AND EEA → deny all (default safe)
     * - If not collected AND non-EEA → grant all (no legal requirement)
     */
    fun applyConsentDefaults() {
        val granted = when {
            isConsentCollected -> isConsentGranted
            isDeviceInEea() -> false
            else -> true
        }
        applyFirebaseConsent(granted)
    }

    /**
     * Save user's consent choice and apply to Firebase.
     */
    fun setConsent(granted: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONSENT_GRANTED, granted)
            .putBoolean(KEY_CONSENT_COLLECTED, true)
            .apply()

        applyFirebaseConsent(granted)
    }

    /**
     * Reset consent state (for debug/testing).
     */
    fun resetConsent() {
        prefs.edit().clear().apply()
    }

    private fun applyFirebaseConsent(granted: Boolean) {
        val analytics = FirebaseAnalytics.getInstance(context)
        val consentMap = mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to granted.toConsentStatus(),
            FirebaseAnalytics.ConsentType.AD_STORAGE to granted.toConsentStatus(),
            FirebaseAnalytics.ConsentType.AD_USER_DATA to granted.toConsentStatus(),
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to granted.toConsentStatus()
        )
        analytics.setConsent(consentMap)
    }

    private fun Boolean.toConsentStatus(): FirebaseAnalytics.ConsentStatus =
        if (this) FirebaseAnalytics.ConsentStatus.GRANTED
        else FirebaseAnalytics.ConsentStatus.DENIED

    /**
     * Detect if device is likely in the EEA/UK using:
     * 1. SIM card country (most reliable — set by carrier)
     * 2. Network country (fallback — based on cell tower)
     * 3. Device locale (last resort — user can change it)
     */
    internal fun isDeviceInEea(): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        val countryCode = tm?.simCountryIso?.lowercase()
            ?: tm?.networkCountryIso?.lowercase()
            ?: java.util.Locale.getDefault().country.lowercase()

        return countryCode in EEA_COUNTRY_CODES
    }

    companion object {
        private const val PREFS_NAME = "consent_prefs"
        private const val KEY_CONSENT_GRANTED = "consent_granted"
        private const val KEY_CONSENT_COLLECTED = "consent_collected"

        // EU 27 + EEA (Iceland, Liechtenstein, Norway) + UK
        internal val EEA_COUNTRY_CODES = setOf(
            "at", "be", "bg", "hr", "cy", "cz", "dk", "ee", "fi", "fr",
            "de", "gr", "hu", "ie", "it", "lv", "lt", "lu", "mt", "nl",
            "pl", "pt", "ro", "sk", "si", "es", "se", // EU 27
            "is", "li", "no", // EEA non-EU
            "gb" // UK (post-Brexit, still requires GDPR-like consent)
        )
    }
}
