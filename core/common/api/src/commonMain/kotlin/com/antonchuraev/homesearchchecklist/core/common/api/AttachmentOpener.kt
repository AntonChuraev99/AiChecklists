package com.antonchuraev.homesearchchecklist.core.common.api

/**
 * Platform-specific handler for opening a stored attachment in an external viewer.
 *
 * Call sites MUST check [PlatformCapabilities.attachmentsSupported] before using this class.
 *
 * Android: fires Intent.ACTION_VIEW via FileProvider URI with FLAG_GRANT_READ_URI_PERMISSION.
 * iOS: deferred until Phase 5 (UIDocumentInteractionController or QuickLook).
 * wasmJs: deferred until v2 (OPFS attachments unsupported).
 */
expect class AttachmentOpener {

    /**
     * Attempts to open the file at [path] in an appropriate external app.
     * [mimeType] is used for intent type resolution; if null the platform tries to infer it.
     *
     * Returns true if the intent was dispatched (no guarantee the target app handled it).
     * Returns false on any error (no matching app, FileProvider failure, etc.).
     */
    suspend fun openExternally(path: String, mimeType: String?): Boolean
}
