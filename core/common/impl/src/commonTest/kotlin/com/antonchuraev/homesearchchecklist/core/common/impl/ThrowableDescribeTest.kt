package com.antonchuraev.homesearchchecklist.core.common.impl

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Guards the web log sink's ability to describe a failure.
 *
 * The regression these lock down shipped to prod: the wasmJs logger printed `throwable.message`
 * alone, so a Kotlin/Wasm NPE (whose message is `null`) reached the console as
 * `calendar_range_fetch_failed | null` — no type, no stack, no cause.
 */
class ThrowableDescribeTest {

    @Test
    fun describeForLog_throwableWithMessage_namesTypeAndMessage() {
        val described = IllegalStateException("db is closed").describeForLog()

        assertContains(described, "IllegalStateException")
        assertContains(described, "db is closed")
    }

    @Test
    fun describeForLog_nullMessage_stillNamesType() {
        // The exact prod shape: NullPointerException carrying no message at all.
        // The type alone is the difference between "something is null" and a place to look.
        val described = NullPointerException().describeForLog()

        assertContains(described, "NullPointerException")
        assertContains(described, "(no message)")
    }

    @Test
    fun describeForLog_nullMessage_doesNotRenderLiteralNull() {
        val described = NullPointerException().describeForLog()

        assertFalse(
            described.contains(": null"),
            "A null message must read as (no message), not as the useless literal `null`; was: $described",
        )
    }

    @Test
    fun describeForLog_chainedCause_reportsTheCause() {
        val root = IllegalArgumentException("corrupted `checklist_fills.items` JSON in Room")
        val described = IllegalStateException("today_range_fetch_failed", root).describeForLog()

        assertContains(described, "IllegalStateException")
        assertContains(described, "today_range_fetch_failed")
        // The cause is the actionable half — it names the column that actually blew up.
        assertContains(described, "IllegalArgumentException")
        assertContains(described, "corrupted `checklist_fills.items` JSON in Room")
    }

    @Test
    fun describeForLog_deepChain_terminatesAndStaysBounded() {
        var t: Throwable = RuntimeException("root")
        repeat(30) { i -> t = RuntimeException("wrap $i", t) }

        val described = t.describeForLog()

        assertTrue(described.isNotBlank())
        // Bounded by MAX_CAUSE_DEPTH — a runaway chain must not flood the console.
        assertTrue(
            described.lineSequence().count() < 200,
            "Description should stay bounded, but had ${described.lineSequence().count()} lines",
        )
    }

    @Test
    fun describeForLog_causeChain_isNotDoublePrinted() {
        val root = IllegalArgumentException("the actual cause")
        val described = IllegalStateException("wrapper", root).describeForLog()

        // stackTraceToString() unwinds causes on some targets; we must not append them twice.
        val causeMentions = described.split("the actual cause").size - 1
        assertTrue(
            causeMentions <= 2,
            "Cause should not be repeated; found $causeMentions mentions in: $described",
        )
    }
}
