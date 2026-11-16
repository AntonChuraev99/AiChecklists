@file:OptIn(UiToolingDataApi::class)

package com.antonchuraev.aichecklists.app.layoutdebug

import android.util.Log
import androidx.compose.ui.tooling.data.Group
import androidx.compose.ui.tooling.data.NodeGroup
import androidx.compose.ui.tooling.data.UiToolingDataApi
import androidx.compose.ui.tooling.data.asTree
import com.antonchuraev.homesearchchecklist.layoutdebug.LayoutDebugBridge

private const val TAG = "layout-debug"

/**
 * Turns the live composition into a tree an agent can act on.
 *
 * Raw `asTree()` output has one group per composable *call* — far too noisy to click
 * through, since a single visible element is a chain like
 * `Scaffold → Surface → CompositionLocalProvider → Box → Layout → ReusableComposeNode`,
 * all with identical bounds. Three passes fix that:
 *
 *  1. keep only groups that occupy space and can be named;
 *  2. collapse identical-bounds chains onto the *outermost* node, because that one
 *     carries the call site in project code while the inner ones point into Compose;
 *  3. give every node the nearest descendant `LayoutNode`, so a tweak can be applied
 *     even when the selected node is a call group with no layout node of its own.
 */
object TreeCapture {

    /** Result of one capture: JSON plus the layout nodes overrides can act on. */
    class Result(val json: String, val nodes: Map<String, Any>)

    private class N(
        var kind: String,
        var sourceFile: String?,
        var sourceLine: Int,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        var layoutNode: Any?,
    ) {
        /** The node this group owns, before ancestors inherit it — used for stable ids. */
        val ownLayoutNode: Any? = layoutNode
        var id: String = ""
        var parent: N? = null
        var children: MutableList<N> = ArrayList()
        var depth: Int = 0

        val sameBoxAs: (N) -> Boolean
            get() = { o -> o.left == left && o.top == top && o.right == right && o.bottom == bottom }
    }

    fun capture(density: Float, widthPx: Int, heightPx: Int): Result {
        // Snapshot the set first: it is weak and mutated by the composer as we read.
        val tables = synchronized(LayoutDebugBridge.tables) { LayoutDebugBridge.tables.toList() }
        if (tables.isEmpty()) {
            return Result("""{"error":"нет slot-таблиц — LayoutDebugRoot ещё не композился"}""", emptyMap())
        }

        val root = N("Composition", null, -1, 0, 0, widthPx, heightPx, null)
        root.id = "root"
        for (table in tables) {
            // One unreadable table (disposed mid-read, say) must not cost the whole tree.
            val tree = runCatching { table.asTree() }
                .onFailure { Log.w(TAG, "slot-таблица пропущена: ${it.message}", it) }
                .getOrNull() ?: continue
            build(tree, root)
        }

        collapse(root)
        inheritLayoutNodes(root)
        assignIds(root, 0)

        val layoutNodes = HashMap<String, Any>()
        val out = StringBuilder()
        out.append('{')
        out.append("\"target\":\"android\"")
        out.append(",\"unit\":\"dp\"")
        // Bounds stay in device pixels so they line up with the screenshot 1:1; divide by
        // this to get the dp an agent should write into code.
        out.append(",\"pxPerUnit\":").append(density)
        out.append(",\"viewport\":{\"w\":").append(widthPx)
            .append(",\"h\":").append(heightPx)
            .append(",\"scale\":").append(density).append('}')
        out.append(",\"rootId\":\"").append(root.id).append('"')
        out.append(",\"nodes\":{")
        var first = true
        serialize(root, out, layoutNodes) { if (!first) out.append(','); first = false }
        out.append("}}")

        return Result(out.toString(), layoutNodes)
    }

    // --- pass 1: keep what is selectable ------------------------------------

