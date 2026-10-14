package com.antonchuraev.homesearchchecklist.activation

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals

class ActivationReminderOptionsTest {

    private val zone = TimeZone.of("Europe/Moscow")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        LocalDateTime(y, mo, d, h, mi).toInstant(zone).toEpochMilliseconds()

    @Test
    fun beforeEvening_defaultsToTodayEight_thenTomorrowNine() {
        val options = activationReminderOptions(at(2026, 10, 14, 19, 59), zone)

        assertEquals(listOf(ReminderOptionDay.Today, ReminderOptionDay.Tomorrow), options.map { it.day })
        assertEquals(listOf(20, 9), options.map { it.hour })
        assertEquals(listOf(at(2026, 10, 14, 20), at(2026, 10, 15, 9)), options.map { it.triggerAtMs })
    }

    @Test
    fun fromEvening_defaultsToTomorrowNine_thenTomorrowEight() {
        val options = activationReminderOptions(at(2026, 10, 14, 20, 0), zone)

        assertEquals(listOf(ReminderOptionDay.Tomorrow, ReminderOptionDay.Tomorrow), options.map { it.day })
        assertEquals(listOf(at(2026, 10, 15, 9), at(2026, 10, 15, 20)), options.map { it.triggerAtMs })
    }

    @Test
    fun lastDayOfMonth_tomorrowRollsOver() {
        val options = activationReminderOptions(at(2026, 10, 31, 22), zone)
        assertEquals(at(2026, 11, 1, 9), options.first().triggerAtMs)
    }

    @Test
    fun clockLabel_isZeroPadded() {
        assertEquals("09:00", formatReminderClock(9, 0))
        assertEquals("20:05", formatReminderClock(20, 5))
    }
}
