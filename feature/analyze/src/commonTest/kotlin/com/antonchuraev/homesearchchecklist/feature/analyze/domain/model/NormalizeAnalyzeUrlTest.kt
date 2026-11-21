package com.antonchuraev.homesearchchecklist.feature.analyze.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NormalizeAnalyzeUrlTest {

    @Test
    fun schemeLessHost_getsHttps() {
        assertEquals("https://example.com/a", normalizeAnalyzeUrl("example.com/a"))
        assertEquals("https://www.example.co.uk", normalizeAnalyzeUrl("  www.example.co.uk  "))
        assertEquals("https://example.com:8080/x", normalizeAnalyzeUrl("example.com:8080/x"))
    }

    @Test
    fun httpAndHttps_areKept() {
        assertEquals("https://x.y/z", normalizeAnalyzeUrl("https://x.y/z"))
        assertEquals("http://x.y/z?q=1#f", normalizeAnalyzeUrl("http://x.y/z?q=1#f"))
        assertEquals("https://x.y", normalizeAnalyzeUrl("HTTPS://x.y"))
    }

    @Test
    fun notAWebLink_isNull() {
        assertNull(normalizeAnalyzeUrl("hello world"))
        assertNull(normalizeAnalyzeUrl("ftp://x.y"))
        assertNull(normalizeAnalyzeUrl("abc"))
        assertNull(normalizeAnalyzeUrl("mailto:a@b.cd"))
        assertNull(normalizeAnalyzeUrl("https://localhost"))
        assertNull(normalizeAnalyzeUrl(".com"))
        assertNull(normalizeAnalyzeUrl(""))
        assertNull(normalizeAnalyzeUrl("   "))
    }
}
