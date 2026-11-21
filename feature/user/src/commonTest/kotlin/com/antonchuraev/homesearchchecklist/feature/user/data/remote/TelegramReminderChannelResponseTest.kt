package com.antonchuraev.homesearchchecklist.feature.user.data.remote

import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Phase-4 fields of CF `telegram_account` (contract 2026-11-20). */
class TelegramReminderChannelResponseTest {

    private fun status(body: String) = parseTelegramAccountResponse(200, body, ::decodeTelegramStatus)

    private fun setChannel(code: Int, body: String, requested: ReminderChannel = ReminderChannel.Telegram) =
        parseTelegramAccountResponse(code, body) { decodeReminderChannelReply(it, requested) }

    @Test
    fun status_phase4Server_carriesChannelAndZone() {
        val result = status(
            """{"success":true,"linked":true,"telegram_username":"anton","reminder_channel":"both","timezone":"Europe/Berlin"}""",
        )
        assertEquals(
            TelegramAccountResult.Success(
                TelegramLinkStatus.Linked("anton", null, null, ReminderChannel.Both, "Europe/Berlin"),
            ),
            result,
        )
    }

    /** The rollout guard: a pre-phase-4 server sends no `reminder_channel` → null → section hidden. */
    @Test
    fun status_withoutChannelField_isNull_notPush() {
        val linked = (status("""{"success":true,"linked":true,"telegram_username":"anton"}""")
            as TelegramAccountResult.Success).value as TelegramLinkStatus.Linked
        assertEquals(null, linked.reminderChannel)
        assertEquals(null, linked.timezone)
    }

    @Test
    fun status_unknownChannelValue_isNull() {
        val linked = (status("""{"success":true,"linked":true,"reminder_channel":"sms"}""")
            as TelegramAccountResult.Success).value as TelegramLinkStatus.Linked
        assertEquals(null, linked.reminderChannel)
    }

    @Test
    fun setChannel_echoedValue_wins() {
        assertEquals(
            TelegramAccountResult.Success(ReminderChannel.Push),
            setChannel(200, """{"success":true,"reminder_channel":"push"}"""),
        )
    }

    @Test
    fun setChannel_noEcho_meansTheRequestedValueWasStored() {
        assertEquals(
            TelegramAccountResult.Success(ReminderChannel.Telegram),
            setChannel(200, """{"success":true}"""),
        )
    }

    @Test
    fun setChannel_unknownEcho_isServerFailure() {
        assertEquals(
            TelegramAccountResult.Failure(TelegramAccountError.Server(200, "malformed_body")),
            setChannel(200, """{"success":true,"reminder_channel":"sms"}"""),
        )
    }

    @Test
    fun setChannel_invalidTimezone_is400ServerWithCode() {
        assertEquals(
            TelegramAccountResult.Failure(
                TelegramAccountError.Server(400, TelegramAccountApiService.ERROR_INVALID_TIMEZONE),
            ),
            setChannel(400, """{"success":false,"error":"invalid_timezone"}"""),
        )
    }

    @Test
    fun requestBody_omitsNullTimezone() {
        assertEquals(
            """{"action":"set_reminder_channel","channel":"both"}""",
            buildTelegramRequestBody("set_reminder_channel", mapOf("channel" to "both", "timezone" to null)),
        )
    }
}
