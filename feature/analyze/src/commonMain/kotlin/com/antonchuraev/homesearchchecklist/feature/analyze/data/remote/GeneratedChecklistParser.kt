package com.antonchuraev.homesearchchecklist.feature.analyze.data.remote

import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistItem
import com.antonchuraev.homesearchchecklist.feature.checklist.domain.model.ChecklistNodeType
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * One node in a `generate_checklist` response. Each node is EITHER a leaf or a folder:
 * - Leaf: `{"text": "...", "checked": false}` — no `type`/`children`.
 * - Folder: `{"text": "Folder name", "type": "folder", "children": [ <recursive> ]}`.
 *
 * Back-compat: the legacy flat response is a list of leaves with no `type`/`children`; with field
 * defaults + `ignoreUnknownKeys` every such node decodes as a leaf ([type] == null →
 * [ChecklistNodeType.ITEM] at flatten time). [children] defaults to null so both an absent list
 * and an empty list mean "no children"; a folder may legitimately have empty [children] (an empty
 * folder is valid).
 *
 * Kept `internal` (not `private` to the service) so the flattening logic can be unit-tested
 * against the exact same DTO + parser the production HTTP path uses.
 */
@Serializable
internal data class GenItemDto(
    val text: String = "",
    val checked: Boolean = false,
    val type: String? = null,
    val children: List<GenItemDto>? = null
) {
    /** A node is a folder iff its `type` is the literal "folder" (case-insensitive), regardless of children. */
    val isFolder: Boolean get() = type?.equals("folder", ignoreCase = true) == true
}

/**
 * Recursively unwinds a (possibly nested) `generate_checklist` response tree into a FLAT
 * `List<ChecklistItem>` carrying parent/child links via [ChecklistItem.parentId] and
 * [ChecklistItem.type]. The output is the template's folder tree as an adjacency list.
 *
 * Contract:
 * - **Order**: DFS pre-order — a folder node appears immediately before its descendants, and
 *   siblings keep their source order. (The presentation/UI relies on this for a stable display.)
 * - **Leaf** → `ChecklistItem(text).withType(ITEM).withParentId(parentId).withId(uuid)`.
 *   The leaf's `checked` flag is intentionally ignored: this builds a TEMPLATE, whose items are
 *   always unchecked (checked state lives on the fill, not the template).
 * - **Folder** → `ChecklistItem(text).withType(FOLDER).withParentId(parentId).withId(uuid)`, then
 *   its children are flattened with `parentId = thisFolder.id`. Empty/absent children → empty
 *   folder (the folder node is still emitted).
 * - **Unique ids**: every emitted node gets a fresh `Uuid.random()` string id, so a child's
 *   `parentId` can never collide with an unrelated node — the default millis-based id generator
 *   can collide when many items are created in the same millisecond (see [ChecklistItem.withId]).
 * - **Back-compat / robustness**: a node without `type`/`children` is treated as a leaf; the whole
 *   function is null-safe via the DTO defaults, so a flat legacy response yields a flat list with
 *   `parentId = null`, `type = ITEM` for every item.
 *
 * @param nodes top-level response items (already deserialized).
 * @param parentId parent folder id for this level; `null` at the checklist root.
 */
@OptIn(ExperimentalUuidApi::class)
internal fun flattenGeneratedItems(
    nodes: List<GenItemDto>,
    parentId: String? = null
): List<ChecklistItem> {
    val flat = mutableListOf<ChecklistItem>()
    for (node in nodes) {
        if (node.isFolder) {
            val folder = ChecklistItem(text = node.text)
                .withType(ChecklistNodeType.FOLDER)
                .withParentId(parentId)
                .withId(Uuid.random().toString())
            flat += folder
            // Recurse into children (if any), parented to this folder.
            node.children?.let { children ->
                flat += flattenGeneratedItems(children, parentId = folder.id)
            }
        } else {
            val leaf = ChecklistItem(text = node.text)
                .withType(ChecklistNodeType.ITEM)
                .withParentId(parentId)
                .withId(Uuid.random().toString())
            flat += leaf
        }
    }
    return flat
}
