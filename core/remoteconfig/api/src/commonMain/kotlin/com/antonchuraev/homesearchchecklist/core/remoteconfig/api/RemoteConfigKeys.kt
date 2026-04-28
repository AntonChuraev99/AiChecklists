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

    // Templates
    const val TEMPLATES_JSON = "templates_json"

    // Onboarding type: "interactive" or "default"
    const val ONBOARDING = "onboarding"

    // Update Feed
    const val UPDATE_FEED_JSON = "update_feed_json"

    // Paywall A/B variant: "timeline_v1" | "features_v1" | "compare_v1"
    const val PAYWALL_VARIANT = "paywall_variant"
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

    // Templates - default empty, populated from Remote Config
    const val TEMPLATES_JSON = ""

    // Onboarding type: "interactive" or "default"
    const val ONBOARDING = "interactive"

    // Paywall A/B variant default
    const val PAYWALL_VARIANT = "features_v1"

    // Update Feed — 17 chronological feature posts (v1.6–v1.13, Jan–Apr 2026) + 4 de-duped Google
    // Play release notes (v1.6, v1.7, v1.10, v1.13). Posts use main-version format (X.Y). Grouped
    // by version in the UI (Google Play release-notes style). 8 unique post-versions → 8 release
    // cards. De-dup rules applied: each storeDescription line appears exactly once across all
    // versions. v1.8, v1.9, v1.11, v1.12 have posts but no storeDescription (all lines were
    // covered by earlier versions or by feature posts in the same group).
    // Fallback default when Firebase Remote Config is unavailable.
    // After merging, update Firebase RC Console with the same JSON.
    //
    // Action buttons: reserved for non-obvious functionality only (currently: home-screen widget
    // setup via `gisti://widget_instruction`). Posts describing features that users reach through
    // the normal navigation surface (home, analyze, templates, create) intentionally carry no
    // actions — those CTAs were pure noise, duplicating the bottom nav/drawer.
    const val UPDATE_FEED_JSON = """{"posts":[{"id":"widget_v1","version":"1.6","title":"Add the home screen widget","description":"Pin any checklist to your home screen and tick items without opening the app.","publishedAtMillis":1769644800000,"iconName":"Widgets","actions":[{"label":"Show me how","deepLink":"gisti://widget_instruction"}]},{"id":"inline_input_v1","version":"1.7","title":"Inline item input","description":"Add checklist items without leaving the detail view — just type and tap plus.","publishedAtMillis":1769731200000,"iconName":"Bolt"},{"id":"ai_language_v1","version":"1.8","title":"AI speaks your language","description":"Analyze content in any language — Gisti responds in the same tongue.","publishedAtMillis":1769990400000,"iconName":"AutoAwesome"},{"id":"fill_target_v1","version":"1.8","title":"Choose exactly what to fill","description":"Select which items the AI should target when filling a checklist.","publishedAtMillis":1771372800000,"iconName":"Tune"},{"id":"csat_v1","version":"1.8","title":"Tell us how we are doing","description":"Your feedback shapes what we build next — take 30 seconds to share your thoughts.","publishedAtMillis":1771459200000,"iconName":"Campaign"},{"id":"reminders_v1","version":"1.9","title":"Never miss a thing","description":"Set a one-time reminder on any checklist so nothing slips through the cracks.","publishedAtMillis":1771718400000,"iconName":"Notifications"},{"id":"quick_add_v1","version":"1.9","title":"Faster item entry","description":"A dedicated input bar at the bottom of every checklist makes adding items instant.","publishedAtMillis":1772064000000,"iconName":"PlaylistAddCheck"},{"id":"drag_drop_v1","version":"1.10","title":"Reorder and clean up","description":"Long-press any item to drag it exactly where you want it.","publishedAtMillis":1772150400000,"iconName":"DragIndicator"},{"id":"recurring_v1","version":"1.11","title":"Repeat reminders on autopilot","description":"Daily, weekly, monthly — set it once and Gisti keeps nudging you on schedule.","publishedAtMillis":1773100800000,"iconName":"Replay"},{"id":"swipe_delete_v1","version":"1.11","title":"Swipe to delete","description":"Swipe left on any item to remove it in one smooth gesture.","publishedAtMillis":1773100800000,"iconName":"Bolt"},{"id":"interactive_onboarding_v1","version":"1.11","title":"Guided first checklist","description":"New users now get a hands-on walkthrough that builds a real checklist step by step.","publishedAtMillis":1773187200000,"iconName":"Celebration"},{"id":"templates_v1","version":"1.11","title":"47 ready-made templates","description":"Pick from travel, work, health, and more — a solid starting point for any task.","publishedAtMillis":1773360000000,"iconName":"Star"},{"id":"discover_more_v1","version":"1.11","title":"Discover more setup tips","description":"Learn how to add the widget and set reminders to get the most out of Gisti.","publishedAtMillis":1773360000000,"iconName":"AutoAwesome","actions":[{"label":"Add widget","deepLink":"gisti://widget_instruction"}]},{"id":"auto_delete_v1","version":"1.12","title":"Tidy checklists automatically","description":"Turn on auto-delete so finished items disappear the moment you check them off.","publishedAtMillis":1774137600000,"iconName":"PlaylistAddCheck"},{"id":"bulk_delete_v1","version":"1.12","title":"Clear completed in one tap","description":"Overflow menu now wipes every checked item at once — great after a long session.","publishedAtMillis":1774137600000,"iconName":"Bolt"},{"id":"inline_edit_v1","version":"1.13","title":"Rename items inline","description":"Tap the pencil next to any item in edit mode to rename it. Long text now wraps across several lines.","publishedAtMillis":1776470400000,"iconName":"PlaylistAddCheck"},{"id":"theme_v1","version":"1.13","title":"Dark theme and Material You","description":"Switch between light, dark, or system in Settings. On Android 12+ turn on Dynamic Color to match your wallpaper.","publishedAtMillis":1776470400000,"iconName":"AutoAwesome"}],"releaseNotes":{"1.6":{"notes":"⚡ Improved Performance\nThe app now runs faster and smoother.\n\n🐛 Bug Fixes\nSquashed minor bugs for a more stable experience.","publishedAtMillis":1769644800000},"1.7":{"notes":"⚡ Smoother List Animations\nYour checklists now scroll with polished animations for a more fluid productivity experience.\n\n🎯 Subscription Sync\nYour premium AI features now sync across all your devices automatically.","publishedAtMillis":1770163200000},"1.10":{"notes":"📌 Organize Your Checklist\nTap ⋮ to separate done and to-do items — completed tasks move to the bottom so you focus on what is left.","publishedAtMillis":1772150400000},"1.13":{"notes":"⚡ Smoother Navigation\nFixed rare empty screens when switching quickly between menu items.","publishedAtMillis":1776470400000}}}"""
}
