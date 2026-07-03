package com.antonchuraev.homesearchchecklist.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.TaskStackBuilder
import com.antonchuraev.aichecklists.R
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.random.Random

/**
 * FCM entry point for the re-engagement messaging flow.
 *
 * Design decisions (see brief constraints + [PushAnalytics]):
 *  - [onNewToken] persists the token immediately via [PushTokenRepository].
 *  - The campaign sends **data-only** payloads, so [onMessageReceived] is always invoked (even in
 *    the background) and we build the notification by hand with an explicit channel id.
 *  - Delivery/open/dismiss are instrumented: [AnalyticsEvents.Push.RECEIVED] fires here the moment
 *    FCM hands us the payload; [AnalyticsEvents.Push.OPENED] fires in [MainActivity] on tap (extras
 *    threaded through the content PendingIntent); [AnalyticsEvents.Push.DISMISSED] fires in
 *    [PushDismissReceiver] via the delete PendingIntent.
 *  - Channel routing is data-driven: `data.channel == "reminders"` -> the high-importance reminders
 *    channel (functional), anything else -> the promotions channel (promo, independently mutable).
 *
 * Dependencies are pulled from the running Koin container (this Service has no constructor for DI),
 * mirroring `ReminderReceiver`. If Koin is not yet started (cold process spun up purely to deliver a
 * push) analytics is skipped (logged) and token persistence is deferred to the next app start.
 */
