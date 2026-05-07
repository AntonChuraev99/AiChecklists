@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.core.datastore.api

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * wasmJs implementation of DataStore<Preferences> backed by browser localStorage.
 *
 * Persistence model:
 * - One localStorage entry per DataStore name (e.g. `gisti_user_datastore`).
 * - Value is a small JSON object: `{ "key": {"t": "B|S|I|L|F|D", "v": <val>}, ... }`.
 * - Reads on construction → initial Preferences synchronously, no flicker.
 * - Writes through updateData() serialize the entire snapshot back to localStorage.
 *
 * We avoid AndroidX `PreferencesSerializer` (suspend API not callable from
 * constructor on wasmJs) and `OkioStorage` (needs an OPFS-backed FileSystem
 * that doesn't ship for browsers in DataStore 1.2/1.3 yet).
 *
 * Supported value types match what `AppDatastore` wrapper exposes:
 *   Boolean, String, Int, Long, Float, Double.
 * Adding a new type → extend [encodePref] / [decodePref] below.
 */
private class LocalStoragePreferencesDataStore(
    private val storageKey: String,
) : DataStore<Preferences> {

    private val mutex = Mutex()
    private val state = MutableStateFlow(loadFromStorage())

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(
        transform: suspend (Preferences) -> Preferences,
    ): Preferences = mutex.withLock {
        val current = state.value
        val next = transform(current)
        if (next !== current) {
            writeToStorage(next)
            state.value = next
        }
        next
    }

    private fun loadFromStorage(): Preferences {
        val raw = jsLocalStorageGetItem(storageKey) ?: return emptyPreferences()
        if (raw.isBlank() || raw == "{}") return emptyPreferences()
        return runCatching {
            val obj = json.parseToJsonElement(raw).jsonObject
            val mut = mutablePreferencesOf()
            for (name in obj.keys) {
                val element = obj[name] ?: continue
                val entry = element.jsonObject
                val type = entry["t"]?.jsonPrimitive?.content ?: continue
                val value = entry["v"]?.jsonPrimitive ?: continue
                decodePref(mut, name, type, value)
            }
            mut
        }.getOrElse { emptyPreferences() }
    }

    private fun writeToStorage(prefs: Preferences) {
        runCatching {
            val map = prefs.asMap()
            val sb = StringBuilder("{")
            var first = true
            for ((key, value) in map) {
                val encoded = encodePref(value) ?: continue
                if (!first) sb.append(',')
                first = false
                sb.append('"').append(escapeJson(key.name)).append("\":")
                sb.append(encoded)
            }
            sb.append('}')
            jsLocalStorageSetItem(storageKey, sb.toString())
        }
    }
}

actual fun createDataStore(name: String): DataStore<Preferences> {
    val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return LocalStoragePreferencesDataStore(storageKey = "gisti_$safeName")
}

private val json = Json { ignoreUnknownKeys = true }

private fun encodePref(value: Any): String? = when (value) {
    is Boolean -> "{\"t\":\"B\",\"v\":$value}"
    is String -> "{\"t\":\"S\",\"v\":\"${escapeJson(value)}\"}"
    is Int -> "{\"t\":\"I\",\"v\":$value}"
    is Long -> "{\"t\":\"L\",\"v\":$value}"
    is Float -> "{\"t\":\"F\",\"v\":$value}"
    is Double -> "{\"t\":\"D\",\"v\":$value}"
    else -> null
}

private fun decodePref(
    target: androidx.datastore.preferences.core.MutablePreferences,
    name: String,
    type: String,
    value: JsonPrimitive,
) {
    when (type) {
        "B" -> target[booleanPreferencesKey(name)] = value.boolean
        "S" -> target[stringPreferencesKey(name)] = value.content
        "I" -> target[intPreferencesKey(name)] = value.int
        "L" -> target[androidx.datastore.preferences.core.longPreferencesKey(name)] = value.content.toLong()
        "F" -> target[androidx.datastore.preferences.core.floatPreferencesKey(name)] = value.content.toFloat()
        "D" -> target[androidx.datastore.preferences.core.doublePreferencesKey(name)] = value.content.toDouble()
    }
}

private fun escapeJson(s: String): String = buildString(s.length + 4) {
    for (c in s) {
        when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '' -> append("\\f")
            else -> if (c.code < 0x20) append("\\u").append(c.code.toString(16).padStart(4, '0')) else append(c)
        }
    }
}

@JsFun("(key) => { try { return window.localStorage.getItem(key); } catch (e) { return null; } }")
private external fun jsLocalStorageGetItem(key: String): String?

@JsFun("(key, value) => { try { window.localStorage.setItem(key, value); return true; } catch (e) { return false; } }")
private external fun jsLocalStorageSetItem(key: String, value: String): Boolean
