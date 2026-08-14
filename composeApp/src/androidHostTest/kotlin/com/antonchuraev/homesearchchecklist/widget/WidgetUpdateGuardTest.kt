package com.antonchuraev.homesearchchecklist.widget

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Locks the contract of the widget update guard.
 *
 * The defect it protects against cannot be reproduced on a host JVM: on an OEM ROM that reports
 * API 34 without `JobScheduler.forNamespace`, the first `WorkManager.getInstance()` — reached from
 * every `GlanceAppWidget.update()/updateAll()` — throws `NoSuchMethodError`. What CAN be pinned
 * here is the part that silently regresses: that the guard catches an `Error` and not merely an
 * `Exception`, that the throwable is handed to the logger (this is what makes it a Crashlytics
 * non-fatal instead of an invisible swallow), that the user gets told, and that cancellation is
 * still allowed through.
 *
 * `NoSuchMethodError` is used verbatim as the failure below because a `catch (e: Exception)` — the
 * shape this code had before — passes every other test one could write here and fails only this
 * one.
 */
class WidgetUpdateGuardTest {

    private class RecordingLogger : AppLogger {
        val errors = mutableListOf<Pair<String, Throwable?>>()
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) {
            errors += message to throwable
        }
    }

    @Test
    fun `NoSuchMethodError from the widget update does not escape`() = runBlocking {
        val logger = RecordingLogger()

        val updated = runWidgetUpdateGuarded(TAG, logger, onDegraded = {}) {
            throw NoSuchMethodError("No virtual method forNamespace(...)Landroid/app/job/JobScheduler;")
        }

        assertFalse(updated, "an Error from the Glance update must be degraded, not propagated")
    }

    @Test
    fun `the failure reaches the logger with the throwable attached`() = runBlocking {
        val logger = RecordingLogger()
        val failure = NoSuchMethodError("forNamespace")

        runWidgetUpdateGuarded(TAG, logger, onDegraded = {}) { throw failure }

        assertEquals(1, logger.errors.size, "expected exactly one error log")
        // The throwable param is what triggers Crashlytics recordException — a message-only log
        // would leave the broken ROM invisible in diagnostics.
        assertSame(failure, logger.errors.single().second)
    }

    @Test
    fun `the user is told, with the same throwable`() = runBlocking {
        val failure = NoSuchMethodError("forNamespace")
        var reported: Throwable? = null

        runWidgetUpdateGuarded(TAG, RecordingLogger(), onDegraded = { reported = it }) { throw failure }

        assertSame(failure, reported, "silent degrade on a path the user just tapped is a bug")
    }

    @Test
    fun `a successful update reports success and stays silent`() = runBlocking {
        val logger = RecordingLogger()
        var reported: Throwable? = null
        var ran = false

        val updated = runWidgetUpdateGuarded(TAG, logger, onDegraded = { reported = it }) { ran = true }

        assertTrue(ran, "the update lambda must actually run")
        assertTrue(updated)
        assertTrue(logger.errors.isEmpty(), "the happy path must not log an error")
        assertNull(reported, "the happy path must not toast at the user")
    }

    @Test
    fun `cancellation is rethrown, not reported as a widget failure`() = runBlocking {
        val logger = RecordingLogger()
        var reported: Throwable? = null

        val thrown = runCatching {
            runWidgetUpdateGuarded(TAG, logger, onDegraded = { reported = it }) {
                throw CancellationException("activity destroyed mid-update")
            }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException, "swallowing cancellation breaks structured concurrency")
        assertTrue(logger.errors.isEmpty(), "normal teardown is not an error")
        assertNull(reported, "normal teardown must not surface as a failure toast")
    }

    @Test
    fun `a feedback callback that itself throws does not escape either`() = runBlocking {
        val logger = RecordingLogger()

        val updated = runWidgetUpdateGuarded(
            TAG,
            logger,
            onDegraded = { throw IllegalStateException("toast on a dead context") },
        ) {
            throw NoSuchMethodError("forNamespace")
        }

        assertFalse(updated)
        // Both the original failure and the broken feedback must be visible; neither may kill the
        // process, since ToggleItemActivity runs this on a scope with no Activity left behind it.
        assertEquals(2, logger.errors.size)
    }

    @Test
    fun `a null logger is tolerated`() = runBlocking {
        // Koin may not be up when a widget tap arrives in a freshly started process.
        val updated =
            runWidgetUpdateGuarded(TAG, logger = null, onDegraded = {}) { throw NoSuchMethodError("forNamespace") }
        assertFalse(updated)
    }

    private companion object {
        const val TAG = "WidgetTest"
    }
}
