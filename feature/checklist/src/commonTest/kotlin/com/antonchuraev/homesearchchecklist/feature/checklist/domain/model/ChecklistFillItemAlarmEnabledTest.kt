package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/**
 * The date/alarm split on [ChecklistFillItem].
 *
 * A due date is free and unlimited; the OS alarm that fires for it is the metered thing. Until now a
 * single field (`reminderAt`) meant BOTH, so there was no way to render "this has a date" differently
 * from "this will buzz at you" — and no way to keep a date while dropping the alarm.
 *
 * ## Why the default is `true` and not `false`
 * Every `reminderAt` already in the field was written by a code path that also armed an alarm. A
 * `false` default would decode all of that history as "alarm off" and silently stop notifying every
 * existing user — a regression wearing a migration's clothes. The tests below pin that decision:
 * legacy JSON (which has no `alarmEnabled` key at all) MUST come back as `true`.
 *
 * ## Why the field is last in the constructor
 * Fill items live inside a JSON blob (`ChecklistFill.items`), so a new field is a schema change with
 * no SQL migration — but only while it is appended. `Checklist.kt` documents the end of the
 * constructor as the safe position; `ignoreUnknownKeys` covers the reverse direction (an older build
 * reading a newer row).
 */
class ChecklistFillItemAlarmEnabledTest {

    /** Exactly the serializer the sync layer uses (`SyncRepositoryImpl.kt:53`). */
    private val json = Json { ignoreUnknownKeys = true }

    // ── Default ──

    @Test
    fun fillItem_alarmEnabled_defaultsToTrue() {
        val item = ChecklistFillItem(text = "Buy milk", checked = false)

        assertTrue(item.alarmEnabled)
    }

    // ── Legacy decode: the whole point of the `true` default ──

    @Test
    fun decode_legacyJsonWithoutAlarmEnabled_readsAsAlarmArmed() {
        // Byte-for-byte the shape written by every build before this field existed: a one-shot
        // reminder and no `alarmEnabled` key anywhere.
        val legacy = """[{"text":"Call dentist","checked":false,"id":"1720000000001_1000",""" +
            """"reminderAt":1700000000000}]"""

        val decoded = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), legacy)

        assertEquals(1, decoded.size)
        assertTrue(
            decoded[0].alarmEnabled,
            "legacy rows have no alarmEnabled key; decoding them as alarm-off would silently " +
                "mute notifications for every existing user",
        )
        assertEquals(1_700_000_000_000L, decoded[0].reminderAt)
    }

    @Test
    fun decode_explicitFalse_readsAsAlarmOff() {
        val wire = """[{"text":"Water plants","checked":false,"id":"1720000000002_2000",""" +
            """"reminderAt":1700000000000,"alarmEnabled":false}]"""

        val decoded = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), wire)

        assertFalse(decoded[0].alarmEnabled)
    }

    @Test
    fun encode_alarmEnabledTrue_isOmittedFromTheWire() {
        // encodeDefaults=false, so the default value must not appear. This is what keeps the
        // itemsJson byte-contract (and the MCP server's TypeScript encoder) unchanged as long as
        // nothing turns the alarm off.
        val items = listOf(
            ChecklistFillItem(text = "Task", checked = false).withReminderAt(1_700_000_000_000L),
        )

        val encoded = json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), items)

        assertFalse(
            encoded.contains("alarmEnabled"),
            "default-valued alarmEnabled must stay off the wire; encoded = $encoded",
        )
    }

    @Test
    fun encode_alarmEnabledFalse_roundTrips() {
        val items = listOf(
            ChecklistFillItem(text = "Task", checked = false)
                .withReminderAt(1_700_000_000_000L)
                .withAlarmEnabled(false),
        )

        val encoded = json.encodeToString(ListSerializer(ChecklistFillItem.serializer()), items)
        val decoded = json.decodeFromString(ListSerializer(ChecklistFillItem.serializer()), encoded)

        assertTrue(encoded.contains("\"alarmEnabled\":false"))
        assertFalse(decoded[0].alarmEnabled)
    }

    // ── withAlarmEnabled ──

    @Test
    fun withAlarmEnabled_returnsNewInstanceAndPreservesEverythingElse() {
        val original = ChecklistFillItem(
            text = "Call dentist",
            checked = true,
            note = "before noon",
            weekday = 3,
            priority = 1,
            templateItemId = "tpl-1",
        ).withReminderAt(1_700_000_000_000L)

        val updated = original.withAlarmEnabled(false)

        assertNotSame(original, updated)
        assertFalse(updated.alarmEnabled)
        assertTrue(original.alarmEnabled, "the original instance must not be mutated")
        assertEquals(original.text, updated.text)
        assertEquals(original.checked, updated.checked)
        assertEquals(original.note, updated.note)
        assertEquals(original.id, updated.id)
        assertEquals(original.weekday, updated.weekday)
        assertEquals(original.priority, updated.priority)
        assertEquals(original.reminderAt, updated.reminderAt)
        assertEquals(original.templateItemId, updated.templateItemId)
    }

    // ── Every other withX must forward the flag ──
    //
    // The class has a private constructor and hand-written positional copy helpers, so a new field
    // is preserved only where someone remembered to thread it through. A helper that forgets it
    // does not fail to compile — it silently re-arms the alarm on the next edit of an unrelated
    // field. One test per helper, because "one of them forgot" is exactly the failure mode.

    private fun alarmOffItem() = ChecklistFillItem(
        text = "Task",
        checked = false,
        note = "note",
        weekday = 2,
        priority = 1,
        templateItemId = "tpl",
    ).withReminderAt(1_700_000_000_000L).withAlarmEnabled(false)

    @Test
    fun withChecked_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withChecked(true).alarmEnabled)

    @Test
    fun withNote_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withNote("other").alarmEnabled)

    @Test
    fun withWeekday_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withWeekday(5).alarmEnabled)

    @Test
    fun withText_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withText("renamed").alarmEnabled)

    @Test
    fun withPriority_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withPriority(0).alarmEnabled)

    @Test
    fun withReminderAt_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withReminderAt(1_800_000_000_000L).alarmEnabled)

    @Test
    fun withRepeatRule_preservesAlarmEnabled() {
        val updated = alarmOffItem().withRepeatRule(
            rule = ReminderRepeatRule(type = RepeatType.DAILY),
            timeOfDayMinutes = 540,
            nextAt = 1_800_000_000_000L,
        )
        assertFalse(updated.alarmEnabled)
    }

    @Test
    fun withRepeatAdvanced_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withRepeatAdvanced(1_800_000_000_000L, 2).alarmEnabled)

    @Test
    fun withAttachments_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withAttachments(emptyList()).alarmEnabled)

    @Test
    fun withTemplateItemId_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withTemplateItemId("tpl-2").alarmEnabled)

    @Test
    fun withReminderFullScreen_preservesAlarmEnabled() =
        assertFalse(alarmOffItem().withReminderFullScreen(true).alarmEnabled)

    /**
     * Clearing the reminder re-arms the flag.
     *
     * `withReminderCleared()` resets the whole reminder block, and `alarmEnabled` is part of it: an
     * item with no date has no alarm to speak of, and leaving the flag at `false` would make the
     * NEXT date the user sets silently arrive without a notification.
     */
    @Test
    fun withReminderCleared_resetsAlarmEnabledToTrue() =
        assertTrue(alarmOffItem().withReminderCleared().alarmEnabled)
}
