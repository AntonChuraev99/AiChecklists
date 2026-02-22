package com.antonchuraev.homesearchchecklist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val appNavigator: AppNavigator by inject()
    private val analyticsTracker: AnalyticsTracker by inject()
    private val debugMenuDetector = if (AppBuildConfig.isDebug) {
        DebugMenuDetector { appNavigator.navigateToDebugMenu() }
    } else {
        null
    }

    var pendingChecklistId: Long? = null
        private set

    fun consumePendingChecklistId(): Long? {
        val id = pendingChecklistId
        pendingChecklistId = null
        return id
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppContextHolder.init(applicationContext)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT , Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Check for deep link in launch intent (cold start from notification)
        extractDeepLinkChecklistId(intent)?.let { id ->
            pendingChecklistId = id
        }

        setContent {
            App()

            // Consume pending deep link after NavController is ready (cold start from notification)
            LaunchedEffect(Unit) {
                consumePendingChecklistId()?.let { id ->
                    appNavigator.navigateToChecklistDetail(id)
                    analyticsTracker.event("reminder_notification_tapped", mapOf(
                        "checklist_id" to id.toString()
                    ))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm start — NavController is already ready
        extractDeepLinkChecklistId(intent)?.let { id ->
            appNavigator.navigateToChecklistDetail(id)
            analyticsTracker.event("reminder_notification_tapped", mapOf(
                "checklist_id" to id.toString()
            ))
        }
    }

    private fun extractDeepLinkChecklistId(intent: Intent?): Long? {
        if (intent?.action != ReminderReceiver.ACTION_OPEN_CHECKLIST) return null
        val id = intent.getLongExtra(ReminderReceiver.EXTRA_NAVIGATE_CHECKLIST_ID, -1L)
        return if (id != -1L) id else null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (debugMenuDetector?.onKeyDown(keyCode) == true) {
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}

private class DebugMenuDetector(
    private val onDebugMenuTriggered: () -> Unit
) {
    private var lastVolumeUpTime: Long = 0
    private var lastVolumeDownTime: Long = 0
    private var sequenceCount: Int = 0
    private var expectingVolumeDown: Boolean = true

    companion object {
        private const val SEQUENCE_TIMEOUT_MS = 500L
        private const val REQUIRED_SEQUENCE_COUNT = 3 // Up-Down-Up or Down-Up-Down
    }

    fun onKeyDown(keyCode: Int): Boolean {
        val currentTime = System.currentTimeMillis()

        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (expectingVolumeDown) {
                    // Starting new sequence or continuing after down
                    if (sequenceCount == 0 || currentTime - lastVolumeDownTime < SEQUENCE_TIMEOUT_MS) {
                        sequenceCount++
                        lastVolumeUpTime = currentTime
                        expectingVolumeDown = true

                        if (sequenceCount >= REQUIRED_SEQUENCE_COUNT) {
                            resetSequence()
                            onDebugMenuTriggered()
                            return true
                        }
                    } else {
                        resetSequence()
                        sequenceCount = 1
                        lastVolumeUpTime = currentTime
                    }
                } else {
                    if (currentTime - lastVolumeDownTime < SEQUENCE_TIMEOUT_MS) {
                        sequenceCount++
                        lastVolumeUpTime = currentTime
                        expectingVolumeDown = true

                        if (sequenceCount >= REQUIRED_SEQUENCE_COUNT) {
                            resetSequence()
                            onDebugMenuTriggered()
                            return true
                        }
                    } else {
                        resetSequence()
                        sequenceCount = 1
                        lastVolumeUpTime = currentTime
                        expectingVolumeDown = true
                    }
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (currentTime - lastVolumeUpTime < SEQUENCE_TIMEOUT_MS && sequenceCount > 0) {
                    sequenceCount++
                    lastVolumeDownTime = currentTime
                    expectingVolumeDown = false

                    if (sequenceCount >= REQUIRED_SEQUENCE_COUNT) {
                        resetSequence()
                        onDebugMenuTriggered()
                        return true
                    }
                } else {
                    resetSequence()
                }
            }
        }
        return false
    }

    private fun resetSequence() {
        sequenceCount = 0
        lastVolumeUpTime = 0
        lastVolumeDownTime = 0
        expectingVolumeDown = true
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    val context = LocalContext.current
    AppContextHolder.init(context)
    App()
}