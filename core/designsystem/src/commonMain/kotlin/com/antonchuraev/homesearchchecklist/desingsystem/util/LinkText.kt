package com.antonchuraev.homesearchchecklist.desingsystem.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * KMP-safe URL detection & pretty-rendering helpers for checklist item text / notes.
 *
 * Pure `commonMain` — NO `android.util.Patterns` / `Linkify` / `java.net.URI` (Android-only,
 * won't compile for wasmJs/iOS). URL matching is a deliberately simple `https?://\S+` scheme:
 * we only beautify links the user explicitly pasted with a scheme, never auto-link bare
 * `example.com` (avoids false positives on plain prose).
 *
 * These produce **read-only** visuals. The actual "open" action lives in `ItemDetailsSheet`
 * via `LocalUriHandler` — the checklist card itself stays non-clickable (30/70 hit-zone rule).
 */

private val URL_REGEX = Regex("""https?://\S+""")

// Trailing sentence punctuation that a greedy `\S+` would otherwise swallow into the URL
// (e.g. "see https://x.com/p." → the period is NOT part of the link).
private val TRAILING_PUNCTUATION = """[.,;:!?)\]}"'»]+$""".toRegex()

private fun cleanUrl(raw: String): String = raw.replace(TRAILING_PUNCTUATION, "")

/** Every distinct URL in [text] (trailing sentence punctuation stripped). Empty if none/null. */
fun extractUrls(text: String?): List<String> =
    text?.let { URL_REGEX.findAll(it).map { match -> cleanUrl(match.value) }.distinct().toList() }
        ?: emptyList()

/**
 * If this string (trimmed) is **exactly one URL and nothing else**, returns that URL; else null.
 * Used to decide "render the whole line as a domain tag" vs "linkify URLs inline among words".
 */
fun String.asWholeUrl(): String? {
    val trimmed = trim()
    val urls = extractUrls(trimmed)
    return if (urls.size == 1 && cleanUrl(trimmed) == urls[0]) urls[0] else null
}

/**
 * Reduces a URL to a short host label for display:
 * `https://www.linkedin.com/posts/foo?x=1` → `linkedin.com`.
 * Purely string-based (no `java.net.URI`): strips scheme, `user:pass@`, path, query and `:port`,
 * then drops a leading `www.`. Falls back to the original string if the host comes out blank.
 */
fun displayDomain(url: String): String {
    var host = url.substringAfter("://", url) // drop scheme (keep whole string if none)
    host = host.substringBefore('/')          // drop path/query/fragment
    host = host.substringAfterLast('@')        // drop credentials (user:pass@host)
    host = host.substringBefore(':')           // drop :port
    return host.removePrefix("www.").ifBlank { url }
}

/**
 * Builds a read-only [AnnotatedString] where each `https?://…` URL inside [raw] is replaced
 * in place by its [displayDomain], styled with [linkColor]; surrounding text is kept verbatim.
 *
 * READ-ONLY by design — no `LinkAnnotation`/`withLink`: on the checklist card the tap is owned
 * by the 30/70 hit-zone overlay, so an inline clickable link would never receive the tap anyway
 * (and `ui-card-patterns` forbids clickable elements on the card). Opening is offered separately
 * in `ItemDetailsSheet`. If [raw] has no URL, returns a plain `AnnotatedString(raw)` (cheap).
 *
 * @param raw       The original item text / note.
 * @param linkColor Color for the inlined domain token (usually `colorScheme.primary`).
 */
@Composable
fun rememberLinkifiedText(raw: String, linkColor: Color): AnnotatedString =
    remember(raw, linkColor) {
        if (!URL_REGEX.containsMatchIn(raw)) {
            AnnotatedString(raw)
        } else {
            buildAnnotatedString {
                var lastEnd = 0
                for (match in URL_REGEX.findAll(raw)) {
                    // Plain text before this URL — appended verbatim.
                    if (match.range.first > lastEnd) {
                        append(raw.substring(lastEnd, match.range.first))
                    }
                    val cleaned = cleanUrl(match.value)
                    // Any trailing punctuation we trimmed off the URL stays as normal text.
                    val trailing = match.value.removePrefix(cleaned)
                    withStyle(SpanStyle(color = linkColor)) {
                        append(displayDomain(cleaned))
                    }
                    if (trailing.isNotEmpty()) append(trailing)
                    lastEnd = match.range.last + 1
                }
                if (lastEnd < raw.length) append(raw.substring(lastEnd))
            }
        }
    }
