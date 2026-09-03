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
        /**
         * A checklist was created. ALWAYS carries [AnalyticsParams.SOURCE] = a
         * [ChecklistSource] wire value — never a free-text literal. The source split is the
         * ONLY way to tell which creation path a cohort actually used; an un-sourced
         * `checklist_created` is indistinguishable across 5 different product flows.
         *
         * EVERY path that persists a new checklist MUST emit this (see [ChecklistSource] for
         * the enumerated paths). A silent path is a hole in the funnel that looks like
         * "nobody creates checklists" rather than "we forgot to measure it" — exactly the
         * artifact that made the web funnel unreadable until 2026-07-15.
         *
         * Deliberate exceptions (their own events, NOT this one):
         *  - the splash-time seed checklist -> [Onboarding.FIRST_CHECKLIST_AUTO_CREATED]
         *    (not a user act — counting it here would inflate the create funnel by one per install).
         */
        const val CREATED = "checklist_created"

        /**
         * The free checklist ceiling refused, or announced itself to, a create attempt.
         *
         * The counterpart of [Reminder.RECURRING_LIMIT_HIT] for the project quota, and it exists for
         * the same reason: the ceiling was only observable downstream as
         * `paywall_opened{source=checklist_limit}`, which carries no
         * [AnalyticsParams.FORM_VARIANT] — so the v2-only limit banner and the both-arms Save gate
         * were one undifferentiated blob, and the redesigned surface was invisible exactly where the
         * rest of the create funnel had just been made visible.
         *
         * ALWAYS carries [AnalyticsParams.SOURCE] (a `LIMIT_SOURCE_*` value below) and
         * [AnalyticsParams.FORM_VARIANT]. This is a UI-affordance event, NOT a create — it must
         * never be counted in the create funnel.
         */
        const val LIMIT_HIT = "checklist_limit_hit"

        // ── Values of [AnalyticsParams.SOURCE] on [LIMIT_HIT] ─────────────────
        /** The in-form limit banner's upgrade CTA was tapped. v2 project form only. */
        const val LIMIT_SOURCE_CREATE_BANNER = "create_banner"

        /** Save was tapped while the ceiling was already reached, so the create never ran. */
        const val LIMIT_SOURCE_CREATE_SAVE = "create_save"

        const val DELETED = "checklist_deleted"
        const val FILL_CREATED = "fill_created"
        const val DEFAULT_FILL_UPDATED = "default_fill_updated"
        const val FILL_COMPLETED = "fill_completed"
        const val SHARED = "share_checklist"
    }

    /**
     * Bundled template library (Templates -> TemplatePreview -> Use).
     *
     * The library doubled (47 -> 81 templates on 2026-07-22) with NO way to tell whether anyone
     * opens or uses it: the only signal was `checklist_created` with source="template", which
     * fires at the very end of the funnel and cannot separate "nobody browses templates" from
     * "people browse but abandon the preview". These two events close that gap:
     *
     *   [PREVIEWED] = a template detail actually rendered (fires once per successful load,
     *                 NOT on a load failure — a failed load is not a view)
     *   -> [USED]   = the user turned that template into a checklist
     *
     * previewed - used = the abandonment the library is actually paying for. Both carry
     * [AnalyticsParams.TEMPLATE_SLUG] so a single underperforming template is attributable,
     * and [AnalyticsParams.TEMPLATE_CATEGORY] so a whole weak category is too.
     *
     * [USED] is emitted ALONGSIDE [Checklist.CREATED] (source="template"), never instead of it:
     * the create funnel must stay complete, this pair only adds the template dimension.
     */
    /**
     * Folder lifecycle inside a checklist. Named constants instead of the string literals these
     * were emitted with until 2026-07-26: a literal drifts silently (a typo renames the event and
     * the old name simply stops arriving), and it hides the set from anyone auditing coverage.
     *
     * [DELETED] has never been ingested in prod — folders get created, renamed and flattened, but
     * not deleted. The emit is reachable (checked 2026-07-26), so this is a product fact, not a
     * hole in the instrumentation.
     */
    object Folder {
        const val CREATED = "folder_created"
        const val RENAMED = "folder_renamed"
        const val DELETED = "folder_deleted"
        const val FLATTENED = "folders_flattened"

        /** Folders turned on/off for a checklist. Carries `enabled`. */
        const val ENABLED_TOGGLED = "folders_enabled_toggled"
    }

    object Template {
        /** A template preview screen finished loading and is on screen. */
        const val PREVIEWED = "template_previewed"

        /** A template preview was converted into a real checklist. */
        const val USED = "template_used"
    }

    /**
     * SEO checklist-gallery deep-link funnel (`app.gisti-ai.com/?g=create&template={slug}`).
     *
     * The gallery (gisti-ai.com/checklists/) is the Tier-1 organic acquisition surface, so its
     * arrival -> outcome funnel must be readable on its own:
     *
     *   [DEEPLINK_OPENED]  = a gallery link actually resolved into the app (top-of-funnel;
     *                        fires BEFORE the template fetch, so it counts arrivals even when
     *                        the create then fails)
     *   -> [Checklist.CREATED] with [AnalyticsParams.SOURCE] = "gallery"   (success)
     *   -> [DEEPLINK_FAILED] with [AnalyticsParams.REASON]                 (failure)
     *
     * opened = created(gallery) + failed. A gap between them means slugs are resolving to
     * nothing (stale gallery page / Firestore drift) — invisible before these events existed.
     * Every event carries [AnalyticsParams.TEMPLATE_SLUG] plus any utm_* captured off the
     * deep-link, so organic traffic is attributable to the exact landing page.
     *
     * ⚠️ Data BEFORE 2026-07-28 does not satisfy that equation: an arrival whose create was
     * interrupted (Activity recreation) was retried by the next collector and re-emitted
     * [DEEPLINK_OPENED] each time, so the left side inflated while the right side stayed put
     * (measured: 7 opened / 1 created / 0 failed over ~1.5 unique users). `opened` is now
     * deduplicated per arrival, so only later data is directly comparable — treat any older
     * `opened` count as an upper bound, and prefer unique-user counts across the boundary.
     */
    object Gallery {
        /**
         * A gallery deep-link was parsed and handed to the create flow. Fires once per ARRIVAL —
         * not once per processing attempt: a cancelled-and-retried create is still one arrival
         * (deduplicated in `PendingGalleryDeepLink.markOpenedReported`).
         */
        const val DEEPLINK_OPENED = "gallery_deeplink_opened"

        /**
         * The deep-link arrived but no checklist was created. Param [AnalyticsParams.REASON]:
         * "not_found" (unknown/stale slug — the gallery page and Firestore have drifted apart)
         * | "error" (fetch/persist failure — carries [AnalyticsParams.ERROR_MESSAGE]).
         */
        const val DEEPLINK_FAILED = "gallery_deeplink_failed"
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

        // ── Values of [AnalyticsParams.SOURCE] on [RECURRING_LIMIT_HIT] / [REPEAT_SCHEDULE_SET] ──
        /**
         * WHICH surface hit the recurring ceiling. Three screens emit [RECURRING_LIMIT_HIT], so an
         * unqualified event answers "did it happen" and never "where" — and a discriminator built
         * on "source is absent" breaks the moment a fourth emitter appears. **Every** emit site
         * passes one of these; adding a site means adding a value, not omitting the key.
         *
         * The two detail values intentionally repeat the `navigateToPaywall(source = …)` literals of
         * the same methods, so the limit hit and the paywall it opens join on one value.
         */
        const val LIMIT_SOURCE_CREATE_PROJECT = "create_project"

        /** Repeat tab opened from the checklist-detail reminder sheet. */
        const val LIMIT_SOURCE_CHECKLIST_DETAIL = "detail_recurring_limit"

        /** The retention nudge banner on checklist detail, accepted by the user. */
        const val LIMIT_SOURCE_RECURRING_NUDGE = "recurring_nudge_limit"
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

    // ─── AI entry affordances (the DOORS into Analyze / AI-create) ───────────
    /**
     * Fired the moment a user taps a door into an AI flow, BEFORE any navigation or credit check.
     *
     * Deliberately upstream of [Analyze.STARTED]: the v2 shell shipped with no reachable route to
     * Analyze at all, and because nothing was emitted at the door the gap read as "nobody wanted
     * it" instead of "there is no button". A tap event whose funnel to `ai_analyze_started` reads
     * 100 → 0 is a broken destination; no tap event at all is undiagnosable.
     *
     * Params: [AnalyticsParams.DESTINATION] (AiEntryDestination.wire) · [AnalyticsParams.SOURCE]
     * (AiEntrySource.wire) · [AnalyticsParams.INPUT_TYPE] (AnalyzeInputKind.wire — only when
     * destination is `analyze`) · [AnalyticsParams.HAS_QUERY] / [AnalyticsParams.QUERY_LEN] (only
     * from the Templates empty-search door, which carries the user's typed words into the prompt).
     */
    object AiEntry {
        const val TAPPED = "ai_entry_tapped"
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

        // ── Reversible actions (D1: apply now, offer Undo after) ──────────────────
        // Confident add/complete no longer emit PREVIEW_SHOWN — there is no preview to show, the
        // action already ran. That funnel therefore drops in VOLUME by design; these three events
        // are where that traffic went. ACTION_AUTO_APPLIED carries action_type + routed_layer, so
        // "auto-applied → undone" is the new regret rate (the old proxy was preview_rejected).
        const val ACTION_AUTO_APPLIED = "ai_chat_action_auto_applied"
        const val ACTION_UNDONE = "ai_chat_action_undone"
        const val ACTION_MOVED = "ai_chat_action_moved"

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
        /**
         * A paywall impression — the funnel entry, the denominator of every conversion rate.
         *
         * Emitted by `PaywallViewModel.init` (NOT by a screen), because the paywall is rendered by
         * three different hosts and only the ViewModel is common to all of them:
         *  - the standalone paywall screen (`PaywallRoute`),
         *  - the onboarding paywall page (slides onboarding),
         *  - the onboarding paywall step (interactive onboarding).
         *
         * ⚠️ Until 2026-07-28 this fired from `PaywallRoute` only, while
         * [PURCHASE_BUTTON_CLICKED] fired from the shared ViewModel — so the two ends of the
         * funnel counted DIFFERENT populations and the "paywall → tap" rate read ~71% (both
         * onboarding surfaces contributed taps but no impressions). Series before and after that
         * date are not comparable: `paywall_shown` volume steps up by the onboarding surfaces.
         * Always split on [AnalyticsParams.SURFACE].
         *
         * Fires 1:1 with `paywall_opened` and carries the same params; `paywall_opened` is the
         * older name kept alive because the `PaywallsV1` A/B experiment and existing dashboards
         * are built on it. Both are emitted from ONE param map so they can never drift again.
         * For a NEW chart prefer this one.
         *
         * ⚠️ Known over-count on [SURFACE_ONBOARDING], slides-onboarding variant only: that screen
         * builds `PaywallViewModel` eagerly on page 1 (to preload products), not on the paywall
         * page, so a user who taps Skip before reaching it still emits an impression. The
         * interactive onboarding builds the ViewModel lazily at its paywall step and is exact.
         * This is inherited from `paywall_opened`, which has always behaved this way. Treat
         * `surface=onboarding` as an UPPER bound until the slides screen is made lazy.
         */
        const val SHOWN = "paywall_shown"
        const val CLOSED = "paywall_closed"

        /**
         * The subscribe CTA was tapped, before billing runs. Carries
         * [AnalyticsParams.IS_REPEAT_TAP] — filter `false` for a funnel, or the numerator
         * double-counts users who re-tapped a slow billing sheet.
         */
        const val PURCHASE_BUTTON_CLICKED = "purchase_button_clicked"

        // ── Values of [AnalyticsParams.SURFACE] ───────────────────────────────
        /** Standalone paywall screen reached via `navigateToPaywall` (a limit/gate was hit). */
        const val SURFACE_PAYWALL_SCREEN = "paywall_screen"
        /** Paywall page/step embedded in onboarding — shown to everyone, no gate was hit. */
        const val SURFACE_ONBOARDING = "onboarding"
        /** Web "install the mobile app" stand-in — no products, can never produce a purchase. */
        const val SURFACE_WEB_INSTALL = "web_install"

        /**
         * The [AnalyticsParams.SOURCE] tag both onboarding paywall hosts pass into
         * `PaywallViewModel`; it is what maps them to [SURFACE_ONBOARDING]. Kept here (not in the
         * onboarding module) because the paywall module must resolve the surface without
         * depending on onboarding.
         */
        const val SOURCE_ONBOARDING_TRIAL = "onboarding_trial"
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
        // Sheet closed without a terminal action. Carries [AnalyticsParams.HAD_RATING]
        // (a rating was picked before closing) + [AnalyticsParams.SOURCE] = auto | manual |
        // feedback (which entry opened the sheet), so an auto-show dismissal is separable
        // from a manual-drawer one — a naive dismissed/shown double-counts both entries.
        // Auto shows also carry [AnalyticsParams.TRIGGER_EVENT] + [AnalyticsParams.SCORE].
        const val DISMISSED = "csat_dismissed"
        const val RATING_SELECTED = "csat_rating_selected"
        const val REVIEW_TAPPED = "csat_review_tapped"
        /**
         * A review request returned (rated / dismissed / quota-exceeded / never launched — the
         * store API never says which).
         *
         * Carries [AnalyticsParams.SOURCE]:
         *  - `review_launch`   — the platform ACCEPTED the launch, closing the request its
         *    [REVIEW_TAPPED] started. **At most one per tap — this is the arm to funnel on.**
         *    It is an upper bound on real impressions, not a count of them: Play renders nothing
         *    once the review quota is spent and reports no error for it, so a quota-suppressed
         *    card lands here. No API reports whether a card appeared or a rating was left.
         *  - `not_shown`       — the request never reached the platform. Split by `not_shown_reason`
         *    = `no_host_activity` | `launch_failed` | `cancelled` | `unsupported` (web/iOS have no
         *    review API). These four are one arm only because none of them is a launch.
         *  - `repeat_callback` — the launcher called back again for a request already completed.
         *    Kept as an event rather than dropped so any duplication stays measurable; since
         *    2026-07-27 requests are one-shot ([kotlinx.coroutines.channels.Channel] CONFLATED,
         *    consumed on receipt) so this **should flatline at zero** — a non-zero count means the
         *    one-shot guarantee broke.
         *
         * History: before 2026-07-27 the arm was two-way and a never-launched request was stamped
         * `review_launch`; unfiltered, completed could EXCEED tapped (prod, 30d: 6 vs 4). Note the
         * inverse is now possible: two rapid taps conflate into one launch, so `review_launch` can
         * fall BELOW `tapped` without any review being lost.
         */
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

        /**
         * An attachment was successfully persisted onto an item.
         *
         * Exists to give [LOAD_FAILED] a denominator. Until this shipped the only attachment
         * event was the failure one, so a rising failure count was unreadable: it could mean the
         * feature broke, or simply that more people started attaching files. A rate needs both.
         *
         * Fires AFTER the repository write succeeds — never on pick, never on a rejected file
         * (too large / unreadable), so it counts attachments that actually exist.
         * Carries [AnalyticsParams.SOURCE] to separate the two entry points: "item" (attach onto
         * an existing item) vs "item_create" (staged while the item is still being typed) —
         * they have different failure modes and different quotas.
         */
        const val ADDED = "attachment_added"
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
         * confirmed / attachment). Distinct from [Checklist.CREATED] — fires at most once per user.
         *
         * Carries [AnalyticsParams.VARIANT] = the `activation_bundle_v1` value, and since the
         * 2026-07-26 fix it genuinely fires in BOTH arms.
         *
         * ⚠️ **Do not compare `variant` across the 2026-07-26 cut.** Before that date the emit was
         * unreachable for control users: it is guarded by the per-UID new-user-pending marker, and
         * that marker was set only inside the treatment branch of `applyFirstChecklistExperiment`.
         * So every historical event carries `variant="true"` regardless of the real arm — measured
         * 27 `"true"` / 0 `"false"` over the 30d before the fix, while control devices provably
         * received `activation_bundle_v1=false` in their activated RC config. Any breakdown that
         * spans the cut mixes "treatment only" with "both arms" and will read as a fake lift.
         */
        const val FIRST_AI_CHECKLIST_CREATED = "activation_first_ai_checklist_created"

        /** The one-time reminder opt-in was resolved. Param: [AnalyticsParams.OUTCOME] = "granted" | "skipped". */
        const val REMINDER_OPTIN = "activation_reminder_optin"
    }

    // ─── Settings ────────────────────────────────────────────────────────────
    object Settings {
        /**
         * User picked a language. Carries [AnalyticsParams.LANGUAGE] = BCP-47 tag
         * ("en" / "ru" / "hi") or "system", and [AnalyticsParams.SOURCE] = the surface that
         * originated it, one of {"settings", "chat_picker"} — "settings" is the app UI-language
         * picker (SettingsViewModel), "chat_picker" is the AI-chat reply-language picker
         * (ChatViewModel.OnResponseLanguageSelected). Fires on the explicit user selection only
         * (not the reactive load), so in-app language adoption — invisible before this — is
         * measurable (the flagship Hindi launch shipped with zero language events).
         */
        const val LANGUAGE_SELECTED = "language_selected"

        /**
         * User switched the navigation shell in Settings. Carries [AnalyticsParams.NAV_ARM] =
         * "control" | "v2" (the value CHOSEN) and [AnalyticsParams.SOURCE] = "settings".
         *
         * The one signal that answers "is anyone opting out of the new navigation?" — the sticky
         * `nav_arm` user property cannot, because it is written once per process and a user who
         * toggles is counted under whichever value that process happened to mirror.
         */
        const val NAV_VARIANT_SELECTED = "nav_variant_selected"
    }

    /**
     * Navigation shell events. Since 2026-08-03 the shell is a user SETTING, not an A/B arm
     * (`docs/decisions/2026-08-03-shift-from-ai-first-to-checklist-first.md`): v2 (the Todoist-style
     * 4-tab shell with the chat behind a FAB) is the default, v1 (drawer + chat dock) is opt-in.
     *
     * Every event here is segmented by the sticky [AnalyticsParams.NAV_ARM] user property, which is
     * set for BOTH shells — see [SHELL_SHOWN] for why that matters. It is now a *description of what
     * rendered*, not an experiment dimension: any dashboard that treated it as an A/B split must be
     * retired rather than reused, and a user who switches mid-session is attributed to whichever
     * value the process mirrored first ([Settings.NAV_VARIANT_SELECTED] is what records the switch).
     *
     * ⚠️ Deliberately absent: a per-swipe event for the Inbox project pager.
     * `ObservableAnalyticsTracker` re-broadcasts every `event()` on a `MutableSharedFlow`
     * (`extraBufferCapacity = 64`, `tryEmit` → drops on overflow) that `CsatManager` consumes to
     * decide when to show the survey. A high-frequency event would flood that buffer and silently
     * degrade CSAT triggering — an unrelated subsystem. [TAB_SELECTED] is user-initiated across 4
     * destinations and is safe; anything per-keystroke or per-swipe is not.
     */
    object Nav {
        /**
         * The navigation shell mounted for the first time this process.
         *
         * Fires in BOTH arms — that is the entire point: this is the arm-exposure DENOMINATOR, so
         * every downstream rate (activation, creates, chat opens) has a comparable base. The
         * cautionary precedent is [Activation.FIRST_AI_CHECKLIST_CREATED], whose emit was reachable in
         * one arm only and therefore produced a fake lift for a month.
         *
         * [AnalyticsParams.VARIANT] takes TWO values: `"control"` and `"v2"`.
         *
         * It took a third, `"unassigned"`, while the shell was a Remote Config experiment: those users
         * rendered the control shell as a fail-safe without being in the experiment, and analyses had
         * to filter them out. Since 2026-08-04 the shell is a Settings choice, the resolver reads no
         * Remote Config, and every user resolves to a concrete arm — so `"unassigned"` is no longer
         * emitted. **A series spanning that date must treat its disappearance as a definition change,
         * not as the population going to zero.**
         */
        const val SHELL_SHOWN = "nav_shell_shown"

        /**
         * A bottom-nav / rail / drawer destination was tapped in the v2 shell. v2-only by
         * construction (the control shell has no tab bar). Param: [AnalyticsParams.TAB].
         */
        const val TAB_SELECTED = "nav_tab_selected"

        /**
         * The AI chat was opened from a v2 chrome affordance. v2-only by construction.
         *
         * This is the v2-side substitute for the dock-scoped `ai_chat_opened(source="dock")`,
         * which cannot fire in v2 because the dock is gone. Do NOT compare the arms on dock-scoped
         * chat events — compare this plus `screen_view: chat` against the control dock events.
         *
         * [AnalyticsParams.SOURCE] distinguishes the affordances, which are NOT interchangeable:
         * - `"fab"` — the shell's floating action button on a tab screen.
         * - `"detail_toolbar"` — the top-bar action on a project detail screen, where the shell FAB
         *   is hidden. Without this value the busiest screen in the app would contribute chat opens
         *   with no attribution at all.
         * - `"home_chip"` — one of the six prompt chips on the Projects tab (Create with AI / Photo /
         *   PDF / Link / Remind / Plan day). In control these same chips live inside the dock, so this
         *   is the value to compare against the control arm's dock chip taps.
         */
        const val CHAT_FAB_TAPPED = "nav_chat_fab_tapped"

        /**
         * The manual "+" FAB was tapped — the v2 arm's create-a-task entry point. v2-only by
         * construction, and deliberately a SEPARATE event from [CHAT_FAB_TAPPED]: the two FABs sit
         * side by side and answer the experiment's second question — when both an AI and a manual
         * create affordance are one tap away, which one do people actually reach for. Folding them
         * into one event with a `source` param would erase exactly that comparison.
         *
         * [AnalyticsParams.SOURCE] now carries TWO values, and the split is by window size, not by
         * user behaviour — read them together or the series breaks at the release that introduced
         * the second one:
         * - `"fab"` — the manual "+" in the navigation rail header (Medium) and the extended FAB in
         *   the permanent drawer (Expanded). This was the ONLY value until the inline row shipped,
         *   and on Compact it is no longer reachable at all: the v2 Compact shell dropped the FAB
         *   stack when the AI button moved into the bottom bar.
         * - `"inline_row"` — the inline "+ Add task" row: the last item of the Inbox list, and the
         *   pinned row under the pager on the Calendar tab. Compact only.
         *
         * Versioning: `"inline_row"` cannot appear on any build with versionCode <= 78 (1.18.7) —
         * that is the last version cut before the row existed. To compare create intent across
         * releases, sum both values; splitting on `source` alone shows a phantom collapse of `"fab"`
         * at the boundary, because the phone traffic moved to the new value rather than disappearing.
         *
         * NOTE this counts INTENT, not creation: the dock it opens can be dismissed without adding
         * anything. Join against [Inbox.QUICK_ADDED] for the completion rate.
         */
        const val CREATE_FAB_TAPPED = "nav_create_fab_tapped"
    }

    /**
     * The v2 Inbox — the quick-capture tab that replaces Home in the treatment arm. Every event
     * here is v2-only by construction (the Inbox does not exist in control).
     */
    object Inbox {
        /**
         * The system Inbox checklist was auto-created for this user (fires once, on the
         * absent → present transition).
         *
         * MUST NOT be [Checklist.CREATED]: the Inbox is created for us, once per user, in the v2
         * arm only. Routing it through the normal create path would add exactly +1
         * `checklist_created` per treatment user and invalidate every creation and activation
         * comparison between the arms. Same reasoning — and the same solution — as
         * [Onboarding.FIRST_CHECKLIST_AUTO_CREATED] (see the note at its declaration).
         */
        const val SYSTEM_CREATED = "inbox_system_created"

        /**
         * A task was captured through the quick-add dock. Param: [AnalyticsParams.SOURCE] =
         * "inbox" (the Inbox page) | "project" (a project page of the pager) | "calendar" (the
         * Calendar tab's capture, which always lands in the system Inbox), so capture-into-inbox
         * and quick-add-to-project stay distinguishable — they are different user intents.
         */
        const val QUICK_ADDED = "inbox_quick_added"

        /** A task was triaged out of the Inbox into a project. */
        const val TASK_MOVED = "inbox_task_moved"

        /** A task was deleted from the triage sheet. */
        const val TASK_DELETED = "inbox_task_deleted"
    }

    // ─── Install attribution (ad vs organic acquisition) ─────────────────────
    object Attribution {
        /**
         * Fired ONCE per install, the moment the Play install referrer is read (Android only —
         * `InstallReferrerCapture`, composeApp/androidMain).
         *
         * Why an event and not only the sticky utm_ + [AnalyticsParams.GCLID] user-properties that
         * already exist: a user-property has no date. It answers "was this user ever attributed",
         * never "who arrived on THIS day" — an install from May is indistinguishable from one from
         * today, so an acquisition cohort cannot be put on a timeline at all. An event carries its
         * own timestamp, so "who came from ads on 12 Aug and what did they do next" becomes a
         * normal funnel starting at this event.
         *
         * Fires for EVERY install, paid or not: the organic ones are the denominator the paid
         * cohort is measured against.
         *
         * Params — only the keys the referrer actually carried, never a default:
         *  [AnalyticsParams.SOURCE] / [AnalyticsParams.MEDIUM] / [AnalyticsParams.CAMPAIGN] /
         *  [AnalyticsParams.TERM] / [AnalyticsParams.CONTENT] / [AnalyticsParams.GCLID], plus
         *  [AnalyticsParams.IS_PAID] which is always present.
         */
        const val INSTALL_ATTRIBUTED = "install_attributed"
    }
}

