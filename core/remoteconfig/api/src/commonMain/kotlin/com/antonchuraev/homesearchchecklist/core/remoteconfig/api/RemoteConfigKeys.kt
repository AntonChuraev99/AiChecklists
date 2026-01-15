package com.antonchuraev.homesearchchecklist.core.remoteconfig.api

/**
 * Centralized remote config keys for type safety.
 */
object RemoteConfigKeys {
    const val FEATURE_AI_ANALYSIS_ENABLED = "feature_ai_analysis_enabled"
    const val FEATURE_PAYWALL_ENABLED = "feature_paywall_enabled"
    const val MAX_CHECKLIST_ITEMS = "max_checklist_items"
    const val AI_ANALYSIS_MAX_INPUT_LENGTH = "ai_analysis_max_input_length"
}

/**
 * Default values for remote config keys.
 */
object RemoteConfigDefaults {
    const val FEATURE_AI_ANALYSIS_ENABLED = true
    const val FEATURE_PAYWALL_ENABLED = false
    const val MAX_CHECKLIST_ITEMS = 100L
    const val AI_ANALYSIS_MAX_INPUT_LENGTH = 10000L
}
