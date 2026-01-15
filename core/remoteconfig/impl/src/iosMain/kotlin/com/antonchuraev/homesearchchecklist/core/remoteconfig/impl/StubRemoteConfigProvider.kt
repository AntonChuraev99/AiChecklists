package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigDefaults
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigKeys
import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider

/**
 * iOS stub implementation. Returns default values.
 *
 * To implement Firebase for iOS:
 * 1. Add Firebase iOS SDK via CocoaPods
 * 2. Initialize Firebase in AppDelegate.swift
 * 3. Replace this stub with real implementation
 */
class StubRemoteConfigProvider : RemoteConfigProvider {

    private val defaults: Map<String, Any> = mapOf(
        RemoteConfigKeys.FEATURE_AI_ANALYSIS_ENABLED to RemoteConfigDefaults.FEATURE_AI_ANALYSIS_ENABLED,
        RemoteConfigKeys.FEATURE_PAYWALL_ENABLED to RemoteConfigDefaults.FEATURE_PAYWALL_ENABLED,
        RemoteConfigKeys.MAX_CHECKLIST_ITEMS to RemoteConfigDefaults.MAX_CHECKLIST_ITEMS,
        RemoteConfigKeys.AI_ANALYSIS_MAX_INPUT_LENGTH to RemoteConfigDefaults.AI_ANALYSIS_MAX_INPUT_LENGTH,
        RemoteConfigKeys.MIN_APP_VERSION to RemoteConfigDefaults.MIN_APP_VERSION,
        RemoteConfigKeys.MAINTENANCE_MODE to RemoteConfigDefaults.MAINTENANCE_MODE,
    )

    override suspend fun fetchAndActivate(): Boolean = true

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return (defaults[key] as? Boolean) ?: defaultValue
    }

    override fun getString(key: String, defaultValue: String): String {
        return (defaults[key] as? String) ?: defaultValue
    }

    override fun getLong(key: String, defaultValue: Long): Long {
        return (defaults[key] as? Long) ?: defaultValue
    }
}
