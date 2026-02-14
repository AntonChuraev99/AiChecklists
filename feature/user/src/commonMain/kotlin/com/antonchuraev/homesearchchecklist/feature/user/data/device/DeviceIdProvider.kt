package com.antonchuraev.homesearchchecklist.feature.user.data.device

import com.antonchuraev.homesearchchecklist.core.datastore.api.AppDatastore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Provides persistent unique device identifier for analytics and user tracking.
 *
 * **IMPORTANT**: Uses UUID v4 stored in DataStore to persist across app reinstalls.
 * This fixes Firebase Analytics showing duplicate devices after reinstall.
 *
 * Implementation details:
 * - Uses Mutex for thread-safe UUID generation
 * - Validates existing UUID format to handle DataStore corruption
 * - UUID persists across app reinstalls via DataStore backup
 *
 * @param appDatastore Persistent storage for device ID
 */
class DeviceIdProvider(
    private val appDatastore: AppDatastore
) {
    private val mutex = Mutex()

    companion object {
        private const val DEVICE_ID_KEY = "device_id"

        /**
         * UUID v4 regex pattern for validation.
         * Format: xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx
         * where y is one of [8, 9, A, B]
         */
        private val UUID_REGEX = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
            RegexOption.IGNORE_CASE
        )
    }

    /**
     * Gets existing device ID or creates new one if not exists.
     *
     * **Thread-safe**: Uses Mutex to prevent race conditions during UUID generation.
     * **Validation**: Replaces invalid UUIDs (e.g., from DataStore corruption).
     *
     * @return Persistent UUID v4 string
     */
    suspend fun getOrCreateDeviceId(): String = mutex.withLock {
        // Try to get existing ID
        val storedId = appDatastore.observeString(DEVICE_ID_KEY, "").first()

        // Validate: must be non-blank and valid UUID format
        if (storedId.isNotBlank() && isValidUUID(storedId)) {
            return storedId
        }

        // Generate new UUID if missing or invalid
        val newId = generateUUID()
        appDatastore.saveString(DEVICE_ID_KEY, newId)
        return newId
    }

    /**
     * Validates UUID v4 format.
     */
    private fun isValidUUID(uuid: String): Boolean {
        return UUID_REGEX.matches(uuid)
    }

    /**
     * Generates UUID v4 using expect/actual pattern.
     */
    private fun generateUUID(): String = uuidString()
}

/**
 * Platform-specific UUID generation.
 * Uses expect/actual for multiplatform support.
 */
internal expect fun uuidString(): String
