package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.common.api.ReminderChannelSaveResult
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramFailureReason
import com.antonchuraev.homesearchchecklist.core.common.api.TelegramLinkKnowledge
import com.antonchuraev.homesearchchecklist.core.datastore.api.HintsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderDeliveryPrefsRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkCacheRepository
import com.antonchuraev.homesearchchecklist.core.datastore.api.TelegramLinkSnapshot
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TelegramLinkCacheTest {

    private val prefs = FakeReminderPrefs()
    private val cache = FakeLinkCache()
    private val hints = FakeHints()
    private val writer = TelegramLinkCacheWriter(prefs, cache, hints, SilentLogger())

    // ── Writer ──────────────────────────────────────────────────────────────

    @Test
    fun status_notLinked_writesNotLinked_andDoesNotRetirePromo() = runTest {
        writer.onStatus("uid-a", TelegramLinkStatus.NotLinked)
        assertEquals(TelegramLinkSnapshot("uid-a", linked = false), cache.state.value)
        assertEquals(ReminderChannel.Push, prefs.channel)
        assertFalse(hints.retired.value)
    }

    @Test
    fun status_linked_writesLinkedWithChannel_andRetiresPromo() = runTest {
        writer.onStatus("uid-a", linked(ReminderChannel.Telegram))
        assertEquals(TelegramLinkSnapshot("uid-a", true, ReminderChannel.Telegram), cache.state.value)
        assertEquals(ReminderChannel.Telegram, prefs.channel)
        assertTrue(hints.retired.value)
    }

    @Test
    fun status_linkedOnPrePhase4Server_keepsChannelNotReported_receiverStillPush() = runTest {
        writer.onStatus("uid-a", linked(null))
        assertEquals(TelegramLinkSnapshot("uid-a", true, null), cache.state.value)
        assertEquals(ReminderChannel.Push, prefs.channel)
    }

    @Test
    fun claim_writesLinked_andRetiresPromo_withoutTouchingReceiverChannel() = runTest {
        prefs.channel = ReminderChannel.Both
        writer.onClaimed("uid-a", linked(null))
        assertEquals(TelegramLinkSnapshot("uid-a", true, null), cache.state.value)
        assertTrue(hints.retired.value)
        assertEquals(ReminderChannel.Both, prefs.channel)
    }

    @Test
    fun unlink_writesNotLinked_receiverPush_promoStaysRetired() = runTest {
        writer.onStatus("uid-a", linked(ReminderChannel.Both))
        writer.onUnlinked("uid-a")
        assertEquals(TelegramLinkSnapshot("uid-a", linked = false), cache.state.value)
        assertEquals(ReminderChannel.Push, prefs.channel)
        assertTrue(hints.retired.value)
    }

    @Test
    fun channelSaved_updatesLinkedSnapshotOfSameAccount() = runTest {
        writer.onStatus("uid-a", linked(ReminderChannel.Push))
        writer.onChannelSaved("uid-a", ReminderChannel.Both)
        assertEquals(TelegramLinkSnapshot("uid-a", true, ReminderChannel.Both), cache.state.value)
        assertEquals(ReminderChannel.Both, prefs.channel)
    }

    @Test
    fun channelSaved_neverTurnsNotLinkedOrOtherAccountIntoLinked() = runTest {
        writer.onStatus("uid-a", TelegramLinkStatus.NotLinked)
        writer.onChannelSaved("uid-a", ReminderChannel.Both)
        assertEquals(TelegramLinkSnapshot("uid-a", linked = false), cache.state.value)

        writer.onStatus("uid-b", linked(ReminderChannel.Push))
        writer.onChannelSaved("uid-a", ReminderChannel.Telegram)
        assertEquals(TelegramLinkSnapshot("uid-b", true, ReminderChannel.Push), cache.state.value)
    }

    /** Bot `/reminders` → FCM `reminder_channel`: the sheet must not keep showing the old channel. */
    @Test
    fun channelPushed_updatesLinkedSnapshotOfSameAccount_only() = runTest {
        writer.onStatus("uid-a", linked(ReminderChannel.Both))
        writer.onChannelPushed("uid-a", ReminderChannel.Telegram)
        assertEquals(TelegramLinkSnapshot("uid-a", true, ReminderChannel.Telegram), cache.state.value)
        assertEquals(ReminderChannel.Telegram, prefs.channel)

        writer.onChannelPushed("uid-b", ReminderChannel.Push)
        writer.onChannelPushed(null, ReminderChannel.Push)
        assertEquals(TelegramLinkSnapshot("uid-a", true, ReminderChannel.Telegram), cache.state.value)

        writer.onStatus("uid-a", TelegramLinkStatus.NotLinked)
        writer.onChannelPushed("uid-a", ReminderChannel.Both)
        assertEquals(TelegramLinkSnapshot("uid-a", linked = false), cache.state.value)
    }

    @Test
    fun nullAccount_writesNoSnapshot() = runTest {
        writer.onStatus(null, linked(ReminderChannel.Telegram))
        assertNull(cache.state.value)
    }

    @Test
    fun failingCacheWrite_doesNotThrow_andOtherCachesStillWritten() = runTest {
        cache.failWrites = true
        writer.onStatus("uid-a", linked(ReminderChannel.Telegram))
        assertEquals(ReminderChannel.Telegram, prefs.channel)
        assertTrue(hints.retired.value)
    }

    // ── Knowledge ───────────────────────────────────────────────────────────

    @Test
    fun knowledge_table() {
        val linkedA = TelegramLinkSnapshot("uid-a", true, ReminderChannel.Both)
        val notLinkedA = TelegramLinkSnapshot("uid-a", linked = false)
        assertEquals(TelegramLinkKnowledge.SignedOut, telegramLinkKnowledge(TelegramAuthPhase.SignedOut, null, linkedA))
        assertEquals(TelegramLinkKnowledge.Unknown, telegramLinkKnowledge(TelegramAuthPhase.SigningIn, "uid-a", linkedA))
        assertEquals(TelegramLinkKnowledge.Unknown, telegramLinkKnowledge(TelegramAuthPhase.SignedIn, "uid-a", null))
        assertEquals(TelegramLinkKnowledge.Unknown, telegramLinkKnowledge(TelegramAuthPhase.SignedIn, "uid-b", linkedA))
        assertEquals(TelegramLinkKnowledge.Unknown, telegramLinkKnowledge(TelegramAuthPhase.SignedIn, null, linkedA))
        assertEquals(TelegramLinkKnowledge.NotLinked, telegramLinkKnowledge(TelegramAuthPhase.SignedIn, "uid-a", notLinkedA))
        assertEquals(
            TelegramLinkKnowledge.Linked(ReminderChannel.Both),
            telegramLinkKnowledge(TelegramAuthPhase.SignedIn, "uid-a", linkedA),
        )
    }

    @Test
    fun telegramLink_followsAuthAndCache() = runTest {
        val phase = MutableStateFlow(TelegramAuthPhase.SignedOut)
        val uid = MutableStateFlow<String?>(null)
        val link = TelegramReminderLinkImpl(FakeTelegramLinkRepository(), cache, phase, uid, backgroundScope)
        assertEquals(TelegramLinkKnowledge.SignedOut, link.telegramLink.first())

        phase.value = TelegramAuthPhase.SignedIn
        uid.value = "uid-a"
        assertEquals(TelegramLinkKnowledge.Unknown, link.telegramLink.first())

        cache.state.value = TelegramLinkSnapshot("uid-a", true, ReminderChannel.Telegram)
        assertEquals(TelegramLinkKnowledge.Linked(ReminderChannel.Telegram), link.telegramLink.first())
    }

    // ── Save ────────────────────────────────────────────────────────────────

    @Test
    fun save_mapsSuccessAndFailureReasons() = runTest {
        val repo = FakeTelegramLinkRepository()
        val link = TelegramReminderLinkImpl(repo, cache, flowOf(TelegramAuthPhase.SignedIn), flowOf("uid-a"), backgroundScope)
        assertEquals(ReminderChannelSaveResult.Saved(ReminderChannel.Both), link.setReminderChannel(ReminderChannel.Both))

        repo.channelResult = { TelegramAccountResult.Failure(TelegramAccountError.Network(RuntimeException("offline"))) }
        assertEquals(ReminderChannelSaveResult.Failed(TelegramFailureReason.Network), link.setReminderChannel(ReminderChannel.Push))
        repo.channelResult = { TelegramAccountResult.Failure(TelegramAccountError.NoAccount) }
        assertEquals(ReminderChannelSaveResult.Failed(TelegramFailureReason.Account), link.setReminderChannel(ReminderChannel.Push))
        repo.channelResult = { TelegramAccountResult.Failure(TelegramAccountError.Server(500, null)) }
        assertEquals(ReminderChannelSaveResult.Failed(TelegramFailureReason.Server), link.setReminderChannel(ReminderChannel.Push))
    }

    /** Pitfall 2: closing the sheet / clearing its ViewModel must not cancel the server write. */
    @Test
    fun save_survivesCallerCancellation() = runTest {
        val fake = FakeTelegramLinkRepository()
        val gate = CompletableDeferred<TelegramAccountResult<ReminderChannel>>()
        fake.channelGates += gate
        val repo = CompletionRecordingRepository(fake)
        val link = TelegramReminderLinkImpl(repo, cache, flowOf(TelegramAuthPhase.SignedIn), flowOf("uid-a"), backgroundScope)

        val caller = launch { link.setReminderChannel(ReminderChannel.Telegram) }
        runCurrent()
        assertEquals(listOf(ReminderChannel.Telegram), fake.channelRequests)
        caller.cancel()
        runCurrent()

        // The server answers after the caller is gone: the write still runs to completion (in the
        // real repository, that is where the caches are updated).
        gate.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        runCurrent()
        assertTrue(caller.isCancelled)
        assertEquals(listOf(ReminderChannel.Telegram), repo.completed)
    }

    @Test
    fun save_serializesWritesInCallOrder() = runTest {
        val repo = FakeTelegramLinkRepository()
        val first = CompletableDeferred<TelegramAccountResult<ReminderChannel>>()
        repo.channelGates += first
        val link = TelegramReminderLinkImpl(repo, cache, flowOf(TelegramAuthPhase.SignedIn), flowOf("uid-a"), backgroundScope)

        val a = async { link.setReminderChannel(ReminderChannel.Telegram) }
        val b = async { link.setReminderChannel(ReminderChannel.Both) }
        runCurrent()
        // The second write waits until the first one is answered.
        assertEquals(listOf(ReminderChannel.Telegram), repo.channelRequests)

        first.complete(TelegramAccountResult.Success(ReminderChannel.Telegram))
        assertEquals(ReminderChannelSaveResult.Saved(ReminderChannel.Telegram), a.await())
        assertEquals(ReminderChannelSaveResult.Saved(ReminderChannel.Both), b.await())
        assertEquals(listOf(ReminderChannel.Telegram, ReminderChannel.Both), repo.channelRequests)
    }

    private fun linked(channel: ReminderChannel?) =
        TelegramLinkStatus.Linked(username = "anton", firstName = "Anton", linkedAtMs = null, reminderChannel = channel)
}

