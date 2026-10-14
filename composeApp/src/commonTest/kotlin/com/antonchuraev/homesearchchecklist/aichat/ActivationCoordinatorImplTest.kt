package com.antonchuraev.homesearchchecklist.aichat

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsEvents
import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderTarget
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
        sut.onAiChecklistCreated(checklistId = 10L)
        // Second creation: no longer pending → must NOT fire again.
        sut.onAiChecklistCreated(checklistId = 11L)
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

        sut.onAiChecklistCreated(checklistId = 10L)
        advanceUntilIdle()

        assertTrue(
            analytics.events.none { it.first == AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED },
            "a non-pending (returning) user must never fire the first-AI-checklist event",
        )
    }

    // The former `onAiChecklistCreated_eventFiresInBothArms_butReminderOnlyInTreatment` pinned the
    // OFF arm of `activation_bundle_v1` (no reminder request). The flag and that arm were deleted on
    // 2026-10-14, so its ON half lives on below and the OFF half has no code left to guard.

    @Test
    fun onAiChecklistCreated_pendingNewUser_requestsReminderOptInForThatChecklist() = runTest {
        val sut = coordinator(FakeActivationPrefs(pending = setOf("uid-1")), RecordingAnalytics())

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onAiChecklistCreated(checklistId = 42L)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(42L), collected, "the first AI checklist must request the reminder opt-in")
    }

    @Test
    fun onUserChecklistCreated_firstManualChecklist_requestsSheet_butKeepsFirstAiEventForLater() = runTest {
        val prefs = FakeActivationPrefs(pending = setOf("uid-1"))
        val analytics = RecordingAnalytics()
        val sut = coordinator(prefs, analytics)

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onUserChecklistCreated(checklistId = 5L)
        advanceUntilIdle()
        sut.reportReminderOptInOutcome(granted = false)
        sut.onAiChecklistCreated(checklistId = 6L)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(5L), collected, "sheet once, for the first (manual) checklist only")
        assertEquals(
            1,
            analytics.events.count { it.first == AnalyticsEvents.Activation.FIRST_AI_CHECKLIST_CREATED },
            "a manual first checklist must not swallow the later first-AI event",
        )
    }

    @Test
    fun onUserChecklistCreated_twiceBeforeOutcome_requestsSheetOnce() = runTest {
        val sut = coordinator(FakeActivationPrefs(pending = setOf("uid-1")), RecordingAnalytics())

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onUserChecklistCreated(checklistId = 1L)
        sut.onUserChecklistCreated(checklistId = 2L)
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(1L), collected, "a double create must not queue a second sheet")
    }

    @Test
    fun reminderOptInPending_setOnRequest_clearedOnOutcome() = runTest {
        val sut = coordinator(FakeActivationPrefs(pending = setOf("uid-1")), RecordingAnalytics())
        assertEquals(null, sut.reminderOptInTarget.value)

        sut.onUserChecklistCreated(checklistId = 9L)
        assertEquals(ReminderTarget.Checklist(9L), sut.reminderOptInTarget.value, "pending while the sheet is up")

        sut.reportReminderOptInOutcome(granted = false)
        assertEquals(null, sut.reminderOptInTarget.value, "cleared once resolved")
    }

    private val task = ReminderTarget.Item(checklistId = 3L, fillId = 30L, itemId = "item-1", text = "Buy milk")

    @Test
    fun firstTaskBeforeAnyChecklist_winsTheSheet_andTargetsThatItem() = runTest {
        val sut = coordinator(FakeActivationPrefs(pending = setOf("uid-1")), RecordingAnalytics())

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onUserTaskCreated(task)
        assertEquals(task, sut.reminderOptInTarget.value, "the sheet must remind about THAT task")
        sut.reportReminderOptInOutcome(granted = true)
        sut.onUserChecklistCreated(checklistId = 4L)
        sut.onUserTaskCreated(task.copy(itemId = "item-2"))
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(3L), collected, "once per install: the later checklist and task get nothing")
    }

    @Test
    fun firstChecklistBeforeAnyTask_winsTheSheet_taskWhileSheetUpIsIgnored() = runTest {
        val sut = coordinator(FakeActivationPrefs(pending = setOf("uid-1")), RecordingAnalytics())

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onUserChecklistCreated(checklistId = 8L)
        sut.onUserTaskCreated(task) // while the sheet is still up
        advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(8L), collected)
        assertEquals(ReminderTarget.Checklist(8L), sut.reminderOptInTarget.value, "the task must not retarget the open sheet")
    }

    @Test
    fun platformWithoutSystemNotifications_noRequest_noOutcomeEvent_notMarkedShown() = runTest {
        val prefs = FakeActivationPrefs(pending = setOf("uid-1"))
        val analytics = RecordingAnalytics()
        val sut = ActivationCoordinatorImpl(
            activationPrefs = prefs,
            userDataRepository = FakeUserDataRepository("uid-1"),
            analytics = analytics,
            logger = NoOpLogger(),
            supportsSystemNotifications = { false },
        )

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onUserChecklistCreated(checklistId = 1L)
        sut.onUserTaskCreated(task)
        sut.cancelReminderOptIn() // the host's defensive clear must stay silent too
        advanceUntilIdle()
        job.cancel()

        assertTrue(collected.isEmpty(), "web / iOS must never request the sheet")
        assertEquals(null, sut.reminderOptInTarget.value)
        assertTrue(
            analytics.events.none { it.first == AnalyticsEvents.Activation.REMINDER_OPTIN },
            "no activation_reminder_optin outcome for a user who never saw the sheet",
        )
        assertTrue("uid-1" !in prefs.shown, "not marked shown — the user never saw it")
    }

    @Test
    fun restoreAfterProcessDeath_holdsTheLatch_andTheTarget() = runTest {
        // Fresh coordinator (process restarted) + the UI restored the open sheet's target.
        val sut = coordinator(FakeActivationPrefs(pending = setOf("uid-1")), RecordingAnalytics())
        sut.restoreReminderOptIn(task)
        assertEquals(task, sut.reminderOptInTarget.value, "banners stay suppressed under the restored sheet")

        val collected = mutableListOf<Long>()
        val job = launch { sut.reminderOptInRequests.toList(collected) }
        advanceUntilIdle()
        sut.onUserChecklistCreated(checklistId = 5L) // must not queue a second sheet
        advanceUntilIdle()
        job.cancel()
        assertTrue(collected.isEmpty())

        sut.reportReminderOptInOutcome(granted = true)
        assertEquals(null, sut.reminderOptInTarget.value)
    }

    @Test
    fun taskByReturningUser_neverRequestsSheet() = runTest {
        val sut = coordinator(FakeActivationPrefs(pending = emptySet()), RecordingAnalytics())
        sut.onUserTaskCreated(task)
        assertEquals(null, sut.reminderOptInTarget.value)
    }

    @Test
    fun decideActivationStep_matrix() {
        // returning user: nothing on any path
        assertEquals(ActivationStep(false, false), decideActivationStep(false, false, isAiPath = true))
        assertEquals(ActivationStep(false, false), decideActivationStep(false, false, isAiPath = false))
        // new user, sheet not shown yet
        assertEquals(ActivationStep(true, true), decideActivationStep(true, false, isAiPath = true))
        assertEquals(ActivationStep(false, true), decideActivationStep(true, false, isAiPath = false))
        // new user, sheet already shown: only the AI event may still be pending
        assertEquals(ActivationStep(true, false), decideActivationStep(true, true, isAiPath = true))
        assertEquals(ActivationStep(false, false), decideActivationStep(true, true, isAiPath = false))
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
        sut.onAiChecklistCreated(checklistId = 7L)
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
