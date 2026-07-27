package com.antonchuraev.homesearchchecklist.feature.checklist.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.NavExperimentResolver
import com.antonchuraev.homesearchchecklist.core.common.api.NavVariant
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Behaviour tests for [ReconcileInboxForControlArmUseCase] — the CONTROL-arm rollback path for the
 * nav A/B experiment.
 *
 * Two failure directions matter and they pull against each other:
 *  * failing to de-flag leaves a control user permanently locked out of the tasks in their Inbox;
 *  * de-flagging too eagerly dissolves a real v2 user's Inbox into their Projects list.
 *
 * The second is why an unassigned arm (Remote Config has not answered) must be treated as "not
 * control yet" even though the resolver reports [NavVariant.CONTROL] for it.
 */
class ReconcileInboxForControlArmUseCaseTest {

    // ─── The rollback actually happens ───────────────────────────────────

    @Test
    fun assignedControlArm_withFlaggedRow_clearsTheFlag() = runTest {
        val repo = FakeRepository(hasInbox = true)
        val useCase = useCase(repo, arm = NavVariant.CONTROL, assigned = true)

        assertTrue(useCase(), "an assigned control arm holding an Inbox row must de-flag it")
        assertEquals(1, repo.clearCallCount)
    }

    @Test
    fun assignedControlArm_withNothingFlagged_isANoOp() = runTest {
        val repo = FakeRepository(hasInbox = false)
        val useCase = useCase(repo, arm = NavVariant.CONTROL, assigned = true)

        assertFalse(useCase(), "nothing to repair must not be reported as a repair")
        assertEquals(1, repo.clearCallCount, "the check itself is one cheap query")
    }

    // ─── It can never fire for a v2 user ────────────────────────────────

    @Test
    fun v2Arm_neverTouchesTheFlag() = runTest {
        val repo = FakeRepository(hasInbox = true)
        val useCase = useCase(repo, arm = NavVariant.V2, assigned = true)

        assertFalse(useCase())
        assertEquals(0, repo.clearCallCount, "the v2 arm's Inbox is a live feature, not stale state")
    }

    /**
     * The regression this guard exists for: a v2 user reinstalls, Firestore re-syncs their flagged
     * row, and Remote Config has not assigned an arm yet — so the resolver reports CONTROL. Acting
     * on that would dissolve their Inbox. The retry on a later launch (once the arm IS assigned) must
     * still work, so the skip must NOT consume the one-shot guard.
     */
    @Test
    fun controlButArmNotAssignedYet_skipsAndStillReconcilesOnceAssigned() = runTest {
        val repo = FakeRepository(hasInbox = true)
        // RC has assigned nothing yet, so the resolver reports CONTROL but not assigned.
        val resolver = FakeResolver(NavVariant.CONTROL, assigned = false)
        val useCase = ReconcileInboxForControlArmUseCase(
            repository = repo,
            navResolver = resolver,
            logger = RecordingLogger(),
        )

        assertFalse(useCase(), "an unassigned arm is not proof of control")
        assertEquals(0, repo.clearCallCount, "a v2 user in the rc-activation gap must keep their Inbox")

        // A later launch: Remote Config finally assigned control.
        resolver.assigned = true

        assertTrue(useCase(), "the skip must not have consumed the one-shot guard")
        assertEquals(1, repo.clearCallCount)
    }

    // ─── One-shot + idempotent ──────────────────────────────────────────

    @Test
    fun secondCallInTheSameProcess_doesNoWork() = runTest {
        val repo = FakeRepository(hasInbox = true)
        val useCase = useCase(repo, arm = NavVariant.CONTROL, assigned = true)

        assertTrue(useCase())
        assertFalse(useCase(), "already reconciled — a repeat call must report no change")
        assertFalse(useCase())
        assertEquals(1, repo.clearCallCount, "the repository must be hit exactly once per process")
    }

    @Test
    fun repeatedV2Calls_shortCircuitToo() = runTest {
        val repo = FakeRepository(hasInbox = true)
        val resolver = FakeResolver(NavVariant.V2)
        val useCase = ReconcileInboxForControlArmUseCase(
            repository = repo,
            navResolver = resolver,
            logger = RecordingLogger(),
        )

        repeat(3) { assertFalse(useCase()) }
        assertEquals(
            1,
            resolver.resolveCount,
            "the arm is sticky, so one resolve is enough to decide for the whole process",
        )
    }

    // ─── Never breaks app start ─────────────────────────────────────────

