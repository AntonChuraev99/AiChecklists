package com.antonchuraev.homesearchchecklist.core.common.api

// iOS Phase 5: real implementation deferred (UIDocumentInteractionController / QuickLook).
// PlatformCapabilities.attachmentsSupported = false on iOS — call sites must gate on it.
actual class AttachmentOpener {
    actual suspend fun openExternally(path: String, mimeType: String?): Boolean = false
}
