package com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure unit tests for [ChecklistTree]. Trees are built directly in memory from
 * [ChecklistItem]s (no Room, no fakes); fill check-state is wired through
 * [ChecklistFillItem.templateItemId] just like production.
 */
class ChecklistTreeTest {

    // ── Builders ──────────────────────────────────────────────────────────────

    private fun folder(text: String, parentId: String? = null): ChecklistItem =
        ChecklistItem(text = text, type = ChecklistNodeType.FOLDER, parentId = parentId)

    private fun leaf(text: String, parentId: String? = null): ChecklistItem =
        ChecklistItem(text = text, type = ChecklistNodeType.ITEM, parentId = parentId)

    /** A fill item linked to [templateId], with the given checked state. */
    private fun fill(templateId: String, checked: Boolean): ChecklistFillItem =
        ChecklistFillItem(text = "fill", checked = checked, templateItemId = templateId)

    // ── childrenOf ────────────────────────────────────────────────────────────

    @Test
    fun childrenOf_rootLevel_returnsOnlyRootNodes() {
        val rootA = leaf("a")
        val rootFolder = folder("F")
        val nested = leaf("nested", parentId = rootFolder.id)
        val items = listOf(rootA, rootFolder, nested)

        val children = ChecklistTree.childrenOf(items, parentId = null)

        assertEquals(listOf(rootA.id, rootFolder.id), children.map { it.id })
    }

    @Test
    fun childrenOf_nestedLevel_returnsDirectChildrenInOrder() {
        val parent = folder("F")
        val c1 = leaf("c1", parentId = parent.id)
        val c2 = leaf("c2", parentId = parent.id)
        val grandchildFolder = folder("G", parentId = parent.id)
        val deep = leaf("deep", parentId = grandchildFolder.id)
        val items = listOf(parent, c1, c2, grandchildFolder, deep)

        val children = ChecklistTree.childrenOf(items, parentId = parent.id)

        // Only direct children, original order, NOT the grandchild's leaf.
        assertEquals(listOf(c1.id, c2.id, grandchildFolder.id), children.map { it.id })
    }

    @Test
    fun childrenOf_unknownParent_returnsEmpty() {
        val items = listOf(leaf("a"))
        assertTrue(ChecklistTree.childrenOf(items, parentId = "nope").isEmpty())
    }

    // ── descendantIds / descendantLeaves (depth >= 3) ────────────────────────

    @Test
    fun descendantIds_deepTree_collectsAllDescendantsExcludingRootFolder() {
        // root → f1 → f2 → f3 → leaf
        val f1 = folder("f1")
        val f2 = folder("f2", parentId = f1.id)
        val f3 = folder("f3", parentId = f2.id)
        val deepLeaf = leaf("deep", parentId = f3.id)
        val midLeaf = leaf("mid", parentId = f2.id)
        // Sibling branch that must NOT be collected.
        val other = folder("other")
        val otherLeaf = leaf("otherLeaf", parentId = other.id)
        val items = listOf(f1, f2, f3, deepLeaf, midLeaf, other, otherLeaf)

        val ids = ChecklistTree.descendantIds(items, folderId = f1.id)

        assertEquals(setOf(f2.id, f3.id, deepLeaf.id, midLeaf.id), ids)
        assertFalse(f1.id in ids, "folderId itself must be excluded")
        assertFalse(other.id in ids)
        assertFalse(otherLeaf.id in ids)
    }

    @Test
    fun descendantLeaves_deepTree_collectsAllLeavesAtEveryLevel() {
        // f1 → f2 → f3 → leafC ; f2 → leafB ; f1 → leafA  (leaves at 3 different depths)
        val f1 = folder("f1")
        val leafA = leaf("A", parentId = f1.id)
        val f2 = folder("f2", parentId = f1.id)
        val leafB = leaf("B", parentId = f2.id)
        val f3 = folder("f3", parentId = f2.id)
        val leafC = leaf("C", parentId = f3.id)
        val items = listOf(f1, leafA, f2, leafB, f3, leafC)

        val leaves = ChecklistTree.descendantLeaves(items, folderId = f1.id)

        // Folders excluded; all three leaves present.
        assertEquals(setOf(leafA.id, leafB.id, leafC.id), leaves.map { it.id }.toSet())
        assertTrue(leaves.all { it.type == ChecklistNodeType.ITEM })
    }

    // ── folderProgress ────────────────────────────────────────────────────────

    @Test
    fun folderProgress_mixedCheckedAcrossLevels_countsCheckedAndTotal() {
        // f1 → leafA (checked) ; f1 → f2 → leafB (unchecked) ; f2 → leafC (checked)
        val f1 = folder("f1")
        val leafA = leaf("A", parentId = f1.id)
        val f2 = folder("f2", parentId = f1.id)
        val leafB = leaf("B", parentId = f2.id)
        val leafC = leaf("C", parentId = f2.id)
        val items = listOf(f1, leafA, f2, leafB, leafC)
        val fills = listOf(
            fill(leafA.id, checked = true),
            fill(leafB.id, checked = false),
            fill(leafC.id, checked = true),
        )

        val progress = ChecklistTree.folderProgress(items, fills, folderId = f1.id)

        assertEquals(FolderProgress(checked = 2, total = 3), progress)
    }

