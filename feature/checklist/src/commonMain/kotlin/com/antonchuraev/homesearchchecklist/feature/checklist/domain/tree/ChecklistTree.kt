package com.antonchuraev.homesearchchecklist.feature.checklist.domain.tree

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistFillItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType

/**
 * Aggregate checked/total of the leaves under a folder.
 * [total] = number of descendant leaves; [checked] = how many of them are marked done.
 */
data class FolderProgress(val checked: Int, val total: Int)

/**
 * Pure (side-effect-free) tree helpers over the template folder structure.
 *
 * The tree is an adjacency list: every [ChecklistItem] points at its parent via
 * [ChecklistItem.parentId] (null = checklist root). Folders are nodes with
 * [ChecklistItem.type] == [ChecklistNodeType.FOLDER]; everything else is a leaf.
 *
 * All functions are total and defensive against malformed data (missing nodes, duplicate
 * ids, parent cycles) so they never loop forever or throw on corrupted persisted state.
 */
object ChecklistTree {

    /** Direct children of [parentId] (null = root), in their original [items] order. */
    fun childrenOf(items: List<ChecklistItem>, parentId: String?): List<ChecklistItem> =
        items.filter { it.parentId == parentId }

    /**
     * Ids of every descendant of [folderId] (children, grandchildren, …), NOT including
     * [folderId] itself. Cycle-safe via a visited set.
     */
    fun descendantIds(items: List<ChecklistItem>, folderId: String): Set<String> {
        val childrenByParent: Map<String?, List<ChecklistItem>> = items.groupBy { it.parentId }
        val result = LinkedHashSet<String>()
        val stack = ArrayDeque<String>()
        childrenByParent[folderId]?.forEach { stack.addLast(it.id) }
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            // Guard against cycles and diamond/duplicate edges in corrupted data.
            if (!result.add(current)) continue
            childrenByParent[current]?.forEach { stack.addLast(it.id) }
        }
        return result
    }

    /**
     * All leaf items (type == ITEM) anywhere beneath [folderId], at any depth.
     * Returned in [items] order; excludes folder nodes and [folderId] itself.
     */
    fun descendantLeaves(items: List<ChecklistItem>, folderId: String): List<ChecklistItem> {
        val ids = descendantIds(items, folderId)
        return items.filter { it.id in ids && it.type == ChecklistNodeType.ITEM }
    }

    /**
     * Checked/total over the leaves beneath [folderId]. A leaf counts toward [total] always;
     * it counts toward [checked] only when a matching [ChecklistFillItem] (linked by
     * [ChecklistFillItem.templateItemId] == leaf id) exists and is checked. Leaves without a
     * fill are treated as unchecked. Robust to duplicate/missing fills.
     */
    fun folderProgress(
        items: List<ChecklistItem>,
        fillItems: List<ChecklistFillItem>,
        folderId: String,
    ): FolderProgress {
        val leaves = descendantLeaves(items, folderId)
        // Map templateItemId -> checked. On duplicate links, "any checked wins".
        val checkedByTemplateId = HashMap<String, Boolean>()
        for (fill in fillItems) {
            val key = fill.templateItemId ?: continue
            checkedByTemplateId[key] = (checkedByTemplateId[key] ?: false) || fill.checked
        }
        val checked = leaves.count { checkedByTemplateId[it.id] == true }
        return FolderProgress(checked = checked, total = leaves.size)
    }

    /**
     * Path from the checklist root down to [nodeId], inclusive (root first, [nodeId] last) —
     * handy for breadcrumbs/headers. Returns emptyList if [nodeId] is not found.
     * Cycle-safe: a parent loop in corrupted data stops the walk instead of hanging.
     */
    fun ancestorPath(items: List<ChecklistItem>, nodeId: String): List<ChecklistItem> {
        val byId: Map<String, ChecklistItem> = items.associateBy { it.id }
        val start = byId[nodeId] ?: return emptyList()
        val reversed = ArrayList<ChecklistItem>()
        val visited = HashSet<String>()
        var current: ChecklistItem? = start
        while (current != null) {
            if (!visited.add(current.id)) break // cycle guard
            reversed.add(current)
            val parentId = current.parentId ?: break
            current = byId[parentId]
        }
        return reversed.asReversed()
    }

    /**
     * Whether [nodeId] may be re-parented under [targetParentId] (null = root).
     * False when the target is the node itself or any of its descendants (would create a
     * cycle); true otherwise, including moving to root.
     */
    fun canMove(items: List<ChecklistItem>, nodeId: String, targetParentId: String?): Boolean {
        if (targetParentId == null) return true
        if (targetParentId == nodeId) return false
        return targetParentId !in descendantIds(items, nodeId)
    }

    /**
     * Ids to remove for a cascading folder delete: [folderId] plus all of its descendants.
     * Sibling branches are untouched.
     */
    fun cascadeDeleteIds(items: List<ChecklistItem>, folderId: String): Set<String> =
        descendantIds(items, folderId) + folderId
}
