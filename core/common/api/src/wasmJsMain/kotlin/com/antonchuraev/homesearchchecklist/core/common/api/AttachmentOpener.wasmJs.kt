package com.antonchuraev.homesearchchecklist.core.common.api

// wasmJs: OPFS attachments and external file opening deferred to v2.
// PlatformCapabilities.attachmentsSupported = false on wasmJs — call sites must gate on it.
actual class AttachmentOpener {
    actual suspend fun openExternally(path: String, mimeType: String?): Boolean = false
}
