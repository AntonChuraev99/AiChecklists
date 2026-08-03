package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/**
 * Android implementation using Firebase Remote Config.
 */
class FirebaseRemoteConfigProvider(
    private val logger: AppLogger,
) : RemoteConfigProvider {

    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        // Debug builds fetch on every call (0s) so RC values and A/B experiment
        // assignments are visible immediately during testing; production keeps the
        // 1h throttle. Detected via the debuggable manifest flag (no DI needed here).
        val isDebuggable = try {
            val ctx = com.google.firebase.FirebaseApp.getInstance().applicationContext
            (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        } catch (e: Exception) {
            false
        }
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(if (isDebuggable) 0L else 3600L)
            // Dead-network safety ceiling for a SINGLE fetch. Onboarding awaits fetchAndActivate()
            // reactively (no external timeout in SplashViewModel), so this is the only bound on how
            // long the first launch can wait: an unreachable network fails after 15s instead of the
            // 60s SDK default — short enough not to hang splash, long enough for a slow real-device
            // cold start to still receive its A/B experiment assignment.
            .setFetchTimeoutInSeconds(15L)
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(getDefaultsMap())
    }

    private fun getDefaultsMap(): Map<String, Any> = mapOf(
        RemoteConfigKeys.FEATURE_AI_ANALYSIS_ENABLED to RemoteConfigDefaults.FEATURE_AI_ANALYSIS_ENABLED,
        RemoteConfigKeys.MAX_CHECKLIST_ITEMS to RemoteConfigDefaults.MAX_CHECKLIST_ITEMS,
        RemoteConfigKeys.AI_ANALYSIS_MAX_INPUT_LENGTH to RemoteConfigDefaults.AI_ANALYSIS_MAX_INPUT_LENGTH,
        RemoteConfigKeys.MIN_APP_VERSION to RemoteConfigDefaults.MIN_APP_VERSION,
        RemoteConfigKeys.MAINTENANCE_MODE to RemoteConfigDefaults.MAINTENANCE_MODE,
        RemoteConfigKeys.AI_FUNCTIONS_BASE_URL to RemoteConfigDefaults.AI_FUNCTIONS_BASE_URL,
        RemoteConfigKeys.AI_DAILY_LIMIT_FREE to RemoteConfigDefaults.AI_DAILY_LIMIT_FREE,
        RemoteConfigKeys.AI_DAILY_LIMIT_PREMIUM to RemoteConfigDefaults.AI_DAILY_LIMIT_PREMIUM,
        RemoteConfigKeys.MAX_CHECKLISTS_FREE to RemoteConfigDefaults.MAX_CHECKLISTS_FREE,
        RemoteConfigKeys.MAX_FILLS_FREE to RemoteConfigDefaults.MAX_FILLS_FREE,
        RemoteConfigKeys.MAX_RECURRING_REMINDERS_FREE to RemoteConfigDefaults.MAX_RECURRING_REMINDERS_FREE,
        RemoteConfigKeys.ONBOARDING to RemoteConfigDefaults.ONBOARDING,
        RemoteConfigKeys.PAYWALL_VARIANT to RemoteConfigDefaults.PAYWALL_VARIANT,
        RemoteConfigKeys.PAYWALL_DEFAULT_PLAN to RemoteConfigDefaults.PAYWALL_DEFAULT_PLAN,
        RemoteConfigKeys.PAYWALL_CONFIG to RemoteConfigDefaults.PAYWALL_CONFIG,
        RemoteConfigKeys.ACTIVATION_BUNDLE_V1 to RemoteConfigDefaults.ACTIVATION_BUNDLE_V1,
        RemoteConfigKeys.PUSH_TIMING_ARM to RemoteConfigDefaults.PUSH_TIMING_ARM,
        RemoteConfigKeys.NAV_V2_ARM to RemoteConfigDefaults.NAV_V2_ARM,
    )

    @Volatile
    private var lastError: Throwable? = null

    override fun lastFetchError(): Throwable? = lastError

    override suspend fun fetchAndActivate(): Boolean {
        // Warm up Firebase Installations FIRST so the RC fetch has a valid installation auth token.
        // ~26% of empty-onboarding first launches (prod, 2026-07-07) failed with "Firebase
        // Installations failed to get installation auth token for fetch" — FIS registration races
        // the very first cold-start fetch. Awaiting the id here forces registration to settle before
        // the fetch below. Non-fatal by design (see warmUpInstallations): on any timeout/error we
        // still proceed to the fetch, so this can never make first launch worse than today.
        warmUpInstallations()
        return try {
            remoteConfig.fetchAndActivate().await().also { lastError = null }
        } catch (e: Exception) {
            // Do NOT swallow: capture the real cause so onboarding diagnostics can report it.
            // A Play-signed build rejected by App Check / API-key SHA restrictions fails here
            // with a 403-class FirebaseRemoteConfigServerException; without this the onboarding
            // A/B silently collapses to the empty client default — which since 2026-07-28
            // resolves to the "ai_welcome" arm (was "slides"); see GetOnboardingVariantUseCase.
            lastError = e
            false
        }
    }

    /**
     * Awaits the Firebase Installations id to force installation registration BEFORE the RC fetch,
     * eliminating the FIS-token race that empties the onboarding RC value on cold start.
     *
     * Strictly best-effort:
     *  - Bounded by [FIS_WARMUP_TIMEOUT_MS] via [withTimeoutOrNull] so a broken / absent-GMS device
     *    (custom ROM, degraded Play Services) can never hang splash — on timeout the id is null and
     *    we proceed to the fetch anyway.
     *  - Any non-cancellation exception is logged as a warning and swallowed (proceed to fetch).
     *  - [CancellationException] is re-thrown to keep structured concurrency intact.
     *
     * FIS caches the id after the first success, so this only costs latency on the genuine cold
     * start — exactly where the race lives.
     */
    private suspend fun warmUpInstallations() {
        try {
            val id = withTimeoutOrNull(FIS_WARMUP_TIMEOUT_MS) {
                FirebaseInstallations.getInstance().id.await()
            }
            if (id == null) {
                logger.warning(
                    TAG,
                    "FIS warm-up timed out after ${FIS_WARMUP_TIMEOUT_MS}ms — proceeding to RC fetch without a confirmed installation token",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warning(
                TAG,
                "FIS warm-up failed (${e::class.simpleName}: ${e.message}) — proceeding to RC fetch anyway",
            )
        }
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return try {
            remoteConfig.getBoolean(key)
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun getString(key: String, defaultValue: String): String {
        return try {
            val value = remoteConfig.getString(key)
            value.ifEmpty { defaultValue }
        } catch (e: Exception) {
            defaultValue
        }
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return try {
            val value = remoteConfig.getLong(key)
            // Firebase returns 0 if key not found or defaults not yet applied
            // Use provided defaultValue in this case
            if (value == 0L) defaultValue else value
        } catch (e: Exception) {
            defaultValue
        }
    }

    companion object {
        private const val TAG = "RemoteConfig"

        // Upper bound on the FIS warm-up. On a healthy device the cached/registered id returns in
        // well under a second; this ceiling only guards a broken/absent-GMS device from stalling
        // splash. On timeout we proceed to the fetch anyway (best-effort, never fatal).
        private const val FIS_WARMUP_TIMEOUT_MS = 5_000L
    }
}
