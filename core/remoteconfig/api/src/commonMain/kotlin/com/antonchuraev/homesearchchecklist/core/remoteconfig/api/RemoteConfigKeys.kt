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
    const val MAX_ATTACHMENTS_PER_ITEM_FREE = "max_attachments_per_item_free"

    // Onboarding type: "ai_welcome" | "interactive" | "default" (slides) | "none" (skip to main).
    // "ai_welcome" is Android-only — every other platform degrades it to "default"
    // (see GetOnboardingVariantUseCase).
    const val ONBOARDING = "onboarding"

    // Paywall A/B variant: "timeline_v1" | "features_v1" | "compare_v1"
    const val PAYWALL_VARIANT = "paywall_variant"

    // Paywall default plan: "monthly" | "yearly" — controls which plan is pre-selected on open.
    // Default is "monthly" so users in low-tier countries see $1.99 instead of $20/yr.
    // Remotely switchable to "yearly" via Firebase RC for markets where yearly converts better.
    const val PAYWALL_DEFAULT_PLAN = "paywall_default_plan"

    // Paywall config (JSON blob) — A/B-testable offer selection.
    // Shape: { "currentOffer": "<revenuecat_offering_id>" } now; extensible later.
    const val PAYWALL_CONFIG = "paywall_config"

    // First-checklist A/B variant: "auto_create" | "current".
    // "auto_create" (the default) seeds a "Your first checklist" template on first launch
    // for new users; "current" keeps the existing empty-state flow.
    const val FIRST_CHECKLIST_VARIANT = "first_checklist_variant"

    // Retention push timing A/B arm: "behavioral" | "fixed".
    //   behavioral — the local retention auto-pushes (streak-save / overdue / weekly digest) are
    //                delivered at the user's most-active hour (a 24-slot on-device activity histogram).
    //   fixed      — delivered at a fixed default window (~19:00 local).
    // Sticky per user (client reads it once, mirrors it into the sticky user-property push_timing_arm
    // — NOT push_ab_arm, which is the server's per-send push-COPY arm — and tags every retention push
    // event with push_ab_experiment="timing"). Live from release -> assigned
    // in the Firebase RC console via a percent split; NEVER hardcoded. Only affects OUR auto-pushes —
    // user-set reminders always fire at the time the user chose.
    const val PUSH_TIMING_ARM = "push_timing_arm"

    // New-user activation bundle master switch (boolean). When ON (the default):
    //   - SKIP the static first-checklist auto-seed so the user lands on the AI first-run hero,
    //   - render the activation hero (prompt + chips) on the empty MainScreen,
    //   - show the one-time reminder opt-in after the new user's first AI checklist.
    // When OFF: the EXACT pre-activation behavior (static auto-create + first_checklist_variant
    // A/B + plain EmptyState). Remotely toggleable so the whole bundle can be A/B-tested later.
    const val ACTIVATION_BUNDLE_V1 = "activation_bundle_v1"

    // Todoist-style navigation A/B arm: "control" | "v2".
    //   control — today's shell (drawer + chat dock), byte-identical behaviour.
    //   v2      — 4-tab shell (Inbox / Calendar / Projects / Overview), chat behind a FAB.
    //
    // Empty means "RC has not assigned an arm yet" and is DELIBERATELY distinguishable from
    // "control" — see RemoteConfigDefaults.NAV_V2_ARM.
    //
    // STRING, not Boolean, on purpose: FirebaseRemoteConfigProvider.getBoolean has no absent-key
    // fallback (Firebase answers `false` for an unknown key and that `false` is returned as a
    // legitimate value), so a boolean flag cannot express "not assigned". getString does apply
    // `value.ifEmpty { defaultValue }`.
    //
    // Sticky per install: NavExperimentResolver persists the first non-empty arm and never
    // re-reads RC afterwards, so a mid-session fetchAndActivate() can never swap the shell.
    // The percent split is assigned in the Firebase RC CLIENT console template, NEVER hardcoded
    // (PUSH_TIMING_ARM is the cautionary precedent: it exists in no template, so its "experiment"
    // never actually ran).
    const val NAV_V2_ARM = "nav_v2_arm"
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
    // 5 mirrors the live Remote Config value; this default only applies before the first
    // successful fetch, so a lower number here silently sold the free tier short on a cold
    // start. Keep in sync with the Console parameter (see CLAUDE.md limits table).
    const val MAX_CHECKLISTS_FREE = 5L
    const val MAX_FILLS_FREE = 5L
    const val MAX_RECURRING_REMINDERS_FREE = 10L
    const val MAX_WEEKLY_CHECKLISTS_FREE = 1L
    const val MAX_ATTACHMENTS_PER_ITEM_FREE = 3L

    // Onboarding type: "ai_welcome" | "interactive" | "default" (slides) | "none" (skip to main).
    //
    // ⚠️ MUST STAY EMPTY. This value is also pushed into the Firebase SDK's in-app defaults
    // (FirebaseRemoteConfigProvider.getDefaultsMap), so remoteConfig.getString("onboarding")
    // returns exactly what is written here whenever nothing was fetched. Empty is therefore the
    // only sentinel that lets us tell "RC returned a real assignment" apart from "fetch failed /
    // not assigned yet" — the distinction the onboarding_rc_resolved.rc_value_empty analytics
    // param is built on. Putting a real arm name here would silently pin that param to false
    // forever and blind the A/B health signal.
    //
    // WHICH ARM the empty sentinel resolves to is a separate, product-level decision and lives in
    // GetOnboardingVariantUseCase — since 2026-07-28 it is AI_WELCOME (was: slides), so a user
    // whose Remote Config never arrived still gets the flagship AI first-run.
    const val ONBOARDING = ""

    // Paywall A/B variant default
    const val PAYWALL_VARIANT = "features_v1"

    // Paywall default plan — "monthly" so low-tier markets see affordable price first.
    const val PAYWALL_DEFAULT_PLAN = "monthly"

    // Empty client default by design: empty -> resolver falls back to
    // PaywallRemoteConfig.DEFAULT_OFFER. Distinguishes "RC returned config" from "fetch failed".
    const val PAYWALL_CONFIG = ""

    // First-checklist A/B variant default: "auto_create".
    //
    // Auto-creating the starter checklist is the desired baseline product behavior, so the
    // client default is "auto_create" rather than empty: a brand-new user gets the "Your
    // first checklist" seed even on the very first cold start, before the first successful
    // Remote Config fetch (getString falls back to this value via `ifEmpty`). Remote Config
    // can still override per-cohort — set the parameter to "current" to opt a control group
    // out of auto-create. Keep this in sync with the Firebase Console parameter default.
    const val FIRST_CHECKLIST_VARIANT = "auto_create"

    // Retention push timing arm. "behavioral" = deliver at the user's most-active hour.
    //
    // ⚠️ NOT AN EXPERIMENT TODAY (verified 2026-07-15): this key exists in NEITHER RC template,
    // so every user runs "behavioral" — there is no split to read. Two things must happen before
    // it is one: (1) add the parameter to the CLIENT template — PushTimingResolver reads the
    // client namespace, so adding it to the *server* template would silently do nothing; and
    // (2) attach a condition for the split. Until then, treat any timing comparison as invalid.
    const val PUSH_TIMING_ARM = "behavioral"

    // Activation bundle ON by default — this is the desired baseline product behavior (AI
    // first-run instead of a static seed). Default-ON is fail-open BY DESIGN: a failed/slow
    // Remote Config fetch keeps the bundle ON. The read MUST NOT be wrapped in a withTimeout
    // (SplashViewModel already reactively awaits fetchAndActivate() before reading flags), so a
    // slow-network cold start can never silently flip it off. Set to false in the Console to opt
    // a control cohort back into the legacy static-auto-create flow for the A/B comparison.
    const val ACTIVATION_BUNDLE_V1 = true

    // Todoist-style navigation A/B arm.
    //
    // Empty client default is intentional, same rationale as [ONBOARDING] above: any non-empty
    // value MUST come from Remote Config so we can distinguish "RC successfully returned an arm"
    // from "fetch failed / experiment not assigned yet". Empty resolves to CONTROL inside
    // GetNavVariantUseCase but is reported as `assigned = false`, which is what stops the
    // resolver from PERSISTING the fallback.
    //
    // That distinction is load-bearing here in a way it is not for onboarding: SplashViewModel
    // only awaits fetchAndActivate() for users who have not passed onboarding, so on a cold start
    // an existing user reaches the shell with RC possibly still un-activated. Persisting the empty
    // -> CONTROL fallback would pin the entire installed base to control forever and the
    // experiment would read 100/0.
    const val NAV_V2_ARM = ""
}
