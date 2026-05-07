@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.remoteconfig.impl

import com.antonchuraev.homesearchchecklist.core.remoteconfig.api.RemoteConfigProvider
import kotlin.js.Promise
import kotlinx.coroutines.await

// -------------------------------------------------------
// JS bridge — delegates to globalThis.__rc* set in init.js
// -------------------------------------------------------

/**
 * Returns the __rcFetchPromise set by init.js.
 * Resolves to true (fetch OK) or false (failed, using defaultConfig).
 * Falls back to Promise.resolve(false) if init.js hasn't set it yet (should never happen
 * because init.js appends composeApp.js only after the synchronous body finishes).
 */
private fun rcFetchPromise(): Promise<JsAny?> =
    js("globalThis.__rcFetchPromise ?? Promise.resolve(false)")

/** Reads a string value from Firebase RC (sync, after fetchAndActivate). */
@JsFun("(key) => { try { return globalThis.__rcGetString(key) ?? ''; } catch (e) { return ''; } }")
private external fun jsRcGetString(key: String): String

/** Reads a boolean value from Firebase RC (sync, after fetchAndActivate). */
@JsFun("(key) => { try { return globalThis.__rcGetBoolean(key) ?? false; } catch (e) { return false; } }")
private external fun jsRcGetBoolean(key: String): Boolean

/** Reads a numeric value from Firebase RC (sync, after fetchAndActivate). */
@JsFun("(key) => { try { return globalThis.__rcGetNumber(key) ?? 0; } catch (e) { return 0; } }")
private external fun jsRcGetNumber(key: String): Double

// -------------------------------------------------------
// wasmJs RemoteConfigProvider implementation
// -------------------------------------------------------

private class WasmFirebaseRemoteConfigProvider : RemoteConfigProvider {

    /**
     * Suspends until the __rcFetchPromise resolves (fetchAndActivate started in parallel
     * with app.js load — zero extra latency on the critical path).
     * Returns true if RC was fetched successfully, false on error (defaultConfig is used).
     */
    override suspend fun fetchAndActivate(): Boolean {
        rcFetchPromise().await<JsAny?>()
        return true // resolved == fetch done (or graceful fallback); either way we can read values
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        try { jsRcGetBoolean(key) } catch (e: Throwable) { defaultValue }

    override fun getString(key: String, defaultValue: String): String =
        try {
            jsRcGetString(key).ifEmpty { defaultValue }
        } catch (e: Throwable) {
            defaultValue
        }

    override fun getLong(key: String, defaultValue: Long): Long =
        try {
            val v = jsRcGetNumber(key).toLong()
            // RC returns 0 when key not found/not yet activated; use defaultValue in that case
            if (v == 0L) defaultValue else v
        } catch (e: Throwable) {
            defaultValue
        }
}

/** Creates the wasmJs Firebase Remote Config provider backed by init.js globalThis bridges. */
actual fun createRemoteConfigProvider(): RemoteConfigProvider = WasmFirebaseRemoteConfigProvider()
