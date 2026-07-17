package com.antonchuraev.homesearchchecklist.core.common.impl

/** Guards against a cause chain that loops back on itself. */
private const val MAX_CAUSE_DEPTH = 8

/**
 * Renders a throwable for a plain-text log sink, the way `Log.e(tag, msg, throwable)` does on
 * Android: type, message, stack, and the whole causal chain.
 *
 * Exists because the web logger used to print `throwable.message` and nothing else. For a
 * Kotlin/Wasm NPE that message is literally `null`, so a prod crash logged as
 * `calendar_range_fetch_failed | null` — type, stack and cause all discarded at the sink.
 * A tag saying "something was null somewhere" is indistinguishable from no log at all.
 *
 * The type is printed even when the stack is unusable: prod wasm frames are bare
 * `wasm-function[27042]`, but knowing it was a `SerializationException` rather than an NPE
 * already splits the search space in half.
 */
internal fun Throwable.describeForLog(): String = buildString {
    append(renderHeader(this@describeForLog))

    val stack = stackTraceToString()
    if (stack.isNotBlank()) {
        append('\n').append(stack.trimEnd())
    }

    // stackTraceToString() already unwinds causes on some targets (JVM) but not others.
    // Appending unconditionally would double-print the chain, so only walk it ourselves
    // when the rendered stack didn't already cover it.
    if (!stack.contains(CAUSED_BY)) {
        appendCauseChain(this@describeForLog)
    }
}

private const val CAUSED_BY = "Caused by: "

private fun renderHeader(t: Throwable): String =
    "${t::class.simpleName ?: "Throwable"}: ${t.message ?: "(no message)"}"

private fun StringBuilder.appendCauseChain(root: Throwable) {
    val seen = mutableSetOf<Throwable>(root)
    var cause = root.cause
    var depth = 0
    while (cause != null && depth < MAX_CAUSE_DEPTH && seen.add(cause)) {
        append('\n').append(CAUSED_BY).append(renderHeader(cause))
        cause = cause.cause
        depth++
    }
}