/** Records each `setReminderChannel` that ran to its end (not cancelled midway). */
private class CompletionRecordingRepository(
    private val delegate: FakeTelegramLinkRepository,
) : TelegramLinkRepository by delegate {
    val completed = mutableListOf<ReminderChannel>()
    override suspend fun setReminderChannel(channel: ReminderChannel): TelegramAccountResult<ReminderChannel> =
        delegate.setReminderChannel(channel).also { completed += channel }
}

private class FakeReminderPrefs : ReminderDeliveryPrefsRepository {
    var channel: ReminderChannel = ReminderChannel.Push
    private var zoneKey: String? = null
    override suspend fun getReminderChannel() = channel
    override suspend fun setReminderChannel(channel: ReminderChannel) {
        this.channel = channel
    }
    override suspend fun getLastWrittenTimezoneKey() = zoneKey
    override suspend fun setLastWrittenTimezoneKey(key: String) {
        zoneKey = key
    }
}

private class FakeLinkCache : TelegramLinkCacheRepository {
    val state = MutableStateFlow<TelegramLinkSnapshot?>(null)
    var failWrites = false
    override val snapshot: Flow<TelegramLinkSnapshot?> = state
    override suspend fun write(snapshot: TelegramLinkSnapshot) {
        if (failWrites) error("disk full")
        state.value = snapshot
    }
}

private class FakeHints : HintsRepository {
    val retired = MutableStateFlow(false)
    override val hamburgerHintShown: Flow<Boolean> = MutableStateFlow(false)
    override suspend fun markHamburgerHintShown() = Unit
    override val syncBannerDismissCount: Flow<Int> = MutableStateFlow(0)
    override suspend fun incrementSyncBannerDismissCount() = Unit
    override val telegramPromoRetired: Flow<Boolean> = retired
    override suspend fun markTelegramPromoRetired() {
        retired.value = true
    }
}

private class SilentLogger : AppLogger {
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {}
    override fun error(tag: String, message: String, throwable: Throwable?) {}
}
