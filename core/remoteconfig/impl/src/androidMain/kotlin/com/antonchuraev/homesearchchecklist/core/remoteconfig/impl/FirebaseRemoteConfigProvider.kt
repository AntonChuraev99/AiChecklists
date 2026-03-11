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
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour
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
        RemoteConfigKeys.ONBOARDING_CONFIG to RemoteConfigDefaults.ONBOARDING_CONFIG,
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
