package com.antonchuraev.homesearchchecklist.activation

import com.antonchuraev.homesearchchecklist.core.common.api.ReminderTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** F4: the activation sheet's target survives process death as-is — never degrades to "the list". */
class ReminderTargetSaverTest {

    @Test
    fun itemTarget_roundTripsWithTextIntact() {
        val item = ReminderTarget.Item(checklistId = 7L, fillId = 70L, itemId = "a|b", text = "Buy milk, eggs | bread")
        assertEquals(item, reminderTargetFromSaveable(item.toSaveable()))
    }

    @Test
    fun checklistTarget_roundTrips() {
        val checklist = ReminderTarget.Checklist(42L)
        assertEquals(checklist, reminderTargetFromSaveable(checklist.toSaveable()))
    }

    @Test
    fun malformed_restoresNothing_neverAGuessedTarget() {
        assertNull(reminderTargetFromSaveable(emptyList()))
        assertNull(reminderTargetFromSaveable(listOf("item", "7", "70", "id"))) // text missing
        assertNull(reminderTargetFromSaveable(listOf("checklist", "x")))
        assertNull(reminderTargetFromSaveable(listOf("unknown", "1")))
    }
}
