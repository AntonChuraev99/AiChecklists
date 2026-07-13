package com.antonchuraev.homesearchchecklist.notification

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.reminder_full_screen_dismiss
import aichecklists.core.designsystem.generated.resources.reminder_full_screen_label
import aichecklists.core.designsystem.generated.resources.reminder_full_screen_open
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.antonchuraev.homesearchchecklist.MainActivity
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButton
import com.antonchuraev.homesearchchecklist.desingsystem.components.AppButtonSecondary
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppDimens
import com.antonchuraev.homesearchchecklist.desingsystem.theme.AppTheme
import org.jetbrains.compose.resources.stringResource
import org.koin.android.ext.android.inject

/**
 * Alarm-style full-screen reminder shown over the lock screen when a per-item reminder with the
 * `reminderFullScreen` opt-in fires. Launched via `setFullScreenIntent` from `ReminderReceiver`;
 * the OS only actually surfaces it full-screen when the screen is locked/off, otherwise the
 * notification degrades to a heads-up.
 *
 * Category is CATEGORY_ALARM (declared in the Play full-screen-intent declaration as the app's
 * permitted core function) — NOT CATEGORY_CALL.
 */
class FullScreenReminderActivity : ComponentActivity() {

    private val logger: AppLogger by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setupLockScreenBehavior()

        val checklistId = intent.getLongExtra(EXTRA_CHECKLIST_ID, -1L)
        val checklistName = intent.getStringExtra(EXTRA_CHECKLIST_NAME).orEmpty()
        val itemText = intent.getStringExtra(EXTRA_ITEM_TEXT).orEmpty()
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        setContent {
            AppTheme(darkTheme = isSystemInDarkTheme()) {
                FullScreenReminderContent(
                    checklistName = checklistName,
                    itemText = itemText,
                    onOpen = { openChecklist(checklistId, notificationId) },
                    onDismiss = { dismiss(notificationId) }
                )
            }
        }
    }

    /**
     * Show over the keyguard and wake the screen. `setShowWhenLocked` / `setTurnScreenOn` exist on
     * API 27+; below that fall back to the (deprecated) window flags. Keep-screen-on is applied on
     * all versions so the alarm stays visible until the user acts.
     */
    private fun setupLockScreenBehavior() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        // Ask the keyguard to dismiss so the Open/Dismiss buttons are directly actionable
        // (requestDismissKeyguard is API 26+; below that the window flags above suffice).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(KeyguardManager::class.java)?.requestDismissKeyguard(this, null)
        }
    }

    private fun openChecklist(checklistId: Long, notificationId: Int) {
        cancelNotification(notificationId)
        if (checklistId != -1L) {
            val intent = Intent(this, MainActivity::class.java).apply {
                action = ReminderReceiver.ACTION_OPEN_CHECKLIST
                putExtra(ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID, checklistId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            runCatching { startActivity(intent) }
                .onFailure { e ->
                    logger.error(TAG, "Failed to open checklist from full-screen reminder", e)
                }
        }
        finish()
    }

    private fun dismiss(notificationId: Int) {
        cancelNotification(notificationId)
        finish()
    }

    private fun cancelNotification(notificationId: Int) {
        if (notificationId != -1) {
            getSystemService(NotificationManager::class.java)?.cancel(notificationId)
        }
    }

    companion object {
        private const val TAG = "FullScreenReminder"

        private const val EXTRA_CHECKLIST_ID = "fsr_checklist_id"
        private const val EXTRA_CHECKLIST_NAME = "fsr_checklist_name"
        private const val EXTRA_ITEM_TEXT = "fsr_item_text"
        private const val EXTRA_NOTIFICATION_ID = "fsr_notification_id"

        fun createIntent(
            context: Context,
            checklistId: Long,
            checklistName: String,
            itemText: String,
            notificationId: Int
        ): Intent = Intent(context, FullScreenReminderActivity::class.java).apply {
            putExtra(EXTRA_CHECKLIST_ID, checklistId)
            putExtra(EXTRA_CHECKLIST_NAME, checklistName)
            putExtra(EXTRA_ITEM_TEXT, itemText)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
    }
}

@Composable
private fun FullScreenReminderContent(
    checklistName: String,
    itemText: String,
    onOpen: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(AppDimens.SpacingXl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.reminder_full_screen_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(AppDimens.SpacingMd))
            if (checklistName.isNotBlank()) {
                Text(
                    text = checklistName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(AppDimens.SpacingSm))
            }
            Text(
                text = itemText,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(AppDimens.SpacingXl))
            Row(horizontalArrangement = Arrangement.spacedBy(AppDimens.SpacingMd)) {
                AppButtonSecondary(
                    text = stringResource(Res.string.reminder_full_screen_dismiss),
                    onClick = onDismiss
                )
                AppButton(
                    text = stringResource(Res.string.reminder_full_screen_open),
                    onClick = onOpen
                )
            }
        }
    }
}
