package com.antonchuraev.homesearchchecklist.feature.analyze.platform

import android.content.Context
import com.antonchuraev.homesearchchecklist.core.common.api.AppLogger
import com.antonchuraev.homesearchchecklist.feature.analyze.domain.job.AnalysisPayloadStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Payload bytes in app-private `filesDir/analysis_jobs/<jobId>` — survives process death (unlike the
 * picker's cache path, which the OS may clear while a job waits for a connection). Written to a temp
 * file and renamed, so a crash mid-write never leaves a truncated payload that would be sent as-is.
 * The scheduler deletes the file once the job is terminal and acknowledged.
 */
class FileAnalysisPayloadStore(
    context: Context,
    private val logger: AppLogger,
) : AnalysisPayloadStore {

    private val dir = File(context.applicationContext.filesDir, DIR_NAME)

    override suspend fun put(jobId: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!dir.isDirectory && !dir.mkdirs()) error("cannot create ${dir.path}")
            val target = fileFor(jobId)
            val temp = File(dir, "${target.name}.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(target)) {
                temp.delete()
                error("rename to ${target.name} failed")
            }
        }.fold(
            onSuccess = { true },
            onFailure = { e ->
                if (e is CancellationException) throw e
                logger.error(TAG, "payload write failed for job $jobId (${bytes.size} bytes): ${e.message}", e)
                false
            },
        )
    }

    override suspend fun read(jobId: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = fileFor(jobId)
        if (!file.isFile) return@withContext null
        runCatching { file.readBytes() }.getOrElse { e ->
            if (e is CancellationException) throw e
            logger.error(TAG, "payload read failed for job $jobId: ${e.message}", e)
            null
        }
    }

    override suspend fun delete(jobId: String) {
        withContext(Dispatchers.IO) {
            val file = fileFor(jobId)
            if (file.exists() && !file.delete()) {
                logger.warning(TAG, "payload of job $jobId could not be deleted")
            }
        }
    }

    /** Job ids are generated UUIDs; anything else is reduced to a safe file name (no path traversal). */
    private fun fileFor(jobId: String): File = File(dir, jobId.replace(UNSAFE_CHARS, "_"))

    private companion object {
        const val TAG = "AnalysisJob"
        const val DIR_NAME = "analysis_jobs"
        val UNSAFE_CHARS = Regex("[^A-Za-z0-9_-]")
    }
}