/**
 * Event-parameter keys. Same contract as [AnalyticsEvents]: the string values
 * are wire format. Reuse a key across events so a single GA4/Amplitude property
 * carries one consistent meaning (e.g. `source` everywhere = "what triggered this").
 */
object AnalyticsParams {
    const val SOURCE = "source"
    const val LANGUAGE = "language"
    const val TRIGGER_EVENT = "trigger_event"
    const val SCORE = "score"
    const val VARIANT = "variant"
    const val PAGE = "page"
    const val ERROR = "error"
    const val FORMAT = "format"
    const val INPUT_TYPE = "input_type"

    // ── ai_entry_tapped dimensions ────────────────────────────────────────────────────────────
    // WHICH AI flow the tapped door leads to: AiEntryDestination.wire = analyze | ai_create.
    // NOTE the paywall already sends a `destination` of its own ("google_play") on a different
    // event. Event properties are scoped per event in Amplitude, so the two vocabularies do not
    // collide — and reusing the key beats inventing a near-synonym that splits "where does this
    // go" across two columns.
    const val DESTINATION = "destination"

    // Whether the Templates empty-search door carried the user's typed words into the AI prompt,
    // and how many characters. Only that one door sets them: a high-intent tap (the user described
    // what they wanted and we had nothing to show) is the case worth telling apart from a cold tap.
    const val HAS_QUERY = "has_query"
    const val QUERY_LEN = "query_len"

