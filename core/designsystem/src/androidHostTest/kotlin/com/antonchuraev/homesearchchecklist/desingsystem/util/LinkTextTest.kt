package com.antonchuraev.homesearchchecklist.desingsystem.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-JVM regression guards for the KMP link helpers in `LinkText.kt` — no Compose / Robolectric,
 * so they run fast on the host JVM with the default JUnit runner.
 *
 * These lock the URL-detection contract that drives the "pretty link tag" feature: which strings
 * count as a whole-line URL (→ chip) vs an inline URL among words (→ linkified), and how a raw URL
 * is reduced to a short domain label. Edge cases (trailing punctuation, port, credentials, `www.`)
 * are the part most likely to regress silently.
 */
class LinkTextTest {

    // ─── displayDomain ───────────────────────────────────────────────────────

    @Test
    fun displayDomain_stripsScheme_www_andPath() {
        assertEquals(
            "linkedin.com",
            displayDomain("https://www.linkedin.com/posts/daria-shulgina_foo-cv?utm_source=x"),
        )
    }

    @Test
    fun displayDomain_keepsSubdomain() {
        assertEquals("sub.example.co.uk", displayDomain("http://sub.example.co.uk/path?q=1"))
    }

    @Test
    fun displayDomain_dropsCredentialsAndPort() {
        assertEquals("host.com", displayDomain("https://user:pass@host.com:8080/x"))
    }

    @Test
    fun displayDomain_bareHost_unchanged() {
        assertEquals("example.com", displayDomain("https://example.com"))
    }

    // ─── String.asWholeUrl ───────────────────────────────────────────────────

    @Test
    fun asWholeUrl_pureUrl_returnsUrl() {
        assertEquals("https://x.com/p", "https://x.com/p".asWholeUrl())
    }

    @Test
    fun asWholeUrl_trimsSurroundingWhitespace() {
        assertEquals("https://x.com/p", "  https://x.com/p  ".asWholeUrl())
    }

    @Test
    fun asWholeUrl_trailingPeriod_isStripped_stillWhole() {
        assertEquals("https://x.com/p", "https://x.com/p.".asWholeUrl())
    }

    @Test
    fun asWholeUrl_urlAmongWords_isNull() {
        assertNull("see https://x.com now".asWholeUrl())
    }

    @Test
    fun asWholeUrl_plainText_isNull() {
        assertNull("buy milk at the store".asWholeUrl())
    }

    @Test
    fun asWholeUrl_twoUrls_isNull() {
        assertNull("https://a.com https://b.com".asWholeUrl())
    }

    // ─── extractUrls ─────────────────────────────────────────────────────────

    @Test
    fun extractUrls_null_isEmpty() {
        assertEquals(emptyList<String>(), extractUrls(null))
    }

    @Test
    fun extractUrls_noUrl_isEmpty() {
        assertEquals(emptyList<String>(), extractUrls("just some text"))
    }

    @Test
    fun extractUrls_multiple_inOrder() {
        assertEquals(
            listOf("https://x.com", "https://y.com/p"),
            extractUrls("a https://x.com b https://y.com/p"),
        )
    }

    @Test
    fun extractUrls_deduplicates() {
        assertEquals(
            listOf("https://x.com"),
            extractUrls("https://x.com and again https://x.com"),
        )
    }

    @Test
    fun extractUrls_stripsTrailingSentencePunctuation() {
        assertEquals(listOf("https://x.com"), extractUrls("open https://x.com."))
    }
}
