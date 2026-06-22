package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Single source of truth for analytics event names.
 *
 * Why this exists: event names used to be ~65 string literals scattered across
 * ~130 call sites. That invites typos and silent drift (one feature spells an
 * event `repeat_schedule_set`, another mistypes it, and the funnel splits in
 * two without any compile error). Centralizing the names here makes every event
 * grep-able, refactor-safe, and impossible to misspell.
 *
 * CONTRACT — these string values are the wire format sent to Firebase Analytics
 * (GA4) and Amplitude. Renaming a `const val`'s VALUE breaks historical
 * continuity in both backends (a renamed event starts a brand-new series).
 * Change the Kotlin identifier freely; change the string only with intent.
 *
 * Naming convention: `snake_case`, `<noun>_<verb-past-tense>` where practical
 * (`checklist_created`, `purchase_completed`). Keep verbs consistent
 * (`*_opened` / `*_closed`, `*_started` / `*_completed` / `*_failed`).
 *
 * GA4 reserved names: `purchase` and `screen_view` are GA4-recognized. `purchase`
 * is the ONLY event GA4 aggregates into revenue/ROAS (see [Conversion]).
 */
object AnalyticsEvents {

    // ─── Onboarding ──────────────────────────────────────────────────────────
    object Onboarding {
        const val STARTED = "onboarding_started"
        const val VM_CREATED = "onboarding_vm_created"
        const val PAGE_VIEWED = "onboarding_page_viewed"
        const val STEP_COMPLETED = "onboarding_step_completed"
        const val SKIPPED = "onboarding_skipped"
        const val COMPLETED = "onboarding_completed"
        const val FIRST_CHECKLIST_AUTO_CREATED = "first_checklist_auto_created"

        /**
         * Fired once per launch when the onboarding variant is resolved from Remote Config
         * (only while onboarding is still pending). Params: VARIANT (slides/none/interactive),
         * RC_ACTIVATED (did fetchAndActivate succeed), RC_VALUE_EMPTY (true = RC returned nothing
         * and we fell back to the client default — the smoking gun for "experiment never assigned",
         * the exact failure that silently collapsed the A/B split to 0% none in prod), FETCH_MS
         * (how long the fetch took — flags slow-network cold starts). Lets a future distribution
         * collapse surface in analytics instead of via user reports.
         */
        const val RC_RESOLVED = "onboarding_rc_resolved"
    }

    // ─── Authentication (Google Sign-In) ─────────────────────────────────────
    object Auth {
        const val LOGIN_STARTED = "login_started"   // user tapped "Sign in with Google"
        const val LOGIN_SUCCESS = "login_success"   // Firebase signInWithCredential succeeded
        // Carries ERROR_CODE (stable Credential Manager type / exception class) + ERROR_MESSAGE.
        // A Play-signed build whose SHA-1 isn't a registered OAuth client fails here.
        const val LOGIN_FAILED = "login_failed"
    }

    // ─── Checklist & fill lifecycle ──────────────────────────────────────────
    object Checklist {
        const val CREATED = "checklist_created"
        const val DELETED = "checklist_deleted"
        const val FILL_CREATED = "fill_created"
        const val DEFAULT_FILL_UPDATED = "default_fill_updated"
        const val FILL_COMPLETED = "fill_completed"
        const val SHARED = "share_checklist"
    }

    // ─── Checklist detail — items & menus ────────────────────────────────────
    object Item {
        const val ADDED_QUICK = "item_added_quick"
        const val CHECKED = "item_checked"
        const val UNCHECKED = "item_unchecked"
        const val DELETED = "item_deleted"
        const val UNDO_DELETE = "item_undo_delete"
        const val AUTO_DELETED = "item_auto_deleted"
        const val COMPLETED_ITEMS_DELETED = "completed_items_deleted"
        const val WEEKLY_ADDED = "weekly_item_added"
        const val WEEKLY_MOVED = "weekly_item_moved"
        const val WIDGET_TOGGLED = "widget_item_toggled"
    }

    object DetailUi {
        const val OVERFLOW_MENU_OPENED = "overflow_menu_opened"
        const val QUICK_ADD_OPENED = "quick_add_opened"
        const val QUICK_ADD_CANCELLED = "quick_add_cancelled"
        const val COMPLETED_SECTION_EXPANDED = "completed_section_expanded"
        const val COMPLETED_SECTION_COLLAPSED = "completed_section_collapsed"
    }

