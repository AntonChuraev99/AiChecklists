package com.antonchuraev.homesearchchecklist.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import org.koin.core.context.GlobalContext

/**
 * Shared push-interaction plumbing for the FCM re-engagement flow.
 *
 * The push lifecycle is emitted from three call sites that must agree on ONE wire format:
 *  - [GistiFirebaseMessagingService] posts the notification, emits [AnalyticsEvents.Push.RECEIVED],
 *    and threads campaign metadata into the content PendingIntent (opened) + delete PendingIntent
 *    (dismissed) via [writeExtras].
 *  - `MainActivity` reads [isPushIntent]/[paramsFromIntent] and emits [AnalyticsEvents.Push.OPENED]
 *    on tap. Opened is emitted in the Activity (not a receiver) because a notification content
 *    intent MUST target an Activity — a BroadcastReceiver/Service trampoline that then starts an
 *    Activity is banned since Android 12, so a receiver would be blocked from opening the app.
 *  - [PushDismissReceiver] emits [AnalyticsEvents.Push.DISMISSED] on swipe-away. A delete intent
 *    only fires analytics (never starts an Activity), so a receiver is its correct, race-free home.
 *
 * Every helper is null-safe against a cold process that delivered a push before Koin started:
 * [tracker] returns null -> the caller logs and no-ops instead of crashing.
 */
internal object PushAnalytics {

    // Namespaced so they never collide with the reminder deep-link extras carried on the same intent
    // (ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID / ACTION_OPEN_CHECKLIST).
    const val EXTRA_CAMPAIGN_ID = "gisti.push.campaign_id"
    const val EXTRA_PUSH_TYPE = "gisti.push.push_type"
    const val EXTRA_CHANNEL = "gisti.push.channel"
    const val EXTRA_AUDIENCE_CLASS = "gisti.push.audience_class"
    const val EXTRA_AB_EXPERIMENT = "gisti.push.ab_experiment"
    const val EXTRA_AB_ARM = "gisti.push.ab_arm"

    /**
     * True iff this intent originated from a push-notification interaction (marker = campaign id
     * extra present). `campaign_id` is required on every payload, so it is always written.
     */
    fun isPushIntent(intent: Intent?): Boolean = intent?.hasExtra(EXTRA_CAMPAIGN_ID) == true

    /** Resolve the shared [AnalyticsTracker] from the running Koin container, or null if not started. */
    fun tracker(): AnalyticsTracker? = GlobalContext.getOrNull()?.getOrNull()

    /**
     * Assemble the shared push params. Only non-blank values are emitted so a metric never sees an
     * empty-string dimension. [abExperiment]/[abArm] are campaign-scoped EVENT params (NOT sticky
     * user-properties) — the server assigns the arm per-send, so it belongs to the campaign, not
     * to the user forever (a sticky timing-arm is Phase 4's concern).
     */
    fun params(
        campaignId: String?,
        pushType: String?,
        channel: String?,
        audienceClass: String?,
        abExperiment: String?,
        abArm: String?,
    ): Map<String, Any> = buildMap {
        pushType?.takeIf { it.isNotBlank() }?.let { put(AnalyticsParams.PUSH_TYPE, it) }
        channel?.takeIf { it.isNotBlank() }?.let { put(AnalyticsParams.CHANNEL, it) }
        audienceClass?.takeIf { it.isNotBlank() }?.let { put(AnalyticsParams.AUDIENCE_CLASS, it) }
        campaignId?.takeIf { it.isNotBlank() }?.let { put(AnalyticsParams.CAMPAIGN_ID, it) }
        abExperiment?.takeIf { it.isNotBlank() }?.let { put(AnalyticsParams.PUSH_AB_EXPERIMENT, it) }
        abArm?.takeIf { it.isNotBlank() }?.let { put(AnalyticsParams.PUSH_AB_ARM, it) }
    }

    /** Copy the campaign metadata onto an interaction PendingIntent target (content or delete). */
    fun writeExtras(
        intent: Intent,
        campaignId: String?,
        pushType: String?,
        channel: String?,
        audienceClass: String?,
        abExperiment: String?,
        abArm: String?,
    ) {
        // campaign_id doubles as the isPushIntent marker -> always present (fall back to "").
        intent.putExtra(EXTRA_CAMPAIGN_ID, campaignId ?: "")
        intent.putExtra(EXTRA_PUSH_TYPE, pushType)
        intent.putExtra(EXTRA_CHANNEL, channel)
        intent.putExtra(EXTRA_AUDIENCE_CLASS, audienceClass)
        intent.putExtra(EXTRA_AB_EXPERIMENT, abExperiment)
        intent.putExtra(EXTRA_AB_ARM, abArm)
    }

    /** Rebuild the analytics params straight from an interaction intent's extras. */
    fun paramsFromIntent(intent: Intent): Map<String, Any> = params(
        campaignId = intent.getStringExtra(EXTRA_CAMPAIGN_ID),
        pushType = intent.getStringExtra(EXTRA_PUSH_TYPE),
        channel = intent.getStringExtra(EXTRA_CHANNEL),
        audienceClass = intent.getStringExtra(EXTRA_AUDIENCE_CLASS),
        abExperiment = intent.getStringExtra(EXTRA_AB_EXPERIMENT),
        abArm = intent.getStringExtra(EXTRA_AB_ARM),
    )
}

/**
 * Emits [AnalyticsEvents.Push.DISMISSED] when the user swipes a re-engagement notification away.
 * Wired as the notification's delete intent in [GistiFirebaseMessagingService]. A delete intent
 * only reports analytics (no Activity start), so a BroadcastReceiver is the correct home — unlike
 * the content intent, which the OS forbids from trampolining through a receiver (Android 12+).
 */
internal class PushDismissReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val logger: AppLogger? = GlobalContext.getOrNull()?.getOrNull()
        val tracker = PushAnalytics.tracker()
        if (tracker == null) {
            logger?.warning(TAG, "dismissed: no AnalyticsTracker (cold process) — skipping")
            return
        }
        runCatching {
            // Out-of-session: a swipe-away delivers to this receiver, not to any Activity.
            tracker.eventOutOfSession(
                AnalyticsEvents.Push.DISMISSED,
                PushAnalytics.paramsFromIntent(intent),
            )
        }.onFailure { e ->
            logger?.error(TAG, "dismissed: failed to emit push_dismissed — ${e.message}", e)
        }
    }

    private companion object {
        const val TAG = "PushFcm"
    }
}
