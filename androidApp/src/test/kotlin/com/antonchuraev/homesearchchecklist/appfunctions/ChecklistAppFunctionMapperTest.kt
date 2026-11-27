package com.antonchuraev.homesearchchecklist.appfunctions

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.appfunctions.AppFunctionAppUnknownException
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionLimitExceededException
import androidx.appfunctions.AppFunctionNotSupportedException
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ReadChecklistItem
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ReminderRepeatRule
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.RepeatType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Logic of the AppFunctions ↔ AI-chat-dispatcher translation: argument normalisation for
 * model-supplied input, LocalDateTime → epoch in the device zone, and DispatchOutcome → result /
 * AppFunctionException mapping. The service wiring itself is verified on device.
 *
 * Robolectric only because every AppFunctionException constructor reads `Bundle.EMPTY`, which the
 * plain android.jar stub leaves null. A plain Application keeps Koin / Firebase out of the test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChecklistAppFunctionMapperTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    /** "Now" = 2026-11-27T12:00Z, so 2026-12-01 is in the future. */
    private val now = Instant.parse("2026-11-27T12:00:00Z").toEpochMilli()

    private fun mapper(zone: ZoneId = berlin) = ChecklistAppFunctionMapper(zoneId = { zone }, nowMillis = { now })

    // ─── Arguments → ToolCall ────────────────────────────────────────────────

    @Test
    fun `createChecklist trims the name and drops blank items`() {
        val call = mapper().createChecklist("  Trip  ", listOf(" Passport ", "", "   ", "Charger"))
        assertEquals(ToolCall.CreateChecklist(name = "Trip", initialItems = listOf("Passport", "Charger")), call)
    }

    @Test
    fun `createChecklist with omitted items creates an empty checklist`() {
        assertEquals(emptyList(), mapper().createChecklist("Trip", null).initialItems)
    }

    @Test
    fun `createChecklist rejects a blank name`() {
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().createChecklist("   ", listOf("a")) }
    }

    @Test
    fun `completeItem rejects blank item text, which would match every item`() {
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().completeItem("Shopping", null, " ") }
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().completeItem("Shopping", null, null) }
    }

    @Test
    fun `omitted optional target arrives as blank or zero and becomes null`() {
        val call = mapper().completeItem(checklistName = "", checklistId = 0L, itemText = "milk")
        assertNull(call.checklistHint)
        assertNull(call.checklistId)
    }

    @Test
    fun `addItems passes the id and trimmed hint through`() {
        val call = mapper().addItems(checklistName = " Shopping ", checklistId = 42L, items = listOf("Milk", " Eggs "))
        assertEquals(ToolCall.AddItems(checklistHint = "Shopping", itemTexts = listOf("Milk", "Eggs"), checklistId = 42L), call)
    }

    @Test
    fun `addItems rejects a list with no non-blank item`() {
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().addItems("Shopping", null, listOf(" ", "")) }
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().addItems("Shopping", null, null) }
    }

    @Test
    fun `setItemReminder converts local time in the device zone to epoch millis`() {
        val local = LocalDateTime.of(2026, 12, 1, 9, 30)

        val inBerlin = mapper(berlin).setItemReminder("Health", null, "Pills", local)
        val inUtc = mapper(ZoneId.of("UTC")).setItemReminder("Health", null, "Pills", local)

        // Berlin is UTC+1 in December: the same wall-clock time is one hour earlier in absolute time.
        assertEquals(Instant.parse("2026-12-01T08:30:00Z").toEpochMilli(), inBerlin.at)
        assertEquals(Instant.parse("2026-12-01T09:30:00Z").toEpochMilli(), inUtc.at)
        assertEquals("Health", inBerlin.checklistHint)
        assertEquals("Pills", inBerlin.itemText)
    }

    @Test
    fun `setItemReminder rejects a time in the past`() {
        val past = LocalDateTime.of(2026, 11, 27, 12, 30) // 11:30Z in Berlin, before "now"
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().setItemReminder("Health", null, "Pills", past) }
    }

    @Test
    fun `setItemReminder rejects a missing time`() {
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().setItemReminder("Health", null, "Pills", null) }
    }

    @Test
    fun `setItemReminder needs a checklist name or id`() {
        val future = LocalDateTime.of(2026, 12, 1, 9, 30)
        val e = assertFailsWith<AppFunctionInvalidArgumentException> { mapper().setItemReminder(" ", 0L, "Pills", future) }
        assertTrue("checklistId" in e.message.orEmpty())
    }

    @Test
    fun `reminder target is resolved with a read carrying the same hint and id`() {
        val future = LocalDateTime.of(2026, 12, 1, 9, 30)
        val byName = mapper().setItemReminder("Health", null, "Pills", future)
        val byId = mapper().setItemReminder(null, 12L, "Pills", future)

        assertEquals(ToolCall.ReadChecklist(name = "Health", checklistId = null), mapper().reminderTargetRead(byName))
        assertEquals(ToolCall.ReadChecklist(name = "", checklistId = 12L), mapper().reminderTargetRead(byId))
    }

    // ─── Repeating reminders are refused ─────────────────────────────────────

    private val daily = ReminderRepeatRule(type = RepeatType.DAILY)

    @Test
    fun `an item with a repeating reminder is refused`() {
        val items = listOf(ChecklistFillItem("Take pills", checked = false).withRepeatRule(daily, 540, now + 1_000))
        val e = assertFailsWith<AppFunctionNotSupportedException> { mapper().requireNoRepeat(items, "pills") }
        assertTrue("\"Take pills\"" in e.message.orEmpty())
    }

    @Test
    fun `an item with a one-time reminder can be given a new one`() {
        val items = listOf(ChecklistFillItem("Take pills", checked = false).withReminderAt(now + 1_000))
        mapper().requireNoRepeat(items, "pills")
    }

    @Test
    fun `the repeat check looks at the first matching item, as the dispatcher does`() {
        val items = listOf(
            ChecklistFillItem("Pills morning", checked = false),
            ChecklistFillItem("Pills evening", checked = false).withRepeatRule(daily, 1260, now + 1_000),
        )
        // The dispatcher would set the reminder on "Pills morning" — not refused.
        mapper().requireNoRepeat(items, "pills")
        // No match at all — left to the dispatcher, which reports item_not_found.
        mapper().requireNoRepeat(items, "vitamins")
    }

    @Test
    fun `readChecklist needs a name or an id`() {
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().readChecklist(" ", 0L) }
        assertEquals(ToolCall.ReadChecklist(name = "", checklistId = 7L), mapper().readChecklist(null, 7L))
        assertEquals(ToolCall.ReadChecklist(name = "Trip", checklistId = null), mapper().readChecklist("Trip", null))
    }

    @Test
    fun `moveItem requires item and destination, source is optional`() {
        assertEquals(
            ToolCall.MoveItem(itemText = "Milk", toChecklistHint = "Shopping", fromChecklistHint = null, fromChecklistId = 3L),
            mapper().moveItem("Milk", "Shopping", "", 3L),
        )
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().moveItem("Milk", " ", null, null) }
    }

    @Test
    fun `renameChecklist and findItems reject blank input`() {
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().renameChecklist("Trip", "") }
        assertFailsWith<AppFunctionInvalidArgumentException> { mapper().findItems("  ") }
    }

    // ─── DispatchOutcome → result ────────────────────────────────────────────

    @Test
    fun `requireMutation reads checklist id and matched text`() {
        val outcome = DispatchOutcome.Success("chat_dispatch_completed", listOf("Milk 2%", "Shopping"), linkedChecklistId = 5L)
        assertEquals(ChecklistAppFunctionMapper.Mutation(checklistId = 5L, firstArg = "Milk 2%", noChange = false), mapper().requireMutation(outcome))
    }

    @Test
    fun `already done and same-list move are reported as no change`() {
        assertTrue(mapper().requireMutation(DispatchOutcome.Success("chat_dispatch_already_done", listOf("Milk"), linkedChecklistId = 5L)).noChange)
        assertTrue(mapper().requireMutation(DispatchOutcome.Success("chat_dispatch_move_same_list", listOf("Milk", "Shopping"), linkedChecklistId = 5L)).noChange)
        assertFalse(mapper().requireMutation(DispatchOutcome.Success("chat_dispatch_moved_item", listOf("Milk", "Shopping"), linkedChecklistId = 5L)).noChange)
    }

    @Test
    fun `success without an affected checklist is an app error, not a silent success`() {
        assertFailsWith<AppFunctionAppUnknownException> {
            mapper().requireMutation(DispatchOutcome.Success("chat_dispatch_added_many_to", listOf("2", "X")))
        }
    }

    @Test
    fun `requireContent maps items and keeps their done state`() {
        val content = mapper().requireContent(
            DispatchOutcome.ChecklistContent(
                checklistName = "Trip",
                items = listOf(ReadChecklistItem("Passport", true), ReadChecklistItem("Charger", false)),
                checklistId = 9L,
            ),
        )
        assertEquals(ChecklistContent(9L, "Trip", listOf(ChecklistEntry("Passport", true), ChecklistEntry("Charger", false))), content)
    }

    @Test
    fun `a search with no match is an empty result`() {
        assertEquals(FoundItems(0, ""), mapper().requireFound(DispatchOutcome.NotFound("chat_dispatch_find_no_match", listOf("xyz"))))
        assertEquals(
            FoundItems(2, "«Milk» in Shopping; «Milk powder» in Baby"),
            mapper().requireFound(DispatchOutcome.Success("chat_dispatch_find_success", listOf("2", "«Milk» in Shopping; «Milk powder» in Baby"))),
        )
    }

    // ─── DispatchOutcome → exception ─────────────────────────────────────────

    @Test
    fun `ambiguous match is an invalid argument that names every candidate`() {
        val e = mapper().toException(DispatchOutcome.AmbiguousMatch(listOf("Shopping", "Shopping for party")))
        assertTrue(e is AppFunctionInvalidArgumentException)
        assertTrue("\"Shopping\"" in e.message.orEmpty())
        assertTrue("\"Shopping for party\"" in e.message.orEmpty())
        assertTrue("checklistId" in e.message.orEmpty())
    }

    @Test
    fun `ambiguity on a name-only target does not suggest an id the function cannot take`() {
        val e = mapper().toException(
            DispatchOutcome.AmbiguousMatch(listOf("Shopping", "Shopping for party")),
            ChecklistAppFunctionMapper.Targeting.NAME_ONLY,
        )
        assertTrue(e is AppFunctionInvalidArgumentException)
        assertTrue("\"Shopping\"" in e.message.orEmpty() && "\"Shopping for party\"" in e.message.orEmpty())
        assertFalse("checklistId" in e.message.orEmpty())
        assertTrue("Gisti app" in e.message.orEmpty())
    }

    @Test
    fun `ambiguity in moveItem without a source id names both ways out`() {
        val e = mapper().toException(
            DispatchOutcome.AmbiguousMatch(listOf("Shopping", "Shopping for party")),
            ChecklistAppFunctionMapper.Targeting.MOVE_SOURCE_OR_DESTINATION,
        )
        assertTrue("fromChecklistId" in e.message.orEmpty())
        assertTrue("toChecklistName" in e.message.orEmpty())
        assertFalse(" checklistId" in e.message.orEmpty())
    }

    @Test
    fun `moveItem targeting depends on whether the source is pinned by id`() {
        val pinned = mapper().moveItem("Milk", "Shopping", null, 3L)
        val byName = mapper().moveItem("Milk", "Shopping", "Inbox list", null)
        assertEquals(ChecklistAppFunctionMapper.Targeting.NAME_ONLY, mapper().moveTargeting(pinned))
        assertEquals(ChecklistAppFunctionMapper.Targeting.MOVE_SOURCE_OR_DESTINATION, mapper().moveTargeting(byName))
    }

    @Test
    fun `rename ambiguity reaches the agent through requireMutation with the name-only hint`() {
        val e = assertFailsWith<AppFunctionInvalidArgumentException> {
            mapper().requireMutation(
                DispatchOutcome.AmbiguousMatch(listOf("Shopping", "Shopping for party")),
                ChecklistAppFunctionMapper.Targeting.NAME_ONLY,
            )
        }
        assertFalse("checklistId" in e.message.orEmpty())
    }

    @Test
    fun `free plan limit maps to limit exceeded with an upgrade hint`() {
        val e = mapper().toException(DispatchOutcome.RequiresPremium)
        assertTrue(e is AppFunctionLimitExceededException)
        assertTrue("Premium" in e.message.orEmpty())
    }

    @Test
    fun `not found outcomes map to element not found with the searched text`() {
        val item = mapper().toException(DispatchOutcome.NotFound("chat_dispatch_item_not_found", listOf("milk", "Shopping")))
        assertTrue(item is AppFunctionElementNotFoundException)
        assertTrue("\"milk\"" in item.message.orEmpty() && "\"Shopping\"" in item.message.orEmpty())

        val list = mapper().toException(DispatchOutcome.NotFound("chat_dispatch_no_checklist_match", listOf("Groceries")))
        assertTrue(list is AppFunctionElementNotFoundException)
        assertTrue("listChecklists" in list.message.orEmpty())

        assertTrue(mapper().toException(DispatchOutcome.NotFound("chat_dispatch_no_checklists")) is AppFunctionElementNotFoundException)
    }

    @Test
    fun `blocked move maps to not supported`() {
        val e = mapper().toException(DispatchOutcome.NotFound("chat_dispatch_move_blocked", listOf("Pills")))
        assertTrue(e is AppFunctionNotSupportedException)
        assertTrue("\"Pills\"" in e.message.orEmpty())
    }

    @Test
    fun `internal failures and unknown keys map to app unknown and keep the cause`() {
        val failed = mapper().toException(DispatchOutcome.NotFound("chat_dispatch_operation_failed", listOf("disk full")))
        assertTrue(failed is AppFunctionAppUnknownException)
        assertTrue("disk full" in failed.message.orEmpty())

        val unknown = mapper().toException(DispatchOutcome.NotFound("chat_dispatch_something_new"))
        assertTrue(unknown is AppFunctionAppUnknownException)
        assertTrue("chat_dispatch_something_new" in unknown.message.orEmpty())
    }
}
