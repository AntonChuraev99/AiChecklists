package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground = at least one Activity STARTED, from `ProcessLifecycleOwner` (fed by androidx.startup's
 * ProcessLifecycleInitializer, which is why only individual initializers may ever be removed from the
 * manifest, never the whole InitializationProvider). A worker-started process with no UI reads false.
 *
 * Koin may create this off the main thread (the worker resolves the processor graph on a background
 * thread), so the observer is registered on the main looper; the initial value is read right away.
 */
class ProcessLifecycleAppForegroundMonitor(private val logger: AppLogger) : AppForegroundMonitor {

    private val state = MutableStateFlow(readNow())
    override val isInForeground: StateFlow<Boolean> = state.asStateFlow()

    init {
        val register = Runnable {
            runCatching {
                ProcessLifecycleOwner.get().lifecycle.addObserver(
                    LifecycleEventObserver { owner, _ ->
                        state.value = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                    },
                )
            }.onFailure { e ->
                logger.error(TAG, "ProcessLifecycleOwner unavailable — foreground flag frozen at ${state.value}", e)
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) register.run() else Handler(Looper.getMainLooper()).post(register)
    }

    private fun readNow(): Boolean = runCatching {
        ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
    }.getOrElse {
        // Unknown → foreground: the in-app signal is shown on return anyway, a wrong "background"
        // would post a notification over a visible app.
        true
    }

    private companion object {
        const val TAG = "AnalysisJob"
    }
}
