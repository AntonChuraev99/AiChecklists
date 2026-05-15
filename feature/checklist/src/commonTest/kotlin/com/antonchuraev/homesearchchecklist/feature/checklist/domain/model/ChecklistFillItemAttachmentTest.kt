package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * Unit tests for attachment helpers added to [ChecklistFillItem].
 *
 * Verified properties per test:
 * - Returns a NEW instance (immutability / @ConsistentCopyVisibility contract)
 * - The targeted field has the expected new value
 * - All unrelated fields are unchanged (id, text, checked, note, weekday, priority, reminder fields)
 * - JSON round-trip with ignoreUnknownKeys proves backward compatibility with old DB rows
 */
class ChecklistFillItemAttachmentTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun attachment(suffix: String = "1") = Attachment(
        id = "att_$suffix",
        path = "/data/user/0/com.example/files/attachments/1/item/$suffix.jpg",
        fileName = "photo_$suffix.jpg",
        mimeType = "image/jpeg",
        sizeBytes = 102_400L,
        createdAt = 1_700_000_000_000L,
    )

    private fun baseItem() = ChecklistFillItem(
        text = "Review docs",
        checked = false,
        note = "important",
        weekday = 3,
        priority = 1,
    ).withReminderAt(9_000L)

    // ── withAttachmentAdded ───────────────────────────────────────────────────

    @Test
    fun withAttachmentAdded_appendsToEmptyList() {
        val item = baseItem()
        val att = attachment("1")
        val updated = item.withAttachmentAdded(att)

        assertEquals(1, updated.attachments.size)
        assertEquals(att, updated.attachments[0])
    }

    @Test
    fun withAttachmentAdded_appendsToExistingList() {
        val att1 = attachment("1")
        val att2 = attachment("2")
        val item = baseItem().withAttachmentAdded(att1)
        val updated = item.withAttachmentAdded(att2)

        assertEquals(2, updated.attachments.size)
        assertEquals(att1, updated.attachments[0])
        assertEquals(att2, updated.attachments[1])
    }

    @Test
    fun withAttachmentAdded_returnsNewInstance() {
        val item = baseItem()
        val updated = item.withAttachmentAdded(attachment())

        assertNotSame(item, updated)
    }

    @Test
    fun withAttachmentAdded_preservesAllOtherFields() {
        val item = baseItem()
        val updated = item.withAttachmentAdded(attachment())

        assertEquals(item.id, updated.id)
        assertEquals(item.text, updated.text)
        assertEquals(item.checked, updated.checked)
        assertEquals(item.note, updated.note)
        assertEquals(item.weekday, updated.weekday)
        assertEquals(item.priority, updated.priority)
        assertEquals(item.reminderAt, updated.reminderAt)
        assertEquals(item.repeatRule, updated.repeatRule)
        assertEquals(item.repeatNextAt, updated.repeatNextAt)
        assertEquals(item.repeatOccurrenceCount, updated.repeatOccurrenceCount)
    }

    // ── withAttachmentRemoved ─────────────────────────────────────────────────

    @Test
    fun withAttachmentRemoved_removesById() {
        val att1 = attachment("1")
        val att2 = attachment("2")
        val item = baseItem().withAttachmentAdded(att1).withAttachmentAdded(att2)
        val updated = item.withAttachmentRemoved("att_1")

        assertEquals(1, updated.attachments.size)
        assertEquals(att2, updated.attachments[0])
    }

    @Test
    fun withAttachmentRemoved_preservesOrder() {
        val att1 = attachment("1")
        val att2 = attachment("2")
        val att3 = attachment("3")
        val item = baseItem()
            .withAttachmentAdded(att1)
            .withAttachmentAdded(att2)
            .withAttachmentAdded(att3)
        val updated = item.withAttachmentRemoved("att_2")

        assertEquals(2, updated.attachments.size)
        assertEquals(att1, updated.attachments[0])
        assertEquals(att3, updated.attachments[1])
    }

    @Test
    fun withAttachmentRemoved_unknownId_returnsSameList() {
        val att = attachment("1")
        val item = baseItem().withAttachmentAdded(att)
        val updated = item.withAttachmentRemoved("att_nonexistent")

        assertEquals(1, updated.attachments.size)
        assertEquals(att, updated.attachments[0])
    }

    @Test
    fun withAttachmentRemoved_emptyList_noException() {
        val item = baseItem()
        val updated = item.withAttachmentRemoved("att_x")

        assertTrue(updated.attachments.isEmpty())
    }

    // ── withAttachments ───────────────────────────────────────────────────────

    @Test
    fun withAttachments_replacesEntirely() {
        val att1 = attachment("1")
        val att2 = attachment("2")
        val item = baseItem().withAttachmentAdded(att1)
        val updated = item.withAttachments(listOf(att2))

        assertEquals(1, updated.attachments.size)
        assertEquals(att2, updated.attachments[0])
    }

    @Test
    fun withAttachments_emptyListClearsAll() {
        val item = baseItem().withAttachmentAdded(attachment())
        val updated = item.withAttachments(emptyList())

        assertTrue(updated.attachments.isEmpty())
    }

    // ── Existing with*() helpers preserve attachments ─────────────────────────

    @Test
    fun withChecked_preservesAttachments() {
        val att = attachment()
        val item = baseItem().withAttachmentAdded(att)
        val updated = item.withChecked(true)

        assertEquals(listOf(att), updated.attachments)
        assertEquals(item.id, updated.id)
    }

    @Test
    fun withNote_preservesAttachments() {
        val att = attachment()
        val item = baseItem().withAttachmentAdded(att)
        val updated = item.withNote("updated note")

        assertEquals(listOf(att), updated.attachments)
    }

    @Test
    fun withText_preservesAttachments() {
        val att = attachment()
        val item = baseItem().withAttachmentAdded(att)
        val updated = item.withText("new text")

        assertEquals(listOf(att), updated.attachments)
    }

    @Test
    fun withPriority_preservesAttachments() {
        val att = attachment()
        val item = baseItem().withAttachmentAdded(att)
        val updated = item.withPriority(0)

        assertEquals(listOf(att), updated.attachments)
    }

    @Test
    fun withReminderCleared_preservesAttachments() {
        val att = attachment()
        val item = baseItem().withAttachmentAdded(att)
        val updated = item.withReminderCleared()

        assertEquals(listOf(att), updated.attachments)
    }

    // ── JSON backward-compat round-trip ──────────────────────────────────────

    @Test
    fun jsonRoundTrip_oldRowWithoutAttachments_defaultsToEmptyList() {
        // Simulate a DB row serialized BEFORE attachments field existed.
        // The JSON has no "attachments" key — with ignoreUnknownKeys=true AND default value,
        // the decoded object must produce an empty list.
        val oldJson = """[{"text":"Buy milk","checked":false,"note":null,"id":"123_1","weekday":null,"priority":0,"reminderAt":null,"repeatRule":null,"repeatTimeOfDayMinutes":null,"repeatNextAt":null,"repeatOccurrenceCount":0}]"""

        val json = Json { ignoreUnknownKeys = true }
        val decoded = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), oldJson)

        assertEquals(1, decoded.size)
        assertTrue(decoded[0].attachments.isEmpty(), "Old DB rows without 'attachments' must decode to emptyList()")
    }

    @Test
    fun jsonRoundTrip_newRowWithAttachments_preservesData() {
        val att = attachment()
        val item = ChecklistFillItem(text = "Task", checked = false).withAttachmentAdded(att)

        val json = Json { ignoreUnknownKeys = true }
        val encoded = json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), listOf(item))
        val decoded = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), encoded)

        assertEquals(1, decoded.size)
        assertEquals(1, decoded[0].attachments.size)
        assertEquals(att.id, decoded[0].attachments[0].id)
        assertEquals(att.path, decoded[0].attachments[0].path)
        assertEquals(att.mimeType, decoded[0].attachments[0].mimeType)
    }
}
