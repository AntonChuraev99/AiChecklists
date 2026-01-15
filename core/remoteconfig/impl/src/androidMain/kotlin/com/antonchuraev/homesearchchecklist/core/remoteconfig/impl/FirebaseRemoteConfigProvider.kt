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
        RemoteConfigKeys.FEATURE_PAYWALL_ENABLED to RemoteConfigDefaults.FEATURE_PAYWALL_ENABLED,
        RemoteConfigKeys.MAX_CHECKLIST_ITEMS to RemoteConfigDefaults.MAX_CHECKLIST_ITEMS,
        RemoteConfigKeys.AI_ANALYSIS_MAX_INPUT_LENGTH to RemoteConfigDefaults.AI_ANALYSIS_MAX_INPUT_LENGTH,
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
            remoteConfig.getLong(key)
        } catch (e: Exception) {
            defaultValue
        }
    }
}