    private fun build(group: Group, parent: N) {
        val target = if (keep(group)) {
            val loc = group.location
            N(
                kind = kindOf(group),
                sourceFile = loc?.sourceFile,
                sourceLine = loc?.lineNumber ?: -1,
                left = group.box.left,
                top = group.box.top,
                right = group.box.right,
                bottom = group.box.bottom,
                layoutNode = (group as? NodeGroup)?.node,
            ).also {
                it.parent = parent
                parent.children.add(it)
            }
        } else {
            // Not worth showing, but its descendants may be — hoist them.
            parent
        }
        for (child in group.children) build(child, target)
    }

    private fun keep(group: Group): Boolean {
        val box = group.box
        if (box.right - box.left <= 0 || box.bottom - box.top <= 0) return false
        return group is NodeGroup || group.name != null
    }

    private fun kindOf(group: Group): String =
        group.name ?: (group as? NodeGroup)?.node?.javaClass?.simpleName ?: "Group"

    // --- pass 2: collapse identical-bounds chains ---------------------------

    private fun collapse(node: N) {
        while (node.children.size == 1) {
            val only = node.children[0]
            if (!node.sameBoxAs(only)) break
            // Keep this node's identity (outermost = call site in project code), adopt the
            // child's children, and inherit a layout node if we do not have one.
            if (node.layoutNode == null) node.layoutNode = only.layoutNode
            node.children = only.children
            for (c in node.children) c.parent = node
        }
        for (child in node.children) collapse(child)
    }

    // --- pass 3: make every node movable if any descendant is ---------------

    private fun inheritLayoutNodes(node: N): Any? {
        for (child in node.children) {
            val fromChild = inheritLayoutNodes(child)
            if (node.layoutNode == null) node.layoutNode = fromChild
        }
        return node.layoutNode
    }

    /**
     * Ids address nodes of *this* snapshot only. A node owning a layout node is named after
     * that node's identity; anything else falls back to its position in the tree, which
     * shifts when a subcomposition appears or a slot table is collected. That is why live
     * overrides are tracked by layout node instance in [LayoutDebugAgent], never by id.
     */
    private fun assignIds(node: N, depth: Int) {
        node.depth = depth
        node.children.forEachIndexed { index, child ->
            val own = child.ownLayoutNode
            child.id = if (own != null) "L${System.identityHashCode(own)}" else "${node.id}.$index"
            assignIds(child, depth + 1)
        }
    }

    // --- serialization ------------------------------------------------------

    private fun serialize(
        node: N,
        out: StringBuilder,
        layoutNodes: MutableMap<String, Any>,
        separator: () -> Unit,
    ) {
        node.layoutNode?.let { layoutNodes[node.id] = it }
        separator()
        out.append('"').append(node.id).append("\":{")
        out.append("\"id\":\"").append(node.id).append('"')
        out.append(",\"parentId\":")
        val parentId = node.parent?.id
        if (parentId == null) out.append("null") else out.append('"').append(parentId).append('"')
        out.append(",\"childIds\":[")
        node.children.forEachIndexed { i, c ->
            if (i > 0) out.append(',')
            out.append('"').append(c.id).append('"')
        }
        out.append(']')
        out.append(",\"depth\":").append(node.depth)
        out.append(",\"kind\":").appendJsonString(node.kind)
        out.append(",\"label\":").appendJsonString(labelOf(node))
        out.append(",\"bounds\":{\"x\":").append(node.left)
            .append(",\"y\":").append(node.top)
            .append(",\"w\":").append(node.right - node.left)
            .append(",\"h\":").append(node.bottom - node.top).append('}')
        out.append(",\"source\":")
        val file = node.sourceFile
        if (file == null) {
            out.append("null")
        } else {
            out.append("{\"file\":").appendJsonString(file)
            out.append(",\"line\":").append(node.sourceLine).append('}')
        }
        out.append(",\"movable\":").append(node.layoutNode != null)
        out.append('}')
        for (child in node.children) serialize(child, out, layoutNodes, separator)
    }

    private fun labelOf(node: N): String {
        val file = node.sourceFile ?: return node.kind
        return "${node.kind} ($file:${node.sourceLine})"
    }

    private fun StringBuilder.appendJsonString(value: String): StringBuilder {
        append('"')
        for (ch in value) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
            }
        }
        append('"')
        return this
    }
}
