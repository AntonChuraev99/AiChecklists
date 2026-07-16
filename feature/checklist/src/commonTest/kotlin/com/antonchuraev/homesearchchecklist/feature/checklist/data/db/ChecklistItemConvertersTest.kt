package com.antonchuraev.homesearchchecklist.feature.checklist.data.db

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistViewMode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Covers the JSON `items` columns of `checklists` / `checklist_fills`.
 *
 * Motivation: a prod web origin hung Home on an infinite spinner whose only evidence was
 * `NullPointerException: null` — no column, no row, no clue. These converters are on that exact
 * path (`observeRemindersInRange` reads both columns), so any throw here must name its column.
 */
class ChecklistItemConvertersTest {

    private val converters = ChecklistItemConverters()

    // ─── Round-trip (happy path) ──────────────────────────────────────────────

    @Test
    fun fromString_emptyValue_returnsEmptyList() {
        assertEquals(emptyList(), converters.fromString(""))
    }

    @Test
    fun fillItemsFromString_emptyValue_returnsEmptyList() {
        assertEquals(emptyList(), converters.fillItemsFromString(""))
    }

    @Test
    fun fromString_encodedItems_roundTripsBack() {
        val items = listOf(ChecklistItem(text = "Milk"), ChecklistItem(text = "Bread", checked = true))

        val decoded = converters.fromString(converters.toString(items))

        assertEquals(items, decoded)
    }

    @Test
    fun fillItemsFromString_encodedItems_roundTripsBack() {
        val items = listOf(ChecklistFillItem(text = "Passport", checked = true, note = "in the drawer"))

        val decoded = converters.fillItemsFromString(converters.fillItemsToString(items))

        assertEquals(items, decoded)
    }

    // ─── Corruption is reported WITH the column name ─────────────────────────

    @Test
    fun fromString_malformedJson_throwsNamingTheChecklistsColumn() {
        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fromString("{ this is not json")
        }

        assertContains(e.message!!, "checklists.items")
    }

    @Test
    fun fillItemsFromString_malformedJson_throwsNamingTheFillsColumn() {
        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fillItemsFromString("{ this is not json")
        }

        assertContains(e.message!!, "checklist_fills.items")
    }

    @Test
    fun fromString_missingRequiredField_throwsDecodeException() {
        // `text` is non-null with no default — a row written by an older schema could lack it.
        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fromString("""[{"checked":true}]""")
        }

        assertContains(e.message!!, "checklists.items")
    }

    @Test
    fun fillItemsFromString_nullInNonNullField_throwsDecodeException() {
        // The shape a Kotlin/Wasm NPE-with-null-message would most plausibly come from.
        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fillItemsFromString("""[{"text":null,"checked":false}]""")
        }

        assertContains(e.message!!, "checklist_fills.items")
    }

    // ─── Diagnostics quality ─────────────────────────────────────────────────

    @Test
    fun fromString_malformedJson_preservesOriginalCause() {
        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fromString("{ this is not json")
        }

        // Without the cause the enrichment would destroy the very evidence it exists to carry.
        assertNotNull(e.cause)
    }

    @Test
    fun fromString_malformedJson_reportsPayloadLength() {
        val corrupted = "{ this is not json"

        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fromString(corrupted)
        }

        assertContains(e.message!!, "${corrupted.length} chars")
    }

    @Test
    fun fromString_malformedJson_messageLeaksNoUserContent() {
        // This message reaches Crashlytics; the JSON holds user-authored checklist text.
        // Regression guard: the first cut of this class interpolated `cause.message`, and
        // kotlinx.serialization embeds the offending JSON input in it — so the "safe" summary
        // quietly republished the payload.
        val secret = "Divorce lawyer appointment"

        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fromString("""[{"text":"$secret","checked":}]""")
        }

        assertTrue(
            !e.message!!.contains(secret),
            "Decode failure must not echo checklist content into logs, but was: ${e.message}",
        )
    }

    @Test
    fun fromString_malformedJson_chainedCauseStillCarriesPayload() {
        // Characterisation, NOT an endorsement: kotlinx.serialization puts the raw JSON in its
        // own message, and we deliberately keep the cause chained because a Kotlin/Wasm NPE is
        // only diagnosable via its stack — redacting it would drop the stack, i.e. the point.
        // Locked down so the trade-off is visible in review rather than folklore.
        // If this test ever fails, kotlinx stopped embedding input: drop the KDoc warning
        // on ChecklistJsonDecodeException and delete this test.
        val secret = "Divorce lawyer appointment"

        val e = assertFailsWith<ChecklistJsonDecodeException> {
            converters.fromString("""[{"text":"$secret","checked":}]""")
        }

        assertTrue(
            e.cause?.message?.contains(secret) == true,
            "Expected the chained cause to still embed the payload (documented limitation); " +
                "cause was: ${e.cause?.message}",
        )
    }

    // ─── viewMode (nullable input — the pattern the items columns lack) ───────

    @Test
    fun viewModeFromString_null_returnsStandard() {
        assertEquals(ChecklistViewMode.Standard, converters.viewModeFromString(null))
    }

    @Test
    fun viewModeFromString_weekly_returnsWeekly() {
        assertEquals(ChecklistViewMode.Weekly, converters.viewModeFromString(ChecklistViewMode.Weekly.name))
    }

    @Test
    fun viewModeFromString_unknownValue_fallsBackToStandard() {
        assertEquals(ChecklistViewMode.Standard, converters.viewModeFromString("Fortnightly"))
    }
}
