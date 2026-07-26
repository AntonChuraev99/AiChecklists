package com.antonchuraev.homesearchchecklist.layoutdebug

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.InternalComposeApi
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.LocalInspectionTables
import java.util.Collections
import java.util.WeakHashMap

/**
 * Publishes the app's composition slot tables so a debug-only agent can read the real
 * Compose tree (bounds + source file/line) at runtime.
 *
 * Deliberately tiny and dependency-free: only `compose-runtime`, which this module
 * already uses. Everything that parses the tree — `ui-tooling`, the HTTP endpoint, the
 * override channel — lives in `androidApp/src/debug`, so it is absent from release
 * builds by source set, not by a runtime flag.
 *
 * Inert until [enabled] is set, which only the debug agent does.
 */
object LayoutDebugBridge {

    @Volatile
    var enabled: Boolean = false

    /**
     * Every composition's slot table, root and subcompositions alike.
     *
     * A single root [CompositionData] is not enough: `SubcomposeLayout` — which
     * `Scaffold`, lazy lists and the navigation host all use — composes its content
     * into a *separate* composition with its own table. Reading only the root loses
     * most of the actual screen. The runtime adds each new table here on its own once
     * `LocalInspectionTables` is provided.
     *
     * Weak so a disposed composition does not keep its slot table alive.
     */
    val tables: MutableSet<CompositionData> =
        Collections.synchronizedSet(Collections.newSetFromMap(WeakHashMap()))
}

/**
 * Wraps the app content. A no-op — literally just `content()` — unless the debug agent
 * enabled the bridge.
 *
 * `collectParameterInformation()` must run before the subtree composes: it tells the
 * composer to keep the source information the Compose compiler emitted, which is what
 * turns a node into a `file:line` an agent can edit.
 */
@OptIn(InternalComposeApi::class)
@Composable
fun LayoutDebugRoot(content: @Composable () -> Unit) {
    if (!LayoutDebugBridge.enabled) {
        content()
        return
    }
    currentComposer.collectParameterInformation()
    LayoutDebugBridge.tables.add(currentComposer.compositionData)
    CompositionLocalProvider(LocalInspectionTables provides LayoutDebugBridge.tables) {
        content()
    }
}
