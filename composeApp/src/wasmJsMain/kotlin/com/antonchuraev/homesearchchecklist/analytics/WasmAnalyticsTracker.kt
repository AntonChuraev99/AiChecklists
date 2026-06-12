@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.analytics

import com.antonchuraev.homesearchchecklist.core.common.api.AnalyticsTracker

/**
 * wasmJs [AnalyticsTracker] that forwards to Firebase JS Analytics (GA4) via the
 * `globalThis.__analytics*` bridges defined in init.js.template.
 *
 * Params/properties maps are serialized to JSON by a tiny hand-rolled encoder (no
 * kotlinx.serialization dependency needed here) — values are expected to be only
 * String/Int/Long/Double/Boolean, matching the [AnalyticsTracker] contract used by
 * the commonMain ViewModels. Unsupported value types are coerced to their string
 * form so a single bad param never drops the whole event.
 *
 * All JS bridges are no-throw (try/catch on the JS side) and silently no-op until
 * Firebase Analytics finishes its async init — early splash events may be lost,
 * which is acceptable.
 */
internal class WasmAnalyticsTracker : AnalyticsTracker {

    override fun setUserId(userId: String) {
        jsSetUserId(userId)
    }

    override fun setUserProperties(properties: Map<String, Any>) {
        jsSetUserProperties(toJson(properties))
    }

    override fun screenView(name: String) {
        jsScreenView(name)
    }

    override fun event(name: String, params: Map<String, Any>) {
        jsLogEvent(name, toJson(params))
    }

    // --- JSON encoding (no external deps; String/number/boolean values only) ---

    private fun toJson(map: Map<String, Any>): String {
        if (map.isEmpty()) return "{}"
        val sb = StringBuilder(map.size * 16)
        sb.append('{')
        var first = true
        for ((key, value) in map) {
            if (!first) sb.append(',')
            first = false
            appendJsonString(sb, key)
            sb.append(':')
            appendJsonValue(sb, value)
        }
        sb.append('}')
        return sb.toString()
    }

    private fun appendJsonValue(sb: StringBuilder, value: Any) {
        when (value) {
            is Boolean -> sb.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> sb.append(value.toString())
            is Double -> appendNumber(sb, value)
            is Float -> appendNumber(sb, value.toDouble())
            else -> appendJsonString(sb, value.toString())
        }
    }

    private fun appendNumber(sb: StringBuilder, d: Double) {
        // JSON has no NaN/Infinity literals — fall back to a quoted string so the
        // payload stays valid JSON and JSON.parse on the JS side never throws.
        if (d.isNaN() || d.isInfinite()) {
            appendJsonString(sb, d.toString())
        } else {
            sb.append(d.toString())
        }
    }

    private fun appendJsonString(sb: StringBuilder, raw: String) {
        sb.append('"')
        for (c in raw) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else ->
                    if (c < ' ') {
                        sb.append("\\u")
                        val hex = c.code.toString(16)
                        repeat(4 - hex.length) { sb.append('0') }
                        sb.append(hex)
                    } else {
                        sb.append(c)
                    }
            }
        }
        sb.append('"')
    }
}

// --- globalThis bridges. Wrapped in private functions (NOT external var — that is
// an ESM strict-mode ReferenceError on wasmJs). The bridges themselves are defined
// and try/catch-guarded in init.js.template. ---

private fun jsLogEvent(name: String, paramsJson: String) {
    js("globalThis.__analyticsLogEvent(name, paramsJson)")
}

private fun jsScreenView(name: String) {
    js("globalThis.__analyticsScreenView(name)")
}

private fun jsSetUserId(id: String) {
    js("globalThis.__analyticsSetUserId(id)")
}

private fun jsSetUserProperties(propsJson: String) {
    js("globalThis.__analyticsSetUserProperties(propsJson)")
}
