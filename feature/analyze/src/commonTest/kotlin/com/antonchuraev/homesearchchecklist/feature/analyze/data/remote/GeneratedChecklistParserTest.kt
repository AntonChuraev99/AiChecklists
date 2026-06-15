package com.antonchuraev.homesearchchecklist.feature.analyze.data.remote

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [flattenGeneratedItems] — the nested→flat unwinder for `generate_checklist`
 * responses.
 *
 * Verified contract:
 * - Back-compat: a flat list of leaves (no type/children) → flat list, all `parentId == null`,
 *   all `type == ITEM`, `hasFolders == false`.
 * - Nested: folders + leaves + 3-deep nesting → correct `parentId` links, DFS pre-order, every id
 *   unique, `hasFolders == true`.
 * - A folder with no children → an empty folder node is still emitted.
 * - Many sibling leaves → all ids unique (no millis-based collisions).
 */
class GeneratedChecklistParserTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun leaf(text: String, checked: Boolean = false) =
        GenItemDto(text = text, checked = checked)

    private fun folder(text: String, children: List<GenItemDto>) =
        GenItemDto(text = text, type = "folder", children = children)

    private fun List<ChecklistItem>.byText(text: String): ChecklistItem =
        single { it.text == text }

    // ── Back-compat: flat response ────────────────────────────────────────────

    @Test
    fun flatten_flatLeafList_producesFlatItemsAllRootAllItemType() {
        val nodes = listOf(leaf("Passport"), leaf("Tickets"), leaf("Charger"))

        val result = flattenGeneratedItems(nodes)

        assertEquals(3, result.size)
        assertEquals(listOf("Passport", "Tickets", "Charger"), result.map { it.text })
        assertTrue(result.all { it.parentId == null }, "every leaf must be root-level")
        assertTrue(result.all { it.type == ChecklistNodeType.ITEM }, "every node must be ITEM")
        assertTrue(result.none { it.isFolder }, "flat response must have no folders")
    }

    // ── Nested: folder with leaves + a nested folder (depth 3) ────────────────

    @Test
    fun flatten_nestedThreeLevels_linksParentIdsAndKeepsDfsOrder() {
        // root:
        //   Documents (folder)
        //     Passport (leaf)
        //     Visa (folder)
        //       Photo (leaf)            <- depth 3
        //   Snacks (leaf at root)
        val nodes = listOf(
            folder(
                "Documents",
                children = listOf(
                    leaf("Passport"),
                    folder("Visa", children = listOf(leaf("Photo"))),
                ),
            ),
            leaf("Snacks"),
        )

        val result = flattenGeneratedItems(nodes)

        // DFS pre-order: parent appears before its descendants, siblings keep order.
        assertEquals(
            listOf("Documents", "Passport", "Visa", "Photo", "Snacks"),
            result.map { it.text },
        )

        val documents = result.byText("Documents")
        val passport = result.byText("Passport")
        val visa = result.byText("Visa")
        val photo = result.byText("Photo")
        val snacks = result.byText("Snacks")

        // Types.
        assertEquals(ChecklistNodeType.FOLDER, documents.type)
        assertEquals(ChecklistNodeType.FOLDER, visa.type)
        assertEquals(ChecklistNodeType.ITEM, passport.type)
        assertEquals(ChecklistNodeType.ITEM, photo.type)
        assertEquals(ChecklistNodeType.ITEM, snacks.type)

        // Parent links.
        assertNull(documents.parentId, "root folder has no parent")
        assertNull(snacks.parentId, "root leaf has no parent")
        assertEquals(documents.id, passport.parentId, "Passport is under Documents")
        assertEquals(documents.id, visa.parentId, "Visa is under Documents")
        assertEquals(visa.id, photo.parentId, "Photo is under Visa (depth 3)")

        // hasFolders signal.
        assertTrue(result.any { it.isFolder }, "nested response must report folders")

        // All ids unique.
        val ids = result.map { it.id }
        assertEquals(ids.size, ids.toSet().size, "all node ids must be unique")
    }

    // ── Empty folder ──────────────────────────────────────────────────────────

    @Test
    fun flatten_folderWithEmptyChildren_emitsEmptyFolderNode() {
        val nodesAbsentChildren = listOf(GenItemDto(text = "Empty", type = "folder", children = null))
        val nodesEmptyList = listOf(GenItemDto(text = "Empty", type = "folder", children = emptyList()))

        for (nodes in listOf(nodesAbsentChildren, nodesEmptyList)) {
            val result = flattenGeneratedItems(nodes)
            assertEquals(1, result.size, "empty folder still produces exactly the folder node")
            val folder = result.single()
            assertEquals("Empty", folder.text)
            assertEquals(ChecklistNodeType.FOLDER, folder.type)
            assertNull(folder.parentId)
        }
    }

    // ── Id uniqueness under volume (millis-collision regression) ──────────────

    @Test
    fun flatten_manyLeaves_allIdsUnique() {
        // 200 leaves created in a tight loop would collide with the default millis-based id
        // generator (~ guaranteed within the same ms). withId(Uuid.random()) must prevent that.
        val nodes = (1..200).map { leaf("Item $it") }

        val result = flattenGeneratedItems(nodes)

        assertEquals(200, result.size)
        val ids = result.map { it.id }
        assertEquals(200, ids.toSet().size, "all 200 ids must be unique")
    }

    // ── Mixed-depth nesting, multiple folders at root ─────────────────────────

    @Test
    fun flatten_multipleRootFolders_eachChildLinksToCorrectParent() {
        val nodes = listOf(
            folder("Morning", children = listOf(leaf("Coffee"), leaf("Stretch"))),
            folder("Evening", children = listOf(leaf("Read"))),
        )

        val result = flattenGeneratedItems(nodes)

        val morning = result.byText("Morning")
        val evening = result.byText("Evening")
        assertEquals(morning.id, result.byText("Coffee").parentId)
        assertEquals(morning.id, result.byText("Stretch").parentId)
        assertEquals(evening.id, result.byText("Read").parentId)
        // Coffee/Stretch must NOT be linked to Evening, and vice-versa.
        assertTrue(result.byText("Read").parentId != morning.id)
    }
}
