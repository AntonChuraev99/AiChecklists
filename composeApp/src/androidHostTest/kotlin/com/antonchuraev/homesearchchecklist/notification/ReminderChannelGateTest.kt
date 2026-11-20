package com.antonchuraev.homesearchchecklist.notification

import com.antonchuraev.homesearchchecklist.core.datastore.api.ReminderChannel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Off-device unit test for [shouldPostReminderNotification] — the pure gate that decides whether a
 * fired reminder posts a notification on the phone. Only the Telegram channel suppresses it; an
 * absent or unknown channel behaves as Push.
 */
class ReminderChannelGateTest {

    @Test
    fun push_posts() {
        assertTrue(shouldPostReminderNotification(ReminderChannel.Push))
    }

    @Test
    fun both_posts() {
        assertTrue(shouldPostReminderNotification(ReminderChannel.Both))
    }

    @Test
    fun telegram_doesNotPost() {
        assertFalse(shouldPostReminderNotification(ReminderChannel.Telegram))
    }

    @Test
    fun absentChannel_postsLikePush() {
        assertTrue(shouldPostReminderNotification(null))
    }

    @Test
    fun unknownWireValue_postsLikePush() {
        assertTrue(shouldPostReminderNotification(ReminderChannel.fromWire("sms")))
        assertTrue(shouldPostReminderNotification(ReminderChannel.fromWire(null)))
    }
}