    @Test
    fun folderProgress_leafWithoutFill_countsTowardTotalNotChecked() {
        // Two leaves; only one has a (checked) fill. The unlinked one is total-but-unchecked.
        val f1 = folder("f1")
        val leafA = leaf("A", parentId = f1.id)
        val leafB = leaf("B", parentId = f1.id) // no fill at all
        val items = listOf(f1, leafA, leafB)
        val fills = listOf(fill(leafA.id, checked = true))

        val progress = ChecklistTree.folderProgress(items, fills, folderId = f1.id)

        assertEquals(FolderProgress(checked = 1, total = 2), progress)
    }

    @Test
    fun folderProgress_noLeaves_returnsZeroOverZero() {
        val empty = folder("empty")
        val items = listOf(empty)
        val progress = ChecklistTree.folderProgress(items, emptyList(), folderId = empty.id)
        assertEquals(FolderProgress(checked = 0, total = 0), progress)
    }

    @Test
    fun folderProgress_duplicateFillsForSameLeaf_anyCheckedCountsOnce() {
        // Defensive: corrupted data with two fills for one template leaf.
        val f1 = folder("f1")
        val leafA = leaf("A", parentId = f1.id)
        val items = listOf(f1, leafA)
        val fills = listOf(
            fill(leafA.id, checked = false),
            fill(leafA.id, checked = true),
        )

        val progress = ChecklistTree.folderProgress(items, fills, folderId = f1.id)

        assertEquals(FolderProgress(checked = 1, total = 1), progress)
    }

    // ── ancestorPath ──────────────────────────────────────────────────────────

    @Test
    fun ancestorPath_deepNode_returnsRootToNodeInclusive() {
        val f1 = folder("f1")
        val f2 = folder("f2", parentId = f1.id)
        val leafC = leaf("C", parentId = f2.id)
        val items = listOf(f1, f2, leafC)

        val path = ChecklistTree.ancestorPath(items, nodeId = leafC.id)

        assertEquals(listOf(f1.id, f2.id, leafC.id), path.map { it.id })
    }

    @Test
    fun ancestorPath_rootNode_returnsSingletonSelf() {
        val rootLeaf = leaf("root")
        val items = listOf(rootLeaf)
        val path = ChecklistTree.ancestorPath(items, nodeId = rootLeaf.id)
        assertEquals(listOf(rootLeaf.id), path.map { it.id })
    }

    @Test
    fun ancestorPath_unknownNode_returnsEmpty() {
        val items = listOf(leaf("a"))
        assertTrue(ChecklistTree.ancestorPath(items, nodeId = "ghost").isEmpty())
    }

    // ── canMove ───────────────────────────────────────────────────────────────

    @Test
    fun canMove_intoItself_isFalse() {
        val f1 = folder("f1")
        val items = listOf(f1)
        assertFalse(ChecklistTree.canMove(items, nodeId = f1.id, targetParentId = f1.id))
    }

    @Test
    fun canMove_intoDirectChild_isFalse() {
        val f1 = folder("f1")
        val child = folder("child", parentId = f1.id)
        val items = listOf(f1, child)
        assertFalse(ChecklistTree.canMove(items, nodeId = f1.id, targetParentId = child.id))
    }

    @Test
    fun canMove_intoDeepDescendant_isFalse() {
        val f1 = folder("f1")
        val f2 = folder("f2", parentId = f1.id)
        val f3 = folder("f3", parentId = f2.id)
        val items = listOf(f1, f2, f3)
        assertFalse(ChecklistTree.canMove(items, nodeId = f1.id, targetParentId = f3.id))
    }

    @Test
    fun canMove_intoUnrelatedFolder_isTrue() {
        val f1 = folder("f1")
        val unrelated = folder("unrelated")
        val items = listOf(f1, unrelated)
        assertTrue(ChecklistTree.canMove(items, nodeId = f1.id, targetParentId = unrelated.id))
    }

    @Test
    fun canMove_toRoot_isTrue() {
        val f1 = folder("f1")
        val child = folder("child", parentId = f1.id)
        val items = listOf(f1, child)
        assertTrue(ChecklistTree.canMove(items, nodeId = child.id, targetParentId = null))
    }

    // ── cascadeDeleteIds ──────────────────────────────────────────────────────

    @Test
    fun cascadeDeleteIds_returnsFolderPlusAllDescendants_notSiblingBranch() {
        val target = folder("target")
        val tChild = folder("tChild", parentId = target.id)
        val tLeaf = leaf("tLeaf", parentId = tChild.id)
        // Sibling branch must survive.
        val sibling = folder("sibling")
        val sLeaf = leaf("sLeaf", parentId = sibling.id)
        val items = listOf(target, tChild, tLeaf, sibling, sLeaf)

        val ids = ChecklistTree.cascadeDeleteIds(items, folderId = target.id)

        assertEquals(setOf(target.id, tChild.id, tLeaf.id), ids)
        assertFalse(sibling.id in ids)
        assertFalse(sLeaf.id in ids)
    }
}
