package com.antonchuraev.homesearchchecklist.core.remoteconfig.api

/**
 * Centralized remote config keys for type safety.
 */
object RemoteConfigKeys {
    const val FEATURE_AI_ANALYSIS_ENABLED = "feature_ai_analysis_enabled"
    const val MAX_CHECKLIST_ITEMS = "max_checklist_items"
    const val AI_ANALYSIS_MAX_INPUT_LENGTH = "ai_analysis_max_input_length"
    const val MIN_APP_VERSION = "min_app_version"
    const val MAINTENANCE_MODE = "maintenance_mode"
    const val AI_FUNCTIONS_BASE_URL = "ai_functions_base_url"
    const val AI_DAILY_LIMIT_FREE = "ai_daily_limit_free"
    const val AI_DAILY_LIMIT_PREMIUM = "ai_daily_limit_premium"

    // Free user limits
    const val MAX_CHECKLISTS_FREE = "max_checklists_free"
    const val MAX_FILLS_FREE = "max_fills_free"
    const val MAX_RECURRING_REMINDERS_FREE = "max_recurring_reminders_free"
    const val MAX_WEEKLY_CHECKLISTS_FREE = "max_weekly_checklists_free"

    // Templates
    const val TEMPLATES_JSON = "templates_json"

    // Onboarding type: "interactive" or "default"
    const val ONBOARDING = "onboarding"

    // Paywall A/B variant: "timeline_v1" | "features_v1" | "compare_v1"
    const val PAYWALL_VARIANT = "paywall_variant"

    // Paywall default plan: "monthly" | "yearly" — controls which plan is pre-selected on open.
    // Default is "monthly" so users in low-tier countries see $1.99 instead of $20/yr.
    // Remotely switchable to "yearly" via Firebase RC for markets where yearly converts better.
    const val PAYWALL_DEFAULT_PLAN = "paywall_default_plan"
}

/**
 * Default values for remote config keys.
 */
object RemoteConfigDefaults {
    const val FEATURE_AI_ANALYSIS_ENABLED = true
    const val MAX_CHECKLIST_ITEMS = 100L
    const val AI_ANALYSIS_MAX_INPUT_LENGTH = 10000L
    const val MIN_APP_VERSION = "1.0.0"
    const val MAINTENANCE_MODE = false
    const val AI_FUNCTIONS_BASE_URL = "https://us-central1-aichecklists-40230.cloudfunctions.net"
    const val AI_DAILY_LIMIT_FREE = 10L
    const val AI_DAILY_LIMIT_PREMIUM = 300L

    // Free user limits
    const val MAX_CHECKLISTS_FREE = 4L
    const val MAX_FILLS_FREE = 5L
    const val MAX_RECURRING_REMINDERS_FREE = 1L
    const val MAX_WEEKLY_CHECKLISTS_FREE = 1L

    // Templates - default empty, populated from Remote Config
    const val TEMPLATES_JSON = ""

    // Onboarding type: "interactive" or "default"
    const val ONBOARDING = "interactive"

    // Paywall A/B variant default
    const val PAYWALL_VARIANT = "features_v1"

    // Paywall default plan — "monthly" so low-tier markets see affordable price first.
    const val PAYWALL_DEFAULT_PLAN = "monthly"
}
