package com.antonchuraev.homesearchchecklist.activation

import aichecklists.core.designsystem.generated.resources.Res
import aichecklists.core.designsystem.generated.resources.activation_sheet_reminder_failed
import aichecklists.core.designsystem.generated.resources.activation_sheet_reminder_set
import aichecklists.core.designsystem.generated.resources.activation_sheet_today_lower
import aichecklists.core.designsystem.generated.resources.activation_sheet_tomorrow_lower
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationPermissionSource
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderTarget
import com.antonchuraev.homesearchchecklist.core.common.api.notificationPermissionRequested
import com.antonchuraev.homesearchchecklist.core.common.api.notificationPermissionResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationPermissionAction
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.rememberNotificationPermissionController
import com.antonchuraev.homesearchchecklist.feature.home.presentation.notifications.NotificationsBannerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.getString
import org.koin.compose.koinInject

private const val TAG = "Activation"

/**
 * Behaviour host of [ActivationReminderSheet] (no ViewModel exists for this app-shell overlay).
 * Renders nothing while [target] is null; calls [onClose] when the sheet resolves.
 *
 * [target] is what the reminder is set on — the user's first checklist or first task — and is
 * restored by App.kt after process death (ReminderTargetSaver), so it never degrades to "the list".
 *
 * Paths: notifications on → schedule chosen time + snackbar · can ask → system dialog OVER the
 * sheet, close on any outcome (grant → schedule + snackbar; deny → banner suppressed for the
 * session, outcome "skipped") · permanently denied (known, or the OS answered with no dialog) → app
 * settings open, the sheet stays; on ON_RESUME with notifications on → schedule + close · skip /
 * scrim / back → outcome "skipped". A failed write shows an error snackbar and reports "skipped"
 * (truthful: no reminder exists). Web / iOS: never requested (coordinator gate); defensively
 * cancelled here without an outcome.
 */
@Composable
fun ActivationReminderHost(
    target: ReminderTarget?,
    snackbarHostState: SnackbarHostState,
    // App-root scope: scheduling + snackbar must outlive this host, which leaves composition on close.
    scope: CoroutineScope,
    onClose: () -> Unit,
) {
    if (target == null) return
    val coordinator: ActivationCoordinator = koinInject()
    val repository: ChecklistRepository = koinInject()
    val scheduler: ChecklistReminderScheduler = koinInject()
    val analytics: AnalyticsTracker = koinInject()
    val logger: AppLogger = koinInject()
    val bannerSession: NotificationsBannerSession = koinInject()

    if (!scheduler.supportsSystemNotifications) {
        LaunchedEffect(target) {
            logger.info(TAG, "reminder opt-in skipped: platform has no system notifications")
            // Defensive: the coordinator does not request the sheet on these platforms. If a request
            // still arrives, clear it WITHOUT an outcome — a "skipped" here would dilute the
            // Android-only `activation_reminder_optin` metric with users who never saw the sheet.
            coordinator.cancelReminderOptIn()
            onClose()
        }
        return
    }

    var previewText by remember(target) { mutableStateOf<String?>(null) }
    LaunchedEffect(target) {
        previewText = runCatching { activationReminderPreviewText(target, repository) }
            .onFailure { e -> logger.error(TAG, "load preview for opt-in failed: ${e.message}", e) }
            .getOrNull()
            .orEmpty()
    }
    val options = remember(target) {
        activationReminderOptions(kotlin.time.Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault())
    }
    var selectedIndex by rememberSaveable(target.toSaveable()) { mutableIntStateOf(0) }
    var isRequesting by remember { mutableStateOf(false) }
    var awaitingSettings by rememberSaveable(target.toSaveable()) { mutableStateOf(false) }
    var resolved by remember(target) { mutableStateOf(false) }

    /** Closes once; [report] runs in the app scope (double tap / dialog + resume race safe). */
    fun close(report: suspend () -> Unit) {
        if (resolved) return
        resolved = true
        scope.launch { report() }
        onClose()
    }

    fun skip() = close { coordinator.reportReminderOptInOutcome(granted = false) }

    fun scheduleAndClose() {
        val option = options[selectedIndex]
        close {
            val scheduled = runCatching {
                applyActivationReminder(target, option.triggerAtMs, repository, scheduler, analytics)
            }.onFailure { e ->
                logger.error(TAG, "schedule activation reminder failed: ${e.message}", e)
            }.isSuccess
            // Outcome follows what actually exists: no reminder written → not "granted".
            coordinator.reportReminderOptInOutcome(granted = scheduled)
            if (scheduled) {
                val time = formatReminderClock(option.hour, option.minute)
                // Lower-case day: the label sits mid-sentence ("Reminder set for today, 20:00").
                val label = when (option.day) {
                    ReminderOptionDay.Today -> getString(Res.string.activation_sheet_today_lower, time)
                    ReminderOptionDay.Tomorrow -> getString(Res.string.activation_sheet_tomorrow_lower, time)
                }
                snackbarHostState.showSnackbar(getString(Res.string.activation_sheet_reminder_set, label))
            } else {
                snackbarHostState.showSnackbar(getString(Res.string.activation_sheet_reminder_failed))
            }
        }
    }

    val permission = rememberNotificationPermissionController { result ->
        isRequesting = false
        analytics.notificationPermissionResult(
            source = NotificationPermissionSource.ACTIVATION_SHEET,
            granted = result.granted,
            permanentlyDenied = result.permanentlyDenied,
        )
        when {
            result.granted -> scheduleAndClose()
            // The OS answered with no dialog; the controller opened settings in the same tap. Same as
            // the Settings path: keep the sheet, schedule on return if notifications got enabled.
            result.openedSettings -> awaitingSettings = true
            else -> {
                // The user just said no to notifications: do not nag with the banner this session.
                bannerSession.suppressForSession()
                skip()
            }
        }
    }
    var mode by remember { mutableStateOf(permission.nextAction().toSheetMode()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        mode = permission.nextAction().toSheetMode()
        if (awaitingSettings && mode == ActivationSheetMode.NotificationsOn) {
            awaitingSettings = false
            scheduleAndClose()
        }
    }

    ActivationReminderSheet(
        checklistName = previewText.orEmpty(),
        isTask = target is ReminderTarget.Item,
        options = options,
        selectedIndex = selectedIndex,
        mode = mode,
        isRequesting = isRequesting,
        onSelect = { selectedIndex = it },
        onConfirm = {
            when (mode) {
                ActivationSheetMode.NotificationsOn -> scheduleAndClose()
                ActivationSheetMode.CanAsk -> {
                    isRequesting = true
                    analytics.notificationPermissionRequested(NotificationPermissionSource.ACTIVATION_SHEET)
                    permission.request()
                }
                ActivationSheetMode.SettingsRequired -> {
                    awaitingSettings = true
                    permission.request()
                }
            }
        },
        onSkip = { skip() },
        // Dismiss (scrim/back) counts as skip — never re-ask.
        onDismiss = { skip() },
    )
}

private fun NotificationPermissionAction.toSheetMode(): ActivationSheetMode = when (this) {
    NotificationPermissionAction.AlreadyEnabled -> ActivationSheetMode.NotificationsOn
    NotificationPermissionAction.SystemDialog -> ActivationSheetMode.CanAsk
    NotificationPermissionAction.Settings -> ActivationSheetMode.SettingsRequired
}
