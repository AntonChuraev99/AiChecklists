package com.antonchuraev.homesearchchecklist.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.antonchuraev.aichecklists.R

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
     * Create all FCM-owned channels. Idempotent — calling `createNotificationChannel`
     * with an existing id only updates mutable fields (name/description), never resets
     * user-changed importance. Safe to call on every app start.
     */
    fun createAll(context: Context) {
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
