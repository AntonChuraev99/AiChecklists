package com.antonchuraev.homesearchchecklist.navigation

import androidx.compose.ui.platform.UriHandler
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression repro for the Crashlytics FATAL (issues c1aeb170 / 123ba20e): opening a drawer
 * external link through the platform [UriHandler] crashed the app with
 * `ActivityNotFoundException` on devices with no browser / mail client, because
 * [UriHandler.openUri] maps to `startActivity(ACTION_VIEW / ACTION_SENDTO)` on Android.
 *
 * [safeOpenUri] must guard that call so the crash degrades to a logged warning. These tests
 * assert the guard contract. With the `runCatching {}` guard removed (raw `uriHandler.openUri`),
 * [safeOpenUri_handlerThrows_doesNotPropagate] fails — the exception escapes, i.e. the app
 * crashes — which is exactly the shipped bug.
 */
class SafeOpenUriTest {

    /** [UriHandler] that always fails to open — models a device with no activity to handle the intent. */
    private class ThrowingUriHandler : UriHandler {
        override fun openUri(uri: String): Unit = throw RuntimeException("no activity")
    }

    /** [UriHandler] that opens successfully and records the uri it was asked to open. */
    private class RecordingUriHandler : UriHandler {
        val openedUris = mutableListOf<String>()
        override fun openUri(uri: String) {
            openedUris += uri
        }
    }

    /** [AppLogger] that records every `warning()` call as a (tag, message) pair. */
    private class RecordingLogger : AppLogger {
        val warnings = mutableListOf<Pair<String, String>>()
        override fun debug(tag: String, message: String) {}
        override fun info(tag: String, message: String) {}
        override fun warning(tag: String, message: String) {
            warnings += tag to message
        }
        override fun error(tag: String, message: String, throwable: Throwable?) {}
    }

    @Test
    fun safeOpenUri_handlerThrows_doesNotPropagate() {
        // THE crash repro: a throwing handler must not let the exception escape safeOpenUri.
        // Without the runCatching guard this call propagates and the app crashes.
        safeOpenUri(ThrowingUriHandler(), RecordingLogger(), "https://gisti-ai.com/")
        // Reaching here without an exception is the assertion — the guard swallowed the throw.
        assertTrue(true, "safeOpenUri must return normally when the UriHandler throws")
    }

    @Test
    fun safeOpenUri_handlerThrows_logsWarningWithTagAndUrl() {
        val logger = RecordingLogger()
        val url = "https://app.gisti-ai.com/privacy"

        safeOpenUri(ThrowingUriHandler(), logger, url)

        assertEquals(1, logger.warnings.size, "exactly one warning must be logged on failure")
        val (tag, message) = logger.warnings.single()
        assertEquals("DrawerLink", tag, "warning must be tagged DrawerLink")
        assertTrue(
            message.contains(url),
            "warning message must contain the failing url; was: $message",
        )
    }

    @Test
    fun safeOpenUri_handlerSucceeds_opensUriAndNoWarning() {
        val handler = RecordingUriHandler()
        val logger = RecordingLogger()
        val url = "mailto:support@gisti-ai.com"

        safeOpenUri(handler, logger, url)

        assertEquals(listOf(url), handler.openedUris, "the exact url must be passed to openUri")
        assertEquals(0, logger.warnings.size, "no warning must be logged on the happy path")
    }
}
