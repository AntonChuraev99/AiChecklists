@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import kotlinx.coroutines.yield

/*
 * ByteArray <-> Uint8Array copies. Kotlin/Wasm GC arrays and JS typed arrays share no memory, so the
 * copy is element-wise (the repo's existing pattern, see FileReader.wasmJs.kt). A 12 MP photo is ~5 MB:
 * the copy yields to the event loop between chunks so it never freezes the page in one long task.
 */

private const val COPY_CHUNK = 256 * 1024

internal suspend fun ByteArray.toUint8Array(): JsAny {
    val target = jsNewUint8Array(size)
    var index = 0
    while (index < size) {
        val end = minOf(size, index + COPY_CHUNK)
        while (index < end) {
            jsUint8ArraySet(target, index, this[index].toInt())
            index++
        }
        if (index < size) yield()
    }
    return target
}

internal suspend fun uint8ArrayToByteArray(source: JsAny): ByteArray {
    val length = jsUint8ArrayLength(source)
    val result = ByteArray(length)
    var index = 0
    while (index < length) {
        val end = minOf(length, index + COPY_CHUNK)
        while (index < end) {
            result[index] = jsUint8ArrayGet(source, index).toByte()
            index++
        }
        if (index < length) yield()
    }
    return result
}

@JsFun("(n) => new Uint8Array(n)")
private external fun jsNewUint8Array(length: Int): JsAny

@JsFun("(arr, i, b) => { arr[i] = b; }")
private external fun jsUint8ArraySet(arr: JsAny, index: Int, value: Int)

@JsFun("(arr) => arr.length")
private external fun jsUint8ArrayLength(arr: JsAny): Int

@JsFun("(arr, i) => arr[i]")
private external fun jsUint8ArrayGet(arr: JsAny, index: Int): Int