    // AI analyze failure taxonomy on ai_analyze_failed — coarse machine reason so failures group in
    // Amplitude WITHOUT regex over the free-text [ERROR]. Values (wire): credit_gate | daily_limit |
    // input_too_long | network | timeout | server_5xx | auth_403 | user_not_ready | unknown.
    // Kept alongside [ERROR] (raw string) for the long tail. See AiFailureReason.
    const val FAILURE_REASON = "failure_reason"

    // Checklist / item
    const val CHECKLIST_ID = "checklist_id"
    const val FILL_ID = "fill_id"
    const val ITEM_COUNT = "item_count"

    /**
     * WHICH create form the event came from — a [CreateFormVariant] wire value.
     *
     * ADDITIVE to [SOURCE], never a replacement for it. The v2 "New project" form and the classic
     * create-checklist form are one mount point behind one flag, so both used to emit a
     * byte-identical `checklist_created` (`source = manual`): the redesign was unmeasurable even at
     * 100% rollout. Splitting [SOURCE] instead would have moved volume out of
     * [ChecklistSource.MANUAL] — the reference value every live create dashboard is built on — so
     * the arm arrives as its own dimension and every existing `source` breakdown keeps its meaning.
     *
     * Read at the EMIT SITE from the form's own gate, not from the `nav_arm` user-property: the
     * property is sticky per process and goes stale the moment the user flips the shell in Settings,
     * whereas this says what the user was actually looking at when the event fired. Absent on every
     * non-form creation path (chat, gallery, analyze, template) — absence means "not the create
     * form", so a form funnel filters on presence rather than joining two events.
     */
    const val FORM_VARIANT = "form_variant"

