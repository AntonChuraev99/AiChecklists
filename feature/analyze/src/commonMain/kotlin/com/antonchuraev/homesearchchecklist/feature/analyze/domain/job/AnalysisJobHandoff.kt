package com.antonchuraev.homesearchchecklist.feature.analyze.domain.job

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A failed job handed from the app snackbar / notification to the Analyze screen it opens (FEATURE_SPEC
 * rows 37–38). The screen attaches to that job and shows it in the Failed phase — reason, buttons and
 * the captured input — instead of an empty form; "Try again" then reuses the captured input
 * ([AnalysisJobScheduler.retry]). App-scoped (Koin single); the next Analyze visit [take]s it once.
 */
class AnalysisJobHandoff {
    private val pending = MutableStateFlow<String?>(null)

    fun offer(jobId: String) {
        pending.value = jobId
    }

    /** The handed-over job id, consumed: a second visit does not see it again. */
    fun take(): String? {
        val id = pending.value ?: return null
        return if (pending.compareAndSet(id, null)) id else null
    }
}

/**
 * "Was the notification permission already asked for an analysis?" — persisted, so owner decision 3
 * ("ask once, at the first analysis") survives process death and Activity recreation.
 */
interface AnalysisNotificationAskLatch {
    /** True exactly once ever: the first caller gets true and the latch closes. */
    suspend fun claimFirstAsk(): Boolean
}

/** Per-process latch: tests and the in-process fallback. Production binds the DataStore one. */
class InMemoryAnalysisNotificationAskLatch : AnalysisNotificationAskLatch {
    private var asked = false
    override suspend fun claimFirstAsk(): Boolean {
        if (asked) return false
        asked = true
        return true
    }
}
