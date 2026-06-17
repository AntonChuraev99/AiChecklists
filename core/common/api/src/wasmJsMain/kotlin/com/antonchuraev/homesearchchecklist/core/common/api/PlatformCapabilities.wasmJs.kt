package com.antonchuraev.homesearchchecklist.core.common.api

actual object PlatformCapabilities {
    // Web attachments are durable via OPFS (AttachmentStorage.wasmJs → globalThis.__opfs*).
    // OPFS needs no COOP/COEP (async main-thread API) and survives page reloads.
    actual val attachmentsSupported: Boolean = true
}
