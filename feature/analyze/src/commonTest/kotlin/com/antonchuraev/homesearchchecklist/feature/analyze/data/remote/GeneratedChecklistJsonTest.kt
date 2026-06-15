package com.antonchuraev.homesearchchecklist.feature.analyze.data.remote

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end-ish tests over the EXACT production parsing pair: deserialize the `items` array of a
 * `generate_checklist` response with the same lenient [Json] config [FirebaseAiServiceImpl] uses,
 * then run [flattenGeneratedItems]. This exercises the real DTO + parser rather than a reimagined
 * pipeline, so JSON-shape and back-compat bugs are caught here.
 */
class GeneratedChecklistJsonTest {

    // Mirrors the json config in FirebaseAiServiceImpl.
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private fun parseItems(itemsJson: String) =
        flattenGeneratedItems(json.decodeFromString(ListSerializer(GenItemDto.serializer()), itemsJson))

    // ── Back-compat: legacy flat response (no type/children) ──────────────────

    @Test
    fun parse_legacyFlatJson_decodesAsFlatItemsNoFolders() {
        // The shape generate_checklist returned before folders existed.
        val itemsJson = """
            [
              {"text": "Buy milk", "checked": false},
              {"text": "Buy eggs", "checked": false}
            ]
        """.trimIndent()

        val result = parseItems(itemsJson)

        assertEquals(listOf("Buy milk", "Buy eggs"), result.map { it.text })
        assertTrue(result.all { it.parentId == null })
        assertTrue(result.all { it.type == ChecklistNodeType.ITEM })
        assertFalse(result.any { it.isFolder }, "legacy flat JSON => hasFolders should be false")
    }

    // ── Robust to unknown / missing fields ────────────────────────────────────

    @Test
    fun parse_jsonWithUnknownFieldsAndMissingChecked_isRobust() {
        // Extra server fields (note, confidence) + a leaf missing "checked" must not break parsing.
        val itemsJson = """
            [
              {"text": "Item with extras", "checked": true, "note": "srv-only", "confidence": 0.9},
              {"text": "Item missing checked"}
            ]
        """.trimIndent()

        val result = parseItems(itemsJson)

        assertEquals(2, result.size)
        assertEquals(listOf("Item with extras", "Item missing checked"), result.map { it.text })
        assertTrue(result.all { it.type == ChecklistNodeType.ITEM })
    }

    // ── Nested JSON (the new contract) ────────────────────────────────────────

    @Test
    fun parse_nestedJson_buildsParentLinkedTreeInDfsOrder() {
        val itemsJson = """
            [
              {
                "text": "Documents",
                "type": "folder",
                "children": [
                  {"text": "Passport", "checked": false},
                  {
                    "text": "Visa",
                    "type": "folder",
                    "children": [
                      {"text": "Photo", "checked": false}
                    ]
                  }
                ]
              },
              {"text": "Snacks", "checked": false}
            ]
        """.trimIndent()

        val result = parseItems(itemsJson)

        assertEquals(
            listOf("Documents", "Passport", "Visa", "Photo", "Snacks"),
            result.map { it.text },
        )

        val documents = result.single { it.text == "Documents" }
        val visa = result.single { it.text == "Visa" }
        assertEquals(ChecklistNodeType.FOLDER, documents.type)
        assertEquals(ChecklistNodeType.FOLDER, visa.type)
        assertEquals(documents.id, result.single { it.text == "Passport" }.parentId)
        assertEquals(documents.id, visa.parentId)
        assertEquals(visa.id, result.single { it.text == "Photo" }.parentId)
        assertNull(result.single { it.text == "Snacks" }.parentId)
        assertTrue(result.any { it.isFolder }, "nested JSON => hasFolders should be true")

        val ids = result.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "ids must be unique")
    }

    // ── Empty folder via JSON ─────────────────────────────────────────────────

    @Test
    fun parse_jsonFolderWithEmptyChildren_emitsEmptyFolder() {
        val itemsJson = """
            [
              {"text": "Later", "type": "folder", "children": []}
            ]
        """.trimIndent()

        val result = parseItems(itemsJson)

        assertEquals(1, result.size)
        assertEquals("Later", result.single().text)
        assertEquals(ChecklistNodeType.FOLDER, result.single().type)
    }
}
