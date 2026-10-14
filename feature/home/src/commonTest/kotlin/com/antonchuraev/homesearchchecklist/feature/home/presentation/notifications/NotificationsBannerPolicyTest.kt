package com.antonchuraev.homesearchchecklist.feature.home.presentation.notifications

import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.NotificationPermissionAction
import com.antonchuraev.homesearchchecklist.core.common.api.NotificationDialogOutcome
import com.antonchuraev.homesearchchecklist.core.common.api.classifyNotificationDialogResult
import com.antonchuraev.homesearchchecklist.core.common.api.isPermanent
import com.antonchuraev.homesearchchecklist.core.common.api.isPermanentDenialStillValid
import com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.resolveNotificationPermissionAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsBannerPolicyTest {

    private fun show(
        platform: Boolean = true,
        enabled: Boolean = false,
        count: Int = 0,
        dismissed: Boolean = false,
        suppressed: Boolean = false,
    ) = shouldShowNotificationsBanner(platform, enabled, count, dismissed, suppressed)

    @Test
    fun banner_showsOnlyWhenOffOnSupportedPlatformAndNotDismissed() {
        assertTrue(show())
        assertFalse(show(platform = false), "web/iOS never show it")
        assertFalse(show(enabled = true), "notifications on → no banner")
        assertFalse(show(dismissed = true), "x in this session hides it")
        assertFalse(show(suppressed = true), "deny in the activation sheet hides it for the session")
        assertTrue(show(count = 2), "two dismisses still show in a new session")
        assertFalse(show(count = 3), "third dismiss hides it forever")
    }

    @Test
    fun dismissCount_secondDismissInSameSession_doesNotCount() {
        assertEquals(1, dismissCountAfterDismiss(0, dismissedThisSession = false))
        assertEquals(1, dismissCountAfterDismiss(1, dismissedThisSession = true))
        assertEquals(3, dismissCountAfterDismiss(2, dismissedThisSession = false))
    }

    @Test
    fun session_rollsOnlyAfterThirtyMinutesInBackground() {
        val start = ForegroundSessionState(index = 0, backgroundedAtMs = null)
        val gap = NotificationsBannerSession.SESSION_GAP_MS
        assertEquals(0, start.onForeground(1_000).index, "foreground without a background edge keeps the session")
        assertEquals(0, start.onBackground(0).onForeground(gap - 1).index, "29:59 away → same session")
        assertEquals(1, start.onBackground(0).onForeground(gap).index, "30:00 away → new session")
    }

    @Test
    fun sessionFlags_clearWhenSessionRolls() {
        var now = 0L
        val session = NotificationsBannerSession { now }
        session.markDismissed()
        session.suppressForSession()
        assertTrue(session.claimShownEvent(CHECKLIST))
        assertFalse(session.claimShownEvent(CHECKLIST), "shown event once per (session, screen)")
        assertTrue(session.dismissedThisSession && session.suppressedThisSession)

        session.onBackground()
        now += NotificationsBannerSession.SESSION_GAP_MS
        session.onForeground()

        assertFalse(session.dismissedThisSession)
        assertFalse(session.suppressedThisSession)
        assertTrue(session.claimShownEvent(CHECKLIST), "new session may report shown again")
    }

    @Test
    fun shownEvent_dedupedPerScreen_notAcrossScreens() {
        val session = NotificationsBannerSession { 0L }
        assertTrue(session.claimShownEvent(CHECKLIST))
        assertTrue(session.claimShownEvent(INBOX), "the inbox banner must get its own shown event (screen param)")
        assertFalse(session.claimShownEvent(INBOX))
        assertFalse(session.claimShownEvent(CHECKLIST))
    }

    @Test
    fun permissionAction_dialogUnlessPermanenceWasProven() {
        fun action(enabled: Boolean = false, api33: Boolean = true, granted: Boolean = false, permanent: Boolean = false) =
            resolveNotificationPermissionAction(enabled, api33, granted, permanent)

        assertEquals(NotificationPermissionAction.AlreadyEnabled, action(enabled = true))
        assertEquals(NotificationPermissionAction.SystemDialog, action(), "never asked / dismissed / denied once → dialog")
        assertEquals(NotificationPermissionAction.Settings, action(permanent = true), "proven permanent denial")
        assertEquals(NotificationPermissionAction.Settings, action(api33 = false), "API < 33 with notifications off")
        assertEquals(NotificationPermissionAction.Settings, action(granted = true), "permission granted, app toggle off")
    }

    @Test
    fun dialogResult_dismissIsNotPermanent() {
        // F2: back / tap outside on Android 11+ leaves rationale false before AND after.
        val outcome = classifyNotificationDialogResult(
            granted = false, dialogShown = true, rationaleBefore = false, rationaleAfter = false,
        )
        assertEquals(NotificationDialogOutcome.DeniedCanAskAgain, outcome)
        assertFalse(outcome.isPermanent)
    }

    @Test
    fun dialogResult_permanentOnlyWhenProvable() {
        assertEquals(
            NotificationDialogOutcome.Granted,
            classifyNotificationDialogResult(granted = true, dialogShown = true, rationaleBefore = false, rationaleAfter = false),
        )
        assertEquals(
            NotificationDialogOutcome.DeniedCanAskAgain,
            classifyNotificationDialogResult(granted = false, dialogShown = true, rationaleBefore = false, rationaleAfter = true),
            "first 'Don't allow' → rationale turns on, can ask again",
        )
        assertEquals(
            NotificationDialogOutcome.PermanentlyDenied,
            classifyNotificationDialogResult(granted = false, dialogShown = true, rationaleBefore = true, rationaleAfter = false),
            "second 'Don't allow' → rationale flips off",
        )
        // F3: upgraded user already blocked — the OS answers without any UI.
        val blocked = classifyNotificationDialogResult(granted = false, dialogShown = false, rationaleBefore = false, rationaleAfter = false)
        assertEquals(NotificationDialogOutcome.BlockedNoDialog, blocked)
        assertTrue(blocked.isPermanent)
    }

    @Test
    fun storedPermanentFlag_staleOnceTheOsWouldAskAgain() {
        assertTrue(isPermanentDenialStillValid(storedFlag = true, permissionGranted = false, rationaleNow = false))
        assertFalse(isPermanentDenialStillValid(storedFlag = true, permissionGranted = false, rationaleNow = true))
        assertFalse(isPermanentDenialStillValid(storedFlag = true, permissionGranted = true, rationaleNow = false))
        assertFalse(isPermanentDenialStillValid(storedFlag = false, permissionGranted = false, rationaleNow = false))
    }

    private companion object {
        const val CHECKLIST = "checklist"
        const val INBOX = "inbox"
    }
}
