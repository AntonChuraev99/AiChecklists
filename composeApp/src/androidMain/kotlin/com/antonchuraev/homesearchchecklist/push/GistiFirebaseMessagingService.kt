package com.antonchuraev.homesearchchecklist.push

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.antonchuraev.aichecklists.R
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.PushTokenRepository
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
 * Design decisions (see brief constraints):
 *  - [onNewToken] persists the token immediately via [PushTokenRepository].
 *  - [onMessageReceived] always builds the notification by hand with an explicit channel id —
 *    the re-engagement campaign sends **data-only** payloads, so this method is always
 *    invoked (even in the background) and the system never auto-shows anything for us.
 *
 * Dependencies are pulled from the running Koin container (this Service has no constructor for
 * DI), mirroring how `ReminderReceiver` resolves its repository. If Koin is not yet started
 * (cold process spun up purely to deliver a push) we fall back to a direct notification build
 * for display and skip token persistence — the next app start re-registers the token anyway.
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

        // Re-engagement campaign sends data-only payloads. Read title/body from the data map,
        // falling back to the notification block if a payload ever includes one.
        val data = message.data
        val title = data[KEY_TITLE]
            ?: message.notification?.title
            ?: getString(R.string.app_name)
        val body = data[KEY_BODY] ?: message.notification?.body
        if (body.isNullOrBlank()) {
            logger?.warning(TAG, "onMessageReceived: no body in data or notification — skipping")
            return
        }

        showNotification(title = title, body = body)
    }

    private fun showNotification(title: String, body: String) {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            Random.nextInt(),
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, PushNotificationChannels.PROMOTIONS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_checkbox_checked)
            .setContentTitle(title.take(200))
            .setContentText(body.take(400))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(400)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(Random.nextInt(), notification)
    }

    private companion object {
        const val TAG = "PushFcm"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
    }
}
