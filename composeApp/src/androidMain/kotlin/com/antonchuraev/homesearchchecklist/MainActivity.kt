package com.antonchuraev.homesearchchecklist

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.antonchuraev.homesearchchecklist.consent.ConsentDialog
import com.antonchuraev.homesearchchecklist.core.auth.api.GoogleAuthRepository
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppContextHolder
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppThemeMode
import com.antonchuraev.homesearchchecklist.core.datastore.api.ThemeRepository
import com.antonchuraev.homesearchchecklist.core.navigation.api.AddToChecklistPurpose
import com.antonchuraev.homesearchchecklist.core.navigation.api.AppNavigator
import com.antonchuraev.homesearchchecklist.notification.ReminderReceiver
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

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

    /**
     * Pending ACTION_PROCESS_TEXT request captured on cold start (before the NavController is
     * ready). Consumed in the setContent LaunchedEffect once navigation can run.
     */
    private var pendingProcessText: ProcessTextRequest? = null

    private data class ProcessTextRequest(val text: String, val mode: ProcessTextMode)

    private fun extractProcessTextRequest(intent: Intent?): ProcessTextRequest? {
        if (intent?.action != ProcessTextContract.ACTION_PROCESS_TEXT) return null
        val text = intent.getStringExtra(ProcessTextContract.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
            ?: return null
        val mode = intent.getStringExtra(ProcessTextContract.EXTRA_MODE)
            ?.let { name -> ProcessTextMode.entries.firstOrNull { it.name == name } }
            ?: ProcessTextMode.CREATE_AI
        return ProcessTextRequest(text, mode)
    }

    private fun routeProcessText(request: ProcessTextRequest) {
        analyticsTracker.event(
            "process_text_entry",
            mapOf("mode" to request.mode.name.lowercase())
        )
        when (request.mode) {
            ProcessTextMode.CREATE_AI ->
                appNavigator.navigateToAnalyzeScreen(initialText = request.text, fillDefault = false)
            ProcessTextMode.FILL_AI ->
                appNavigator.navigateToAddToChecklistPicker(
                    text = request.text,
                    purpose = AddToChecklistPurpose.FILL_AI,
                )
            ProcessTextMode.NEW_CHECKLIST ->
                appNavigator.navigateToCreateChecklistScreen(initialText = request.text)
            ProcessTextMode.ADD_TO_EXISTING ->
                appNavigator.navigateToAddToChecklistPicker(request.text)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppContextHolder.init(applicationContext)
        // Initial edge-to-edge (light default) — overridden reactively in setContent once theme is resolved
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // Check for deep link in launch intent (cold start from notification)
        extractDeepLinkChecklistId(intent)?.let { id ->
            pendingChecklistId = id
        }

        // Check for an ACTION_PROCESS_TEXT request (cold start from the selection toolbar).
        // Navigation can't run until the NavController is ready, so stash and consume below.
        extractProcessTextRequest(intent)?.let { request ->
            pendingProcessText = request
        }

        setContent {
            // Reactive edge-to-edge: switch status bar icon style when app theme changes
            val themeRepository: ThemeRepository = koinInject()
            val themeMode by themeRepository.themeMode.collectAsStateWithLifecycle(initialValue = AppThemeMode.Light)
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
                AppThemeMode.System -> systemDark
            }
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            App()

            // Provide Activity reference to GoogleAuthRepository so Credential Manager
            // can display its bottom sheet. Must be called after Koin is initialized.
            val googleAuthRepository: GoogleAuthRepository = koinInject()
            val activity = this@MainActivity
            LaunchedEffect(activity) {
                googleAuthRepository.setActivityContext(activity)
            }

            // Show consent dialog for EEA/UK users on first launch
            val consentManager = GistiApplication.consentManager
            var showConsentDialog by remember {
                mutableStateOf(consentManager.isConsentRequired())
            }
            if (showConsentDialog) {
                ConsentDialog(
                    onAccept = {
                        consentManager.setConsent(granted = true)
                        showConsentDialog = false
                    },
                    onDecline = {
                        consentManager.setConsent(granted = false)
                        showConsentDialog = false
                    }
                )
            }

            // Consume pending deep link after NavController is ready (cold start from notification)
            LaunchedEffect(Unit) {
                consumePendingChecklistId()?.let { id ->
                    appNavigator.navigateToChecklistDetail(id)
                    analyticsTracker.event(AnalyticsEvents.Reminder.NOTIFICATION_TAPPED, mapOf(
                        "checklist_id" to id.toString()
                    ))
                }

                // Consume pending ACTION_PROCESS_TEXT request (cold start from selection toolbar)
                pendingProcessText?.let { request ->
                    pendingProcessText = null
                    routeProcessText(request)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Warm start — NavController is already ready
        extractDeepLinkChecklistId(intent)?.let { id ->
            appNavigator.navigateToChecklistDetail(id)
            analyticsTracker.event(AnalyticsEvents.Reminder.NOTIFICATION_TAPPED, mapOf(
                "checklist_id" to id.toString()
            ))
        }
        // Warm start from the ACTION_PROCESS_TEXT selection toolbar — navigate immediately.
        extractProcessTextRequest(intent)?.let { request ->
            routeProcessText(request)
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