    // ─── Reminders (one-shot, per-item, recurring) ───────────────────────────
    object Reminder {
        const val SET = "reminder_set"
        const val CANCELLED = "reminder_cancelled"
        const val ITEM_SET = "item_reminder_set"
        const val ITEM_REMOVED = "item_reminder_removed"
        const val REPEAT_SCHEDULE_SET = "repeat_schedule_set"
        const val REPEAT_SCHEDULE_CANCELLED = "repeat_schedule_cancelled"
        const val RECURRING_LIMIT_HIT = "recurring_limit_hit"
        const val NOTIFICATION_TAPPED = "reminder_notification_tapped"
        const val RECURRING_FIRED = "recurring_reminder_fired"
        const val RECURRING_CHECKS_RESET = "recurring_checks_reset"
        const val RECURRING_ENDED = "recurring_reminder_ended"
        const val RECURRING_RECOVERED = "recurring_reminder_recovered"
        const val RECURRING_CANCELLED = "recurring_reminder_cancelled"
    }

    // ─── AI: Analyze (Photo/PDF/Text/Link/Voice → checklist) ─────────────────
    object Analyze {
        const val STARTED = "ai_analyze_started"
        const val COMPLETED = "ai_analyze_completed"
        const val FAILED = "ai_analyze_failed"
    }

    // ─── AI Chat (flagship interaction layer) ────────────────────────────────
    object Chat {
        const val OPENED = "ai_chat_opened"
        const val MESSAGE_SENT = "ai_chat_message_sent"
        const val RESPONSE_RECEIVED = "ai_chat_response_received"
        const val THUMB_UP = "ai_chat_thumb_up"
        const val THUMB_DOWN = "ai_chat_thumb_down"
        const val FEEDBACK = "ai_chat_feedback"
        const val PREVIEW_SHOWN = "ai_chat_preview_shown"
        const val PREVIEW_CONFIRMED = "ai_chat_preview_confirmed"
        const val PREVIEW_REJECTED = "ai_chat_preview_rejected"
    }

    // ─── Paywall & purchase funnel ───────────────────────────────────────────
    object Paywall {
        const val SHOWN = "paywall_shown"
        const val CLOSED = "paywall_closed"
        const val PURCHASE_BUTTON_CLICKED = "purchase_button_clicked"
        const val TERMS_CLICKED = "paywall_terms_clicked"
        const val PRIVACY_CLICKED = "paywall_privacy_clicked"
        const val SUPPORT_CLICKED = "paywall_support_clicked"
        const val PRODUCTS_LOAD_SUCCESS = "products_load_success"
        const val PRODUCTS_LOAD_EMPTY = "products_load_empty"
        const val PRODUCTS_LOAD_FAILED = "products_load_failed"
        const val PURCHASE_COMPLETED = "purchase_completed"
        const val PURCHASE_CANCELLED = "purchase_cancelled"
        const val PURCHASE_FAILED = "purchase_failed"
        const val RESTORE_BUTTON_CLICKED = "restore_button_clicked"
        const val RESTORE_COMPLETED = "restore_completed"
        const val RESTORE_NO_SUBSCRIPTION = "restore_no_subscription"
        const val RESTORE_FAILED = "restore_failed"
    }

    /**
     * Revenue / Google Ads conversion events. These carry money and feed GA4
     * revenue + Google Ads ROAS — touch with extra care (see [ConversionEventHelper]).
     */
    object Conversion {
        const val FREE_TRIAL_START = "free_trial_start"

        /**
         * Legacy manual purchase event. NO LONGER EMITTED as of 2026-06-12 — it duplicated
         * Firebase's auto-collected `in_app_purchase` and risked GA4 double-counting. Retained
         * here only as a reference to the historical wire name (still present in past GA4/Ads
         * data). Direct purchases now fire [PURCHASE] only. Do not re-emit without removing the
         * Firebase auto-event first.
         */
        const val IN_APP_PURCHASE = "in_app_purchase"

        /** GA4 reserved ecommerce event — the only one aggregated into revenue. */
        const val PURCHASE = "purchase"
    }

    // ─── Credits restore (post-purchase reconciliation) ──────────────────────
    object Credits {
        const val RESTORE_STARTED = "credits_restore_started"
        const val RESTORE_SUCCESS = "credits_restore_success"
        const val RESTORE_RETRY = "credits_restore_retry"
        const val RESTORE_FAILED = "credits_restore_failed"
    }

    // ─── CSAT survey + in-app review ─────────────────────────────────────────
    object Csat {
        const val SHOWN = "csat_shown"
        const val OPENED = "csat_opened"
        const val DISMISSED = "csat_dismissed"
        const val RATING_SELECTED = "csat_rating_selected"
        const val REVIEW_TAPPED = "csat_review_tapped"
        const val REVIEW_SKIPPED = "csat_review_skipped"
        const val FEEDBACK_OPENED = "feedback_opened"
    }

    // ─── Updates feed ────────────────────────────────────────────────────────
    object UpdateFeed {
        const val ACTION_CLICK = "update_feed_action_click"
    }

