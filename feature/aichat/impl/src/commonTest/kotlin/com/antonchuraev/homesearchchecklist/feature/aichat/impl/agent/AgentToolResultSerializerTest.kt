package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.DispatchOutcome
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ReadChecklistItem
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentToolResultSerializerTest {

    // ─── Success ──────────────────────────────────────────────────────────────

    @Test
    fun success_withArgsAndChecklistId_serializedCorrectly() {
        val outcome = DispatchOutcome.Success(
            messageKey = "chat_dispatch_added_to",
            args = listOf("milk", "Shopping"),
            linkedChecklistId = 42L,
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals("success", json["status"]?.jsonPrimitive?.contentOrNull)
        val details = json["details"]?.jsonArray
        assertEquals(2, details?.size)
        assertEquals("milk", details?.get(0)?.jsonPrimitive?.contentOrNull)
        assertEquals("Shopping", details?.get(1)?.jsonPrimitive?.contentOrNull)
        assertEquals(42L, json["checklist_id"]?.jsonPrimitive?.content?.toLongOrNull())
    }

    @Test
    fun success_emptyArgs_detailsOmitted() {
        val outcome = DispatchOutcome.Success(
            messageKey = "chat_dispatch_created_empty",
            args = emptyList(),
            linkedChecklistId = null,
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals("success", json["status"]?.jsonPrimitive?.contentOrNull)
        assertNull(json["details"])
        assertNull(json["checklist_id"])
    }

    @Test
    fun success_nullChecklistId_checklistIdOmitted() {
        val outcome = DispatchOutcome.Success(
            messageKey = "chat_dispatch_find_success",
            args = listOf("2"),
            linkedChecklistId = null,
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertNull(json["checklist_id"])
    }

    // ─── ChecklistContent ─────────────────────────────────────────────────────

    @Test
    fun checklistContent_serializedCorrectly() {
        val outcome = DispatchOutcome.ChecklistContent(
            checklistName = "Shopping",
            items = listOf(
                ReadChecklistItem(text = "milk", checked = true),
                ReadChecklistItem(text = "eggs", checked = false),
            ),
            checklistId = 7L,
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals("success", json["status"]?.jsonPrimitive?.contentOrNull)
        assertEquals("Shopping", json["checklist"]?.jsonPrimitive?.contentOrNull)
        val items = json["items"]?.jsonArray
        assertEquals(2, items?.size)
        val first = items?.get(0)?.jsonObject
        assertEquals("milk", first?.get("text")?.jsonPrimitive?.contentOrNull)
        assertTrue(first?.get("done")?.jsonPrimitive?.boolean ?: false)
        val second = items?.get(1)?.jsonObject
        assertEquals("eggs", second?.get("text")?.jsonPrimitive?.contentOrNull)
        assertFalse(second?.get("done")?.jsonPrimitive?.boolean ?: true)
    }

    @Test
    fun checklistContent_emptyItems_emptyArray() {
        val outcome = DispatchOutcome.ChecklistContent(
            checklistName = "Empty",
            items = emptyList(),
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals(0, json["items"]?.jsonArray?.size)
    }

    // ─── NotFound ─────────────────────────────────────────────────────────────

    @Test
    fun notFound_withArgs_serializedCorrectly() {
        val outcome = DispatchOutcome.NotFound(
            messageKey = "chat_dispatch_item_not_found",
            args = listOf("milk", "Shopping"),
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals("not_found", json["status"]?.jsonPrimitive?.contentOrNull)
        val details = json["details"]?.jsonArray
        assertEquals(2, details?.size)
        assertEquals("milk", details?.get(0)?.jsonPrimitive?.contentOrNull)
    }

    @Test
    fun notFound_emptyArgs_detailsOmitted() {
        val outcome = DispatchOutcome.NotFound("chat_dispatch_no_checklists", emptyList())
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals("not_found", json["status"]?.jsonPrimitive?.contentOrNull)
        assertNull(json["details"])
    }

    // ─── AmbiguousMatch ───────────────────────────────────────────────────────

    @Test
    fun ambiguousMatch_serializedCorrectly() {
        val outcome = DispatchOutcome.AmbiguousMatch(
            candidates = listOf("Shopping", "Shopping List", "Shop Errands"),
        )
        val json = AgentToolResultSerializer.serialize(outcome)
        assertEquals("ambiguous", json["status"]?.jsonPrimitive?.contentOrNull)
        val candidates = json["candidates"]?.jsonArray
        assertEquals(3, candidates?.size)
        assertEquals("Shopping", candidates?.get(0)?.jsonPrimitive?.contentOrNull)
    }

    // ─── RequiresPremium ──────────────────────────────────────────────────────

    @Test
    fun requiresPremium_serializedCorrectly() {
        val json = AgentToolResultSerializer.serialize(DispatchOutcome.RequiresPremium)
        assertEquals("requires_premium", json["status"]?.jsonPrimitive?.contentOrNull)
    }

    // ─── declinedResult ───────────────────────────────────────────────────────

    @Test
    fun declinedResult_hasStatusDeclined() {
        val json = AgentToolResultSerializer.declinedResult()
        assertEquals("declined", json["status"]?.jsonPrimitive?.contentOrNull)
    }
}
