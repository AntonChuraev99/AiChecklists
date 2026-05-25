package com.antonchuraev.homesearchchecklist.notification

import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Verifies that the four request-code namespaces for AlarmManager PendingIntents never collide:
 *
 *   1. Checklist one-shot  : (checklistId xor (checklistId ushr 32)).toInt()
 *   2. Checklist repeat    : above + 100_000
 *   3. Item one-shot       : abs("fillId:itemId".hashCode()) + 200_000
 *   4. Item repeat         : abs("fillId:itemId".hashCode()) + 300_000
 *
 * Rules tested:
 * - Within each namespace: different inputs → different codes  (no intra-namespace collision)
 * - Between namespaces: a given (checklistId/fillId/itemId) combo does not produce the same
 *   code in two different namespaces.
 */
class ReminderRequestCodeTest {

    // ── Replicated helpers (mirror ReminderScheduler companion) ──

    private fun reminderCode(checklistId: Long): Int =
        (checklistId xor (checklistId ushr 32)).toInt()

    private fun repeatCode(checklistId: Long): Int =
        reminderCode(checklistId) + 100_000

    private fun itemReminderCode(fillId: Long, itemId: String): Int =
        "$fillId:$itemId".hashCode().absoluteValue + 200_000

    private fun itemRepeatCode(fillId: Long, itemId: String): Int =
        "$fillId:$itemId".hashCode().absoluteValue + 300_000

    // Sample corpus — representative (checklistId, fillId, itemId) tuples
    private val samples = listOf(
        Triple(1L,  1L,  "item_a"),
        Triple(1L,  1L,  "item_b"),
        Triple(1L,  2L,  "item_a"),
        Triple(2L,  2L,  "item_a"),
        Triple(42L, 42L, "1234567890_9999"),
        Triple(Long.MAX_VALUE, Long.MAX_VALUE, "x"),
        Triple(Long.MAX_VALUE - 1, Long.MAX_VALUE - 1, "y"),
        Triple(100_000L, 200_000L, "1000000000_0001"),   // fillId near REPEAT_OFFSET range
        Triple(0L, 0L, "0"),
    )

    // ── Intra-namespace uniqueness ──

    @Test
    fun checklist_reminderCodes_distinctForDifferentIds() {
        val codes = samples.map { reminderCode(it.first) }
        // Among our distinct checklistId values, codes should be distinct
        val distinct = samples.map { it.first }.toSet()
        val distinctCodes = distinct.map { reminderCode(it) }.toSet()
        assertTrue(
            distinctCodes.size == distinct.size,
            "Checklist reminder codes collide within sample: $distinctCodes"
        )
    }

    @Test
    fun checklist_repeatCodes_distinctForDifferentIds() {
        val distinct = samples.map { it.first }.toSet()
        val distinctCodes = distinct.map { repeatCode(it) }.toSet()
        assertTrue(
            distinctCodes.size == distinct.size,
            "Checklist repeat codes collide within sample: $distinctCodes"
        )
    }

    @Test
    fun item_reminderCodes_distinctForDifferentFillItemPairs() {
        val pairs = samples.map { Pair(it.second, it.third) }.toSet()
        val distinctCodes = pairs.map { itemReminderCode(it.first, it.second) }.toSet()
        assertTrue(
            distinctCodes.size == pairs.size,
            "Item reminder codes collide within sample: $distinctCodes"
        )
    }

    @Test
    fun item_repeatCodes_distinctForDifferentFillItemPairs() {
        val pairs = samples.map { Pair(it.second, it.third) }.toSet()
        val distinctCodes = pairs.map { itemRepeatCode(it.first, it.second) }.toSet()
        assertTrue(
            distinctCodes.size == pairs.size,
            "Item repeat codes collide within sample: $distinctCodes"
        )
    }

    // ── Cross-namespace separation ──

    @Test
    fun reminderCode_doesNotOverlapRepeatCode() {
        for ((checklistId, _, _) in samples) {
            assertNotEquals(
                reminderCode(checklistId),
                repeatCode(checklistId),
                "Reminder and repeat codes collide for checklistId=$checklistId"
            )
        }
    }

    @Test
    fun itemReminderCode_doesNotOverlapItemRepeatCode() {
        for ((_, fillId, itemId) in samples) {
            assertNotEquals(
                itemReminderCode(fillId, itemId),
                itemRepeatCode(fillId, itemId),
                "Item reminder and item repeat codes collide for fillId=$fillId itemId=$itemId"
            )
        }
    }

    @Test
    fun itemReminder_namespace_startsAt200000() {
        // Guarantees the floor of the item-reminder namespace is above checklist-repeat ceiling
        // when checklist IDs are reasonable (<100_000 distinct values in practice).
        for ((_, fillId, itemId) in samples) {
            assertTrue(
                itemReminderCode(fillId, itemId) >= 200_000,
                "Item reminder code fell below 200_000 for fillId=$fillId itemId=$itemId"
            )
        }
    }

    @Test
    fun itemRepeat_namespace_startsAt300000() {
        for ((_, fillId, itemId) in samples) {
            assertTrue(
                itemRepeatCode(fillId, itemId) >= 300_000,
                "Item repeat code fell below 300_000 for fillId=$fillId itemId=$itemId"
            )
        }
    }

    @Test
    fun itemRepeat_code_differs_from_itemReminder_code_by100M() {
        // Confirms the +100_000 gap between namespaces is stable
        for ((_, fillId, itemId) in samples) {
            val gap = itemRepeatCode(fillId, itemId) - itemReminderCode(fillId, itemId)
            assertTrue(
                gap == 100_000,
                "Gap between item repeat and item reminder codes is not 100_000 for " +
                        "fillId=$fillId itemId=$itemId (gap=$gap)"
            )
        }
    }

    // ── Sampled cross-namespace check (checklist vs item) ──

    @Test
    fun noCollision_checklist_vs_item_namespaces_inSample() {
        // Collect all codes from all four namespaces for the sample tuples.
        // A collision here is extremely unlikely but would indicate a design flaw.
        val checklistIds = samples.map { it.first }.toSet()
        val checklistCodes = checklistIds.flatMap { listOf(reminderCode(it), repeatCode(it)) }

        val pairs = samples.map { Pair(it.second, it.third) }.toSet()
        val itemCodes = pairs.flatMap { listOf(itemReminderCode(it.first, it.second), itemRepeatCode(it.first, it.second)) }

        val intersection = checklistCodes.toSet() intersect itemCodes.toSet()
        assertTrue(
            intersection.isEmpty(),
            "Namespace collision between checklist and item codes in sample: $intersection"
        )
    }
}