    /**
     * WHAT KIND of list the create form persisted — a [CreatedListKind] wire value.
     *
     * Without it a Weekly project (created empty by design) and an ordinary empty project are the
     * same row: `source = manual`, `item_count = 0`. Emitted for BOTH kinds, never only for the
     * interesting one — absence-as-a-value cannot be counted as a share of anything.
     *
     * ⚠️ Counting ALL weekly lists means `list_kind = weekly` **OR** `source = weekly`: the
     * "My Week" entry point in `TemplatesViewModel` reports itself through
     * [ChecklistSource.WEEKLY], while the create form stays [ChecklistSource.MANUAL] plus this key.
     * Deliberate — the two are different product acts and folding them into one value would have
     * both perturbed the `manual` series and made the entry points indistinguishable again.
     */
    const val LIST_KIND = "list_kind"

    /**
     * Slug of a PUBLIC gallery template — the Firestore doc id under `gallery_templates`, as
     * carried on the `?g=create&template={slug}` deep-link. Gallery surface only.
     *
     * ⚠️ Do NOT reuse for the bundled library — its ids live in a different key space (the JSON
     * sources under `data/checklists`) and would silently split every breakdown in two. Use
     * [BUNDLED_TEMPLATE_ID] there.
     */
    const val TEMPLATE_SLUG = "template_slug"

