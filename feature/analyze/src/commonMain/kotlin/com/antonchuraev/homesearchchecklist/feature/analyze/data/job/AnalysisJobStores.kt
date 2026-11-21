package com.antonchuraev.homesearchchecklist.feature.analyze.data.job

import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJob
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisJobStore
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisNotificationAskLatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Job records as ONE JSON string under one DataStore key — a short-lived queue of a handful of rows,
 * not per-entity data. Every write is a read-modify-write under [lock]; the store is a Koin single and
 * both writers (the UI and the Android worker) live in the same process, so the in-process lock is what
 * makes [update] atomic ([AppDatastore] has no transactional edit of its own).
 *
 * An undecodable blob (format change, corruption) is logged and treated as empty: losing the queue
 * loses at most the in-flight "ready" signals — the checklists themselves are already in Room.
 */
class DataStoreAnalysisJobStore(
    private val datastore: AppDatastore,
    private val logger: AppLogger,
) : AnalysisJobStore {

    private val lock = Mutex()

    override val jobs: Flow<List<AnalysisJob>> = datastore.observeString(KEY, "").map(::decode)

    override suspend fun get(jobId: String): AnalysisJob? = current().firstOrNull { it.id == jobId }

    override suspend fun put(job: AnalysisJob) {
        lock.withLock { write(current().filterNot { it.id == job.id } + job) }
    }

    override suspend fun update(jobId: String, transform: (AnalysisJob) -> AnalysisJob?): AnalysisJob? =
        lock.withLock {
            val all = current()
            val existing = all.firstOrNull { it.id == jobId } ?: return@withLock null
            val updated = transform(existing) ?: return@withLock existing
            write(all.map { if (it.id == jobId) updated else it })
            updated
        }

    override suspend fun delete(jobId: String) {
        lock.withLock { write(current().filterNot { it.id == jobId }) }
    }

    private suspend fun current(): List<AnalysisJob> = decode(datastore.observeString(KEY, "").first())

    private suspend fun write(jobs: List<AnalysisJob>) {
        datastore.saveString(KEY, json.encodeToString(serializer, jobs))
    }

    private fun decode(raw: String): List<AnalysisJob> {
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrElse { e ->
            logger.error(TAG, "analysis job queue is unreadable, starting empty: ${e.message}", e)
            emptyList()
        }
    }

    private companion object {
        const val TAG = "AnalysisJob"
        const val KEY = "analysis_jobs_v1"
        val serializer = ListSerializer(AnalysisJob.serializer())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}

/**
 * "Notification permission was asked for an analysis" in DataStore: asked once EVER (owner decision 3),
 * across process death and Activity recreation. The in-process lock makes check-then-set atomic for
 * the two ViewModels that could race on it.
 */
class DataStoreAnalysisNotificationAskLatch(
    private val datastore: AppDatastore,
) : AnalysisNotificationAskLatch {
    private val lock = Mutex()

    override suspend fun claimFirstAsk(): Boolean = lock.withLock {
        if (datastore.observeBoolean(KEY, false).first()) return@withLock false
        datastore.saveBoolean(KEY, true)
        true
    }

    private companion object {
        const val KEY = "analysis_notification_asked_v1"
    }
}

/** Same contract in memory: tests and the in-process fallback of the Analyze ViewModel. */
class InMemoryAnalysisJobStore : AnalysisJobStore {
    private val lock = Mutex()
    private val state = MutableStateFlow<List<AnalysisJob>>(emptyList())

    override val jobs: Flow<List<AnalysisJob>> = state.asStateFlow()

    override suspend fun get(jobId: String): AnalysisJob? = state.value.firstOrNull { it.id == jobId }

    override suspend fun put(job: AnalysisJob) {
        lock.withLock { state.value = state.value.filterNot { it.id == job.id } + job }
    }

    override suspend fun update(jobId: String, transform: (AnalysisJob) -> AnalysisJob?): AnalysisJob? =
        lock.withLock {
            val existing = state.value.firstOrNull { it.id == jobId } ?: return@withLock null
            val updated = transform(existing) ?: return@withLock existing
            state.value = state.value.map { if (it.id == jobId) updated else it }
            updated
        }

    override suspend fun delete(jobId: String) {
        lock.withLock { state.value = state.value.filterNot { it.id == jobId } }
    }
}
