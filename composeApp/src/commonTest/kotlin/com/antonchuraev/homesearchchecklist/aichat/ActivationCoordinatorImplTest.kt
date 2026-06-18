package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.ActivationPrefsRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [ActivationCoordinatorImpl] — the new-user activation funnel orchestrator.
 *
 * Focus: FIRST_AI_CHECKLIST_CREATED fires exactly once (only while the user is new-user-pending),
 * and the reminder opt-in is requested once in the treatment arm.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivationCoordinatorImplTest {

    private fun coordinator(
        prefs: FakeActivationPrefs,
        analytics: RecordingAnalytics,
        userId: String = "uid-1",
    ): ActivationCoordinatorImpl = ActivationCoordinatorImpl(
        activationPrefs = prefs,
        userDataRepository = FakeUserDataRepository(userId),
        analytics = analytics,
        logger = NoOpLogger(),
    )

    @Test
    fun onAiChecklistCreated_pendingNewUser_firesFirstAiEventOnce_andClearsPending() = runTest {
        val prefs = FakeActivationPrefs(pending = setOf("uid-1"))
        val analytics = RecordingAnalytics()
        val sut = coordinator(prefs, analytics)

        // First creation: pending → fires the event and consumes the pending flag.
        sut.onAiChecklistCreated(checklistId = 10L, activationBundleEnabled = true)
        // Second creation: no longer pending → must NOT fire again.
        sut.onAiChecklistCreated(checklistId = 11L, activationBundleEnabled = true)
        advanceUntilIdle()

        val firstAiEvents = analytics.events.count {
            it.first == AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED
        }
        assertEquals(1, firstAiEvents, "FIRST_AI_CHECKLIST_CREATED must fire exactly once per user")
        assertTrue("uid-1" !in prefs.pending, "pending flag must be cleared after the first AI checklist")
    }

    @Test
    fun onAiChecklistCreated_notPending_doesNotFire() = runTest {
        val prefs = FakeActivationPrefs(pending = emptySet())
        val analytics = RecordingAnalytics()
        val sut = coordinator(prefs, analytics)

        sut.onAiChecklistCreated(checklistId = 10L, activationBundleEnabled = true)
        advanceUntilIdle()

        assertTrue(
            analytics.events.none { it.first == AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED },
            "a non-pending (returning) user must never fire the first-AI-checklist event",
        )
    }

    @Test
    fun onAiChecklistCreated_eventFiresInBothArms_butReminderOnlyInTreatment() = runTest {
        // Control arm (flag OFF): event still fires (so the A/B can compare), but no reminder request.
        val prefsOff = FakeActivationPrefs(pending = setOf("uid-1"))
        val analyticsOff = RecordingAnalytics()
        val sutOff = coordinator(prefsOff, analyticsOff)

        val collectedOff = mutableListOf<Long>()
        val jobOff = launch { sutOff.reminderOptInRequests.toList(collectedOff) }
        advanceUntilIdle()
        sutOff.onAiChecklistCreated(checklistId = 10L, activationBundleEnabled = false)
        advanceUntilIdle()
        jobOff.cancel()

        assertEquals(
            1,
            analyticsOff.events.count { it.first == AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED },
            "FIRST_AI_CHECKLIST_CREATED must fire in the control arm too",
        )
        assertTrue(collectedOff.isEmpty(), "control arm (flag OFF) must NOT request the reminder opt-in")

        // Treatment arm (flag ON): event fires AND a reminder opt-in is requested for the checklist.
        val prefsOn = FakeActivationPrefs(pending = setOf("uid-1"))
        val analyticsOn = RecordingAnalytics()
        val sutOn = coordinator(prefsOn, analyticsOn)

        val collectedOn = mutableListOf<Long>()
        val jobOn = launch { sutOn.reminderOptInRequests.toList(collectedOn) }
        advanceUntilIdle()
        sutOn.onAiChecklistCreated(checklistId = 42L, activationBundleEnabled = true)
        advanceUntilIdle()
        jobOn.cancel()

        assertEquals(listOf(42L), collectedOn, "treatment arm must request the reminder opt-in for the new checklist")
    }

    @Test
    fun reportReminderOptInOutcome_marksShown_andEmitsOutcomeEvent() = runTest {
        val prefs = FakeActivationPrefs(pending = setOf("uid-1"))
        val analytics = RecordingAnalytics()
        val sut = coordinator(prefs, analytics)

        // Trigger a reminder request so pendingReminderUid is set to "uid-1".
        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onAiChecklistCreated(checklistId = 7L, activationBundleEnabled = true)
        advanceUntilIdle()

        sut.reportReminderOptInOutcome(granted = true)
        advanceUntilIdle()
        job.cancel()

        assertTrue("uid-1" in prefs.shown, "reporting the outcome must mark the opt-in shown (never re-ask)")
        val optInEvent = analytics.events.firstOrNull { it.first == AnalyticsEvents.Activation.REMINDER_OPTIN }
        assertEquals("granted", optInEvent?.second?.get("outcome"), "outcome param must record granted")
    }

    // ── Test doubles ──────────────────────────────────────────────────────────

    private class FakeActivationPrefs(
        pending: Set<String> = emptySet(),
        shown: Set<String> = emptySet(),
    ) : ActivationPrefsRepository {
        val pending = pending.toMutableSet()
        val shown = shown.toMutableSet()

        override suspend fun isNewUserPending(uid: String): Boolean = uid in pending
        override suspend fun setNewUserPending(uid: String) { pending += uid }
        override suspend fun clearNewUserPending(uid: String) { pending -= uid }
        override suspend fun isReminderOptInShown(uid: String): Boolean = uid in shown
        override suspend fun markReminderOptInShown(uid: String) { shown += uid }
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) {}
        override fun setUserProperties(properties: Map<String, Any>) {}
        override fun screenView(name: String) {}
        override fun event(name: String, params: Map<String, Any>) { events += name to params }
    }

    private class FakeUserDataRepository(private val userId: String) : UserDataRepository {
        private val _flow = MutableStateFlow(UserData(userId = userId))
        override fun getUserDataFlow(): StateFlow<UserData> = _flow
        override suspend fun getUserData(): UserData = UserData(userId = userId)
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(UserData(userId = userId), isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(UserData(userId = userId), isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class NoOpLogger : AppLogger {
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }
}