internal class GistiFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val logger: AppLogger?
        get() = GlobalContext.getOrNull()?.getOrNull()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val koin = GlobalContext.getOrNull()
        if (koin == null) {
            // Koin not started yet — token will be re-fetched and registered on next app start.
            return
        }
        val repository: PushTokenRepository? = koin.getOrNull()
        if (repository == null) {
            koin.getOrNull<AppLogger>()?.warning(TAG, "onNewToken: no PushTokenRepository bound")
            return
        }
        serviceScope.launch {
            repository.registerToken(token)
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val payload = PushPayload(
            title = data[KEY_TITLE] ?: message.notification?.title ?: getString(R.string.app_name),
            body = data[KEY_BODY] ?: message.notification?.body,
            channelToken = data[KEY_CHANNEL],
            pushType = data[KEY_PUSH_TYPE],
            audienceClass = data[KEY_AUDIENCE_CLASS],
            campaignId = data[KEY_CAMPAIGN_ID],
            checklistId = data[KEY_CHECKLIST_ID]?.toLongOrNull(),
            abExperiment = data[KEY_AB_EXPERIMENT],
            abArm = data[KEY_AB_ARM],
        )

        // Delivery denominator: emit RECEIVED the moment FCM hands us the payload — even if the
        // payload is malformed (no body), it still arrived, so it counts against "sent".
        emitReceived(payload)

        if (data.containsKey(KEY_CHECKLIST_ID) && payload.checklistId == null) {
            logger?.warning(
                TAG,
                "onMessageReceived: unparseable checklist_id='${data[KEY_CHECKLIST_ID]}' — no deep-link (campaign=${payload.campaignId})",
            )
        }
        if (payload.body.isNullOrBlank()) {
            logger?.warning(TAG, "onMessageReceived: no body — skipping display (campaign=${payload.campaignId})")
            return
        }

        showNotification(payload)
    }

    private fun emitReceived(payload: PushPayload) {
        val tracker = PushAnalytics.tracker()
        if (tracker == null) {
            logger?.warning(TAG, "received: no AnalyticsTracker (cold process) — campaign=${payload.campaignId}")
            return
        }
        runCatching {
            tracker.event(AnalyticsEvents.Push.RECEIVED, payload.analyticsParams())
        }.onFailure { e ->
            logger?.error(TAG, "received: failed to emit push_received — ${e.message}", e)
        }
    }

    private fun showNotification(payload: PushPayload) {
        // Stable id per campaign so a re-send updates the existing notification instead of stacking.
        val notificationId = payload.campaignId?.hashCode() ?: Random.nextInt()
        val isReminders = payload.channelToken == PushNotificationChannels.DATA_CHANNEL_REMINDERS
        val channelId = PushNotificationChannels.channelIdFor(payload.channelToken)

        val contentPendingIntent = buildContentPendingIntent(payload, notificationId)
        val deletePendingIntent = buildDeletePendingIntent(payload, notificationId)

        val body = payload.body.orEmpty().take(400)
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_checkbox_checked)
            .setContentTitle(payload.title.take(200))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (isReminders) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(deletePendingIntent)
        if (isReminders) {
            builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
        }

        getSystemService(NotificationManager::class.java).notify(notificationId, builder.build())
    }

    /**
     * Content intent -> MainActivity (Activity target — required; a receiver trampoline that starts
     * an Activity is banned on Android 12+). If [PushPayload.checklistId] is present we reuse the
     * SAME deep-link contract as local reminders ([ReminderReceiver.ACTION_OPEN_CHECKLIST] +
     * [ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID]) via [TaskStackBuilder], so MainActivity's
     * existing [MainActivity.extractDeepLinkChecklistId] navigates. Push metadata is always attached
     * so [AnalyticsEvents.Push.OPENED] can be emitted on tap regardless of deep-link.
     */
    private fun buildContentPendingIntent(payload: PushPayload, notificationId: Int): PendingIntent {
        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            payload.checklistId?.let { id ->
                action = ReminderReceiver.ACTION_OPEN_CHECKLIST
                putExtra(ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID, id)
            }
            PushAnalytics.writeExtras(
                intent = this,
                campaignId = payload.campaignId,
                pushType = payload.pushType,
                channel = payload.channelToken,
                audienceClass = payload.audienceClass,
                abExperiment = payload.abExperiment,
                abArm = payload.abArm,
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (payload.checklistId != null) {
            TaskStackBuilder.create(this).run {
                addNextIntentWithParentStack(contentIntent)
                getPendingIntent(notificationId, flags)!!
            }
        } else {
            PendingIntent.getActivity(this, notificationId, contentIntent, flags)
        }
    }

    /** Delete intent -> [PushDismissReceiver] (broadcast — no Activity start, so a receiver is fine). */
    private fun buildDeletePendingIntent(payload: PushPayload, notificationId: Int): PendingIntent {
        val deleteIntent = Intent(this, PushDismissReceiver::class.java).apply {
            PushAnalytics.writeExtras(
                intent = this,
                campaignId = payload.campaignId,
                pushType = payload.pushType,
                channel = payload.channelToken,
                audienceClass = payload.audienceClass,
                abExperiment = payload.abExperiment,
                abArm = payload.abArm,
            )
        }
        return PendingIntent.getBroadcast(
            this,
            notificationId,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Parsed data-only payload for one push. */
    private data class PushPayload(
        val title: String,
        val body: String?,
        val channelToken: String?,
        val pushType: String?,
        val audienceClass: String?,
        val campaignId: String?,
        val checklistId: Long?,
        val abExperiment: String?,
        val abArm: String?,
    ) {
        fun analyticsParams(): Map<String, Any> = PushAnalytics.params(
            campaignId = campaignId,
            pushType = pushType,
            channel = channelToken,
            audienceClass = audienceClass,
            abExperiment = abExperiment,
            abArm = abArm,
        )
    }

    private companion object {
        const val TAG = "PushFcm"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_CHANNEL = "channel"
        const val KEY_PUSH_TYPE = "push_type"
        const val KEY_AUDIENCE_CLASS = "audience_class"
        const val KEY_CAMPAIGN_ID = "campaign_id"
        const val KEY_CHECKLIST_ID = "checklist_id"
        const val KEY_AB_EXPERIMENT = "push_ab_experiment"
        const val KEY_AB_ARM = "push_ab_arm"
    }
}