    /**
     * Id of a BUNDLED library template (JSON sources under `data/checklists`, e.g.
     * `5-day-paris-packing-list`). Deliberately a separate key from [TEMPLATE_SLUG]: the two
     * namespaces do not coincide, so folding them into one property would make both the gallery
     * funnel and the library breakdown unreadable while looking perfectly fine.
     */
    const val BUNDLED_TEMPLATE_ID = "bundled_template_id"

    /**
     * Category a bundled template belongs to. Carried by [AnalyticsEvents.Template] events so a
     * weak category is visible without enumerating all 81 template slugs one by one.
     */
    const val TEMPLATE_CATEGORY = "template_category"

    /**
     * Whether the user modified a bundled template before creating from it
     * ([AnalyticsEvents.Template.USED]). Separates "shipped as-is" from "used as a starting
     * point" — the same event count means two different things for the library.
     */
    const val WAS_EDITED = "was_edited"

    /**
     * Campaign attribution captured off a deep-link query string (see AnalyticsUtm).
     *
     * WEB ONLY by nature: Amplitude autocapture `attribution` stays OFF on purpose (a partial
     * autocapture config in Amplitude 2.x defaults unlisted fields to `true`, and campaign
     * autocapture starts a NEW SESSION on every campaign change — inflating session metrics and
     * breaking Android/Web parity). So we attach utm to the events that need it, explicitly,
     * instead of letting the SDK re-shape sessions.
     *
     * Scope: these keys describe two distinct paths that reuse the same property names:
     *  - CLICK campaign of an already-installed app (deep-link query string, see AnalyticsUtm).
     *  - INSTALL attribution — Play attaches an encoded `referrer` param to store links (Play drops
     *    bare utm_* on a store URL). On Android, InstallReferrerCapture (composeApp/androidMain)
     *    reads that once per install via the Play Install Referrer Library and forwards the same
     *    utm_* keys (+ [GCLID]) as user-properties, so ad-install cohorts are segmentable in
     *    Amplitude (its Android SDK has no UTM/referrer autocapture — Browser-SDK-only).
     */
    const val UTM_SOURCE = "utm_source"
    const val UTM_MEDIUM = "utm_medium"
    const val UTM_CAMPAIGN = "utm_campaign"
    const val UTM_TERM = "utm_term"
    const val UTM_CONTENT = "utm_content"