    @Test
    fun repositoryFailure_isSwallowedAndLoggedWithTheThrowable() = runTest {
        val repo = FakeRepository(hasInbox = true, failOnClear = true)
        val logger = RecordingLogger()
        val useCase = ReconcileInboxForControlArmUseCase(
            repository = repo,
            navResolver = FakeResolver(NavVariant.CONTROL),
            logger = logger,
        )

        assertFalse(useCase(), "a failed rollback must not be reported as a success")
        assertEquals(1, logger.errors.size, "the failure must be logged, never swallowed silently")
        assertTrue(
            logger.errors.single().second != null,
            "the throwable must be passed so the failure reaches Crashlytics",
        )
    }

    @Test
    fun resolverFailure_isSwallowed() = runTest {
        val repo = FakeRepository(hasInbox = true)
        val logger = RecordingLogger()
        val useCase = ReconcileInboxForControlArmUseCase(
            repository = repo,
            navResolver = ThrowingResolver(),
            logger = logger,
        )

        assertFalse(useCase())
        assertEquals(0, repo.clearCallCount, "an unreadable arm must never be treated as control")
        assertEquals(1, logger.errors.size)
    }

    // ─── helpers / test doubles ─────────────────────────────────────────

    private fun useCase(
        repo: FakeRepository,
        arm: NavVariant,
        assigned: Boolean,
    ) = ReconcileInboxForControlArmUseCase(
        repository = repo,
        navResolver = FakeResolver(arm, assigned),
        logger = RecordingLogger(),
    )

    /**
     * [assigned] is a `var` so a test can model the state actually at stake: the SAME install whose
     * arm was not assigned on one launch and is assigned on the next.
     */
    private class FakeResolver(
        private val arm: NavVariant,
        var assigned: Boolean = true,
    ) : NavExperimentResolver {
        var resolveCount = 0
            private set

        override fun currentArm(): NavVariant = arm
        override suspend fun ensureResolved(): NavVariant {
            resolveCount++
            return arm
        }

        override fun isArmAssigned(): Boolean = assigned
    }

    /** Models the resolver itself failing (DataStore corrupt underneath it, say). */
    private class ThrowingResolver : NavExperimentResolver {
        override fun currentArm(): NavVariant = NavVariant.CONTROL
        override suspend fun ensureResolved(): NavVariant =
            throw IllegalStateException("datastore corrupt")

        override fun isArmAssigned(): Boolean = false
    }

    private class RecordingLogger : AppLogger {
        val errors = mutableListOf<Pair<String, Throwable?>>()

        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors += message to throwable
        }
    }

    /**
     * Only [clearInboxFlag] carries behaviour; the rest are the usual empty stubs (this interface has
     * 40+ members). [ChecklistRepository.clearInboxFlag] has a default body, so a fake that forgets
     * to override it would silently report "nothing flagged" — hence the explicit override here.
     */
    private class FakeRepository(
        private val hasInbox: Boolean,
        private val failOnClear: Boolean = false,
    ) : ChecklistRepository {
        var clearCallCount = 0
            private set

        override suspend fun clearInboxFlag(): Boolean {
            clearCallCount++
            if (failOnClear) throw IllegalStateException("db write failed")
            return hasInbox
        }

        // ── Unused stubs ──
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 0L
        override suspend fun updateChecklist(checklist: Checklist) {}
        override suspend fun updateChecklistTemplate(checklist: Checklist) {}
        override suspend fun deleteChecklist(checklist: Checklist) {}
        override suspend fun getChecklistById(id: Long): Checklist? = null
        override fun observeChecklistById(id: Long): Flow<Checklist?> = flowOf(null)
        override suspend fun reorderChecklists(orderedIds: List<Long>) {}
        override suspend fun setSeparateCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setAutoDeleteCompleted(checklistId: Long, value: Boolean) {}
        override suspend fun setFoldersEnabled(checklistId: Long, value: Boolean) {}
        override suspend fun setReminder(checklistId: Long, reminderAt: Long?) {}
        override suspend fun countActiveReminders(): Int = 0
        override suspend fun getActiveReminders(): List<ChecklistReminderInfo> = emptyList()
        override suspend fun getDefaultFillOneShot(checklistId: Long): ChecklistFill? = null
        override suspend fun getAllItemRemindersForRescheduling(): List<ItemReminderInfo> = emptyList()
        override suspend fun setRepeatSchedule(
            checklistId: Long,
            rule: ReminderRepeatRule,
            timeOfDayMinutes: Int,
            firstTriggerAt: Long,
        ) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> =
            flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> =
            flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 0L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    }
}
