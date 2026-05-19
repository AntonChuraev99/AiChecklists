package com.antonchuraev.homesearchchecklist.feature.aichat.api.domain.model

import kotlinx.serialization.Serializable

/**
 * A file the user attached to a chat message before sending.
 *
 * [sourcePath] is the platform-local path (or URI string) to the original file
 * as returned by the file / camera picker. It is NOT a stored path — the dispatcher
 * is responsible for persisting via [AttachmentStoragePort] on AttachToItem.
 *
 * [mimeType] examples: "image/jpeg", "image/png", "application/pdf",
 * "text/plain", "audio/m4a". Used to pick the correct [AttachmentSource] and
 * to pass the right [AnalyzeInputData] variant to [GeminiAiAnalyzer].
 *
 * [fileName] is the human-readable display name (shown in the chip strip).
 * [sizeBytes] is optional; used for size-limit checks in the dispatcher.
 */
@Serializable
data class ChatAttachment(
    val sourcePath: String,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long = 0L,
)

/**
 * Broad category of a [ChatAttachment], used to select the correct
 * [AnalyzeInputData] variant when routing to [GeminiAiAnalyzer].
 */
enum class AttachmentSource {
    Image,
    Pdf,
    Text,
    Audio,
}

/**
 * Maps a MIME type string to the appropriate [AttachmentSource] bucket.
 * Unknown / unsupported MIME types return null.
 */
fun mimeTypeToAttachmentSource(mimeType: String): AttachmentSource? = when {
    mimeType.startsWith("image/") -> AttachmentSource.Image
    mimeType == "application/pdf" -> AttachmentSource.Pdf
    mimeType.startsWith("text/") -> AttachmentSource.Text
    mimeType.startsWith("audio/") -> AttachmentSource.Audio
    // Common audio container MIMEs that some pickers report
    mimeType in setOf("video/mp4", "video/webm") -> AttachmentSource.Audio
    else -> null
}
