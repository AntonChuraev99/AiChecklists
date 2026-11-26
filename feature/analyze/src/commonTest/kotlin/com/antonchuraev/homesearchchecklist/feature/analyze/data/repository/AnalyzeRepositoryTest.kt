package com.antonchuraev.homesearchchecklist.feature.analyze.data.repository

import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiInputType
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.AiServiceResponse
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FillChecklistResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FirebaseAiService
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.GenerateChecklistResult
import com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.RegisterUserResult
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.model.AiAnalyzeError
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
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the repository sends for file material, and what it refuses to send (FEATURE_SPEC
 * analyze-reliability-background-2026-11-20 §6, red-repros 4, 6, 7).
 */
class AnalyzeRepositoryTest {

    private val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x37) // "%PDF-1.7"

    private fun repo(
        service: FirebaseAiService,
        readBytes: (String) -> ByteArray? = { null },
        userData: FakeUserDataRepository = FakeUserDataRepository(),
    ) = AnalyzeRepositoryImpl(
        firebaseAiService = service,
        checklistRepository = NoopChecklistRepository(),
        userDataRepository = userData,
        readFileBytes = readBytes,
    )

    /** Red-repro 4: a PDF used to go out as the TEXT "PDF content from: <name>" — the model never saw the file. */
    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun pdf_isSentAsPdfBase64OfTheFileBytes() = runTest {
        val service = CapturingFirebaseAiService()

        repo(service, readBytes = { path -> if (path == "/files/lease.pdf") pdfBytes else null })
            .analyzeData(AnalyzeInputData.PdfDocument("/files/lease.pdf", "lease.pdf"), targetChecklist = null)

        assertEquals(AiInputType.PDF_BASE64, service.lastInputType, "a PDF must be sent as pdf_base64")
        assertEquals(Base64.encode(pdfBytes), service.lastInputData, "input_data must be the base64 of the file bytes")
    }

    /** Red-repro 6: an unreadable photo used to be sent as the text "Failed to read image: …" and charged. */
    @Test
    fun unreadablePhoto_failsLocally_withoutARequest() = runTest {
        val service = CapturingFirebaseAiService()

        val result = repo(service, readBytes = { null })
            .analyzeData(AnalyzeInputData.Photo("/cache/gone.jpg"), targetChecklist = null)

        assertEquals(0, service.callCount, "nothing may be sent when the file cannot be read")
        assertTrue(
            result.exceptionOrNull() is AiAnalyzeError.InputUnreadable,
            "expected InputUnreadable, got ${result.exceptionOrNull()}",
        )
    }

    /** Red-repro 7 (repository boundary): an empty success used to become an empty checklist. */
    @Test
    fun emptySuccess_isNothingFound_notAnEmptyChecklist() = runTest {
        val service = CapturingFirebaseAiService(generateItems = emptyList())

        val result = repo(service).analyzeData(AnalyzeInputData.RawText("asdf"), targetChecklist = null)

        assertTrue(
            result.exceptionOrNull() is AiAnalyzeError.NothingFound,
            "a success with zero items must be NothingFound, got ${result.getOrNull() ?: result.exceptionOrNull()}",
        )
    }

    @Test
    fun emptyFillSuccess_isNothingFound() = runTest {
        val service = CapturingFirebaseAiService(fillItems = emptyList())

        val result = repo(service).analyzeData(
            AnalyzeInputData.RawText("asdf"),
            targetChecklist = Checklist(id = 7L, name = "Trip", items = listOf(ChecklistItem(text = "Passport"))),
        )

        assertTrue(result.exceptionOrNull() is AiAnalyzeError.NothingFound)
    }

    @Test
    fun successBalance_isWrittenAsASingleKey_notAStaleSnapshot() = runTest {
        val userData = FakeUserDataRepository()
        val service = CapturingFirebaseAiService(credits = 60)

        repo(service, userData = userData).analyzeData(AnalyzeInputData.RawText("trip"), targetChecklist = null)

        assertEquals(listOf(60), userData.creditWrites)
        assertEquals(0, userData.updateCalls, "update(copy) would roll premium/onboarding back to the snapshot")
    }

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class CapturingFirebaseAiService(
        private val generateItems: List<ChecklistItem> = listOf(ChecklistItem(text = "Item")),
        private val fillItems: List<com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FilledItem> =
            listOf(com.antonchuraev.homesearchchecklist.feature.analyze.data.remote.FilledItem(0, "Passport", true, null)),
        private val credits: Int = 40,
    ) : FirebaseAiService {
        var callCount = 0
        var lastInputType: AiInputType? = null
        var lastInputData: String? = null

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
        ): Result<AiServiceResponse<FillChecklistResult>> {
            callCount++
            lastInputType = inputType
            lastInputData = inputData
            return Result.success(
                AiServiceResponse(
                    success = true,
                    data = FillChecklistResult(fillItems, summary = "", confidence = 0.9f, aiCredits = credits),
                ),
            )
        }

        override suspend fun generateChecklist(
            userId: String,
            isPremium: Boolean,
            prompt: String,
            inputType: AiInputType,
            inputData: String?,
        ): Result<AiServiceResponse<GenerateChecklistResult>> {
            callCount++
            lastInputType = inputType
            lastInputData = inputData
            return Result.success(
                AiServiceResponse(
                    success = true,
                    data = GenerateChecklistResult(
                        checklistName = "Name",
                        items = generateItems,
                        summary = "",
                        confidence = 0.9f,
                        aiCredits = credits,
                    ),
                ),
            )
        }
    }

    private class FakeUserDataRepository : UserDataRepository {
        private val userData = UserData(userId = "user-1", isPremium = true, aiCredits = 100, isOnboardingPassed = true)
        private val flow = MutableStateFlow(userData)
        val creditWrites = mutableListOf<Int>()
        var updateCalls = 0

        override fun getUserDataFlow(): StateFlow<UserData> = flow
        override suspend fun getUserData(): UserData = flow.value
        override suspend fun update(userData: UserData) {
            updateCalls++
            flow.value = userData
        }
        override suspend fun setAiCredits(credits: Int) {
            creditWrites += credits
            flow.value = flow.value.copy(aiCredits = credits)
        }
        override suspend fun ensureUserRegistered(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun syncWithServer(): Result<RegistrationData> =
            Result.success(RegistrationData(userData = userData, isNewUser = false))
        override suspend fun isPaywallLinked(): Boolean = false
        override suspend fun setPaywallLinked(linked: Boolean) {}
        override suspend fun restoreCreditsAfterPurchase(): Result<Int> = Result.success(0)
        override suspend fun getFirstLaunchAtMillis(): Long = 0L
    }

    private class NoopChecklistRepository : ChecklistRepository {
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
