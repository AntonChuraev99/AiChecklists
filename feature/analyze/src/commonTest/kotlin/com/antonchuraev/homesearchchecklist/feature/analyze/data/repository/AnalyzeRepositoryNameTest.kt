package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiInputType
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiServiceResponse
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FillChecklistResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.GenerateChecklistResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.RegisterUserResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.UsageInfo
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AnalyzeInputData
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fix 1 at the repository boundary: the AI-suggested checklist name (CF `checklist_name`) must flow
 * from [GenerateChecklistResult.checklistName] into [com.antonchuraev.homesearchchecklist.feature
 * .analyze.domain.model.AnalyzeResult.suggestedName] on the generate (new-checklist) path. Before
 * the fix it was dropped, so every generated checklist shared the generic default title.
 */
class AnalyzeRepositoryNameTest {

    private fun repo(service: FirebaseAiService) = AnalyzeRepositoryImpl(
        firebaseAiService = service,
        checklistRepository = NoopChecklistRepository(),
        userDataRepository = FakeUserDataRepository(),
    )

    @Test
    fun analyzeData_generatePath_mapsServerChecklistNameIntoSuggestedName() = runTest {
        val service = FakeFirebaseAiService(
            generateResult = GenerateChecklistResult(
                checklistName = "Trip Packing Checklist",
                items = listOf(ChecklistItem(text = "Passport"), ChecklistItem(text = "Charger")),
                summary = "",
                confidence = 0.9f,
                aiCredits = 5,
                hasFolders = false,
            )
        )

        // targetChecklist == null → generate path (new checklist).
        val result = repo(service).analyzeData(AnalyzeInputData.RawText("trip"), targetChecklist = null)

        assertTrue(result.isSuccess)
        assertEquals("Trip Packing Checklist", result.getOrNull()?.suggestedName)
    }

    @Test
    fun analyzeData_generatePath_nullServerName_keepsSuggestedNameNull() = runTest {
        // Server omitted the name → suggestedName stays null so presentation applies the LOCALIZED
        // default (the service no longer injects an English "New Checklist" literal).
        val service = FakeFirebaseAiService(
            generateResult = GenerateChecklistResult(
                checklistName = null,
                items = listOf(ChecklistItem(text = "Item")),
                summary = "",
                confidence = 0.8f,
                aiCredits = 3,
                hasFolders = false,
            )
        )

        val result = repo(service).analyzeData(AnalyzeInputData.RawText("x"), targetChecklist = null)

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull()?.suggestedName)
    }

    // ── Fakes ───────────────────────────────────────────────────────────────

    private class FakeFirebaseAiService(
        private val generateResult: GenerateChecklistResult,
    ) : FirebaseAiService {
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
        ): Result<AiServiceResponse<GenerateChecklistResult>> =
            Result.success(AiServiceResponse(success = true, data = generateResult))

        override suspend fun getUsageStats(
            userId: String,
            isPremium: Boolean,
        ): Result<AiServiceResponse<UsageInfo>> = error("not used")
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
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    }
}
