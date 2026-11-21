package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisReadyNotifier
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AppForegroundMonitor
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.ImageDownscaler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/*
 * Minimal port implementations for targets without a platform-specific one yet (iOS, and wasmJs until
 * `@wasmjs-expert` replaces the downscaler / monitor). Each one is honest about what it does NOT do.
 */

/**
 * Payload bytes kept in memory. Lost with the process / page: `resumePending` then fails a file job as
 * INTERRUPTED (owner decision 6 for the web, v1).
 */
class InMemoryAnalysisPayloadStore : AnalysisPayloadStore {
    private val lock = Mutex()
    private val payloads = mutableMapOf<String, ByteArray>()

    override suspend fun put(jobId: String, bytes: ByteArray): Boolean {
        lock.withLock { payloads[jobId] = bytes }
        return true
    }

    override suspend fun read(jobId: String): ByteArray? = lock.withLock { payloads[jobId] }

    override suspend fun delete(jobId: String) {
        lock.withLock { payloads.remove(jobId) }
    }
}

/**
 * No downscale on this target: returns null, which the [ImageDownscaler] contract defines as "not
 * downscaled — send the original bytes with their original MIME type". Returning the input instead
 * would claim JPEG for a PNG.
 */
class PassThroughImageDownscaler : ImageDownscaler {
    override suspend fun downscaleToJpeg(bytes: ByteArray, maxLongSidePx: Int, jpegQuality: Int): ByteArray? = null
}

/** Always foreground: delivery happens in-app (snackbar) on targets without a background notion. */
class AlwaysForegroundMonitor : AppForegroundMonitor {
    override val isInForeground: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()
}

/** No system notification on this target; the in-app signal delivers the outcome on return. */
class NoOpAnalysisReadyNotifier : AnalysisReadyNotifier {
    override fun notifyFinished(job: AnalysisJob) = Unit

    override fun cancel(jobId: String) = Unit
}
