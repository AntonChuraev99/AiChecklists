package com.antonchuraev.homesearchchecklist.feature.aichat.impl.presentation

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ChatIntent
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.IntentClassification
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.RoutingLayer
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.format.ChatDateFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

// ════════════════════════════════════════════════════════════════════════════
// A question must not roll back state that changed while it was being built.
//
// The init collectors (credit balance / premium, Deep Thinking, default list, response language)
// write _screenState concurrently with the send path. Building a question suspends — resource
// reads (really asynchronous since Compose Multiplatform 1.12), the preview renderer, the date
// formatter. A write shaped `_screenState.value = _screenState.value.copy(x = suspendCall())`
// reads the state BEFORE that suspension and writes the stale snapshot back AFTER it, silently
// erasing whatever a collector wrote in between: the credit counter shows a credit the classifier
// already spent until the next emission.
//
// The window is opened deterministically, without Compose Resources: the date formatter (which
// buildObjectRows calls for a reminder's time row) pushes a new balance into the user-data flow
// and yields, so the balance collector runs while the question is still being assembled.
// ════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalCoroutinesApi::class)
class ChatStateSnapshotRaceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Formats like [TokenDateFormatter], but every call first publishes a NEW balance and yields —
     * the Firestore sync landing exactly while the VM is suspended inside the question builder.
     * A fresh value per call, so the newest balance is always one the snapshot cannot hold.
     */
    private class BalanceShiftingFormatter(private val userRepo: HarnessUserDataRepository) : ChatDateFormatter {
        var calls = 0
            private set

        override suspend fun formatDateTime(epochMs: Long): String {
            shiftBalance()
            return "DT($epochMs)"
        }

        override suspend fun formatDay(epochMs: Long): String {
            shiftBalance()
            return "DAY($epochMs)"
        }

        private suspend fun shiftBalance() {
            calls++
            userRepo.update(userRepo.getUserData().copy(aiCredits = SHIFTED_BALANCE_BASE + calls))
            yield()
        }
    }

    @Test
    fun showWriteChoice_balanceArrivesWhileQuestionIsBuilt_newBalanceSurvives() = runTest {
        val userRepo = HarnessUserDataRepository("u1")
        val initialBalance = userRepo.getUserData().aiCredits
        val formatter = BalanceShiftingFormatter(userRepo)
        val rig = buildVmRig(
            classification = IntentClassification(
                intent = ChatIntent.SetReminder,
                confidence = 1f,
                layer = RoutingLayer.Local,
                preBuiltToolCall = ToolCall.SetItemReminder(
                    checklistHint = "Покупки",
                    itemText = "Молоко",
                    at = 1_784_000_000_000L,
                ),
            ),
            lists = listOf("Покупки"),
            dateFormatter = formatter,
            userRepo = userRepo,
        )

        rig.send("напомни про молоко завтра в 9")
        advanceUntilIdle()

        val state = rig.viewModel.screenState.value
        assertNotNull(state.pendingChoice, "no pendingChoice — the reminder question was never asked")
        // Preconditions: the window really opened, and the shifted balance differs from the start.
        assertNotEquals(0, formatter.calls, "the date formatter was never called — the race window never opened")
        val latestBalance = userRepo.getUserData().aiCredits
        assertNotEquals(initialBalance, latestBalance, "the fake did not change the balance")

        assertEquals(
            latestBalance,
            state.creditBalance,
            "a stale state snapshot overwrote the balance the collector wrote while the question " +
                "was being built (snapshot taken before a suspend call inside copy())",
        )
    }

    private companion object {
        /** Far from the harness default (100), so a stale snapshot can never match by accident. */
        const val SHIFTED_BALANCE_BASE = 40
    }
}
