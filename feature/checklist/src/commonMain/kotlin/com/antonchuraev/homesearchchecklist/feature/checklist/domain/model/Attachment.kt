package com.antonchuraev.homesearchchecklist.feature.checklist.domain.model

import com.antonchuraev.homesearchchecklist.core.common.api.currentTimeMillis
import kotlin.random.Random
import kotlinx.serialization.Serializable

/**
 * A single file or image attachment associated with a [ChecklistFillItem].
 *
 * Attachments are stored inside the JSON blob of [ChecklistFillItem.attachments].
 * No separate SQL table — serialized/deserialized by the Room TypeConverter
 * (Json { ignoreUnknownKeys = true } guarantees backward compat with older DB rows).
 *
 * [path] is the LOCAL, platform-specific cache of the bytes:
 *   Android  — absolute path inside filesDir/attachments/<fillId>/<itemId>/
 *   iOS      — file:// URI
 *   wasmJs   — opfs://attachments/<fillId>/<itemId>/<attachmentId>.<ext>
 *
 * [storagePath] is the platform-INDEPENDENT cloud anchor (Firebase Storage object key,
 * e.g. users/<uid>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>). It travels with the
 * attachment in the synced JSON blob, so a device that does not yet hold the local bytes can
 * lazily download them from Storage. `null` until the bytes are uploaded (legacy/offline rows),
 * which is why image loaders fall back to [path] first and only reach for [storagePath] when the
 * local file is missing. The local [path] is intentionally NOT synced — it is meaningless on
 * another device/platform.
 *
 * [width]/[height] are optional metadata for image-grid sizing;
 * populated at store time if the platform can read image dimensions cheaply.
 */
@Serializable
data class Attachment(
    val id: String,
    val path: String,
    val fileName: String,
    val mimeType: String? = null,
    val sizeBytes: Long,
    val createdAt: Long,
    val width: Int? = null,
    val height: Int? = null,
    val storagePath: String? = null,
) {
    /** True when [mimeType] identifies this as an image (any `image/` subtype). */
    val isImage: Boolean
        get() = mimeType?.startsWith("image/") == true

    companion object {
        /** Generates a collision-resistant id. Same entropy strategy as [ChecklistItem.generateId]. */
        fun generateId(): String = "att_${currentTimeMillis()}_${Random.nextInt(0, 10000)}"
    }
}
