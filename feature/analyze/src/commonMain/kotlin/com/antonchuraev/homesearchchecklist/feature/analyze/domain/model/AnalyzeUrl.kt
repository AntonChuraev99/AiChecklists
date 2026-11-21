package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

/**
 * Validates and normalizes what the user typed into the Link field, BEFORE anything is sent (an
 * invalid link used to reach the server and cost credits). Returns the `http(s)` URL to send, or null
 * when the text is not a web link.
 *
 * - `example.com/a` → `https://example.com/a` (scheme-less input is the common case on mobile);
 * - `http://` / `https://` kept as typed (scheme lower-cased);
 * - spaces, another scheme (`ftp://`, `mailto:`), or a host without a dot (`abc`, `localhost`) → null.
 */
fun normalizeAnalyzeUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null

    val schemeEnd = trimmed.indexOf("://")
    val (scheme, rest) = if (schemeEnd >= 0) {
        trimmed.substring(0, schemeEnd).lowercase() to trimmed.substring(schemeEnd + 3)
    } else {
        // "mailto:x@y.z" / "javascript:..." have a scheme without "//"; "example.com:8080/x" is a port.
        val colon = trimmed.indexOf(':')
        if (colon > 0 && trimmed.substring(0, colon).all { it.isLetter() } &&
            !trimmed.substring(colon + 1).takeWhile { it != '/' }.all { it.isDigit() }
        ) {
            return null
        }
        "https" to trimmed
    }
    if (scheme != "http" && scheme != "https") return null

    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    val host = authority.substringAfterLast('@').substringBefore(':')
    if (!isPlausibleHost(host)) return null
    return "$scheme://$rest"
}

private fun isPlausibleHost(host: String): Boolean {
    if (host.length < 3 || '.' !in host) return false
    if (host.startsWith('.') || host.endsWith('.') || ".." in host) return false
    return host.all { it.isLetterOrDigit() || it == '.' || it == '-' }
}
