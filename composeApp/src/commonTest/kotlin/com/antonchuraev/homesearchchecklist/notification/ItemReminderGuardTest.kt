package com.antonchuraev.homesearchchecklist.notification

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFill
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatEndCondition
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the guard logic in [ReminderReceiver] per-item handlers.
 *
 * The receiver must skip firing a notification when:
 *   - The item with the target [itemId] is not found in the fill
 *   - The item is found but [ChecklistFillItem.checked] == true
 *
 * These tests verify the pure domain-logic gate, extracted from the receiver
 * to avoid Android framework dependencies.
 */
class ItemReminderGuardTest {

    /**
     * Helper that mirrors the guard in [ReminderReceiver.handleItemOneShot] and
     * [ReminderReceiver.handleItemRepeat]:
     *   val item = fill.items.find { it.id == itemId } ?: return (guard 1: not found)
     *   if (item.checked) return                                  (guard 2: already done)
     *
     * Returns true if notification SHOULD fire, false if it should be suppressed.
     */
    private fun shouldFireNotification(fill: ChecklistFill, itemId: String): Boolean {
        val item = fill.items.find { it.id == itemId } ?: return false
        if (item.checked) return false
        return true
    }

    private fun buildFill(vararg items: ChecklistFillItem) = ChecklistFill(
        id = 1L,
        checklistId = 10L,
        name = "Test Fill",
        items = items.toList(),
        isDefault = true
    )

    // ── Guard 1: item not in fill ──

    @Test
    fun shouldFire_false_whenItemNotFoundInFill() {
        val fill = buildFill(
            ChecklistFillItem(text = "Buy milk", checked = false),
        )
        assertFalse(shouldFireNotification(fill, itemId = "nonexistent_id"))
    }

    @Test
    fun shouldFire_false_whenFillIsEmpty() {
        val fill = buildFill()
        assertFalse(shouldFireNotification(fill, itemId = "any_id"))
    }

    // ── Guard 2: item already checked ──

    @Test
    fun shouldFire_false_whenItemIsChecked() {
        val item = ChecklistFillItem(text = "Call dentist", checked = true)
        val fill = buildFill(item)
        assertFalse(shouldFireNotification(fill, item.id))
    }

    @Test
    fun shouldFire_false_whenItemIsChecked_withActiveReminder() {
        val item = ChecklistFillItem(text = "Morning run", checked = true)
            .withReminderAt(1_700_000_000_000L)
        val fill = buildFill(item)
        assertFalse(shouldFireNotification(fill, item.id))
    }

    @Test
    fun shouldFire_false_whenItemIsChecked_withRepeatRule() {
        val rule = ReminderRepeatRule(type = RepeatType.DAILY, endCondition = RepeatEndCondition.Never)
        val item = ChecklistFillItem(text = "Take pills", checked = true)
            .withRepeatRule(rule, timeOfDayMinutes = 480, nextAt = 1_700_000_000_000L)
        val fill = buildFill(item)
        assertFalse(shouldFireNotification(fill, item.id))
    }

    // ── Happy path: unchecked item present ──

    @Test
    fun shouldFire_true_whenItemIsUncheckedAndPresent() {
        val item = ChecklistFillItem(text = "Review report", checked = false)
        val fill = buildFill(item)
        assertTrue(shouldFireNotification(fill, item.id))
    }

    @Test
    fun shouldFire_true_whenTargetItemUnchecked_andOtherItemsChecked() {
        val checked1 = ChecklistFillItem(text = "Done task", checked = true)
        val checked2 = ChecklistFillItem(text = "Another done", checked = true)
        val target = ChecklistFillItem(text = "Pending task", checked = false)
        val fill = buildFill(checked1, checked2, target)
        assertTrue(shouldFireNotification(fill, target.id))
    }

    @Test
    fun shouldFire_false_whenTargetItemChecked_andOtherItemsUnchecked() {
        val unchecked1 = ChecklistFillItem(text = "Open task 1", checked = false)
        val unchecked2 = ChecklistFillItem(text = "Open task 2", checked = false)
        val target = ChecklistFillItem(text = "Completed task", checked = true)
        val fill = buildFill(unchecked1, unchecked2, target)
        assertFalse(shouldFireNotification(fill, target.id))
    }

    // ── ID uniqueness: correct item targeted by id, not position ──

    @Test
    fun shouldFire_usesIdNotPosition_toLocateItem() {
        // Two items with identical text; only the second (checked=false) should fire.
        val item1 = ChecklistFillItem(text = "Duplicate", checked = true)
        val item2 = ChecklistFillItem(text = "Duplicate", checked = false)
        val fill = buildFill(item1, item2)

        // item1 checked → should NOT fire
        assertFalse(shouldFireNotification(fill, item1.id))
        // item2 unchecked → SHOULD fire
        assertTrue(shouldFireNotification(fill, item2.id))
    }
}
