package com.antonchuraev.homesearchchecklist.feature.create.domain.usecase

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.ChecklistSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.FirestoreSyncDataSource
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.GalleryTemplateItemData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.GalleryTemplateSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.UserDocSyncData
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Attachment
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.Checklist
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistRepeatInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ItemReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.TodayReminderInfo
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.repository.ChecklistRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behaviour contract of the gallery deep-link create-as-is flow.
 *
 * Two cases are regression guards for prod incidents, NOT nice-to-haves:
 * - a terminal [AppResult.Loading] must map to Error, never NotFound (`not_found` is the
 *   gallery↔Firestore drift signal and must stay clean);
 * - a default fill that never materialises must NOT hang the use case forever (that hang is
 *   why `gallery_deeplink_opened` fired 7× against 1 create and 0 failures — the caller
 *   emitted no outcome at all).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CreateChecklistFromGalleryTemplateUseCaseTest {

    // ─── Fakes ────────────────────────────────────────────────────────────

    private class FakeGalleryDataSource(
        private val response: AppResult<GalleryTemplateSyncData?>,
    ) : FirestoreSyncDataSource {
        val requestedSlugs = mutableListOf<String>()

        override suspend fun fetchGalleryTemplate(slug: String): AppResult<GalleryTemplateSyncData?> {
            requestedSlugs += slug
            return response
        }

        override fun observeChecklistIds(userId: String): Flow<AppResult<List<String>>> = emptyFlow()
        override fun observeChecklist(userId: String, cloudId: String): Flow<AppResult<ChecklistSyncData>> = emptyFlow()
        override suspend fun uploadChecklist(userId: String, data: ChecklistSyncData): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun deleteChecklist(userId: String, cloudId: String): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun uploadBatch(userId: String, checklists: List<ChecklistSyncData>): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun fetchAllChecklists(userId: String): AppResult<List<ChecklistSyncData>> = AppResult.Success(emptyList())
        override fun observeUserDoc(userId: String): Flow<AppResult<UserDocSyncData?>> = emptyFlow()
        override suspend fun findUserIdByGoogleUid(googleUid: String): AppResult<String?> = AppResult.Success(null)
    }

    /**
     * Mirrors the real repository contract that matters here: [addChecklist] persists the
     * template AND auto-creates the default fill (note = null per item, each row linked back
     * via `templateItemId`). Set [emitDefaultFill] = false to model the pathological case where
     * that fill never shows up on the flow.
     */
    private class FakeChecklistRepository(
        private val newChecklistId: Long = NEW_CHECKLIST_ID,
        private val emitDefaultFill: Boolean = true,
    ) : ChecklistRepository {
        val addedChecklists = mutableListOf<Checklist>()
        val updatedFills = mutableListOf<ChecklistFill>()

        private val defaultFill = MutableStateFlow<ChecklistFill?>(null)

        override suspend fun addChecklist(checklist: Checklist): Long {
            addedChecklists += checklist
            if (emitDefaultFill) {
                defaultFill.value = ChecklistFill(
                    id = DEFAULT_FILL_ID,
                    checklistId = newChecklistId,
                    name = checklist.name,
                    isDefault = true,
                    items = checklist.items.map { item ->
                        ChecklistFillItem(
                            text = item.text,
                            checked = false,
                            note = null,
                            templateItemId = item.id,
                        )
                    },
                )
            }
            return newChecklistId
        }

        override fun getDefaultFillByChecklistId(checklistId: Long): Flow<ChecklistFill?> = defaultFill

        override suspend fun updateFill(fill: ChecklistFill) {
            updatedFills += fill
        }

        override val checklists: Flow<List<Checklist>> = flowOf(emptyList())
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
        override fun observeRemindersInRange(fromMs: Long, toMs: Long): Flow<List<TodayReminderInfo>> = flowOf(emptyList())
        override suspend fun getRemindersInRange(fromMs: Long, toMs: Long): List<TodayReminderInfo> = emptyList()
        override suspend fun togglePriority(fillId: Long, itemId: String): Result<Unit> = Result.success(Unit)
        override suspend fun addAttachment(fillId: Long, itemId: String, attachment: Attachment) = Unit
        override suspend fun removeAttachment(fillId: Long, itemId: String, attachmentId: String) = Unit
        override suspend fun getTotalAdditionalFillCount(): Int = 0
        override suspend fun getWeeklyChecklistCount(): Int = 0
        override val weeklyChecklistCount: Flow<Int> = flowOf(0)
        override fun getFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override fun getAdditionalFillsByChecklistId(checklistId: Long): Flow<List<ChecklistFill>> = flowOf(emptyList())
        override suspend fun getFillById(id: Long): ChecklistFill? = null
        override suspend fun getFillCountByChecklistId(checklistId: Long): Int = 0
        override suspend fun addFill(fill: ChecklistFill): Long = 1L
        override suspend fun deleteFill(fill: ChecklistFill) {}
        override suspend fun reorderItems(fill: ChecklistFill, checklist: Checklist) {}
    }

    private class RecordingLogger : AppLogger {
        data class Entry(val tag: String, val message: String, val throwable: Throwable?)

        val errors = mutableListOf<Entry>()

        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {}
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors += Entry(tag, message, throwable)
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private class Rig(
        val useCase: CreateChecklistFromGalleryTemplateUseCase,
        val repository: FakeChecklistRepository,
        val dataSource: FakeGalleryDataSource,
        val logger: RecordingLogger,
    )

    private fun buildRig(
        response: AppResult<GalleryTemplateSyncData?>,
        emitDefaultFill: Boolean = true,
    ): Rig {
        val dataSource = FakeGalleryDataSource(response)
        val repository = FakeChecklistRepository(emitDefaultFill = emitDefaultFill)
        val logger = RecordingLogger()
        return Rig(
            useCase = CreateChecklistFromGalleryTemplateUseCase(
                firestoreSyncDataSource = dataSource,
                checklistRepository = repository,
                logger = logger,
            ),
            repository = repository,
            dataSource = dataSource,
            logger = logger,
        )
    }

    private fun template(vararg items: GalleryTemplateItemData) = GalleryTemplateSyncData(
        slug = SLUG,
        category = "travel",
        title = TITLE,
        ordered = true,
        items = items.toList(),
    )

    // ─── Tests ────────────────────────────────────────────────────────────

    /** Case 1 — happy path: checklist created as-is, per-item notes preserved into the default fill. */
    @Test
    fun invoke_whenTemplateHasNotes_createsChecklistAndBackFillsNotesIntoDefaultFill() = runTest {
        val rig = buildRig(
            AppResult.Success(
                template(
                    GalleryTemplateItemData(text = "Passport", note = "Front pocket of the backpack"),
                    GalleryTemplateItemData(text = "Sunscreen", note = null),
                    GalleryTemplateItemData(text = "Cash", note = "Small bills for taxis"),
                )
            )
        )

        val result = rig.useCase(SLUG)

        assertIs<CreateChecklistFromGalleryTemplateUseCase.Result.Created>(result)
        assertEquals(NEW_CHECKLIST_ID, result.checklistId)
        assertEquals(listOf(SLUG), rig.dataSource.requestedSlugs)

        val created = rig.repository.addedChecklists.single()
        assertEquals(TITLE, created.name)
        assertEquals(listOf("Passport", "Sunscreen", "Cash"), created.items.map { it.text })
        assertEquals(listOf(false, false, false), created.items.map { it.checked })

        val fill = rig.repository.updatedFills.single()
        assertEquals(listOf("Passport", "Sunscreen", "Cash"), fill.items.map { it.text })
        assertEquals(
            listOf("Front pocket of the backpack", null, "Small bills for taxis"),
            fill.items.map { it.note },
        )
        assertEquals(DEFAULT_FILL_ID, fill.id, "must update the auto-created default fill, not add a second one")
        assertTrue(rig.logger.errors.isEmpty(), "happy path must not log errors: ${rig.logger.errors}")
    }

    /** Case 2 — note-less template: the fill write is skipped entirely (blank notes count as absent). */
    @Test
    fun invoke_whenTemplateHasNoNotes_createsChecklistWithoutWritingFill() = runTest {
        val rig = buildRig(
            AppResult.Success(
                template(
                    GalleryTemplateItemData(text = "Passport", note = null),
                    GalleryTemplateItemData(text = "Sunscreen", note = "   "),
                )
            )
        )

        val result = rig.useCase(SLUG)

        assertIs<CreateChecklistFromGalleryTemplateUseCase.Result.Created>(result)
        assertEquals(NEW_CHECKLIST_ID, result.checklistId)
        assertEquals(1, rig.repository.addedChecklists.size)
        assertEquals(
            emptyList(),
            rig.repository.updatedFills,
            "note-less template must not write the fill (no needless sync churn)",
        )
    }

    /** Case 3 — unknown slug: NotFound, and nothing is persisted. */
    @Test
    fun invoke_whenSlugUnknown_returnsNotFoundWithoutCreating() = runTest {
        val rig = buildRig(AppResult.Success(null))

        val result = rig.useCase(SLUG)

        assertEquals(CreateChecklistFromGalleryTemplateUseCase.Result.NotFound, result)
        assertEquals(emptyList(), rig.repository.addedChecklists)
        assertEquals(emptyList(), rig.repository.updatedFills)
    }

    /** Case 4 — transport/decode failure: Error carrying the original cause, logged with the throwable. */
    @Test
    fun invoke_whenFetchFails_returnsErrorWithOriginalCauseAndLogsThrowable() = runTest {
        val boom = IllegalStateException("firestore unavailable")
        val rig = buildRig(AppResult.Error(boom))

        val result = rig.useCase(SLUG)

        val error = assertIs<CreateChecklistFromGalleryTemplateUseCase.Result.Error>(result)
        assertEquals(boom, error.cause, "the original exception must survive into the result")
        assertEquals(emptyList(), rig.repository.addedChecklists)

        val logged = rig.logger.errors.single()
        assertEquals(boom, logged.throwable, "must log WITH the throwable (Crashlytics recordException)")
        assertTrue(SLUG in logged.message, "log line must name the slug: ${logged.message}")
    }

    /**
     * Case 5 — REGRESSION GUARD. A terminal [AppResult.Loading] is an unfinished fetch, not a stale
     * slug. Mapping it to NotFound made the funnel report `reason=not_found`, which is the signal
     * that the live gallery page and Firestore drifted apart — polluting it hides real content bugs.
     */
    @Test
    fun invoke_whenFetchReturnsTerminalLoading_returnsErrorNotNotFound() = runTest {
        val rig = buildRig(AppResult.Loading)

        val result = rig.useCase(SLUG)

        assertIs<CreateChecklistFromGalleryTemplateUseCase.Result.Error>(result)
        assertEquals(emptyList(), rig.repository.addedChecklists)

        val logged = rig.logger.errors.single()
        assertNotNull(logged.throwable, "an unexpected terminal Loading must be logged with a throwable")
        assertTrue(SLUG in logged.message, "log line must name the slug: ${logged.message}")
    }

    /**
     * Case 6 — REGRESSION GUARD (prod incident). The default fill never materialises. The wait is
     * bounded, so the use case still returns Created (checklist is the deliverable, notes are a
     * nice-to-have) instead of suspending forever — an infinite wait is what left the caller
     * emitting neither `checklist_created` nor `gallery_deeplink_failed` and showing no snackbar.
     *
     * Runs on virtual time (`runTest` auto-advances the scheduler while the body is suspended), so
     * the 5s bound costs no real wall-clock time.
     */
    @Test
    fun invoke_whenDefaultFillNeverAppears_returnsCreatedAfterBoundedWaitAndLogs() = runTest {
        val rig = buildRig(
            response = AppResult.Success(
                template(GalleryTemplateItemData(text = "Passport", note = "Front pocket of the backpack"))
            ),
            emitDefaultFill = false,
        )

        val result = rig.useCase(SLUG)

        assertIs<CreateChecklistFromGalleryTemplateUseCase.Result.Created>(result)
        assertEquals(NEW_CHECKLIST_ID, result.checklistId, "the checklist is persisted and its id must reach the caller")
        assertEquals(1, rig.repository.addedChecklists.size)
        assertEquals(
            emptyList(),
            rig.repository.updatedFills,
            "no fill ever appeared, so nothing may be written back",
        )
        assertTrue(
            currentTime > 0,
            "expected a real (virtual-time) wait before degrading, got currentTime=$currentTime",
        )

        val logged = rig.logger.errors.single()
        assertNotNull(logged.throwable, "degrading to 'created without notes' must not be silent")
    }

    /**
     * Case 7 — cancellation must NOT be laundered into a Result.Error. `runCatching` swallows
     * CancellationException too, so without the explicit rethrow a cancelled coroutine would be
     * reported as a genuine failure (and logged as one), breaking structured concurrency.
     *
     * Discriminators that fail if the rethrow is removed: the use case would then *return* a value
     * (captured in [completedResult]) and log an error.
     */
    @Test
    fun invoke_whenCancelledWhileWaitingForFill_propagatesCancellationInsteadOfReturningError() = runTest {
        val rig = buildRig(
            response = AppResult.Success(
                template(GalleryTemplateItemData(text = "Passport", note = "Front pocket of the backpack"))
            ),
            emitDefaultFill = false,
        )
        var completedResult: CreateChecklistFromGalleryTemplateUseCase.Result? = null

        // UNDISPATCHED: runs synchronously up to the first real suspension — the wait for the fill.
        val running = async(start = CoroutineStart.UNDISPATCHED) {
            rig.useCase(SLUG).also { completedResult = it }
        }
        assertEquals(1, rig.repository.addedChecklists.size, "must be suspended past addChecklist")

        running.cancel()
        advanceUntilIdle()

        assertFailsWith<CancellationException> { running.await() }
        assertNull(completedResult, "a cancelled invocation must not produce a Result at all")
        assertEquals(emptyList(), rig.logger.errors, "cancellation is not a failure and must not be logged as one")
    }

    private companion object {
        const val SLUG = "beach-day-packing"
        const val TITLE = "Beach Day Packing"
        const val NEW_CHECKLIST_ID = 55L
        const val DEFAULT_FILL_ID = 900L
    }
}
