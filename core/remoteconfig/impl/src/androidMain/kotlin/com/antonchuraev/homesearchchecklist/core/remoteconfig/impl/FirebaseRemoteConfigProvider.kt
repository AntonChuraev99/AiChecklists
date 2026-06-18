package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

/**
 * Android implementation using Firebase Remote Config.
 */
class FirebaseRemoteConfigProvider : RemoteConfigProvider {

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
    )

    override suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
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
}