    /**
     * New-user activation bundle (behind RC flag `activation_bundle_v1`).
     *
     * These events ALWAYS fire (even when the flag is OFF) so the later A/B test can
     * compare both arms — except [FIRST_RUN_SHOWN], which is only meaningful when the
     * hero is actually rendered (flag ON). Every event carries [AnalyticsParams.VARIANT]
     * = the flag value ("true"/"false") so funnels are filterable by arm.
     */
    object Activation {
        /** The activation hero (prompt + chips) was shown on the empty MainScreen. Flag ON only. */
        const val FIRST_RUN_SHOWN = "activation_first_run_shown"

        /** A hero template chip was tapped. Param: [AnalyticsParams.CHIP_KEY] = chip id (e.g. "trip"). */
        const val CHIP_TAPPED = "activation_chip_tapped"

        /**
         * A new user's FIRST checklist was created through the AI path (chat create / preview-
         * confirmed / attachment). Distinct from [Checklist.CREATED] — fires at most once per user,
         * in BOTH arms, so the activation funnel can compare static-seed vs AI-first-run cohorts.
         */
        const val FIRST_AI_CHECKLIST_CREATED = "activation_first_ai_checklist_created"

        /** The one-time reminder opt-in was resolved. Param: [AnalyticsParams.OUTCOME] = "granted" | "skipped". */
        const val REMINDER_OPTIN = "activation_reminder_optin"
    }
}

/**
 * Event-parameter keys. Same contract as [AnalyticsEvents]: the string values
 * are wire format. Reuse a key across events so a single GA4/Amplitude property
 * carries one consistent meaning (e.g. `source` everywhere = "what triggered this").
 */
object AnalyticsParams {
    const val SOURCE = "source"
    const val VARIANT = "variant"
    const val PAGE = "page"
    const val ERROR = "error"
    const val FORMAT = "format"
    const val INPUT_TYPE = "input_type"

    // Checklist / item
    const val CHECKLIST_ID = "checklist_id"
    const val FILL_ID = "fill_id"
    const val ITEM_COUNT = "item_count"
    const val PROGRESS = "progress"
    const val COMPLETED_COUNT = "completed_count"
    const val HAD_TEXT = "had_text"

    // AI chat
    const val MESSAGE_ID = "message_id"
    const val ROUTED_LAYER = "routed_layer"
    const val DEEP_THINKING_ENABLED = "deep_thinking_enabled"
    const val INPUT_METHOD = "input_method"
    const val CHAR_LEN = "char_len"
    const val HAS_CONTEXT_CHECKLIST = "has_context_checklist"
    const val CREDITS_USED = "credits_used"
    const val LATENCY_MS = "latency_ms"
    const val OUTCOME = "outcome"
    const val ACTION_TYPE = "action_type"

    // Paywall / purchase
    const val PRODUCT_ID = "product_id"
    const val HAS_FREE_TRIAL = "has_free_trial"
    const val ITEM_ID = "item_id"
    const val ITEM_NAME = "item_name"
    const val TRIAL_DURATION = "trial_duration"
    const val VALUE = "value"
    const val CURRENCY = "currency"
    const val TRANSACTION_ID = "transaction_id"

    // CSAT
    const val RATING = "rating"
    const val HAD_RATING = "had_rating"

    // Onboarding Remote Config resolution (diagnostics for A/B assignment health)
    const val RC_ACTIVATED = "rc_activated"
    const val RC_VALUE_EMPTY = "rc_value_empty"
    const val FETCH_MS = "fetch_ms"
    // Exception class + message when fetchAndActivate() fails (prod-only signing/App Check fetch failure)
    const val RC_ERROR = "rc_error"

    // Activation bundle — which hero template chip was tapped (e.g. "trip", "groceries").
    const val CHIP_KEY = "chip_key"

    // Generic error diagnostics (login_failed etc.): code = stable type/class id, message = human text.
    const val ERROR_CODE = "error_code"
    const val ERROR_MESSAGE = "error_message"
}

/**
 * Screen names passed to [AnalyticsTracker.screenView]. Centralized so the set
 * of tracked screens is auditable in one place (a missing screen here usually
 * means a missing `screenView` call at that screen's composition root).
 */
object AnalyticsScreens {
    const val MAIN = "main"
    const val CHECKLIST_DETAIL = "checklist_detail"
    const val FILL_DETAIL = "fill_detail"
    const val CREATE_CHECKLIST = "create_checklist"
    const val TEMPLATES = "templates"
    const val ANALYZE = "analyze"
    const val ANALYZE_RESULT = "analyze_result"
    const val CHAT = "chat"
    const val ONBOARDING = "onboarding"
    const val INTERACTIVE_ONBOARDING = "interactive_onboarding"
    const val WELCOME_ONBOARDING = "welcome_onboarding"
    const val PAYWALL = "paywall"
    const val PAYWALL_WEB_INSTALL = "paywall_web_install"
    const val SHARE = "share"
    const val UPDATE_FEED = "update_feed"
}
