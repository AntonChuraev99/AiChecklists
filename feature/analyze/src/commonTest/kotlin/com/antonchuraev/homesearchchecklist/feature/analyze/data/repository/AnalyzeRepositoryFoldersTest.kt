package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiInputType
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiServiceResponse
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FillChecklistResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.GenerateChecklistResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.RegisterUserResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.UsageInfo
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeResult
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.RegistrationData
import com.antonchuraev.homesearchchecklist.feature.user.domain.model.UserData
import com.antonchuraev.homesearchchecklist.feature.user.domain.repository.UserDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Folder-structure preservation when creating a checklist from an AI result
 * ([AnalyzeRepositoryImpl.createChecklistFromResult]).
 *
 * The AI result carries a FLAT [AnalyzeResult.suggestedItems] list whose folder structure lives
 * in each item's [ChecklistItem.parentId] / [ChecklistItem.type], plus [AnalyzeResult.hasFolders].
 * These tests assert the created [Checklist] sets `foldersEnabled` from that flag and forwards the
 * items verbatim (links intact). The default fill (one linked row per node, folders included) is
 * produced by the real repository's addChecklist and exercised by repository-level tests.
 */
class AnalyzeRepositoryFoldersTest {

    private fun repo(checklistRepository: ChecklistRepository) = AnalyzeRepositoryImpl(
        firebaseAiService = FakeFirebaseAiService(),
        checklistRepository = checklistRepository,
        userDataRepository = FakeUserDataRepository(),
    )

    @Test
    fun createChecklistFromResult_withFolders_enablesFoldersAndPreservesTree() = runTest {
        val folder = ChecklistItem(text = "Documents", type = ChecklistNodeType.FOLDER)
        val child = ChecklistItem(text = "Passport", parentId = folder.id)
        val rootLeaf = ChecklistItem(text = "Book tickets")
        val result = AnalyzeResult(
            suggestedItems = listOf(folder, child, rootLeaf),
            hasFolders = true,
        )
        val captor = CapturingChecklistRepository()

        val created = repo(captor).createChecklistFromResult("Trip", result)

        assertTrue(created.isSuccess)
        val saved = assertNotNull(captor.lastAddedChecklist)
        assertTrue(saved.foldersEnabled, "foldersEnabled must be true when hasFolders=true")
        assertEquals(3, saved.items.size)

        // The folder node keeps its FOLDER type and root position.
        val savedFolder = saved.items.first { it.id == folder.id }
        assertEquals(ChecklistNodeType.FOLDER, savedFolder.type)
        assertNull(savedFolder.parentId)

        // The child keeps its parentId link to the folder (structure survived).
        val savedChild = saved.items.first { it.id == child.id }
        assertEquals(ChecklistNodeType.ITEM, savedChild.type)
        assertEquals(folder.id, savedChild.parentId)

        // The created checklist carries the id returned by addChecklist.
        assertEquals(captor.assignedId, created.getOrNull()?.id)
    }

    @Test
    fun createChecklistFromResult_withoutFolders_keepsFlatAndDisablesFolders() = runTest {
        val items = listOf(
            ChecklistItem(text = "Milk"),
            ChecklistItem(text = "Eggs"),
        )
        val result = AnalyzeResult(suggestedItems = items, hasFolders = false)
        val captor = CapturingChecklistRepository()

        val created = repo(captor).createChecklistFromResult("Groceries", result)

        assertTrue(created.isSuccess)
        val saved = assertNotNull(captor.lastAddedChecklist)
        assertFalse(saved.foldersEnabled, "foldersEnabled must be false when hasFolders=false")
        assertEquals(2, saved.items.size)
        assertTrue(saved.items.all { it.type == ChecklistNodeType.ITEM && it.parentId == null })
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private class CapturingChecklistRepository(
        val assignedId: Long = 42L,
    ) : NoopChecklistRepository() {
        var lastAddedChecklist: Checklist? = null
        override suspend fun addChecklist(checklist: Checklist): Long {
            lastAddedChecklist = checklist
            return assignedId
        }
    }

    private open class NoopChecklistRepository : ChecklistRepository {
        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
        override suspend fun addChecklist(checklist: Checklist): Long = 1L
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
        override suspend fun setRepeatSchedule(checklistId: Long, rule: ReminderRepeatRule, timeOfDayMinutes: Int, firstTriggerAt: Long) {}
        override suspend fun advanceRepeatSchedule(checklistId: Long, nextAt: Long?, newCount: Int) {}
        override suspend fun clearRepeatSchedule(checklistId: Long) {}
        override suspend fun resetDefaultFillChecks(checklistId: Long) {}
        override suspend fun countActiveRepeatSchedules(): Int = 0
        override suspend fun getActiveRepeatSchedules(): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getPastDueRepeatSchedules(nowMillis: Long): List<ChecklistRepeatInfo> = emptyList()
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) {}
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) {}
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = flowOf(null)
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun updateFill(fill: ChecklistFill) {}
        override suspend fun deleteFill(fill: ChecklistFill) {}
    }

    private class FakeUserDataRepository : UserDataRepository {
        private val userData = UserData(userId = "test", isPremium = false)
        private val flow = MutableStateFlow(userData)
        override fun getUserDataFlow(): StateFlow<UserData> = flow
        override suspend fun getUserData(): UserData = userData
        override suspend fun update(userData: UserData) {}
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class FakeFirebaseAiService : FirebaseAiService {
        override suspend fun registerUser(
            deviceId: String,
            appVersion: String?,
            platform: String?,
        ): Result<AiServiceResponse<RegisterUserResult>> = error("not used")

        override suspend fun analyzeAndFillChecklist(
            userId: String,
            isPremium: Boolean,
            checklist: Checklist,
            inputType: AiInputType,
            inputData: String,
        ): Result<AiServiceResponse<FillChecklistResult>> = error("not used")

        override suspend fun generateChecklist(
            userId: String,
            isPremium: Boolean,
            prompt: String,
            inputType: AiInputType,
            inputData: String?,
        ): Result<AiServiceResponse<GenerateChecklistResult>> = error("not used")

        override suspend fun getUsageStats(
            userId: String,
            isPremium: Boolean,
        ): Result<AiServiceResponse<UsageInfo>> = error("not used")
    }
}
