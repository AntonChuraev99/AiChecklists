package com.antonchuraev.homesearchchecklist.core.common.api

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Crashlytics 9775ecb7: `UnsatisfiedLinkError: dlopen failed: library "libsqliteJni.so" not found`
 * killed the process on the first Room `open()`. The selector must route such devices to the OS
 * SQLite driver and report why, while every other device keeps the bundled driver.
 */
class SqliteDriverSelectorTest {

    private class FakeDriver(val label: String) : SQLiteDriver {
        override fun open(fileName: String): SQLiteConnection = error("$label driver is never opened here")
    }

    private val bundled = FakeDriver("bundled")
    private val framework = FakeDriver("framework")

    @Test
    fun missingNativeLibrary_fallsBackToFrameworkDriver_andReportsTheLinkError() {
        val linkError = UnsatisfiedLinkError("dlopen failed: library \"libsqliteJni.so\" not found")
        val reported = mutableListOf<Throwable>()

        val driver = SqliteDriverSelector.select(
            loadBundledLibrary = { throw linkError },
            bundledDriver = { bundled },
            frameworkDriver = { framework },
            onFallback = { reported += it },
        )

        assertSame(framework, driver)
        assertEquals(listOf<Throwable>(linkError), reported)
    }

    @Test
    fun loadableNativeLibrary_keepsBundledDriver_andReportsNothing() {
        val reported = mutableListOf<Throwable>()
        var frameworkBuilt = false

        val driver = SqliteDriverSelector.select(
            loadBundledLibrary = { },
            bundledDriver = { bundled },
            frameworkDriver = { frameworkBuilt = true; framework },
            onFallback = { reported += it },
        )

        assertSame(bundled, driver)
        assertTrue(reported.isEmpty(), "no fallback report expected, got $reported")
        assertTrue(!frameworkBuilt, "framework driver must not be created when the bundled library loads")
    }
}
