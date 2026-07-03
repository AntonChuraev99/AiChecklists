package com.antonchuraev.homesearchchecklist.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.antonchuraev.aichecklists.R
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver

/**
 * Notification channels owned by the FCM / re-engagement layer.
 *
 * Channels MUST be created in `Application.onCreate()` (not lazily in
 * `onMessageReceived`) — otherwise the very first push that arrives before a channel
 * exists is silently dropped by the system on Android 8+.
 *
 * The reminders channel (`checklist_reminders`) is created separately by
 * `ReminderReceiver.createNotificationChannel` and is intentionally not duplicated here.
 */
internal object PushNotificationChannels {

    /**
     * Channel for re-engagement / product-update pushes delivered via FCM.
     * IMPORTANCE_DEFAULT: shows in the tray with sound, no heads-up intrusion (these are
     * marketing/update messages, not time-critical reminders).
     */
    const val PROMOTIONS_CHANNEL_ID = "promotions"

    /**
     * Wire tokens the server sets in the FCM `data.channel` field. Deliberately short and
     * decoupled from the OS channel ids: `"reminders"` (functional pushes — routed into the
     * high-importance [ReminderReceiver.CHANNEL_ID]) and `"promo"` (everything promotional —
     * routed into [PROMOTIONS_CHANNEL_ID], which the user can mute independently).
     */
    const val DATA_CHANNEL_REMINDERS = "reminders"
    const val DATA_CHANNEL_PROMO = "promo"

    /**
     * Map a `data.channel` wire token to the OS [NotificationChannel] id. Functional pushes
     * (`"reminders"`) land in the reminders channel; anything else — including an absent/unknown
     * token — falls back to [PROMOTIONS_CHANNEL_ID]. This is the ONLY place that decides which
     * channel a push posts to, guaranteeing promo pushes can never leak into the reminders channel.
     */
    fun channelIdFor(dataChannel: String?): String =
        if (dataChannel == DATA_CHANNEL_REMINDERS) ReminderReceiver.CHANNEL_ID else PROMOTIONS_CHANNEL_ID

    /**
     * Create all FCM-owned channels. Idempotent — calling `createNotificationChannel`
     * with an existing id only updates mutable fields (name/description), never resets
     * user-changed importance. Safe to call on every app start.
     */
    fun createAll(context: Context) {
        // NotificationChannel exists only on API 26+ (minSdk is 24). Referencing it on
        // API 24/25 throws NoClassDefFoundError at startup — pre-O devices post pushes with
        // no channel, so there is nothing to create.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val promotions = NotificationChannel(
            PROMOTIONS_CHANNEL_ID,
            context.getString(R.string.promotions_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.promotions_notification_channel_desc)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(promotions)
    }
}
