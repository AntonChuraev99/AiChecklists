package com.antonchuraev.homesearchchecklist.consent

import android.content.Context
import android.content.SharedPreferences
import android.telephony.TelephonyManager
import android.util.Log
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

/**
 * Manages GDPR/ePrivacy consent for EEA/UK users.
 *
 * Uses Firebase Analytics setConsent{} API directly (no UMP SDK needed,
 * since the app doesn't use AdMob).
 *
 * Consent state is persisted in a dedicated SharedPreferences file
 * (separate from the app's main DataStore) because consent must be
 * applied synchronously before Firebase initialization.
 *
 * Region detection reads [TelephonyManager], whose getters are synchronous binder calls into the
 * telephony service (`ISub.getDefaultSubIdAsUser`) — on the main thread they ANR on a slow radio
 * stack. Detection therefore runs only on [Dispatchers.IO], once per PROCESS (memoized in memory):
 *
 * 1. [applyConsentDefaults] (synchronous, main thread) decides from the region cached in prefs by
 *    the previous process; no cache yet → DENIED.
 * 2. [startRegionResolution] re-detects the region on every process start (while consent is not
 *    collected), refreshes the prefs cache and corrects the consent — including a downgrade to
 *    DENIED when a device that was cached as non-EEA is now in the EEA/UK (moved, new SIM).
 *
 * A cached GRANTED for such a "moved" user lives only until step 2 finishes. That is no weaker
 * than before: Firebase persists the last consent across launches and auto-initializes from its
 * ContentProvider before Application.onCreate, so the old per-start synchronous check never held
 * DENIED "from the very first moment" for this user either.
 *
 * @param scope long-lived scope for the per-process background region resolution.
 */
class ConsentManager(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val regionMutex = Mutex()

    /** Serializes an explicit choice ([setConsent]) against the post-resolution correction. */
    private val consentLock = Any()

    /** Region detected by THIS process; never seeded from the prefs cache. */
    @Volatile
    private var processIsEea: Boolean? = null

    val isConsentCollected: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_COLLECTED, false)

    val isConsentGranted: Boolean
        get() = prefs.getBoolean(KEY_CONSENT_GRANTED, false)

    /**
     * Check if consent dialog is required.
     * Only EEA/UK users need explicit consent under GDPR/ePrivacy.
     *
     * Suspends until THIS process has a fresh region (never answers from the prefs cache);
     * telephony is queried off the main thread.
     */
    suspend fun isConsentRequired(): Boolean {
        if (isConsentCollected) return false
        return resolveIsEea()
    }

    /**
     * Apply consent defaults to Firebase Analytics.
     * Must be called BEFORE any Firebase Analytics events are logged. Never touches telephony.
     *
     * - If consent was previously collected → apply saved state
     * - If not collected AND a region is cached by an earlier process → deny in EEA/UK, grant
     *   elsewhere (corrected by [startRegionResolution] once the fresh region is known)
     * - If not collected AND nothing cached → deny
     */
    fun applyConsentDefaults() {
        applyFirebaseConsent(
            startupConsentGranted(isConsentCollected, isConsentGranted, cachedRegionIsEea()),
        )
    }

    /**
     * Re-detect the region for this process on [scope] and correct the startup consent
     * ([consentAfterFreshRegion]): fresh EEA/UK → DENIED (downgrades a stale cached grant),
     * fresh non-EEA → GRANTED. No-op once the user has made a choice.
     *
     * Call AFTER Koin is started, so a telephony failure reaches [AppLogger] (and Crashlytics)
     * instead of logcat only.
     */
    fun startRegionResolution() {
        if (isConsentCollected) return
        scope.launch {
            val isEea = resolveIsEea()
            synchronized(consentLock) {
                consentAfterFreshRegion(isEea, isConsentCollected)?.let(::applyFirebaseConsent)
            }
        }
    }

    /**
     * Save user's consent choice and apply to Firebase.
     */
    fun setConsent(granted: Boolean) {
        synchronized(consentLock) {
            prefs.edit()
                .putBoolean(KEY_CONSENT_GRANTED, granted)
                .putBoolean(KEY_CONSENT_COLLECTED, true)
                .apply()

            applyFirebaseConsent(granted)
        }
    }

    /**
     * Reset consent state (for debug/testing). Also drops the cached region; the region detected
     * by this process stays valid.
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
     * Region cached in prefs by an earlier process — used ONLY for the synchronous startup
     * decision; null when it has never been resolved.
     */
    private fun cachedRegionIsEea(): Boolean? =
        if (prefs.contains(KEY_IS_EEA)) prefs.getBoolean(KEY_IS_EEA, true) else null

    /**
     * Resolve whether the device is in the EEA/UK, fresh for this process, on [Dispatchers.IO].
     * One telephony query per process: concurrent callers (startup resolution + the consent dialog
     * check) share it under [regionMutex]. The result refreshes the prefs cache for the next start.
     */
    internal suspend fun resolveIsEea(): Boolean = withContext(Dispatchers.IO) {
        regionMutex.withLock {
            processIsEea ?: detectIsEea().also { isEea ->
                processIsEea = isEea
                prefs.edit().putBoolean(KEY_IS_EEA, isEea).apply()
            }
        }
    }

    /**
     * Detect if device is likely in the EEA/UK using:
     * 1. SIM card country (most reliable — set by carrier)
     * 2. Network country (fallback — based on cell tower)
     * 3. Device locale (last resort — user can change it)
     *
     * Blocking binder IPC — call only off the main thread.
     */
    private fun detectIsEea(): Boolean {
        val tm = readTelephony("TelephonyManager") {
            context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        }
        val sim = tm?.let { readTelephony("simCountryIso") { it.simCountryIso } }
        val network = tm?.let { readTelephony("networkCountryIso") { it.networkCountryIso } }
        val locale = java.util.Locale.getDefault().country.orEmpty()

        return isEeaCountry(simCountryIso = sim, networkCountryIso = network, localeCountry = locale)
    }

    private inline fun <T> readTelephony(source: String, read: () -> T): T? =
        runCatching(read).onFailure { logTelephonyFailure(source, it) }.getOrNull()

    /**
     * A telephony getter failed; resolution falls back to the next signal (ultimately the locale).
     * UnsupportedOperationException is the documented outcome on a device without the telephony
     * feature (a Wi-Fi tablet), so it is a warning; anything else (SecurityException, a dead
     * binder) is unexpected and is recorded as an error.
     */
    private fun logTelephonyFailure(source: String, throwable: Throwable) {
        val message = "$source unavailable, falling back for EEA detection: ${throwable.message}"
        val logger = GlobalContext.getOrNull()?.getOrNull<AppLogger>()
        when {
            logger == null -> Log.w(TAG, message, throwable)
            throwable is UnsupportedOperationException -> logger.warning(TAG, message)
            else -> logger.error(TAG, message, throwable)
        }
    }

    companion object {
        private const val TAG = "Consent"
        private const val PREFS_NAME = "consent_prefs"
        private const val KEY_CONSENT_GRANTED = "consent_granted"
        private const val KEY_CONSENT_COLLECTED = "consent_collected"
        private const val KEY_IS_EEA = "is_eea"

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
