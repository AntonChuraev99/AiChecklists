package com.antonchuraev.homesearchchecklist.feature.home.presentation.detail

/**
 * Web (wasmJs): the OPFS fetcher/reader only understands `opfs://attachments/…` paths.
 *
 * - A web-captured attachment already carries a valid `opfs://` [path] → return it unchanged.
 * - An attachment synced from Android/iOS carries a foreign local [path] that OPFS cannot read →
 *   rebuild the web-local path from the platform-independent [storagePath]
 *   (`users/<uid>/attachments/<fillId>/<itemId>/<attachmentId>.<ext>`) by dropping the `users/<uid>/`
 *   prefix, matching the `opfs://attachments/<fillId>/<itemId>/<attachmentId>.<ext>` contract in
 *   `AttachmentCloudStorage.wasmJs.kt`.
 * - Legacy rows with no [storagePath] have no cloud anchor to derive from → fall back to the foreign
 *   [path] (the loader will surface its own decode error, reported by the caller).
 *
 * Pure string transform (no JS interop).
 */
internal actual fun resolveAttachmentLocalPath(path: String, storagePath: String?): String {
    if (path.startsWith("opfs://")) return path
    val tail = storagePath?.substringAfter("/attachments/", missingDelimiterValue = "")
    return if (!tail.isNullOrBlank()) "opfs://attachments/$tail" else path
}
