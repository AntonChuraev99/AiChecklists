package com.antonchuraev.homesearchchecklist.feature.home.presentation.notifications

import com.antonchuraev.homesearchchecklist.core.common.api.ActivationCoordinator
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsParams
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationPermissionSource
import com.antonchuraev.homesearchchecklist.core.common.api.notificationPermissionRequested
import com.antonchuraev.homesearchchecklist.core.common.api.notificationPermissionResult
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.scheduler.ChecklistReminderScheduler
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationPermissionAction
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationPermissionResult
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationsBannerUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The notifications-off banner's behaviour, shared by every surface that shows it (checklist
 * detail, v2 Inbox). One implementation so the two surfaces cannot drift: same visibility rule,
 * same session / persisted dismiss counter, same analytics (tagged with [screen]).
 *
 * Owned by a ViewModel ([scope] = viewModelScope). The host mirrors [banner] into its UiState and
 * forwards the four user events; [onTurnedOn] is the host's confirmation snackbar, fired at most
 * once per visible→hidden flip (dialog callback and the ON_RESUME after it race — see [applyEnabled]).
 */
class NotificationsBannerController(
    private val scope: CoroutineScope,
    private val screen: String,
    private val reminderScheduler: ChecklistReminderScheduler,
    private val datastore: AppDatastore,
    private val session: NotificationsBannerSession,
    private val activationCoordinator: ActivationCoordinator,
    private val analytics: AnalyticsTracker,
    private val onTurnedOn: () -> Unit,
) {
    private val _banner = MutableStateFlow<NotificationsBannerUi?>(null)
    val banner: StateFlow<NotificationsBannerUi?> = _banner.asStateFlow()

    private var settingsRequired = false
    private var reminderVariant: () -> Boolean = { false }
    private var started = false

    /**
     * Wires the controller once. [reminderVariant] is read on every refresh ("your reminder won't
     * ring" copy). Re-evaluates on EVERY change of the activation sheet's pending target: when it
     * opens, an already-visible banner hides (it would sit under the modal sheet); when it resolves,
     * a deny there keeps it suppressed for the session and a skip lets it appear again.
     */
    fun start(reminderVariant: () -> Boolean) {
        this.reminderVariant = reminderVariant
        if (started) return
        started = true
        scope.launch {
            activationCoordinator.reminderOptInTarget
                .drop(1)
                .collect { refresh() }
        }
    }

    /** Recomputes visibility from live platform state; returns it. */
    suspend fun refresh(): Boolean {
        val enabled = reminderScheduler.areNotificationsEnabled()
        if (enabled && _banner.value != null) {
            applyEnabled()
            return false
        }
        val visible = shouldShowNotificationsBanner(
            platformSupportsNotifications = reminderScheduler.supportsSystemNotifications,
            notificationsEnabled = enabled,
            persistedDismissCount = datastore.observeInt(PREF_DISMISS_COUNT, 0).first(),
            dismissedThisSession = session.dismissedThisSession,
            suppressedThisSession = session.suppressedThisSession,
        ) && activationCoordinator.reminderOptInTarget.value == null // never under the modal sheet
        _banner.value = if (visible) {
            NotificationsBannerUi(reminderVariant = reminderVariant(), settingsRequired = settingsRequired)
        } else {
            null
        }
        if (visible && session.claimShownEvent(screen)) {
            analytics.event(AnalyticsEvents.NotificationsBanner.SHOWN, mapOf(AnalyticsParams.SCREEN to screen))
        }
        return visible
    }

    /** ON_RESUME: [settingsRequired] = controller.nextAction() == Settings. */
    fun onStatusCheck(settingsRequired: Boolean) {
        this.settingsRequired = settingsRequired
        scope.launch { refresh() }
    }

    fun onCta(action: NotificationPermissionAction) {
        when (action) {
            NotificationPermissionAction.SystemDialog -> {
                analytics.event(
                    AnalyticsEvents.NotificationsBanner.CTA,
                    mapOf(AnalyticsParams.ACTION to ACTION_SYSTEM_DIALOG, AnalyticsParams.SCREEN to screen),
                )
                analytics.notificationPermissionRequested(NotificationPermissionSource.BANNER)
            }
            NotificationPermissionAction.Settings -> analytics.event(
                AnalyticsEvents.NotificationsBanner.CTA,
                mapOf(AnalyticsParams.ACTION to ACTION_SETTINGS, AnalyticsParams.SCREEN to screen),
            )
            // Enabled between render and tap: the platform controller already reported granted.
            NotificationPermissionAction.AlreadyEnabled -> Unit
        }
    }

    fun onPermissionResult(result: NotificationPermissionResult) {
        analytics.notificationPermissionResult(
            source = NotificationPermissionSource.BANNER,
            granted = result.granted,
            permanentlyDenied = result.permanentlyDenied,
        )
        if (result.granted) {
            applyEnabled()
        } else {
            // Denied: banner stays (spec); the next CTA follows the permanent-deny answer.
            settingsRequired = result.permanentlyDenied
            _banner.value = _banner.value?.copy(settingsRequired = result.permanentlyDenied)
        }
    }

    fun onDismiss() {
        _banner.value = null // immediate feedback; persist after
        scope.launch {
            val before = datastore.observeInt(PREF_DISMISS_COUNT, 0).first()
            val after = dismissCountAfterDismiss(before, session.dismissedThisSession)
            session.markDismissed()
            if (after != before) datastore.saveInt(PREF_DISMISS_COUNT, after)
            analytics.event(
                AnalyticsEvents.NotificationsBanner.DISMISSED,
                mapOf(AnalyticsParams.DISMISS_COUNT to after, AnalyticsParams.SCREEN to screen),
            )
        }
    }

    /** Notifications got enabled through another ask on the same screen (reminder flow). */
    fun onGrantedElsewhere() = applyEnabled()

    private fun applyEnabled() {
        if (_banner.value == null) return
        _banner.value = null
        onTurnedOn()
    }

    companion object {
        /** Persisted x count, device-global and shared by all surfaces (hidden forever at 3). */
        const val PREF_DISMISS_COUNT = "notif_banner_dismiss_count"
        const val SCREEN_CHECKLIST = "checklist"
        const val SCREEN_INBOX = "inbox"
        private const val ACTION_SYSTEM_DIALOG = "system_dialog"
        private const val ACTION_SETTINGS = "settings"
    }
}
