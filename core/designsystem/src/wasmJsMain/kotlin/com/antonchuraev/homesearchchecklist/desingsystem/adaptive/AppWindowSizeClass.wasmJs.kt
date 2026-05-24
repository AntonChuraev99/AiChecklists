@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.desingsystem.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Reads window.innerWidth via globalThis bridge.
 *
 * NOT a top-level `external var` — Kotlin/Wasm compiles composeApp.js as an ESM module
 * (strict mode). A bare-name assignment in the JS binding throws ReferenceError.
 * `js("globalThis.innerWidth")` is the idiomatic bridge — same pattern as
 * core/remoteconfig RemoteConfigFactory.wasmJs.kt and core/designsystem AppLocale.wasmJs.kt.
 *
 * Browser CSS px ≈ Compose dp at device-pixel-ratio=1 (standard density).
 * For adaptive Compact/Medium/Expanded bucketing this precision is sufficient.
 * If retina-correct dp is ever needed: divide by globalThis.devicePixelRatio.
 */
private fun readInnerWidth(): Int = js("globalThis.innerWidth")

/**
 * Registers a "resize" event listener on globalThis and returns the handler reference
 * so it can be passed to removeResizeListener on disposal.
 * The JS wrapper captures the Kotlin lambda as a function and calls it on each resize.
 */
@JsFun("(cb) => { const handler = () => cb(); globalThis.addEventListener('resize', handler); return handler; }")
private external fun addResizeListener(cb: () -> Unit): JsAny

/**
 * Removes the previously-registered resize listener using the handler reference
 * returned by addResizeListener.
 */
@JsFun("(handler) => { globalThis.removeEventListener('resize', handler); }")
private external fun removeResizeListener(handler: JsAny)

@Composable
actual fun rememberAppWindowSizeClass(): AppWindowSizeClass {
    var widthDp by remember { mutableStateOf(readInnerWidth()) }
    DisposableEffect(Unit) {
        val handler = addResizeListener { widthDp = readInnerWidth() }
        onDispose { removeResizeListener(handler) }
    }
    return classifyWindowWidth(widthDp)
}