    /** Google Ads click identifier — captured only from the Play install referrer (see InstallReferrerCapture). */
    const val GCLID = "gclid"

    /**
     * Campaign identity as it travels on [AnalyticsEvents.Attribution.INSTALL_ATTRIBUTED].
     *
     * Deliberately UNPREFIXED (`medium`, not `utm_medium`): the utm_* names above are the sticky
     * user-properties, these are event params. Keeping the two namespaces apart is what makes
     * "users whose INSTALL was attributed to X" (event) distinguishable from "users currently
     * tagged X" (property) in a segment. [SOURCE] is reused as-is — same meaning as everywhere
     * else, "what this came from".
     *
     * A key is present only when the referrer really carried it; an absent one stays absent.
     */
    const val MEDIUM = "medium"
    const val CAMPAIGN = "campaign"
    const val TERM = "term"
    const val CONTENT = "content"

    /**
     * Paid-install verdict on [AnalyticsEvents.Attribution.INSTALL_ATTRIBUTED]. Always present.
     *
     * Derived from the presence of [GCLID], NOT from the medium: Google Ads auto-tagging attaches a
     * bare `gclid` and no utm at all (a `utm_medium == "cpc"` rule misses those installs entirely),
     * while Play labels its own organic store traffic `utm_medium=organic` and any hand-tagged link
     * can claim `cpc` for free.
     */
    const val IS_PAID = "is_paid"
    const val PROGRESS = "progress"
    const val COMPLETED_COUNT = "completed_count"
    const val HAD_TEXT = "had_text"

    // ── Capture dock: the due date the task was born with (AnalyticsEvents.Inbox.QUICK_ADDED) ──
    // Three ADDITIVE dimensions on the existing capture event, never a fourth event. The funnel the
    // due rail is judged by is `nav_create_fab_tapped` → `inbox_quick_added`; a new event would leave
    // that funnel counting the pre-rail behaviour forever, and this one already carries 30 days of
    // history to compare against.

    /**
     * Whether the captured task carried a due date. ALWAYS present, on both capture surfaces.
     *
     * The rail's primary metric reads straight off this as a share of the event, with no join against
     * the item table — which is the point: the "3.4% of tasks get a date" baseline this work exists to
     * move could not be reproduced in Amplitude at all, because nothing emitted it.
     */
    const val HAS_DUE_DATE = "has_due_date"

    /**
     * Whole days from TODAY to the due date in the user's own zone: 0 = today, 1 = tomorrow, 7 = next
     * week.
     *
     * ABSENT — not 0, not -1 — when there is no date. [HAS_DUE_DATE] is the presence flag, and a
     * sentinel here would silently join the "today" bucket in every average taken over this property.
     *
     * Days rather than millis because the question it answers is which PRESETS the offer set needs: a
     * histogram over five small integers is readable, one over epoch deltas is not.
     */
    const val DUE_DATE_OFFSET_DAYS = "due_date_offset_days"

    /** HOW the date was set — a [DateInputMethod] wire value. Always present. */
    const val DATE_INPUT_METHOD = "date_input_method"

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

