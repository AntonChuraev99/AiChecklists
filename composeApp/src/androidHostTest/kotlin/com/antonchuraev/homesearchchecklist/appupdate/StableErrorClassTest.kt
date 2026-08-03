package com.antonchuraev.homesearchchecklist.appupdate

import com.google.android.play.core.install.InstallException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Guards the `error_class` label sent with `app_update_unexpected_error`.
 *
 * The defect this protects against was invisible in the app and visible only in Amplitude:
 * after the vc74 keep-rule narrowing, all 13 values on 1.18.4+ arrived as R8 tokens (`ng1`,
 * `q13`) while the same exception had reached 1.17.16 readable. Obfuscation itself cannot be
 * reproduced here — debug builds do not obfuscate — so what these tests lock is the part that
 * CAN regress silently: which branch a given throwable falls into, and that unmatched types
 * still produce a value instead of blowing up.
 *
 * Order matters in the `when`, so the subtype cases below are the real assertions: a
 * `SocketTimeoutException` must report as `IOException`, not as its own R8-renameable name.
 */
class StableErrorClassTest {

    @Test
    fun `cancellation reports as CancellationException`() {
        // The observed offenders — LeftCompositionCancellationException,
        // ForgottenCoroutineScopeException — reach this branch as subtypes.
        assertEquals("CancellationException", stableErrorClass(CancellationException("cancelled")))
    }

    @Test
    fun `cancellation subtype still reports as CancellationException`() {
        class LeftCompositionCancellationException : CancellationException("left composition")
        assertEquals("CancellationException", stableErrorClass(LeftCompositionCancellationException()))
    }

    @Test
    fun `io failure reports as IOException`() {
        assertEquals("IOException", stableErrorClass(IOException("boom")))
    }

    @Test
    fun `io subtype reports as IOException, not its own name`() {
        // Stands in for ktor's HttpRequestTimeoutException, which R8 renamed to `qi3`.
        assertEquals("IOException", stableErrorClass(SocketTimeoutException("timeout")))
    }

    @Test
    fun `play install failure reports as InstallException`() {
        // InstallException has no public constructor; the SDK builds it from an error code.
        val installException = runCatching {
            val ctor = InstallException::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
            ctor.isAccessible = true
            ctor.newInstance(-6)
        }.getOrNull()

        if (installException == null) {
            // The SDK changed its constructor shape — do not fail the suite over a reflection
            // detail, but do not silently claim coverage either.
            println("SKIP: InstallException is not constructible via reflection in this SDK version")
            return
        }
        assertEquals("InstallException", stableErrorClass(installException))
    }

    @Test
    fun `unmatched throwable falls back to simpleName`() {
        class SomethingElseException : RuntimeException("nope")
        // Honest gap: in a release build this value may itself be obfuscated. The point of the
        // assertion is that the fallback still yields a label rather than throwing.
        assertEquals("SomethingElseException", stableErrorClass(SomethingElseException()))
    }

    @Test
    fun `anonymous throwable with no simpleName still yields a label`() {
        val anonymous = object : RuntimeException("anon") {}
        // Kotlin gives anonymous objects a null simpleName — the `?: "Unknown"` branch exists
        // for exactly this, and without it the analytics call would NPE on a crash path.
        val label = stableErrorClass(anonymous)
        assertEquals(true, label.isNotBlank(), "expected a non-blank label, got '$label'")
    }
}
