package com.antonchuraev.homesearchchecklist.widget

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast // review-rules:allow-toast-no-compose-surface
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * Runs a Glance widget update so that a platform-level failure degrades instead of killing the
 * process.
 *
 * **Why `Throwable` and not `Exception`.** Every `GlanceAppWidget.update()/updateAll()` routes
 * through `SessionManagerImpl.startSession()` → `WorkManager.getInstance(context)`. Since
 * `WorkManagerInitializer` was taken off the `androidx.startup` path, that call is the point where
 * WorkManager is first built — and on an OEM ROM that reports API 34 while shipping a
 * `framework.jar` without `JobScheduler.forNamespace`, building it fails with `NoSuchMethodError`.
 * That is an `Error`, not an `Exception`: a `catch (e: Exception)` lets it through to
 * `Thread.uncaughtExceptionHandler`, which kills the process.
 *
 * **Why catching here is legitimate.** The root cause — eager WorkManager construction on the
 * start path — is already fixed; what is left is a foreign, broken ROM that the app can survive
 * but cannot repair. So this is a graceful degrade, not a muffler: the throwable is always logged
 * (which records it as a Crashlytics non-fatal, keeping the ROM visible in diagnostics) and the
 * caller always gets a chance to tell the user via [onDegraded].
 *
 * `CancellationException` is rethrown. An activity destroyed mid-update is normal coroutine
 * teardown, not a widget failure; swallowing it would break structured concurrency and would
 * report a phantom failure to the user.
 *
 * @param tag log tag of the calling widget entry point.
 * @param logger resolved from Koin by the caller; null when the graph is not up yet.
 * @param onDegraded user-visible feedback for the failure. Defaults to no-op only so tests can
 *   omit it — production call sites must pass one; the widget leaves no UI behind to absorb a
 *   silent failure.
 * @param update the Glance call to run.
 * @return true when the update completed, false when it was degraded.
 */
internal suspend fun runWidgetUpdateGuarded(
    tag: String,
    logger: AppLogger?,
    onDegraded: suspend (Throwable) -> Unit,
    update: suspend () -> Unit,
): Boolean {
    val failure = runCatching { update() }.exceptionOrNull() ?: return true
    if (failure is CancellationException) throw failure

    logger?.error(tag, "widget update failed: ${failure.message}", failure)
    // The feedback must not become the next uncaught throwable on this path — that would
    // reintroduce exactly the process kill this function exists to prevent.
    runCatching { onDegraded(failure) }.onFailure {
        // Teardown is not a feedback failure: rethrow so structured concurrency still works and
        // Crashlytics does not collect phantom non-fatals every time an activity dies mid-update.
        if (it is CancellationException) throw it
        logger?.error(tag, "widget degrade feedback failed: ${it.message}", it)
    }
    return false
}

/**
 * Posts a text toast from any thread.
 *
 * A toast is the only user-visible surface these widget entry points have: `ToggleItemActivity` is
 * translucent and `noHistory` (it has already finished when the failure surfaces), and Glance
 * cannot render a snackbar. `Toast` needs a prepared Looper and the widget work runs on a
 * background dispatcher, hence the post to main. Text toasts — `makeText` without `setView` — are
 * still delivered from the background on API 30+; only custom-view toasts are blocked there.
 *
 * @param messageRes an Android string resource. Widget and notification strings live in the
 *   `androidMain/res` `values` folders (localized en/ru/hi) rather than in Compose Resources,
 *   matching `ProcessTextActivity` — there is no composition here to read `Res.string` from.
 */
internal fun postWidgetToast(context: Context, messageRes: Int) {
    val appContext = context.applicationContext
    Handler(Looper.getMainLooper()).post {
        // The widget path has no Compose composition and no live activity to host a snackbar, and
        // the show is already posted to the main looper — which is the crash the rule guards against.
        Toast.makeText(appContext, messageRes, Toast.LENGTH_SHORT).show() // review-rules:allow-toast-no-compose-surface
    }
}
