@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobLauncher
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.isTerminal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Arms the browser's own "Leave site? Changes you made may not be saved" prompt while an analysis is
 * in flight, so a reload / tab close does not silently drop a file job (a reload fails it as
 * INTERRUPTED — the bytes live only in memory, owner decision 6 for v1).
 *
 * "In flight" = any job not in a terminal status (Queued or Running): on the web a Queued job is
 * already inside the in-process launcher, and a reload loses it the same way.
 *
 * The `beforeunload` listener is attached only while armed and removed otherwise: a permanent
 * listener costs the page its back/forward-cache eligibility in some browsers. The prompt text is the
 * browser's own (custom text has been ignored by every engine for years); browsers also show it only
 * after the user has interacted with the page, which a started analysis always implies.
 */
class BeforeUnloadJobGuard(
    private val jobs: () -> StateFlow<List<AnalysisJob>>,
    private val logger: AppLogger,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private var started = false

    /** Idempotent. Called on the first launch, i.e. never before a job exists. */
    fun ensureStarted() {
        if (started) return
        started = true
        scope.launch {
            try {
                jobs()
                    .map { all -> all.any { !it.status.isTerminal } }
                    .distinctUntilChanged()
                    .collect { inFlight ->
                        jsSetBeforeUnloadArmed(inFlight)
                        logger.debug(TAG, "beforeunload prompt ${if (inFlight) "armed" else "disarmed"}")
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The guard is a safety net, never a reason to fail a job: log and leave it unarmed.
                jsSetBeforeUnloadArmed(false)
                logger.error(TAG, "beforeunload guard stopped: ${e.message}", e)
            }
        }
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}

/**
 * [AnalysisJobLauncher] decorator that starts [BeforeUnloadJobGuard] on the first launch. A job can only
 * be in flight after `launch` (fresh enqueue, retry, or `resumePending` after a reload all go through
 * it), so this needs no app-startup hook in common code.
 */
class BeforeUnloadAwareLauncher(
    private val delegate: AnalysisJobLauncher,
    private val guard: BeforeUnloadJobGuard,
) : AnalysisJobLauncher {

    override fun launch(jobId: String) {
        guard.ensureStarted()
        delegate.launch(jobId)
    }

    override fun cancel(jobId: String) = delegate.cancel(jobId)
}

@JsFun(
    """(armed) => {
        try {
            const KEY = '__analyzeBeforeUnloadHandler';
            const current = globalThis[KEY];
            if (armed) {
                if (current) return;
                const handler = (event) => {
                    event.preventDefault();
                    // Legacy engines (Chrome < 119, older Safari) need returnValue set to show the prompt.
                    event.returnValue = '';
                    return '';
                };
                globalThis[KEY] = handler;
                globalThis.addEventListener('beforeunload', handler);
            } else if (current) {
                globalThis.removeEventListener('beforeunload', current);
                globalThis[KEY] = null;
            }
        } catch (e) {
            console.warn('[AnalysisJob] beforeunload toggle failed:', e);
        }
    }"""
)
private external fun jsSetBeforeUnloadArmed(armed: Boolean)
