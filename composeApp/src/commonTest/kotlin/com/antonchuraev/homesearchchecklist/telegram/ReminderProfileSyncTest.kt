package com.antonchuraev.homesearchchecklist.telegram

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.common.api.AppResult
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderDeliveryPrefsRepository
import com.antonchuraev.homesearchchecklist.feature.checklist.data.sync.UserDocMergeUnsupported
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountError
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramAccountResult
import com.antonchuraev.homesearchchecklist.feature.user.data.remote.TelegramLinkStatus
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderProfileSyncTest {

    private val prefs = InMemoryReminderPrefs()
    private val logger = RecordingLogger()
    private val writes = mutableListOf<Pair<String, Map<String, String>>>()
    private var writeResult: AppResult<Unit> = AppResult.Success(Unit)
    private var refreshCalls = 0
    private var refreshResult: TelegramAccountResult<*> = TelegramAccountResult.Success(TelegramLinkStatus.NotLinked)
    private var zone: String? = "Europe/Berlin"
    private var now = 1_000_000L

    private fun TestScope.sync() = ReminderProfileSync(
        appScope = backgroundScope,
        identity = emptyFlow(),
        reminderPrefs = prefs,
        deviceTimeZone = { zone },
        writeUserDoc = { uid, fields -> writes += uid to fields; writeResult },
        refreshChannel = { refreshCalls++; refreshResult },
        logger = logger,
        nowMs = { now },
    )

    private val alice = ReminderIdentity.SignedIn("uid-alice")

    // ── Zone write ──────────────────────────────────────────────────────────

    @Test
    fun zone_writtenOnce_thenOnlyWhenItChanges() = runTest {
        val sync = sync()
        sync.runOnce(alice)
        sync.runOnce(alice)
        assertEquals(
            listOf("uid-alice" to mapOf("timezone" to "Europe/Berlin", "timezone_source" to "device")),
            writes,
        )

        zone = "Asia/Kolkata"
        sync.runOnce(alice)
        assertEquals(2, writes.size)
        assertEquals("Asia/Kolkata", writes.last().second["timezone"])
    }

    @Test
    fun zone_rewrittenForAnotherAccount() = runTest {
        val sync = sync()
        sync.runOnce(alice)
        sync.runOnce(ReminderIdentity.SignedIn("uid-bob"))
        assertEquals(listOf("uid-alice", "uid-bob"), writes.map { it.first })
    }

    @Test
    fun zone_failedWrite_isRetriedNextTime_andLogged() = runTest {
        val sync = sync()
        writeResult = AppResult.Error(Exception("offline"))
        sync.runOnce(alice)
        assertEquals(1, logger.errors.size)
        writeResult = AppResult.Success(Unit)
        sync.runOnce(alice)
        sync.runOnce(alice)
        assertEquals(2, writes.size, "retried once after the failure, then cached")
    }

    @Test
    fun zone_unsupportedPlatform_warnsOnce_noErrorSpam() = runTest {
        val sync = sync()
        writeResult = AppResult.Error(UserDocMergeUnsupported())
        sync.runOnce(alice)
        sync.runOnce(alice)
        assertTrue(logger.errors.isEmpty())
        assertEquals(1, logger.warnings.size)
    }

    @Test
    fun zone_unknownOnThisPlatform_writesNothing() = runTest {
        zone = null
        sync().runOnce(alice)
        assertTrue(writes.isEmpty())
    }

    @Test
    fun signedOutOrUnknown_writesNoZone() = runTest {
        val sync = sync()
        sync.runOnce(ReminderIdentity.Unknown)
        sync.runOnce(ReminderIdentity.SignedOut)
        assertTrue(writes.isEmpty())
        assertEquals(0, refreshCalls)
    }

    // ── Channel cache refresh ───────────────────────────────────────────────

    @Test
    fun channelRefresh_atMostOncePer15Minutes() = runTest {
        val sync = sync()
        sync.runOnce(alice)
        now += ReminderProfileSync.CHANNEL_REFRESH_MIN_MS - 1
        sync.runOnce(alice)
        assertEquals(1, refreshCalls)
        now += 1
        sync.runOnce(alice)
        assertEquals(2, refreshCalls)
    }

    @Test
    fun channelRefresh_noIdToken_isRetriedOnTheNextTick() = runTest {
        val sync = sync()
        refreshResult = TelegramAccountResult.Failure(TelegramAccountError.NotSignedIn)
        sync.runOnce(alice)
        sync.runOnce(alice)
        assertEquals(2, refreshCalls)
    }

    @Test
    fun signedOut_resetsCacheToPush_soThePhoneNotifiesAgain() = runTest {
        prefs.channel = ReminderChannel.Telegram
        sync().runOnce(ReminderIdentity.SignedOut)
        assertEquals(ReminderChannel.Push, prefs.channel)
    }

    // ── Pure helpers ────────────────────────────────────────────────────────

    @Test
    fun cachedChannel_fromStatus() {
        assertEquals(ReminderChannel.Push, cachedChannelFor(TelegramLinkStatus.NotLinked))
        assertEquals(ReminderChannel.Push, cachedChannelFor(TelegramLinkStatus.Linked("a", null, null, null)))
        assertEquals(
            ReminderChannel.Telegram,
            cachedChannelFor(TelegramLinkStatus.Linked("a", null, null, ReminderChannel.Telegram)),
        )
    }

    @Test
    fun ianaZone_acceptsAreaLocationAndUtc_rejectsSystemAndOffsets() {
        assertEquals("Europe/Berlin", ianaZoneOrNull("Europe/Berlin"))
        assertEquals("America/Argentina/Buenos_Aires", ianaZoneOrNull("America/Argentina/Buenos_Aires"))
        assertEquals("Etc/GMT+3", ianaZoneOrNull("Etc/GMT+3"))
        assertEquals("UTC", ianaZoneOrNull("UTC"))
        assertEquals(null, ianaZoneOrNull("SYSTEM"))
        assertEquals(null, ianaZoneOrNull("+03:00"))
        assertEquals(null, ianaZoneOrNull("GMT+3"))
        assertEquals(null, ianaZoneOrNull(""))
        assertEquals(null, ianaZoneOrNull(null))
    }
}

private class InMemoryReminderPrefs : ReminderDeliveryPrefsRepository {
    var channel: ReminderChannel = ReminderChannel.Push
    var zoneKey: String? = null
    override suspend fun getReminderChannel() = channel
    override suspend fun setReminderChannel(channel: ReminderChannel) {
        this.channel = channel
    }
    override suspend fun getLastWrittenTimezoneKey() = zoneKey
    override suspend fun setLastWrittenTimezoneKey(key: String) {
        zoneKey = key
    }
}

private class RecordingLogger : AppLogger {
    val errors = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    override fun debug(tag: String, message: String) {}
    override fun info(tag: String, message: String) {}
    override fun warning(tag: String, message: String) {
        warnings += message
    }
    override fun error(tag: String, message: String, throwable: Throwable?) {
        errors += message
    }
}
