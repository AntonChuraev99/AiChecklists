package com.antonchuraev.homesearchchecklist.feature.aichat.impl.agent

import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.AgentToolCall
import com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model.ToolCall
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AgentToolCallMapperTest {

    // ─── add_item ─────────────────────────────────────────────────────────────

    @Test
    fun addItem_withHint_mapsCorrectly() {
        val call = agentCall(
            "add_item",
            buildJsonObject {
                put("item_text", "milk")
                put("checklist_hint", "shopping")
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.AddItem>(result)
        assertEquals("milk", result.itemText)
        assertEquals("shopping", result.checklistHint)
    }

    @Test
    fun addItem_withoutHint_nullHint() {
        val call = agentCall(
            "add_item",
            buildJsonObject { put("item_text", "eggs") }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.AddItem>(result)
        assertNull(result.checklistHint)
    }

    @Test
    fun addItem_blankItemText_returnsNull() {
        val call = agentCall(
            "add_item",
            buildJsonObject { put("item_text", "  ") }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    @Test
    fun addItem_missingItemText_returnsNull() {
        val call = agentCall("add_item", buildJsonObject { put("checklist_hint", "foo") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── add_items ────────────────────────────────────────────────────────────

    @Test
    fun addItems_parsesArrayAndHint() {
        val call = agentCall(
            "add_items",
            buildJsonObject {
                putJsonArray("item_texts") {
                    add("apples")
                    add("bananas")
                    add("cherries")
                }
                put("checklist_hint", "groceries")
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.AddItems>(result)
        assertEquals(listOf("apples", "bananas", "cherries"), result.itemTexts)
        assertEquals("groceries", result.checklistHint)
    }

    @Test
    fun addItems_emptyArray_returnsNull() {
        val call = agentCall(
            "add_items",
            buildJsonObject {
                putJsonArray("item_texts") {}
            }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    @Test
    fun addItems_allBlankEntries_returnsNull() {
        val call = agentCall(
            "add_items",
            buildJsonObject {
                putJsonArray("item_texts") {
                    add("  ")
                    add("")
                }
            }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    @Test
    fun addItems_missingArray_returnsNull() {
        val call = agentCall("add_items", buildJsonObject { put("checklist_hint", "foo") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── create_checklist ─────────────────────────────────────────────────────

    @Test
    fun createChecklist_withInitialItems_mapsCorrectly() {
        val call = agentCall(
            "create_checklist",
            buildJsonObject {
                put("name", "Vacation")
                putJsonArray("initial_items") {
                    add("passport")
                    add("sunscreen")
                }
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.CreateChecklist>(result)
        assertEquals("Vacation", result.name)
        assertEquals(listOf("passport", "sunscreen"), result.initialItems)
    }

    @Test
    fun createChecklist_withoutInitialItems_defaultsToEmpty() {
        val call = agentCall(
            "create_checklist",
            buildJsonObject { put("name", "Todo") }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.CreateChecklist>(result)
        assertEquals(emptyList(), result.initialItems)
    }

    @Test
    fun createChecklist_blankName_returnsNull() {
        val call = agentCall("create_checklist", buildJsonObject { put("name", "") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── complete_item ────────────────────────────────────────────────────────

    @Test
    fun completeItem_mapsCorrectly() {
        val call = agentCall(
            "complete_item",
            buildJsonObject {
                put("item_text", "buy groceries")
                put("checklist_hint", "todo")
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.CompleteItem>(result)
        assertEquals("buy groceries", result.itemText)
        assertEquals("todo", result.checklistHint)
    }

    @Test
    fun completeItem_blankText_returnsNull() {
        val call = agentCall("complete_item", buildJsonObject { put("item_text", " ") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── delete_item ──────────────────────────────────────────────────────────

    @Test
    fun deleteItem_mapsCorrectly() {
        val call = agentCall(
            "delete_item",
            buildJsonObject { put("item_text", "old task") }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.DeleteItem>(result)
        assertEquals("old task", result.itemText)
    }

    @Test
    fun deleteItem_blankText_returnsNull() {
        val call = agentCall("delete_item", buildJsonObject { put("item_text", "") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── set_item_reminder ────────────────────────────────────────────────────

    @Test
    fun setItemReminder_withZSuffix_parsesToNonNullAt() {
        val call = agentCall(
            "set_item_reminder",
            buildJsonObject {
                put("item_text", "dentist")
                put("when_iso", "2026-06-01T09:00:00Z")
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.SetItemReminder>(result)
        assertEquals("dentist", result.itemText)
        assertNotNull(result.at)
        // Verify epoch: 2026-06-01T09:00:00Z = 1780304400000 ms
        assertEquals(1780304400000L, result.at)
    }

    @Test
    fun setItemReminder_withLocalNoOffset_parsesToNonNullAt() {
        val call = agentCall(
            "set_item_reminder",
            buildJsonObject {
                put("item_text", "call mom")
                put("when_iso", "2026-06-01T09:00:00")
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.SetItemReminder>(result)
        assertNotNull(result.at)
    }

    @Test
    fun setItemReminder_unparsableWhenIso_returnsNull() {
        val call = agentCall(
            "set_item_reminder",
            buildJsonObject {
                put("item_text", "foo")
                put("when_iso", "not-a-date")
            }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    @Test
    fun setItemReminder_blankItemText_returnsNull() {
        val call = agentCall(
            "set_item_reminder",
            buildJsonObject {
                put("item_text", "")
                put("when_iso", "2026-06-01T09:00:00Z")
            }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    @Test
    fun setItemReminder_missingWhenIso_returnsNull() {
        val call = agentCall(
            "set_item_reminder",
            buildJsonObject { put("item_text", "something") }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── rename_checklist ─────────────────────────────────────────────────────

    @Test
    fun renameChecklist_mapsCorrectly() {
        val call = agentCall(
            "rename_checklist",
            buildJsonObject {
                put("checklist_hint", "old name")
                put("new_name", "new name")
            }
        )
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.RenameChecklist>(result)
        assertEquals("old name", result.checklistHint)
        assertEquals("new name", result.newName)
    }

    @Test
    fun renameChecklist_blankNewName_returnsNull() {
        val call = agentCall(
            "rename_checklist",
            buildJsonObject {
                put("checklist_hint", "old")
                put("new_name", "  ")
            }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    @Test
    fun renameChecklist_blankHint_returnsNull() {
        val call = agentCall(
            "rename_checklist",
            buildJsonObject {
                put("checklist_hint", "")
                put("new_name", "new")
            }
        )
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── find_items ───────────────────────────────────────────────────────────

    @Test
    fun findItems_mapsCorrectly() {
        val call = agentCall("find_items", buildJsonObject { put("query", "milk") })
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.FindItemsQuery>(result)
        assertEquals("milk", result.query)
    }

    @Test
    fun findItems_blankQuery_returnsNull() {
        val call = agentCall("find_items", buildJsonObject { put("query", "  ") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── read_checklist ───────────────────────────────────────────────────────

    @Test
    fun readChecklist_mapsCorrectly() {
        val call = agentCall("read_checklist", buildJsonObject { put("name", "Shopping") })
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.ReadChecklist>(result)
        assertEquals("Shopping", result.name)
    }

    @Test
    fun readChecklist_blankName_returnsNull() {
        val call = agentCall("read_checklist", buildJsonObject { put("name", "") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── unknown name ─────────────────────────────────────────────────────────

    @Test
    fun unknownName_returnsNull() {
        val call = agentCall("do_something_else", buildJsonObject { put("foo", "bar") })
        assertNull(AgentToolCallMapper.map(call))
    }

    // ─── absent checklist_hint ────────────────────────────────────────────────

    @Test
    fun checklistHintAbsent_producesNullHint() {
        val call = agentCall("add_item", buildJsonObject { put("item_text", "task") })
        val result = AgentToolCallMapper.map(call)
        assertIs<ToolCall.AddItem>(result)
        assertNull(result.checklistHint)
    }

    // ─── parseIsoToEpochMs (unit level) ──────────────────────────────────────

    @Test
    fun parseIsoToEpochMs_withZSuffix_returnsEpoch() {
        val result = AgentToolCallMapper.parseIsoToEpochMs("2026-06-01T09:00:00Z")
        assertNotNull(result)
        // 2026-06-01T09:00:00Z = 1780304400000 ms epoch
        assertEquals(1780304400000L, result)
    }

    @Test
    fun parseIsoToEpochMs_localNoOffset_returnsNonNull() {
        // We can't assert exact epoch (depends on system tz), but must be non-null
        assertNotNull(AgentToolCallMapper.parseIsoToEpochMs("2026-06-01T09:00:00"))
    }

    @Test
    fun parseIsoToEpochMs_invalid_returnsNull() {
        assertNull(AgentToolCallMapper.parseIsoToEpochMs("not-a-date"))
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun agentCall(name: String, args: kotlinx.serialization.json.JsonObject) =
        AgentToolCall(id = "test-id", name = name, args = args)
}
