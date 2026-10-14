package com.antonchuraev.homesearchchecklist.push

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger

/**
 * Whether a notification posted to a given channel can actually reach the user's shade.
 *
 * `push_received` is emitted when the payload ARRIVES (FCM `onMessageReceived`) or when a local
 * retention push is POSTED — both happen even when the app-level toggle is off or the target
 * channel is muted, in which case `NotificationManager.notify` is a silent no-op. Attaching this
 * flag as [com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams.NOTIFICATIONS_ENABLED]
 * separates "received" from "could have been shown" without renaming the event.
 */
internal object PushDisplayability {

    /**
     * Pure decision, host-testable.
     *
     * @param appNotificationsEnabled `NotificationManagerCompat.areNotificationsEnabled()`.
     * @param sdkInt device API level; channels exist only from O (26).
     * @param channelImportance importance of the target channel, or null when the channel does not
     *   exist (on O+ a post to a missing channel is dropped by the system).
     */
    fun isShowable(appNotificationsEnabled: Boolean, sdkInt: Int, channelImportance: Int?): Boolean {
        if (!appNotificationsEnabled) return false
        if (sdkInt < Build.VERSION_CODES.O) return true
        return channelImportance != null && channelImportance != NotificationManager.IMPORTANCE_NONE
    }

    /**
     * Reads the live state for [channelId]; never throws.
     *
     * A failed read (e.g. `DeadObjectException` from NotificationManagerService) returns `null`,
     * not `false`: `false` would be counted as "user disabled notifications" and skew the funnel,
     * while an absent property is honestly "unknown". The failure is logged as a warning.
     */
    fun isShowable(context: Context, channelId: String, logger: AppLogger?): Boolean? = runCatching {
        val manager = NotificationManagerCompat.from(context)
        isShowable(
            appNotificationsEnabled = manager.areNotificationsEnabled(),
            sdkInt = Build.VERSION.SDK_INT,
            channelImportance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.getNotificationChannel(channelId)?.importance
            } else {
                null
            },
        )
    }.onFailure { e ->
        logger?.warning(
            TAG,
            "displayability read failed for channel '$channelId' — notifications_enabled omitted: " +
                "${e::class.simpleName}: ${e.message}",
        )
    }.getOrNull()

    /**
     * [params] plus [AnalyticsParams.NOTIFICATIONS_ENABLED] when [showable] is known; unchanged when
     * it is `null` (read failed), so "unknown" never masquerades as "disabled".
     */
    fun withShowable(params: Map<String, Any>, showable: Boolean?): Map<String, Any> =
        if (showable == null) params else params + (AnalyticsParams.NOTIFICATIONS_ENABLED to showable)

    private const val TAG = "Push"
}