    /**
     * The offer this paywall impression was CONFIGURED to show — the RevenueCat offering id
     * resolved from Remote Config `paywall_config.currentOffer`, falling back to
     * `PaywallRemoteConfig.DEFAULT_OFFER` (the A/B control) when RC is empty or unparsable.
     * This is the arm of the `CurrentOfferTrialVSNoTrial` experiment (trial offering vs no-trial
     * offering), carried on EVERY paywall impression.
     *
     * Present unconditionally, in BOTH arms: the value is known in `PaywallViewModel.init`, before
     * RevenueCat is contacted at all, so it does not depend on the product catalog loading. The
     * guard wraps the DATA (empty RC → control offer), never the EVENT.
     *
     * ⚠️ DELIBERATELY NOT the same name as the `current_offer` USER-property set after a
     * successful catalog load. They mean different things and are populated on different
     * conditions:
     *  - `current_offer` (user-property) = the offering RevenueCat actually DELIVERED. Written only
     *    inside `loadProducts().onSuccess`, so with most catalog loads failing in prod it was
     *    absent for the large majority of `paywall_opened` events — which left the experiment
     *    verdict resting on a small, self-selected sample.
     *  - `configured_offer` (this event-property) = what we ASKED for. Always present.
     * Reusing one name for both would repeat the `push_ab_arm` defect, where the same key lives as
     * a user- and an event-property with different meanings and Amplitude silently segments an arm
     * against itself. Keeping both lets a dashboard cross "configured" against "delivered" and see
     * the delivery gap instead of hiding it.
     *
     * Absent on `SURFACE_WEB_INSTALL` impressions on purpose: the browser has no RevenueCat, so no
     * offer is configured there and such an impression can never convert.
     */
    const val CONFIGURED_OFFER = "configured_offer"

    /**
     * WHICH paywall UI this event came from — see the `SURFACE_*` values on
     * [AnalyticsEvents.Paywall]. Distinct from [SOURCE], which says WHY the paywall was opened
     * (`checklist_limit`, `chat_insufficient_credits`, …).
     *
     * The paywall is rendered by more than one host (the standalone paywall screen AND the
     * onboarding paywall step), so a funnel that does not split on `surface` mixes two
     * populations with very different intent. Present on every event of the purchase funnel.
     *
     * Reused by `ai_chat_opened` for the same reason with a different vocabulary: the SCREEN the
     * chat dock was opened over (`inbox` / `agenda` / `projects` / `overview`, `none` in the
     * control arm and on non-tab routes — see `ChatSurface.wireValue`). One param, two value sets,
     * disjoint by event name; a second key would split "which surface" across two columns.
     */
    const val SURFACE = "surface"

    /**
     * `true` when the subscribe CTA was tapped while a purchase started by an EARLIER tap was
     * still running. Such a tap is a duplicate of the intent, not a new one — funnels on
     * [AnalyticsEvents.Paywall.PURCHASE_BUTTON_CLICKED] should filter `is_repeat_tap = false`.
     *
     * Marked rather than dropped: a silently swallowed event makes duplicate taps unmeasurable,
     * and the duplicate rate is itself a UX signal (a slow billing sheet makes users re-tap).
     */
    const val IS_REPEAT_TAP = "is_repeat_tap"
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
    const val SIZE_BYTES = "size_bytes"           // attachment payload size on [Attachment.ADDED]

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
     * Server assigns the arm for promo pushes via the Remote Config SERVER template
     * (the `assign_model_arm` mechanism); client reads it from the push payload and also
     * carries a client-side arm for timing experiments. Live from release -> managed in the
     * Firebase RC console (percent split), never hardcoded.
     *
     * ⚠️ EVENT-SCOPED ONLY. The pair is what makes it unambiguous — the arm alone is
     * meaningless, since "control" (copy) and "behavioral" (timing) share this key. Never
     * mirror it into a user-property: a user-property has no [PUSH_AB_EXPERIMENT] companion,
     * so it silently reads as the server copy-arm for every consumer. That exact bug shipped
     * (2026-07-27 healthcheck): the client wrote the TIMING arm here as a user-property, so
     * `gp:push_ab_arm` was "behavioral" for 190/190 users and every push-copy A/B segmentation
     * compared an arm against itself with nothing in the output to show it. The timing arm's
     * user-property is [PUSH_TIMING_ARM].
     */
    const val PUSH_AB_EXPERIMENT = "push_ab_experiment"
    const val PUSH_AB_ARM = "push_ab_arm"

    /**
     * Sticky USER-property: the retention-push TIMING arm ("behavioral" | "fixed"), mirroring
     * the value of Remote Config key `push_timing_arm` this install resolved (same name on
     * purpose — same source, same value domain, so `gp:push_timing_arm` needs no glossary).
     *
     * A DIFFERENT experiment from the server-assigned push COPY arm, which arrives per-send as
     * the event-property [PUSH_AB_ARM] ("control" | "a" | "b") from `push_promotions.py` and is
     * never a user-property. Segmenting the copy A/B by a user-property is always wrong.
     */
    const val PUSH_TIMING_ARM = "push_timing_arm"

    // ─── Navigation A/B experiment (object AnalyticsEvents.Nav) ──────────────
    /**
     * Navigation arm, "control" | "v2". Doubles as the sticky user-property KEY (same string),
     * exactly like [AI_MODEL_VARIANT] and [PUSH_AB_ARM] — this is the ONLY reliable segmentation
     * key for the nav experiment.
     *
     * Why not the usual `rc_activated=True` filter: [RC_ACTIVATED] is a param on
     * `onboarding_rc_resolved`, which fires once per install on the not-yet-onboarded branch of
     * splash. The nav-experiment population is overwhelmingly EXISTING users, who never fire that
     * event, so filtering on it would empty the dataset.
     *
     * Set in both arms, but NEVER for a user Remote Config has not assigned yet — those carry no
     * `nav_arm` at all and are excluded from the analysis instead of being miscounted as control.
     *
     * Always a String, never a Boolean: Firebase stringifies user properties while Amplitude
     * preserves native types, so a boolean reads as "true" in GA4 and `true` in Amplitude and a
     * dashboard filtering on the wrong type silently returns zero rows.
     */
    const val NAV_ARM = "nav_arm"

    /** Which v2 destination was selected on [AnalyticsEvents.Nav.TAB_SELECTED]. Wire values are `V2Destination`'s constants. */
    const val TAB = "tab"
}

