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

    // ─── Push / re-engagement (FCM campaigns + notification lifecycle) ────────
    /**
     * FCM push measurement contour. Distinct from [Reminder] (which tracks LOCAL
     * AlarmManager notifications the device schedules for itself): these track the
     * server-driven push pipeline + the raw notification lifecycle, so re-engagement
     * campaigns are measurable end-to-end and provable against retention.
     *
     * Every push event carries the shared push params ([AnalyticsParams.PUSH_TYPE],
     * [AnalyticsParams.CHANNEL], [AnalyticsParams.AUDIENCE_CLASS],
     * [AnalyticsParams.CAMPAIGN_ID], [AnalyticsParams.PUSH_HOLDOUT]) so any metric can
     * be sliced by campaign / type / control-bucket without a new chart per dimension.
     *
     * Attribution model: [SENT] (server) -> [RECEIVED] (client) -> [OPENED] (client) is
     * the delivery/open funnel; open->downstream-action within a window proves real use;
     * retention split on [AnalyticsParams.PUSH_HOLDOUT] proves *incremental* lift
     * (open-vs-not measures selection, not effect — see docs/reports/push-retention-*).
     */
    object Push {
        /**
         * Emitted SERVER-SIDE (firebase-functions `send_promotions_batch` -> Amplitude
         * HTTP API) at the moment a push is dispatched. The ONLY reliable CTR/delivery
         * denominator — a client cannot count a push that never arrived. Declared here
         * so the wire name has ONE source of truth across client, server, and dashboards.
         */
        const val SENT = "push_sent"

        /** Client `onMessageReceived` (our payload is data-only -> always invoked). delivery = received / sent. */
        const val RECEIVED = "push_received"

        /** Client, on notification tap -> deep-link. open rate = opened / received. Sibling of [Reminder.NOTIFICATION_TAPPED]. */
        const val OPENED = "push_opened"

        /** Client, swipe-away via `setDeleteIntent`. Notification-fatigue proxy. */
        const val DISMISSED = "push_dismissed"

        /**
         * Client, sampled on app start: system notifications enabled + per-channel
         * importance. Android has NO channel-disable callback, so we poll on start to
         * measure opt-out drift (e.g. user muted the "Tips & Offers" channel alone).
         * Carries [AnalyticsParams.NOTIFICATIONS_ENABLED] + [AnalyticsParams.CHANNEL] +
         * [AnalyticsParams.CHANNEL_IMPORTANCE].
         */
        const val PERMISSION_STATE = "push_permission_state"
    }

    /**
     * [AnalyticsParams.PUSH_TYPE] values for LOCAL (on-device AlarmManager) retention pushes.
     *
     * These are deliberately DISJOINT from any server FCM push_type (`reengagement`/`winback`/…),
     * so the shared push funnel ([Push.RECEIVED] -> [Push.OPENED]) stays a single, sliceable series:
     * filtering `push_type IN (streak_save, overdue, digest)` isolates the local retention pushes,
     * everything else is server-driven. Wire strings — keep identical to the values enumerated in the
     * [AnalyticsParams.PUSH_TYPE] contract comment.
     */
    object LocalPushType {
        /** Recurring (daily-habit) checklist still has open items in the delivery window. Functional. */
        const val STREAK_SAVE = "streak_save"

        /** A partially-done checklist left untouched for >= 1 day. Functional. */
        const val OVERDUE = "overdue"

        /** Weekly summary of open items across all checklists. Promotional. */
        const val DIGEST = "digest"

        /**
         * D0->D1 come-back: a ONE-SHOT nudge ~20-24h after the user's FIRST checklist, keyed to
         * that checklist, with NO recurring/overdue precondition. Covers the day-1 leak point the
         * other three local pushes structurally cannot reach (see docs push-retention "D0->D1 gap").
         * Functional. Flows through the shared push funnel: [Push.RECEIVED]/[Push.OPENED] with
         * push_type = comeback + campaign_id = "local_comeback".
         */
        const val COMEBACK = "comeback"
    }

    /**
     * On-device retention MECHANICS beyond the raw push funnel: whether a local nudge was armed /
     * skipped, and the recurring-list nudge outcome. These make each new retention lever provable
     * in Amplitude on its own (scheduled vs shown vs skipped; nudge shown vs accepted vs dismissed),
     * not just visible through the shared push delivery events.
     */
    object Retention {
        /** Come-back alarm ARMED after the first checklist. Params: [AnalyticsParams.CHECKLIST_ID], [AnalyticsParams.DELAY_HOURS]. */
        const val COMEBACK_SCHEDULED = "retention_comeback_scheduled"

        /** Come-back alarm actually FIRED and deps resolved (broadcast received on a WARM process),
         *  emitted before any gate. Splits scheduled -> fired -> (shown | skipped). A large
         *  scheduled-minus-fired gap ⇒ the alarm fired cold (see Crashlytics "comeback dropped") or
         *  never fired (OEM/Doze). */
        const val COMEBACK_FIRED = "retention_comeback_fired"

        /** Come-back alarm fired but nothing shown. Param: [AnalyticsParams.REASON] = permission_off | no_checklist | already_active | frequency_cap. */
        const val COMEBACK_SKIPPED = "retention_comeback_skipped"

        /** Recurring-list nudge surfaced (offer to make a list repeat). Param: [AnalyticsParams.SOURCE] = create | detail | first_run. */
        const val RECURRING_NUDGE_SHOWN = "recurring_nudge_shown"

        /** User accepted the recurring nudge -> a repeat schedule was set. */
        const val RECURRING_NUDGE_ACCEPTED = "recurring_nudge_accepted"

        /** User dismissed the recurring nudge (feedback on every action — no silent exit). */
        const val RECURRING_NUDGE_DISMISSED = "recurring_nudge_dismissed"
    }

    /**
     * Home-screen widget lifecycle + promo funnel. Until now the ONLY widget event was
     * [Item.WIDGET_TOGGLED] (an item checked from the widget) — so widget ADOPTION and the
     * widget's RETURN contribution were both invisible. [ADDED] measures adoption; [OPENED]
     * (deep-link tap) is the widget's actual retention signal; the PROMO_* trio measures the
     * post-first-checklist promo sheet that drives installs.
     */
    object Widget {
        /** Widget bound to a checklist (first onUpdate for a new appWidgetId / config confirmed). */
        const val ADDED = "widget_added"

        /** Widget tapped -> deep-link into the checklist (the widget-driven return signal). */
        const val OPENED = "widget_opened"

        /** The "add the widget" promo sheet was shown (after the first checklist). */
        const val PROMO_SHOWN = "widget_promo_shown"

        /** User tapped "Add widget" -> system pin-widget request launched. */
        const val PROMO_ACCEPTED = "widget_promo_accepted"

        /** User dismissed the widget promo sheet (feedback on every action — no silent exit). */
        const val PROMO_DISMISSED = "widget_promo_dismissed"
    }

    // ─── AI: Analyze (Photo/PDF/Text/Link/Voice -> checklist) ─────────────────
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

        // ── Voice input (mic in the chat input row -> Cloud Function transcription) ──
        // One event per mic action so the funnel (tapped -> cancelled / transcribed / failed) is
        // measurable: until these shipped the voice feature was completely un-instrumented, so
        // "is voice popular?" was unanswerable. VOICE_TRANSCRIBED carries CHAR_LEN; the FAILED
        // event carries OUTCOME (file_missing / network_error / service_error / insufficient_credits).
        const val VOICE_STARTED = "ai_chat_voice_started"
        const val VOICE_CANCELLED = "ai_chat_voice_cancelled"
        const val VOICE_TRANSCRIBED = "ai_chat_voice_transcribed"
        const val VOICE_TRANSCRIBE_EMPTY = "ai_chat_voice_transcribe_empty"
        const val VOICE_TRANSCRIBE_FAILED = "ai_chat_voice_transcribe_failed"
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

        // Post-cancel reason picker — after the user dismisses the native purchase sheet
        // ([PURCHASE_CANCELLED]) we show a neutral one-tap reason chooser to measure WHY.
        // Fires at most once per app session (see CancelReasonSessionGate). CANCEL_REASON carries
        // [AnalyticsParams.REASON] = the tapped reason key PLUS the SAME source/product_id/sku_id/
        // plan_type params as PURCHASE_CANCELLED, so the two events join in analytics. DISMISSED
        // fires when the user taps "Not now" (feedback on every action — no silent exit).
        // Not GA4-reserved (no `purchase` / `firebase_*` prefix) -> safe on both providers.
        const val CANCEL_REASON = "paywall_cancel_reason"
        const val CANCEL_REASON_DISMISSED = "paywall_cancel_reason_dismissed"
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
        // Review flow returned (rated / dismissed / quota-exceeded — the store API never says which).
        const val REVIEW_COMPLETED = "csat_review_completed"
        const val FEEDBACK_OPENED = "feedback_opened"
        const val SUBMITTED = "csat_submitted"
        const val CHIP_TOGGLED = "csat_chip_toggled"
        const val FEEDBACK_SUBMITTED = "feedback_submitted"
    }

    // ─── Updates feed ────────────────────────────────────────────────────────
    object UpdateFeed {
        const val ACTION_CLICK = "update_feed_action_click"
    }

    // ─── Attachments (image/file thumbnail + cloud materialize) ──────────────
    object Attachment {
        /**
         * An attachment thumbnail/viewer failed to display — the intermittent "broken image"
         * placeholder the user sees. Fires for BOTH failure stages so the bug is finally
         * attributable (until this shipped a decode failure on a materialized-Ready file was
         * completely invisible — no log, no event):
         *  - [AnalyticsParams.STAGE] = "materialize" — the cloud download (or local-existence probe)
         *    produced no local bytes at all. Carries [AnalyticsParams.HAS_STORAGE_PATH] +
         *    [AnalyticsParams.ERROR_MESSAGE] (Storage/App Check/CORS reason).
         *  - [AnalyticsParams.STAGE] = "decode" — materialize said Ready (local file present) but
         *    Coil could not decode the bytes -> the classic partial/corrupt-cache case.
         * Always carries [AnalyticsParams.MIME_TYPE] when known.
         */
        const val LOAD_FAILED = "attachment_load_failed"
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
    const val TRIGGER_EVENT = "trigger_event"
    const val SCORE = "score"
    const val VARIANT = "variant"
    const val PAGE = "page"
    const val ERROR = "error"
    const val FORMAT = "format"
    const val INPUT_TYPE = "input_type"

    // AI analyze failure taxonomy on ai_analyze_failed — coarse machine reason so failures group in
    // Amplitude WITHOUT regex over the free-text [ERROR]. Values (wire): credit_gate | daily_limit |
    // input_too_long | network | timeout | server_5xx | auth_403 | user_not_ready | unknown.
    // Kept alongside [ERROR] (raw string) for the long tail. See AiFailureReason.
    const val FAILURE_REASON = "failure_reason"

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

    // AI model A/B experiment — server-assigned arm, sticky user-property + event dimension.
    // AI_MODEL_VARIANT doubles as the user-property KEY (same string) so every downstream
    // event (incl. the paywall funnel) is segmentable by arm.
    const val AI_MODEL_VARIANT = "ai_model_variant"
    const val AI_MODEL_ID = "ai_model_id"
    const val AI_FLOW = "ai_flow"

    // Paywall / purchase
    const val PRODUCT_ID = "product_id"
    const val HAS_FREE_TRIAL = "has_free_trial"
    const val ITEM_ID = "item_id"
    const val ITEM_NAME = "item_name"
    const val TRIAL_DURATION = "trial_duration"
    const val VALUE = "value"
    const val CURRENCY = "currency"
    const val TRANSACTION_ID = "transaction_id"
    // Post-cancel reason picker — the tapped reason key (CancelReason.key). Distinct from the
    // literal "reason" already used ad-hoc by products_load_empty; this constant centralizes it.
    const val REASON = "reason"

    // CSAT
    const val RATING = "rating"
    const val HAD_RATING = "had_rating"

    // Onboarding Remote Config resolution (diagnostics for A/B assignment health)
    const val RC_ACTIVATED = "rc_activated"
    const val RC_VALUE_EMPTY = "rc_value_empty"
    const val FETCH_MS = "fetch_ms"
    // Exception class + message when fetchAndActivate() fails (prod-only signing/App Check fetch failure)
    const val RC_ERROR = "rc_error"
    // How many fetchAndActivate() attempts the fast-retry loop issued (1-based total), and which
    // attempt finally succeeded (1-based index; 0 = never recovered). Together they MEASURE whether
    // the FIS warm-up + fast retry actually recovers the cold-start installation-token race — the
    // ~26% FIS slice of empty-onboarding first launches (2026-07-07).
    const val RC_ATTEMPTS = "rc_attempts"
    const val RC_RECOVERED_ON_ATTEMPT = "recovered_on_attempt"

    // Activation bundle — which hero template chip was tapped (e.g. "trip", "groceries").
    const val CHIP_KEY = "chip_key"

    // Retention come-back nudge — how far ahead the one-shot alarm was armed (diagnostics on
    // retention_comeback_scheduled; pairs with [AnalyticsEvents.Retention.COMEBACK_SKIPPED] reason).
    const val DELAY_HOURS = "delay_hours"

    // Generic error diagnostics (login_failed etc.): code = stable type/class id, message = human text.
    const val ERROR_CODE = "error_code"
    const val ERROR_MESSAGE = "error_message"

    // Attachment load diagnostics (attachment_load_failed).
    const val STAGE = "stage"                     // "materialize" (cloud download) | "decode" (Coil)
    const val HAS_STORAGE_PATH = "has_storage_path" // was a cloud copy even expected?
    const val MIME_TYPE = "mime_type"

    // ─── Push / re-engagement (object Push) ──────────────────────────────────
    // Shared across every push event so a single metric can slice by type / channel /
    // campaign / control-bucket. Values are wire format — keep server (Python) + client
    // (Kotlin) + dashboards spelling these identically.
    const val PUSH_TYPE = "push_type"        // reminder|streak_save|overdue|digest|reengagement|winback|upsell|tip|release
    const val CHANNEL = "channel"            // reminders|promo — which NotificationChannel carried it
    const val AUDIENCE_CLASS = "audience_class" // functional|promotional (functional is never Premium-suppressed)
    const val CAMPAIGN_ID = "campaign_id"    // per-send id -> each campaign independently measurable & comparable
    /**
     * Promo holdout bucket. Doubles as the user-property KEY (same string) exactly like
     * [AI_MODEL_VARIANT]: `true` = user is in the control group that receives NO
     * promotional pushes. Segment N-Day retention exposed vs holdout to prove the
     * campaign's *incremental* retention lift (the causal number, not a correlation).
     */
    const val PUSH_HOLDOUT = "push_holdout"
    const val IS_PREMIUM = "is_premium"      // entitlement at event time — promo suppressed when true
    // push_permission_state diagnostics
    const val NOTIFICATIONS_ENABLED = "notifications_enabled" // app-level toggle (areNotificationsEnabled)
    const val CHANNEL_IMPORTANCE = "channel_importance"       // per-channel importance (0 = user muted this channel)
    /**
     * Push A/B experiment dimension. Two params so several experiments can run at once
     * without colliding: [PUSH_AB_EXPERIMENT] = which experiment ("copy" | "timing" |
     * "cadence"), [PUSH_AB_ARM] = the assigned variant ("control" | "a" | "b" | …).
     * [PUSH_AB_ARM] doubles as a sticky user-property (same pattern as [AI_MODEL_VARIANT]).
     * Server assigns the arm for promo pushes via the Remote Config SERVER template
     * (the `assign_model_arm` mechanism); client reads it from the push payload and also
     * carries a client-side arm for timing experiments. Live from release -> managed in the
     * Firebase RC console (percent split), never hardcoded.
     */
    const val PUSH_AB_EXPERIMENT = "push_ab_experiment"
    const val PUSH_AB_ARM = "push_ab_arm"
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
