package com.antonchuraev.homesearchchecklist.feature.checklist.ui.reminder

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannelSaveResult
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramFailureReason
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramReminderLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ReminderDeliveryChannelControllerTest {

    // ── Pure mapping (spec §5.2) ──────────────────────────────────────────────

    @Test
    fun mapping_knowledgeToRow() {
        assertNull(reminderDeliveryRowUi(TelegramLinkKnowledge.Unknown, null, false, null, web = false))
        assertEquals(
            ReminderDeliveryRowUi.Connect(web = false),
            reminderDeliveryRowUi(TelegramLinkKnowledge.SignedOut, null, false, null, web = false),
        )
        assertEquals(
            ReminderDeliveryRowUi.Connect(web = true),
            reminderDeliveryRowUi(TelegramLinkKnowledge.NotLinked, null, false, null, web = true),
        )
        assertNull(reminderDeliveryRowUi(TelegramLinkKnowledge.Linked(null), null, false, null, web = false))
        assertEquals(
            ReminderDeliveryRowUi.Choice(ReminderChannel.Both, saving = ReminderChannel.Push, failed = false, web = true),
            reminderDeliveryRowUi(TelegramLinkKnowledge.Linked(ReminderChannel.Both), ReminderChannel.Push, false, null, web = true),
        )
    }

    @Test
    fun mapping_confirmedOverrideBridgesTheCacheLag() {
        val row = reminderDeliveryRowUi(
            TelegramLinkKnowledge.Linked(ReminderChannel.Push), null, false, ReminderChannel.Telegram, web = false,
        )
        assertEquals(ReminderChannel.Telegram, (row as ReminderDeliveryRowUi.Choice).confirmed)
    }

    // ── Controller ────────────────────────────────────────────────────────────

    private class FakeLink(initial: TelegramLinkKnowledge) : TelegramReminderLink {
        val state = MutableStateFlow(initial)
        override val telegramLink: Flow<TelegramLinkKnowledge> = state
        val calls = mutableListOf<ReminderChannel>()
        val pending = ArrayDeque<CompletableDeferred<ReminderChannelSaveResult>>()

        override suspend fun setReminderChannel(channel: ReminderChannel): ReminderChannelSaveResult {
            calls += channel
            val answer = CompletableDeferred<ReminderChannelSaveResult>()
            pending.addLast(answer)
            return answer.await()
        }

        /** Answers the oldest in-flight call; a success also updates the cache, like the real impl. */
        fun answer(result: ReminderChannelSaveResult) {
            if (result is ReminderChannelSaveResult.Saved) state.value = TelegramLinkKnowledge.Linked(result.channel)
            pending.removeFirst().complete(result)
        }
    }

    private class RecordingAnalytics : AnalyticsTracker {
        val events = mutableListOf<Pair<String, Map<String, Any>>>()
        override fun setUserId(userId: String) = Unit
        override fun setUserProperties(properties: Map<String, Any>) = Unit
        override fun screenView(name: String) = Unit
        override fun event(name: String, params: Map<String, Any>) { events += name to params }
    }

    private object SilentLogger : AppLogger {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }

    private fun TestScope.controller(link: FakeLink, analytics: AnalyticsTracker = RecordingAnalytics()) =
        ReminderDeliveryChannelController(link, analytics, SilentLogger, backgroundScope, web = false)
            .also { runCurrent() }

    private fun ReminderDeliveryChannelController.choice() = delivery.value as ReminderDeliveryRowUi.Choice

    @Test
    fun sameValueAsConfirmed_sendsNothing() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val c = controller(link)
        c.onChannelClick(ReminderChannel.Push)
        runCurrent()
        assertEquals(emptyList(), link.calls)
        assertNull(c.choice().saving)
    }

    @Test
    fun tapShowsSaving_successConfirms_andReportsSheetSource() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val analytics = RecordingAnalytics()
        val c = controller(link, analytics)
        c.onChannelClick(ReminderChannel.Telegram)
        runCurrent()
        assertEquals(ReminderChannel.Telegram, c.choice().saving)
        assertEquals(ReminderChannel.Push, c.choice().confirmed) // value line keeps the confirmed value

        link.answer(ReminderChannelSaveResult.Saved(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(ReminderDeliveryRowUi.Choice(ReminderChannel.Telegram, null, false, false), c.delivery.value)
        assertEquals(
            listOf("tg_reminder_channel_set" to mapOf<String, Any>("channel" to "telegram", "source" to "reminder_sheet")),
            analytics.events,
        )
    }

    @Test
    fun latestTapWins_whileInFlight_oneMoreRequestForTheLastValueOnly() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val c = controller(link)
        c.onChannelClick(ReminderChannel.Telegram)
        runCurrent()
        c.onChannelClick(ReminderChannel.Both)
        c.onChannelClick(ReminderChannel.Push) // back to confirmed while Telegram is in flight: a real change
        runCurrent()
        assertEquals(listOf(ReminderChannel.Telegram), link.calls)
        assertEquals(ReminderChannel.Push, c.choice().saving)

        link.answer(ReminderChannelSaveResult.Saved(ReminderChannel.Telegram))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Telegram, ReminderChannel.Push), link.calls)
        assertEquals(ReminderChannel.Push, c.choice().saving)

        link.answer(ReminderChannelSaveResult.Saved(ReminderChannel.Push))
        runCurrent()
        assertEquals(ReminderDeliveryRowUi.Choice(ReminderChannel.Push, null, false, false), c.delivery.value)
    }

    @Test
    fun tapOnTheValueBeingSaved_isDeduped() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val c = controller(link)
        c.onChannelClick(ReminderChannel.Both)
        runCurrent()
        c.onChannelClick(ReminderChannel.Both)
        link.answer(ReminderChannelSaveResult.Saved(ReminderChannel.Both))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Both), link.calls)
    }

    @Test
    fun failure_revertsToConfirmed_flagsFailed_reportsReason_nextTapClearsIt() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Both))
        val analytics = RecordingAnalytics()
        val c = controller(link, analytics)
        c.onChannelClick(ReminderChannel.Push)
        runCurrent()
        link.answer(ReminderChannelSaveResult.Failed(TelegramFailureReason.Network))
        runCurrent()
        assertEquals(ReminderDeliveryRowUi.Choice(ReminderChannel.Both, null, failed = true, web = false), c.delivery.value)
        assertEquals(
            "tg_reminder_channel_set_failed" to mapOf<String, Any>("channel" to "push", "source" to "reminder_sheet", "reason" to "network"),
            analytics.events.single(),
        )

        c.onChannelClick(ReminderChannel.Push) // retry = tap again
        runCurrent()
        assertEquals(false, c.choice().failed)
        assertEquals(ReminderChannel.Push, c.choice().saving)
    }

    @Test
    fun failedEvent_reportsTheChannelOfTheFailedRequest_notThePendingTap() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val analytics = RecordingAnalytics()
        val c = controller(link, analytics)
        c.onChannelClick(ReminderChannel.Telegram)
        runCurrent()
        c.onChannelClick(ReminderChannel.Both) // pending while Telegram is in flight
        link.answer(ReminderChannelSaveResult.Failed(TelegramFailureReason.Server))
        runCurrent()
        assertEquals("telegram", analytics.events.single().second["channel"])
    }

    @Test
    fun tapOnConfirmedRadioAfterFailure_clearsTheFailure_withoutARequest() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Both))
        val c = controller(link)
        c.onChannelClick(ReminderChannel.Push)
        runCurrent()
        link.answer(ReminderChannelSaveResult.Failed(TelegramFailureReason.Network))
        runCurrent()
        c.onChannelClick(ReminderChannel.Both) // the confirmed (reverted) radio
        runCurrent()
        assertEquals(false, c.choice().failed)
        assertEquals(listOf(ReminderChannel.Push), link.calls)
    }

    @Test
    fun serverAnswersAnotherChannel_noResendLoop_serverValueWins() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val c = controller(link)
        c.onChannelClick(ReminderChannel.Telegram)
        runCurrent()
        link.answer(ReminderChannelSaveResult.Saved(ReminderChannel.Both))
        runCurrent()
        assertEquals(listOf(ReminderChannel.Telegram), link.calls)
        assertEquals(ReminderDeliveryRowUi.Choice(ReminderChannel.Both, null, false, false), c.delivery.value)
    }

    @Test
    fun sheetShown_clearsAStaleFailure() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Both))
        val c = controller(link)
        c.onChannelClick(ReminderChannel.Push)
        runCurrent()
        link.answer(ReminderChannelSaveResult.Failed(TelegramFailureReason.Server))
        runCurrent()
        c.onSheetShown()
        runCurrent()
        assertEquals(false, c.choice().failed)
    }

    @Test
    fun web_onMapsPushToBoth_offMapsToPush_selectedRowSendsNothing() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.Linked(ReminderChannel.Push))
        val c = ReminderDeliveryChannelController(link, RecordingAnalytics(), SilentLogger, backgroundScope, web = true)
        runCurrent()
        c.onWebClick(on = false) // "Don't send" already selected
        runCurrent()
        assertEquals(emptyList(), link.calls)
        c.onWebClick(on = true)
        runCurrent()
        assertEquals(listOf(ReminderChannel.Both), link.calls) // never `telegram` from the web
    }

    @Test
    fun notLinked_tapIsIgnored_rowIsConnect() = runTest {
        val link = FakeLink(TelegramLinkKnowledge.NotLinked)
        val c = controller(link)
        assertIs<ReminderDeliveryRowUi.Connect>(c.delivery.value)
        c.onChannelClick(ReminderChannel.Telegram)
        runCurrent()
        assertEquals(emptyList(), link.calls)
    }

    @Test
    fun rowTapped_reportsStateActionSurface() = runTest {
        val analytics = RecordingAnalytics()
        val c = controller(FakeLink(TelegramLinkKnowledge.SignedOut), analytics)
        c.onRowTapped("connect", "inbox_task")
        assertEquals(
            "telegram_reminder_row_tapped" to mapOf<String, Any>("state" to "signed_out", "action" to "connect", "surface" to "inbox_task"),
            analytics.events.single(),
        )
    }
}