/**
 * The enumerated creation paths reported as [AnalyticsParams.SOURCE] on
 * [AnalyticsEvents.Checklist.CREATED].
 *
 * Why a type and not a literal: the wire values used to be free-text strings duplicated at
 * each call site, so a path could be added with NO source at all (or a typo'd one) and nothing
 * failed — the funnel just silently lost it. Most creation paths were unmeasured this way, the
 * flagship chat dock among them, which is why the web funnel read as zero creates. A closed set
 * makes "which paths exist" answerable by reading this enum, and `when` blocks over it exhaustive.
 *
 * The enum only closes one direction (value -> wire). Nothing makes a NEW call site emit, so the
 * reverse — every persisting path has a value — is a review question, not a compiler one: the
 * first version of this enum shipped while three paths were still silent.
 *
 * CONTRACT — [wire] is the analytics wire format (see [AnalyticsEvents] header). "ai" and
 * "manual" predate this enum and MUST keep their exact spelling: renaming either splits the
 * historical series in GA4/Amplitude. Adding a value is safe; changing one is not.
 *
 * Adding a creation path? Add the value here AND emit [AnalyticsEvents.Checklist.CREATED] at
 * the site that persists the checklist — an enum value with no emit site is a lie.
 */
enum class ChecklistSource(val wire: String) {
    /** Create-checklist screen — the user typed the items. */
    MANUAL("manual"),

    /** Analyze flow (Photo/PDF/Text/Link/Voice -> checklist), incl. the editable preview. */
    AI("ai"),

    /** SEO gallery deep-link (`?g=create&template={slug}`) — created AS-IS, no AI credit. */
    GALLERY("gallery"),

    /** AI Chat `create_checklist` tool call — the flagship interaction layer. */
    CHAT("chat"),

    /** AI Chat `create_checklist_from_attachment` — a file dropped into the chat. */
    ATTACHMENT("attachment"),

    /** Bundled template picked in-app (Templates list or its preview). Not the SEO gallery. */
    TEMPLATE("template"),

    /** Recurring weekly checklist spawned from its parent — a Premium feature. */
    WEEKLY("weekly"),
}

/**
 * Which create form produced an event, reported as [AnalyticsParams.FORM_VARIANT].
 *
 * A type rather than two literals for the same reason as [ChecklistSource]: the two call sites that
 * emit it are the only thing standing between "the redesign shipped" and "the redesign is
 * measurable", and a typo in either is silent — it just reads as a third arm nobody can explain.
 *
 * CONTRACT — [wire] values are frozen. [V2] pairs with the `nav_arm` user-property value `"v2"`;
 * [CLASSIC] pairs with `nav_arm = "control"` (experiment vocabulary, kept as-is on that key so the
 * historical nav series stays whole). The mismatch is deliberate: this key describes a FORM, and
 * "control" only means something while an experiment is running.
 */
enum class CreateFormVariant(val wire: String) {
    /** The redesigned "New project" form (v2 nav shell). */
    V2("v2"),

    /** The original create-checklist form, still reachable by switching the shell in Settings. */
    CLASSIC("classic"),
}

/**
 * What kind of list a create form persisted, reported as [AnalyticsParams.LIST_KIND].
 *
 * Mirrors the domain's `ChecklistViewMode`, which lives in `feature:checklist` and therefore cannot
 * be referenced from here — so the wire values are restated, and adding a view mode means adding a
 * value here too or the new mode silently reports as [STANDARD].
 */
enum class CreatedListKind(val wire: String) {
    /** An ordinary project: the user's own items, possibly none. */
    STANDARD("standard"),

    /** Weekly-mode project — created empty by definition, filled per weekday. */
    WEEKLY("weekly"),
}

/**
 * Campaign parameters carried on a deep-link query string.
 *
 * The [KEYS] whitelist is load-bearing, not cosmetic: it bounds what a crafted URL can inject
 * into an event. Without it any `?foo=bar` on a deep-link would become an event param and could
 * blow GA4's 25-params-per-event ceiling, dropping the params we actually need. Values are
 * trimmed and capped at [MAX_VALUE_LEN] (GA4's per-value limit) for the same reason.
 */
object AnalyticsUtm {

    /** The 5 standard utm_* keys. Wire names double as the event-param keys (no mapping layer). */
    val KEYS: List<String> = listOf(
        AnalyticsParams.UTM_SOURCE,
        AnalyticsParams.UTM_MEDIUM,
        AnalyticsParams.UTM_CAMPAIGN,
        AnalyticsParams.UTM_TERM,
        AnalyticsParams.UTM_CONTENT,
    )

    /** GA4 truncates event-param values beyond 100 chars — cap here so what we send is what lands. */
    const val MAX_VALUE_LEN = 100

    /**
     * Picks the whitelisted utm_* keys out of a deep-link query. Platform-agnostic: callers pass
     * their own lookup (wasmJs parses `window.location.search`; Android uses
     * `Uri.getQueryParameter`), so the whitelist + sanitizing live in ONE place for both.
     *
     * @param lookup returns the raw value for a query key, or null when absent.
     * @return only the present, non-blank keys — empty map when the link carries no campaign.
     */
    fun from(lookup: (String) -> String?): Map<String, String> =
        KEYS.mapNotNull { key ->
            lookup(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it.take(MAX_VALUE_LEN) }
        }.toMap()
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

    // ─── v2 navigation only ──────────────────────────────────────────────────
    const val INBOX = "inbox"
    const val OVERVIEW = "overview"

    /**
     * The v2 Projects tab.
     *
     * This name did NOT exist while the tab rendered `MainScreen`: reporting [MAIN] kept the
     * historical main-screen series continuous and arm-comparable, and a second name would have
     * split it at the experiment start. That reasoning expired on 2026-08-03 — the tab is now a
     * different screen (a flat list, `AppNavRoute.Projects`) and [MAIN] is the classic layout's home
     * screen. Filing both under one name would make "which of the two did the user actually see"
     * unanswerable.
     *
     * ⚠️ The `main` series therefore CHANGES MEANING from the release that ships this: before it was
     * "home screen OR v2 projects tab", after it is "home screen on the classic layout only". Any
     * before/after read across that boundary must add the two names together.
     */
    const val PROJECTS = "projects"
}
