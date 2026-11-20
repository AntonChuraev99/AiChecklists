package com.antonchuraev.homesearchchecklist.push

import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import kotlin.test.Test
import kotlin.test.assertEquals

/** Off-device test for [parseReminderChannelPush] — the reminder-channel control push parser. */
class ReminderChannelPushTest {

    private fun msg(channel: String?): Map<String, String> = buildMap {
        put("type", "reminder_channel")
        if (channel != null) put("channel", channel)
    }

    @Test
    fun validChannels_apply() {
        assertEquals(ReminderChannelPushDecision.Apply(ReminderChannel.Push), parseReminderChannelPush(msg("push")))
        assertEquals(ReminderChannelPushDecision.Apply(ReminderChannel.Telegram), parseReminderChannelPush(msg("telegram")))
        assertEquals(ReminderChannelPushDecision.Apply(ReminderChannel.Both), parseReminderChannelPush(msg("both")))
    }

    @Test
    fun unknownChannel_isIgnored() {
        assertEquals(ReminderChannelPushDecision.Ignore("sms"), parseReminderChannelPush(msg("sms")))
    }

    @Test
    fun missingChannel_isIgnored() {
        assertEquals(ReminderChannelPushDecision.Ignore(null), parseReminderChannelPush(msg(null)))
    }

    @Test
    fun regularRemindersPush_isNotAChannelUpdate() {
        // A campaign push also carries "channel" (reminders / promo) but no type=reminder_channel.
        val campaign = mapOf("channel" to "reminders", "title" to "t", "body" to "b", "push_type" to "comeback")
        assertEquals(ReminderChannelPushDecision.NotAChannelUpdate, parseReminderChannelPush(campaign))
    }

    @Test
    fun otherType_isNotAChannelUpdate() {
        assertEquals(
            ReminderChannelPushDecision.NotAChannelUpdate,
            parseReminderChannelPush(mapOf("type" to "something_else", "channel" to "telegram")),
        )
    }
}
