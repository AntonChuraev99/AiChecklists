package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail.weekly

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeeklyHelpersTest {

    // ── weeklyOrderFromToday — rolling order from today through full week ──

    @Test
    fun weeklyOrderFromToday_monday_returnsMonToSun() {
        val expected = listOf(1, 2, 3, 4, 5, 6, 7)
        assertEquals(expected, weeklyOrderFromToday(1))
    }

    @Test
    fun weeklyOrderFromToday_friday_returnsFriThruThu() {
        // Today=Fri(5) → Fri, Sat, Sun, Mon, Tue, Wed, Thu
        val expected = listOf(5, 6, 7, 1, 2, 3, 4)
        assertEquals(expected, weeklyOrderFromToday(5))
    }

    @Test
    fun weeklyOrderFromToday_sunday_returnsSunThruSat() {
        // Today=Sun(7) → Sun, Mon, Tue, Wed, Thu, Fri, Sat
        val expected = listOf(7, 1, 2, 3, 4, 5, 6)
        assertEquals(expected, weeklyOrderFromToday(7))
    }

    @Test
    fun weeklyOrderFromToday_alwaysReturnsSevenDays() {
        for (today in 1..7) {
            assertEquals(7, weeklyOrderFromToday(today).size, "today=$today should yield 7 days")
        }
    }

    @Test
    fun weeklyOrderFromToday_allValuesInValidRange() {
        for (today in 1..7) {
            val order = weeklyOrderFromToday(today)
            order.forEach { day ->
                assertTrue(day in 1..7, "day $day from today=$today not in ISO range")
            }
        }
    }

    @Test
    fun weeklyOrderFromToday_noDuplicates() {
        for (today in 1..7) {
            val order = weeklyOrderFromToday(today)
            assertEquals(7, order.toSet().size, "today=$today has duplicates: $order")
        }
    }

    @Test
    fun weeklyOrderFromToday_firstElementIsToday() {
        for (today in 1..7) {
            assertEquals(today, weeklyOrderFromToday(today).first())
        }
    }

    @Test
    fun weeklyOrderFromToday_lastElementIsYesterday() {
        // Yesterday = today - 1, wrapping (Mon's yesterday = Sun)
        val expectedYesterday = mapOf(
            1 to 7, 2 to 1, 3 to 2, 4 to 3, 5 to 4, 6 to 5, 7 to 6
        )
        for (today in 1..7) {
            assertEquals(
                expectedYesterday[today],
                weeklyOrderFromToday(today).last(),
                "today=$today: last should be yesterday"
            )
        }
    }
}
